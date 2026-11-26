package org.suppressingfire.wemi.network

import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry

internal data class RulesWriteEngineOutcome(
    val storeResult: StoreRulesResult,
    val clockSynced: Boolean,
    val verifiedLocalSnapshot: DeviceHardwareRulesSnapshot?,
    val logs: List<SoapLogEntry>
)

/**
 * Armored transaction engine for mutating and restoring Belkin on-device `rules.db` archives.
 *
 * Every mutation follows the strict anti-brick sequence:
 * 1. Download current `/rules.db` whenever `<ruleDbPath>` is non-empty (bootstrapping the 9-table
 *    Belkin schema only when `<ruleDbPath>` is blank or the switch returns HTTP 404).
 * 2. Save an untouched raw-ZIP backup before altering any byte (deduplicated & atomic).
 * 3. Automatically run `timesync1#TimeSync` when clock drift is detected.
 * 4. Mutate a temporary SQLite copy in place (preserving existing tables & ZIP entry name).
 * 5. Run [HardwareRulesSafetyValidator], upload via `StoreRules` (`10s` read timeout), confirm the
 *    bumped version via `FetchRules` read-back, and build a verified local snapshot fallback so a
 *    transient post-write reload drop never overwrites verified rules with an error state.
 */
internal object HardwareRulesWriteEngine {

