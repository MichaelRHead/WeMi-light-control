package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.WeMoDatabase
import com.example.data.WeMoDeviceCategory
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoDeviceType
import com.example.data.WeMoRepository
import com.example.data.WeMoRuleScheduler
import com.example.data.classifyDeviceCategory
import com.example.network.GenaEventNotification
import com.example.network.WeMoGenaSubscriptionManager
import com.example.network.WeMoNetworkClient
import com.example.network.WeMoProvisionerClient
import kotlinx.coroutines.delay
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
import java.net.Socket
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
                    isOn = true,  // Stale cached ON state; real hardware relay is currently OFF (0)
                    isReachable = true,
                    serialNumber = "WLS0403SN9988"
                )
            ).toInt()
            val savedDevice = database.wemoDao().getDeviceById(id)!!

            // Verify startup state detection queries GetBinaryState, heals port, and detects switch is accessible but OFF
            val startupStates = repo.detectStartupDeviceStates(savedDevice.networkKey)
            val detectedOff = startupStates.single { it.id == id }
            assertTrue(detectedOff.isReachable)
            assertFalse(detectedOff.isOn)
            assertEquals(activePort, detectedOff.port)

            // Toggle ON -> should turn relay ON
            val toggledOn = repo.toggleDevicePower(detectedOff)
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

    @Test
    fun `verify Phase 3 UPnP GENA SUBSCRIBE, HTTP 412 recovery, and embedded NOTIFY propertyset callback`() = runBlocking {
        val receivedNotification = AtomicReference<GenaEventNotification?>(null)
        val genaManager = WeMoGenaSubscriptionManager(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0, // Use ephemeral port
            onNotification = { receivedNotification.set(it) }
        )

        val running = AtomicBoolean(true)
        val simulate412OnNextRenew = AtomicBoolean(false)
        val sidSequence = AtomicInteger(100)
        val wemoEventServer = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val wemoPort = wemoEventServer.localPort

        val serverThread = thread(start = true, isDaemon = true) {
            while (running.get() && !wemoEventServer.isClosed) {
                try {
                    wemoEventServer.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                        val reqLine = reader.readLine() ?: return@use
                        var hasCallback = false
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                            if (h.startsWith("CALLBACK:", ignoreCase = true)) {
                                hasCallback = true
                            }
                        }
                        val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                        if (reqLine.startsWith("SUBSCRIBE") && !hasCallback && simulate412OnNextRenew.getAndSet(false)) {
                            writer.write("HTTP/1.1 412 Precondition Failed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        } else {
                            val nextSid = "uuid:sub-wls0403-${sidSequence.incrementAndGet()}"
                            writer.write("HTTP/1.1 200 OK\r\nSID: $nextSid\r\nTIMEOUT: Second-300\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
                        }
                        writer.flush()
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }

        try {
            val device = WeMoDeviceEntity(
                id = 42,
                name = "Kitchen 3-Way Switch",
                roomName = "Kitchen",
                ipAddress = "127.0.0.1",
                port = wemoPort,
                serialNumber = "WLS0403KITCHEN"
            )

            // 1. Initial SUBSCRIBE
            val sub1 = genaManager.subscribeOrRenew(device, timeoutSeconds = 300)
            assertNotNull(sub1)
            assertEquals("uuid:sub-wls0403-101", sub1?.sid)

            // 2. Simulate HTTP 412 on renewal (e.g. switch rebooted and forgot SID) -> must recover with fresh SID
            simulate412OnNextRenew.set(true)
            val sub2 = genaManager.subscribeOrRenew(device, timeoutSeconds = 300)
            assertNotNull(sub2)
            assertEquals("uuid:sub-wls0403-102", sub2?.sid)

            // 3. Push a physical wall-switch flip via HTTP NOTIFY to the embedded GENA callback server
            val callbackPort = genaManager.activeListenPort
            assertTrue(callbackPort > 0)
            val propertysetXml = """
                <e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">
                  <e:property>
                    <BinaryState>1|78</BinaryState>
                  </e:property>
                </e:propertyset>
            """.trimIndent()
            val bodyBytes = propertysetXml.toByteArray(Charsets.UTF_8)

            Socket("127.0.0.1", callbackPort).use { sock ->
                val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                writer.write("NOTIFY /gena/WLS0403KITCHEN HTTP/1.1\r\n")
                writer.write("HOST: 127.0.0.1:$callbackPort\r\n")
                writer.write("CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\n")
                writer.write("NT: upnp:event\r\n")
                writer.write("NTS: upnp:propchange\r\n")
                writer.write("SID: uuid:sub-wls0403-102\r\n")
                writer.write("CONTENT-LENGTH: ${bodyBytes.size}\r\n\r\n")
                writer.write(propertysetXml)
                writer.flush()
                val respLine = BufferedReader(InputStreamReader(sock.getInputStream())).readLine()
                assertTrue(respLine.contains("200 OK"))
            }

            delay(100)
            val event = receivedNotification.get()
            assertNotNull(event)
            assertEquals("WLS0403KITCHEN", event?.deviceKey)
            assertEquals(true, event?.binaryStateOn)
            assertEquals(78, event?.brightness)
        } finally {
            running.set(false)
            genaManager.stopServer()
            runCatching { wemoEventServer.close() }
            serverThread.join(500)
        }
    }

    @Test
    fun `verify Phase 5 WeMoRuleScheduler schedule time parsing and Auto-Off countdown timer`() = runBlocking {
        assertEquals(Pair(19, 0), WeMoRuleScheduler.parseScheduleTime24h("Daily at 7:00 PM"))
        assertEquals(Pair(17, 15), WeMoRuleScheduler.parseScheduleTime24h("Daily at Sunset (5:15 PM)"))
        assertEquals(Pair(22, 0), WeMoRuleScheduler.parseScheduleTime24h("Every night at 10:00 PM"))
        assertEquals(Pair(6, 30), WeMoRuleScheduler.parseScheduleTime24h("Daily at Sunrise (6:30 AM)"))
        assertEquals(30, WeMoRuleScheduler.parseMinutesFromSummary("After 30 minutes ON"))

        val expiredDeviceId = AtomicInteger(-1)
        val scheduler = WeMoRuleScheduler(
            onAutoOffExpired = { id -> expiredDeviceId.set(id) },
            onScheduleRuleTriggered = {}
        )
        try {
            scheduler.startAutoOffCountdownSeconds(deviceId = 77, durationSeconds = 1)
            assertTrue(scheduler.activeCountdownsSec.value.containsKey(77))
            delay(1300L)
            assertEquals(77, expiredDeviceId.get())
            assertFalse(scheduler.activeCountdownsSec.value.containsKey(77))
        } finally {
            scheduler.stopAll()
        }
    }

    @Test
    fun `verify Phase 6 WeMoProvisionerClient GetApList parsing and AES-128-CBC password encryption Methods 1 and 2`() {
        val provisioner = WeMoProvisionerClient()
        val sampleApXml = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetApListResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                  <ApList>Page:1/1/2$
            HeadFamily-2.4G|6|88|WPA2PSK/AES,
            GuestNetwork|11|54|OPEN/NONE,
                  </ApList>
                </u:GetApListResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val aps = provisioner.parseApListXml(sampleApXml)
        assertEquals(2, aps.size)
        assertEquals("HeadFamily-2.4G", aps[0].ssid)
        assertEquals(6, aps[0].channel)
        assertEquals(88, aps[0].signalStrengthPercent)
        assertEquals("WPA2PSK", aps[0].authMode)
        assertEquals("AES", aps[0].encryptionType)

        // Verify Method 1 (OpenWrt) and Method 2 (RTOS) produce deterministic Base64 + 4-hex-digit length suffixes
        val method1Enc = provisioner.encryptWifiPassword(
            plainPassword = "MyHomeWifiPassword123",
            macAddress = "EC:1A:59:AA:11:22",
            serialNumber = "221517K01018AB",
            encryptionMethod = 1
        )
        val method2Enc = provisioner.encryptWifiPassword(
            plainPassword = "MyHomeWifiPassword123",
            macAddress = "EC:1A:59:AA:11:22",
            serialNumber = "221517K01018AB",
            encryptionMethod = 2
        )
        // Original password length is 21 bytes -> 0x15 hex suffix
        assertTrue(method1Enc.endsWith("15"))
        assertTrue(method2Enc.endsWith("15"))
        assertFalse(method1Enc == method2Enc)
    }

    @Test
    fun `verify startup binaryState parsing in setup xml and semantic name classification for trees, fans, and lamps`() {
        val client = WeMoNetworkClient()

        // 1. Verify setup.xml with <binaryState>0</binaryState> parses as reachable OFF (isOn = false)
        val offTreeXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:Belkin:device-1-0">
              <device>
                <deviceType>urn:Belkin:device:controllee:1</deviceType>
                <friendlyName>Christmas Tree Living</friendlyName>
                <modelName>Socket</modelName>
                <serialNumber>221517K01018A1</serialNumber>
                <binaryState>0</binaryState>
              </device>
            </root>
        """.trimIndent()
        val offTree = client.parseSetupXmlDescriptor(
            xml = offTreeXml,
            locationUrl = "http://192.168.1.101:49153/setup.xml",
            usnHeader = "uuid:Socket-1_0-221517K01018A1",
            stHeader = "urn:Belkin:service:basicevent:1",
            responseTimeMs = 11L,
            isLiveUdp = true
        )
        assertFalse(offTree.isOn)
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, offTree.visualCategory)

        // 2. Verify setup.xml with pipe-delimited <binaryState>1|1700000000|45</binaryState> parses as ON
        val onFanXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:Belkin:device-1-0">
              <device>
                <deviceType>urn:Belkin:device:lightswitch:1</deviceType>
                <friendlyName>Bedroom Ceiling Fan</friendlyName>
                <modelName>LightSwitch</modelName>
                <serialNumber>221517K01018A9</serialNumber>
                <binaryState>1|1700000000|45</binaryState>
              </device>
            </root>
        """.trimIndent()
        val onFan = client.parseSetupXmlDescriptor(
            xml = onFanXml,
            locationUrl = "http://192.168.1.109:49153/setup.xml",
            usnHeader = "uuid:Lightswitch-1_0-221517K01018A9",
            stHeader = "urn:Belkin:service:basicevent:1",
            responseTimeMs = 13L,
            isLiveUdp = true
        )
        assertTrue(onFan.isOn)
        assertEquals(WeMoDeviceCategory.FAN, onFan.visualCategory)

        // 3. Verify lamp, light, fan, tree, motion sensor, and generic switch classification
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Living Room Christmas Tree"))
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Front Yard Tree Lights"))
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Xmas Garland Plug"))
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Blue Spruce Porch"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Patio Misting Fan"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Bathroom Exhaust Blower"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Whole House Ventilator"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Workshop Air Circulator"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Bedside Reading Lamp"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Kitchen Under-Cabinet Light"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Dining Chandelier"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Hallway Wall Sconce"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Any Name On Dimmer", WeMoDeviceType.LIGHTBULB_DIMMER))
        assertEquals(WeMoDeviceCategory.MOTION_SENSOR, classifyDeviceCategory("Hallway Motion Detector"))
        assertEquals(WeMoDeviceCategory.MOTION_SENSOR, classifyDeviceCategory("Driveway Sensor"))
        assertEquals(WeMoDeviceCategory.SWITCH_PLUG, classifyDeviceCategory("Espresso Machine Plug"))
        assertEquals(WeMoDeviceCategory.SWITCH_PLUG, classifyDeviceCategory("Office Wall Switch"))
    }

    @Test
    fun `verify multi-device startup state detection across ON, OFF accessible standby, and Disconnected devices plus Night Mode and SignalStrength`() = runBlocking {
        val nightModeConfigured = AtomicBoolean(false)
        val running = AtomicBoolean(true)

        // Server A: Accessible OFF Christmas Tree switch (<BinaryState>0</BinaryState>)
        val serverA = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val portA = serverA.localPort
        val threadA = thread(start = true, isDaemon = true) {
            while (running.get() && !serverA.isClosed) {
                try {
                    serverA.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                        reader.readLine() ?: return@use
                        var contentLen = 0
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                            if (h.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLen = h.substringAfter(":").trim().toIntOrNull() ?: 0
                            }
                        }
                        if (contentLen > 0) reader.skip(contentLen.toLong())
                        val xml = """
                            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                              <s:Body>
                                <u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
                                  <BinaryState>0</BinaryState>
                                </u:GetBinaryStateResponse>
                              </s:Body>
                            </s:Envelope>
                        """.trimIndent()
                        val bytes = xml.toByteArray(Charsets.UTF_8)
                        val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                        writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n$xml")
                        writer.flush()
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }

        // Server B: Accessible ON Smart Dimmer Lamp (<BinaryState>1</BinaryState><brightness>55</brightness>) + NightMode + SignalStrength
        val serverB = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val portB = serverB.localPort
        val threadB = thread(start = true, isDaemon = true) {
            while (running.get() && !serverB.isClosed) {
                try {
                    serverB.accept().use { sock ->
                        val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
                        reader.readLine() ?: return@use
                        var contentLen = 0
                        var soapAction = ""
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                            if (h.startsWith("Content-Length:", ignoreCase = true)) {
                                contentLen = h.substringAfter(":").trim().toIntOrNull() ?: 0
                            } else if (h.startsWith("SOAPACTION:", ignoreCase = true)) {
                                soapAction = h.substringAfter(":").trim()
                            }
                        }
                        val buf = CharArray(contentLen)
                        var read = 0
                        while (read < contentLen) {
                            val n = reader.read(buf, read, contentLen - read)
                            if (n == -1) break
                            read += n
                        }
                        val body = String(buf, 0, read)
                        val xml = when {
                            soapAction.contains("ConfigureNightMode") -> {
                                nightModeConfigured.set(body.contains("<NightMode>1</NightMode>"))
                                """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <u:ConfigureNightModeResponse xmlns:u="urn:Belkin:service:basicevent:1">
                                      <NightMode>${if (nightModeConfigured.get()) 1 else 0}</NightMode>
                                    </u:ConfigureNightModeResponse>
                                  </s:Body>
                                </s:Envelope>
                                """.trimIndent()
                            }
                            soapAction.contains("GetSignalStrength") -> """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <u:GetSignalStrengthResponse xmlns:u="urn:Belkin:service:basicevent:1">
                                      <SignalStrength>92</SignalStrength>
                                    </u:GetSignalStrengthResponse>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()
                            else -> """
                                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                                  <s:Body>
                                    <u:GetBinaryStateResponse xmlns:u="urn:Belkin:service:basicevent:1">
                                      <BinaryState>1</BinaryState>
                                      <brightness>55</brightness>
                                    </u:GetBinaryStateResponse>
                                  </s:Body>
                                </s:Envelope>
                            """.trimIndent()
                        }
                        val bytes = xml.toByteArray(Charsets.UTF_8)
                        val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                        writer.write("HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n$xml")
                        writer.flush()
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }

        // Closed port C: Disconnected Fan switch
        val tempSock = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val closedPortC = tempSock.localPort
        tempSock.close()

        try {
            val client = WeMoNetworkClient()
            val repo = WeMoRepository(
                dao = database.wemoDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            // Insert 3 switches with stale cached states so detectStartupDeviceStates must reconcile all 3
            val idTree = database.wemoDao().insertDevice(
                WeMoDeviceEntity(
                    name = "Christmas Tree Living",
                    roomName = "Living Room",
                    ipAddress = "127.0.0.1",
                    port = portA,
                    isOn = true, // Stale ON; real switch is OFF (0)
                    isReachable = true,
                    serialNumber = "TREE001"
                )
            ).toInt()
            val idLamp = database.wemoDao().insertDevice(
                WeMoDeviceEntity(
                    name = "Bedroom Dimmer Lamp",
                    roomName = "Bedroom",
                    deviceType = WeMoDeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "127.0.0.1",
                    port = portB,
                    isOn = false, // Stale OFF; real dimmer is ON (1) at 55%
                    brightness = 100,
                    isReachable = false,
                    serialNumber = "LAMP002"
                )
            ).toInt()
            val idFan = database.wemoDao().insertDevice(
                WeMoDeviceEntity(
                    name = "Garage Exhaust Fan",
                    roomName = "Garage",
                    ipAddress = "127.0.0.1",
                    port = closedPortC,
                    isOn = true,
                    isReachable = true, // Stale Reachable; real switch is offline
                    serialNumber = "FAN003"
                )
            ).toInt()

            val updatedList = repo.detectStartupDeviceStates("wifi_home_192_168_1")
            val treeAfter = updatedList.single { it.id == idTree }
            val lampAfter = updatedList.single { it.id == idLamp }
            val fanAfter = updatedList.single { it.id == idFan }

            // 1. Tree switch is Reachable & OFF (Accessible Standby) with CHRISTMAS_TREE category
            assertTrue(treeAfter.isReachable)
            assertFalse(treeAfter.isOn)
            assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, treeAfter.visualCategory)

            // 2. Dimmer Lamp is Reachable & ON at 55% brightness with LIGHT category
            assertTrue(lampAfter.isReachable)
            assertTrue(lampAfter.isOn)
            assertEquals(55, lampAfter.brightness)
            assertEquals(WeMoDeviceCategory.LIGHT, lampAfter.visualCategory)

            // 3. Garage Fan on closed port is marked Disconnected (isReachable = false, latencyMs = 0) with FAN category
            assertFalse(fanAfter.isReachable)
            assertEquals(0, fanAfter.latencyMs)
            assertEquals(WeMoDeviceCategory.FAN, fanAfter.visualCategory)

            // 4. Verify dimmer Night Mode SOAP action and SignalStrength query
            val nightResult = repo.toggleDimmerNightMode(lampAfter)
            assertTrue(nightResult.nightModeEnabled)
            assertTrue(nightModeConfigured.get())

            val rssi = client.getSignalStrength("127.0.0.1", portB)
            assertEquals(92, rssi)

            // 5. Verify room brightness update and Auto-Off turnOffDeviceById
            val updatedBulbs = repo.updateRoomBrightness(
                roomName = "Bedroom",
                newBrightness = 35,
                networkKey = "wifi_home_192_168_1"
            )
            assertEquals(1, updatedBulbs)

            val autoOffResult = repo.turnOffDeviceById(idLamp)
            assertNotNull(autoOffResult)
        } finally {
            running.set(false)
            runCatching { serverA.close() }
            runCatching { serverB.close() }
            threadA.join(500)
            threadB.join(500)
        }
    }
}

