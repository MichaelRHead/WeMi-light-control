package com.example.network

import android.content.Context
import com.example.data.SoapLogEntry
import com.example.data.SsdpDiscoveredDevice
import com.example.data.WeMoDeviceEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SoapCallResult(
    val reachableOnLan: Boolean,
    val binaryStateOn: Boolean?,
    val brightness: Int?,
    val resolvedPort: Int,
    val latencyMs: Int,
    val logEntry: SoapLogEntry,
    val nightModeEnabled: Boolean? = null
)

data class WeMoMetaInfo(
    val macAddress: String,
    val serialNumber: String,
    val deviceSku: String,
    val firmwareVersion: String,
    val accessPointSsid: String,
    val modelName: String
)

data class SsdpScanReport(
    val localInterfaceIp: String,
    val searchTargetUsed: String,
    val mSearchRequestRaw: String,
    val discoveredDevices: List<SsdpDiscoveredDevice>,
    val rawSocketHitCount: Int,
    val scanDurationMs: Long,
    val logEntry: SoapLogEntry
)

/**
 * Core Belkin WeMo UPnP / SOAP network client.
 *
 * Responsibilities kept cohesive here:
 * - 9-port `probeWemoPort` state machine (`49153, 49152, 49154, 49151, 49155..49159`) separating
 *   ARP/SYN host-offline timeout vs. `ECONNREFUSED` closed-port skip vs. HTTP read timeout on wedged ports,
 *   plus `<serialNumber>`/`<UDN>` DHCP IP swap verification
 * - UPnP SOAP control actions (`SetBinaryState` with `<BinaryState>Error</BinaryState>` quirk,
 *   `GetBinaryState`, `GetMetaInfo`, `GetSignalStrength`, `ChangeFriendlyName`, `ConfigureNightMode`)
 * - Live descriptor enrichment (`fetchLiveDeviceDescriptor` and `probeAndFetchDeviceDescriptor`)
 *
 * Delegates stateless XML/SKU parsing to [WeMoXmlAndSkuParser] and SSDP multicast/unicast discovery
 * to [WeMoSsdpDiscoveryEngine].
 */
