package org.suppressingfire.wemi.network

import java.io.File
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry

/**
 * Armored transaction engine for mutating and restoring Belkin on-device `rules.db` archives.
 *
 * Every mutation follows the strict anti-brick sequence:
 * 1. Download current `/rules.db` (or bootstrap the 9-table Belkin schema when DB v0 / HTTP 404).
 * 2. Save an untouched raw-ZIP backup before altering any byte.
 * 3. Automatically run `timesync1#TimeSync` when clock drift is detected.
 * 4. Mutate a temporary SQLite copy in place (preserving existing tables & ZIP entry name).
 * 5. Run [HardwareRulesSafetyValidator], upload via `StoreRules`, and confirm the bumped version
 *    via `FetchRules` read-back.
 */
internal object HardwareRulesWriteEngine {

    fun executeMutateAndStoreRulesDb(
        httpClient: LanHttpClient,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult,
        workDir: File,
        backupRootDir: File,
        autoSyncClockIfNeeded: Boolean,
        nowUtcMillisProvider: () -> Long,
        timeZone: TimeZone,
        mutateDb: (sqliteFile: File) -> Unit
    ): Triple<StoreRulesResult, Boolean, List<SoapLogEntry>> {
        val logs = mutableListOf<SoapLogEntry>()
        val fetchInfo = probe.fetchRulesResponse
            ?: return Triple(
                StoreRulesResult(
                    success = false,
                    verifiedVersion = 0,
                    errorInfo = probe.reason,
                    validation = HardwareRulesValidationResult(false, listOf(probe.reason)),
                    logEntry = probe.logEntry
                ),
                false,
                logs
            )

        val (workingDbFile, zipEntryName, prepError) = prepareWorkingSqliteCopy(
            httpClient = httpClient,
            device = device,
            activePort = probe.resolvedPort,
            fetchInfo = fetchInfo,
            workDir = workDir,
            backupRootDir = backupRootDir
        )
        if (workingDbFile == null || prepError != null) {
            val msg = prepError ?: "Unable to prepare rules DB copy"
            val failLog = SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = "http://${device.ipAddress}:${probe.resolvedPort}/rules.db",
                soapAction = "rules1#StoreRules (Prep Failed)",
                statusText = msg,
                xmlSnippet = msg,
                isLiveLanSuccess = false
            )
            logs += failLog
            return Triple(
                StoreRulesResult(
                    success = false,
                    verifiedVersion = fetchInfo.ruleDbVersion,
                    errorInfo = msg,
                    validation = HardwareRulesValidationResult(false, listOf(msg)),
                    logEntry = failLog
                ),
                false,
                logs
            )
        }

