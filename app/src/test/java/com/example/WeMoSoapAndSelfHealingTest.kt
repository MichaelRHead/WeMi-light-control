package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.WeMoDatabase
import com.example.data.WeMoDeviceCategory
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
import java.net.InetAddress
import java.net.ServerSocket

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeMoSoapAndSelfHealingTest {

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
    fun `verify loopback UPnP port shift probing, BinaryState Error quirk, MetaInfo, and ChangeFriendlyName`() = runBlocking {
        FakeWeMoUpnpServer(
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
            assertTrue(server.relayStateOn.get())

            // Test <BinaryState>Error</BinaryState> when switch is already ON
            val alreadyOnResult = client.setBinaryState(toggledOn.copy(isOn = false), targetOn = true)
            assertTrue(alreadyOnResult.reachableOnLan)
            assertEquals(true, alreadyOnResult.binaryStateOn)

            // 3. Test ChangeFriendlyName hardware sync
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
    fun `verify Real LAN Mode reverts optimistic toggle and marks device OFFLINE when unreachable`() = runBlocking {
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
    fun `verify manual saveDevice probes fresh switch IP for real port, serialNumber, hardware SKU, and initial binaryState`() = runBlocking {
        FakeWeMoUpnpServer(
            initialFriendlyName = "Attic Exhaust Fan",
            deviceTypeUrn = "urn:Belkin:device:controllee:1",
            modelName = "WSP080",
            serialNumber = "WSP080MANUAL77",
            firmwareVersion = "WeMo_WW_2.00.11888.PVT",
            udn = "uuid:Socket-1_0-WSP080MANUAL77",
            macAddress = "EC1A59CC5566",
            signalStrengthPercent = 89,
            initialRelayOn = false
        ).use { server ->
            val realPort = server.port
            val client = WeMoNetworkClient(extraCandidatePorts = listOf(realPort))
            val repo = WeMoRepository(
                dao = database.wemoDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            // Save a manual device by IP ("127.0.0.1") with wrong default port 49153 and placeholder serial "221517K01018NEW"
            repo.saveDevice(
                device = WeMoDeviceEntity(
                    id = 0,
                    name = "",
                    roomName = "Living Room",
                    ipAddress = "127.0.0.1",
                    port = 49153,
                    serialNumber = "221517K01018NEW"
                ),
                syncFriendlyNameToHardware = false
            )

            val saved = database.wemoDao().getAllDevices().first().single()
            assertEquals("Attic Exhaust Fan", saved.name)
            assertEquals(realPort, saved.port)
            assertEquals("WSP080MANUAL77", saved.serialNumber)
            assertEquals("EC1A59CC5566", saved.macAddress)
            assertTrue(saved.hardwareModel.startsWith("WSP080"))
            assertTrue(saved.isReachable)
            assertFalse(saved.isOn) // Probed real OFF state from GetBinaryState
            assertEquals(89, saved.signalStrengthPercent)
            assertEquals(WeMoDeviceCategory.FAN, saved.visualCategory)
        }
    }

    @Test
    fun `verify multi-device startup state detection across ON, OFF accessible standby, and Disconnected devices plus Night Mode and SignalStrength`() = runBlocking {
        // Server A: Accessible OFF Christmas Tree switch (<BinaryState>0</BinaryState>)
        val serverA = FakeWeMoUpnpServer(
            initialFriendlyName = "Christmas Tree Living",
            serialNumber = "TREE001",
            initialRelayOn = false
        )
        // Server B: Accessible ON Smart Dimmer Lamp (<BinaryState>1</BinaryState><brightness>55</brightness>) + NightMode + SignalStrength
        val serverB = FakeWeMoUpnpServer(
            initialFriendlyName = "Bedroom Dimmer Lamp",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "LAMP002",
            signalStrengthPercent = 92,
            initialRelayOn = true,
            initialBrightness = 55
        )
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
                    port = serverA.port,
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
                    port = serverB.port,
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
            assertTrue(serverB.nightModeEnabled.get())

            val rssi = client.getSignalStrength("127.0.0.1", serverB.port)
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

            // 6. Verify resetLanHomesSwitchesAndRooms clears all discovered switches/rooms and re-seeds clean 'My Home'
            repo.resetLanHomesSwitchesAndRooms()
            assertEquals(0, database.wemoDao().getDeviceCount())
            val resetHomes = database.wemoDao().getAllHomeNetworks().first()
            assertEquals(1, resetHomes.size)
            assertEquals("My Home", resetHomes.single().homeName)
            assertFalse(resetHomes.single().ssidLabel.startsWith("WeMo.", ignoreCase = true))
        } finally {
            serverA.close()
            serverB.close()
        }
    }

    @Test
    fun `verify ensureSeeded sanitizes polluted WeMo soft-AP home names and SSIDs and adoptDiscoveredDevice preserves My Home`() = runBlocking {
        val repo = WeMoRepository(
            dao = database.wemoDao(),
            networkClient = WeMoNetworkClient(),
            initialDemoModeOverride = false
        )

        // 1. Seed Room with a polluted Home Network row ("WeMo.Switch.998 Home" / "WeMo.Switch.998") and polluted device wifiSsid
        database.wemoDao().insertHomeNetwork(
            com.example.data.WeMoHomeNetworkEntity(
                networkKey = "wifi_lan_192_168_1",
                homeName = "WeMo.Switch.998 Home",
                ssidLabel = "WeMo.Switch.998",
                subnetPrefix = "192.168.1",
                gatewayIp = "192.168.1.1",
                lastConnectedTime = "Connected now",
                autoReconnectOnArrival = true,
                sortOrder = 0
            )
        )
        database.wemoDao().insertHomeNetwork(
            com.example.data.WeMoHomeNetworkEntity(
                networkKey = "wifi_lan_192_168_50",
                homeName = "Home Wi-Fi (192.168.50.x)",
                ssidLabel = "WeMo.Light.332",
                subnetPrefix = "192.168.50",
                gatewayIp = "192.168.50.1",
                lastConnectedTime = "Yesterday",
                autoReconnectOnArrival = true,
                sortOrder = 1
            )
        )
        val pollutedDeviceId = database.wemoDao().insertDevice(
            WeMoDeviceEntity(
                name = "Porch Switch",
                roomName = "Porch & Entryway",
                networkKey = "wifi_lan_192_168_1",
                wifiSsid = "WeMo.Mini.551",
                ipAddress = "192.168.1.140",
                port = 49153,
                isOn = false,
                isReachable = true,
                serialNumber = "PORCH551"
            )
        ).toInt()
        val stuckBedLampId = database.wemoDao().insertDevice(
            WeMoDeviceEntity(
                name = "Shprese Bed Lamp",
                roomName = "Living Room",
                networkKey = "wifi_lan_192_168_1",
                wifiSsid = "LAN 192.168.1.0/24",
                ipAddress = "192.168.1.205",
                port = 49153,
                isOn = false,
                isReachable = true,
                serialNumber = "BED205"
            )
        ).toInt()
        val stuckStairwayId = database.wemoDao().insertDevice(
            WeMoDeviceEntity(
                name = "Stairway Light",
                roomName = "Living Room",
                networkKey = "wifi_lan_192_168_1",
                wifiSsid = "LAN 192.168.1.0/24",
                ipAddress = "192.168.1.34",
                port = 49153,
                isOn = false,
                isReachable = true,
                serialNumber = "STAIR34"
            )
        ).toInt()
        val stuckGirlsId = database.wemoDao().insertDevice(
            WeMoDeviceEntity(
                name = "Girls Light",
                roomName = "Living Room",
                networkKey = "wifi_lan_192_168_1",
                wifiSsid = "LAN 192.168.1.0/24",
                ipAddress = "192.168.1.40",
                port = 49153,
                isOn = false,
                isReachable = true,
                serialNumber = "GIRLS40"
            )
        ).toInt()
        val stuckUnassignedId = database.wemoDao().insertDevice(
            WeMoDeviceEntity(
                name = "Espresso Machine",
                roomName = "Living Room",
                networkKey = "wifi_lan_192_168_1",
                wifiSsid = "LAN 192.168.1.0/24",
                ipAddress = "192.168.1.99",
                port = 49153,
                isOn = false,
                isReachable = true,
                serialNumber = "ESPRESSO99"
            )
        ).toInt()

        // 2. Calling ensureSeeded() in Real LAN Mode must automatically sanitize both homes, device wifiSsid, and legacy "Living Room" room assignments
        repo.ensureSeeded()
        val sanitizedHomes = database.wemoDao().getAllHomeNetworks().first()
        val primaryHome = sanitizedHomes.first { it.networkKey == "wifi_lan_192_168_1" }
        val secondaryHome = sanitizedHomes.first { it.networkKey == "wifi_lan_192_168_50" }
        assertEquals("My Home", primaryHome.homeName)
        assertEquals("LAN 192.168.1.0/24", primaryHome.ssidLabel)
        assertEquals("Home (192.168.50.x)", secondaryHome.homeName)
        assertEquals("LAN 192.168.50.0/24", secondaryHome.ssidLabel)

        val sanitizedDevice = database.wemoDao().getDeviceById(pollutedDeviceId)!!
        assertEquals("LAN 192.168.1.0/24", sanitizedDevice.wifiSsid)
        assertEquals("Porch & Entryway", sanitizedDevice.roomName)
        assertEquals("Bedroom", database.wemoDao().getDeviceById(stuckBedLampId)!!.roomName)
        assertEquals("Hallway & Landing", database.wemoDao().getDeviceById(stuckStairwayId)!!.roomName)
        assertEquals("Bedroom", database.wemoDao().getDeviceById(stuckGirlsId)!!.roomName)
        assertEquals("Unassigned", database.wemoDao().getDeviceById(stuckUnassignedId)!!.roomName)

        // 3. Adopting a device that reports a soft-AP SSID ("WeMo.Switch.777") must NOT pollute the device or home SSID
        val adoptedSoftAp = repo.adoptDiscoveredDevice(
            discovered = com.example.data.SsdpDiscoveredDevice(
                usn = "uuid:Lightswitch-1_0-SOFT777",
                name = "Garage Overhead Light",
                roomName = "Garage",
                ipAddress = "192.168.1.141",
                port = 49153,
                locationUrl = "http://192.168.1.141:49153/setup.xml",
                deviceType = WeMoDeviceType.SWITCH_PLUG,
                modelName = "LightSwitch",
                hardwareSku = "F7C030",
                serialNumber = "SOFT777",
                firmwareVersion = "WeMo_WW_2.00.11532.PVT",
                macAddress = "EC1A59000777",
                wifiSsid = "WeMo.Switch.777",
                signalStrengthPercent = 88,
                isOn = true,
                brightness = 100,
                responseTimeMs = 14L,
                searchTarget = "urn:Belkin:service:basicevent:1",
                isLiveUdpResponse = true
            ),
            activeNetworkKey = "wifi_lan_192_168_1",
            activeWifiSsid = "LAN 192.168.1.0/24"
        )
        assertEquals("LAN 192.168.1.0/24", adoptedSoftAp.wifiSsid)
        val homeAfterSoftAp = database.wemoDao().getAllHomeNetworks().first().first { it.networkKey == "wifi_lan_192_168_1" }
        assertEquals("My Home", homeAfterSoftAp.homeName)
        assertEquals("LAN 192.168.1.0/24", homeAfterSoftAp.ssidLabel)

        // 4. Adopting a device that reports a real router SSID ("HeadFamily-2.4G") updates generic ssidLabel while keeping homeName = "My Home"
        val adoptedRealRouter = repo.adoptDiscoveredDevice(
            discovered = com.example.data.SsdpDiscoveredDevice(
                usn = "uuid:Lightswitch-1_0-REAL888",
                name = "Kitchen Under-Cabinet Light",
                roomName = "Kitchen",
                ipAddress = "192.168.1.142",
                port = 49153,
                locationUrl = "http://192.168.1.142:49153/setup.xml",
                deviceType = WeMoDeviceType.SWITCH_PLUG,
                modelName = "LightSwitch",
                hardwareSku = "F7C030",
                serialNumber = "REAL888",
                firmwareVersion = "WeMo_WW_2.00.11532.PVT",
                macAddress = "EC1A59000888",
                wifiSsid = "HeadFamily-2.4G",
                signalStrengthPercent = 91,
                isOn = false,
                brightness = 100,
                responseTimeMs = 11L,
                searchTarget = "urn:Belkin:service:basicevent:1",
                isLiveUdpResponse = true
            ),
            activeNetworkKey = "wifi_lan_192_168_1",
            activeWifiSsid = "LAN 192.168.1.0/24"
        )
        assertEquals("HeadFamily-2.4G", adoptedRealRouter.wifiSsid)
        val homeAfterRealRouter = database.wemoDao().getAllHomeNetworks().first().first { it.networkKey == "wifi_lan_192_168_1" }
        assertEquals("My Home", homeAfterRealRouter.homeName)
        assertEquals("HeadFamily-2.4G", homeAfterRealRouter.ssidLabel)
    }
}
