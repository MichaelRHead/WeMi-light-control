package org.suppressingfire.wemi.network

import android.content.Context
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
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
    private val extraCandidatePorts: List<Int> = emptyList(),
    socketFactoryOverride: SocketFactory? = null
) {
    private val wifiSocketFactory = socketFactoryOverride ?: NetworkMonitor.createWifiBoundSocketFactory(appContext)

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

        private val SERIAL_TAG_REGEX = Regex("<serialNumber>([^<]+)</serialNumber>", RegexOption.IGNORE_CASE)
        private val UDN_TAG_REGEX = Regex("<UDN>([^<]+)</UDN>", RegexOption.IGNORE_CASE)
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
    ): String = XmlAndSkuParser.classifyHardwareSku(modelName, modelDescription, udn, serialNumber, metaSku, isRtos)

    fun parseSetupXmlDescriptor(
        xml: String,
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice = XmlAndSkuParser.parseSetupXmlDescriptor(xml, locationUrl, usnHeader, stHeader, responseTimeMs, isLiveUdp)

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
        val cleanExpectedUdn = LanRuntimeUtils.canonicalHardwareUdn(cleanExpected)
        val isExpectedUdn = cleanExpected.startsWith("uuid:", ignoreCase = true)
        val verifyIdentity = if (isExpectedUdn) cleanExpectedUdn.isNotEmpty() else !LanRuntimeUtils.isPlaceholderSerial(cleanExpected)

        for (port in ports) {
            try {
                wifiSocketFactory.createSocket().use { tcpSocket ->
                    tcpSocket.connect(InetSocketAddress(ipAddress, port), 550)
                }
            } catch (e: CancellationException) {
                throw e
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
                                val realXmlSerial = SERIAL_TAG_REGEX
                                    .find(xml)?.groupValues?.getOrNull(1)?.trim()
                                    .takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty()
                                val cleanXmlUdn = LanRuntimeUtils.canonicalHardwareUdn(
                                    UDN_TAG_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()
                                )
                                val matches = (realXmlSerial.isNotEmpty() && (
                                    realXmlSerial.equals(cleanExpected, ignoreCase = true) ||
                                        (isExpectedUdn && cleanExpectedUdn.endsWith("-$realXmlSerial", ignoreCase = true))
                                    )) || (cleanXmlUdn.isNotEmpty() && (
                                    cleanXmlUdn.equals(cleanExpectedUdn, ignoreCase = true) ||
                                        (!isExpectedUdn && cleanXmlUdn.contains(cleanExpected, ignoreCase = true))
                                    ))
                                if (!matches && (realXmlSerial.isNotEmpty() || cleanXmlUdn.isNotEmpty())) {
                                    return@withContext null
                                }
                            }
                            return@withContext port
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
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
        onError: ((String) -> Unit)? = null,
        executeOnPort: suspend (Int) -> SoapCallResult?
    ): SoapCallResult? {
        try {
            val directResult = executeOnPort(device.port)
            if (directResult != null) return directResult
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError?.invoke(":${device.port}=${e.javaClass.simpleName}")
        }

        val expectedIdentity = LanRuntimeUtils.expectedHardwareIdentity(device.serialNumber, device.udn)
        val discoveredPort = probeWemoPort(
            ipAddress = device.ipAddress,
            preferredPort = device.port,
            expectedSerialOrUdn = expectedIdentity
        )
        if (discoveredPort != null && discoveredPort != device.port) {
            try {
                val retryResult = executeOnPort(discoveredPort)
                if (retryResult != null) return retryResult
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError?.invoke(":$discoveredPort=${e.javaClass.simpleName}")
            }
        } else if (discoveredPort == null && expectedIdentity.isNotEmpty()) {
            onError?.invoke("9-port probe: no match")
        } else if (discoveredPort == null && expectedIdentity.isEmpty()) {
            for (port in orderedCandidatePorts(device.port).drop(1)) {
                try {
                    wifiSocketFactory.createSocket().use { s ->
                        s.connect(InetSocketAddress(device.ipAddress, port), 350)
                    }
                    val fallbackResult = executeOnPort(port)
                    if (fallbackResult != null) return fallbackResult
                } catch (e: CancellationException) {
                    throw e
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

    private data class SoapOutcome(
        val binaryStateOn: Boolean? = null,
        val brightness: Int? = null,
        val nightModeEnabled: Boolean? = null,
        val statusText: String? = null
    )

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
        onSuccess: (code: Int, respXml: String) -> SoapOutcome
    ): SoapCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val soapBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
            action = action,
            innerXml = innerXml
        )
        val soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, action)
        val failureCauses = mutableListOf<String>()

        val healedResult = executeWithPortHealing(device, onError = { failureCauses.add(it) }) { port ->
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            val response = httpClient.postXml(endpoint, soapBody, soapAction)
            if (response.isSuccessful) {
                val elapsedMs = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(1)
                val outcome = onSuccess(response.code, response.body)
                return@executeWithPortHealing SoapCallResult(
                    reachableOnLan = true,
                    binaryStateOn = outcome.binaryStateOn,
                    brightness = outcome.brightness,
                    resolvedPort = port,
                    latencyMs = elapsedMs,
                    logEntry = SoapLogEntry(
                        timestamp = nowTime(),
                        deviceName = logDeviceName,
                        endpoint = endpoint,
                        soapAction = successActionLabel,
                        statusText = outcome.statusText ?: "HTTP ${response.code} OK (${elapsedMs}ms)",
                        xmlSnippet = response.body.take(260).ifEmpty { soapBody },
                        isLiveLanSuccess = true
                    ),
                    nightModeEnabled = outcome.nightModeEnabled
                )
            }
            failureCauses.add(":$port=HTTP_${response.code}")
            null
        }
        if (healedResult != null) return@withContext healedResult

        val fallbackEndpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        val causeSummary = failureCauses.distinct().take(2).joinToString(", ")
        val resolvedOfflineStatus = if (causeSummary.isNotEmpty()) {
            "$offlineStatusText ($causeSummary)"
        } else {
            offlineStatusText
        }
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
                statusText = resolvedOfflineStatus,
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
        val clampedBrightness = targetBrightness?.coerceIn(0, 100)
        val innerXml = buildString {
            append("<BinaryState>$stateInt</BinaryState>")
            if (clampedBrightness != null) {
                append("\n<brightness>$clampedBrightness</brightness>")
            }
        }
        val label = "SetBinaryState($stateInt${if (clampedBrightness != null) ", lvl=$clampedBrightness%" else ""})"
        return executeBasicEventSoapCall(
            device = device,
            action = "SetBinaryState",
            innerXml = innerXml,
            successActionLabel = label,
            offlineStatusText = "Offline • Unreachable on LAN"
        ) { _, respXml ->
            val parsed = XmlAndSkuParser.parseBinaryStatePayload(respXml)
            val confirmedOn = if (parsed.isIdempotentError) targetOn else (parsed.binaryOn ?: targetOn)
            SoapOutcome(
                binaryStateOn = confirmedOn,
                brightness = parsed.brightness ?: clampedBrightness ?: device.brightness
            )
        }
    }

    suspend fun getBinaryState(device: DeviceEntity): SoapCallResult =
        executeBasicEventSoapCall(
            device = device,
            action = "GetBinaryState"
        ) { code, bodyStr ->
            val parsed = XmlAndSkuParser.parseBinaryStatePayload(bodyStr)
            SoapOutcome(
                binaryStateOn = parsed.binaryOn ?: false,
                brightness = parsed.brightness,
                nightModeEnabled = parsed.nightModeEnabled,
                statusText = "HTTP $code OK (Live LAN)"
            )
        }

    suspend fun getMetaInfo(ipAddress: String, port: Int): SwitchMetaInfo? =
        descriptorFetcher.getMetaInfo(ipAddress, port)

    suspend fun getSignalStrength(ipAddress: String, port: Int): Int? =
        descriptorFetcher.getSignalStrength(ipAddress, port)

    suspend fun changeFriendlyName(
        device: DeviceEntity,
        newFriendlyName: String
    ): SoapCallResult = executeBasicEventSoapCall(
        device = device,
        action = "ChangeFriendlyName",
        innerXml = "<FriendlyName>${XmlAndSkuParser.escapeXml(newFriendlyName)}</FriendlyName>",
        logDeviceName = newFriendlyName,
        successActionLabel = "ChangeFriendlyName($newFriendlyName)",
        offlineStatusText = "Offline • Name saved locally only",
        offlineBinaryStateOn = device.isOn,
        offlineBrightness = device.brightness
    ) { _, _ -> SoapOutcome(binaryStateOn = device.isOn, brightness = device.brightness) }

    suspend fun configureNightMode(
        device: DeviceEntity,
        enabled: Boolean,
        startTimeSec: Int = 79200,
        endTimeSec: Int = 21600,
        nightBrightness: Int = 20
    ): SoapCallResult {
        val modeInt = if (enabled) 1 else 0
        val clampedNightBrightness = nightBrightness.coerceIn(1, 100)
        val innerXml = """
            <NightMode>$modeInt</NightMode>
            <NightModeBrightness>$clampedNightBrightness</NightModeBrightness>
            <StartTime>$startTimeSec</StartTime>
            <EndTime>$endTimeSec</EndTime>
        """.trimIndent()
        return executeBasicEventSoapCall(
            device = device,
            action = "ConfigureNightMode",
            innerXml = innerXml,
            successActionLabel = "ConfigureNightMode($modeInt, ${clampedNightBrightness}%)",
            offlineActionLabel = "ConfigureNightMode($modeInt)",
            offlineBinaryStateOn = device.isOn,
            offlineBrightness = device.brightness,
            offlineNightMode = device.nightModeEnabled
        ) { _, _ ->
            SoapOutcome(binaryStateOn = device.isOn, brightness = device.brightness, nightModeEnabled = enabled)
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
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        skipIps: Set<String> = emptySet()
    ): List<SsdpDiscoveredDevice> = ssdpEngine.scanSubnetUnicast(subnetPrefix, searchTarget, skipIps)

    suspend fun performSsdpDiscoveryScan(
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        knownLocalDevices: List<DeviceEntity> = emptyList(),
        includeDemoFallbacks: Boolean = false,
        allowUnicastSubnetFallback: Boolean = true,
        onlySweepWhenKnownMissing: Boolean = false
    ): SsdpScanReport = ssdpEngine.performSsdpDiscoveryScan(
        searchTarget,
        knownLocalDevices,
        includeDemoFallbacks,
        allowUnicastSubnetFallback,
        onlySweepWhenKnownMissing
    )
}
