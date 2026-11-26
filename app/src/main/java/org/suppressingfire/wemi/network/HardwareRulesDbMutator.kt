package org.suppressingfire.wemi.network

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-place SQLite mutator for Belkin on-device `rules.db` archives (`temppluginRules.db`).
 *
 * Preserves all pre-existing firmware tables and unrelated rules, sanitizes every user string
 * against unsafe SQL characters before writing, uses parameterized [ContentValues] inside atomic
 * transactions, and switches journal mode back to `DELETE` so the resulting `.db` file has zero
 * WAL/SHM sidecars before [HardwareRulesSafetyValidator.validateAndPackToBase64Zip] packs it.
 */
object HardwareRulesDbMutator {

    private const val DEFAULT_DATE_STAMP = "12201982"
    private const val SUNRISE_PLACEHOLDER_SEC = 21601 // 6:00 AM + 1 (% 10 == 1)
    private const val SUNSET_PLACEHOLDER_SEC = 64802  // 6:00 PM + 2 (% 10 == 2)
    private val UNSAFE_SQL_CHARS_STRIP = Regex("[\";\\r\\n\\u0000]|--")
    private val BACKUP_NAME_REGEX = Regex("rules_v(\\d+)_(\\d{8}_\\d{6})\\.zip")

    val PRESET_SOLAR_LOCATIONS: List<HardwareLocationInfo> = listOf(
        HardwareLocationInfo("San Francisco", "CA", "United States", "37.7749", "-122.4194"),
        HardwareLocationInfo("Los Angeles", "CA", "United States", "34.0522", "-118.2437"),
        HardwareLocationInfo("Seattle", "WA", "United States", "47.6062", "-122.3321"),
        HardwareLocationInfo("Denver", "CO", "United States", "39.7392", "-104.9903"),
        HardwareLocationInfo("Austin", "TX", "United States", "30.2672", "-97.7431"),
        HardwareLocationInfo("Chicago", "IL", "United States", "41.8781", "-87.6298"),
        HardwareLocationInfo("New York", "NY", "United States", "40.7128", "-74.0060"),
        HardwareLocationInfo("Toronto", "ON", "Canada", "43.6532", "-79.3832"),
        HardwareLocationInfo("London", "ENG", "United Kingdom", "51.5072", "-0.1276"),
        HardwareLocationInfo("Sydney", "NSW", "Australia", "-33.8688", "151.2093")
    )