class WeMoNetworkClient(
    private val appContext: Context? = null,
    private val extraCandidatePorts: List<Int> = emptyList()
) {
    private val wifiSocketFactory = WeMoNetworkMonitor.createWifiBoundSocketFactory(appContext)

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(wifiSocketFactory)
        .connectTimeout(750, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .writeTimeout(900, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val xmlMediaType = "text/xml; charset=\"utf-8\"".toMediaType()

    private val ssdpEngine = WeMoSsdpDiscoveryEngine(
        appContext = appContext,
        resolveLocalIpv4Address = ::resolveLocalIpv4Address,
        probeWemoPort = { ip, port, serial -> probeWemoPort(ip, port, serial) },
        fetchLiveDeviceDescriptor = ::fetchLiveDeviceDescriptor,
        nowTime = ::nowTime
    )

    companion object {
        const val SKU_WLS040_3WAY = WeMoXmlAndSkuParser.SKU_WLS040_3WAY
        const val SKU_WDS060_DIMMER_V2 = WeMoXmlAndSkuParser.SKU_WDS060_DIMMER_V2

        val PROBE_PORTS = listOf(49153, 49152, 49154, 49151, 49155, 49156, 49157, 49158, 49159)

        private val PLACEHOLDER_SERIALS = setOf(
            "",
            "221517K01018AB",
            "221517K01018LAN",
            "221517K01018NEW"
        )

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
            WeMoSsdpDiscoveryEngine.registerLoopbackSsdpPort(udpPort)

        fun unregisterLoopbackSsdpPort(udpPort: Int) =
            WeMoSsdpDiscoveryEngine.unregisterLoopbackSsdpPort(udpPort)
    }

    private fun nowTime(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    private fun orderedCandidatePorts(preferredPort: Int): List<Int> {
        return (listOf(preferredPort) + extraCandidatePorts + loopbackCandidatePorts + PROBE_PORTS).distinct()
    }

    fun resolveLocalIpv4Address(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val sortedInterfaces = interfaces.sortedBy { nif ->
                val n = nif.name.lowercase(Locale.US)
                when {
                    n.startsWith("wlan") -> 0
                    n.startsWith("eth") || n.startsWith("en") -> 1
                    else -> 2
                }
            }
            for (nif in sortedInterfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (!hostAddr.startsWith("169.254.")) {
                            return hostAddr
                        }
                    }
                }
            }
            "192.168.1.50"
        } catch (_: Exception) {
            "192.168.1.50"
        }
    }

    fun buildSsdpMSearchPacket(searchTarget: String = "urn:Belkin:service:basicevent:1"): String =
        WeMoXmlAndSkuParser.buildSsdpMSearchPacket(searchTarget)

    fun parseSsdpHeaders(rawResponse: String): Map<String, String> =
        WeMoXmlAndSkuParser.parseSsdpHeaders(rawResponse)

    fun classifyHardwareSku(
        modelName: String,
        modelDescription: String = "",
        udn: String = "",
        serialNumber: String = "",
        metaSku: String = "",
        isRtos: Boolean = false
    ): String = WeMoXmlAndSkuParser.classifyHardwareSku(
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
    ): SsdpDiscoveredDevice = WeMoXmlAndSkuParser.parseSetupXmlDescriptor(
        xml = xml,
        locationUrl = locationUrl,
        usnHeader = usnHeader,
        stHeader = stHeader,
        responseTimeMs = responseTimeMs,
        isLiveUdp = isLiveUdp
    )

    /**
     * Probes a Belkin WeMo host across [PROBE_PORTS] (`49153, 49152, 49154, 49151, 49155..49159`)
     * following `pywemo`'s `probe_wemo` algorithm:
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
        val verifyIdentity = cleanExpected.isNotEmpty() && cleanExpected !in PLACEHOLDER_SERIALS

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

            val descriptorPaths = listOf("/setup.xml", "/Lightsetup.xml")
            for (path in descriptorPaths) {
                try {
                    val req = Request.Builder()
                        .url("http://$ipAddress:$port$path")
                        .header("Connection", "close")
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val xml = resp.body?.string().orEmpty()
                            val isBelkin = xml.contains("Belkin", ignoreCase = true) ||
                                xml.contains("WeMo", ignoreCase = true) ||
                                xml.contains("WeMi", ignoreCase = true) ||
                                xml.contains("basicevent", ignoreCase = true)
                            if (isBelkin) {
                                if (verifyIdentity) {
                                    val xmlSerial = Regex("<serialNumber>([^<]+)</serialNumber>", RegexOption.IGNORE_CASE)
                                        .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                    val xmlUdn = Regex("<UDN>([^<]+)</UDN>", RegexOption.IGNORE_CASE)
                                        .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                    val matches = xmlSerial.equals(cleanExpected, ignoreCase = true) ||
                                        xmlUdn.contains(cleanExpected, ignoreCase = true) ||
                                        cleanExpected.contains(xmlSerial, ignoreCase = true)
                                    if (!matches && xmlSerial.isNotEmpty()) {
                                        return@withContext null
                                    }
                                }
                                return@withContext port
                            }
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
     * probes all 9 Belkin UPnP ports via [probeWemoPort] (and Wi-Fi-bound socket fallback) to heal
     * firmware port hops without duplicating retry boilerplate across SOAP actions.
     */
    private suspend fun executeWithPortHealing(
        device: WeMoDeviceEntity,
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
        } else if (discoveredPort == null) {
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

    /**
     * Sends a Belkin WeMo UPnP SOAP `SetBinaryState` call over HTTP to the device's `IP:port`.
     */
    suspend fun setBinaryState(
        device: WeMoDeviceEntity,
        targetOn: Boolean,
        targetBrightness: Int? = null
    ): SoapCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val stateInt = if (targetOn) 1 else 0
        val brightnessTag = if (targetBrightness != null) {
            "\n      <brightness>${targetBrightness.coerceIn(0, 100)}</brightness>"
        } else {
            ""
        }
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:SetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
                  <BinaryState>$stateInt</BinaryState>$brightnessTag
                </u:SetBinaryState>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val soapAction = "\"urn:Belkin:service:basicevent:1#SetBinaryState\""

        val healedResult = executeWithPortHealing(device) { port ->
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            val request = Request.Builder()
                .url(endpoint)
                .header("SOAPACTION", soapAction)
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val elapsedMs = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(1)
                    val respXml = response.body?.string().orEmpty()
                    val rawState = Regex("<BinaryState>([^<]+)</BinaryState>", RegexOption.IGNORE_CASE)
                        .find(respXml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                    val parts = rawState.split("|")
                    val firstToken = parts.firstOrNull()?.trim().orEmpty()
                    val confirmedOn = when {
                        firstToken.equals("Error", ignoreCase = true) -> targetOn
                        firstToken == "1" || firstToken == "8" -> true
                        firstToken == "0" -> false
                        else -> targetOn
                    }
                    val parsedBrightness = parts.getOrNull(1)?.trim()?.toIntOrNull()
                        ?: Regex("<brightness>(\\d+)</brightness>", RegexOption.IGNORE_CASE)
                            .find(respXml)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ?: targetBrightness
                        ?: device.brightness

                    return@executeWithPortHealing SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = confirmedOn,
                        brightness = parsedBrightness,
                        resolvedPort = port,
                        latencyMs = elapsedMs,
                        logEntry = SoapLogEntry(
                            timestamp = nowTime(),
                            deviceName = device.name,
                            endpoint = endpoint,
                            soapAction = "SetBinaryState($stateInt${if (targetBrightness != null) ", lvl=$targetBrightness%" else ""})",
                            statusText = "HTTP ${response.code} OK (${elapsedMs}ms)",
                            xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                            isLiveLanSuccess = true
                        )
                    )
                }
            }
            null
        }
        if (healedResult != null) return@withContext healedResult

        val fallbackEndpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = null,
            brightness = null,
            resolvedPort = device.port,
            latencyMs = 0,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = fallbackEndpoint,
                soapAction = "SetBinaryState($stateInt${if (targetBrightness != null) ", lvl=$targetBrightness%" else ""})",
                statusText = "Offline • Unreachable on LAN",
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            )
        )
    }

    /**
     * Queries a device's current binary state via UPnP SOAP `GetBinaryState`.
     */
    suspend fun getBinaryState(device: WeMoDeviceEntity): SoapCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:GetBinaryState xmlns:u="urn:Belkin:service:basicevent:1">
                </u:GetBinaryState>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val soapAction = "\"urn:Belkin:service:basicevent:1#GetBinaryState\""

        val healedResult = executeWithPortHealing(device) { port ->
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            val request = Request.Builder()
                .url(endpoint)
                .header("SOAPACTION", soapAction)
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val elapsedMs = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(1)
                    val bodyStr = response.body?.string().orEmpty()
                    val rawState = Regex("<BinaryState>([^<]+)</BinaryState>", RegexOption.IGNORE_CASE)
                        .find(bodyStr)?.groupValues?.getOrNull(1).orEmpty()
                    val parts = rawState.split("|")
                    val isOn = parts.firstOrNull()?.trim().let { it == "1" || it == "8" }
                    val parsedBrightness = parts.getOrNull(1)?.trim()?.toIntOrNull()
                        ?: Regex("<brightness>(\\d+)</brightness>", RegexOption.IGNORE_CASE).find(bodyStr)
                            ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val nightMode = Regex("<NightMode>(\\d+)</NightMode>", RegexOption.IGNORE_CASE).find(bodyStr)
                        ?.groupValues?.getOrNull(1)?.let { it == "1" }
                    return@executeWithPortHealing SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = isOn,
                        brightness = parsedBrightness,
                        resolvedPort = port,
                        latencyMs = elapsedMs,
                        logEntry = SoapLogEntry(
                            timestamp = nowTime(),
                            deviceName = device.name,
                            endpoint = endpoint,
                            soapAction = "GetBinaryState",
                            statusText = "HTTP ${response.code} OK (Live LAN)",
                            xmlSnippet = bodyStr.take(260).ifEmpty { soapBody },
                            isLiveLanSuccess = true
                        ),
                        nightModeEnabled = nightMode
                    )
                }
            }
            null
        }
        if (healedResult != null) return@withContext healedResult

        val fallbackEndpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = null,
            brightness = null,
            resolvedPort = device.port,
            latencyMs = 0,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = fallbackEndpoint,
                soapAction = "GetBinaryState",
                statusText = "Offline • Timeout",
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            )
        )
    }

    /**
     * Queries `urn:Belkin:service:metainfo:1#GetMetaInfo` on `/upnp/control/metainfo1`.
     */
    suspend fun getMetaInfo(ipAddress: String, port: Int): WeMoMetaInfo? = withContext(Dispatchers.IO) {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:GetMetaInfo xmlns:u="urn:Belkin:service:metainfo:1">
                </u:GetMetaInfo>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val req = Request.Builder()
                .url("http://$ipAddress:$port/upnp/control/metainfo1")
                .header("SOAPACTION", "\"urn:Belkin:service:metainfo:1#GetMetaInfo\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                return@withContext WeMoXmlAndSkuParser.parseMetaInfoSoapResponse(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Queries `urn:Belkin:service:basicevent:1#GetSignalStrength` on `/upnp/control/basicevent1`.
     */
    suspend fun getSignalStrength(ipAddress: String, port: Int): Int? = withContext(Dispatchers.IO) {
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:GetSignalStrength xmlns:u="urn:Belkin:service:basicevent:1">
                </u:GetSignalStrength>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val req = Request.Builder()
                .url("http://$ipAddress:$port/upnp/control/basicevent1")
                .header("SOAPACTION", "\"urn:Belkin:service:basicevent:1#GetSignalStrength\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                return@withContext WeMoXmlAndSkuParser.parseSignalStrengthSoapResponse(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates the hardware `friendlyName` stored in the physical Belkin WeMo switch's NVRAM
     * via `urn:Belkin:service:basicevent:1#ChangeFriendlyName`.
     */
    suspend fun changeFriendlyName(
        device: WeMoDeviceEntity,
        newFriendlyName: String
    ): SoapCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val escapedName = newFriendlyName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:ChangeFriendlyName xmlns:u="urn:Belkin:service:basicevent:1">
                  <FriendlyName>$escapedName</FriendlyName>
                </u:ChangeFriendlyName>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val healedResult = executeWithPortHealing(device) { port ->
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            val req = Request.Builder()
                .url(endpoint)
                .header("SOAPACTION", "\"urn:Belkin:service:basicevent:1#ChangeFriendlyName\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(1)
                val respXml = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@executeWithPortHealing SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = device.isOn,
                        brightness = device.brightness,
                        resolvedPort = port,
                        latencyMs = elapsed,
                        logEntry = SoapLogEntry(
                            timestamp = nowTime(),
                            deviceName = newFriendlyName,
                            endpoint = endpoint,
                            soapAction = "ChangeFriendlyName($newFriendlyName)",
                            statusText = "HTTP ${resp.code} OK (${elapsed}ms)",
                            xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                            isLiveLanSuccess = true
                        )
                    )
                }
            }
            null
        }
        if (healedResult != null) return@withContext healedResult

        val endpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = device.isOn,
            brightness = device.brightness,
            resolvedPort = device.port,
            latencyMs = 0,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = newFriendlyName,
                endpoint = endpoint,
                soapAction = "ChangeFriendlyName($newFriendlyName)",
                statusText = "Offline • Name saved locally only",
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            )
        )
    }

    /**
     * Configures Night Mode on Belkin WeMo Smart Dimmers (`F7C059` / `WDS060`) via `ConfigureNightMode`.
     */
    suspend fun configureNightMode(
        device: WeMoDeviceEntity,
        enabled: Boolean,
        startTimeSec: Int = 79200,
        endTimeSec: Int = 21600,
        nightBrightness: Int = 20
    ): SoapCallResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val modeInt = if (enabled) 1 else 0
        val soapBody = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:ConfigureNightMode xmlns:u="urn:Belkin:service:basicevent:1">
                  <NightMode>$modeInt</NightMode>
                  <NightModeBrightness>${nightBrightness.coerceIn(1, 100)}</NightModeBrightness>
                  <StartTime>$startTimeSec</StartTime>
                  <EndTime>$endTimeSec</EndTime>
                </u:ConfigureNightMode>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        val healedResult = executeWithPortHealing(device) { port ->
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            val req = Request.Builder()
                .url(endpoint)
                .header("SOAPACTION", "\"urn:Belkin:service:basicevent:1#ConfigureNightMode\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .header("Connection", "close")
                .post(soapBody.toRequestBody(xmlMediaType))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val elapsed = (System.currentTimeMillis() - startMs).toInt().coerceAtLeast(1)
                val respXml = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    return@executeWithPortHealing SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = device.isOn,
                        brightness = device.brightness,
                        resolvedPort = port,
                        latencyMs = elapsed,
                        logEntry = SoapLogEntry(
                            timestamp = nowTime(),
                            deviceName = device.name,
                            endpoint = endpoint,
                            soapAction = "ConfigureNightMode($modeInt, ${nightBrightness}%)",
                            statusText = "HTTP ${resp.code} OK (${elapsed}ms)",
                            xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                            isLiveLanSuccess = true
                        ),
                        nightModeEnabled = enabled
                    )
                }
            }
            null
        }
        if (healedResult != null) return@withContext healedResult

        val endpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = device.isOn,
            brightness = device.brightness,
            resolvedPort = device.port,
            latencyMs = 0,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = endpoint,
                soapAction = "ConfigureNightMode($modeInt)",
                statusText = "Offline • Timeout",
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            ),
            nightModeEnabled = device.nightModeEnabled
        )
    }

    /**
     * Fetches a full [SsdpDiscoveredDevice] descriptor from a known `IP:port` and enriches it with
     * live `GetBinaryState`, `GetMetaInfo` (`SKU` + 2.4 GHz `SSID`), and `GetSignalStrength` telemetry.
     */
    suspend fun fetchLiveDeviceDescriptor(
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(locationUrl)
                .header("Connection", "close")
                .get()
                .build()
            val xml = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string().orEmpty()
            }
            val isBelkinWeMo = xml.contains("Belkin", ignoreCase = true) ||
                xml.contains("WeMo", ignoreCase = true) ||
                xml.contains("WeMi", ignoreCase = true) ||
                xml.contains("basicevent", ignoreCase = true) ||
                stHeader.contains("Belkin", ignoreCase = true)
            if (!isBelkinWeMo) return@withContext null

            val base = parseSetupXmlDescriptor(
                xml = xml,
                locationUrl = locationUrl,
                usnHeader = usnHeader,
                stHeader = stHeader,
                responseTimeMs = responseTimeMs,
                isLiveUdp = isLiveUdp
            )

            val probeEntity = WeMoDeviceEntity(
                name = base.name,
                ipAddress = base.ipAddress,
                port = base.port,
                serialNumber = base.serialNumber,
                deviceType = base.deviceType.name
            )
            val stateProbe = getBinaryState(probeEntity)
            val meta = getMetaInfo(base.ipAddress, base.port)
            val rssi = getSignalStrength(base.ipAddress, base.port)

            val enrichedSku = if (meta != null && meta.deviceSku.isNotBlank()) {
                classifyHardwareSku(
                    modelName = base.modelName,
                    udn = base.usn,
                    serialNumber = base.serialNumber,
                    metaSku = meta.deviceSku
                )
            } else {
                base.hardwareSku
            }

            base.copy(
                isOn = stateProbe.binaryStateOn ?: base.isOn,
                brightness = stateProbe.brightness ?: base.brightness,
                serialNumber = meta?.serialNumber?.takeIf { it.isNotBlank() } ?: base.serialNumber,
                firmwareVersion = meta?.firmwareVersion?.takeIf { it.isNotBlank() } ?: base.firmwareVersion,
                hardwareSku = enrichedSku,
                macAddress = meta?.macAddress?.takeIf { it.isNotBlank() } ?: base.macAddress,
                wifiSsid = meta?.accessPointSsid.orEmpty(),
                signalStrengthPercent = rssi ?: 85
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Probes a single IP address across all 9 WeMo ports (`probeWemoPort`) and fetches its full
     * `/setup.xml` (or `/Lightsetup.xml`), `GetBinaryState`, `GetMetaInfo`, and `GetSignalStrength`
     * descriptor so newly added devices learn their real port, serial number, MAC, SKU, and state.
     */
    suspend fun probeAndFetchDeviceDescriptor(
        ipAddress: String,
        preferredPort: Int = 49153
    ): SsdpDiscoveredDevice? = withContext(Dispatchers.IO) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isEmpty()) return@withContext null
        val startMs = System.currentTimeMillis()
        val resolvedPort = probeWemoPort(
            ipAddress = cleanIp,
            preferredPort = preferredPort,
            expectedSerialOrUdn = null
        ) ?: return@withContext null
        val rtt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
            val desc = fetchLiveDeviceDescriptor(
                locationUrl = "http://$cleanIp:$resolvedPort$path",
                usnHeader = "",
                stHeader = "urn:Belkin:service:basicevent:1",
                responseTimeMs = rtt,
                isLiveUdp = false
            )
            if (desc != null) return@withContext desc
        }
        null
    }

    suspend fun scanSubnetUnicast(
        subnetPrefix: String,
        searchTarget: String = "urn:Belkin:service:basicevent:1"
    ): List<SsdpDiscoveredDevice> = ssdpEngine.scanSubnetUnicast(
        subnetPrefix = subnetPrefix,
        searchTarget = searchTarget
    )

    suspend fun performSsdpDiscoveryScan(
        searchTarget: String = "urn:Belkin:service:basicevent:1",
        knownLocalDevices: List<WeMoDeviceEntity> = emptyList(),
        includeDemoFallbacks: Boolean = false
    ): SsdpScanReport = ssdpEngine.performSsdpDiscoveryScan(
        searchTarget = searchTarget,
        knownLocalDevices = knownLocalDevices,
        includeDemoFallbacks = includeDemoFallbacks
    )
}
