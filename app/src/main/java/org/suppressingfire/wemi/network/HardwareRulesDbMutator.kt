package org.suppressingfire.wemi.network

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import java.io.File
import java.util.Locale

/**
 * In-place SQLite mutator for Belkin on-device `rules.db` archives (`temppluginRules.db`).
 *
 * Preserves all pre-existing firmware tables and unrelated rules, sanitizes every user string
 * against unsafe SQL characters before writing, uses parameterized [ContentValues] inside atomic
 * transactions with `NO_LOCALIZED_COLLATORS`, and switches journal mode back to `DELETE` so the
 * resulting `.db` file has zero WAL/SHM sidecars before packing.
 */
object HardwareRulesDbMutator {

    private const val DEFAULT_DATE_STAMP = "12201982"
    private const val SUNRISE_PLACEHOLDER_SEC = 21601 // 6:00 AM + 1 (% 10 == 1)
    private const val SUNSET_PLACEHOLDER_SEC = 64802  // 6:00 PM + 2 (% 10 == 2)
    private const val OPEN_RW_FLAGS = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS
    private const val OPEN_RO_FLAGS = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS

    val PRESET_SOLAR_LOCATIONS: List<HardwareLocationInfo> = listOf(
        HardwareLocationInfo("San Francisco", "CA", "United States", "37.7749", "-122.4194", "US"),
        HardwareLocationInfo("Los Angeles", "CA", "United States", "34.0522", "-118.2437", "US"),
        HardwareLocationInfo("Seattle", "WA", "United States", "47.6062", "-122.3321", "US"),
        HardwareLocationInfo("Denver", "CO", "United States", "39.7392", "-104.9903", "US"),
        HardwareLocationInfo("Austin", "TX", "United States", "30.2672", "-97.7431", "US"),
        HardwareLocationInfo("Chicago", "IL", "United States", "41.8781", "-87.6298", "US"),
        HardwareLocationInfo("New York", "NY", "United States", "40.7128", "-74.0060", "US"),
        HardwareLocationInfo("Toronto", "ON", "Canada", "43.6532", "-79.3832", "CA"),
        HardwareLocationInfo("London", "ENG", "United Kingdom", "51.5072", "-0.1276", "GB"),
        HardwareLocationInfo("Sydney", "NSW", "Australia", "-33.8688", "151.2093", "AU")
    )

    fun sanitizeSqlText(raw: String, maxLength: Int = 64): String =
        raw.replace(HardwareRulesSafetyValidator.UNSAFE_SQL_CHARS_REGEX, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(maxLength)

    internal fun resolveCountryCode(location: HardwareLocationInfo): String {
        val explicit = sanitizeSqlText(location.countryCode, maxLength = 2).uppercase(Locale.US)
        if (explicit.length == 2 && explicit.all { it in 'A'..'Z' }) return explicit
        val c = sanitizeSqlText(location.countryName).lowercase(Locale.US)
        return when {
            c.isEmpty() -> ""
            c.length == 2 && c.all { it in 'a'..'z' } -> c.uppercase(Locale.US)
            c.contains("united states") || c in setOf("usa", "u.s.", "u.s.a.") -> "US"
            c.contains("united kingdom") || c in setOf("uk", "england") || c.contains("britain") -> "GB"
            c.contains("canada") -> "CA"
            c.contains("australia") -> "AU"
            c.contains("new zealand") -> "NZ"
            c.contains("germany") -> "DE"
            c.contains("france") -> "FR"
            c.contains("japan") -> "JP"
            c.contains("ireland") -> "IE"
            c.contains("mexico") -> "MX"
            else -> c.filter { it in 'a'..'z' }.take(2).uppercase(Locale.US)
        }
    }

    private fun openHardenedDb(sqliteFile: File, readOnly: Boolean): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(
            sqliteFile.absolutePath, null, if (readOnly) OPEN_RO_FLAGS else OPEN_RW_FLAGS
        )
        try {
            HardwareRulesSchemaGuard.hardenAndValidateSchema(db, readOnly = readOnly, throwOnViolation = true)
            if (!readOnly) ensureJournalDeleteMode(db)
            return db
        } catch (t: Throwable) {
            runCatching { db.close() }
            throw t
        }
    }