    fun sanitizeSqlText(raw: String, maxLength: Int = 64): String =
        raw.replace(UNSAFE_SQL_CHARS_STRIP, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    /**
     * Inserts a new rule or updates [HardwareRuleDraft.existingRuleId] in place inside [sqliteFile].
     * Returns the persisted `RuleID`.
     */
    fun upsertRule(
        sqliteFile: File,
        ownerDeviceUdn: String,
        draft: HardwareRuleDraft
    ): Int {
        require(draft.type.let {
            it == HardwareRuleType.LONG_PRESS ||
                it == HardwareRuleType.COUNTDOWN_TIMER ||
                it == HardwareRuleType.SCHEDULE_INTERVAL ||
                it == HardwareRuleType.SIMPLE_TIMER
        }) { "Unsupported editable rule type: ${draft.type}" }

        val cleanOwnerUdn = sanitizeSqlText(
            LanRuntimeUtils.canonicalHardwareUdn(ownerDeviceUdn).ifBlank { ownerDeviceUdn },
            maxLength = 128
        )
        require(cleanOwnerUdn.isNotBlank()) { "Switch UDN is required to write an on-device rule" }

        val cleanName = sanitizeSqlText(draft.name).ifBlank { defaultRuleName(draft) }
        val cleanTargets = draft.targetDeviceUdns
            .map { sanitizeSqlText(LanRuntimeUtils.canonicalHardwareUdn(it).ifBlank { it }, maxLength = 128) }
            .filter { it.isNotBlank() }
            .distinct()
        if (draft.type == HardwareRuleType.LONG_PRESS) {
            require(cleanTargets.isNotEmpty()) { "Long Press requires at least one target switch" }
        }

        val db = SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            ensureJournalDeleteMode(db)
            val tables = HardwareRulesDbCodec.listTableNames(db).toMutableSet()
            ensureCoreTables(db, tables, needTargets = draft.type == HardwareRuleType.LONG_PRESS)
            ensureRuleDeviceColumns(db, draft.type)

            val rulesCols = HardwareRulesDbCodec.listColumnNames(db, "RULES")
            val ruleDeviceCols = HardwareRulesDbCodec.listColumnNames(db, "RULEDEVICES")

            return db.transaction {
                val targetRuleId = draft.existingRuleId?.takeIf { it > 0 } ?: nextRuleId(db)
                val existingOrder = if (draft.existingRuleId != null) {
                    queryInt(db, "SELECT RuleOrder FROM RULES WHERE CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(targetRuleId.toString()))
                } else {
                    null
                }
                val ruleOrder = existingOrder ?: nextRuleOrder(db)

                val ruleValues = ContentValues().apply {
                    put("RuleID", targetRuleId)
                    put("Name", cleanName)
                    put("Type", draft.type.firmwareType)
                    if ("RULEORDER" in rulesCols) put("RuleOrder", ruleOrder)
                    if ("STARTDATE" in rulesCols) put("StartDate", DEFAULT_DATE_STAMP)
                    if ("ENDDATE" in rulesCols) put("EndDate", DEFAULT_DATE_STAMP)
                    if ("STATE" in rulesCols) put("State", if (draft.isEnabled) "1" else "0")
                    if ("SYNC" in rulesCols) put("Sync", 0)
                }
                db.delete("RULES", "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(targetRuleId.toString()))
                db.insertOrThrow("RULES", null, ruleValues)

                db.delete("RULEDEVICES", "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(targetRuleId.toString()))
                if ("TARGETDEVICES" in tables) {
                    db.delete("TARGETDEVICES", "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(targetRuleId.toString()))
                }

                when (draft.type) {
                    HardwareRuleType.LONG_PRESS -> {
                        insertRuleDeviceRow(
                            db = db,
                            cols = ruleDeviceCols,
                            ruleId = targetRuleId,
                            deviceId = cleanOwnerUdn,
                            dayId = -1,
                            startTime = 60,
                            ruleDuration = 86340,
                            startAction = draft.longPressAction.code.toDouble(),
                            endAction = -1.0,
                            onModeOffset = 0,
                            offModeOffset = 0,
                            countdownTime = -1,
                            endTime = 86400
                        )
                        val targetCols = HardwareRulesDbCodec.listColumnNames(db, "TARGETDEVICES")
                        cleanTargets.forEachIndexed { index, targetUdn ->
                            val tv = ContentValues().apply {
                                put("RuleID", targetRuleId)
                                put("DeviceID", targetUdn)
                                if ("DEVICEINDEX" in targetCols) put("DeviceIndex", index)
                            }
                            db.insertOrThrow("TARGETDEVICES", null, tv)
                        }
                    }

                    HardwareRuleType.COUNTDOWN_TIMER -> {
                        val days = normalizeDays(draft.daysOfWeek)
                        val countdownSec = draft.countdownMinutes.coerceIn(1, 1440) * 60
                        days.forEach { dayId ->
                            insertRuleDeviceRow(
                                db = db,
                                cols = ruleDeviceCols,
                                ruleId = targetRuleId,
                                deviceId = cleanOwnerUdn,
                                dayId = dayId,
                                startTime = 0,
                                ruleDuration = 86400,
                                startAction = 1.0,
                                endAction = 0.0,
                                onModeOffset = 0,
                                offModeOffset = 0,
                                countdownTime = countdownSec,
                                endTime = 86400
                            )
                        }
                    }

                    HardwareRuleType.SCHEDULE_INTERVAL -> {
                        val days = normalizeDays(draft.daysOfWeek)
                        val (startSec, startOffSec, approxStart) = encodeTimePoint(
                            anchor = draft.startAnchor,
                            hour24 = draft.startHour24,
                            minute = draft.startMinute,
                            offsetMinutes = draft.startOffsetMinutes
                        )
                        val (endSec, endOffSec, approxEnd) = encodeTimePoint(
                            anchor = draft.endAnchor,
                            hour24 = draft.endHour24,
                            minute = draft.endMinute,
                            offsetMinutes = draft.endOffsetMinutes
                        )
                        val durationSec = computePositiveIntervalSeconds(approxStart, approxEnd)
                        val startAct = if (draft.startActionOn) 1.0 else 0.0
                        val endAct = if (draft.endActionOn) 1.0 else 0.0
                        days.forEach { dayId ->
                            insertRuleDeviceRow(
                                db = db,
                                cols = ruleDeviceCols,
                                ruleId = targetRuleId,
                                deviceId = cleanOwnerUdn,
                                dayId = dayId,
                                startTime = startSec,
                                ruleDuration = durationSec,
                                startAction = startAct,
                                endAction = endAct,
                                onModeOffset = startOffSec,
                                offModeOffset = endOffSec,
                                countdownTime = -1,
                                endTime = endSec
                            )
                        }
                    }

                    HardwareRuleType.SIMPLE_TIMER -> {
                        val days = normalizeDays(draft.daysOfWeek)
                        val (startSec, startOffSec, _) = encodeTimePoint(
                            anchor = draft.startAnchor,
                            hour24 = draft.startHour24,
                            minute = draft.startMinute,
                            offsetMinutes = draft.startOffsetMinutes
                        )
                        val startAct = if (draft.startActionOn) 1.0 else 0.0
                        days.forEach { dayId ->
                            insertRuleDeviceRow(
                                db = db,
                                cols = ruleDeviceCols,
                                ruleId = targetRuleId,
                                deviceId = cleanOwnerUdn,
                                dayId = dayId,
                                startTime = startSec,
                                ruleDuration = 0,
                                startAction = startAct,
                                endAction = -1.0,
                                onModeOffset = startOffSec,
                                offModeOffset = 0,
                                countdownTime = -1,
                                endTime = -1
                            )
                        }
                    }

                    else -> error("Unhandled rule type ${draft.type}")
                }

                targetRuleId
            }
        } finally {
            runCatching { db.close() }
            cleanupSqliteSidecars(sqliteFile)
        }
    }

    /**
     * Enables or disables any existing rule (`RULES.State = '1'` / `'0'`) in place without
     * altering its `RULEDEVICES` or `TARGETDEVICES` rows.
     */
    fun toggleRuleEnabled(sqliteFile: File, ruleId: Int, enabled: Boolean): Boolean {
        val db = SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        return try {
            ensureJournalDeleteMode(db)
            val cv = ContentValues().apply { put("State", if (enabled) "1" else "0") }
            val updated = db.update("RULES", cv, "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(ruleId.toString()))
            updated > 0
        } finally {
            runCatching { db.close() }
            cleanupSqliteSidecars(sqliteFile)
        }
    }

    /**
     * Removes [ruleId] cleanly across `RULES`, `RULEDEVICES`, `TARGETDEVICES`, and any optional
     * firmware rule tables present in [sqliteFile], leaving all other rules untouched.
     */
    fun deleteRule(sqliteFile: File, ruleId: Int): Boolean {
        val db = SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        return try {
            ensureJournalDeleteMode(db)
            val tables = HardwareRulesDbCodec.listTableNames(db)
            db.transaction {
                val idArg = arrayOf(ruleId.toString())
                val whereClause = "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)"
                val deleted = if ("RULES" in tables) delete("RULES", whereClause, idArg) else 0
                if ("RULEDEVICES" in tables) delete("RULEDEVICES", whereClause, idArg)
                if ("TARGETDEVICES" in tables) delete("TARGETDEVICES", whereClause, idArg)
                if ("DEVICECOMBINATION" in tables) delete("DEVICECOMBINATION", whereClause, idArg)
                if ("RULESNOTIFYMESSAGE" in tables) delete("RULESNOTIFYMESSAGE", whereClause, idArg)
                if ("SENSORNOTIFICATION" in tables) delete("SENSORNOTIFICATION", whereClause, idArg)
                if ("BLOCKEDRULES" in tables) delete("BLOCKEDRULES", "CAST(ruleId AS INTEGER) = CAST(? AS INTEGER)", idArg)
                deleted > 0
            }
        } finally {
            runCatching { db.close() }
            cleanupSqliteSidecars(sqliteFile)
        }
    }

    /**
     * Updates the switch's `LOCATIONINFO` table used by Belkin's on-device `sunriset.c` calculator
     * for Sunrise/Sunset schedules.
     */
    fun updateLocationInfo(sqliteFile: File, location: HardwareLocationInfo) {
        val latNum = location.latitude.trim().toDoubleOrNull()
        val lonNum = location.longitude.trim().toDoubleOrNull()
        require(latNum != null && latNum in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(lonNum != null && lonNum in -180.0..180.0) { "Longitude must be between -180 and 180" }

        val cleanCity = sanitizeSqlText(location.cityName).ifBlank { "Custom Location" }
        val cleanRegion = sanitizeSqlText(location.region)
        val cleanCountry = sanitizeSqlText(location.countryName).ifBlank { "US" }
        val cleanLat = String.format(Locale.US, "%.4f", latNum)
        val cleanLon = String.format(Locale.US, "%.4f", lonNum)

        val db = SQLiteDatabase.openDatabase(sqliteFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            ensureJournalDeleteMode(db)
            val tables = HardwareRulesDbCodec.listTableNames(db)
            if ("LOCATIONINFO" !in tables) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS LOCATIONINFO (LocationPk INTEGER PRIMARY KEY AUTOINCREMENT, cityName TEXT, countryName TEXT, latitude TEXT, longitude TEXT, countryCode TEXT, region TEXT)"
                )
            }
            val cols = HardwareRulesDbCodec.listColumnNames(db, "LOCATIONINFO")
            db.transaction {
                delete("LOCATIONINFO", null, null)
                val cv = ContentValues().apply {
                    if ("CITYNAME" in cols) put("cityName", cleanCity)
                    if ("COUNTRYNAME" in cols) put("countryName", cleanCountry)
                    if ("LATITUDE" in cols) put("latitude", cleanLat)
                    if ("LONGITUDE" in cols) put("longitude", cleanLon)
                    if ("COUNTRYCODE" in cols) put("countryCode", cleanCountry.take(2).uppercase(Locale.US))
                    if ("REGION" in cols) put("region", cleanRegion)
                }
                insertOrThrow("LOCATIONINFO", null, cv)
            }
        } finally {
            runCatching { db.close() }
            cleanupSqliteSidecars(sqliteFile)
        }
    }

    /**
     * Lists retained raw `/rules.db` ZIP backups for [deviceKey], newest first.
     */
    fun listBackupsForDevice(backupRootDir: File, deviceKey: String): List<HardwareRulesBackupEntry> {
        val sanitizedKey = deviceKey.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unknown_switch" }
        val deviceDir = File(backupRootDir, sanitizedKey)
        if (!deviceDir.isDirectory) return emptyList()
        val displayFmt = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        val parseFmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return deviceDir.listFiles { f -> f.isFile && f.extension == "zip" && f.length() > 0L }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val match = BACKUP_NAME_REGEX.matchEntire(file.name)
                val ver = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val stampDate = match?.groupValues?.getOrNull(2)?.let { raw ->
                    runCatching { parseFmt.parse(raw) }.getOrNull()
                } ?: Date(file.lastModified())
                HardwareRulesBackupEntry(
                    filePath = file.absolutePath,
                    dbVersion = ver,
                    createdAtLabel = displayFmt.format(stampDate),
                    sizeBytes = file.length()
                )
            }
    }

    internal fun defaultRuleName(draft: HardwareRuleDraft): String = when (draft.type) {
        HardwareRuleType.LONG_PRESS -> "Long Press ${draft.longPressAction.label}"
        HardwareRuleType.COUNTDOWN_TIMER -> "Auto-Off ${draft.countdownMinutes}m"
        HardwareRuleType.SCHEDULE_INTERVAL -> "On-Device Schedule"
        HardwareRuleType.SIMPLE_TIMER -> if (draft.startActionOn) "Timed Turn On" else "Timed Turn Off"
        else -> "Hardware Rule"
    }

    private fun normalizeDays(days: List<Int>): List<Int> =
        days.filter { it in 1..7 }.distinct().sorted().ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }

    private data class EncodedTimePoint(
        val rawSeconds: Int,
        val offsetSeconds: Int,
        val approxClockSeconds: Int
    )

    private fun encodeTimePoint(
        anchor: HardwareTimeAnchor,
        hour24: Int,
        minute: Int,
        offsetMinutes: Int
    ): EncodedTimePoint {
        val clampedOffset = offsetMinutes.coerceIn(-120, 120) * 60
        return when (anchor) {
            HardwareTimeAnchor.SUNRISE -> EncodedTimePoint(
                rawSeconds = SUNRISE_PLACEHOLDER_SEC,
                offsetSeconds = clampedOffset,
                approxClockSeconds = (21600 + clampedOffset).coerceIn(0, 86340)
            )
            HardwareTimeAnchor.SUNSET -> EncodedTimePoint(
                rawSeconds = SUNSET_PLACEHOLDER_SEC,
                offsetSeconds = clampedOffset,
                approxClockSeconds = (64800 + clampedOffset).coerceIn(0, 86340)
            )
            HardwareTimeAnchor.FIXED_TIME -> {
                val h = hour24.coerceIn(0, 23)
                val m = minute.coerceIn(0, 59)
                val sec = h * 3600 + m * 60
                EncodedTimePoint(rawSeconds = sec, offsetSeconds = 0, approxClockSeconds = sec)
            }
        }
    }

    private fun computePositiveIntervalSeconds(startApproxSec: Int, endApproxSec: Int): Int {
        val diff = if (endApproxSec > startApproxSec) endApproxSec - startApproxSec else (86400 - startApproxSec) + endApproxSec
        return diff.coerceIn(60, 86340)
    }

    private fun insertRuleDeviceRow(
        db: SQLiteDatabase, cols: Set<String>, ruleId: Int, deviceId: String,
        dayId: Int, startTime: Int, ruleDuration: Int, startAction: Double, endAction: Double,
        onModeOffset: Int, offModeOffset: Int, countdownTime: Int, endTime: Int
    ) {
        val cv = ContentValues().apply {
            put("RuleID", ruleId); put("DeviceID", deviceId)
            if ("GROUPID" in cols) put("GroupID", 0)
            put("DayID", dayId); put("StartTime", startTime); put("RuleDuration", ruleDuration)
            if ("STARTACTION" in cols) put("StartAction", startAction)
            if ("ENDACTION" in cols) put("EndAction", endAction)
            if ("SENSORDURATION" in cols) put("SensorDuration", -1)
            if ("TYPE" in cols) put("Type", -1)
            if ("VALUE" in cols) put("Value", -1)
            if ("LEVEL" in cols) put("Level", -1)
            if ("ONMODEOFFSET" in cols) put("OnModeOffset", onModeOffset)
            if ("OFFMODEOFFSET" in cols) put("OffModeOffset", offModeOffset)
            if ("COUNTDOWNTIME" in cols) put("CountdownTime", countdownTime)
            if ("ENDTIME" in cols) put("EndTime", endTime)
        }
        db.insertOrThrow("RULEDEVICES", null, cv)
    }

    private fun ensureCoreTables(db: SQLiteDatabase, tables: MutableSet<String>, needTargets: Boolean) {
        if ("RULES" !in tables) {
            db.execSQL("CREATE TABLE RULES (RuleID PRIMARY KEY, Name TEXT NOT NULL, Type TEXT NOT NULL, RuleOrder INTEGER, StartDate TEXT, EndDate TEXT, State TEXT, Sync INTEGER)")
            tables += "RULES"
        }
        if ("RULEDEVICES" !in tables) {
            db.execSQL("CREATE TABLE RULEDEVICES (RuleDevicePK INTEGER PRIMARY KEY AUTOINCREMENT, RuleID INTEGER, DeviceID TEXT, GroupID INTEGER, DayID INTEGER, StartTime INTEGER, RuleDuration INTEGER, StartAction REAL, EndAction REAL, SensorDuration INTEGER, Type INTEGER, Value INTEGER, Level INTEGER, ZBCapabilityStart TEXT, ZBCapabilityEnd TEXT, OnModeOffset INTEGER, OffModeOffset INTEGER, CountdownTime INTEGER, EndTime INTEGER)")
            tables += "RULEDEVICES"
        }
        if (needTargets && "TARGETDEVICES" !in tables) {
            db.execSQL("CREATE TABLE TARGETDEVICES (TargetDevicesPK INTEGER PRIMARY KEY AUTOINCREMENT, RuleID INTEGER, DeviceID TEXT, DeviceIndex INTEGER)")
            tables += "TARGETDEVICES"
        }
    }

    private fun ensureRuleDeviceColumns(db: SQLiteDatabase, type: HardwareRuleType) {
        val cols = HardwareRulesDbCodec.listColumnNames(db, "RULEDEVICES")
        if (type == HardwareRuleType.COUNTDOWN_TIMER && "COUNTDOWNTIME" !in cols) {
            db.execSQL("ALTER TABLE RULEDEVICES ADD COLUMN CountdownTime INTEGER DEFAULT -1")
        }
        if (type == HardwareRuleType.SCHEDULE_INTERVAL || type == HardwareRuleType.SIMPLE_TIMER) {
            if ("ONMODEOFFSET" !in cols) db.execSQL("ALTER TABLE RULEDEVICES ADD COLUMN OnModeOffset INTEGER DEFAULT 0")
            if ("OFFMODEOFFSET" !in cols) db.execSQL("ALTER TABLE RULEDEVICES ADD COLUMN OffModeOffset INTEGER DEFAULT 0")
            if ("ENDTIME" !in cols) db.execSQL("ALTER TABLE RULEDEVICES ADD COLUMN EndTime INTEGER DEFAULT -1")
        }
    }

    private fun nextRuleId(db: SQLiteDatabase): Int =
        (queryInt(db, "SELECT MAX(CAST(RuleID AS INTEGER)) FROM RULES", null) ?: 0) + 1

    private fun nextRuleOrder(db: SQLiteDatabase): Int =
        (queryInt(db, "SELECT MAX(RuleOrder) FROM RULES", null) ?: -1) + 1

    private fun queryInt(db: SQLiteDatabase, sql: String, args: Array<String>?): Int? =
        db.rawQuery(sql, args).use { c -> if (c.moveToFirst()) HardwareRulesDbCodec.cursorInt(c, 0) else null }

    private fun ensureJournalDeleteMode(db: SQLiteDatabase) {
        runCatching { db.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() } }
    }

    internal fun cleanupSqliteSidecars(sqliteFile: File) {
        listOf("-journal", "-wal", "-shm").forEach { suffix ->
            runCatching { File("${sqliteFile.path}$suffix").delete() }
        }
    }
}
