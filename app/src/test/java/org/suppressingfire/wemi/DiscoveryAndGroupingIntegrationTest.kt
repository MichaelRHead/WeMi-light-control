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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.SsdpDiscoveryEngine

/**
 * Tier 2 Discovery & Room Grouping Hardening Integration Tests:
 * - expectedHardwareIdentity, Tier-2 canonical UDN mismatch guard & Stage C multicast-zero fallback policy
 * - probeWemoPort & executeWithPortHealing Tier-2 UDN verification when serial is blank
 * - 4-pass hardware identity resolution priority (Real Serial -> Canonical UDN -> Exact IP:Port -> Friendly Name)
 * - Router SSID preservation on soft-AP re-adoption and stale-free Home SSID sync on reconnect
 * - Manual IP Tier-2 UDN deduplication when existing serial is blank & whitespace-trimmed room reordering
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiscoveryAndGroupingIntegrationTest {

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
    fun `verify expectedHardwareIdentity, Tier-2 UDN mismatch guard, and Stage C multicast-zero fallback policy`() {
        assertEquals(
            "221644K0101234",
            LanRuntimeUtils.expectedHardwareIdentity("221644K0101234", "uuid:Socket-1_0-221644K0101234")
        )
        assertEquals(
            "uuid:Socket-1_0-221644K0101234",
            LanRuntimeUtils.expectedHardwareIdentity("UNKNOWN", "uuid:Socket-1_0-221644K0101234::urn:Belkin:service:basicevent:1")
        )
        assertEquals("", LanRuntimeUtils.expectedHardwareIdentity("UNKNOWN", "uuid:Socket-1_0-221517K01018NEW"))

        val udnOnlyDevice = DeviceEntity(
            name = "WeMo Light Switch",
            ipAddress = "192.168.1.40",
            port = 49153,
            serialNumber = "",
            udn = "uuid:Socket-1_0-221644K010AAA1"
        )
        assertEquals("uuid:Socket-1_0-221644K010AAA1", udnOnlyDevice.concurrencyKey())

        val sameIpDifferentUdn = SsdpDiscoveredDevice(
            name = "WeMo Light Switch",
            ipAddress = "192.168.1.40",
            port = 49153,
            deviceType = DeviceType.SWITCH_PLUG,
            modelName = "Socket-1_0",
            serialNumber = "",
            firmwareVersion = "",
            usn = "uuid:Socket-1_0-221644K010BBB2::urn:Belkin:service:basicevent:1",
            searchTarget = "urn:Belkin:service:basicevent:1",
            locationUrl = "http://192.168.1.40:49153/setup.xml",
            responseTimeMs = 10L
        )
        assertFalse(
            "Mismatched canonical UDNs must never fall through to Tier-3 IP/name matching",
            udnOnlyDevice.matchesDiscovered(sameIpDifferentUdn, requireSamePortWhenBlankSerial = false)
        )

        val dhcpMovedSameUdn = sameIpDifferentUdn.copy(
            ipAddress = "192.168.1.99",
            port = 49154,
            usn = "uuid:Socket-1_0-221644K010AAA1::urn:Belkin:service:basicevent:1"
        )
        assertTrue(udnOnlyDevice.matchesDiscovered(dhcpMovedSameUdn, requireSamePortWhenBlankSerial = true))

        val knownA = udnOnlyDevice.copy(serialNumber = "221644K010AAA1")
        val knownB = udnOnlyDevice.copy(
            name = "Porch Switch",
            ipAddress = "192.168.1.41",
            serialNumber = "221644K010BBB2",
            udn = "uuid:Socket-1_0-221644K010BBB2"
        )
        val stageBHitA = dhcpMovedSameUdn.copy(serialNumber = "221644K010AAA1", isLiveUdpResponse = false)
        val stageBHitB = sameIpDifferentUdn.copy(
            ipAddress = "192.168.1.41",
            serialNumber = "221644K010BBB2",
            isLiveUdpResponse = false
        )
        val multicastHitA = stageBHitA.copy(isLiveUdpResponse = true)

        assertFalse(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                listOf(multicastHitA), listOf(knownA, knownB),
                allowUnicastSubnetFallback = true, fastMode = false, includeDemoFallbacks = false, onlyWhenKnownMissing = false
            )
        )
        assertTrue(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                listOf(stageBHitA), listOf(knownA),
                allowUnicastSubnetFallback = true, fastMode = false, includeDemoFallbacks = false, onlyWhenKnownMissing = false
            )
        )
        assertFalse(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                listOf(stageBHitA, stageBHitB), listOf(knownA, knownB),
                allowUnicastSubnetFallback = true, fastMode = false, includeDemoFallbacks = false, onlyWhenKnownMissing = true
            )
        )
        assertTrue(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                listOf(stageBHitA), listOf(knownA, knownB),
                allowUnicastSubnetFallback = true, fastMode = false, includeDemoFallbacks = false, onlyWhenKnownMissing = true
            )
        )
        assertFalse(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                emptyList(), emptyList(),
                allowUnicastSubnetFallback = true, fastMode = true, includeDemoFallbacks = false, onlyWhenKnownMissing = false
            )
        )
    }

    @Test
    fun `verify probeWemoPort and executeWithPortHealing enforce Tier-2 UDN identity when serial is blank`() =
        runBlocking {
            val deadPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            FakeUpnpServer(
                initialFriendlyName = "Porch Plug",
                serialNumber = "UNKNOWN",
                udn = "uuid:Socket-1_0-221644K010AAA1",
                initialRelayOn = true
            ).use { serverA ->
                FakeUpnpServer(
                    initialFriendlyName = "Garage Plug",
                    serialNumber = "UNKNOWN",
                    udn = "uuid:Socket-1_0-221644K010BBB2",
                    initialRelayOn = false
                ).use { serverB ->
                    val client = NetworkClient(extraCandidatePorts = listOf(serverA.port, serverB.port))
                    assertEquals(
                        serverA.port,
                        client.probeWemoPort(
                            ipAddress = "127.0.0.1",
                            preferredPort = deadPort,
                            expectedSerialOrUdn = "uuid:Socket-1_0-221644K010AAA1",
                            candidatePortsOverride = listOf(deadPort, serverA.port)
                        )
                    )
                    assertEquals(
                        null,
                        client.probeWemoPort(
                            ipAddress = "127.0.0.1",
                            preferredPort = deadPort,
                            expectedSerialOrUdn = "uuid:Socket-1_0-221644K010AAA1",
                            candidatePortsOverride = listOf(deadPort, serverB.port)
                        )
                    )
                    assertEquals(
                        serverA.port,
                        client.probeWemoPort(
                            ipAddress = "127.0.0.1",
                            preferredPort = deadPort,
                            expectedSerialOrUdn = "uuid:Socket-1_0-221644K010AAA1",
                            candidatePortsOverride = listOf(deadPort, serverB.port, serverA.port)
                        )
                    )

                    val matchingUdnDevice = DeviceEntity(
                        name = "Porch Plug",
                        ipAddress = "127.0.0.1",
                        port = deadPort,
                        serialNumber = "",
                        udn = "uuid:Socket-1_0-221644K010AAA1"
                    )
                    val healed = client.getBinaryState(matchingUdnDevice)
                    assertTrue(healed.reachableOnLan)
                    assertEquals(serverA.port, healed.resolvedPort)
                    assertEquals(true, healed.binaryStateOn)

                    val foreignUdnClient = NetworkClient(extraCandidatePorts = listOf(serverB.port))
                    val foreignUdnDevice = matchingUdnDevice.copy(udn = "uuid:Socket-1_0-221644K010CCC3")
                    val rejected = foreignUdnClient.getBinaryState(foreignUdnDevice)
                    assertFalse(
                        "executeWithPortHealing must not fall back to an unverified port when canonical UDN exists",
                        rejected.reachableOnLan
                    )
                }
            }
        }

    @Test
    fun `verify 4-pass identity priority prevents factory-named switch cross-matching on UDN and endpoint`() =
        runBlocking {
            val dao = database.switchDao()
            val homeKey = "wifi_lan_192_168_1"
            dao.insertHomeNetwork(HomeNetworkEntity(homeKey, "My Home", "HomeNet-2.4G", "192.168.1"))

            // Two factory-named switches with blank serials distinguished only by canonical UDN
            val devAId = dao.insertDevice(
                DeviceEntity(
                    name = "WeMo Light Switch", roomName = "Unassigned", networkKey = homeKey,
                    ipAddress = "192.168.1.40", port = 49153, serialNumber = "",
                    udn = "uuid:Socket-1_0-221644K010AAA1", sortOrder = 0
                )
            ).toInt()
            val devBId = dao.insertDevice(
                DeviceEntity(
                    name = "WeMo Light Switch", roomName = "Unassigned", networkKey = homeKey,
                    ipAddress = "192.168.1.41", port = 49154, serialNumber = "",
                    udn = "uuid:Socket-1_0-221644K010BBB2", sortOrder = 1
                )
            ).toInt()

            val repo = SwitchRepository(dao = dao, networkClient = NetworkClient(), initialDemoModeOverride = false)
            val discoveredB = SsdpDiscoveredDevice(
                name = "WeMo Light Switch", ipAddress = "192.168.1.88", port = 49155,
                deviceType = DeviceType.SWITCH_PLUG, modelName = "Socket-1_0", serialNumber = "",
                firmwareVersion = "WeMo_WW_2.00.12000", usn = "uuid:Socket-1_0-221644K010BBB2::urn:Belkin:service:basicevent:1",
                searchTarget = "urn:Belkin:service:basicevent:1", locationUrl = "http://192.168.1.88:49155/setup.xml",
                responseTimeMs = 9L
            )
            val updatedB = repo.adoptDiscoveredDevice(discoveredB, homeKey, "HomeNet-2.4G")
            assertEquals(devBId, updatedB.id)
            assertEquals("192.168.1.88", updatedB.ipAddress)
            assertEquals(49155, updatedB.port)
            assertEquals("192.168.1.40", dao.getDeviceById(devAId)?.ipAddress)

            // Two blank-serial, blank-UDN factory-named switches distinguished only by exact IP:Port (Tier 3a > Tier 3b)
            val plain1Id = dao.insertDevice(
                DeviceEntity(
                    name = "WeMo Mini", roomName = "Unassigned", networkKey = homeKey,
                    ipAddress = "192.168.1.50", port = 49153, isOn = false, serialNumber = "", udn = "", sortOrder = 2
                )
            ).toInt()
            val plain2Id = dao.insertDevice(
                DeviceEntity(
                    name = "WeMo Mini", roomName = "Unassigned", networkKey = homeKey,
                    ipAddress = "192.168.1.51", port = 49153, isOn = false, serialNumber = "", udn = "", sortOrder = 3
                )
            ).toInt()

            val discoveredPlain2 = discoveredB.copy(
                name = "WeMo Mini", ipAddress = "192.168.1.51", port = 49153,
                usn = "", locationUrl = "http://192.168.1.51:49153/setup.xml", isOn = true
            )
            val matchedPlain2 = repo.adoptDiscoveredDevice(discoveredPlain2, homeKey, "HomeNet-2.4G")
            assertEquals(plain2Id, matchedPlain2.id)
            assertNotEquals(plain1Id, matchedPlain2.id)
            assertTrue(dao.getDeviceById(plain2Id)?.isOn == true)
            assertEquals(false, dao.getDeviceById(plain1Id)?.isOn)
        }

    @Test
    fun `verify reconnect syncs router SSID without clobbering and re-adoption preserves router SSID on soft-AP`() =
        runBlocking {
            val dao = database.switchDao()
            val homeKey = "wifi_lan_127_0_0"
            dao.insertHomeNetwork(HomeNetworkEntity(homeKey, "My Home", "LAN 127.0.0.0/24", "127.0.0"))

            FakeUpnpServer(
                initialFriendlyName = "Kitchen Island Dimmer", serialNumber = "221840K0105511",
                udn = "uuid:Dimmer-1_0-221840K0105511", wifiSsid = "HeadFamily-2.4G", enableSsdpResponder = true
            ).use { server ->
                val repo = SwitchRepository(dao = dao, networkClient = NetworkClient(), initialDemoModeOverride = false)
                val summary = repo.reconnectAndRediscoverHome(homeKey)
                assertEquals(1, summary.reconnectedCount)
                assertEquals("HeadFamily-2.4G", summary.ssidLabel)
                assertEquals("HeadFamily-2.4G", dao.getAllHomeNetworks().first().first { it.networkKey == homeKey }.ssidLabel)

                val persistedSwitch = dao.getAllDevices().first().first()
                assertEquals("HeadFamily-2.4G", persistedSwitch.wifiSsid)
                assertEquals("Kitchen", persistedSwitch.roomName)

                // Re-adopting when GetMetaInfo returns a Belkin soft-AP SSID must keep the known router SSID
                val softApDiscovery = SsdpDiscoveredDevice(
                    name = "Kitchen Island Dimmer", roomName = "Kitchen", ipAddress = "127.0.0.1", port = server.port,
                    deviceType = DeviceType.LIGHTBULB_DIMMER, modelName = "Dimmer-1_0", serialNumber = "221840K0105511",
                    firmwareVersion = "WeMo_WW_2.00.12000", usn = "uuid:Dimmer-1_0-221840K0105511::urn:Belkin:service:basicevent:1",
                    searchTarget = "urn:Belkin:service:basicevent:1", locationUrl = "http://127.0.0.1:${server.port}/setup.xml",
                    responseTimeMs = 6L, wifiSsid = "WeMo.Dimmer.551"
                )
                assertEquals("HeadFamily-2.4G", repo.adoptDiscoveredDevice(softApDiscovery, homeKey, "LAN 127.0.0.0/24").wifiSsid)
            }
        }

    @Test
    fun `verify manual IP saveDevice deduplicates via UDN when existing serial is blank and trims roomName on reorder`() =
        runBlocking {
            val dao = database.switchDao()
            val homeKey = "wifi_lan_127_0_0"
            dao.insertHomeNetwork(
                HomeNetworkEntity(
                    networkKey = homeKey,
                    homeName = "My Home",
                    ssidLabel = "HeadFamily-2.4G",
                    subnetPrefix = "127.0.0"
                )
            )

            FakeUpnpServer(
                initialFriendlyName = "Kitchen Pantry Switch",
                serialNumber = "WLS0403SN7744",
                udn = "uuid:Lightswitch-3_0-WLS0403SN7744",
                initialRelayOn = true
            ).use { server ->
                val existingId = dao.insertDevice(
                    DeviceEntity(
                        name = "Kitchen Pantry Switch",
                        roomName = "  Kitchen  ",
                        networkKey = homeKey,
                        ipAddress = "127.0.0.1",
                        port = 49153,
                        serialNumber = "",
                        udn = "uuid:Lightswitch-3_0-WLS0403SN7744",
                        sortOrder = 0
                    )
                ).toInt()
                val unassignedId = dao.insertDevice(
                    DeviceEntity(
                        name = "Desk Plug",
                        roomName = "Unassigned",
                        networkKey = homeKey,
                        ipAddress = "192.168.1.77",
                        port = 49153,
                        serialNumber = "SER-DESK-1",
                        sortOrder = 1
                    )
                ).toInt()
                val kitchen2Id = dao.insertDevice(
                    DeviceEntity(
                        name = "Kitchen Sink Light",
                        roomName = "Kitchen",
                        networkKey = homeKey,
                        ipAddress = "192.168.1.78",
                        port = 49153,
                        serialNumber = "SER-SINK-2",
                        sortOrder = 2
                    )
                ).toInt()

                val repo = SwitchRepository(dao = dao, networkClient = NetworkClient(), initialDemoModeOverride = false)
                val saved = repo.saveDevice(
                    DeviceEntity(
                        id = 0,
                        name = "",
                        roomName = "Unassigned",
                        networkKey = homeKey,
                        ipAddress = "127.0.0.1",
                        port = server.port,
                        serialNumber = ""
                    )
                )
                assertEquals(existingId, saved.id)
                assertEquals("WLS0403SN7744", saved.serialNumber)
                assertEquals(3, dao.getAllDevices().first().size)

                // Restore whitespace-padded room to verify moveDeviceOrder trims roomName consistently
                dao.updateDevice(saved.copy(roomName = "  Kitchen  "))
                repo.moveDeviceOrder(dao.getDeviceById(existingId)!!, moveUp = false, withinRoomOnly = true)
                val reordered = dao.getAllDevices().first().sortedBy { it.sortOrder }.map { it.id }
                assertEquals(listOf(kitchen2Id, unassignedId, existingId), reordered)
            }
        }

    @Test
    fun `verify executeSsdpScan anti-hijack guard and exact-endpoint-first batch adoption ordering`() =
        runBlocking {
            val dao = database.switchDao()
            val homeKey = "wifi_lan_127_0_0"
            dao.insertHomeNetwork(HomeNetworkEntity(homeKey, "My Home", "HomeNet-2.4G", "127.0.0"))
            val deadPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

            FakeUpnpServer(
                initialFriendlyName = "WeMo Light Switch", serialNumber = "SER-ONLINE-A",
                udn = "uuid:Socket-1_0-SER-ONLINE-A", initialRelayOn = true, enableSsdpResponder = true
            ).use { serverA ->
                val onlineAId = dao.insertDevice(
                    DeviceEntity(
                        name = "WeMo Light Switch", roomName = "Unassigned", networkKey = homeKey,
                        ipAddress = "127.0.0.1", port = serverA.port, isOn = false,
                        serialNumber = "SER-ONLINE-A", udn = "uuid:Socket-1_0-SER-ONLINE-A", sortOrder = 0
                    )
                ).toInt()
                val offlineBId = dao.insertDevice(
                    DeviceEntity(
                        name = "WeMo Light Switch", roomName = "Unassigned", networkKey = homeKey,
                        ipAddress = "127.0.0.1", port = deadPort, isOn = false,
                        serialNumber = "", udn = "", sortOrder = 1
                    )
                ).toInt()

                val repo = SwitchRepository(dao = dao, networkClient = NetworkClient(), initialDemoModeOverride = false)
                repo.executeSsdpScan(autoAdoptNew = false, activeNetworkKey = homeKey)
                assertEquals(true, dao.getDeviceById(onlineAId)?.isOn)
                assertEquals("", dao.getDeviceById(offlineBId)?.serialNumber)
                assertEquals(deadPort, dao.getDeviceById(offlineBId)?.port)

                // Batch adoption with a brand-new factory-named switch listed before existing .51 must not swap rows
                val blank51Id = dao.insertDevice(
                    DeviceEntity(
                        name = "WeMo Mini", roomName = "Unassigned", networkKey = homeKey,
                        ipAddress = "192.168.1.51", port = 49153, serialNumber = "", udn = "", sortOrder = 2
                    )
                ).toInt()
                val baseDisc = SsdpDiscoveredDevice(
                    name = "WeMo Mini", ipAddress = "192.168.1.99", port = 49153,
                    deviceType = DeviceType.SWITCH_PLUG, modelName = "Socket", serialNumber = "SER-NEW-99",
                    firmwareVersion = "WeMo_WW_2.00", usn = "uuid:Socket-1_0-SER-NEW-99",
                    searchTarget = "urn:Belkin:service:basicevent:1", locationUrl = "http://192.168.1.99:49153/setup.xml",
                    responseTimeMs = 8L
                )
                val disc51 = baseDisc.copy(
                    ipAddress = "192.168.1.51", serialNumber = "SER-REAL-51",
                    usn = "uuid:Socket-1_0-SER-REAL-51", locationUrl = "http://192.168.1.51:49153/setup.xml"
                )
                assertEquals(1, repo.adoptDiscoveredDevices(listOf(baseDisc, disc51), homeKey, "HomeNet-2.4G"))
                assertEquals("SER-REAL-51", dao.getDeviceById(blank51Id)?.serialNumber)
                assertEquals("192.168.1.51", dao.getDeviceById(blank51Id)?.ipAddress)
                assertTrue(dao.getAllDevices().first().any { it.serialNumber == "SER-NEW-99" && it.ipAddress == "192.168.1.99" })
            }
        }
}
