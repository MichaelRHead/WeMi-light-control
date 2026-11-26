package org.suppressingfire.wemi.network

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class UnpackedRulesDbResult(
    val zipEntryName: String,
    val rules: List<HardwareRule>,
    val locationInfo: HardwareLocationInfo?,
    val existingTables: Set<String>,
    val integrityOk: Boolean,
    val diagnosticMessage: String = ""
)

/**
 * Zero-dependency SQLite-in-ZIP reader, packer, and raw-ZIP backup manager for Belkin smart-switch
 * on-device `rules.db` archives (`temppluginRules.db`). Pre-upload anti-brick validation and schema
 * bootstrap live in [HardwareRulesSafetyValidator].
 */
object HardwareRulesDbCodec {

    const val DEFAULT_ZIP_ENTRY_NAME = "temppluginRules.db"
    const val MAX_ZIP_BYTES = 2 * 1024 * 1024
    const val MAX_UNZIPPED_READ_BYTES = 4 * 1024 * 1024

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /**
     * Unpacks a switch's downloaded `/rules.db` ZIP bytes into a temporary file, validates its
     * ZIP/SQLite structure, and parses all native rules, target devices, sensors, and location
     * metadata in strict read-only mode.
     */
    internal fun unpackAndParseRulesZip(
        zipBytes: ByteArray,
        inspectedDeviceUdn: String,
        workDir: File
    ): UnpackedRulesDbResult {
        if (zipBytes.isEmpty()) {
            return UnpackedRulesDbResult(
                zipEntryName = DEFAULT_ZIP_ENTRY_NAME,
                rules = emptyList(),
                locationInfo = null,
                existingTables = emptySet(),
                integrityOk = false,
                diagnosticMessage = "Empty rules archive"
            )
        }
        if (zipBytes.size > MAX_ZIP_BYTES) {
            return UnpackedRulesDbResult(
                zipEntryName = DEFAULT_ZIP_ENTRY_NAME,
                rules = emptyList(),
                locationInfo = null,
                existingTables = emptySet(),
                integrityOk = false,
                diagnosticMessage = "Archive exceeds safe 2 MiB limit (${zipBytes.size} bytes)"
            )
        }

        val extracted = extractSingleSqliteEntry(zipBytes)
            ?: return UnpackedRulesDbResult(
                zipEntryName = DEFAULT_ZIP_ENTRY_NAME,
                rules = emptyList(),
                locationInfo = null,
                existingTables = emptySet(),
                integrityOk = false,
                diagnosticMessage = "Archive is not a valid rules.db ZIP containing SQLite 3 data"
            )

        workDir.mkdirs()
        val tempDbFile = File.createTempFile("wemi_rules_ro_", ".db", workDir)
        return try {
            tempDbFile.writeBytes(extracted.second)
            parseSqliteFileReadOnly(
                sqliteFile = tempDbFile,
                zipEntryName = extracted.first,
                inspectedDeviceUdn = inspectedDeviceUdn
            )
        } catch (e: Exception) {
            UnpackedRulesDbResult(
                zipEntryName = extracted.first,
                rules = emptyList(),
                locationInfo = null,
                existingTables = emptySet(),
                integrityOk = false,
                diagnosticMessage = "Unable to read rules SQLite DB: ${e.message ?: e.javaClass.simpleName}"
            )
        } finally {
            runCatching { tempDbFile.delete() }
            HardwareRulesDbMutator.cleanupSqliteSidecars(tempDbFile)
        }
    }