    /** Guards against overwriting/deleting rules owned by another switch or shared across multiple switches. */
    internal fun verifyRuleOwnedExclusivelyBy(sqliteFile: File, ruleId: Int, ownerDeviceUdn: String) {
        val cleanOwner = LanRuntimeUtils.canonicalHardwareUdn(ownerDeviceUdn).ifBlank { ownerDeviceUdn.trim() }.lowercase(Locale.US)
        val db = openHardenedDb(sqliteFile, readOnly = true)
        try {
            if ("RULEDEVICES" !in HardwareRulesDbCodec.listTableNames(db)) return
            val owners = LinkedHashSet<String>()
            db.rawQuery("SELECT DeviceID FROM RULEDEVICES WHERE CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(ruleId.toString())).use { c ->
                while (c.moveToNext()) {
                    val raw = HardwareRulesDbCodec.cursorString(c, 0)
                    val clean = LanRuntimeUtils.canonicalHardwareUdn(raw).ifBlank { raw.trim() }.lowercase(Locale.US)
                    if (clean.isNotBlank()) owners.add(clean)
                }
            }
            if (owners.size > 1) throw IllegalStateException("Rule #$ruleId is shared across multiple switches and cannot be modified here")
            if (cleanOwner.isNotBlank() && owners.isNotEmpty() && owners.single() != cleanOwner) {
                throw IllegalStateException("Rule #$ruleId belongs to another switch and is read-only here")
            }
        } finally {
            runCatching { db.close() }
        }
    }

