package org.suppressingfire.wemi.network

import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import java.io.File

/**
 * Anti-brick pre-upload validator and Belkin 9-table schema bootstrapper for on-device `rules.db`
 * archives (`temppluginRules.db`).
 *
 * Every mutation flow must pass [validateSqliteDbForStore] and [validateAndPackToBase64Zip] before
 * any `StoreRules` SOAP call is allowed on the LAN:
 * 1. Enforces flash-safe SQLite (`<= 512 KiB`) and ZIP (`<= 128 KiB`) caps plus SQLite 3 magic.
 * 2. Requires `PRAGMA integrity_check == "ok"` and presence of core `RULES` & `RULEDEVICES` tables.
 * 3. Rejects unsafe SQL characters (`"`, `;`, `--`, CR/LF, NUL) across rule names, types, and
 *    device IDs so the switch's unparameterized C `snprintf` SQL queries cannot break or inject.
 * 4. Enforces firmware bounds: `DayID` in `1..7` (or `-1` exclusively for `Long Press`, rejecting
 *    bitmasks like `127`), `StartTime` & `RuleDuration` in `0..86400`, `RuleDuration > 0` for
 *    `Time Interval`, and `CountdownTime > 0` for `Countdown Rule`.
 */
object HardwareRulesSafetyValidator {

    const val MAX_STORE_SQLITE_BYTES = 512 * 1024
    const val MAX_STORE_ZIP_BYTES = 128 * 1024

    private val UNSAFE_SQL_CHARS_REGEX = Regex("[\";\\r\\n\\u0000]|--")

