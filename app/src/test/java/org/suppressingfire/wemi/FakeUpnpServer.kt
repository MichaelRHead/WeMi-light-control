package org.suppressingfire.wemi

import org.suppressingfire.wemi.network.NetworkClient
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Reusable loopback HTTP / UPnP / SOAP / GENA / `WiFiSetup1` smart switch hardware simulator
 * bound to `127.0.0.1` on an ephemeral port (`ServerSocket(0)`).
 *
 * Used across protocol unit tests and full-stack Jetpack Compose + Robolectric E2E tests
 * to verify real socket/HTTP/SOAP serialization without requiring physical LAN hardware.
 */
class FakeUpnpServer(
    initialFriendlyName: String = "Hallway 3-Way Switch",
    val deviceTypeUrn: String = "urn:Belkin:device:lightswitch:1",
    val modelName: String = "WLS0403",
    val serialNumber: String = "WLS0403SN9988",
    val firmwareVersion: String = "WeMo_WW_2.00.12000.PVT",
    val udn: String = "uuid:Lightswitch-3_0-WLS0403SN9988",
    val macAddress: String = "EC1A59AA1122",
    val isRtos: Boolean = true,
    val signalStrengthPercent: Int = 89,
    val wifiSsid: String = "HeadFamily-2.4G",
    initialRelayOn: Boolean = false,
    initialBrightness: Int = 64,
    enableSsdpResponder: Boolean = false,
    registerAsCandidatePort: Boolean = false
) : AutoCloseable {

    val relayStateOn = AtomicBoolean(initialRelayOn)
    val dimmerBrightness = AtomicInteger(initialBrightness)
    val friendlyName = AtomicReference(initialFriendlyName)
    val nightModeEnabled = AtomicBoolean(false)

    val returnErrorOnDuplicateSet = AtomicBoolean(false)
    val failSetupXml404 = AtomicBoolean(false)
    val failGetApList500 = AtomicBoolean(false)
    val simulate412OnNextGenaRenew = AtomicBoolean(false)
    val forceNetworkStatusZero = AtomicBoolean(false)

    val connectHomeNetworkCalls = AtomicInteger(0)
    val networkStatusPolls = AtomicInteger(0)
    val closeSetupCalled = AtomicBoolean(false)
    val setupDoneCalled = AtomicBoolean(false)
    val lastGenaCallbackPort = AtomicInteger(0)
    val receivedEncryptedPassword = AtomicReference("")
    val receivedSoapActions = CopyOnWriteArrayList<String>()
    val receivedSsdpSearches = CopyOnWriteArrayList<String>()

    private val running = AtomicBoolean(true)
    private val ssdpDiscoverable = AtomicBoolean(enableSsdpResponder)
    private val sidSequence = AtomicInteger(100)
    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort

    private val ssdpUdpSocket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
    val ssdpUdpPort: Int = ssdpUdpSocket.localPort

    init {
        if (enableSsdpResponder) {
            NetworkClient.registerLoopbackSsdpPort(ssdpUdpPort)
        }
        if (registerAsCandidatePort) {
            NetworkClient.registerLoopbackCandidatePort(port)
        }
    }

    fun setCandidatePortDiscoverable(enabled: Boolean) {
        if (enabled) {
            NetworkClient.registerLoopbackCandidatePort(port)
        } else {
            NetworkClient.unregisterLoopbackCandidatePort(port)
        }
    }

    fun setSsdpDiscoverable(enabled: Boolean) {
        ssdpDiscoverable.set(enabled)
        if (enabled) {
            NetworkClient.registerLoopbackSsdpPort(ssdpUdpPort)
        } else {
            NetworkClient.unregisterLoopbackSsdpPort(ssdpUdpPort)
        }
    }

    private val ssdpResponderThread = thread(start = true, isDaemon = true, name = "FakeSsdp-$ssdpUdpPort") {
        val buf = ByteArray(2048)
        while (running.get() && !ssdpUdpSocket.isClosed) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                ssdpUdpSocket.receive(packet)
                if (!ssdpDiscoverable.get()) continue
                val reqText = String(packet.data, 0, packet.length, Charsets.UTF_8)
                if (reqText.contains("M-SEARCH", ignoreCase = true)) {
                    val stMatch = Regex("(?m)^ST:\\s*(.+)$", RegexOption.IGNORE_CASE)
                        .find(reqText)?.groupValues?.getOrNull(1)?.trim()
                        ?: "urn:Belkin:service:basicevent:1"
                    receivedSsdpSearches.add(stMatch)
                    val responseText = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("CACHE-CONTROL: max-age=86400\r\n")
                        append("EXT:\r\n")
                        append("LOCATION: http://127.0.0.1:$port/setup.xml\r\n")
                        append("OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n")
                        append("ST: $stMatch\r\n")
                        append("USN: $udn::$stMatch\r\n\r\n")
                    }
                    val respBytes = responseText.toByteArray(Charsets.UTF_8)
                    val reply = DatagramPacket(respBytes, respBytes.size, packet.socketAddress)
                    ssdpUdpSocket.send(reply)
                }
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private val serverThread = thread(start = true, isDaemon = true, name = "FakeUpnpServer-$port") {
        while (running.get() && !serverSocket.isClosed) {
            try {
                serverSocket.accept().use { sock ->
                    handleConnection(sock)
                }
            } catch (_: Exception) {
                if (!running.get()) break
            }
        }
    }

    private fun handleConnection(sock: Socket) {
        sock.soTimeout = 2000
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = readHttpLine(input) ?: return
        var contentLength = 0
        var soapAction = ""
        var hasCallback = false
        while (true) {
            val header = readHttpLine(input) ?: break
            if (header.isEmpty()) break
            when {
                header.startsWith("Content-Length:", ignoreCase = true) -> {
                    contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                header.startsWith("SOAPACTION:", ignoreCase = true) -> {
                    soapAction = header.substringAfter(":").trim()
                }
                header.startsWith("CALLBACK:", ignoreCase = true) -> {
                    hasCallback = true
                    val parsedPort = Regex(":(\\d+)/").find(header)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (parsedPort != null) {
                        lastGenaCallbackPort.set(parsedPort)
                    }
                }
            }
        }
        val bodyBytes = ByteArray(contentLength.coerceAtLeast(0))
        var readTotal = 0
        while (readTotal < bodyBytes.size) {
            val n = input.read(bodyBytes, readTotal, bodyBytes.size - readTotal)
            if (n == -1) break
            readTotal += n
        }
        val reqBody = String(bodyBytes, 0, readTotal, Charsets.UTF_8)
        if (soapAction.isNotBlank()) {
            receivedSoapActions.add(soapAction.trim('"'))
        }

        val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
        if (requestLine.startsWith("SUBSCRIBE")) {
            if (!hasCallback && simulate412OnNextGenaRenew.getAndSet(false)) {
                writer.write("HTTP/1.1 412 Precondition Failed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            } else {
                val nextSid = "uuid:sub-wls0403-${sidSequence.incrementAndGet()}"
                writer.write("HTTP/1.1 200 OK\r\nSID: $nextSid\r\nTIMEOUT: Second-300\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            }
            writer.flush()
            return
        }

        var statusCode = "200 OK"
        val respXml = when {
            requestLine.contains("GET /setup.xml") && failSetupXml404.get() -> {
                statusCode = "404 Not Found"
                "<error/>"
            }
            requestLine.contains("GET /setup.xml") || requestLine.contains("GET /Lightsetup.xml") -> """
                <?xml version="1.0"?>
                <root xmlns="urn:Belkin:device-1-0">
                  <device>
                    <deviceType>$deviceTypeUrn</deviceType>
                    <friendlyName>${friendlyName.get()}</friendlyName>
                    <manufacturer>Belkin International Inc.</manufacturer>
                    <modelName>$modelName</modelName>
                    <serialNumber>$serialNumber</serialNumber>
                    <firmwareVersion>$firmwareVersion</firmwareVersion>
                    <UDN>$udn</UDN>
                    <macAddress>$macAddress</macAddress>
                    <rtos>${if (isRtos) 1 else 0}</rtos>
                    <iot>0</iot>
                    <binaryOption>0</binaryOption>
                    <binaryState>${if (relayStateOn.get()) 1 else 0}</binaryState>
                    <brightness>${dimmerBrightness.get()}</brightness>
                  </device>
                </root>
            """.trimIndent()

            soapAction.contains("SetBinaryState") -> {
                val targetInt = Regex("<BinaryState>(\\d+)</BinaryState>")
                    .find(reqBody)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val targetBri = Regex("<brightness>(\\d+)</brightness>")
                    .find(reqBody)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (targetBri != null) {
                    dimmerBrightness.set(targetBri)
                }
                val wasOn = relayStateOn.getAndSet(targetInt == 1)
                val stateReply = if (returnErrorOnDuplicateSet.get() && wasOn == (targetInt == 1)) {
                    "Error"
                } else {
                    targetInt.toString()
                }
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:SetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
                      <BinaryState>$stateReply</BinaryState>
                      <brightness>${dimmerBrightness.get()}</brightness>
                    </u:SetBinaryStateResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("GetBinaryState") -> {
                val stateInt = if (relayStateOn.get()) 1 else 0
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
                      <BinaryState>$stateInt</BinaryState>
                      <brightness>${dimmerBrightness.get()}</brightness>
                    </u:GetBinaryStateResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("GetMetaInfo") -> """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetMetaInfoResponse xmlns:u="urn:Belkin:service:metainfo:1">
                      <MetaInfo>$macAddress|$serialNumber|$modelName|$firmwareVersion|$wifiSsid|$modelName</MetaInfo>
                    </u:GetMetaInfoResponse>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            soapAction.contains("GetSignalStrength") -> """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetSignalStrengthResponse xmlns:u="urn:Belkin:service:basicevent:1">
                      <SignalStrength>$signalStrengthPercent</SignalStrength>
                    </u:GetSignalStrengthResponse>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            soapAction.contains("ChangeFriendlyName") -> {
                val newName = Regex("<FriendlyName>([^<]+)</FriendlyName>")
                    .find(reqBody)?.groupValues?.getOrNull(1)?.trim() ?: friendlyName.get()
                friendlyName.set(newName)
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:ChangeFriendlyNameResponse xmlns:u="urn:Belkin:service:basicevent:1">
                      <PluginKey>0</PluginKey>
                    </u:ChangeFriendlyNameResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("ConfigureNightMode") -> {
                nightModeEnabled.set(reqBody.contains("<NightMode>1</NightMode>"))
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:ConfigureNightModeResponse xmlns:u="urn:Belkin:service:basicevent:1">
                      <NightMode>${if (nightModeEnabled.get()) 1 else 0}</NightMode>
                    </u:ConfigureNightModeResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("GetApList") -> {
                if (failGetApList500.get()) {
                    statusCode = "500 Internal Server Error"
                    "<s:Fault/>"
                } else {
                    """
                    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                      <s:Body>
                        <u:GetApListResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                          <ApList>Page:1/1/2$
                    HeadFamily-2.4G|6|91|WPA2PSK/AES,
                    NeighborNet|11|42|WPA2PSK/AES,
                          </ApList>
                        </u:GetApListResponse>
                      </s:Body>
                    </s:Envelope>
                    """.trimIndent()
                }
            }

            soapAction.contains("GetNetworkList") -> """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetNetworkListResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                      <NetworkList>Page:1/1/2$
                HeadFamily-2.4G|6|91|WPA2PSK/AES,
                NeighborNet|11|42|WPA2PSK/AES,
                      </NetworkList>
                    </u:GetNetworkListResponse>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()

            soapAction.contains("ConnectHomeNetwork") -> {
                connectHomeNetworkCalls.incrementAndGet()
                val pwd = Regex("<password>([^<]+)</password>")
                    .find(reqBody)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                receivedEncryptedPassword.set(pwd)
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:ConnectHomeNetworkResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                      <PairingStatus>1</PairingStatus>
                    </u:ConnectHomeNetworkResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("GetNetworkStatus") -> {
                val pollNum = networkStatusPolls.incrementAndGet()
                val statusVal = if (forceNetworkStatusZero.get()) {
                    0
                } else {
                    when (pollNum) {
                        1 -> 0
                        2 -> 3
                        else -> 1
                    }
                }
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetNetworkStatusResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                      <NetworkStatus>$statusVal</NetworkStatus>
                    </u:GetNetworkStatusResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("CloseSetup") -> {
                closeSetupCalled.set(true)
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:CloseSetupResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                      <status>success</status>
                    </u:CloseSetupResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            soapAction.contains("SetSetupDoneStatus") -> {
                setupDoneCalled.set(true)
                """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:SetSetupDoneStatusResponse xmlns:u="urn:Belkin:service:basicevent:1">
                      <Status>0</Status>
                    </u:SetSetupDoneStatusResponse>
                  </s:Body>
                </s:Envelope>
                """.trimIndent()
            }

            else -> "<root/>"
        }

        val bytes = respXml.toByteArray(Charsets.UTF_8)
        writer.write("HTTP/1.1 $statusCode\r\n")
        writer.write("Content-Type: text/xml; charset=\"utf-8\"\r\n")
        writer.write("Content-Length: ${bytes.size}\r\n")
        writer.write("Connection: close\r\n\r\n")
        writer.write(respXml)
        writer.flush()
    }

    private fun readHttpLine(input: BufferedInputStream): String? {
        val out = ByteArrayOutputStream(128)
        while (out.size() < 8192) {
            val b = input.read()
            if (b == -1) {
                return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            if (b == '\n'.code) {
                return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            out.write(b)
        }
        return out.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
    }

    fun pushGenaNotify(
        callbackPort: Int,
        deviceKey: String = serialNumber,
        binaryStatePayload: String = "1|78",
        sid: String = "uuid:sub-wls0403-102",
        extraPropertyXml: String = ""
    ): String {
        val propertysetXml = buildString {
            appendLine("<e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">")
            appendLine("  <e:property>")
            appendLine("    <BinaryState>$binaryStatePayload</BinaryState>")
            appendLine("  </e:property>")
            if (extraPropertyXml.isNotBlank()) {
                appendLine("  <e:property>")
                appendLine("    $extraPropertyXml")
                appendLine("  </e:property>")
            }
            append("</e:propertyset>")
        }
        val bodyBytes = propertysetXml.toByteArray(Charsets.UTF_8)
        Socket("127.0.0.1", callbackPort).use { sock ->
            sock.soTimeout = 1500
            val out = sock.getOutputStream()
            val headerBytes = buildString {
                append("NOTIFY /gena/$deviceKey HTTP/1.1\r\n")
                append("HOST: 127.0.0.1:$callbackPort\r\n")
                append("CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\n")
                append("NT: upnp:event\r\n")
                append("NTS: upnp:propchange\r\n")
                append("SID: $sid\r\n")
                append("CONTENT-LENGTH: ${bodyBytes.size}\r\n\r\n")
            }.toByteArray(Charsets.ISO_8859_1)
            out.write(headerBytes)
            out.write(bodyBytes)
            out.flush()
            return BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1)).readLine().orEmpty()
        }
    }

    override fun close() {
        running.set(false)
        NetworkClient.unregisterLoopbackSsdpPort(ssdpUdpPort)
        NetworkClient.unregisterLoopbackCandidatePort(port)
        runCatching { ssdpUdpSocket.close() }
        runCatching { serverSocket.close() }
        ssdpResponderThread.join(300)
        serverThread.join(500)
    }
}
