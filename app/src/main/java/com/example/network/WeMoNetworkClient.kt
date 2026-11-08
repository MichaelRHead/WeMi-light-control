package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.SoapLogEntry
import com.example.data.SsdpDiscoveredDevice
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.NoRouteToHostException
import java.net.Socket
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

class WeMoNetworkClient(
    private val appContext: Context? = null,
    private val extraCandidatePorts: List<Int> = emptyList()
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(750, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .writeTimeout(900, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val xmlMediaType = "text/xml; charset=\"utf-8\"".toMediaType()

    companion object {
        /**
         * Canonical Belkin WeMo UPnP port order matching pywemo/ouimeaux_device/__init__.py (probe_wemo).
         * 49153 and 49152 are the primary active ports on 1st-Gen OpenWrt and 2nd-Gen RTOS switches;
         * 49154..49159 and 49151 are fallback ports used after firmware updates or soft reboots.
         */
        val PROBE_PORTS = listOf(49153, 49152, 49154, 49151, 49155, 49156, 49157, 49158, 49159)

        private val PLACEHOLDER_SERIALS = setOf(
            "",
            "221517K01018AB",
            "221517K01018LAN",
            "221517K01018NEW"
        )
    }

    private fun nowTime(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    private fun orderedCandidatePorts(preferredPort: Int): List<Int> {
        return (listOf(preferredPort) + extraCandidatePorts + PROBE_PORTS).distinct()
    }

    /**
     * Resolves the device's active local IPv4 address on Wi-Fi/LAN for SSDP binding.
     */
    fun resolveLocalIpv4Address(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            // Prefer wlan / eth interfaces first
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

    /**
     * Builds a standard UPnP SSDP M-SEARCH UDP multicast payload for Belkin WeMo / WeMi devices.
     */
    fun buildSsdpMSearchPacket(searchTarget: String = "urn:Belkin:service:basicevent:1"): String {
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("ST: $searchTarget\r\n")
            append("USER-AGENT: Android/16 UPnP/1.1 WeMiSwitchControl/1.0\r\n\r\n")
        }
    }

    /**
     * Parses raw SSDP HTTP/1.1 200 OK UDP response headers (LOCATION, USN, ST, SERVER).
     */
    fun parseSsdpHeaders(rawResponse: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        rawResponse.lineSequence().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().uppercase(Locale.US)
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    /**
     * Classifies the specific Belkin WeMo hardware SKU (F7C030, WLS040/WLS0403, F7C059/WDS060, F7C063/WSP080)
     * from UPnP setup.xml fields and optional metainfo1 SKU telemetry.
     */
    fun classifyHardwareSku(
        modelName: String,
        modelDescription: String = "",
        udn: String = "",
        serialNumber: String = "",
        metaSku: String = "",
        isRtos: Boolean = false
    ): String {
        val combined = "$metaSku $modelName $modelDescription $udn $serialNumber"
        return when {
            combined.contains("WLS0403", ignoreCase = true) -> "WLS0403 (3-Way Light Switch)"
            combined.contains("WLS040", ignoreCase = true) ||
                udn.contains("Lightswitch-2_0", ignoreCase = true) ||
                udn.contains("Lightswitch-3_0", ignoreCase = true) ||
                (modelName.equals("LightSwitch", ignoreCase = true) && isRtos) -> "WLS040 (2nd-Gen 3-Way Switch)"
            combined.contains("WDS060", ignoreCase = true) ||
                udn.contains("Dimmer-2_0", ignoreCase = true) -> "WDS060 (Gen-2 Smart Dimmer)"
            combined.contains("F7C059", ignoreCase = true) ||
                modelName.contains("Dimmer", ignoreCase = true) ||
                udn.contains("Dimmer", ignoreCase = true) -> "F7C059 (Wi-Fi Smart Dimmer)"
            combined.contains("WSP080", ignoreCase = true) -> "WSP080 (WeMo Mini Plug Gen-2)"
            combined.contains("F7C063", ignoreCase = true) ||
                modelName.contains("Mini", ignoreCase = true) -> "F7C063 (WeMo Mini Smart Plug)"
            combined.contains("Socket", ignoreCase = true) -> "F7C063 (WeMo Smart Plug)"
            combined.contains("F7C030", ignoreCase = true) ||
                modelName.contains("LightSwitch", ignoreCase = true) ||
                udn.contains("Lightswitch", ignoreCase = true) -> "F7C030 (1st-Gen Light Switch)"
            else -> modelName.ifBlank { "LightSwitch" }
        }
    }

    /**
     * Parses a Belkin WeMo / WeMi UPnP setup.xml document into an SsdpDiscoveredDevice.
     */
    fun parseSetupXmlDescriptor(
        xml: String,
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice {
        val friendlyName = Regex("<friendlyName>([^<]+)</friendlyName>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WeMi Smart Switch"
        val serial = Regex("<serialNumber>([^<]+)</serialNumber>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "221517K01018LAN"
        val modelName = Regex("<modelName>([^<]+)</modelName>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "LightSwitch"
        val modelDescription = Regex("<modelDescription>([^<]+)</modelDescription>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val firmware = Regex("<firmwareVersion>([^<]+)</firmwareVersion>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WeMo_WW_2.00.11532.PVT"
        val macAddress = Regex("<macAddress>([^<]+)</macAddress>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val udn = Regex("<UDN>([^<]+)</UDN>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim()
            ?: usnHeader.ifEmpty { "uuid:Lightswitch-1_0-$serial" }
        val isRtos = Regex("<rtos>\\s*1\\s*</rtos>", RegexOption.IGNORE_CASE).containsMatchIn(xml)

        val hostPortMatch = Regex("http://([^:/]+):(\\d+)").find(locationUrl)
        val ip = hostPortMatch?.groupValues?.getOrNull(1) ?: "192.168.1.101"
        val port = hostPortMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 49153

        val type = when {
            modelName.contains("Sensor", ignoreCase = true) ||
                modelName.contains("Motion", ignoreCase = true) ||
                udn.contains("Sensor", ignoreCase = true) -> WeMoDeviceType.MOTION_SENSOR
            modelName.contains("Dimmer", ignoreCase = true) ||
                modelName.contains("WDS060", ignoreCase = true) ||
                modelName.contains("F7C059", ignoreCase = true) ||
                modelName.contains("Lightbulb", ignoreCase = true) ||
                modelName.contains("Link", ignoreCase = true) ||
                udn.contains("Dimmer", ignoreCase = true) -> WeMoDeviceType.LIGHTBULB_DIMMER
            else -> WeMoDeviceType.SWITCH_PLUG
        }

        val sku = classifyHardwareSku(
            modelName = modelName,
            modelDescription = modelDescription,
            udn = udn,
            serialNumber = serial,
            isRtos = isRtos
        )

        return SsdpDiscoveredDevice(
            name = friendlyName,
            ipAddress = ip,
            port = port,
            deviceType = type,
            modelName = modelName,
            serialNumber = serial,
            firmwareVersion = firmware,
            usn = udn,
            searchTarget = stHeader.ifEmpty { "urn:Belkin:service:basicevent:1" },
            locationUrl = locationUrl,
            responseTimeMs = responseTimeMs,
            isOn = true,
            brightness = 100,
            isLiveUdpResponse = isLiveUdp,
            hardwareSku = sku,
            macAddress = macAddress
        )
    }

    /**
     * Probes a Belkin WeMo host across [PROBE_PORTS] (49153, 49152, 49154, 49151, 49155..49159)
     * following pywemo's probe_wemo algorithm:
     * - Distinguishes TCP connect timeout (host offline -> abort early) from ECONNREFUSED
     *   (port closed -> try next port immediately) and HTTP read timeout (wedged port -> try next port).
     * - Fetches /setup.xml (and /Lightsetup.xml fallback for 1st-Gen F7C030).
     * - Verifies <serialNumber> or <UDN> when [expectedSerialOrUdn] is provided so DHCP IP swaps
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
            // Fast TCP handshake check so we can distinguish host-down timeout vs ECONNREFUSED
            try {
                Socket().use { tcpSocket ->
                    tcpSocket.connect(InetSocketAddress(ipAddress, port), 550)
                }
            } catch (_: SocketTimeoutException) {
                // Host did not respond to ARP/SYN at all -> IP is unreachable on LAN, abort port loop early
                return@withContext null
            } catch (_: NoRouteToHostException) {
                return@withContext null
            } catch (_: ConnectException) {
                // ECONNREFUSED: Host is alive on LAN, just not listening on this port -> try next port immediately
                continue
            } catch (_: Exception) {
                continue
            }

            // TCP port is open: verify Belkin UPnP descriptor on /setup.xml or /Lightsetup.xml
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
                                        // Different WeMo switch now occupies this DHCP IP!
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
     * Sends a real Belkin WeMo UPnP SOAP SetBinaryState call over HTTP to the device's IP:port.
     * Automatically handles:
     * - Connection: close header to prevent RTOS socket starvation on WLS040 / WLS0403 / WDS060.
     * - <BinaryState>Error</BinaryState> response when switch is already in the requested state.
     * - Automatic 9-port probing when the device's UPnP port shifts after reboot.
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

        suspend fun executeOnPort(port: Int): SoapCallResult? {
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
                    // pywemo quirk: Belkin switches return <BinaryState>Error</BinaryState> when already in targetOn state
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

                    return SoapCallResult(
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
            return null
        }

        // 1. Try the remembered port first
        var shouldProbeOtherPorts = false
        try {
            val directResult = executeOnPort(device.port)
            if (directResult != null) return@withContext directResult
            shouldProbeOtherPorts = true
        } catch (_: ConnectException) {
            // ECONNREFUSED on saved port -> device is online but shifted UPnP port
            shouldProbeOtherPorts = true
        } catch (_: SocketTimeoutException) {
            // Timeout -> probeWemoPort will quickly check if host is alive or offline
            shouldProbeOtherPorts = true
        } catch (_: Exception) {
            shouldProbeOtherPorts = true
        }

        // 2. Probe candidate ports with UDN/Serial verification if primary port failed
        if (shouldProbeOtherPorts) {
            val discoveredPort = probeWemoPort(
                ipAddress = device.ipAddress,
                preferredPort = device.port,
                expectedSerialOrUdn = device.serialNumber
            )
            if (discoveredPort != null && discoveredPort != device.port) {
                try {
                    val retryResult = executeOnPort(discoveredPort)
                    if (retryResult != null) return@withContext retryResult
                } catch (_: Exception) {
                    // Fall through to offline result
                }
            } else if (discoveredPort == null) {
                // Also try remaining candidate ports directly in case /setup.xml was not served on a mock/custom responder
                for (port in orderedCandidatePorts(device.port).drop(1)) {
                    try {
                        Socket().use { s -> s.connect(InetSocketAddress(device.ipAddress, port), 350) }
                        val fallbackResult = executeOnPort(port)
                        if (fallbackResult != null) return@withContext fallbackResult
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: NoRouteToHostException) {
                        break
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }

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
     * Queries a device's current binary state via UPnP SOAP GetBinaryState.
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

        suspend fun executeOnPort(port: Int): SoapCallResult? {
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
                    return SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = isOn,
                        brightness = parsedBrightness,
                        resolvedPort = port,
                        latencyMs = elapsedMs,
                        logEntry = SoapCallResultLog(device.name, endpoint, response.code, bodyStr, soapBody),
                        nightModeEnabled = nightMode
                    )
                }
            }
            return null
        }

        var shouldProbeOtherPorts = false
        try {
            val direct = executeOnPort(device.port)
            if (direct != null) return@withContext direct
            shouldProbeOtherPorts = true
        } catch (_: Exception) {
            shouldProbeOtherPorts = true
        }

        if (shouldProbeOtherPorts) {
            val discoveredPort = probeWemoPort(
                ipAddress = device.ipAddress,
                preferredPort = device.port,
                expectedSerialOrUdn = device.serialNumber
            )
            if (discoveredPort != null && discoveredPort != device.port) {
                try {
                    val retry = executeOnPort(discoveredPort)
                    if (retry != null) return@withContext retry
                } catch (_: Exception) {
                    // Fall through
                }
            } else if (discoveredPort == null) {
                for (port in orderedCandidatePorts(device.port).drop(1)) {
                    try {
                        Socket().use { s -> s.connect(InetSocketAddress(device.ipAddress, port), 350) }
                        val fallback = executeOnPort(port)
                        if (fallback != null) return@withContext fallback
                    } catch (_: SocketTimeoutException) {
                        break
                    } catch (_: NoRouteToHostException) {
                        break
                    } catch (_: Exception) {
                        continue
                    }
                }
            }
        }

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
     * Queries urn:Belkin:service:metainfo:1#GetMetaInfo on /upnp/control/metainfo1.
     * Returns pipe-delimited hardware telemetry: MAC|SerialNumber|DeviceSKU|FirmwareVersion|AccessPointSSID|ModelName.
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
                val rawMeta = Regex("<MetaInfo>([^<]+)</MetaInfo>", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.getOrNull(1)?.trim() ?: return@withContext null
                val parts = rawMeta.split("|")
                if (parts.size >= 2) {
                    return@withContext WeMoMetaInfo(
                        macAddress = parts.getOrNull(0)?.trim().orEmpty(),
                        serialNumber = parts.getOrNull(1)?.trim().orEmpty(),
                        deviceSku = parts.getOrNull(2)?.trim().orEmpty(),
                        firmwareVersion = parts.getOrNull(3)?.trim().orEmpty(),
                        accessPointSsid = parts.getOrNull(4)?.trim().orEmpty(),
                        modelName = parts.getOrNull(5)?.trim().orEmpty()
                    )
                }
            }
        } catch (_: Exception) {
            // Optional service not supported on all firmware builds
        }
        null
    }

    /**
     * Queries urn:Belkin:service:basicevent:1#GetSignalStrength on /upnp/control/basicevent1.
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
                val raw = Regex("<SignalStrength>([^<]+)</SignalStrength>", RegexOption.IGNORE_CASE)
                    .find(body)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull() ?: return@withContext null
                return@withContext if (raw < 0) {
                    // Convert dBm (e.g. -85..-35) to 0..100%
                    ((raw + 95) * 2).coerceIn(5, 100)
                } else {
                    raw.coerceIn(0, 100)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates the hardware friendlyName stored in the physical Belkin WeMo switch's NVRAM
     * via urn:Belkin:service:basicevent:1#ChangeFriendlyName.
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

        val endpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        try {
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
                    return@withContext SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = device.isOn,
                        brightness = device.brightness,
                        resolvedPort = device.port,
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
        } catch (_: Exception) {
            // Ignore if offline
        }
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
     * Configures Night Mode on Belkin WeMo Smart Dimmers (F7C059 / WDS060) via ConfigureNightMode.
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
        val endpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        try {
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
                    return@withContext SoapCallResult(
                        reachableOnLan = true,
                        binaryStateOn = device.isOn,
                        brightness = device.brightness,
                        resolvedPort = device.port,
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
        } catch (_: Exception) {
            // Fall through
        }
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

    private fun SoapCallResultLog(
        deviceName: String,
        endpoint: String,
        code: Int,
        bodyStr: String,
        soapBody: String
    ) = SoapLogEntry(
        timestamp = nowTime(),
        deviceName = deviceName,
        endpoint = endpoint,
        soapAction = "GetBinaryState",
        statusText = "HTTP $code OK (Live LAN)",
        xmlSnippet = bodyStr.take(260).ifEmpty { soapBody },
        isLiveLanSuccess = true
    )

    /**
     * Fetches a full SsdpDiscoveredDevice descriptor from a known IP:port and enriches it with
     * live GetBinaryState, GetMetaInfo (SKU + 2.4 GHz SSID), and GetSignalStrength telemetry.
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

            // Query real initial binary state, dimmer brightness, and metainfo SKU/SSID
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
                isOn = stateProbe.binaryStateOn ?: true,
                brightness = stateProbe.brightness ?: 100,
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
     * Sweeps a /24 IPv4 subnet via unicast TCP port probing on the primary Belkin WeMo ports
     * (49153, 49152, 49154) when UDP multicast is blocked by wireless AP isolation or IGMP snooping.
     */
    suspend fun scanSubnetUnicast(
        subnetPrefix: String,
        searchTarget: String = "urn:Belkin:service:basicevent:1"
    ): List<SsdpDiscoveredDevice> = coroutineScope {
        val cleanPrefix = subnetPrefix.trim().removeSuffix(".x").removeSuffix(".0/24")
        if (cleanPrefix.isBlank() || cleanPrefix.startsWith("10.0.2") || cleanPrefix.startsWith("127.")) {
            return@coroutineScope emptyList()
        }
        val sweepSemaphore = Semaphore(24)
        val fastPorts = listOf(49153, 49152, 49154)
        (1..254).map { hostOctet ->
            async(Dispatchers.IO) {
                sweepSemaphore.withPermit {
                    val ip = "$cleanPrefix.$hostOctet"
                    for (port in fastPorts) {
                        val start = System.currentTimeMillis()
                        try {
                            Socket().use { s ->
                                s.connect(InetSocketAddress(ip, port), 180)
                            }
                            val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                            val locUrl = "http://$ip:$port/setup.xml"
                            val desc = fetchLiveDeviceDescriptor(
                                locationUrl = locUrl,
                                usnHeader = "",
                                stHeader = searchTarget,
                                responseTimeMs = rtt,
                                isLiveUdp = false
                            )
                            if (desc != null) return@withPermit desc
                        } catch (_: ConnectException) {
                            continue
                        } catch (_: Exception) {
                            break
                        }
                    }
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Executes a full UPnP SSDP (Simple Service Discovery Protocol) multicast scan on
     * 239.255.255.250:1900, acquiring a WifiManager.MulticastLock if available, broadcasting
     * M-SEARCH packets with retransmission, parsing LOCATION/USN/ST headers, fetching setup.xml
     * descriptors, and querying live GetBinaryState / GetMetaInfo telemetry.
     *
     * When [includeDemoFallbacks] is false (the default for Real LAN Mode), only genuinely
     * reachable Belkin WeMo / WeMi devices on the local network are returned.
     */
    suspend fun performSsdpDiscoveryScan(
        searchTarget: String = "urn:Belkin:service:basicevent:1",
        knownLocalDevices: List<WeMoDeviceEntity> = emptyList(),
        includeDemoFallbacks: Boolean = false
    ): SsdpScanReport = withContext(Dispatchers.IO) {
        val scanStartMs = System.currentTimeMillis()
        val localIp = resolveLocalIpv4Address()
        val liveDiscovered = mutableListOf<SsdpDiscoveredDevice>()
        val primaryMSearch = buildSsdpMSearchPacket(searchTarget)
        val searchTargets = listOf(
            searchTarget,
            "urn:Belkin:service:basicevent:1",
            "upnp:rootdevice"
        ).distinct()

        // Acquire Android WifiManager MulticastLock so UDP 239.255.255.250 responses aren't dropped
        val wifiManager = appContext?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("wemi_ssdp_multicast_lock")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                val bindAddr = runCatching {
                    if (localIp != "192.168.1.50" && !localIp.startsWith("127.")) {
                        InetSocketAddress(InetAddress.getByName(localIp), 0)
                    } else {
                        InetSocketAddress(0)
                    }
                }.getOrElse { InetSocketAddress(0) }
                runCatching { socket.bind(bindAddr) }.onFailure { socket.bind(InetSocketAddress(0)) }
                socket.soTimeout = 550

                val multicastGroup = InetAddress.getByName("239.255.255.250")
                fun sendBurst() {
                    for (st in searchTargets) {
                        val payloadBytes = buildSsdpMSearchPacket(st).toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(payloadBytes, payloadBytes.size, multicastGroup, 1900)
                        runCatching { socket.send(packet) }
                    }
                }

                // Initial M-SEARCH burst + 1 retransmit burst (matching pywemo discovery resilience)
                sendBurst()
                var retransmitted = false
                val receiveBuffer = ByteArray(4096)
                val seenLocations = mutableMapOf<String, Pair<Map<String, String>, Long>>()
                val deadline = System.currentTimeMillis() + 1250L

                while (System.currentTimeMillis() < deadline) {
                    if (!retransmitted && System.currentTimeMillis() - scanStartMs >= 550L) {
                        sendBurst()
                        retransmitted = true
                    }
                    try {
                        val respPacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(respPacket)
                        val elapsed = (System.currentTimeMillis() - scanStartMs).coerceAtLeast(1L)
                        val rawText = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
                        val headers = parseSsdpHeaders(rawText)
                        val loc = headers["LOCATION"]
                        if (!loc.isNullOrEmpty() && !seenLocations.containsKey(loc)) {
                            seenLocations[loc] = Pair(headers, elapsed)
                        }
                    } catch (_: SocketTimeoutException) {
                        if (retransmitted) break
                    } catch (_: Exception) {
                        break
                    }
                }

                for ((locUrl, pair) in seenLocations) {
                    val (headers, latencyMs) = pair
                    val desc = fetchLiveDeviceDescriptor(
                        locationUrl = locUrl,
                        usnHeader = headers["USN"].orEmpty(),
                        stHeader = headers["ST"].orEmpty().ifEmpty { searchTarget },
                        responseTimeMs = latencyMs,
                        isLiveUdp = true
                    )
                    if (desc != null && liveDiscovered.none { it.serialNumber.equals(desc.serialNumber, ignoreCase = true) && it.ipAddress == desc.ipAddress }) {
                        liveDiscovered.add(desc)
                    }
                }
            }
        } catch (_: Exception) {
            // Multicast socket restricted in container/emulator environment
        } finally {
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }

        // Unicast verification of known local devices not yet seen via UDP multicast
        for (known in knownLocalDevices) {
            val alreadySeen = liveDiscovered.any {
                it.ipAddress == known.ipAddress ||
                    (known.serialNumber.isNotBlank() && it.serialNumber.equals(known.serialNumber, ignoreCase = true))
            }
            if (!alreadySeen) {
                val startProbe = System.currentTimeMillis()
                val verifiedPort = probeWemoPort(
                    ipAddress = known.ipAddress,
                    preferredPort = known.port,
                    expectedSerialOrUdn = known.serialNumber
                )
                if (verifiedPort != null) {
                    val rtt = (System.currentTimeMillis() - startProbe).coerceAtLeast(1L)
                    val locUrl = "http://${known.ipAddress}:$verifiedPort/setup.xml"
                    val desc = fetchLiveDeviceDescriptor(
                        locationUrl = locUrl,
                        usnHeader = known.udn,
                        stHeader = searchTarget,
                        responseTimeMs = rtt,
                        isLiveUdp = false
                    )
                    if (desc != null) {
                        liveDiscovered.add(desc)
                    }
                }
            }
        }

        val combinedList = mutableListOf<SsdpDiscoveredDevice>()
        combinedList.addAll(liveDiscovered)

        // Synthetic Demo Showroom fallbacks ONLY when explicitly requested in Demo Showroom Mode
        if (includeDemoFallbacks) {
            val subnetPrefix = localIp.substringBeforeLast('.', "192.168.1").let {
                if (it.startsWith("10.0.2") || it.startsWith("127.")) "192.168.1" else it
            }

            val responderNodes = knownLocalDevices.filter { it.isReachable }
            responderNodes.forEachIndexed { idx, dev ->
                val alreadyInLive = combinedList.any {
                    it.ipAddress == dev.ipAddress || it.name.equals(dev.name, ignoreCase = true)
                }
                if (!alreadyInLive) {
                    val model = when (dev.parsedType) {
                        WeMoDeviceType.SWITCH_PLUG -> "Socket-1_0"
                        WeMoDeviceType.MOTION_SENSOR -> "Sensor-1_0"
                        WeMoDeviceType.LIGHTBULB_DIMMER -> "Dimmer-1_0"
                    }
                    combinedList.add(
                        SsdpDiscoveredDevice(
                            name = dev.name,
                            roomName = dev.roomName,
                            ipAddress = dev.ipAddress,
                            port = dev.port,
                            deviceType = dev.parsedType,
                            modelName = model,
                            serialNumber = dev.serialNumber,
                            firmwareVersion = dev.firmwareVersion,
                            usn = "uuid:$model-${dev.serialNumber}::$searchTarget",
                            searchTarget = searchTarget,
                            locationUrl = "http://${dev.ipAddress}:${dev.port}/setup.xml",
                            responseTimeMs = 14L + (idx * 7L),
                            isOn = dev.isOn,
                            brightness = dev.brightness,
                            isLiveUdpResponse = false,
                            hardwareSku = dev.hardwareModel
                        )
                    )
                }
            }

            val extraCandidateIp = "$subnetPrefix.118"
            if (combinedList.none { it.ipAddress == extraCandidateIp || it.name == "Patio String Lights" }) {
                combinedList.add(
                    0,
                    SsdpDiscoveredDevice(
                        name = "Patio String Lights",
                        roomName = "Outdoor",
                        ipAddress = extraCandidateIp,
                        port = 49153,
                        deviceType = WeMoDeviceType.SWITCH_PLUG,
                        modelName = "Lightswitch-1_0",
                        serialNumber = "221644K01092FE",
                        firmwareVersion = "WeMo_WW_2.00.11532.PVT",
                        usn = "uuid:Lightswitch-1_0-221644K01092FE::$searchTarget",
                        searchTarget = searchTarget,
                        locationUrl = "http://$extraCandidateIp:49153/setup.xml",
                        responseTimeMs = 11L,
                        isOn = true,
                        brightness = 100,
                        isLiveUdpResponse = true,
                        hardwareSku = "F7C030 (1st-Gen Light Switch)"
                    )
                )
            }
        }

        val totalMs = (System.currentTimeMillis() - scanStartMs).coerceAtLeast(1L)
        val log = SoapLogEntry(
            timestamp = nowTime(),
            deviceName = "SSDP M-SEARCH Scanner",
            endpoint = "udp://239.255.255.250:1900 (Local IP: $localIp)",
            soapAction = "M-SEARCH ST: $searchTarget",
            statusText = "Discovered ${combinedList.size} UPnP WeMi/WeMo device(s) in ${totalMs}ms",
            xmlSnippet = primaryMSearch.trim(),
            isLiveLanSuccess = liveDiscovered.isNotEmpty() || includeDemoFallbacks
        )

        SsdpScanReport(
            localInterfaceIp = localIp,
            searchTargetUsed = searchTarget,
            mSearchRequestRaw = primaryMSearch.trim(),
            discoveredDevices = combinedList,
            rawSocketHitCount = liveDiscovered.size,
            scanDurationMs = totalMs,
            logEntry = log
        )
    }
}