    /**
     * Inserts a new rule or updates [HardwareRuleDraft.existingRuleId] in place inside [sqliteFile].
     * Returns the persisted `RuleID`.
     */
    fun upsertRule(sqliteFile: File, ownerDeviceUdn: String, draft: HardwareRuleDraft): Int {
        require(
            draft.type in setOf(
                HardwareRuleType.LONG_PRESS, HardwareRuleType.COUNTDOWN_TIMER,
                HardwareRuleType.SCHEDULE_INTERVAL, HardwareRuleType.SIMPLE_TIMER
            )
        ) { "Unsupported editable rule type: ${draft.type}" }

        val cleanOwnerUdn = sanitizeSqlText(LanRuntimeUtils.canonicalHardwareUdn(ownerDeviceUdn).ifBlank { ownerDeviceUdn }, maxLength = 128)
        require(cleanOwnerUdn.isNotBlank()) { "Switch UDN is required to write an on-device rule" }

        val cleanName = sanitizeSqlText(draft.name).ifBlank { defaultRuleName(draft) }
        val cleanTargets = draft.targetDeviceUdns
            .map { sanitizeSqlText(LanRuntimeUtils.canonicalHardwareUdn(it).ifBlank { it }, maxLength = 128) }
            .filter { it.isNotBlank() }
            .distinct()
        if (draft.type == HardwareRuleType.LONG_PRESS) {
            require(cleanTargets.isNotEmpty()) { "Long Press requires at least one target switch" }
        }

        val db = openHardenedDb(sqliteFile, readOnly = false)
        try {
            val tables = HardwareRulesDbCodec.listTableNames(db).toMutableSet()
            ensureCoreTables(db, tables, needTargets = draft.type == HardwareRuleType.LONG_PRESS)
            ensureRuleDeviceColumns(db, draft.type)

            val rulesCols = HardwareRulesDbCodec.listColumnNames(db, "RULES")
            val ruleDeviceCols = HardwareRulesDbCodec.listColumnNames(db, "RULEDEVICES")

            return db.transaction {
                val targetRuleId = draft.existingRuleId?.takeIf { it > 0 } ?: nextRuleId(db)
                val existingOrder = if (draft.existingRuleId != null && "RULEORDER" in rulesCols) {
                    queryInt(db, "SELECT RuleOrder FROM RULES WHERE CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", arrayOf(targetRuleId.toString()))
                } else null
                val ruleOrder = existingOrder ?: if ("RULEORDER" in rulesCols) nextRuleOrder(db) else 0
                val preservedWindow = if (draft.type == HardwareRuleType.COUNTDOWN_TIMER && draft.existingRuleId != null) {
                    readExistingCountdownWindow(db, ruleDeviceCols, targetRuleId)
                } else null

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
                val idArg = arrayOf(targetRuleId.toString())
                db.delete("RULES", "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", idArg)
                db.insertOrThrow("RULES", null, ruleValues)

                db.delete("RULEDEVICES", "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", idArg)
                if ("TARGETDEVICES" in tables) {
                    db.delete("TARGETDEVICES", "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)", idArg)
                }

                when (draft.type) {
                    HardwareRuleType.LONG_PRESS -> {
                        insertRuleDeviceRow(
                            db = db, cols = ruleDeviceCols, ruleId = targetRuleId, deviceId = cleanOwnerUdn,
                            dayId = -1, startTime = 60, ruleDuration = 86340,
                            startAction = draft.longPressAction.code.toDouble(), endAction = -1.0,
                            onModeOffset = 0, offModeOffset = 0, countdownTime = -1, endTime = 86400
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
                        val w = preservedWindow ?: CountdownWindow(0, 86400, 86400, 0, 0)
                        days.forEach { dayId ->
                            insertRuleDeviceRow(
                                db = db, cols = ruleDeviceCols, ruleId = targetRuleId, deviceId = cleanOwnerUdn,
                                dayId = dayId, startTime = w.startTime, ruleDuration = w.ruleDuration,
                                startAction = 1.0, endAction = 0.0,
                                onModeOffset = w.onModeOffset, offModeOffset = w.offModeOffset,
                                countdownTime = countdownSec, endTime = w.endTime
                            )
                        }
                    }

                    HardwareRuleType.SCHEDULE_INTERVAL -> {
                        val days = normalizeDays(draft.daysOfWeek)
                        val (startSec, startOffSec, approxStart) = encodeTimePoint(
                            draft.startAnchor, draft.startHour24, draft.startMinute, draft.startOffsetMinutes
                        )
                        val (endSec, endOffSec, approxEnd) = encodeTimePoint(
                            draft.endAnchor, draft.endHour24, draft.endMinute, draft.endOffsetMinutes
                        )
                        val endTag = when (draft.endAnchor) {
                            HardwareTimeAnchor.SUNRISE -> 1
                            HardwareTimeAnchor.SUNSET -> 2
                            HardwareTimeAnchor.FIXED_TIME -> 0
                        }
                        val baseDur = computePositiveIntervalSeconds(approxStart, approxEnd)
                        val durationSec = ((baseDur / 10) * 10 + endTag).coerceIn(60, 86400)
                        val startAct = if (draft.startActionOn) 1.0 else 0.0
                        val endAct = if (draft.endActionOn) 1.0 else 0.0
                        days.forEach { dayId ->
                            insertRuleDeviceRow(
                                db = db, cols = ruleDeviceCols, ruleId = targetRuleId, deviceId = cleanOwnerUdn,
                                dayId = dayId, startTime = startSec, ruleDuration = durationSec,
                                startAction = startAct, endAction = endAct,
                                onModeOffset = startOffSec, offModeOffset = endOffSec,
                                countdownTime = -1, endTime = endSec
                            )
                        }
                    }

                    HardwareRuleType.SIMPLE_TIMER -> {
                        val days = normalizeDays(draft.daysOfWeek)
                        val (startSec, startOffSec, _) = encodeTimePoint(
                            draft.startAnchor, draft.startHour24, draft.startMinute, draft.startOffsetMinutes
                        )
                        val startAct = if (draft.startActionOn) 1.0 else 0.0
                        days.forEach { dayId ->
                            insertRuleDeviceRow(
                                db = db, cols = ruleDeviceCols, ruleId = targetRuleId, deviceId = cleanOwnerUdn,
                                dayId = dayId, startTime = startSec, ruleDuration = 0,
                                startAction = startAct, endAction = -1.0,
                                onModeOffset = startOffSec, offModeOffset = 0,
                                countdownTime = -1, endTime = -1
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
        val db = openHardenedDb(sqliteFile, readOnly = false)
        return try {
            val tables = HardwareRulesDbCodec.listTableNames(db)
            if ("RULES" !in tables || "STATE" !in HardwareRulesDbCodec.listColumnNames(db, "RULES")) return false
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
        val db = openHardenedDb(sqliteFile, readOnly = false)
        return try {
            val tables = HardwareRulesDbCodec.listTableNames(db)
            db.transaction {
                val idArg = arrayOf(ruleId.toString())
                val whereRuleId = "CAST(RuleID AS INTEGER) = CAST(? AS INTEGER)"
                val deleted = if ("RULES" in tables) delete("RULES", whereRuleId, idArg) else 0
                if ("RULEDEVICES" in tables) delete("RULEDEVICES", whereRuleId, idArg)
                if ("TARGETDEVICES" in tables) delete("TARGETDEVICES", whereRuleId, idArg)
                if ("DEVICECOMBINATION" in tables) delete("DEVICECOMBINATION", whereRuleId, idArg)
                if ("RULESNOTIFYMESSAGE" in tables) {
                    val notifyCols = HardwareRulesDbCodec.listColumnNames(db, "RULESNOTIFYMESSAGE")
                    when {
                        "NOTIFYRULEID" in notifyCols ->
                            delete("RULESNOTIFYMESSAGE", "CAST(NotifyRuleID AS INTEGER) = CAST(? AS INTEGER)", idArg)
                        "RULEID" in notifyCols -> delete("RULESNOTIFYMESSAGE", whereRuleId, idArg)
                    }
                }
                if ("SENSORNOTIFICATION" in tables) {
                    val sensorCols = HardwareRulesDbCodec.listColumnNames(db, "SENSORNOTIFICATION")
                    if ("RULEID" in sensorCols) delete("SENSORNOTIFICATION", whereRuleId, idArg)
                }
                if ("BLOCKEDRULES" in tables) {
                    val blockedCols = HardwareRulesDbCodec.listColumnNames(db, "BLOCKEDRULES")
                    if ("RULEID" in blockedCols) {
                        delete("BLOCKEDRULES", "CAST(ruleId AS INTEGER) = CAST(? AS INTEGER)", idArg)
                    }
                }
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

        val cleanCity = sanitizeSqlText(location.cityName)
        val cleanRegion = sanitizeSqlText(location.region)
        val cleanCountry = sanitizeSqlText(location.countryName)
        val cleanCountryCode = resolveCountryCode(location)
        val cleanLat = String.format(Locale.US, "%.4f", latNum)
        val cleanLon = String.format(Locale.US, "%.4f", lonNum)

        val db = openHardenedDb(sqliteFile, readOnly = false)
        try {
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
                    if ("COUNTRYCODE" in cols) put("countryCode", cleanCountryCode)
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
    fun listBackupsForDevice(backupRootDir: File, deviceKey: String): List<HardwareRulesBackupEntry> =
        HardwareRulesDbCodec.listBackupsForDevice(backupRootDir, deviceKey)

    internal fun defaultRuleName(draft: HardwareRuleDraft): String = when (draft.type) {
        HardwareRuleType.LONG_PRESS -> "Long Press ${draft.longPressAction.label}"
        HardwareRuleType.COUNTDOWN_TIMER -> "Auto-Off ${draft.countdownMinutes}m"
        HardwareRuleType.SCHEDULE_INTERVAL -> "On-Device Schedule"
        HardwareRuleType.SIMPLE_TIMER -> if (draft.startActionOn) "Timed Turn On" else "Timed Turn Off"
        else -> "Hardware Rule"
    }

    private data class CountdownWindow(
        val startTime: Int,
        val ruleDuration: Int,
        val endTime: Int,
        val onModeOffset: Int,
        val offModeOffset: Int
    )

    private fun readExistingCountdownWindow(db: SQLiteDatabase, cols: Set<String>, ruleId: Int): CountdownWindow? {
        val endExpr = if ("ENDTIME" in cols) "EndTime" else "-1"
        val onOffExpr = if ("ONMODEOFFSET" in cols) "OnModeOffset" else "0"
        val offOffExpr = if ("OFFMODEOFFSET" in cols) "OffModeOffset" else "0"
        return db.rawQuery(
            "SELECT StartTime, RuleDuration, $endExpr, $onOffExpr, $offOffExpr FROM RULEDEVICES WHERE CAST(RuleID AS INTEGER) = CAST(? AS INTEGER) LIMIT 1",
            arrayOf(ruleId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return@use null
            val st = HardwareRulesDbCodec.cursorInt(c, 0)?.takeIf { it in 0..86400 } ?: return@use null
            val dur = HardwareRulesDbCodec.cursorInt(c, 1)?.takeIf { it in 0..86400 } ?: return@use null
            val end = HardwareRulesDbCodec.cursorInt(c, 2)?.takeIf { it in 0..86400 }
                ?: if (st == 0 && dur == 86400) 86400 else ((st + dur) % 86400)
            CountdownWindow(
                startTime = st,
                ruleDuration = dur,
                endTime = end,
                onModeOffset = HardwareRulesDbCodec.cursorInt(c, 3)?.takeIf { it != -1 } ?: 0,
                offModeOffset = HardwareRulesDbCodec.cursorInt(c, 4)?.takeIf { it != -1 } ?: 0
            )
        }
    }

    private fun normalizeDays(days: List<Int>): List<Int> =
        days.filter { it in 1..7 }.distinct().sorted().ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }

    private data class EncodedTimePoint(val rawSeconds: Int, val offsetSeconds: Int, val approxClockSeconds: Int)

    private fun encodeTimePoint(anchor: HardwareTimeAnchor, hour24: Int, minute: Int, offsetMinutes: Int): EncodedTimePoint {
        val clampedOffset = offsetMinutes.coerceIn(-120, 120) * 60
        return when (anchor) {
            HardwareTimeAnchor.SUNRISE -> EncodedTimePoint(SUNRISE_PLACEHOLDER_SEC, clampedOffset, (21600 + clampedOffset).coerceIn(0, 86340))
            HardwareTimeAnchor.SUNSET -> EncodedTimePoint(SUNSET_PLACEHOLDER_SEC, clampedOffset, (64800 + clampedOffset).coerceIn(0, 86340))
            HardwareTimeAnchor.FIXED_TIME -> (hour24.coerceIn(0, 23) * 3600 + minute.coerceIn(0, 59) * 60).let { EncodedTimePoint(it, 0, it) }
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
            if ("ZBCAPABILITYSTART" in cols) put("ZBCapabilityStart", "-1")
            if ("ZBCAPABILITYEND" in cols) put("ZBCapabilityEnd", "-1")
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
        listOf("-journal", "-wal", "-shm").forEach { suffix -> runCatching { File("${sqliteFile.path}$suffix").delete() } }
    }
}
