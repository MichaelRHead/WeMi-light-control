package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.WeMoDatabase
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.data.WeMoRepository
import com.example.network.WeMoNetworkClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeMoProtocolTest {

    private lateinit var database: WeMoDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeMoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify hardware SKU classification for F7C030, WLS0403, F7C059, WDS060, F7C063, and WSP080`() {
        val client = WeMoNetworkClient()

        assertTrue(
            client.classifyHardwareSku(
                modelName = "LightSwitch",
                udn = "uuid:Lightswitch-1_0-221517K01018FF"
            ).startsWith("F7C030")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "WLS0403",
                udn = "uuid:Lightswitch-3_0-221901K01022AA",
                isRtos = true
            ).startsWith("WLS0403")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "Dimmer",
                udn = "uuid:Dimmer-1_0-221733K15000BB"
            ).startsWith("F7C059")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "WDS060",
                udn = "uuid:Dimmer-2_0-222011K15000CC",
                isRtos = true
            ).startsWith("WDS060")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "Socket",
                metaSku = "WSP080",
                udn = "uuid:Socket-1_0-221844K01099DD"
            ).startsWith("WSP080")
        )
    }

    @Test
    fun `verify loopback UPnP port shift probing, BinaryState Error quirk, MetaInfo, and ChangeFriendlyName`() = runBlocking {
        val relayStateOn = AtomicBoolean(false)
        val dimmerBrightness = AtomicInteger(64)
        val friendlyNameRef = AtomicReference("Hallway 3-Way Switch")
        val returnErrorOnDuplicateSet = AtomicBoolean(true)
        val running = AtomicBoolean(true)

        val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val activePort = serverSocket.localPort

        val serverThread = thread(start = true, isDaemon = true) {
            while (running.get() && !serverSocket.isClosed) {
                try {
                    val sock = serverSocket.accept()
                    sock.use { clientSock ->
                        val reader = BufferedReader(InputStreamReader(clientSock.getInputStream(), Charsets.UTF_8))
                        val requestLine = reader.readLine() ?: return@use
                        var contentLength = 0
                        var soapAction = ""
                        while (true) {
                            val header = reader.readLine() ?: break
                            if (header.isEmpty()) break
                            if (header.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
                            } else if (header.startsWith("SOAPACTION:", ignoreCase = true)) {
                                soapAction = header.substringAfter(":").trim()
                            }
                        }
                        val bodyChars = CharArray(contentLength)
                        var readTotal = 0
                        while (readTotal < contentLength) {
                            val n = reader.read(bodyChars, readTotal, contentLength - readTotal)
                            if (n == -1) break
                            readTotal += n
                        }
                        val reqBody = String(bodyChars, 0, readTotal)

                        val respXml = when {
                            requestLine.contains("GET /setup.xml") || requestLine.contains("GET /Lightsetup.xml") -> """
                                <?xml version="1.0"?>
                                <root xmlns="urn:Belkin:device-1-0">
                                  <device>
                                    <deviceType>urn:Belkin:device:lightswitch:1</deviceType>
                                    <friendlyName>${friendlyNameRef.get()}</friendlyName>
                                    <manufacturer>Belkin International Inc.</manufacturer>
                                    <modelName>WLS0403</modelName>
                                    <serialNumber>WLS0403SN9988</serialNumber>
                                    <firmwareVersion>WeMo_WW_2.00.12000.PVT</firmwareVersion>
                                    <UDN>uuid:Lightswitch-3_0-WLS0403SN9988</UDN>
                                    <macAddress>EC1A59AA1122</macAddress>
                                    <rtos>1</rtos>
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
                                // Simulate pywemo quirk: return <BinaryState>Error</BinaryState> when already in state
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
                                      <MetaInfo>EC1A59AA1122|WLS0403SN9988|WLS0403|WeMo_WW_2.00.12000.PVT|HeadFamily-2.4G|LightSwitch</MetaInfo>
                                    </u:GetMetaInfoResponse>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()

                            soapAction.contains("ChangeFriendlyName") -> {
                                val newName = Regex("<FriendlyName>([^<]+)</FriendlyName>")
                                    .find(reqBody)?.groupValues?.getOrNull(1)?.trim() ?: friendlyNameRef.get()
                                friendlyNameRef.set(newName)
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

                            else -> "<root/>"
                        }

                        val bytes = respXml.toByteArray(Charsets.UTF_8)
                        val writer = OutputStreamWriter(clientSock.getOutputStream(), Charsets.UTF_8)
                        writer.write("HTTP/1.1 200 OK\r\n")
                        writer.write("Content-Type: text/xml; charset=\"utf-8\"\r\n")
                        writer.write("Content-Length: ${bytes.size}\r\n")
                        writer.write("Connection: close\r\n\r\n")
                        writer.write(respXml)
                        writer.flush()
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }

        try {
            val client = WeMoNetworkClient(extraCandidatePorts = listOf(activePort))

            // 1. Verify probeWemoPort finds activePort even when preferredPort is wrong (49153), and verifies serialNumber
            val probedPort = client.probeWemoPort(
                ipAddress = "127.0.0.1",
                preferredPort = 49153,
                expectedSerialOrUdn = "WLS0403SN9988"
            )
            assertEquals(activePort, probedPort)

            // Verify probeWemoPort rejects a mismatched serialNumber (DHCP swap protection)
            val mismatchedPort = client.probeWemoPort(
                ipAddress = "127.0.0.1",
                preferredPort = activePort,
                expectedSerialOrUdn = "WRONG_SERIAL_999"
            )
            assertNull(mismatchedPort)

            // 2. Verify repository in Real LAN Mode (initialDemoModeOverride = false) heals port shift and toggles power
            val repo = WeMoRepository(
                dao = database.wemoDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()
            // Clean real-LAN startup must have 0 fake demo devices
            assertEquals(0, database.wemoDao().getDeviceCount())

            val id = database.wemoDao().insertDevice(
                WeMoDeviceEntity(
                    name = "Hallway 3-Way Switch",
                    roomName = "Hallway & Landing",
                    ipAddress = "127.0.0.1",
                    port = 49153, // Stale port; real server is on activePort
                    isOn = false,
                    isReachable = true,
                    serialNumber = "WLS0403SN9988"
                )
            ).toInt()
            val savedDevice = database.wemoDao().getDeviceById(id)!!

            // Toggle ON -> should auto-heal port to activePort and turn relay ON
            val toggledOn = repo.toggleDevicePower(savedDevice)
            assertTrue(toggledOn.isReachable)
            assertTrue(toggledOn.isOn)
            assertEquals(activePort, toggledOn.port)
            assertTrue(relayStateOn.get())

            // Test <BinaryState>Error</BinaryState> when switch is already ON
            val alreadyOnResult = client.setBinaryState(toggledOn.copy(isOn = false), targetOn = true)
            assertTrue(alreadyOnResult.reachableOnLan)
            assertEquals(true, alreadyOnResult.binaryStateOn)

            // 3. Test ChangeFriendlyName hardware sync
            repo.saveDevice(
                device = toggledOn.copy(name = "Upstairs 3-Way Rocker"),
                syncFriendlyNameToHardware = true
            )
            assertEquals("Upstairs 3-Way Rocker", friendlyNameRef.get())

            // 4. Test GetMetaInfo parsing
            val meta = client.getMetaInfo("127.0.0.1", activePort)
            assertNotNull(meta)
            assertEquals("WLS0403", meta?.deviceSku)
            assertEquals("HeadFamily-2.4G", meta?.accessPointSsid)
        } finally {
            running.set(false)
            runCatching { serverSocket.close() }
            serverThread.join(500)
        }
    }

    @Test
    fun `verify Real LAN Mode reverts optimistic toggle and marks device OFFLINE when unreachable`() = runBlocking {
        // Use a local closed port so TCP connect fails with ECONNREFUSED immediately
        val tempSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val closedPort = tempSocket.localPort
        tempSocket.close()

        val client = WeMoNetworkClient()
        val repo = WeMoRepository(
            dao = database.wemoDao(),
            networkClient = client,
            initialDemoModeOverride = false
        )
        repo.ensureSeeded()

        val id = database.wemoDao().insertDevice(
            WeMoDeviceEntity(
                name = "Unplugged Mini Plug",
                roomName = "Living Room",
                ipAddress = "127.0.0.1",
                port = closedPort,
                isOn = true,
                isReachable = true,
                latencyMs = 15,
                serialNumber = "WSP080OFFLINE1"
            )
        ).toInt()
        val initial = database.wemoDao().getDeviceById(id)!!

        // Attempt to toggle OFF while device is unreachable on LAN
        val afterToggle = repo.toggleDevicePower(initial)

        // Must revert to prior isOn = true and mark OFFLINE (isReachable = false, latencyMs = 0)
        assertFalse(afterToggle.isReachable)
        assertEquals(0, afterToggle.latencyMs)
        assertTrue(afterToggle.isOn)

        val persisted = database.wemoDao().getAllDevices().first().single()
        assertFalse(persisted.isReachable)
        assertTrue(persisted.isOn)
    }
}