    suspend fun orchestrateMutateAndStore(
        httpClient: LanHttpClient,
        storeHttpClient: LanHttpClient,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult?,
        workDir: File,
        backupRootDir: File,
        autoSyncClockIfNeeded: Boolean,
        nowUtcMillisProvider: () -> Long,
        timeZone: TimeZone,
        onPortResolved: ((Int) -> Unit)?,
        refreshFromSwitch: suspend (DeviceEntity) -> Pair<DeviceHardwareRulesSnapshot, List<SoapLogEntry>>,
        mutateDb: (sqliteFile: File) -> Unit
    ): Pair<HardwareRulesWriteResult, List<SoapLogEntry>> {
        val logs = mutableListOf<SoapLogEntry>()
        if (probe == null) {
            logs += probeFailureLog(device)
            return HardwareRulesWriteResult(false, null, "Switch unreachable on LAN (${device.ipAddress}:${device.port})") to logs
        }
        onPortResolved?.invoke(probe.resolvedPort)
        logs += probe.logEntry
        if (probe.capability != HardwareRulesCapability.VERIFIED_SUPPORTED) {
            return HardwareRulesWriteResult(false, null, "Switch firmware does not support on-device rules storage (${probe.reason})") to logs
        }

        val outcome = executeMutateAndStoreRulesDb(
            httpClient = httpClient, storeHttpClient = storeHttpClient, device = device, probe = probe,
            workDir = workDir, backupRootDir = backupRootDir, autoSyncClockIfNeeded = autoSyncClockIfNeeded,
            nowUtcMillisProvider = nowUtcMillisProvider, timeZone = timeZone, mutateDb = mutateDb
        )
        logs += outcome.logs
        if (!outcome.storeResult.success) {
            return HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = outcome.storeResult.errorInfo.ifBlank { "Unable to save rules to switch" },
                clockSynced = outcome.clockSynced
            ) to logs
        }
        val healedDevice = if (probe.resolvedPort != device.port) device.copy(port = probe.resolvedPort) else device
        val finalSnap = finalizeWriteSnapshot(outcome.storeResult, outcome.verifiedLocalSnapshot, { refreshFromSwitch(healedDevice) }, logs)
        val clockSuffix = if (outcome.clockSynced) " • clock synced" else ""
        return HardwareRulesWriteResult(
            success = true,
            snapshot = finalSnap,
            message = "Saved on-device rules (DB v${outcome.storeResult.verifiedVersion}$clockSuffix)",
            clockSynced = outcome.clockSynced
        ) to logs
    }

    suspend fun orchestrateRestoreBackup(
        httpClient: LanHttpClient,
        storeHttpClient: LanHttpClient,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult?,
        backupFile: File,
        workDir: File,
        backupRootDir: File,
        onPortResolved: ((Int) -> Unit)?,
        refreshFromSwitch: suspend (DeviceEntity) -> Pair<DeviceHardwareRulesSnapshot, List<SoapLogEntry>>
    ): Pair<HardwareRulesWriteResult, List<SoapLogEntry>> {
        val logs = mutableListOf<SoapLogEntry>()
        if (probe == null) {
            logs += probeFailureLog(device)
            return HardwareRulesWriteResult(false, null, "Switch unreachable on LAN (${device.ipAddress}:${device.port})") to logs
        }
        onPortResolved?.invoke(probe.resolvedPort)
        logs += probe.logEntry
        if (probe.capability != HardwareRulesCapability.VERIFIED_SUPPORTED) {
            return HardwareRulesWriteResult(false, null, "Switch firmware does not support on-device rules storage") to logs
        }

        val outcome = executeRestoreRulesBackup(
            httpClient = httpClient, storeHttpClient = storeHttpClient, device = device, probe = probe,
            backupFile = backupFile, workDir = workDir, backupRootDir = backupRootDir
        )
        logs += outcome.logs
        if (!outcome.storeResult.success) {
            return HardwareRulesWriteResult(false, null, outcome.storeResult.errorInfo.ifBlank { "Backup restore failed" }) to logs
        }
        val healedDevice = if (probe.resolvedPort != device.port) device.copy(port = probe.resolvedPort) else device
        val finalSnap = finalizeWriteSnapshot(outcome.storeResult, outcome.verifiedLocalSnapshot, { refreshFromSwitch(healedDevice) }, logs)
        return HardwareRulesWriteResult(true, finalSnap, "Restored backup to switch (DB v${outcome.storeResult.verifiedVersion})") to logs
    }

    internal fun probeFailureLog(device: DeviceEntity): SoapLogEntry = SoapLogEntry(
        timestamp = HardwareRulesProtocol.nowTime(),
        deviceName = device.name,
        endpoint = "http://${device.ipAddress}:${device.port}/rulesservice.xml",
        soapAction = "rules1#Probe",
        statusText = "[ERR] Unreachable or identity mismatch",
        xmlSnippet = "Unable to verify switch at ${device.ipAddress}:${device.port}",
        isLiveLanSuccess = false
    )

    fun executeMutateAndStoreRulesDb(
        httpClient: LanHttpClient,
        storeHttpClient: LanHttpClient,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult,
        workDir: File,
        backupRootDir: File,
        autoSyncClockIfNeeded: Boolean,
        nowUtcMillisProvider: () -> Long,
        timeZone: TimeZone,
        mutateDb: (sqliteFile: File) -> Unit
    ): RulesWriteEngineOutcome {
        val logs = mutableListOf<SoapLogEntry>()
        val fetchInfo = probe.fetchRulesResponse ?: return RulesWriteEngineOutcome(
            StoreRulesResult(false, 0, probe.reason, HardwareRulesValidationResult(false, listOf(probe.reason)), probe.logEntry),
            false, null, logs
        )

        val (workingDbFile, zipEntryName, prepError) = prepareWorkingSqliteCopy(
            httpClient, device, probe.resolvedPort, fetchInfo, workDir, backupRootDir
        )
        if (workingDbFile == null || prepError != null) {
            val msg = prepError ?: "Unable to prepare rules DB copy"
            val failLog = SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = "http://${device.ipAddress}:${probe.resolvedPort}/rules.db",
                soapAction = "rules1#StoreRules (Prep Failed)",
                statusText = "[ERR] $msg",
                xmlSnippet = msg,
                isLiveLanSuccess = false
            )
            logs += failLog
            return RulesWriteEngineOutcome(
                StoreRulesResult(false, fetchInfo.ruleDbVersion, msg, HardwareRulesValidationResult(false, listOf(msg)), failLog),
                false, null, logs
            )
        }

        return try {
            var clockSynced = false
            var timeInfo: HardwareDeviceTimeInfo? = null
            if (autoSyncClockIfNeeded) {
                val nowMs = nowUtcMillisProvider()
                timeInfo = HardwareRulesProtocol.executeGetDeviceTime(
                    httpClient, device.ipAddress, probe.resolvedPort, { nowMs / 1000L }, device.name
                ) { logs += it }
                if (timeInfo?.needsTimeSync == true) {
                    val syncLog = HardwareRulesProtocol.executeSyncDeviceTime(httpClient, device, probe.resolvedPort, nowMs, timeZone)
                    logs += syncLog
                    clockSynced = syncLog.isLiveLanSuccess
                    if (clockSynced) timeInfo = HardwareDeviceTimeInfo(nowMs / 1000L, 0L, false)
                }
            }

            val preRuleIds = readRuleIdsSnapshot(workingDbFile)
            try {
                mutateDb(workingDbFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                val errLog = SoapLogEntry(
                    timestamp = HardwareRulesProtocol.nowTime(),
                    deviceName = device.name,
                    endpoint = "http://${device.ipAddress}:${probe.resolvedPort}/upnp/control/rules1",
                    soapAction = "rules1#StoreRules (Mutation Rejected)",
                    statusText = "[ERR] $msg",
                    xmlSnippet = msg,
                    isLiveLanSuccess = false
                )
                logs += errLog
                return RulesWriteEngineOutcome(
                    StoreRulesResult(false, fetchInfo.ruleDbVersion, msg, HardwareRulesValidationResult(false, listOf(msg)), errLog),
                    clockSynced, null, logs
                )
            }

            val addedRuleIds = readRuleIdsSnapshot(workingDbFile) - preRuleIds
            val storeResult = HardwareRulesProtocol.executeStoreValidatedRulesDb(
                httpClient = httpClient, storeHttpClient = storeHttpClient, device = device,
                activePort = probe.resolvedPort, sqliteFile = workingDbFile, zipEntryName = zipEntryName,
                currentDbVersion = fetchInfo.ruleDbVersion, isRawBackupRestore = false,
                mutatedRuleIds = addedRuleIds, inspectedOwnerUdn = device.udn, actionLabelPrefix = "rules1#StoreRules"
            )
            logs += storeResult.logEntry
            val localSnap = if (storeResult.success) {
                buildVerifiedLocalSnapshot(workingDbFile, zipEntryName, device, probe, storeResult.verifiedVersion, timeInfo, backupRootDir)
            } else null
            RulesWriteEngineOutcome(storeResult, clockSynced, localSnap, logs)
        } finally {
            cleanupTempDb(workingDbFile)
        }
    }

    fun executeRestoreRulesBackup(
        httpClient: LanHttpClient,
        storeHttpClient: LanHttpClient,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult,
        backupFile: File,
        workDir: File,
        backupRootDir: File
    ): RulesWriteEngineOutcome {
        val logs = mutableListOf<SoapLogEntry>()
        val fetchInfo = probe.fetchRulesResponse ?: return RulesWriteEngineOutcome(
            StoreRulesResult(false, 0, probe.reason, HardwareRulesValidationResult(false, listOf(probe.reason)), probe.logEntry),
            false, null, logs
        )

        val (canonicalBackup, confineErr) = HardwareRulesBackupStore.resolveConfinedBackupFile(backupRootDir, backupFile)
        if (canonicalBackup == null || confineErr != null) {
            return failedRestoreOutcome(device, backupFile.name, fetchInfo.ruleDbVersion, confineErr ?: "Invalid backup", logs)
        }

        val extracted = HardwareRulesDbCodec.extractSingleSqliteEntry(canonicalBackup.readBytes())
            ?: return failedRestoreOutcome(
                device, canonicalBackup.name, fetchInfo.ruleDbVersion,
                "Backup archive is corrupted or lacks a valid SQLite 3 rules DB", logs
            )

        val livePath = fetchInfo.ruleDbPath.trim()
        if (livePath.isNotEmpty()) {
            try {
                val liveResp = httpClient.getBytes(HardwareRulesProtocol.resolveRuleDbUrl(device.ipAddress, probe.resolvedPort, livePath))
                if (liveResp.isSuccessful && liveResp.bodyBytes.isNotEmpty()) {
                    HardwareRulesBackupStore.saveRawZipBackup(backupRootDir, device.concurrencyKey(), fetchInfo.ruleDbVersion, liveResp.bodyBytes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort pre-restore snapshot
            }
        }

        workDir.mkdirs()
        val tempDb = File.createTempFile("wemi_rules_restore_", ".db", workDir)
        return try {
            tempDb.writeBytes(extracted.second)
            val result = HardwareRulesProtocol.executeStoreValidatedRulesDb(
                httpClient = httpClient, storeHttpClient = storeHttpClient, device = device,
                activePort = probe.resolvedPort, sqliteFile = tempDb, zipEntryName = extracted.first,
                currentDbVersion = fetchInfo.ruleDbVersion, isRawBackupRestore = true, actionLabelPrefix = "rules1#RestoreBackup"
            )
            logs += result.logEntry
            val localSnap = if (result.success) {
                buildVerifiedLocalSnapshot(tempDb, extracted.first, device, probe, result.verifiedVersion, null, backupRootDir)
            } else null
            RulesWriteEngineOutcome(result, false, localSnap, logs)
        } finally {
            cleanupTempDb(tempDb)
        }
    }

    private suspend fun finalizeWriteSnapshot(
        storeResult: StoreRulesResult,
        localFallback: DeviceHardwareRulesSnapshot?,
        refreshFromSwitch: suspend () -> Pair<DeviceHardwareRulesSnapshot, List<SoapLogEntry>>,
        logs: MutableList<SoapLogEntry>
    ): DeviceHardwareRulesSnapshot? {
        val (refreshedSnap, refreshLogs) = refreshFromSwitch()
        logs += refreshLogs
        val reloadTrusted = !refreshedSnap.hasInspectionError &&
            refreshedSnap.capability == HardwareRulesCapability.VERIFIED_SUPPORTED &&
            refreshedSnap.integrityOk &&
            refreshedSnap.dbVersion >= storeResult.verifiedVersion
        return if (reloadTrusted) refreshedSnap else (localFallback ?: refreshedSnap)
    }

    private fun buildVerifiedLocalSnapshot(
        sqliteFile: File,
        zipEntryName: String,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult,
        verifiedVersion: Int,
        deviceTimeInfo: HardwareDeviceTimeInfo?,
        backupRootDir: File
    ): DeviceHardwareRulesSnapshot? = runCatching {
        val parsed = HardwareRulesDbCodec.parseSqliteFileReadOnly(sqliteFile, zipEntryName, device.udn)
        DeviceHardwareRulesSnapshot(
            deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
            capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
            longPressCapable = HardwareRulesClient.computeEffectiveLongPressCapable(device, true, probe.servedLightSetupXml),
            dbVersion = verifiedVersion, zipEntryName = parsed.zipEntryName, rules = parsed.rules,
            locationInfo = parsed.locationInfo, deviceTimeInfo = deviceTimeInfo,
            existingTables = parsed.existingTables, integrityOk = parsed.integrityOk,
            availableBackups = HardwareRulesBackupStore.listBackupsForDevice(backupRootDir, device.concurrencyKey()),
            diagnosticMessage = "Saved ${parsed.rules.size} rule(s) (DB v$verifiedVersion)",
            inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = false
        )
    }.getOrNull()

    private fun readRuleIdsSnapshot(sqliteFile: File): Set<Int> = runCatching {
        HardwareRulesDbCodec.parseSqliteFileReadOnly(sqliteFile, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "")
            .rules.map { it.ruleId }.toSet()
    }.getOrDefault(emptySet())

    private fun failedRestoreOutcome(
        device: DeviceEntity, endpoint: String, currentDbVersion: Int, message: String, logs: MutableList<SoapLogEntry>
    ): RulesWriteEngineOutcome {
        val errLog = SoapLogEntry(
            timestamp = HardwareRulesProtocol.nowTime(),
            deviceName = device.name,
            endpoint = endpoint,
            soapAction = "rules1#RestoreBackup",
            statusText = "[ERR] $message",
            xmlSnippet = message,
            isLiveLanSuccess = false
        )
        logs += errLog
        return RulesWriteEngineOutcome(
            StoreRulesResult(false, currentDbVersion, message, HardwareRulesValidationResult(false, listOf(message)), errLog),
            false, null, logs
        )
    }

    private fun prepareWorkingSqliteCopy(
        httpClient: LanHttpClient,
        device: DeviceEntity,
        activePort: Int,
        fetchInfo: FetchRulesSoapResponse,
        workDir: File,
        backupRootDir: File
    ): Triple<File?, String, String?> {
        workDir.mkdirs()
        val tempDb = File.createTempFile("wemi_rules_rw_", ".db", workDir)
        return try {
            val rawPath = fetchInfo.ruleDbPath.trim()
            if (rawPath.isEmpty()) {
                HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(tempDb)
                return Triple(tempDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, null)
            }

            val dbUrl = HardwareRulesProtocol.resolveRuleDbUrl(device.ipAddress, activePort, rawPath)
            val binaryResp = try {
                httpClient.getBytes(dbUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                cleanupTempDb(tempDb)
                return Triple(null, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "Failed downloading current rules DB: ${e.message ?: e.javaClass.simpleName}")
            }

            if (binaryResp.code == 404) {
                HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(tempDb)
                return Triple(tempDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, null)
            }
            if (!binaryResp.isSuccessful) {
                cleanupTempDb(tempDb)
                return Triple(null, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "Switch returned HTTP ${binaryResp.code} reading current rules DB")
            }

            val extracted = HardwareRulesDbCodec.extractSingleSqliteEntry(binaryResp.bodyBytes)
            if (extracted == null) {
                cleanupTempDb(tempDb)
                return Triple(null, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "Current rules archive from switch is not valid SQLite 3 ZIP")
            }

            HardwareRulesBackupStore.saveRawZipBackup(backupRootDir, device.concurrencyKey(), fetchInfo.ruleDbVersion, binaryResp.bodyBytes)
            tempDb.writeBytes(extracted.second)
            Triple(tempDb, extracted.first, null)
        } catch (t: Throwable) {
            cleanupTempDb(tempDb)
            throw t
        }
    }

    private fun cleanupTempDb(tempDb: File) {
        runCatching { tempDb.delete() }
        HardwareRulesDbMutator.cleanupSqliteSidecars(tempDb)
    }
}
