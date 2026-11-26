package org.suppressingfire.wemi.network

import android.database.sqlite.SQLiteDatabase
import android.util.Base64
import java.io.File
import java.util.Locale

/**
 * Anti-brick pre-upload validator and Belkin 9-table schema bootstrapper for on-device `rules.db`
 * archives (`temppluginRules.db`).
 *
 * Every mutation flow must pass [validateSqliteDbForStore] and [validateAndPackToBase64Zip] before
 * any `StoreRules` SOAP call is allowed on the LAN:
 * 1. Enforces flash-safe SQLite (`<= 512 KiB`) and ZIP (`<= 128 KiB`) caps, 20-byte SQLite 3
 *    header magic, and rollback journal header bytes (`header[18] == 1 && header[19] == 1`,
 *    rejecting WAL mode `2`).
 * 2. Requires `PRAGMA integrity_check == "ok"` and presence of core `RULES` & `RULEDEVICES` tables.
 * 3. Rejects unsafe SQL characters (`'`, `"`, `;`, `--`, `/*`, `*/`, CR/LF, NUL) across editable/
 *    mutated rules, target IDs, and `LOCATIONINFO` text columns so the switch's unparameterized C
 *    `snprintf` SQL queries cannot break or inject.
 * 4. Strictly enforces firmware bounds on our 4 editable rule types (and any mutated rows) while
 *    tolerating untouched legacy Belkin rule types (`Motion Controlled`, `Notify Me`, `Insight Rule`,
 *    `Away Mode`, `Maker Sensor Rule`) and raw backup restores (`isRawBackupRestore = true`).
 */
object HardwareRulesSafetyValidator {

    const val MAX_STORE_SQLITE_BYTES = 512 * 1024
    const val MAX_STORE_ZIP_BYTES = 128 * 1024

    internal val UNSAFE_SQL_CHARS_REGEX = Regex("['\";\\r\\n\\u0000]|--|/\\*|\\*/")

    private val STRICT_EDITABLE_RULE_TYPES = setOf(
        HardwareRuleType.LONG_PRESS.firmwareType.lowercase(Locale.US),
        HardwareRuleType.COUNTDOWN_TIMER.firmwareType.lowercase(Locale.US),
        HardwareRuleType.SCHEDULE_INTERVAL.firmwareType.lowercase(Locale.US),
        HardwareRuleType.SIMPLE_TIMER.firmwareType.lowercase(Locale.US)
    )

    fun validateSqliteDbForStore(
        sqliteFile: File,
        isRawBackupRestore: Boolean = false,
        mutatedRuleIds: Set<Int> = emptySet(),
        inspectedOwnerUdn: String? = null
    ): HardwareRulesValidationResult {
        val issues = mutableListOf<String>()
        if (!sqliteFile.isFile || sqliteFile.length() <= 0L) {
            return HardwareRulesValidationResult(false, listOf("SQLite database file is missing or empty"))
        }
        if (sqliteFile.length() > MAX_STORE_SQLITE_BYTES) {
            issues += "SQLite DB exceeds embedded switch flash cap (${sqliteFile.length()} > $MAX_STORE_SQLITE_BYTES bytes)"
        }
        val header = sqliteFile.inputStream().use { inp ->
            val buf = ByteArray(20)
            var offset = 0
            while (offset < buf.size) {
                val n = inp.read(buf, offset, buf.size - offset)
                if (n == -1) break
                offset += n
            }
            if (offset == buf.size) buf else ByteArray(0)
        }
        if (header.size < 20 || !HardwareRulesDbCodec.hasValidSqliteHeader(header)) {
            return HardwareRulesValidationResult(false, listOf("File does not have a valid SQLite 3 header"))
        }
        if (header[18] != 1.toByte() || header[19] != 1.toByte()) {
            return HardwareRulesValidationResult(
                false,
                listOf("SQLite DB must use rollback journal mode (header bytes 18..19=${header[18].toInt() and 0xFF},${header[19].toInt() and 0xFF}; WAL unsupported on switch)")
            )
        }

        val db = runCatching {
            SQLiteDatabase.openDatabase(
                sqliteFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )
        }.getOrElse { err ->
            return HardwareRulesValidationResult(false, listOf("Cannot open SQLite DB: ${err.message}"))
        }

        return try {
            val schemaViolations = HardwareRulesSchemaGuard.hardenAndValidateSchema(
                db = db,
                readOnly = true,
                throwOnViolation = false
            )
            if (schemaViolations.isNotEmpty()) {
                issues += schemaViolations
                return HardwareRulesValidationResult(
                    isSafeToStore = false,
                    issues = issues,
                    tableNames = emptySet(),
                    integrityCheckResult = "blocked_untrusted_schema"
                )
            }

            val integrity = HardwareRulesDbCodec.runSingleTextQuery(db, "PRAGMA integrity_check").orEmpty()
            if (!integrity.equals("ok", ignoreCase = true)) {
                issues += "PRAGMA integrity_check failed: $integrity"
            }
            val tables = HardwareRulesDbCodec.listTableNames(db)
            if ("RULES" !in tables) issues += "Missing required RULES table"
            if ("RULEDEVICES" !in tables) issues += "Missing required RULEDEVICES table"

            if (!isRawBackupRestore) {
                val cleanInspectedOwner = inspectedOwnerUdn
                    ?.let { LanRuntimeUtils.canonicalHardwareUdn(it).ifBlank { it.trim() } }
                    ?.takeIf { it.isNotBlank() }
                val ruleTypesById = validateRulesTable(db, tables, mutatedRuleIds, issues)
                validateRuleDevicesTable(db, tables, ruleTypesById, mutatedRuleIds, cleanInspectedOwner, issues)
                validateTargetDevicesTable(db, tables, ruleTypesById, mutatedRuleIds, issues)
                validateLocationInfoTable(db, tables, issues)
            }

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
        HardwareRulesDbMutator.cleanupSqliteSidecars(targetFile)
        val db = SQLiteDatabase.openDatabase(
            targetFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            runCatching { db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() } }
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
            HardwareRulesDbMutator.cleanupSqliteSidecars(targetFile)
        }
    }

    /**
     * Validates [sqliteFile], packs it into a deflated ZIP preserving [zipEntryName], enforces
     * [MAX_STORE_ZIP_BYTES], and Base64-encodes it for `StoreRules`.
     */
    fun validateAndPackToBase64Zip(
        sqliteFile: File,
        zipEntryName: String = HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME,
        isRawBackupRestore: Boolean = false,
        mutatedRuleIds: Set<Int> = emptySet(),
        inspectedOwnerUdn: String? = null
    ): Pair<String?, HardwareRulesValidationResult> {
        val verdict = validateSqliteDbForStore(
            sqliteFile = sqliteFile,
            isRawBackupRestore = isRawBackupRestore,
            mutatedRuleIds = mutatedRuleIds,
            inspectedOwnerUdn = inspectedOwnerUdn
        )
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

    private fun isStrictRuleType(type: String): Boolean =
        type.trim().lowercase(Locale.US) in STRICT_EDITABLE_RULE_TYPES

    private fun validateRulesTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        mutatedRuleIds: Set<Int>,
        issues: MutableList<String>
    ): Map<Int, String> {
        val ruleTypesById = mutableMapOf<Int, String>()
        if ("RULES" !in tables) return ruleTypesById
        val cols = HardwareRulesDbCodec.listColumnNames(db, "RULES")
        val stateExpr = if ("STATE" in cols) "State" else "'1'"
        db.rawQuery("SELECT RuleID, Name, Type, $stateExpr FROM RULES", null).use { c ->
            while (c.moveToNext()) {
                val id = HardwareRulesDbCodec.cursorInt(c, 0)
                if (id == null || id <= 0) issues += "Invalid RuleID in RULES"
                val name = HardwareRulesDbCodec.cursorString(c, 1)
                val type = HardwareRulesDbCodec.cursorString(c, 2)
                val state = HardwareRulesDbCodec.cursorString(c, 3)
                if (id != null) ruleTypesById[id] = type
                val strict = (id != null && id in mutatedRuleIds) || isStrictRuleType(type)
                if (strict) {
                    if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(name)) issues += "Unsafe SQL character in rule name '$name'"
                    if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(type)) issues += "Unsafe SQL character in rule type '$type'"
                }
                if (state !in setOf("0", "1")) issues += "Invalid RULES.State '$state' for rule #$id"
            }
        }
        return ruleTypesById
    }

    private fun validateRuleDevicesTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        ruleTypesById: Map<Int, String>,
        mutatedRuleIds: Set<Int>,
        inspectedOwnerUdn: String?,
        issues: MutableList<String>
    ) {
        if ("RULEDEVICES" !in tables) return
        val cols = HardwareRulesDbCodec.listColumnNames(db, "RULEDEVICES")
        val countdownExpr = if ("COUNTDOWNTIME" in cols) "CountdownTime" else "-1"
        db.rawQuery(
            "SELECT RuleID, DeviceID, DayID, StartTime, RuleDuration, $countdownExpr FROM RULEDEVICES",
            null
        ).use { c ->
            while (c.moveToNext()) {
                val ruleId = HardwareRulesDbCodec.cursorInt(c, 0) ?: -1
                val devId = HardwareRulesDbCodec.cursorString(c, 1)
                val dayId = HardwareRulesDbCodec.cursorInt(c, 2) ?: -99
                val startTime = HardwareRulesDbCodec.cursorInt(c, 3) ?: -1
                val duration = HardwareRulesDbCodec.cursorInt(c, 4) ?: -1
                val countdown = HardwareRulesDbCodec.cursorInt(c, 5) ?: -1
                val type = ruleTypesById[ruleId].orEmpty()
                val cleanRowOwner = LanRuntimeUtils.canonicalHardwareUdn(devId).ifBlank { devId }
                val isUnownedOtherSwitch = inspectedOwnerUdn != null &&
                    cleanRowOwner.isNotBlank() &&
                    !cleanRowOwner.equals(inspectedOwnerUdn, ignoreCase = true)
                val isMutated = ruleId in mutatedRuleIds
                val isStrictRow = isMutated || (isStrictRuleType(type) && !isUnownedOtherSwitch)

                if (isStrictRow) {
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
                } else {
                    // Untouched legacy Belkin rule types or unowned peer rules tolerate DayID -1/8..10 and -1 sentinels.
                    val legacyDayOk = dayId in 1..10 || dayId == -1
                    if (!legacyDayOk) {
                        issues += "Invalid legacy DayID=$dayId for rule #$ruleId ($type)"
                    }
                    if (startTime != -1 && startTime !in 0..86400) {
                        issues += "StartTime=$startTime out of bounds for legacy rule #$ruleId"
                    }
                    if (duration != -1 && duration !in 0..86400) {
                        issues += "RuleDuration=$duration out of bounds for legacy rule #$ruleId"
                    }
                }
            }
        }
    }

    private fun validateTargetDevicesTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        ruleTypesById: Map<Int, String>,
        mutatedRuleIds: Set<Int>,
        issues: MutableList<String>
    ) {
        if ("TARGETDEVICES" !in tables) return
        val cols = HardwareRulesDbCodec.listColumnNames(db, "TARGETDEVICES")
        if ("RULEID" !in cols || "DEVICEID" !in cols) return
        db.rawQuery("SELECT RuleID, DeviceID FROM TARGETDEVICES", null).use { c ->
            while (c.moveToNext()) {
                val ruleId = HardwareRulesDbCodec.cursorInt(c, 0) ?: -1
                val type = ruleTypesById[ruleId].orEmpty()
                if (ruleId in mutatedRuleIds || isStrictRuleType(type) || type.isBlank()) {
                    val devId = HardwareRulesDbCodec.cursorString(c, 1)
                    if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(devId)) {
                        issues += "Unsafe SQL character in TARGETDEVICES.DeviceID"
                    }
                }
            }
        }
    }

    private fun validateLocationInfoTable(
        db: SQLiteDatabase,
        tables: Set<String>,
        issues: MutableList<String>
    ) {
        if ("LOCATIONINFO" !in tables) return
        val cols = HardwareRulesDbCodec.listColumnNames(db, "LOCATIONINFO")
        val textCols = listOf("cityName", "region", "countryName", "countryCode", "latitude", "longitude")
            .filter { it.uppercase(Locale.US) in cols }
        if (textCols.isEmpty()) return
        db.rawQuery("SELECT ${textCols.joinToString(", ")} FROM LOCATIONINFO", null).use { c ->
            while (c.moveToNext()) {
                textCols.forEachIndexed { idx, colName ->
                    val value = HardwareRulesDbCodec.cursorString(c, idx)
                    if (UNSAFE_SQL_CHARS_REGEX.containsMatchIn(value)) {
                        issues += "Unsafe SQL character in LOCATIONINFO.$colName"
                    }
                }
            }
        }
    }
}