        return try {
            var clockSynced = false
            if (autoSyncClockIfNeeded) {
                val nowMs = nowUtcMillisProvider()
                val timeInfo = HardwareRulesProtocol.executeGetDeviceTime(
                    httpClient = httpClient,
                    ipAddress = device.ipAddress,
                    port = probe.resolvedPort,
                    nowUtcSecondsProvider = { nowMs / 1000L }
                )
                if (timeInfo?.needsTimeSync == true) {
                    val syncLog = HardwareRulesProtocol.executeSyncDeviceTime(
                        httpClient = httpClient,
                        device = device,
                        activePort = probe.resolvedPort,
                        nowUtcMillis = nowMs,
                        timeZone = timeZone
                    )
                    logs += syncLog
                    clockSynced = syncLog.isLiveLanSuccess
                }
            }

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
                    statusText = msg,
                    xmlSnippet = msg,
                    isLiveLanSuccess = false
                )
                logs += errLog
                return Triple(
                    StoreRulesResult(
                        success = false,
                        verifiedVersion = fetchInfo.ruleDbVersion,
                        errorInfo = msg,
                        validation = HardwareRulesValidationResult(false, listOf(msg)),
                        logEntry = errLog
                    ),
                    clockSynced,
                    logs
                )
            }

            val storeResult = HardwareRulesProtocol.executeStoreValidatedRulesDb(
                httpClient = httpClient,
                device = device,
                activePort = probe.resolvedPort,
                sqliteFile = workingDbFile,
                zipEntryName = zipEntryName,
                currentDbVersion = fetchInfo.ruleDbVersion
            )
            logs += storeResult.logEntry
            Triple(storeResult, clockSynced, logs)
        } finally {
            runCatching { workingDbFile.delete() }
            HardwareRulesDbMutator.cleanupSqliteSidecars(workingDbFile)
        }
    }

    /**
     * Restores a saved raw `/rules.db` ZIP backup onto [device] at `currentDbVersion + 1`,
     * still enforcing [HardwareRulesSafetyValidator] and post-store `FetchRules` verification.
     */
    fun executeRestoreRulesBackup(
        httpClient: LanHttpClient,
        device: DeviceEntity,
        probe: RulesCapabilityProbeResult,
        backupFile: File,
        workDir: File,
        backupRootDir: File
    ): Pair<StoreRulesResult, List<SoapLogEntry>> {
        val logs = mutableListOf<SoapLogEntry>()
        val fetchInfo = probe.fetchRulesResponse
            ?: return StoreRulesResult(
                success = false,
                verifiedVersion = 0,
                errorInfo = probe.reason,
                validation = HardwareRulesValidationResult(false, listOf(probe.reason)),
                logEntry = probe.logEntry
            ) to logs

        if (!backupFile.isFile || backupFile.length() <= 0L) {
            return failedRestoreResult(device, backupFile.name, fetchInfo.ruleDbVersion, "Selected backup file is missing or empty", logs)
        }

        val extracted = HardwareRulesDbCodec.extractSingleSqliteEntry(backupFile.readBytes())
            ?: return failedRestoreResult(
                device,
                backupFile.name,
                fetchInfo.ruleDbVersion,
                "Backup archive is corrupted or lacks a valid SQLite 3 rules DB",
                logs
            )

        // Snapshot current live DB before overwriting with the backup (best effort).
        val livePath = fetchInfo.ruleDbPath.trim()
        if (livePath.isNotEmpty() && fetchInfo.ruleDbVersion > 0) {
            runCatching {
                val liveResp = httpClient.getBytes(
                    HardwareRulesProtocol.resolveRuleDbUrl(device.ipAddress, probe.resolvedPort, livePath)
                )
                if (liveResp.isSuccessful && liveResp.bodyBytes.isNotEmpty()) {
                    HardwareRulesDbCodec.saveRawZipBackup(
                        backupRootDir = backupRootDir,
                        deviceKey = device.concurrencyKey(),
                        dbVersion = fetchInfo.ruleDbVersion,
                        rawZipBytes = liveResp.bodyBytes
                    )
                }
            }
        }

        workDir.mkdirs()
        val tempDb = File.createTempFile("wemi_rules_restore_", ".db", workDir)
        return try {
            tempDb.writeBytes(extracted.second)
            val result = HardwareRulesProtocol.executeStoreValidatedRulesDb(
                httpClient = httpClient,
                device = device,
                activePort = probe.resolvedPort,
                sqliteFile = tempDb,
                zipEntryName = extracted.first,
                currentDbVersion = fetchInfo.ruleDbVersion
            )
            logs += result.logEntry
            result to logs
        } finally {
            runCatching { tempDb.delete() }
            HardwareRulesDbMutator.cleanupSqliteSidecars(tempDb)
        }
    }

    private fun failedRestoreResult(
        device: DeviceEntity,
        endpoint: String,
        currentDbVersion: Int,
        message: String,
        logs: MutableList<SoapLogEntry>
    ): Pair<StoreRulesResult, List<SoapLogEntry>> {
        val errLog = SoapLogEntry(
            timestamp = HardwareRulesProtocol.nowTime(),
            deviceName = device.name,
            endpoint = endpoint,
            soapAction = "rules1#RestoreBackup",
            statusText = message,
            xmlSnippet = message,
            isLiveLanSuccess = false
        )
        logs += errLog
        return StoreRulesResult(
            success = false,
            verifiedVersion = currentDbVersion,
            errorInfo = message,
            validation = HardwareRulesValidationResult(false, listOf(message)),
            logEntry = errLog
        ) to logs
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
        val rawPath = fetchInfo.ruleDbPath.trim()
        if (rawPath.isEmpty() || fetchInfo.ruleDbVersion <= 0) {
            HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(tempDb)
            return Triple(tempDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, null)
        }

        val dbUrl = HardwareRulesProtocol.resolveRuleDbUrl(device.ipAddress, activePort, rawPath)
        val binaryResp = try {
            httpClient.getBytes(dbUrl)
        } catch (e: CancellationException) {
            runCatching { tempDb.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { tempDb.delete() }
            return Triple(null, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "Failed downloading current rules DB: ${e.message ?: e.javaClass.simpleName}")
        }

        if (binaryResp.code == 404) {
            HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(tempDb)
            return Triple(tempDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, null)
        }
        if (!binaryResp.isSuccessful) {
            runCatching { tempDb.delete() }
            return Triple(null, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "Switch returned HTTP ${binaryResp.code} reading current rules DB")
        }

        val extracted = HardwareRulesDbCodec.extractSingleSqliteEntry(binaryResp.bodyBytes)
        if (extracted == null) {
            runCatching { tempDb.delete() }
            return Triple(null, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "Current rules archive from switch is not valid SQLite 3 ZIP")
        }

        HardwareRulesDbCodec.saveRawZipBackup(
            backupRootDir = backupRootDir,
            deviceKey = device.concurrencyKey(),
            dbVersion = fetchInfo.ruleDbVersion,
            rawZipBytes = binaryResp.bodyBytes
        )
        tempDb.writeBytes(extracted.second)
        return Triple(tempDb, extracted.first, null)
    }
}
