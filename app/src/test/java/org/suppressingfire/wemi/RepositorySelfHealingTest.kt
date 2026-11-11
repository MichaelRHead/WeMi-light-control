package org.suppressingfire.wemi

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DeviceCategory
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient

/**
 * Tier 2 Repository Self-Healing, Optimistic Offline Revert, Placeholder Serial Probing,
 * Startup Soft-AP SSID Sanitization, and Echo Suppression Integration Tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositorySelfHealingTest {

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
    fun `verify Real LAN Mode reverts optimistic toggle and marks device OFFLINE when unreachable`() = runBlocking {
        val tempSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val closedPort = tempSocket.localPort
        tempSocket.close()

        val repo = SwitchRepository(
            dao = database.switchDao(),
            networkClient = NetworkClient(),
            initialDemoModeOverride = false
        )
        repo.ensureSeeded()

        val id = database.switchDao().insertDevice(
            DeviceEntity(
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
        val initial = database.switchDao().getDeviceById(id)!!

        val afterToggle = repo.toggleDevicePower(initial)

        assertFalse(afterToggle.isReachable)
        assertEquals(0, afterToggle.latencyMs)
        assertTrue(afterToggle.isOn)

        val persisted = database.switchDao().getAllDevices().first().single()
        assertFalse(persisted.isReachable)
        assertTrue(persisted.isOn)
    }

    @Test
    fun `verify manual saveDevice probes fresh switch IP for both NEW and LAN placeholder serials`() = runBlocking {
        FakeUpnpServer(
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
            val client = NetworkClient(extraCandidatePorts = listOf(realPort))
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            // 1. Save manual device with DEFAULT_NEW_PLACEHOLDER_SERIAL ("221517K01018NEW")
            repo.saveDevice(
                device = DeviceEntity(
                    id = 0,
                    name = "",
                    roomName = "Living Room",
                    ipAddress = "127.0.0.1",
                    port = 49153,
                    serialNumber = LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL
                ),
                syncFriendlyNameToHardware = false
            )

            val saved = database.switchDao().getAllDevices().first().single()
            assertEquals("Attic Exhaust Fan", saved.name)
            assertEquals(realPort, saved.port)
            assertEquals("WSP080MANUAL77", saved.serialNumber)
            assertEquals("EC1A59CC5566", saved.macAddress)
            assertTrue(saved.hardwareModel.startsWith("WSP080"))
            assertTrue(saved.isReachable)
            assertFalse(saved.isOn)
            assertEquals(89, saved.signalStrengthPercent)
            assertEquals(DeviceCategory.FAN, saved.visualCategory)

            // 2. Verify DEFAULT_LAN_PLACEHOLDER_SERIAL ("221517K01018LAN") also triggers live hardware probing
            repo.deleteDevice(saved.id)
            repo.saveDevice(
                device = DeviceEntity(
                    id = 0,
                    name = "",
                    roomName = "Garage",
                    ipAddress = "127.0.0.1",
                    port = 49153,
                    serialNumber = LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL
                ),
                syncFriendlyNameToHardware = false
            )
            val savedFromLanPlaceholder = database.switchDao().getAllDevices().first().single()
            assertEquals(realPort, savedFromLanPlaceholder.port)
            assertEquals("WSP080MANUAL77", savedFromLanPlaceholder.serialNumber)
        }
    }

    @Test
    fun `verify multi-device startup state detection across ON, OFF accessible standby, and Disconnected devices plus Night Mode and SignalStrength`() = runBlocking {
        val serverA = FakeUpnpServer(
            initialFriendlyName = "Christmas Tree Living",
            serialNumber = "TREE001",
            initialRelayOn = false
        )
        val serverB = FakeUpnpServer(
            initialFriendlyName = "Bedroom Dimmer Lamp",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "LAMP002",
            signalStrengthPercent = 92,
            initialRelayOn = true,
            initialBrightness = 55
        )
        val tempSock = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val closedPortC = tempSock.localPort
        tempSock.close()

        try {
            val client = NetworkClient()
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            val idTree = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Christmas Tree Living",
                    roomName = "Living Room",
                    ipAddress = "127.0.0.1",
                    port = serverA.port,
                    isOn = true,
                    isReachable = true,
                    serialNumber = "TREE001"
                )
            ).toInt()
            val idLamp = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Bedroom Dimmer Lamp",
                    roomName = "Bedroom",
                    deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "127.0.0.1",
                    port = serverB.port,
                    isOn = false,
                    brightness = 100,
                    isReachable = false,
                    serialNumber = "LAMP002"
                )
            ).toInt()
            val idFan = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Garage Exhaust Fan",
                    roomName = "Garage",
                    ipAddress = "127.0.0.1",
                    port = closedPortC,
                    isOn = true,
                    isReachable = true,
                    serialNumber = "FAN003"
                )
            ).toInt()

            val updatedList = repo.detectStartupDeviceStates("wifi_home_192_168_1")
            val treeAfter = updatedList.single { it.id == idTree }
            val lampAfter = updatedList.single { it.id == idLamp }
            val fanAfter = updatedList.single { it.id == idFan }

            assertTrue(treeAfter.isReachable)
            assertFalse(treeAfter.isOn)
            assertEquals(DeviceCategory.CHRISTMAS_TREE, treeAfter.visualCategory)

            assertTrue(lampAfter.isReachable)
            assertTrue(lampAfter.isOn)
            assertEquals(55, lampAfter.brightness)
            assertEquals(DeviceCategory.LIGHT, lampAfter.visualCategory)

            assertFalse(fanAfter.isReachable)
            assertEquals(0, fanAfter.latencyMs)
            assertEquals(DeviceCategory.FAN, fanAfter.visualCategory)

            val nightResult = repo.toggleDimmerNightMode(lampAfter)
            assertTrue(nightResult.nightModeEnabled)
            assertTrue(serverB.nightModeEnabled.get())

            val rssi = client.getSignalStrength("127.0.0.1", serverB.port)
            assertEquals(92, rssi)

            val updatedBulbs = repo.updateRoomBrightness(
                roomName = "Bedroom",
                newBrightness = 35,
                networkKey = "wifi_home_192_168_1"
            )
            assertEquals(1, updatedBulbs)

            val autoOffResult = repo.turnOffDeviceById(idLamp)
            assertNotNull(autoOffResult)

            repo.resetLanHomesSwitchesAndRooms()
            assertEquals(0, database.switchDao().getDeviceCount())
            val resetHomes = database.switchDao().getAllHomeNetworks().first()
            assertEquals(1, resetHomes.size)
            assertEquals("My Home", resetHomes.single().homeName)
            assertFalse(resetHomes.single().ssidLabel.startsWith("WeMo.", ignoreCase = true))
        } finally {
            serverA.close()
            serverB.close()
        }
    }

    @Test
    fun `verify ensureSeeded sanitizes polluted soft-AP home names and SSIDs and adoptDiscoveredDevice preserves My Home`() = runBlocking {
        val repo = SwitchRepository(
            dao = database.switchDao(),
            networkClient = NetworkClient(),
            initialDemoModeOverride = false
        )

        database.switchDao().insertHomeNetwork(
            HomeNetworkEntity(
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
        database.switchDao().insertHomeNetwork(
            HomeNetworkEntity(
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
        val pollutedDeviceId = database.switchDao().insertDevice(
            DeviceEntity(
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
        suspend fun insertLegacyLivingRoomSwitch(name: String, ip: String, sn: String): Int =
            database.switchDao().insertDevice(
                DeviceEntity(
                    name = name,
                    roomName = "Living Room",
                    networkKey = "wifi_lan_192_168_1",
                    wifiSsid = "LAN 192.168.1.0/24",
                    ipAddress = ip,
                    port = 49153,
                    isOn = false,
                    isReachable = true,
                    serialNumber = sn
                )
            ).toInt()

        val stuckBedLampId = insertLegacyLivingRoomSwitch("Shprese Bed Lamp", "192.168.1.205", "BED205")
        val stuckStairwayId = insertLegacyLivingRoomSwitch("Stairway Light", "192.168.1.34", "STAIR34")
        val stuckGirlsId = insertLegacyLivingRoomSwitch("Girls Light", "192.168.1.40", "GIRLS40")
        val stuckUnassignedId = insertLegacyLivingRoomSwitch("Espresso Machine", "192.168.1.99", "ESPRESSO99")

        repo.ensureSeeded()
        val sanitizedHomes = database.switchDao().getAllHomeNetworks().first()
        val primaryHome = sanitizedHomes.first { it.networkKey == "wifi_lan_192_168_1" }
        val secondaryHome = sanitizedHomes.first { it.networkKey == "wifi_lan_192_168_50" }
        assertEquals("My Home", primaryHome.homeName)
        assertEquals("LAN 192.168.1.0/24", primaryHome.ssidLabel)
        assertEquals("Home (192.168.50.x)", secondaryHome.homeName)
        assertEquals("LAN 192.168.50.0/24", secondaryHome.ssidLabel)

        val sanitizedDevice = database.switchDao().getDeviceById(pollutedDeviceId)!!
        assertEquals("LAN 192.168.1.0/24", sanitizedDevice.wifiSsid)
        assertEquals("Porch & Entryway", sanitizedDevice.roomName)
        assertEquals("Bedroom", database.switchDao().getDeviceById(stuckBedLampId)!!.roomName)
        assertEquals("Hallway & Landing", database.switchDao().getDeviceById(stuckStairwayId)!!.roomName)
        assertEquals("Bedroom", database.switchDao().getDeviceById(stuckGirlsId)!!.roomName)
        assertEquals("Unassigned", database.switchDao().getDeviceById(stuckUnassignedId)!!.roomName)

        val adoptedSoftAp = repo.adoptDiscoveredDevice(
            discovered = SsdpDiscoveredDevice(
                usn = "uuid:Lightswitch-1_0-SOFT777",
                name = "Garage Overhead Light",
                roomName = "Garage",
                ipAddress = "192.168.1.141",
                port = 49153,
                locationUrl = "http://192.168.1.141:49153/setup.xml",
                deviceType = DeviceType.SWITCH_PLUG,
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
        val homeAfterSoftAp = database.switchDao().getAllHomeNetworks().first().first { it.networkKey == "wifi_lan_192_168_1" }
        assertEquals("My Home", homeAfterSoftAp.homeName)
        assertEquals("LAN 192.168.1.0/24", homeAfterSoftAp.ssidLabel)

        val adoptedRealRouter = repo.adoptDiscoveredDevice(
            discovered = SsdpDiscoveredDevice(
                usn = "uuid:Lightswitch-1_0-REAL888",
                name = "Kitchen Under-Cabinet Light",
                roomName = "Kitchen",
                ipAddress = "192.168.1.142",
                port = 49153,
                locationUrl = "http://192.168.1.142:49153/setup.xml",
                deviceType = DeviceType.SWITCH_PLUG,
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
        val homeAfterRealRouter = database.switchDao().getAllHomeNetworks().first().first { it.networkKey == "wifi_lan_192_168_1" }
        assertEquals("My Home", homeAfterRealRouter.homeName)
        assertEquals("HeadFamily-2.4G", homeAfterRealRouter.ssidLabel)
    }

    @Test
    fun `verify toggleDimmerNightMode 9-port self-healing and Real LAN offline revert plus pollActiveHomeStates echo suppression`() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Master Bedroom Dimmer",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060NIGHT77",
            udn = "uuid:Dimmer-2_0-WDS060NIGHT77",
            initialRelayOn = true,
            initialBrightness = 85,
            registerAsCandidatePort = true
        ).use { server ->
            val client = NetworkClient(extraCandidatePorts = listOf(server.port))
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            val staleDimmerId = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Master Bedroom Dimmer",
                    roomName = "Bedroom",
                    networkKey = "wifi_lan_192_168_1",
                    wifiSsid = "HeadFamily-2.4G",
                    deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "127.0.0.1",
                    port = 49159,
                    isOn = true,
                    isReachable = true,
                    brightness = 85,
                    nightModeEnabled = false,
                    serialNumber = "WDS060NIGHT77",
                    hardwareModel = "WDS060"
                )
            ).toInt()
            val staleDimmer = database.switchDao().getDeviceById(staleDimmerId)!!

            // 1. Toggling Night Mode ON heals from stale port 49159 -> server.port and caps brightness 85% -> 20%
            val healed = repo.toggleDimmerNightMode(staleDimmer)
            assertTrue(healed.isReachable)
            assertEquals(server.port, healed.port)
            assertTrue(healed.nightModeEnabled)
            assertEquals(20, healed.brightness)
            assertTrue(server.nightModeEnabled.get())

            // 2. Immediate background poll within 2000ms echo-suppression window must NOT overwrite local state
            server.relayStateOn.set(false)
            server.dimmerBrightness.set(99)
            repo.pollActiveHomeStates("wifi_lan_192_168_1")
            val afterSuppressedPoll = database.switchDao().getDeviceById(staleDimmerId)!!
            assertTrue(afterSuppressedPoll.isOn)
            assertEquals(20, afterSuppressedPoll.brightness)
            assertTrue(afterSuppressedPoll.nightModeEnabled)

            // 3. Close server to simulate offline dimmer in Real LAN Mode -> Night Mode toggle must revert and mark OFFLINE
            server.close()
            val reverted = repo.toggleDimmerNightMode(afterSuppressedPoll)
            assertFalse(reverted.isReachable)
            assertEquals(0, reverted.latencyMs)
            assertTrue(reverted.nightModeEnabled)
            assertEquals(20, reverted.brightness)
            assertEquals("Offline • Timeout", reverted.lastCheckedTime)
        }
    }
}