    internal fun extractSingleSqliteEntry(zipBytes: ByteArray): Pair<String, ByteArray>? {
        if (zipBytes.size < 22 || zipBytes.size > MAX_ZIP_BYTES) return null
        return runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var scannedEntries = 0
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null && entry.isDirectory) {
                    scannedEntries++
                    if (scannedEntries >= HardwareRulesSchemaGuard.MAX_ZIP_ENTRIES) return null
                    entry = zis.nextEntry
                }
                val targetEntry = entry ?: return null
                val cleanName = targetEntry.name.trim()
                if (cleanName.isEmpty() || cleanName.contains("..") || cleanName.contains('/') || cleanName.contains('\\')) {
                    return null
                }
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = zis.read(buf)
                    if (n == -1) break
                    total += n
                    if (total > MAX_UNZIPPED_READ_BYTES) return null
                    out.write(buf, 0, n)
                }
                val dbBytes = out.toByteArray()
                if (!hasValidSqliteHeader(dbBytes)) return null
                cleanName to dbBytes
            }
        }.getOrNull()
    }

    fun hasValidSqliteHeader(bytes: ByteArray): Boolean {
        if (bytes.size < SQLITE_MAGIC.size) return false
        for (i in SQLITE_MAGIC.indices) {
            if (bytes[i] != SQLITE_MAGIC[i]) return false
        }
        return true
    }

    internal fun parseSqliteFileReadOnly(
        sqliteFile: File,
        zipEntryName: String,
        inspectedDeviceUdn: String
    ): UnpackedRulesDbResult {
        val db = SQLiteDatabase.openDatabase(
            sqliteFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        return try {
            HardwareRulesSchemaGuard.hardenAndValidateSchema(db, readOnly = true, throwOnViolation = true)
            val integrity = runSingleTextQuery(db, "PRAGMA integrity_check").orEmpty()
            val integrityOk = integrity.equals("ok", ignoreCase = true)
            val tables = listTableNames(db)
            if ("RULES" !in tables) {
                return UnpackedRulesDbResult(
                    zipEntryName = zipEntryName,
                    rules = emptyList(),
                    locationInfo = readLocationInfo(db, tables),
                    existingTables = tables,
                    integrityOk = integrityOk,
                    diagnosticMessage = if (tables.isEmpty()) "Empty database" else "No RULES table present"
                )
            }

            val targetsByRuleId = readTargetDevices(db, tables)
            val sensorsByRuleId = readSensorCombinations(db, tables)
            val ruleDeviceRowsByRuleId = readRuleDevices(db, tables)
            val locationInfo = readLocationInfo(db, tables)
            val cleanInspectedUdn = LanRuntimeUtils.canonicalHardwareUdn(inspectedDeviceUdn)

            val rulesCols = listColumnNames(db, "RULES")
            val hasRuleOrder = "RULEORDER" in rulesCols
            val orderExpr = if (hasRuleOrder) "RuleOrder" else "0"
            val stateExpr = if ("STATE" in rulesCols) "State" else "'1'"
            val orderClause = if (hasRuleOrder) {
                "ORDER BY RuleOrder ASC, CAST(RuleID AS INTEGER) ASC"
            } else {
                "ORDER BY CAST(RuleID AS INTEGER) ASC"
            }

            val parsedRules = mutableListOf<HardwareRule>()
            db.rawQuery("SELECT RuleID, Name, Type, $orderExpr, $stateExpr FROM RULES $orderClause LIMIT ${HardwareRulesSchemaGuard.MAX_RULES_ROWS}", null)
                .use { cursor ->
                    while (cursor.moveToNext()) {
                        val ruleId = cursorInt(cursor, 0) ?: continue
                        val name = cursorString(cursor, 1).ifBlank { "Rule #$ruleId" }
                        val rawType = cursorString(cursor, 2)
                        val ruleOrder = cursorInt(cursor, 3) ?: 0
                        val isEnabled = cursorString(cursor, 4) == "1"
                        val rows = ruleDeviceRowsByRuleId[ruleId].orEmpty()
                        val groupedByDevice = if (rows.isEmpty()) {
                            mapOf("" to emptyList())
                        } else {
                            rows.groupBy { row ->
                                LanRuntimeUtils.canonicalHardwareUdn(row.deviceId)
                                    .ifBlank { row.deviceId.trim() }
                                    .lowercase(Locale.US)
                            }
                        }
                        val isSharedMultiSwitch = groupedByDevice.size > 1
                        for ((_, deviceRows) in groupedByDevice) {
                            val first = deviceRows.firstOrNull()
                            val rawOwner = first?.deviceId.orEmpty()
                            val cleanOwner = LanRuntimeUtils.canonicalHardwareUdn(rawOwner).ifBlank { rawOwner.trim() }
                            val isOwnedByInspectedSwitch = cleanInspectedUdn.isBlank() ||
                                cleanOwner.isBlank() ||
                                cleanOwner.equals(cleanInspectedUdn, ignoreCase = true)
                            val appliesToSelf = isOwnedByInspectedSwitch ||
                                targetsByRuleId[ruleId].orEmpty().any {
                                    LanRuntimeUtils.canonicalHardwareUdn(it).equals(cleanInspectedUdn, ignoreCase = true)
                                }
                            val st = first?.startTime
                            val dur = first?.ruleDuration
                            val startPoint = st?.takeIf { it >= 0 }?.let {
                                HardwareRuleTimePoint(it, first.onModeOffset?.takeIf { o -> o != -1 } ?: 0)
                            }
                            val rawEnd = first?.endTime?.takeIf { it >= 0 }
                                ?: if (st != null && dur != null && st >= 0 && dur > 0) {
                                    when (val durTag = dur % 10) {
                                        1 -> 21601
                                        2 -> 64802
                                        else -> {
                                            val baseStart = st - (st % 10)
                                            val baseDur = dur - durTag
                                            (baseStart + baseDur) % 86400
                                        }
                                    }
                                } else {
                                    null
                                }
                            val endPoint = rawEnd?.let {
                                HardwareRuleTimePoint(it, first?.offModeOffset?.takeIf { o -> o != -1 } ?: 0)
                            }
                            parsedRules += HardwareRule(
                                ruleId = ruleId,
                                name = name,
                                type = HardwareRuleType.fromFirmwareType(rawType),
                                rawFirmwareType = rawType,
                                isEnabled = isEnabled,
                                ruleOrder = ruleOrder,
                                ownerDeviceUdn = cleanOwner,
                                daysOfWeek = deviceRows.mapNotNull { it.dayId }.distinct(),
                                startTime = startPoint,
                                endTime = endPoint,
                                ruleDurationSeconds = first?.ruleDuration?.coerceAtLeast(0) ?: 0,
                                startAction = first?.startAction,
                                endAction = first?.endAction,
                                countdownSeconds = first?.countdownTime?.coerceAtLeast(0) ?: 0,
                                sensorDurationSeconds = first?.sensorDuration?.coerceAtLeast(0) ?: 0,
                                targetDeviceUdns = targetsByRuleId[ruleId].orEmpty(),
                                sensorDeviceUdns = sensorsByRuleId[ruleId].orEmpty(),
                                appliesToInspectedSwitch = appliesToSelf,
                                isOwnedByInspectedSwitch = isOwnedByInspectedSwitch,
                                isSharedMultiSwitch = isSharedMultiSwitch
                            )
                        }
                    }
                }

            UnpackedRulesDbResult(
                zipEntryName = zipEntryName,
                rules = parsedRules,
                locationInfo = locationInfo,
                existingTables = tables,
                integrityOk = integrityOk
            )
        } finally {
            runCatching { db.close() }
        }
    }

    fun packFileToZipBytes(sqliteFile: File, zipEntryName: String = DEFAULT_ZIP_ENTRY_NAME): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry(zipEntryName))
            zos.write(sqliteFile.readBytes())
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    /**
     * Saves an untouched raw `/rules.db` ZIP snapshot before inspection/mutation, deduplicating
     * identical consecutive snapshots and atomically retaining the newest [maxRetainedPerDevice]
     * backups per switch so a known-good state can always be recovered.
     */
    fun saveRawZipBackup(
        backupRootDir: File,
        deviceKey: String,
        dbVersion: Int,
        rawZipBytes: ByteArray,
        maxRetainedPerDevice: Int = 5
    ): File? = HardwareRulesBackupStore.saveRawZipBackup(
        backupRootDir = backupRootDir,
        deviceKey = deviceKey,
        dbVersion = dbVersion,
        rawZipBytes = rawZipBytes,
        maxRetainedPerDevice = maxRetainedPerDevice
    )

    /**
     * Lists retained raw `/rules.db` ZIP backups for [deviceKey], deterministically newest first.
     */
    fun listBackupsForDevice(backupRootDir: File, deviceKey: String): List<HardwareRulesBackupEntry> =
        HardwareRulesBackupStore.listBackupsForDevice(backupRootDir, deviceKey)

    private data class RawRuleDeviceRow(
        val ruleId: Int,
        val deviceId: String,
        val dayId: Int?,
        val startTime: Int?,
        val ruleDuration: Int?,
        val startAction: Double?,
        val endAction: Double?,
        val sensorDuration: Int?,
        val onModeOffset: Int?,
        val offModeOffset: Int?,
        val countdownTime: Int?,
        val endTime: Int?
    )

    private fun readRuleDevices(db: SQLiteDatabase, tables: Set<String>): Map<Int, List<RawRuleDeviceRow>> {
        if ("RULEDEVICES" !in tables) return emptyMap()
        val cols = listColumnNames(db, "RULEDEVICES")
        fun colOrNull(name: String, fallback: String = "NULL"): String = if (name in cols) name else fallback
        val sql = """
            SELECT RuleID, DeviceID, DayID, StartTime, RuleDuration,
                   ${colOrNull("STARTACTION", "NULL")},
                   ${colOrNull("ENDACTION", "NULL")},
                   ${colOrNull("SENSORDURATION", "-1")},
                   ${colOrNull("ONMODEOFFSET", "-1")},
                   ${colOrNull("OFFMODEOFFSET", "-1")},
                   ${colOrNull("COUNTDOWNTIME", "-1")},
                   ${colOrNull("ENDTIME", "-1")}
            FROM RULEDEVICES LIMIT ${HardwareRulesSchemaGuard.MAX_RULE_DEVICES_ROWS}
        """.trimIndent()
        val map = LinkedHashMap<Int, MutableList<RawRuleDeviceRow>>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val ruleId = cursorInt(c, 0) ?: continue
                map.getOrPut(ruleId) { mutableListOf() }.add(
                    RawRuleDeviceRow(
                        ruleId = ruleId,
                        deviceId = cursorString(c, 1),
                        dayId = cursorInt(c, 2),
                        startTime = cursorInt(c, 3),
                        ruleDuration = cursorInt(c, 4),
                        startAction = cursorDouble(c, 5),
                        endAction = cursorDouble(c, 6),
                        sensorDuration = cursorInt(c, 7),
                        onModeOffset = cursorInt(c, 8),
                        offModeOffset = cursorInt(c, 9),
                        countdownTime = cursorInt(c, 10),
                        endTime = cursorInt(c, 11)
                    )
                )
            }
        }
        return map
    }

    private fun readTargetDevices(db: SQLiteDatabase, tables: Set<String>): Map<Int, List<String>> {
        if ("TARGETDEVICES" !in tables) return emptyMap()
        val cols = listColumnNames(db, "TARGETDEVICES")
        if ("RULEID" !in cols || "DEVICEID" !in cols) return emptyMap()
        val orderParts = buildList {
            if ("DEVICEINDEX" in cols) add("DeviceIndex ASC")
            if ("TARGETDEVICESPK" in cols) add("TargetDevicesPK ASC")
        }
        val orderClause = if (orderParts.isNotEmpty()) " ORDER BY ${orderParts.joinToString(", ")}" else ""
        val map = LinkedHashMap<Int, MutableList<String>>()
        db.rawQuery("SELECT RuleID, DeviceID FROM TARGETDEVICES$orderClause LIMIT ${HardwareRulesSchemaGuard.MAX_TARGET_DEVICES_ROWS}", null)
            .use { c ->
                while (c.moveToNext()) {
                    val ruleId = cursorInt(c, 0) ?: continue
                    val target = cursorString(c, 1)
                    if (target.isNotBlank()) map.getOrPut(ruleId) { mutableListOf() }.add(target)
                }
            }
        return map
    }

    private fun readSensorCombinations(db: SQLiteDatabase, tables: Set<String>): Map<Int, List<String>> {
        if ("DEVICECOMBINATION" !in tables) return emptyMap()
        val cols = listColumnNames(db, "DEVICECOMBINATION")
        if ("RULEID" !in cols || "SENSORID" !in cols) return emptyMap()
        val map = LinkedHashMap<Int, MutableList<String>>()
        db.rawQuery("SELECT RuleID, SensorID FROM DEVICECOMBINATION LIMIT ${HardwareRulesSchemaGuard.MAX_DEVICE_COMBINATION_ROWS}", null).use { c ->
            while (c.moveToNext()) {
                val ruleId = cursorInt(c, 0) ?: continue
                val sensor = cursorString(c, 1)
                if (sensor.isNotBlank()) map.getOrPut(ruleId) { mutableListOf() }.add(sensor)
            }
        }
        return map
    }

    private fun readLocationInfo(db: SQLiteDatabase, tables: Set<String>): HardwareLocationInfo? {
        if ("LOCATIONINFO" !in tables) return null
        val cols = listColumnNames(db, "LOCATIONINFO")
        fun colOrEmpty(name: String): String = if (name.uppercase(Locale.US) in cols) name else "''"
        val sql = """
            SELECT ${colOrEmpty("cityName")}, ${colOrEmpty("region")}, ${colOrEmpty("countryName")},
                   ${colOrEmpty("latitude")}, ${colOrEmpty("longitude")}, ${colOrEmpty("countryCode")}
            FROM LOCATIONINFO LIMIT 1
        """.trimIndent()
        return db.rawQuery(sql, null).use { c ->
            if (!c.moveToFirst()) return@use null
            val info = HardwareLocationInfo(
                cityName = cursorString(c, 0),
                region = cursorString(c, 1),
                countryName = cursorString(c, 2),
                latitude = cursorString(c, 3),
                longitude = cursorString(c, 4),
                countryCode = cursorString(c, 5)
            )
            info.takeIf { it.cityName.isNotBlank() || it.latitude.isNotBlank() || it.longitude.isNotBlank() }
        }
    }

    internal fun listTableNames(db: SQLiteDatabase): Set<String> {
        val names = LinkedHashSet<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
            while (c.moveToNext()) {
                cursorString(c, 0).takeIf { it.isNotBlank() }?.let { names.add(it.uppercase(Locale.US)) }
            }
        }
        return names
    }

    internal fun listColumnNames(db: SQLiteDatabase, tableName: String): Set<String> {
        val safeTable = HardwareRulesSchemaGuard.validateTableIdentifier(tableName)
        val cols = LinkedHashSet<String>()
        db.rawQuery("PRAGMA table_info($safeTable)", null).use { c ->
            while (c.moveToNext()) {
                cursorString(c, 1).takeIf { it.isNotBlank() }?.let { cols.add(it.uppercase(Locale.US)) }
            }
        }
        return cols
    }

    internal fun runSingleTextQuery(db: SQLiteDatabase, sql: String): String? =
        db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) cursorString(c, 0) else null }

    internal fun cursorString(cursor: Cursor, index: Int): String =
        if (cursor.isNull(index)) "" else runCatching { cursor.getString(index).orEmpty().trim() }.getOrDefault("")

    internal fun cursorInt(cursor: Cursor, index: Int): Int? =
        if (cursor.isNull(index)) null else runCatching { cursor.getString(index)?.trim()?.toDoubleOrNull()?.toInt() }.getOrNull()

    internal fun cursorDouble(cursor: Cursor, index: Int): Double? =
        if (cursor.isNull(index)) null else runCatching { cursor.getString(index)?.trim()?.toDoubleOrNull() }.getOrNull()
}
