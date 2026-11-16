package org.suppressingfire.wemi.network

import android.content.Context
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice

/**
 * Core UPnP / SOAP local-LAN network client.
 *
 * Responsibilities kept cohesive here:
 * - 9-port [probeWemoPort] state machine (`49153, 49152, 49154, 49151, 49155..49159`) separating
 *   ARP/SYN host-offline timeout vs. `ECONNREFUSED` closed-port skip vs. HTTP read timeout on wedged ports,
 *   plus `<serialNumber>`/`<UDN>` DHCP IP swap verification
 * - Unified [executeBasicEventSoapCall] + [executeWithPortHealing] UPnP SOAP control pipeline
 *   (`SetBinaryState` with `<BinaryState>Error</BinaryState>` quirk, `GetBinaryState`,
 *   `ChangeFriendlyName`, `ConfigureNightMode`)
 *
 * Delegates live descriptor & metadata enrichment (`GetMetaInfo`, `GetSignalStrength`,
 * `fetchLiveDeviceDescriptor`, `probeAndFetchDeviceDescriptor`) to [DeviceDescriptorFetcher],
 * stateless XML/SKU parsing to [XmlAndSkuParser], and SSDP discovery to [SsdpDiscoveryEngine].
 */
class NetworkClient(
    private val appContext: Context? = null,
    private val extraCandidatePorts: List<Int> = emptyList()
) {
    private val wifiSocketFactory = NetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 750,
        readTimeoutMs = 1200
    )

    private val descriptorFetcher = DeviceDescriptorFetcher(
        httpClient = httpClient,
        probeWemoPort = { ip, port, serial -> probeWemoPort(ip, port, serial) },
        getBinaryState = ::getBinaryState
    )

    private val ssdpEngine = SsdpDiscoveryEngine(
        appContext = appContext,
        resolveLocalIpv4Address = ::resolveLocalIpv4Address,
        probeWemoPort = { ip, port, serial -> probeWemoPort(ip, port, serial) },
        fetchLiveDeviceDescriptor = ::fetchLiveDeviceDescriptor,
        nowTime = ::nowTime,
        wifiSocketFactory = wifiSocketFactory
    )

    companion object {
        const val SKU_WLS040_3WAY = XmlAndSkuParser.SKU_WLS040_3WAY
        const val SKU_WDS060_DIMMER_V2 = XmlAndSkuParser.SKU_WDS060_DIMMER_V2

        val PROBE_PORTS = listOf(49153, 49152, 49154, 49151, 49155, 49156, 49157, 49158, 49159)

        private val loopbackCandidatePorts = java.util.concurrent.CopyOnWriteArraySet<Int>()

        fun registerLoopbackCandidatePort(port: Int) {
            if (port in 1..65535) {
                loopbackCandidatePorts.add(port)
            }
        }

        fun unregisterLoopbackCandidatePort(port: Int) {
            loopbackCandidatePorts.remove(port)
        }

        fun currentLoopbackCandidatePorts(): List<Int> = loopbackCandidatePorts.toList()

        fun registerLoopbackSsdpPort(udpPort: Int) =
            SsdpDiscoveryEngine.registerLoopbackSsdpPort(udpPort)

        fun unregisterLoopbackSsdpPort(udpPort: Int) =
            SsdpDiscoveryEngine.unregisterLoopbackSsdpPort(udpPort)
    }

    private fun nowTime(): String =
        SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())

    private fun orderedCandidatePorts(preferredPort: Int): List<Int> =
        (listOf(preferredPort) + extraCandidatePorts + loopbackCandidatePorts + PROBE_PORTS).distinct()

    fun resolveLocalIpv4Address(): String =
        LanRuntimeUtils.resolvePreferredLanInterfaceIpv4().first

    fun buildSsdpMSearchPacket(searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT): String =
        XmlAndSkuParser.buildSsdpMSearchPacket(searchTarget)

    fun parseSsdpHeaders(rawResponse: String): Map<String, String> =
        XmlAndSkuParser.parseSsdpHeaders(rawResponse)

    fun classifyHardwareSku(
        modelName: String,
        modelDescription: String = "",
        udn: String = "",
        serialNumber: String = "",
        metaSku: String = "",
        isRtos: Boolean = false
    ): String = XmlAndSkuParser.classifyHardwareSku(
        modelName = modelName,
        modelDescription = modelDescription,
        udn = udn,
        serialNumber = serialNumber,
        metaSku = metaSku,
        isRtos = isRtos
    )

    fun parseSetupXmlDescriptor(
        xml: String,
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice = XmlAndSkuParser.parseSetupXmlDescriptor(
        xml = xml,
        locationUrl = locationUrl,
        usnHeader = usnHeader,
        stHeader = stHeader,
        responseTimeMs = responseTimeMs,
        isLiveUdp = isLiveUdp
    )

    /**
     * Probes a smart-switch host across [PROBE_PORTS] (`49153, 49152, 49154, 49151, 49155..49159`):
     * - Distinguishes TCP connect timeout (host offline -> abort early) from `ECONNREFUSED`
     *   (port closed -> try next port immediately) and HTTP read timeout (wedged port -> try next port).
     * - Fetches `/setup.xml` (and `/Lightsetup.xml` fallback for 1st-Gen `F7C030`).
     * - Verifies `<serialNumber>` or `<UDN>` when [expectedSerialOrUdn] is provided so DHCP IP swaps
     *   never attach to the wrong physical switch.
     */
    suspend fun probeWemoPort(
        ipAddress: String,
        preferredPort: Int = 49153,
        expectedSerialOrUdn: String? = null,
        candidatePortsOverride: List<Int>? = null
    ): Int? = withContext(Dispatchers.IO) {
        val ports = candidatePortsOverride ?: orderedCandidatePorts(preferredPort)
        val cleanExpected = expectedSerialOrUdn?.trim().orEmpty()
        val verifyIdentity = !LanRuntimeUtils.isPlaceholderSerial(cleanExpected)

        for (port in ports) {
            try {
                wifiSocketFactory.createSocket().use { tcpSocket ->
                    tcpSocket.connect(InetSocketAddress(ipAddress, port), 550)
                }
            } catch (_: SocketTimeoutException) {
                return@withContext null
            } catch (_: NoRouteToHostException) {
                return@withContext null
            } catch (_: ConnectException) {
                continue
            } catch (_: Exception) {
                continue
            }

            for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
                try {
                    val resp = httpClient.get("http://$ipAddress:$port$path")
                    if (resp.isSuccessful) {
                        val xml = resp.body
                        val isTarget = xml.contains("Belkin", ignoreCase = true) ||
                            xml.contains("WeMo", ignoreCase = true) ||
                            xml.contains("WeMi", ignoreCase = true) ||
                            xml.contains("basicevent", ignoreCase = true)
                        if (isTarget) {
                            if (verifyIdentity) {
                                val xmlSerial = Regex("<serialNumber>([^<]+)</serialNumber>", RegexOption.IGNORE_CASE)
                                    .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                val xmlUdn = Regex("<UDN>([^<]+)</UDN>", RegexOption.IGNORE_CASE)
                                    .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                val matches = xmlSerial.equals(cleanExpected, ignoreCase = true) ||
                                    xmlUdn.contains(cleanExpected, ignoreCase = true) ||
                                    (xmlSerial.isNotEmpty() && cleanExpected.contains(xmlSerial, ignoreCase = true))
                                if (!matches && xmlSerial.isNotEmpty()) {
                                    return@withContext null
                                }
                            }
                            return@withContext port
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    // HTTP read timed out on a wedged port after firmware port-hop; try next port
                } catch (_: Exception) {
                    // Try next path or port
                }
            }
        }
        null
    }

    /**
     * Executes a SOAP action against [device.port] and, if unreachable or wedged, automatically
     * probes all 9 UPnP ports via [probeWemoPort] (and Wi-Fi-bound socket fallback) to heal
     * firmware port hops.
     */
    private suspend fun executeWithPortHealing(
        device: DeviceEntity,
        executeOnPort: suspend (Int) -> SoapCallResult?
    ): SoapCallResult? {
        try {
            val directResult = executeOnPort(device.port)
            if (directResult != null) return directResult
        } catch (_: Exception) {
            // Fall through to 9-port self-healing probe
        }

        val discoveredPort = probeWemoPort(
            ipAddress = device.ipAddress,
            preferredPort = device.port,
            expectedSerialOrUdn = device.serialNumber
        )
        if (discoveredPort != null && discoveredPort != device.port) {
            try {
                val retryResult = executeOnPort(discoveredPort)
                if (retryResult != null) return retryResult
            } catch (_: Exception) {
                // Fall through to offline result
            }
        } else if (discoveredPort == null && LanRuntimeUtils.isPlaceholderSerial(device.serialNumber)) {
            for (port in orderedCandidatePorts(device.port).drop(1)) {
                try {
                    wifiSocketFactory.createSocket().use { s ->
                        s.connect(InetSocketAddress(device.ipAddress, port), 350)
                    }
                    val fallbackResult = executeOnPort(port)
                    if (fallbackResult != null) return fallbackResult
                } catch (_: SocketTimeoutException) {
                    break
                } catch (_: NoRouteToHostException) {
                    break
                } catch (_: Exception) {
                    continue
                }
            }
        }
        return null
    }

    private suspend fun executeBasicEventSoapCall(
        device: DeviceEntity,
        action: String,
        innerXml: String = "",
        logDeviceName: String = device.name,
        successActionLabel: String = action,
        offlineActionLabel: String = successActionLabel,
        offlineStatusText: String = "Offline • Timeout",
        offlineBinaryStateOn: Boolean? = null,
        offlineBrightness: Int? = null,
        offlineNightMode: Boolean? = null,
        onSuccess: (port: Int, endpoint: String, code: Int, elapsedMs: Int, respXml: String, soapBody: String) -> SoapCallResult
    ): SoapCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val soapBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
            action = action,
            innerXml = innerXml
        )
        val soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, action)

        val healedResult = executeWithPortHealing(device) { port ->
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            val response = httpClient.postXml(endpoint, soapBody, soapAction)
            if (response.isSuccessful) {
                val elapsedMs = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(1)
                return@executeWithPortHealing onSuccess(
                    port, endpoint, response.code, elapsedMs, response.body, soapBody
                )
            }
            null
        }
        if (healedResult != null) return@withContext healedResult

        val fallbackEndpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = offlineBinaryStateOn,
            brightness = offlineBrightness,
            resolvedPort = device.port,
            latencyMs = 0,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = logDeviceName,
                endpoint = fallbackEndpoint,
                soapAction = offlineActionLabel,
                statusText = offlineStatusText,
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            ),
            nightModeEnabled = offlineNightMode
        )
    }

    suspend fun setBinaryState(
        device: DeviceEntity,
        targetOn: Boolean,
        targetBrightness: Int? = null
    ): SoapCallResult {
        val stateInt = if (targetOn) 1 else 0
        val innerXml = buildString {
            append("<BinaryState>$stateInt</BinaryState>")
            if (targetBrightness != null) {
                append("\n<brightness>${targetBrightness.coerceIn(0, 100)}</brightness>")
            }
        }
        val label = "SetBinaryState($stateInt${if (targetBrightness != null) ", lvl=$targetBrightness%" else ""})"
        return executeBasicEventSoapCall(
            device = device,
            action = "SetBinaryState",
            innerXml = innerXml,
            successActionLabel = label,
            offlineStatusText = "Offline • Unreachable on LAN"
        ) { port, endpoint, code, elapsedMs, respXml, soapBody ->
            val parsed = XmlAndSkuParser.parseBinaryStatePayload(respXml)
            val confirmedOn = if (parsed.isIdempotentError) targetOn else (parsed.binaryOn ?: targetOn)
            val parsedBrightness = parsed.brightness ?: targetBrightness ?: device.brightness
            SoapCallResult(
                reachableOnLan = true,
                binaryStateOn = confirmedOn,
                brightness = parsedBrightness,
                resolvedPort = port,
                latencyMs = elapsedMs,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = endpoint,
                    soapAction = label,
                    statusText = "HTTP $code OK (${elapsedMs}ms)",
                    xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                    isLiveLanSuccess = true
                )
            )
        }
    }

    suspend fun getBinaryState(device: DeviceEntity): SoapCallResult =
        executeBasicEventSoapCall(
            device = device,
            action = "GetBinaryState"
        ) { port, endpoint, code, elapsedMs, bodyStr, soapBody ->
            val parsed = XmlAndSkuParser.parseBinaryStatePayload(bodyStr)
            SoapCallResult(
                reachableOnLan = true,
                binaryStateOn = parsed.binaryOn ?: false,
                brightness = parsed.brightness,
                resolvedPort = port,
                latencyMs = elapsedMs,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = endpoint,
                    soapAction = "GetBinaryState",
                    statusText = "HTTP $code OK (Live LAN)",
                    xmlSnippet = bodyStr.take(260).ifEmpty { soapBody },
                    isLiveLanSuccess = true
                ),
                nightModeEnabled = parsed.nightModeEnabled
            )
        }

    suspend fun getMetaInfo(ipAddress: String, port: Int): SwitchMetaInfo? =
        descriptorFetcher.getMetaInfo(ipAddress, port)

    suspend fun getSignalStrength(ipAddress: String, port: Int): Int? =
        descriptorFetcher.getSignalStrength(ipAddress, port)

    suspend fun changeFriendlyName(
        device: DeviceEntity,
        newFriendlyName: String
    ): SoapCallResult {
        val label = "ChangeFriendlyName($newFriendlyName)"
        return executeBasicEventSoapCall(
            device = device,
            action = "ChangeFriendlyName",
            innerXml = "<FriendlyName>${XmlAndSkuParser.escapeXml(newFriendlyName)}</FriendlyName>",
            logDeviceName = newFriendlyName,
            successActionLabel = label,
            offlineStatusText = "Offline • Name saved locally only",
            offlineBinaryStateOn = device.isOn,
            offlineBrightness = device.brightness
        ) { port, endpoint, code, elapsedMs, respXml, soapBody ->
            SoapCallResult(
                reachableOnLan = true,
                binaryStateOn = device.isOn,
                brightness = device.brightness,
                resolvedPort = port,
                latencyMs = elapsedMs,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = newFriendlyName,
                    endpoint = endpoint,
                    soapAction = label,
                    statusText = "HTTP $code OK (${elapsedMs}ms)",
                    xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                    isLiveLanSuccess = true
                )
            )
        }
    }

    suspend fun configureNightMode(
        device: DeviceEntity,
        enabled: Boolean,
        startTimeSec: Int = 79200,
        endTimeSec: Int = 21600,
        nightBrightness: Int = 20
    ): SoapCallResult {
        val modeInt = if (enabled) 1 else 0
        val innerXml = """
            <NightMode>$modeInt</NightMode>
            <NightModeBrightness>${nightBrightness.coerceIn(1, 100)}</NightModeBrightness>
            <StartTime>$startTimeSec</StartTime>
            <EndTime>$endTimeSec</EndTime>
        """.trimIndent()
        return executeBasicEventSoapCall(
            device = device,
            action = "ConfigureNightMode",
            innerXml = innerXml,
            successActionLabel = "ConfigureNightMode($modeInt, ${nightBrightness}%)",
            offlineActionLabel = "ConfigureNightMode($modeInt)",
            offlineBinaryStateOn = device.isOn,
            offlineBrightness = device.brightness,
            offlineNightMode = device.nightModeEnabled
        ) { port, endpoint, code, elapsedMs, respXml, soapBody ->
            SoapCallResult(
                reachableOnLan = true,
                binaryStateOn = device.isOn,
                brightness = device.brightness,
                resolvedPort = port,
                latencyMs = elapsedMs,
                logEntry = SoapLogEntry(
                    timestamp = nowTime(),
                    deviceName = device.name,
                    endpoint = endpoint,
                    soapAction = "ConfigureNightMode($modeInt, ${nightBrightness}%)",
                    statusText = "HTTP $code OK (${elapsedMs}ms)",
                    xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                    isLiveLanSuccess = true
                ),
                nightModeEnabled = enabled
            )
        }
    }

    suspend fun fetchLiveDeviceDescriptor(
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice? = descriptorFetcher.fetchLiveDeviceDescriptor(
        locationUrl, usnHeader, stHeader, responseTimeMs, isLiveUdp
    )

    suspend fun probeAndFetchDeviceDescriptor(
        ipAddress: String,
        preferredPort: Int = 49153
    ): SsdpDiscoveredDevice? = descriptorFetcher.probeAndFetchDeviceDescriptor(ipAddress, preferredPort)

    suspend fun scanSubnetUnicast(
        subnetPrefix: String,
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT
    ): List<SsdpDiscoveredDevice> = ssdpEngine.scanSubnetUnicast(subnetPrefix, searchTarget)

    suspend fun performSsdpDiscoveryScan(
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        knownLocalDevices: List<DeviceEntity> = emptyList(),
        includeDemoFallbacks: Boolean = false,
        allowUnicastSubnetFallback: Boolean = true
    ): SsdpScanReport = ssdpEngine.performSsdpDiscoveryScan(
        searchTarget, knownLocalDevices, includeDemoFallbacks, allowUnicastSubnetFallback
    )
}
