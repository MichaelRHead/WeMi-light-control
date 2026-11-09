package com.example.data

import com.example.network.WeMoNetworkClient
import com.example.network.WeMoProvisionResult
import com.example.network.WeMoProvisionerClient
import com.example.network.WeMoScannedAp
import com.example.network.WeMoSetupDeviceDescriptor
import kotlinx.coroutines.flow.first

/**
 * Coordinates zero-cloud `10.22.22.1` Belkin WeMo Setup AP probing, 2.4 GHz AP scanning,
 * `WiFiSetup1#ConnectHomeNetwork` provisioning, Demo Showroom simulation, and post-provisioning
 * Room registration.
 */
internal class WeMoProvisioningCoordinator(
    private val dao: WeMoDao,
    private val provisionerClient: WeMoProvisionerClient,
    private val detectCurrentLanIp: () -> String,
    private val isDemoShowroomMode: () -> Boolean,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String
) {
    @Volatile
    var lastDetectedDescriptor: WeMoSetupDeviceDescriptor? = null
        private set

    @Volatile
    var lastScannedAps: List<WeMoScannedAp> = emptyList()
        private set

    fun latestProbeDiagnosticSummary(): String = provisionerClient.lastProbeDiagnosticSummary

    fun latestApScanDiagnosticSummary(): String = provisionerClient.lastApScanDiagnosticSummary

    /**
     * Probes the factory-reset Belkin WeMo Setup AP at [apHost] (`10.22.22.1`) for its
     * `/setup.xml` / `/Lightsetup.xml` and `GetMetaInfo` descriptor (including `<rtos>`, `<iot>`,
     * `<binaryOption>`, `<new_algo>`, and recommended AES-128-CBC encryption method).
     */
    suspend fun probeSetupApDevice(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152
    ): WeMoSetupDeviceDescriptor? {
        val useFastDemo = isDemoShowroomMode() &&
            apHost == "10.22.22.1" &&
            !detectCurrentLanIp().startsWith("10.22.22.")
        val live = if (useFastDemo) null else provisionerClient.probeSetupDevice(apHost = apHost, preferredPort = apPort)
        if (live != null) {
            lastDetectedDescriptor = live
            return live
        }
        if (isDemoShowroomMode() && apHost == "10.22.22.1") {
            val demo = WeMoSetupDeviceDescriptor(
                host = apHost,
                port = apPort,
                friendlyName = "WeMo Smart Switch",
                modelName = "WLS040",
                hardwareSku = WeMoNetworkClient.SKU_WLS040_3WAY,
                deviceType = WeMoDeviceType.SWITCH_PLUG,
                serialNumber = "221840K01088SETUP",
                macAddress = "EC1A59881122",
                udn = "uuid:Lightswitch-2_0-221840K01088SETUP",
                firmwareVersion = "WeMo_WW_2.00.11563.PVT-RTOS",
                isRtos = true,
                isIot = false,
                binaryOption = false,
                recommendedEncryptionMethod = 2,
                recommendedAddLengths = false,
                newAlgo = false,
                setupXmlPath = "/setup.xml",
                macAndSerialSource = "DemoShowroom",
                rawMetaInfo = "EC1A59881122|221840K01088SETUP|WLS040|WeMo_WW_2.00.11563.PVT-RTOS|Wemo.LS3W.881|WLS040",
                probeTraceSummary = ":49152/setup.xml=DEMO_OK"
            )
            lastDetectedDescriptor = demo
            return demo
        }
        lastDetectedDescriptor = null
        return null
    }

    /**
     * Scans nearby 2.4 GHz Wi-Fi APs from a factory-reset Belkin WeMo Setup AP at `10.22.22.1`
     * via `urn:Belkin:service:WiFiSetup:1#GetApList` (with `GetNetworkList` fallback).
     */
    suspend fun scanSetupApNetworks(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152
    ): List<WeMoScannedAp> {
        val useFastDemo = isDemoShowroomMode() &&
            apHost == "10.22.22.1" &&
            !detectCurrentLanIp().startsWith("10.22.22.")
        val cachedDescriptor = lastDetectedDescriptor?.takeIf { it.host == apHost && it.port == apPort }
        val liveList = if (useFastDemo) {
            emptyList()
        } else {
            provisionerClient.fetchAvailableApList(
                apHost = apHost,
                apPort = apPort,
                knownDescriptor = cachedDescriptor
            )
        }
        if (liveList.isNotEmpty()) {
            lastScannedAps = liveList
            appendLog(
                SoapLogEntry(
                    timestamp = nowFormatted(),
                    deviceName = "WeMo Setup AP ($apHost)",
                    endpoint = "http://$apHost:$apPort/upnp/control/WiFiSetup1",
                    soapAction = "WiFiSetup1#GetApList",
                    statusText = "Discovered ${liveList.size} 2.4 GHz Wi-Fi network(s)",
                    xmlSnippet = liveList.joinToString("\n") { "${it.ssid}|${it.channel}|${it.signalStrengthPercent}|${it.authMode}/${it.encryptionType}" },
                    isLiveLanSuccess = true
                )
            )
            return liveList
        }
        if (isDemoShowroomMode() && apHost == "10.22.22.1") {
            val demoAps = listOf(
                WeMoScannedAp("WeMi-Home-5G", channel = 6, signalStrengthPercent = 92, authMode = "WPA2PSK", encryptionType = "AES"),
                WeMoScannedAp("WeMi-Home-2.4G", channel = 1, signalStrengthPercent = 96, authMode = "WPA2PSK", encryptionType = "AES"),
                WeMoScannedAp("PineLake-Cabin-WiFi", channel = 11, signalStrengthPercent = 78, authMode = "WPA2PSK", encryptionType = "AES"),
                WeMoScannedAp("Studio-Guest-LAN", channel = 6, signalStrengthPercent = 68, authMode = "WPA2PSK", encryptionType = "TKIPAES")
            )
            lastScannedAps = demoAps
            appendLog(
                SoapLogEntry(
                    timestamp = nowFormatted(),
                    deviceName = "WeMo Setup AP ($apHost)",
                    endpoint = "http://$apHost:$apPort/upnp/control/WiFiSetup1",
                    soapAction = "WiFiSetup1#GetApList (Demo Showroom)",
                    statusText = "Simulated ${demoAps.size} nearby 2.4 GHz APs",
                    xmlSnippet = demoAps.joinToString("\n") { "${it.ssid}|${it.channel}|${it.signalStrengthPercent}|${it.authMode}/${it.encryptionType}" },
                    isLiveLanSuccess = false
                )
            )
            return demoAps
        }
        lastScannedAps = emptyList()
        return emptyList()
    }

    /**
     * Provisions a factory-reset Belkin WeMo switch at `10.22.22.1` onto the home 2.4 GHz Wi-Fi
     * network via AES-128-CBC (Auto-Detect or Methods 1/2/3), double-send `ConnectHomeNetwork`,
     * multi-poll `GetNetworkStatus`, `CloseSetup`, and `SetSetupDoneStatus`, and automatically
     * registers the newly provisioned switch in Room (`wemo_devices`) under [activeNetworkKey].
     */
    suspend fun provisionSetupApDevice(
        apHost: String = "10.22.22.1",
        apPort: Int = 49152,
        ssid: String,
        plainPassword: String,
        channel: Int = 6,
        authMode: String = "WPA2PSK",
        encryptionType: String = "AES",
        encryptionMethod: Int = 0,
        addPasswordLengths: Boolean? = null,
        customDeviceName: String? = null,
        roomName: String? = null,
        deviceType: WeMoDeviceType? = null,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        maxStatusPolls: Int = 18,
        statusPollDelayMs: Long = 1000L,
        onProgress: ((String) -> Unit)? = null
    ): WeMoProvisionResult {
        val useFastDemo = isDemoShowroomMode() &&
            apHost == "10.22.22.1" &&
            !detectCurrentLanIp().startsWith("10.22.22.")
        val cachedDescriptor = lastDetectedDescriptor?.takeIf { it.host == apHost }
        val liveResult = if (useFastDemo) {
            null
        } else {
            provisionerClient.provisionDeviceToHomeWifi(
                apHost = apHost,
                apPort = cachedDescriptor?.port ?: apPort,
                ssid = ssid,
                plainPassword = plainPassword,
                channel = channel,
                authMode = authMode,
                encryptionType = encryptionType,
                encryptionMethod = encryptionMethod,
                addPasswordLengths = addPasswordLengths,
                customFriendlyName = customDeviceName,
                maxStatusPolls = maxStatusPolls,
                statusPollDelayMs = statusPollDelayMs,
                knownDescriptor = cachedDescriptor,
                knownScannedAps = lastScannedAps,
                onProgress = onProgress
            )
        }

        val finalResult = if ((liveResult == null || (!liveResult.success && liveResult.networkStatusCode != 2)) && isDemoShowroomMode() && apHost == "10.22.22.1") {
            val isOpen = encryptionType.equals("NONE", ignoreCase = true) || authMode.equals("OPEN", ignoreCase = true) || plainPassword.isEmpty()
            if (!isOpen && plainPassword.length in 1..7) {
                val trace = listOf(
                    "[DEMO] Validating WPA2 password length (${plainPassword.length} chars)",
                    "[STEP 5 SEND] ConnectHomeNetwork(\"$ssid\") -> Status=2 (ShortPassword < 8 chars)",
                    "[STEP 6 HOLD] Setup AP left open for password correction"
                )
                trace.forEach { onProgress?.invoke(it) }
                WeMoProvisionResult(
                    success = false,
                    networkStatusCode = 2,
                    statusMessage = "Password rejected by WeMo (Status=2): Wi-Fi password must be at least 8 characters",
                    encryptedPasswordPayload = "",
                    logEntry = SoapLogEntry(
                        timestamp = nowFormatted(),
                        deviceName = "WeMo Setup AP ($apHost)",
                        endpoint = "http://$apHost:$apPort/upnp/control/WiFiSetup1",
                        soapAction = "WiFiSetup1#ConnectHomeNetwork($ssid)",
                        statusText = "ShortPassword (Status=2): < 8 chars",
                        xmlSnippet = "<NetworkStatus>2</NetworkStatus>",
                        isLiveLanSuccess = false
                    ),
                    debugTraceLines = trace
                )
            } else {
                val demoDescriptor = probeSetupApDevice(apHost, apPort)
                val effMethod = if (encryptionMethod in 1..3) encryptionMethod else (demoDescriptor?.recommendedEncryptionMethod ?: 2)
                val effLengths = addPasswordLengths ?: (effMethod != 2)
                val encPwd = if (isOpen) "" else provisionerClient.encryptWifiPassword(
                    plainPassword = plainPassword,
                    macAddress = demoDescriptor?.macAddress ?: "EC1A59881122",
                    serialNumber = demoDescriptor?.serialNumber ?: "221840K01088SETUP",
                    encryptionMethod = effMethod,
                    addPasswordLengths = effLengths
                )
                val resolvedLabel = customDeviceName?.trim()?.takeIf { it.isNotEmpty() }
                    ?: demoDescriptor?.friendlyName
                    ?: "WeMo Smart Switch"
                val msg = "Provisioned '$resolvedLabel' to '$ssid' (Status=1, Method $effMethod) • Added to switches"
                val trace = listOf(
                    "[STEP 1 PROBE] DEMO 10.22.22.1:49152 (/setup.xml) model=WLS040 rtos=true",
                    "[STEP 4 CRYPTO] Method=$effMethod addLengths=$effLengths plainLen=${plainPassword.length} encLen=${encPwd.length}",
                    "[STEP 5 SEND #1/2] ConnectHomeNetwork(\"$ssid\", Ch=$channel, $authMode/$encryptionType) -> PairingStatus=1",
                    "[POLL #1] GetNetworkStatus -> Status=1 (Connected)",
                    "[STEP 6 CLOSE] CloseSetup -> success | SetSetupDoneStatus -> HTTP_200"
                )
                trace.forEach { onProgress?.invoke(it) }
                WeMoProvisionResult(
                    success = true,
                    networkStatusCode = 1,
                    statusMessage = msg,
                    encryptedPasswordPayload = encPwd,
                    logEntry = SoapLogEntry(
                        timestamp = nowFormatted(),
                        deviceName = resolvedLabel,
                        endpoint = "http://$apHost:$apPort/upnp/control/WiFiSetup1",
                        soapAction = "WiFiSetup1#ConnectHomeNetwork($ssid, Method $effMethod, lengths=$effLengths)",
                        statusText = msg,
                        xmlSnippet = "<ConnectHomeNetwork><ssid>$ssid</ssid><channel>$channel</channel></ConnectHomeNetwork>",
                        isLiveLanSuccess = false
                    ),
                    closeSetupStatus = "success",
                    effectiveEncryptionMethod = effMethod,
                    addedLengthSuffix = effLengths,
                    setupDevice = demoDescriptor?.copy(
                        friendlyName = resolvedLabel,
                        deviceType = deviceType ?: demoDescriptor.deviceType
                    ),
                    debugTraceLines = trace
                )
            }
        } else {
            liveResult!!
        }

        appendLog(finalResult.logEntry)

        if (finalResult.success) {
            val desc = finalResult.setupDevice
            val resolvedName = customDeviceName?.trim()?.takeIf { it.isNotEmpty() }
                ?: desc?.friendlyName?.takeIf { it.isNotBlank() && !it.equals("WeMo Switch", ignoreCase = true) }
                ?: "WeMo Smart Switch"
            val resolvedRoom = roomName?.trim()?.takeIf { it.isNotEmpty() }
                ?: inferRoomFromName(resolvedName)
            val resolvedType = deviceType ?: desc?.deviceType ?: WeMoDeviceType.SWITCH_PLUG
            val existingDevices = dao.getAllDevices().first()

            val homeNetworks = dao.getAllHomeNetworks().first()
            val matchedHomeBySsid = homeNetworks.find { it.ssidLabel.equals(ssid, ignoreCase = true) }
            val targetNetworkKey = matchedHomeBySsid?.networkKey ?: activeNetworkKey
            val homePrefix = homeNetworks.find { it.networkKey == targetNetworkKey }?.subnetPrefix ?: "192.168.1"

            val serial = desc?.serialNumber?.takeIf { it.isNotBlank() && it != "221517K01018AB" }
                ?: "221840K${(100000..999999).random()}"
            val mac = desc?.macAddress?.takeIf { it.isNotBlank() } ?: "EC1A59881122"
            val sku = desc?.hardwareSku?.takeIf { it.isNotBlank() } ?: when (resolvedType) {
                WeMoDeviceType.LIGHTBULB_DIMMER -> WeMoNetworkClient.SKU_WDS060_DIMMER_V2
                else -> WeMoNetworkClient.SKU_WLS040_3WAY
            }
            val udn = desc?.udn?.takeIf { it.isNotBlank() } ?: "uuid:Lightswitch-2_0-$serial"
            val firmware = desc?.firmwareVersion?.takeIf { it.isNotBlank() } ?: "WeMo_WW_2.00.11563.PVT"

            val existing = existingDevices.find {
                (serial.isNotBlank() && it.serialNumber.equals(serial, ignoreCase = true)) ||
                    (it.networkKey == targetNetworkKey && it.name.equals(resolvedName, ignoreCase = true))
            }

            val nextOctet = (120 + (existingDevices.size % 120)).coerceIn(101, 250)
            val placeholderLanIp = "$homePrefix.$nextOctet"

            if (existing == null) {
                val newEntity = WeMoDeviceEntity(
                    name = resolvedName,
                    roomName = resolvedRoom,
                    networkKey = targetNetworkKey,
                    wifiSsid = ssid,
                    deviceType = resolvedType.name,
                    ipAddress = placeholderLanIp,
                    port = desc?.port ?: 49153,
                    isOn = false,
                    isReachable = true,
                    brightness = 100,
                    latencyMs = 15,
                    lastCheckedTime = "Provisioned • ${nowFormatted()}",
                    serialNumber = serial,
                    firmwareVersion = firmware,
                    hardwareModel = sku,
                    macAddress = mac,
                    udn = udn,
                    signalStrengthPercent = 90,
                    sortOrder = existingDevices.size
                )
                dao.insertDevice(newEntity)
            } else {
                dao.updateDevice(
                    existing.copy(
                        name = resolvedName,
                        roomName = resolvedRoom,
                        networkKey = targetNetworkKey,
                        wifiSsid = ssid,
                        deviceType = resolvedType.name,
                        port = desc?.port ?: existing.port,
                        isReachable = true,
                        lastCheckedTime = "Provisioned • ${nowFormatted()}",
                        serialNumber = serial,
                        firmwareVersion = firmware,
                        hardwareModel = sku,
                        macAddress = mac,
                        udn = udn
                    )
                )
            }
        }

        return finalResult
    }
}
