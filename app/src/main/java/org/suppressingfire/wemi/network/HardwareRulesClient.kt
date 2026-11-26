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
    private val portProber: (suspend (ipAddress: String, preferredPort: Int, expectedIdentity: String?) -> Int?)? = null
) {
    private val wifiSocketFactory = socketFactoryOverride ?: NetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 900,
        readTimeoutMs = 4000
    )

    private val storeHttpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 900,
        readTimeoutMs = 10_000
    )

    private fun workCacheDir(): File =
        appContext?.cacheDir?.let { File(it, "wemi_rules_work") }
            ?: File(System.getProperty("java.io.tmpdir") ?: ".", "wemi_rules_work")

    private fun backupRootDir(): File = HardwareRulesBackupStore.resolveBackupRootDir(appContext)

    /**
     * Authoritative Tier-2 capability check (`GET /rulesservice.xml` + `POST rules1#FetchRules`).
     * Never falsely marks an offline switch or generic HTTP 500 fault as `VERIFIED_UNSUPPORTED`.
     */
    suspend fun probeRulesCapability(
        device: DeviceEntity,
        rulesScpdPath: String = "/rulesservice.xml",
        rulesControlPath: String = "/upnp/control/rules1"
    ): RulesCapabilityProbeResult? = withContext(Dispatchers.IO) {
        val endpoint = HardwareRulesProtocol.resolveWorkingEndpoint(httpClient, device, portProber)
            ?: return@withContext null
        val activePort = endpoint.port
        val scpdUrl = "http://${device.ipAddress}:$activePort${HardwareRulesProtocol.ensureLeadingSlash(rulesScpdPath)}"
        val scpdResp = try {
            httpClient.get(scpdUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null
        }

        val scpdActions = if (scpdResp.isSuccessful) HardwareRulesProtocol.parseScpdActionNames(scpdResp.body) else emptySet()
        val hasFetch = scpdActions.any { it.equals("FetchRules", ignoreCase = true) }
        val hasStore = scpdActions.any { it.equals("StoreRules", ignoreCase = true) }
        if (scpdResp.isSuccessful && scpdActions.isNotEmpty() && (!hasFetch || !hasStore)) {
            val missing = buildList { if (!hasFetch) add("FetchRules"); if (!hasStore) add("StoreRules") }.joinToString(", ")
            val reason = "Firmware rules service lacks $missing (exposes: ${scpdActions.joinToString(", ")})"
            return@withContext RulesCapabilityProbeResult(
                capability = HardwareRulesCapability.VERIFIED_UNSUPPORTED,
                resolvedPort = activePort, scpdActions = scpdActions, fetchRulesResponse = null, reason = reason,
                logEntry = SoapLogEntry(
                    timestamp = HardwareRulesProtocol.nowTime(),
                    deviceName = device.name,
                    endpoint = scpdUrl,
                    soapAction = "GET rulesservice.xml",
                    statusText = "Unsupported • Missing $missing",
                    xmlSnippet = reason,
                    isLiveLanSuccess = true
                ),
                servedLightSetupXml = endpoint.servedLightSetupXml
            )
        }

        val controlUrl = "http://${device.ipAddress}:$activePort${HardwareRulesProtocol.ensureLeadingSlash(rulesControlPath)}"
        val fetchXml = XmlAndSkuParser.buildSoapEnvelope(XmlAndSkuParser.URN_RULES, "FetchRules")
        val fetchResp = try {
            httpClient.postXml(controlUrl, fetchXml, XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_RULES, "FetchRules"))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withContext null
        }

        val parsedFetch = if (fetchResp.isSuccessful) HardwareRulesProtocol.parseFetchRulesResponse(fetchResp.body) else null
        if (parsedFetch != null) {
            return@withContext RulesCapabilityProbeResult(
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED,
                resolvedPort = activePort, scpdActions = scpdActions, fetchRulesResponse = parsedFetch,
                reason = "Verified FetchRules (DB v${parsedFetch.ruleDbVersion})",
                logEntry = SoapLogEntry(
                    timestamp = HardwareRulesProtocol.nowTime(),
                    deviceName = device.name,
                    endpoint = controlUrl,
                    soapAction = "rules1#FetchRules",
                    statusText = "HTTP ${fetchResp.code} OK (DB v${parsedFetch.ruleDbVersion})",
                    xmlSnippet = fetchResp.body.take(220),
                    isLiveLanSuccess = true
                ),
                servedLightSetupXml = endpoint.servedLightSetupXml
            )
        }

        val hasUnsupportedUpnpFault = UPNP_ERR_401_REGEX.containsMatchIn(fetchResp.body) ||
            fetchResp.body.contains("Invalid Action", ignoreCase = true) ||
            fetchResp.body.contains("Not Supported", ignoreCase = true)
        val isExplicitUnsupportedFault = fetchResp.code in listOf(404, 405, 501) ||
            (fetchResp.code == 500 && hasUnsupportedUpnpFault)
        val cap = if (isExplicitUnsupportedFault) HardwareRulesCapability.VERIFIED_UNSUPPORTED else device.parsedHardwareRulesCapability
        RulesCapabilityProbeResult(
            capability = cap, resolvedPort = activePort, scpdActions = scpdActions, fetchRulesResponse = null,
            reason = "FetchRules HTTP ${fetchResp.code}",
            logEntry = SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = controlUrl,
                soapAction = "rules1#FetchRules",
                statusText = if (isExplicitUnsupportedFault) "Unsupported (HTTP ${fetchResp.code})" else "[ERR] HTTP ${fetchResp.code}",
                xmlSnippet = fetchResp.body.take(220),
                isLiveLanSuccess = false
            ),
            servedLightSetupXml = endpoint.servedLightSetupXml
        )
    }

    /** Fetches the switch's canonical `<UDN>` from `/setup.xml` alongside its verified port. */
    suspend fun fetchCanonicalDeviceUdn(device: DeviceEntity): Pair<String, Int>? = withContext(Dispatchers.IO) {
        val endpoint = HardwareRulesProtocol.resolveWorkingEndpoint(httpClient, device, portProber)
            ?: return@withContext null
        val udn = endpoint.setupXmlUdn.ifBlank {
            HardwareRulesProtocol.fetchCanonicalUdnOnPort(httpClient, device.ipAddress, endpoint.port).orEmpty()
        }
        if (udn.isNotBlank()) udn to endpoint.port else null
    }

    /**
     * Reads out existing on-device rules from [device] in strict read-only mode, returning a
     * verified [DeviceHardwareRulesSnapshot] plus all SOAP log entries emitted during inspection.
     */
    suspend fun fetchDeviceRulesSnapshot(
        device: DeviceEntity,
        saveRawBackup: Boolean = true,
        nowUtcSecondsProvider: () -> Long = { System.currentTimeMillis() / 1000L },
        onPortResolved: ((Int) -> Unit)? = null
    ): Pair<DeviceHardwareRulesSnapshot, List<SoapLogEntry>> = withContext(Dispatchers.IO) {
        val logs = mutableListOf<SoapLogEntry>()
        val probe = probeRulesCapability(device)
        if (probe == null) {
            logs += HardwareRulesWriteEngine.probeFailureLog(device)
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
                capability = device.parsedHardwareRulesCapability,
                longPressCapable = device.hardwareRulesLongPressCapable,
                dbVersion = 0, availableBackups = listBackupsForDevice(device),
                diagnosticMessage = "Switch unreachable on LAN (${device.ipAddress}:${device.port})",
                inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = true
            ) to logs
        }
        onPortResolved?.invoke(probe.resolvedPort)
        logs += probe.logEntry

        val effectiveLongPress = computeEffectiveLongPressCapable(
            device = device,
            verifiedSupported = probe.capability == HardwareRulesCapability.VERIFIED_SUPPORTED,
            servedLightSetupXml = probe.servedLightSetupXml
        )

        if (probe.capability != HardwareRulesCapability.VERIFIED_SUPPORTED || probe.fetchRulesResponse == null) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
                capability = probe.capability, longPressCapable = effectiveLongPress, dbVersion = 0,
                availableBackups = listBackupsForDevice(device), diagnosticMessage = probe.reason,
                inspectedAt = HardwareRulesProtocol.nowTime(),
                hasInspectionError = probe.capability != HardwareRulesCapability.VERIFIED_UNSUPPORTED
            ) to logs
        }

        val fetchInfo = probe.fetchRulesResponse
        val deviceTime = HardwareRulesProtocol.executeGetDeviceTime(
            httpClient, device.ipAddress, probe.resolvedPort, nowUtcSecondsProvider, device.name
        ) { logs += it }
        val rawPath = fetchInfo.ruleDbPath.trim()
        val existingBackups = listBackupsForDevice(device)
        if (rawPath.isEmpty()) {
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion, deviceTimeInfo = deviceTime, availableBackups = existingBackups,
                diagnosticMessage = "No on-device rules stored yet (DB v${fetchInfo.ruleDbVersion})",
                inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = false
            ) to logs
        }

        val dbUrl = HardwareRulesProtocol.resolveRuleDbUrl(device.ipAddress, probe.resolvedPort, rawPath)
        val binaryResp = try {
            httpClient.getBytes(dbUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errMsg = "Failed downloading rules archive: ${e.message ?: e.javaClass.simpleName}"
            logs += SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = dbUrl,
                soapAction = "GET /rules.db",
                statusText = "[ERR] ${e.javaClass.simpleName}",
                xmlSnippet = errMsg,
                isLiveLanSuccess = false
            )
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion, deviceTimeInfo = deviceTime, availableBackups = existingBackups,
                diagnosticMessage = errMsg, inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = true
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
                deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion, deviceTimeInfo = deviceTime, availableBackups = existingBackups,
                diagnosticMessage = "No on-device rules stored yet (HTTP 404)",
                inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = false
            ) to logs
        }

        if (!binaryResp.isSuccessful) {
            val errMsg = "Switch returned HTTP ${binaryResp.code} for rules archive"
            logs += SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = dbUrl,
                soapAction = "GET /rules.db",
                statusText = "[ERR] HTTP ${binaryResp.code}",
                xmlSnippet = errMsg,
                isLiveLanSuccess = false
            )
            return@withContext DeviceHardwareRulesSnapshot(
                deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = effectiveLongPress,
                dbVersion = fetchInfo.ruleDbVersion, deviceTimeInfo = deviceTime, availableBackups = existingBackups,
                diagnosticMessage = errMsg, inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = true
            ) to logs
        }

        val unpacked = HardwareRulesDbCodec.unpackAndParseRulesZip(binaryResp.bodyBytes, device.udn, workCacheDir())
        val backupFile = if (saveRawBackup && unpacked.integrityOk) {
            HardwareRulesBackupStore.saveRawZipBackup(backupRootDir(), device.concurrencyKey(), fetchInfo.ruleDbVersion, binaryResp.bodyBytes)
        } else null
        val refreshedBackups = listBackupsForDevice(device)

        logs += SoapLogEntry(
            timestamp = HardwareRulesProtocol.nowTime(),
            deviceName = device.name,
            endpoint = dbUrl,
            soapAction = "GET /rules.db",
            statusText = if (unpacked.integrityOk) {
                "HTTP ${binaryResp.code} OK (${binaryResp.bodyBytes.size}B, ${unpacked.rules.size} rule(s))"
            } else {
                "[ERR] Corrupt rules archive (${binaryResp.bodyBytes.size}B)"
            },
            xmlSnippet = "entry=${unpacked.zipEntryName}, tables=${unpacked.existingTables.size}, integrity=${unpacked.integrityOk}",
            isLiveLanSuccess = unpacked.integrityOk
        )

        DeviceHardwareRulesSnapshot(
            deviceId = device.id, deviceName = device.name, deviceUdn = device.udn,
            capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = effectiveLongPress,
            dbVersion = fetchInfo.ruleDbVersion, zipEntryName = unpacked.zipEntryName, rules = unpacked.rules,
            locationInfo = unpacked.locationInfo, deviceTimeInfo = deviceTime, existingTables = unpacked.existingTables,
            integrityOk = unpacked.integrityOk, backupFilePath = backupFile?.absolutePath,
            availableBackups = refreshedBackups,
            diagnosticMessage = unpacked.diagnosticMessage.ifBlank { "Read ${unpacked.rules.size} rule(s) (DB v${fetchInfo.ruleDbVersion})" },
            inspectedAt = HardwareRulesProtocol.nowTime(), hasInspectionError = !unpacked.integrityOk
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
        timeZone: TimeZone = TimeZone.getDefault(),
        onPortResolved: ((Int) -> Unit)? = null
    ): SoapLogEntry = withContext(Dispatchers.IO) {
        val activePort = HardwareRulesProtocol.resolveWorkingEndpoint(httpClient, device, portProber)?.port
            ?: return@withContext SoapLogEntry(
                timestamp = HardwareRulesProtocol.nowTime(),
                deviceName = device.name,
                endpoint = "http://${device.ipAddress}:${device.port}/upnp/control/timesync1",
                soapAction = "timesync1#TimeSync (Aborted)",
                statusText = "[ERR] Switch unreachable or hardware identity mismatch",
                xmlSnippet = "Refused TimeSync without verified switch identity",
                isLiveLanSuccess = false
            )
        onPortResolved?.invoke(activePort)
        HardwareRulesProtocol.executeSyncDeviceTime(httpClient, device, activePort, nowUtcMillis, timeZone)
    }

    suspend fun storeValidatedRulesDb(
        device: DeviceEntity,
        sqliteFile: File,
        zipEntryName: String,
        currentDbVersion: Int
    ): StoreRulesResult = withContext(Dispatchers.IO) {
        val activePort = HardwareRulesProtocol.resolveWorkingEndpoint(httpClient, device, portProber)?.port
            ?: return@withContext StoreRulesResult(
                success = false, verifiedVersion = currentDbVersion,
                errorInfo = "Switch unreachable or hardware identity mismatch at ${device.ipAddress}:${device.port}",
                validation = HardwareRulesValidationResult(false, listOf("Switch unreachable or hardware identity mismatch")),
                logEntry = SoapLogEntry(
                    timestamp = HardwareRulesProtocol.nowTime(),
                    deviceName = device.name,
                    endpoint = "http://${device.ipAddress}:${device.port}/upnp/control/rules1",
                    soapAction = "rules1#StoreRules (Aborted)",
                    statusText = "[ERR] Switch unreachable or identity mismatch",
                    xmlSnippet = "Refused StoreRules without verified switch identity",
                    isLiveLanSuccess = false
                )
            )
        HardwareRulesProtocol.executeStoreValidatedRulesDb(
            httpClient = httpClient, storeHttpClient = storeHttpClient, device = device, activePort = activePort,
            sqliteFile = sqliteFile, zipEntryName = zipEntryName, currentDbVersion = currentDbVersion,
            inspectedOwnerUdn = device.udn
        )
    }

    suspend fun mutateAndStoreRulesDb(
        device: DeviceEntity,
        autoSyncClockIfNeeded: Boolean = true,
        nowUtcMillisProvider: () -> Long = { System.currentTimeMillis() },
        timeZone: TimeZone = TimeZone.getDefault(),
        onPortResolved: ((Int) -> Unit)? = null,
        mutateDb: (sqliteFile: File) -> Unit
    ): Pair<HardwareRulesWriteResult, List<SoapLogEntry>> = withContext(Dispatchers.IO) {
        HardwareRulesWriteEngine.orchestrateMutateAndStore(
            httpClient = httpClient, storeHttpClient = storeHttpClient, device = device,
            probe = probeRulesCapability(device), workDir = workCacheDir(), backupRootDir = backupRootDir(),
            autoSyncClockIfNeeded = autoSyncClockIfNeeded, nowUtcMillisProvider = nowUtcMillisProvider,
            timeZone = timeZone, onPortResolved = onPortResolved,
            refreshFromSwitch = { healed -> fetchDeviceRulesSnapshot(healed, saveRawBackup = false) },
            mutateDb = mutateDb
        )
    }

    suspend fun restoreRulesFromBackup(
        device: DeviceEntity,
        backupFilePath: String,
        onPortResolved: ((Int) -> Unit)? = null
    ): Pair<HardwareRulesWriteResult, List<SoapLogEntry>> = withContext(Dispatchers.IO) {
        HardwareRulesWriteEngine.orchestrateRestoreBackup(
            httpClient = httpClient, storeHttpClient = storeHttpClient, device = device,
            probe = probeRulesCapability(device), backupFile = File(backupFilePath),
            workDir = workCacheDir(), backupRootDir = backupRootDir(), onPortResolved = onPortResolved,
            refreshFromSwitch = { healed -> fetchDeviceRulesSnapshot(healed, saveRawBackup = false) }
        )
    }

    fun listBackupsForDevice(device: DeviceEntity): List<HardwareRulesBackupEntry> =
        HardwareRulesBackupStore.listBackupsForDevice(backupRootDir(), device.concurrencyKey())

    fun clearDeviceBackups(device: DeviceEntity) =
        HardwareRulesBackupStore.clearDeviceBackups(appContext, device.concurrencyKey())

    fun clearAllBackups() = HardwareRulesBackupStore.clearAllBackups(appContext)

    companion object {
        private val UPNP_ERR_401_REGEX = Regex("<errorCode>\\s*401\\s*</errorCode>", RegexOption.IGNORE_CASE)

        internal fun computeEffectiveLongPressCapable(
            device: DeviceEntity,
            verifiedSupported: Boolean,
            servedLightSetupXml: Boolean = false
        ): Boolean {
            if (!verifiedSupported || servedLightSetupXml) return false
            if (device.hardwareRulesLongPressCapable) return true
            if (device.parsedHardwareRulesCapability != HardwareRulesCapability.UNKNOWN) return false
            return device.udn.contains("Lightswitch", ignoreCase = true) ||
                device.udn.contains("Dimmer-1_0", ignoreCase = true) ||
                device.hardwareModel.contains("F7C030", ignoreCase = true) ||
                device.hardwareModel.contains("WLS040", ignoreCase = true) ||
                device.hardwareModel.contains("F7C059", ignoreCase = true)
        }
    }
}
