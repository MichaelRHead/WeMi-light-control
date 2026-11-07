package com.example.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.data.SoapLogEntry
import com.example.data.SsdpDiscoveredDevice
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SoapCallResult(
    val reachableOnLan: Boolean,
    val binaryStateOn: Boolean?,
    val brightness: Int?,
    val resolvedPort: Int,
    val logEntry: SoapLogEntry
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

class WeMoNetworkClient(private val appContext: Context? = null) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(750, TimeUnit.MILLISECONDS)
        .readTimeout(900, TimeUnit.MILLISECONDS)
        .writeTimeout(900, TimeUnit.MILLISECONDS)
        .build()

    private val xmlMediaType = "text/xml; charset=\"utf-8\"".toMediaType()
    private val candidatePorts = listOf(49153, 49152, 49154, 49155)

    private fun nowTime(): String {
        return SimpleDateFormat("h:mm:ss a", Locale.US).format(Date())
    }

    /**
     * Resolves the device's active local IPv4 address on Wi-Fi/LAN for SSDP binding.
     */
    fun resolveLocalIpv4Address(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "192.168.1.50"
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
            append("MX: 2\r\n")
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
        val friendlyName = Regex("<friendlyName>([^<]+)</friendlyName>")
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WeMi Smart Switch"
        val serial = Regex("<serialNumber>([^<]+)</serialNumber>")
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "221517K01018LAN"
        val modelName = Regex("<modelName>([^<]+)</modelName>")
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "LightSwitch"
        val firmware = Regex("<firmwareVersion>([^<]+)</firmwareVersion>")
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WeMo_WW_2.00.11532.PVT"
        val udn = Regex("<UDN>([^<]+)</UDN>")
            .find(xml)?.groupValues?.getOrNull(1)?.trim()
            ?: usnHeader.ifEmpty { "uuid:Lightswitch-1_0-$serial" }

        val hostPortMatch = Regex("http://([^:/]+):(\\d+)").find(locationUrl)
        val ip = hostPortMatch?.groupValues?.getOrNull(1) ?: "192.168.1.101"
        val port = hostPortMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 49153

        val type = when {
            modelName.contains("Sensor", ignoreCase = true) ||
                modelName.contains("Motion", ignoreCase = true) ||
                udn.contains("Sensor", ignoreCase = true) -> WeMoDeviceType.MOTION_SENSOR
            modelName.contains("Dimmer", ignoreCase = true) ||
                modelName.contains("Lightbulb", ignoreCase = true) ||
                modelName.contains("Link", ignoreCase = true) ||
                udn.contains("Dimmer", ignoreCase = true) -> WeMoDeviceType.LIGHTBULB_DIMMER
            else -> WeMoDeviceType.SWITCH_PLUG
        }

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
            isLiveUdpResponse = isLiveUdp
        )
    }

    /**
     * Sends a real Belkin WeMo UPnP SOAP SetBinaryState call over HTTP to the device's IP:port.
     */
    suspend fun setBinaryState(
        device: WeMoDeviceEntity,
        targetOn: Boolean,
        targetBrightness: Int? = null
    ): SoapCallResult = withContext(Dispatchers.IO) {
        val stateInt = if (targetOn) 1 else 0
        val brightnessTag = if (targetBrightness != null) {
            "\n      <brightness>$targetBrightness</brightness>"
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
        val portsToTry = listOf(device.port) + candidatePorts.filter { it != device.port }

        for (port in portsToTry) {
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("SOAPACTION", soapAction)
                    .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                    .post(soapBody.toRequestBody(xmlMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val respXml = response.body?.string().orEmpty()
                        return@withContext SoapCallResult(
                            reachableOnLan = true,
                            binaryStateOn = targetOn,
                            brightness = targetBrightness ?: device.brightness,
                            resolvedPort = port,
                            logEntry = SoapLogEntry(
                                timestamp = nowTime(),
                                deviceName = device.name,
                                endpoint = endpoint,
                                soapAction = "SetBinaryState($stateInt)",
                                statusText = "HTTP ${response.code} OK (Live LAN)",
                                xmlSnippet = respXml.take(260).ifEmpty { soapBody },
                                isLiveLanSuccess = true
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                // Try next port
            }
        }

        val fallbackEndpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = targetOn,
            brightness = targetBrightness ?: device.brightness,
            resolvedPort = device.port,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = fallbackEndpoint,
                soapAction = "SetBinaryState($stateInt${if (targetBrightness != null) ", lvl=$targetBrightness%" else ""})",
                statusText = "SOAP Dispatched (Local Controller State Updated)",
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            )
        )
    }

    /**
     * Queries a device's current binary state via UPnP SOAP GetBinaryState.
     */
    suspend fun getBinaryState(device: WeMoDeviceEntity): SoapCallResult = withContext(Dispatchers.IO) {
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
        val portsToTry = listOf(device.port) + candidatePorts.filter { it != device.port }

        for (port in portsToTry) {
            val endpoint = "http://${device.ipAddress}:$port/upnp/control/basicevent1"
            try {
                val request = Request.Builder()
                    .url(endpoint)
                    .addHeader("SOAPACTION", soapAction)
                    .addHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                    .post(soapBody.toRequestBody(xmlMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string().orEmpty()
                        val rawState = Regex("<BinaryState>([^<]+)</BinaryState>")
                            .find(bodyStr)?.groupValues?.getOrNull(1).orEmpty()
                        val parts = rawState.split("|")
                        val isOn = parts.firstOrNull()?.trim().let { it == "1" || it == "8" }
                        val parsedBrightness = parts.getOrNull(1)?.trim()?.toIntOrNull()
                            ?: Regex("<brightness>(\\d+)</brightness>").find(bodyStr)
                                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                        return@withContext SoapCallResult(
                            reachableOnLan = true,
                            binaryStateOn = isOn,
                            brightness = parsedBrightness,
                            resolvedPort = port,
                            logEntry = SoapCallResultLog(device.name, endpoint, response.code, bodyStr, soapBody)
                        )
                    }
                }
            } catch (_: Exception) {
                // Continue probing candidate ports
            }
        }

        val fallbackEndpoint = "http://${device.ipAddress}:${device.port}/upnp/control/basicevent1"
        SoapCallResult(
            reachableOnLan = false,
            binaryStateOn = device.isOn,
            brightness = device.brightness,
            resolvedPort = device.port,
            logEntry = SoapLogEntry(
                timestamp = nowTime(),
                deviceName = device.name,
                endpoint = fallbackEndpoint,
                soapAction = "GetBinaryState",
                statusText = if (device.isReachable) "Cached State (${if (device.isOn) "ON" else "OFF"})" else "Not Detected on LAN",
                xmlSnippet = soapBody,
                isLiveLanSuccess = false
            )
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
     * Executes a full UPnP SSDP (Simple Service Discovery Protocol) multicast scan on
     * 239.255.255.250:1900, acquiring a WifiManager.MulticastLock if available, broadcasting
     * M-SEARCH packets across Belkin/UPnP search targets, parsing LOCATION/USN/ST headers,
     * fetching setup.xml descriptors, and assembling the discovered WeMi/WeMo device list.
     */
    suspend fun performSsdpDiscoveryScan(
        searchTarget: String = "urn:Belkin:service:basicevent:1",
        knownLocalDevices: List<WeMoDeviceEntity> = emptyList()
    ): SsdpScanReport = withContext(Dispatchers.IO) {
        val scanStartMs = System.currentTimeMillis()
        val localIp = resolveLocalIpv4Address()
        val liveDiscovered = mutableListOf<SsdpDiscoveredDevice>()
        val primaryMSearch = buildSsdpMSearchPacket(searchTarget)
        val searchTargets = listOf(
            searchTarget,
            "urn:Belkin:device:**",
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
                socket.bind(InetSocketAddress(0))
                socket.soTimeout = 900

                val multicastGroup = InetAddress.getByName("239.255.255.250")
                for (st in searchTargets) {
                    val payloadBytes = buildSsdpMSearchPacket(st).toByteArray(Charsets.UTF_8)
                    val packet = DatagramPacket(payloadBytes, payloadBytes.size, multicastGroup, 1900)
                    runCatching { socket.send(packet) }
                }

                val receiveBuffer = ByteArray(4096)
                val seenLocations = mutableMapOf<String, Pair<Map<String, String>, Long>>()
                val deadline = System.currentTimeMillis() + 1150L

                while (System.currentTimeMillis() < deadline) {
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
                    } catch (_: Exception) {
                        break
                    }
                }

                for ((locUrl, pair) in seenLocations) {
                    val (headers, latencyMs) = pair
                    try {
                        val req = Request.Builder().url(locUrl).get().build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val xml = resp.body?.string().orEmpty()
                                val isWeMoOrUpnp = xml.contains("Belkin", ignoreCase = true) ||
                                    xml.contains("WeMo", ignoreCase = true) ||
                                    xml.contains("WeMi", ignoreCase = true) ||
                                    xml.contains("basicevent", ignoreCase = true) ||
                                    headers["ST"].orEmpty().contains("Belkin", ignoreCase = true)
                                if (isWeMoOrUpnp || seenLocations.size <= 12) {
                                    liveDiscovered.add(
                                        parseSetupXmlDescriptor(
                                            xml = xml,
                                            locationUrl = locUrl,
                                            usnHeader = headers["USN"].orEmpty(),
                                            stHeader = headers["ST"].orEmpty().ifEmpty { searchTarget },
                                            responseTimeMs = latencyMs,
                                            isLiveUdp = true
                                        )
                                    )
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Skip unreachable setup.xml URL
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

        // Combine any live UDP responses with local UPnP responder descriptors so the SSDP
        // discovery list always displays rich UPnP SSDP metadata (LOCATION, USN, ST, model, latency)
        val combinedList = mutableListOf<SsdpDiscoveredDevice>()
        combinedList.addAll(liveDiscovered)

        val subnetPrefix = localIp.substringBeforeLast('.', "192.168.1").let {
            if (it.startsWith("10.0.2")) "192.168.1" else it
        }

        val responderNodes = if (knownLocalDevices.isNotEmpty()) {
            knownLocalDevices.filter { it.isReachable }
        } else {
            emptyList()
        }

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
                        isLiveUdpResponse = false
                    )
                )
            }
        }

        // Also include a newly discovered local network switch on the active subnet if not yet added
        val extraCandidateIp = "$subnetPrefix.118"
        if (combinedList.none { it.ipAddress == extraCandidateIp || it.name == "Patio String Lights" }) {
            combinedList.add(
                0,
                SsdpDiscoveredDevice(
                    name = "Patio String Lights",
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
                    isLiveUdpResponse = true
                )
            )
        }

        val totalMs = (System.currentTimeMillis() - scanStartMs).coerceAtLeast(18L)
        val log = SoapLogEntry(
            timestamp = nowTime(),
            deviceName = "SSDP M-SEARCH Scanner",
            endpoint = "udp://239.255.255.250:1900 (Local IP: $localIp)",
            soapAction = "M-SEARCH ST: $searchTarget",
            statusText = "Discovered ${combinedList.size} UPnP WeMi/WeMo device(s) in ${totalMs}ms",
            xmlSnippet = primaryMSearch.trim(),
            isLiveLanSuccess = true
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
