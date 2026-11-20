package org.suppressingfire.wemi

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
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
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.LanHttpClient
import org.suppressingfire.wemi.network.NetworkClient

/**
 * Tier 1 HTTP/1.1 Wire Framing (`LanHttpClient`) & UPnP SOAP 9-Port Probing (`NetworkClient`) Tests.
 *
 * Repository-level Room persistence, concurrency, offline rollback, and startup sanitization
 * tests live in [RepositorySelfHealingTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SoapAndSelfHealingTest {

    private lateinit var database: SwitchDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SwitchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify LanHttpClient frames Content-Length, chunked transfer-encoding, and missing Content-Length with delayed FIN`() {
        val client = LanHttpClient(
            connectTimeoutMs = 400,
            readTimeoutMs = 80
        )

        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val reqIndex = AtomicInteger(0)
            val acceptLoop = Thread {
                repeat(3) {
                    val sock = runCatching { server.accept() }.getOrNull() ?: return@repeat
                    val idx = reqIndex.incrementAndGet()
                    Thread {
                        sock.use { s ->
                            val reader = s.getInputStream()
                            val buf = ByteArray(1024)
                            runCatching { reader.read(buf) }
                            when (idx) {
                                1 -> {
                                    val body = "<root><binaryState>1</binaryState></root>"
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\nContent-Length: ${body.length}\r\n\r\n$body"
                                    s.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                                    s.getOutputStream().flush()
                                    Thread.sleep(250)
                                }
                                2 -> {
                                    val chunk1 = "<BinaryState>"
                                    val chunk2 = "1|85</BinaryState>"
                                    val resp = buildString {
                                        append("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n")
                                        append("${chunk1.length.toString(16)};ext=1\r\n$chunk1\r\n")
                                        append("${chunk2.length.toString(16)}\r\n$chunk2\r\n")
                                        append("0\r\n\r\n")
                                    }
                                    s.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                                    s.getOutputStream().flush()
                                }
                                3 -> {
                                    val body = "<device><friendlyName>Porch Switch</friendlyName></device>"
                                    val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\n\r\n$body"
                                    s.getOutputStream().write(resp.toByteArray(Charsets.UTF_8))
                                    s.getOutputStream().flush()
                                    Thread.sleep(250)
                                }
                            }
                        }
                    }.apply {
                        isDaemon = true
                        start()
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }

            val r1 = client.get("http://127.0.0.1:$port/setup.xml")
            assertTrue(r1.isSuccessful)
            assertEquals("<root><binaryState>1</binaryState></root>", r1.body)

            val r2 = client.postXml(
                url = "http://127.0.0.1:$port/upnp/control/basicevent1",
                soapBody = "<u:GetBinaryState/>",
                soapAction = "\"urn:Belkin:service:basicevent:1#GetBinaryState\""
            )
            assertTrue(r2.isSuccessful)
            assertEquals("<BinaryState>1|85</BinaryState>", r2.body)

            val r3 = client.get("http://127.0.0.1:$port/Lightsetup.xml")
            assertTrue(r3.isSuccessful)
            assertEquals("<device><friendlyName>Porch Switch</friendlyName></device>", r3.body)

            acceptLoop.join(1000)
        }
    }

    @Test
    fun `verify loopback UPnP port shift probing, BinaryState Error quirk, MetaInfo, and ChangeFriendlyName`() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Hallway 3-Way Switch",
            modelName = "WLS0403",
            serialNumber = "WLS0403SN9988",
            udn = "uuid:Lightswitch-3_0-WLS0403SN9988",
            macAddress = "EC1A59AA1122",
            initialRelayOn = false,
            initialBrightness = 64
        ).use { server ->
            server.returnErrorOnDuplicateSet.set(true)
            val activePort = server.port
            val client = NetworkClient(extraCandidatePorts = listOf(activePort))

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
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()
            assertEquals(0, database.switchDao().getDeviceCount())

            val id = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Hallway 3-Way Switch",
                    roomName = "Hallway & Landing",
                    ipAddress = "127.0.0.1",
                    port = 49153, // Stale port; real server is on activePort
                    isOn = true,  // Stale cached ON state; real hardware relay is currently OFF (0)
                    isReachable = true,
                    serialNumber = "WLS0403SN9988"
                )
            ).toInt()
            val savedDevice = database.switchDao().getDeviceById(id)!!

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
            assertTrue(server.relayStateOn.get())

            // Test <BinaryState>Error</BinaryState> when switch is already ON
            val alreadyOnResult = client.setBinaryState(toggledOn.copy(isOn = false), targetOn = true)
            assertTrue(alreadyOnResult.reachableOnLan)
            assertEquals(true, alreadyOnResult.binaryStateOn)

            // 3. Test ChangeFriendlyName hardware sync (including XML entity characters)
            repo.saveDevice(
                device = toggledOn.copy(name = "Upstairs 3-Way Rocker"),
                syncFriendlyNameToHardware = true
            )
            assertEquals("Upstairs 3-Way Rocker", server.friendlyName.get())

            // 4. Test GetMetaInfo parsing
            val meta = client.getMetaInfo("127.0.0.1", activePort)
            assertNotNull(meta)
            assertEquals("WLS0403", meta?.deviceSku)
            assertEquals("HeadFamily-2.4G", meta?.accessPointSsid)
        }
    }

    @Test
    fun `verify NetworkMonitor 10_22_22_x Setup AP snapshot classification and loopback-safe createWifiBoundSocketFactory`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val monitor = org.suppressingfire.wemi.network.NetworkMonitor(
            context = context,
            fallbackIpResolver = { "10.22.22.142" },
            interfaceIpResolver = { "10.22.22.142" to "wlan0" }
        )
        try {
            val snap = monitor.refreshSnapshot()
            assertEquals("10.22.22.142", snap.localIpv4)
            assertEquals("10.22.22", snap.subnetPrefix)
            assertEquals("10.22.22.1", snap.gatewayIp)
            assertTrue(snap.isWemoSetupApSubnet)

            ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { loopbackServer ->
                val factory = org.suppressingfire.wemi.network.NetworkMonitor.createWifiBoundSocketFactory(context)
                factory.createSocket("127.0.0.1", loopbackServer.localPort).use { clientSock ->
                    assertTrue(clientSock.isConnected)
                }
            }
        } finally {
            monitor.stopMonitoring()
        }
    }

    @Test
    fun `verify Stage B known-device unicast discovery isolates placeholder serials and skips 10_22_22 subnet sweep`() = runBlocking {
        val serverA = FakeUpnpServer(
            initialFriendlyName = "Porch Light",
            serialNumber = org.suppressingfire.wemi.network.LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
            udn = "",
            enableSsdpResponder = false
        )
        val serverB = FakeUpnpServer(
            initialFriendlyName = "Garage Light",
            serialNumber = org.suppressingfire.wemi.network.LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
            udn = "",
            enableSsdpResponder = false
        )
        try {
            val client = NetworkClient(extraCandidatePorts = listOf(serverA.port, serverB.port))
            val knownA = DeviceEntity(
                name = "Porch Light",
                ipAddress = "127.0.0.1",
                port = serverA.port,
                serialNumber = org.suppressingfire.wemi.network.LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL
            )
            val knownB = DeviceEntity(
                name = "Garage Light",
                ipAddress = "127.0.0.1",
                port = serverB.port,
                serialNumber = org.suppressingfire.wemi.network.LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL
            )
            val report = client.performSsdpDiscoveryScan(
                knownLocalDevices = listOf(knownA, knownB),
                includeDemoFallbacks = false
            )
            assertEquals(2, report.discoveredDevices.size)
            assertTrue(report.discoveredDevices.any { it.port == serverA.port && it.roomName == "Outdoor" })
            assertTrue(report.discoveredDevices.any { it.port == serverB.port && it.roomName == "Garage" })

            assertTrue(client.scanSubnetUnicast("10.22.22").isEmpty())
        } finally {
            serverA.close()
            serverB.close()
        }
    }

    @Test
    fun `verify manual-IP saveDevice deduplicates existing hardware serialNumber and preserves hardware friendlyName on blank add`() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Kitchen Island Dimmer",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060DEDUP88",
            udn = "uuid:Dimmer-1_0-WDS060DEDUP88",
            wifiSsid = "WeMo.Light.881",
            initialRelayOn = true,
            initialBrightness = 64
        ).use { server ->
            val client = NetworkClient(extraCandidatePorts = listOf(server.port))
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            try {
                val homeKey = repo.ensureSeeded()

                // 1. Pre-seed an offline switch with custom room 'Chef Pantry' at stale port 49159
                val existingId = database.switchDao().insertDevice(
                    DeviceEntity(
                        name = "Kitchen Island Dimmer",
                        roomName = "Chef Pantry",
                        networkKey = homeKey,
                        wifiSsid = "LAN 192.168.1.0/24",
                        deviceType = org.suppressingfire.wemi.data.DeviceType.LIGHTBULB_DIMMER.name,
                        ipAddress = "127.0.0.1",
                        port = 49159,
                        isReachable = false,
                        serialNumber = "WDS060DEDUP88",
                        autoOffMinutes = 15
                    )
                ).toInt()

                // 2. User re-adds by manual IP (id = 0, blank name) -> must heal existing row in-place without duplicating
                val saved = repo.saveDevice(
                    device = DeviceEntity(
                        id = 0,
                        name = "",
                        roomName = "Living Room",
                        networkKey = homeKey,
                        wifiSsid = "LAN 192.168.1.0/24",
                        ipAddress = "127.0.0.1",
                        port = 49153,
                        serialNumber = org.suppressingfire.wemi.network.LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL
                    ),
                    syncFriendlyNameToHardware = true
                )

                assertEquals(1, database.switchDao().getDeviceCount())
                assertEquals(existingId, saved.id)
                assertEquals(server.port, saved.port)
                assertEquals("Chef Pantry", saved.roomName)
                assertEquals("LAN 192.168.1.0/24", saved.wifiSsid)
                assertEquals(64, saved.brightness)
                assertEquals(15, saved.autoOffMinutes)
                assertEquals("Kitchen Island Dimmer", server.friendlyName.get())
                assertFalse(server.receivedSoapActions.any { it.contains("ChangeFriendlyName") })
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun `verify executeSsdpScan preserves concurrent DB edits inside mutex and adoptDiscoveredDevice assigns monotonic sortOrder after deletion`() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Kitchen Island Switch",
            serialNumber = "221803K1300991",
            udn = "uuid:Lightswitch-2_0-221803K1300991",
            wifiSsid = "WeMo.Switch.991",
            initialRelayOn = false,
            enableSsdpResponder = true,
            registerAsCandidatePort = true
        ).use { server ->
            val client = NetworkClient(extraCandidatePorts = listOf(server.port))
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            try {
                // 1. Seed 3 switches (sortOrder 0, 1, 2), then delete the first one (leaving sortOrder = [1, 2], size = 2)
                val firstId = database.switchDao().insertDevice(
                    DeviceEntity(name = "Entry Lamp", roomName = "Entryway", ipAddress = "127.0.0.1", port = 49901, sortOrder = 0)
                ).toInt()
                val targetId = database.switchDao().insertDevice(
                    DeviceEntity(
                        name = "Custom Kitchen Island",
                        roomName = "Kitchen",
                        ipAddress = "127.0.0.1",
                        port = server.port,
                        serialNumber = "221803K1300991",
                        wifiSsid = "HeadFamily-2.4G",
                        autoOffMinutes = 15,
                        sortOrder = 1
                    )
                ).toInt()
                database.switchDao().insertDevice(
                    DeviceEntity(name = "Patio String", roomName = "Outdoor", ipAddress = "127.0.0.1", port = 49902, sortOrder = 2)
                )
                repo.deleteDevice(firstId)

                // 2. Toggle power (recording lastSetStateTimestampMs) and mutate autoOffMinutes/sortOrder concurrently before SSDP scan
                server.relayStateOn.set(true)
                val toggled = repo.setDevicePowerExplicit(database.switchDao().getDeviceById(targetId)!!, true)
                assertTrue(toggled.isOn)
                // Simulate hardware momentarily reporting stale OFF during 1500ms echo window while user also updated autoOffMinutes
                server.relayStateOn.set(false)
                database.switchDao().updateDevice(toggled.copy(autoOffMinutes = 30, sortOrder = 5))

                repo.executeSsdpScan(autoAdoptNew = false)
                val afterScan = database.switchDao().getDeviceById(targetId)!!
                assertEquals("Custom Kitchen Island", afterScan.name)
                assertEquals(30, afterScan.autoOffMinutes)
                assertEquals(5, afterScan.sortOrder)
                assertTrue("1500ms echo guard must preserve recent user ON state", afterScan.isOn)
                assertEquals("Soft-AP SSID from GetMetaInfo must never overwrite router SSID", "HeadFamily-2.4G", afterScan.wifiSsid)

                // 3. Adopt a brand-new discovered switch (with placeholder serial) -> sortOrder must be max(2, 5) + 1 = 6, and serial sanitized to ""
                val newlyAdopted = repo.adoptDiscoveredDevice(
                    org.suppressingfire.wemi.data.SsdpDiscoveredDevice(
                        usn = "uuid:Socket-1_0-NEWMINI999",
                        searchTarget = "urn:Belkin:service:basicevent:1",
                        locationUrl = "http://127.0.0.1:49903/setup.xml",
                        ipAddress = "127.0.0.1",
                        port = 49903,
                        name = "Office Desk Fan",
                        roomName = "Office",
                        deviceType = org.suppressingfire.wemi.data.DeviceType.SWITCH_PLUG,
                        modelName = "Socket",
                        isOn = false,
                        brightness = 100,
                        responseTimeMs = 8,
                        serialNumber = org.suppressingfire.wemi.network.LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
                        firmwareVersion = "WeMo_WW_2.00",
                        hardwareSku = "WSP080",
                        macAddress = "EC1A59000999",
                        wifiSsid = "WeMo.Mini.999"
                    )
                )
                assertEquals(6, newlyAdopted.sortOrder)
                assertEquals("", newlyAdopted.serialNumber)
                org.junit.Assert.assertNotEquals("WeMo.Mini.999", newlyAdopted.wifiSsid)
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun `verify executeWithPortHealing refuses DHCP-swapped foreign switch port and reconnectAndRediscoverHome enriches blank serial and toggles Unassigned room`() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Espresso Machine",
            deviceTypeUrn = "urn:Belkin:device:controllee:1",
            modelName = "WSP080",
            serialNumber = "WSP080NEIGHBOR55",
            udn = "uuid:Socket-1_0-WSP080NEIGHBOR55",
            initialRelayOn = false,
            enableSsdpResponder = true
        ).use { foreignServer ->
            val client = NetworkClient(extraCandidatePorts = listOf(foreignServer.port))
            val repo = SwitchRepository(dao = database.switchDao(), networkClient = client, initialDemoModeOverride = false)
            try {
                val homeKey = repo.ensureSeeded()
                val wrongSerialSwitch = DeviceEntity(
                    id = 1, name = "Bedroom Lamp", roomName = "Bedroom", networkKey = homeKey,
                    ipAddress = "127.0.0.1", port = 49153, serialNumber = "OTHER_SWITCH_SERIAL_01"
                )
                val blockedSet = client.setBinaryState(wrongSerialSwitch, targetOn = true)
                assertFalse("executeWithPortHealing must refuse foreign switch on DHCP IP swap", blockedSet.reachableOnLan)
                assertFalse("Foreign switch relay must remain untouched", foreignServer.relayStateOn.get())

                val manualId = database.switchDao().insertDevice(
                    DeviceEntity(
                        name = "", roomName = "", networkKey = homeKey,
                        ipAddress = "127.0.0.1", port = foreignServer.port, isReachable = false, serialNumber = ""
                    )
                ).toInt()
                repo.reconnectAndRediscoverHome(homeKey)
                val enriched = database.switchDao().getDeviceById(manualId)!!
                assertEquals("WSP080NEIGHBOR55", enriched.serialNumber)
                assertEquals("uuid:Socket-1_0-WSP080NEIGHBOR55", enriched.udn)
                assertEquals("Unassigned", enriched.roomName)

                database.switchDao().updateDevice(enriched.copy(roomName = ""))
                assertEquals(1, repo.toggleRoomPower("Unassigned", targetOn = true, networkKey = homeKey))
                assertTrue(foreignServer.relayStateOn.get())
            } finally {
                repo.close()
            }
        }
    }
}