    fun validateSqliteDbForStore(sqliteFile: File): HardwareRulesValidationResult {
        val issues = mutableListOf<String>()
        if (!sqliteFile.isFile || sqliteFile.length() <= 0L) {
            return HardwareRulesValidationResult(false, listOf("SQLite database file is missing or empty"))
        }
        if (sqliteFile.length() > MAX_STORE_SQLITE_BYTES) {
            issues += "SQLite DB exceeds embedded switch flash cap (${sqliteFile.length()} > $MAX_STORE_SQLITE_BYTES bytes)"
        }
        val header = sqliteFile.inputStream().use { inp ->
            val buf = ByteArray(16)
            val n = inp.read(buf)
            if (n == 16) buf else ByteArray(0)
        }
        if (!HardwareRulesDbCodec.hasValidSqliteHeader(header)) {
            return HardwareRulesValidationResult(false, listOf("File does not have a valid SQLite 3 header"))
        }

        val db = runCatching {
            SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrElse { err ->
            return HardwareRulesValidationResult(false, listOf("Cannot open SQLite DB: ${err.message}"))
        }

        return try {
            val integrity = HardwareRulesDbCodec.runSingleTextQuery(db, "PRAGMA integrity_check").orEmpty()
            if (!integrity.equals("ok", ignoreCase = true)) {
                issues += "PRAGMA integrity_check failed: $integrity"
            }
            val tables = HardwareRulesDbCodec.listTableNames(db)
            if ("RULES" !in tables) issues += "Missing required RULES table"
            if ("RULEDEVICES" !in tables) issues += "Missing required RULEDEVICES table"

            val ruleTypesById = validateRulesTable(db, tables, issues)
            validateRuleDevicesTable(db, tables, ruleTypesById, issues)
            validateTargetDevicesTable(db, tables, issues)

            HardwareRulesValidationResult(
                isSafeToStore = issues.isEmpty(),
                issues = issues,
                tableNames = tables,
                integrityCheckResult = integrity
            )
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * Creates a fresh SQLite database with the full 9-table Belkin firmware schema (used ONLY when
     * a supported switch has never stored rules yet and returns HTTP 404 / version 0).
     */
    fun createEmptyBelkinSchemaDb(targetFile: File) {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) targetFile.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(targetFile, null)
        try {
            listOf(
                "CREATE TABLE RULES (RuleID PRIMARY KEY, Name TEXT NOT NULL, Type TEXT NOT NULL, RuleOrder INTEGER, StartDate TEXT, EndDate TEXT, State TEXT, Sync INTEGER)",
                "CREATE TABLE RULEDEVICES (RuleDevicePK INTEGER PRIMARY KEY AUTOINCREMENT, RuleID INTEGER, DeviceID TEXT, GroupID INTEGER, DayID INTEGER, StartTime INTEGER, RuleDuration INTEGER, StartAction REAL, EndAction REAL, SensorDuration INTEGER, Type INTEGER, Value INTEGER, Level INTEGER, ZBCapabilityStart TEXT, ZBCapabilityEnd TEXT, OnModeOffset INTEGER, OffModeOffset INTEGER, CountdownTime INTEGER, EndTime INTEGER)",
                "CREATE TABLE DEVICECOMBINATION (DeviceCombinationPK INTEGER PRIMARY KEY AUTOINCREMENT, RuleID INTEGER, SensorID TEXT, SensorGroupID INTEGER, DeviceID TEXT, DeviceGroupID INTEGER)",
                "CREATE TABLE GROUPDEVICES (GroupDevicePK INTEGER PRIMARY KEY AUTOINCREMENT, GroupID INTEGER, DeviceID TEXT)",
                "CREATE TABLE LOCATIONINFO (LocationPk INTEGER PRIMARY KEY AUTOINCREMENT, cityName TEXT, countryName TEXT, latitude TEXT, longitude TEXT, countryCode TEXT, region TEXT)",
                "CREATE TABLE BLOCKEDRULES (Primarykey INTEGER PRIMARY KEY AUTOINCREMENT, ruleId TEXT)",
                "CREATE TABLE RULESNOTIFYMESSAGE (RuleID INTEGER PRIMARY KEY AUTOINCREMENT, NotifyRuleID INTEGER, Message TEXT, Frequency INTEGER)",
                "CREATE TABLE SENSORNOTIFICATION (SensorNotificationPK INTEGER PRIMARY KEY AUTOINCREMENT, RuleID INTEGER, NotifyRuleID INTEGER, NotificationMessage TEXT, NotificationDuration INTEGER)",
                "CREATE TABLE TARGETDEVICES (TargetDevicesPK INTEGER PRIMARY KEY AUTOINCREMENT, RuleID INTEGER, DeviceID TEXT, DeviceIndex INTEGER)"
            ).forEach { ddl -> db.execSQL(ddl) }
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * Validates [sqliteFile], packs it into a deflated ZIP preserving [zipEntryName], enforces
     * [MAX_STORE_ZIP_BYTES], and Base64-encodes it for `StoreRules`.
     */
    fun validateAndPackToBase64Zip(
        sqliteFile: File,
        zipEntryName: String = HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME
    ): Pair<String?, HardwareRulesValidationResult> {
        val verdict = validateSqliteDbForStore(sqliteFile)
        if (!verdict.isSafeToStore) return null to verdict
        val safeEntry = zipEntryName.trim()
            .takeIf { it.isNotEmpty() && !it.contains("..") && !it.contains('/') && !it.contains('\\') }
            ?: HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME
        val zipBytes = HardwareRulesDbCodec.packFileToZipBytes(sqliteFile, safeEntry)
        if (zipBytes.size > MAX_STORE_ZIP_BYTES) {
            return null to verdict.copy(
                isSafeToStore = false,
                issues = verdict.issues + "Zipped archive exceeds $MAX_STORE_ZIP_BYTES byte firmware cap (${zipBytes.size} bytes)"
            )
        }
        val b64 = Base64.encodeToString(zipBytes, Base64.NO_WRAP)
        return b64 to verdict
    }

    private fun validateRulesTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        issues: MutableList<String>
    ): Map<Int, String> {
        val ruleTypesById = mutableMapOf<Int, String>()
        if ("RULES" !in tables) return ruleTypesById
        db.rawQuery("SELECT RuleID, Name, Type, State FROM RULES", null).use { c ->
            while (c.moveToNext()) {
                val id = HardwareRulesDbCodec.cursorInt(c, 0)
                if (id == null || id <= 0) issues += "Invalid RuleID in RULES"
                val name = HardwareRulesDbCodec.cursorString(c, 1)
                val type = HardwareRulesDbCodec.cursorString(c, 2)
                val state = HardwareRulesDbCodec.cursorString(c, 3)
                if (id != null) ruleTypesById[id] = type
                if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(name)) issues += "Unsafe SQL character in rule name '$name'"
                if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(type)) issues += "Unsafe SQL character in rule type '$type'"
                if (state !in setOf("0", "1")) issues += "Invalid RULES.State '$state' for rule #$id"
            }
        }
        return ruleTypesById
    }

    private fun validateRuleDevicesTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        ruleTypesById: Map<Int, String>,
        issues: MutableList<String>
    ) {
        if ("RULEDEVICES" !in tables) return
        val cols = HardwareRulesDbCodec.listColumnNames(db, "RULEDEVICES")
        val hasCountdown = "COUNTDOWNTIME" in cols
        val query = if (hasCountdown) {
            "SELECT RuleID, DeviceID, DayID, StartTime, RuleDuration, CountdownTime FROM RULEDEVICES"
        } else {
            "SELECT RuleID, DeviceID, DayID, StartTime, RuleDuration, -1 FROM RULEDEVICES"
        }
        db.rawQuery(query, null).use { c ->
            while (c.moveToNext()) {
                val ruleId = HardwareRulesDbCodec.cursorInt(c, 0) ?: -1
                val devId = HardwareRulesDbCodec.cursorString(c, 1)
                val dayId = HardwareRulesDbCodec.cursorInt(c, 2) ?: -99
                val startTime = HardwareRulesDbCodec.cursorInt(c, 3) ?: -1
                val duration = HardwareRulesDbCodec.cursorInt(c, 4) ?: -1
                val countdown = HardwareRulesDbCodec.cursorInt(c, 5) ?: -1
                val type = ruleTypesById[ruleId].orEmpty()
                if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(devId)) {
                    issues += "Unsafe SQL character in RULEDEVICES.DeviceID for rule #$ruleId"
                }
                val validDay = dayId in 1..7 || (dayId == -1 && type.equals("Long Press", ignoreCase = true))
                if (!validDay) {
                    issues += "Invalid DayID=$dayId for rule #$ruleId ($type); firmware requires 1..7 (or -1 for Long Press)"
                }
                if (startTime !in 0..86400) {
                    issues += "StartTime=$startTime out of 0..86400 bounds for rule #$ruleId"
                }
                if (duration !in 0..86400) {
                    issues += "RuleDuration=$duration out of 0..86400 bounds for rule #$ruleId"
                }
                if (type.equals("Time Interval", ignoreCase = true) && duration <= 0) {
                    issues += "Time Interval rule #$ruleId requires RuleDuration > 0"
                }
                if (type.equals("Countdown Rule", ignoreCase = true) && countdown <= 0) {
                    issues += "Countdown Rule #$ruleId requires CountdownTime > 0"
                }
            }
        }
    }

    private fun validateTargetDevicesTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        issues: MutableList<String>
    ) {
        if ("TARGETDEVICES" !in tables) return
        db.rawQuery("SELECT RuleID, DeviceID FROM TARGETDEVICES", null).use { c ->
            while (c.moveToNext()) {
                val devId = HardwareRulesDbCodec.cursorString(c, 1)
                if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(devId)) {
                    issues += "Unsafe SQL character in TARGETDEVICES.DeviceID"
                }
            }
        }
    }
}
