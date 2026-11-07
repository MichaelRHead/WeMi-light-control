package com.example.network

import com.example.data.SoapLogEntry
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
import java.net.InetAddress
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

data class DiscoveredWeMoDevice(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val deviceType: WeMoDeviceType,
    val serialNumber: String
)

class WeMoNetworkClient {
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
     * Sends a real Belkin WeMo UPnP SOAP SetBinaryState call over HTTP to the device's IP:port.
     * Probes fallback ports (49152..49155) if the primary port fails to connect.
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
                // Try next port or fall back to local controller state
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
                        // WeMo dimmers sometimes return "1|85|..." where token[0] is binary state
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
                            logEntry = SoapLogEntry(
                                timestamp = nowTime(),
                                deviceName = device.name,
                                endpoint = endpoint,
                                soapAction = "GetBinaryState",
                                statusText = "HTTP ${response.code} OK (Live LAN)",
                                xmlSnippet = bodyStr.take(260).ifEmpty { soapBody },
                                isLiveLanSuccess = true
                            )
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

    /**
     * Broadcasts an SSDP M-SEARCH UDP multicast packet to 239.255.255.250:1900 to discover
     * any physical Belkin WeMo / WeMi UPnP switches on the user's Wi-Fi network, and fetches
     * their setup.xml descriptor.
     */
    suspend fun discoverDevicesOnLan(): Pair<List<DiscoveredWeMoDevice>, SoapLogEntry> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredWeMoDevice>()
        val ssdpPayload = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: urn:Belkin:service:basicevent:1\r\n\r\n")
        }

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1100
                val group = InetAddress.getByName("239.255.255.250")
                val data = ssdpPayload.toByteArray(Charsets.UTF_8)
                val packet = DatagramPacket(data, data.size, group, 1900)
                socket.send(packet)

                val receiveBuffer = ByteArray(4096)
                val locations = mutableSetOf<String>()
                val deadline = System.currentTimeMillis() + 1200L
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val respPacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(respPacket)
                        val respText = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
                        val locationMatch = Regex("(?i)LOCATION:\\s*(http://[^\\r\\n]+)")
                            .find(respText)?.groupValues?.getOrNull(1)?.trim()
                        if (!locationMatch.isNullOrEmpty()) {
                            locations.add(locationMatch)
                        }
                    } catch (_: Exception) {
                        break
                    }
                }

                for (loc in locations) {
                    try {
                        val req = Request.Builder().url(loc).get().build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val xml = resp.body?.string().orEmpty()
                                val friendlyName = Regex("<friendlyName>([^<]+)</friendlyName>")
                                    .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WeMo Switch"
                                val serial = Regex("<serialNumber>([^<]+)</serialNumber>")
                                    .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WEMO-LAN"
                                val modelName = Regex("<modelName>([^<]+)</modelName>")
                                    .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                val hostPortMatch = Regex("http://([^:/]+):(\\d+)")
                                    .find(loc)
                                val ip = hostPortMatch?.groupValues?.getOrNull(1) ?: "192.168.1.120"
                                val port = hostPortMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 49153
                                val type = when {
                                    modelName.contains("Sensor", ignoreCase = true) ||
                                        modelName.contains("Motion", ignoreCase = true) -> WeMoDeviceType.MOTION_SENSOR
                                    modelName.contains("Dimmer", ignoreCase = true) ||
                                        modelName.contains("Lightbulb", ignoreCase = true) ||
                                        modelName.contains("Link", ignoreCase = true) -> WeMoDeviceType.LIGHTBULB_DIMMER
                                    else -> WeMoDeviceType.SWITCH_PLUG
                                }
                                discovered.add(
                                    DiscoveredWeMoDevice(
                                        name = friendlyName,
                                        ipAddress = ip,
                                        port = port,
                                        deviceType = type,
                                        serialNumber = serial
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Skip malformed setup.xml
                    }
                }
            }
        } catch (_: Exception) {
            // Multicast socket restricted or offline
        }

        val log = SoapLogEntry(
            timestamp = nowTime(),
            deviceName = "SSDP Multicast Discovery",
            endpoint = "udp://239.255.255.250:1900",
            soapAction = "M-SEARCH urn:Belkin:service:basicevent:1",
            statusText = if (discovered.isNotEmpty()) {
                "Found ${discovered.size} live WeMo device(s) on Wi-Fi"
            } else {
                "SSDP Broadcast Complete (0 new external devices; local list active)"
            },
            xmlSnippet = ssdpPayload.trim(),
            isLiveLanSuccess = discovered.isNotEmpty()
        )
        Pair(discovered, log)
    }
}
