package org.suppressingfire.wemi.network

import android.content.Context
import java.io.File
import java.util.TimeZone
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry

/**
 * Local-LAN UPnP `rules1` & `timesync1` client for authoritative Tier-2 rules capability
 * verification, safe read-only `rules.db` extraction, hardware clock checks, and armored
 * `StoreRules` upload with mandatory read-back verification.
 *
 * Designed for zero impact on bulk SSDP discovery: Tier-1 hints come directly from `/setup.xml`
 * (0 extra RTT), and this client runs lazily on demand under the caller's per-device `Mutex` and
 * `Semaphore(3)` cap.
 */
class HardwareRulesClient(
    private val appContext: Context? = null,
    socketFactoryOverride: SocketFactory? = null,
    private val portProber: suspend (ipAddress: String, preferredPort: Int, expectedIdentity: String?) -> Int? = { _, p, _ -> p }
) {
    private val wifiSocketFactory = socketFactoryOverride ?: NetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 900,
        readTimeoutMs = 4000
    )

    private fun workCacheDir(): File =
        appContext?.cacheDir?.let { File(it, "wemi_rules_work") }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "wemi_rules_work")

    private fun backupRootDir(): File =
        appContext?.filesDir?.let { File(it, "hardware_rules_backups") }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "wemi_hardware_rules_backups")

    /**
     * Authoritative Tier-2 capability check:
     * 1. `GET /rulesservice.xml` — RTOS firmwares (`WDS060`, `WSP080`, `WSP090`) and legacy 2013
     *    builds omit `FetchRules` from `<actionList>`, letting us classify them immediately.
     * 2. `POST /upnp/control/rules1#FetchRules` — confirms the switch actually serves `rules.db`
     *    metadata (including `version = 0` / empty DB).
     *
     * Never falsely marks an offline/unreachable switch as [HardwareRulesCapability.VERIFIED_UNSUPPORTED].
     */
    suspend fun probeRulesCapability(
        device: DeviceEntity,
        rulesScpdPath: String = "/rulesservice.xml",
        rulesControlPath: String = "/upnp/control/rules1"
    ): RulesCapabilityProbeResult? = withContext(Dispatchers.IO) {
        val activePort = resolveWorkingPort(device) ?: return@withContext null
        val scpdUrl = "http://${device.ipAddress}:$activePort${HardwareRulesProtocol.ensureLeadingSlash(rulesScpdPath)}"
        val scpdResp = try {
            httpClient.get(scpdUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null
        }

        val scpdActions = if (scpdResp.isSuccessful) {
            HardwareRulesProtocol.parseScpdActionNames(scpdResp.body)
        } else {
            emptySet()
        }
        if (scpdResp.isSuccessful && scpdActions.isNotEmpty()) {
            val hasFetch = scpdActions.any { it.equals("FetchRules", ignoreCase = true) }
            val hasStore = scpdActions.any { it.equals("StoreRules", ignoreCase = true) }
            if (!hasFetch || !hasStore) {
                val missing = buildList {
                    if (!hasFetch) add("FetchRules")
                    if (!hasStore) add("StoreRules")
                }.joinToString(", ")
                val reason = "Firmware rules service lacks $missing (exposes: ${scpdActions.joinToString(", ")})"
                return@withContext RulesCapabilityProbeResult(
                    capability = HardwareRulesCapability.VERIFIED_UNSUPPORTED,
                    resolvedPort = activePort,
                    scpdActions = scpdActions,
                    fetchRulesResponse = null,
                    reason = reason,
                    logEntry = SoapLogEntry(
                        timestamp = HardwareRulesProtocol.nowTime(),
                        deviceName = device.name,
                        endpoint = scpdUrl,
                        soapAction = "GET rulesservice.xml",
                        statusText = "Unsupported • Missing $missing",
                        xmlSnippet = reason,
                        isLiveLanSuccess = true
                    )
                )
            }
        }

        val controlUrl = "http://${device.ipAddress}:$activePort${HardwareRulesProtocol.ensureLeadingSlash(rulesControlPath)}"
        val fetchXml = XmlAndSkuParser.buildSoapEnvelope(XmlAndSkuParser.URN_RULES, "FetchRules")
        val fetchResp = try {
            httpClient.postXml(
                url = controlUrl,
                soapBody = fetchXml,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_RULES, "FetchRules")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null
        }

        val parsedFetch = if (fetchResp.isSuccessful) HardwareRulesProtocol.parseFetchRulesResponse(fetchResp.body) else null
        if (parsedFetch != null) {
            return@withContext RulesCapabilityProbeResult(
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
                resolvedPort = activePort,
                scpdActions = scpdActions,
                fetchRulesResponse = parsedFetch,
                reason = "Verified FetchRules (DB v${parsedFetch.ruleDbVersion})",
                logEntry = SoapLogEntry(
                    timestamp = HardwareRulesProtocol.nowTime(),
                    deviceName = device.name,
                    endpoint = controlUrl,
                    soapAction = "rules1#FetchRules",
                    statusText = "HTTP ${fetchResp.code} OK (DB v${parsedFetch.ruleDbVersion})",
                    xmlSnippet = fetchResp.body.take(220),
                    isLiveLanSuccess = true
                )
            )
        }

        val isExplicitUnsupportedFault = fetchResp.code in listOf(404, 405, 500, 501) && (
            fetchResp.body.contains("401", ignoreCase = true) ||
                fetchResp.body.contains("Invalid Action", ignoreCase = true) ||
                fetchResp.body.contains("Not Supported", ignoreCase = true) ||
                fetchResp.body.contains("Fault", ignoreCase = true) ||
                fetchResp.code == 404
            )
        val cap = if (isExplicitUnsupportedFault) {
            HardwareRulesCapability.VERIFIED_UNSUPPORTED
        } else {
            device.parsedHardwareRulesCapability
        }
        RulesCapabilityProbeResult(
            capability = cap,
            resolvedPort = activePort,
            scpdActions = scpdActions,
            fetchRulesResponse = null,
            reason = "FetchRules HTTP ${fetchResp.code}",
            logEntry = SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = controlUrl,
                soapAction = "rules1#FetchRules",
                statusText = if (isExplicitUnsupportedFault) "Unsupported (HTTP ${fetchResp.code})" else "HTTP ${fetchResp.code}",
                xmlSnippet = fetchResp.body.take(220),
                isLiveLanSuccess = false
            )
        )
    }

    /**
     * Reads out existing on-device rules from [device] in strict read-only mode, returning a
     * verified [DeviceHardwareRulesSnapshot] plus all SOAP log entries emitted during inspection.
     */
    suspend fun fetchDeviceRulesSnapshot(
        device: DeviceEntity,
        saveRawBackup: Boolean = true,
        nowUtcSecondsProvider: () -> Long = { System.currentTimeMillis() / 1000L }
    ): Pair<DeviceHardwareRulesSnapshot, List<SoapLogEntry>> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<SoapLogEntry>()
        val probe = probeRulesCapability(device)
        if (probe == null) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = device.parsedHardwareRulesCapability,
                longPressCapable = device.hardwareRulesLongPressCapable,
                dbVersion = 0,
                diagnosticMessage = "Switch unreachable on LAN (${device.ipAddress}:${device.port})",
                inspectedAt = HardwareRulesProtocol.nowTime()
            ) to logs
        }
        logs += probe.logEntry

        val isWallSwitchOrGen1Dimmer = device.udn.contains("Lightswitch", ignoreCase = true) ||
            device.udn.contains("Dimmer-1_0", ignoreCase = true) ||
            device.hardwareModel.contains("F7C030", ignoreCase = true) ||
            device.hardwareModel.contains("WLS040", ignoreCase = true) ||
            device.hardwareModel.contains("F7C059", ignoreCase = true)
        val effectiveLongPress = (device.hardwareRulesLongPressCapable || isWallSwitchOrGen1Dimmer) &&
            probe.capability == HardwareRulesCapability.VERIFIED_SUPPORTED

        if (probe.capability != HardwareRulesCapability.VERIFIED_SUPPORTED || probe.fetchRulesResponse == null) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = probe.capability,
                longPressCapable = effectiveLongPress,
                dbVersion = 0,
                diagnosticMessage = probe.reason,
                inspectedAt = HardwareRulesProtocol.nowTime()
            ) to logs
        }

        val fetchInfo = probe.fetchRulesResponse
        val deviceTime = HardwareRulesProtocol.executeGetDeviceTime(
            httpClient = httpClient,
            ipAddress = device.ipAddress,
            port = probe.resolvedPort,
            nowUtcSecondsProvider = nowUtcSecondsProvider
        )
        val rawPath = fetchInfo.ruleDbPath.trim()
        val existingBackups = HardwareRulesDbMutator.listBackupsForDevice(backupRootDir(), device.concurrencyKey())
        if (rawPath.isEmpty() || fetchInfo.ruleDbVersion <= 0) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
                longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion,
                deviceTimeInfo = deviceTime,
                availableBackups = existingBackups,
                diagnosticMessage = "No on-device rules stored yet (DB v${fetchInfo.ruleDbVersion})",
                inspectedAt = HardwareRulesProtocol.nowTime()
            ) to logs
        }

        val dbUrl = HardwareRulesProtocol.resolveRuleDbUrl(device.ipAddress, probe.resolvedPort, rawPath)
        val binaryResp = try {
            httpClient.getBytes(dbUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
                longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion,
                deviceTimeInfo = deviceTime,
                availableBackups = existingBackups,
                diagnosticMessage = "Failed downloading rules archive: ${e.message ?: e.javaClass.simpleName}",
                inspectedAt = HardwareRulesProtocol.nowTime()
            ) to logs
        }

        if (binaryResp.code == 404) {
            logs += SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = dbUrl,
                soapAction = "GET /rules.db",
                statusText = "HTTP 404 (Empty on-device DB)",
                xmlSnippet = "No rules stored yet on switch",
                isLiveLanSuccess = true
            )
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
                longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion,
                deviceTimeInfo = deviceTime,
                availableBackups = existingBackups,
                diagnosticMessage = "No on-device rules stored yet (HTTP 404)",
                inspectedAt = HardwareRulesProtocol.nowTime()
            ) to logs
        }

        if (!binaryResp.isSuccessful) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id,
                deviceName = device.name,
                deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
                longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion,
                deviceTimeInfo = deviceTime,
                availableBackups = existingBackups,
                diagnosticMessage = "Switch returned HTTP ${binaryResp.code} for rules archive",
                inspectedAt = HardwareRulesProtocol.nowTime()
            ) to logs
        }

        val unpacked = HardwareRulesDbCodec.unpackAndParseRulesZip(
            zipBytes = binaryResp.bodyBytes,
            inspectedDeviceUdn = device.udn,
            workDir = workCacheDir()
        )
        val backupFile = if (saveRawBackup && unpacked.integrityOk) {
            HardwareRulesDbCodec.saveRawZipBackup(
                backupRootDir = backupRootDir(),
                deviceKey = device.concurrencyKey(),
                dbVersion = fetchInfo.ruleDbVersion,
                rawZipBytes = binaryResp.bodyBytes
            )
        } else {
            null
        }
        val refreshedBackups = HardwareRulesDbMutator.listBackupsForDevice(backupRootDir(), device.concurrencyKey())

        logs += SoapLogEntry(
            timestamp = HardwareRulesProtocol.nowTime(),
            deviceName = device.name,
            endpoint = dbUrl,
            soapAction = "GET /rules.db",
            statusText = "HTTP ${binaryResp.code} OK (${binaryResp.bodyBytes.size}B, ${unpacked.rules.size} rule(s))",
            xmlSnippet = "entry=${unpacked.zipEntryName}, tables=${unpacked.existingTables.size}, integrity=${unpacked.integrityOk}",
            isLiveLanSuccess = unpacked.integrityOk
        )

        DeviceHardwareRulesSnapshot(
            deviceId = device.id,
            deviceName = device.name,
            deviceUdn = device.udn,
            capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
            longPressCapable = effectiveLongPress,
            dbVersion = fetchInfo.ruleDbVersion,
            zipEntryName = unpacked.zipEntryName,
            rules = unpacked.rules,
            locationInfo = unpacked.locationInfo,
            deviceTimeInfo = deviceTime,
            existingTables = unpacked.existingTables,
            integrityOk = unpacked.integrityOk,
            backupFilePath = backupFile?.absolutePath,
            availableBackups = refreshedBackups,
            diagnosticMessage = unpacked.diagnosticMessage.ifBlank {
                "Read ${unpacked.rules.size} rule(s) (DB v${fetchInfo.ruleDbVersion})"
            },
            inspectedAt = HardwareRulesProtocol.nowTime()
        ) to logs
    }

    suspend fun getDeviceTime(
        ipAddress: String,
        port: Int,
        nowUtcSecondsProvider: () -> Long = { System.currentTimeMillis() / 1000L }
    ): HardwareDeviceTimeInfo? = withContext(Dispatchers.IO) {
        HardwareRulesProtocol.executeGetDeviceTime(httpClient, ipAddress, port, nowUtcSecondsProvider)
    }

    suspend fun syncDeviceTime(
        device: DeviceEntity,
        nowUtcMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): SoapLogEntry = withContext(Dispatchers.IO) {
        val activePort = resolveWorkingPort(device) ?: device.port
        HardwareRulesProtocol.executeSyncDeviceTime(httpClient, device, activePort, nowUtcMillis, timeZone)
    }

    suspend fun storeValidatedRulesDb(
        device: DeviceEntity,
        sqliteFile: File,
        zipEntryName: String,
        currentDbVersion: Int
    ): StoreRulesResult = withContext(Dispatchers.IO) {
        val activePort = resolveWorkingPort(device) ?: device.port
        HardwareRulesProtocol.executeStoreValidatedRulesDb(
            httpClient = httpClient,
            device = device,
            activePort = activePort,
            sqliteFile = sqliteFile,
            zipEntryName = zipEntryName,
            currentDbVersion = currentDbVersion
        )
    }

    /**
     * Executes an armored live mutation on [device]'s on-device `rules.db` (probe → pre-write
     * backup → automatic clock sync if needed → [mutateDb] → anti-brick validate → `StoreRules`
     * → `FetchRules` version verification → refreshed snapshot).
     */
    suspend fun mutateAndStoreRulesDb(
        device: DeviceEntity,
        autoSyncClockIfNeeded: Boolean = true,
        nowUtcMillisProvider: () -> Long = { System.currentTimeMillis() },
        timeZone: TimeZone = TimeZone.getDefault(),
        mutateDb: (sqliteFile: File) -> Unit
    ): Pair<HardwareRulesWriteResult, List<SoapLogEntry>> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<SoapLogEntry>()
        val probe = probeRulesCapability(device)
        if (probe == null) {
            return@withContext HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = "Switch unreachable on LAN (${device.ipAddress}:${device.port})"
            ) to logs
        }
        logs += probe.logEntry
        if (probe.capability != HardwareRulesCapability.VERIFIED_SUPPORTED) {
            return@withContext HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = "Switch firmware does not support on-device rules storage (${probe.reason})"
            ) to logs
        }

        val (storeResult, clockSynced, writeLogs) = HardwareRulesWriteEngine.executeMutateAndStoreRulesDb(
            httpClient = httpClient,
            device = device,
            probe = probe,
            workDir = workCacheDir(),
            backupRootDir = backupRootDir(),
            autoSyncClockIfNeeded = autoSyncClockIfNeeded,
            nowUtcMillisProvider = nowUtcMillisProvider,
            timeZone = timeZone,
            mutateDb = mutateDb
        )
        logs += writeLogs
        if (!storeResult.success) {
            return@withContext HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = storeResult.errorInfo.ifBlank { "Unable to save rules to switch" },
                clockSynced = clockSynced
            ) to logs
        }

        val (refreshedSnap, refreshLogs) = fetchDeviceRulesSnapshot(device, saveRawBackup = false)
        logs += refreshLogs
        val clockSuffix = if (clockSynced) " • clock synced" else ""
        HardwareRulesWriteResult(
            success = true,
            snapshot = refreshedSnap,
            message = "Saved on-device rules (DB v${storeResult.verifiedVersion}$clockSuffix)",
            clockSynced = clockSynced
        ) to logs
    }

    /**
     * Restores a saved raw `/rules.db` ZIP backup onto [device] at `currentVersion + 1` via the
     * anti-brick validator and post-store `FetchRules` verification.
     */
    suspend fun restoreRulesFromBackup(
        device: DeviceEntity,
        backupFilePath: String
    ): Pair<HardwareRulesWriteResult, List<SoapLogEntry>> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<SoapLogEntry>()
        val probe = probeRulesCapability(device)
            ?: return@withContext HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = "Switch unreachable on LAN (${device.ipAddress}:${device.port})"
            ) to logs
        logs += probe.logEntry
        if (probe.capability != HardwareRulesCapability.VERIFIED_SUPPORTED) {
            return@withContext HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = "Switch firmware does not support on-device rules storage"
            ) to logs
        }

        val (storeResult, restoreLogs) = HardwareRulesWriteEngine.executeRestoreRulesBackup(
            httpClient = httpClient,
            device = device,
            probe = probe,
            backupFile = File(backupFilePath),
            workDir = workCacheDir(),
            backupRootDir = backupRootDir()
        )
        logs += restoreLogs
        if (!storeResult.success) {
            return@withContext HardwareRulesWriteResult(
                success = false,
                snapshot = null,
                message = storeResult.errorInfo.ifBlank { "Backup restore failed" }
            ) to logs
        }
        val (refreshedSnap, refreshLogs) = fetchDeviceRulesSnapshot(device, saveRawBackup = false)
        logs += refreshLogs
        HardwareRulesWriteResult(
            success = true,
            snapshot = refreshedSnap,
            message = "Restored backup to switch (DB v${storeResult.verifiedVersion})"
        ) to logs
    }

    fun listBackupsForDevice(device: DeviceEntity): List<HardwareRulesBackupEntry> =
        HardwareRulesDbMutator.listBackupsForDevice(backupRootDir(), device.concurrencyKey())

    private suspend fun resolveWorkingPort(device: DeviceEntity): Int? {
        val expectedId = LanRuntimeUtils.expectedHardwareIdentity(device.serialNumber, device.udn).ifEmpty { null }
        return portProber(device.ipAddress, device.port, expectedId)
    }
}
