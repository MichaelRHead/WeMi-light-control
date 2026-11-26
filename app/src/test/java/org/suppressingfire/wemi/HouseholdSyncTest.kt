package org.suppressingfire.wemi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
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
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HouseholdImportMode
import org.suppressingfire.wemi.network.HouseholdSyncBundle
import org.suppressingfire.wemi.network.HouseholdSyncCodec
import org.suppressingfire.wemi.network.HouseholdSyncCrypto
import org.suppressingfire.wemi.network.HouseholdSyncLanClient
import org.suppressingfire.wemi.network.HouseholdSyncLanServer
import org.suppressingfire.wemi.network.ReceivedHouseholdSync
import org.suppressingfire.wemi.network.SyncedDeviceProfile
import org.suppressingfire.wemi.network.SyncedHomeProfile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HouseholdSyncTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        SwitchDatabase.closeCurrentInstance()
    }

    @Test
    fun jsonCodecRoundTripPreservesCuratedFieldsAndSanitizesUnsafeValues() {
        val homes = listOf(
            HomeNetworkEntity(
                networkKey = "wifi_home_192_168_1",
                homeName = "Main \"Lake\" House",
                ssidLabel = "WeMo.Light.999",
                subnetPrefix = "192.168.1",
                gatewayIp = "192.168.1.1",
                sortOrder = 0
            ),
            HomeNetworkEntity(
                networkKey = "wifi_cgnat_bad",
                homeName = "Chromebook Temp",
                ssidLabel = "HomeWiFi-5G",
                subnetPrefix = "100.115.92",
                gatewayIp = "100.115.92.1",
                sortOrder = 1
            )
        )
        val devices = listOf(
            DeviceEntity(
                id = 1,
                name = "Pierre's \"Warm\" Lamp",
                roomName = "  Sunroom Loft  ",
                networkKey = "wifi_home_192_168_1",
                wifiSsid = "HomeWiFi-5G",
                deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "192.168.1.44",
                port = 49153,
                serialNumber = "221840K01001",
                udn = "uuid:Dimmer-1_0-221840K01001::urn:Belkin:device:dimmer:1",
                macAddress = "EC1A59001122",
                hardwareModel = "Dimmer",
                sortOrder = 3
            ),
            DeviceEntity(
                id = 2,
                name = "Rogue Public IP",
                roomName = "",
                networkKey = "wifi_home_192_168_1",
                wifiSsid = "Wemo.LS3W.123",
                deviceType = DeviceType.SWITCH_PLUG.name,
                ipAddress = "8.8.8.8",
                port = 49152,
                serialNumber = "221517K01018AB",
                sortOrder = 4
            )
        )

        val bundle = HouseholdSyncCodec.fromEntities(homes, devices, sourceDeviceLabel = "Pixel 9\r\nEvil")
        val json = HouseholdSyncCodec.encodeToJson(bundle)
        val decoded = HouseholdSyncCodec.decodeFromJson(json)

        assertEquals(HouseholdSyncCodec.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("Pixel 9", decoded.sourceDeviceLabel)
        assertEquals(2, decoded.homes.size)
        assertEquals("Main \"Lake\" House", decoded.homes[0].homeName)
        assertEquals("Local LAN", decoded.homes[0].ssidLabel) // Soft-AP SSID stripped
        assertEquals("", decoded.homes[1].subnetPrefix) // CGNAT stripped
        assertEquals("", decoded.homes[1].gatewayIp)

        assertEquals(2, decoded.devices.size)
        assertEquals("Pierre's \"Warm\" Lamp", decoded.devices[0].name)
        assertEquals("Sunroom Loft", decoded.devices[0].roomName)
        assertEquals("221840K01001", decoded.devices[0].serialNumber)
        assertEquals("uuid:Dimmer-1_0-221840K01001", decoded.devices[0].udn)
        assertEquals("192.168.1.44", decoded.devices[0].ipAddress)

        assertEquals("Unassigned", decoded.devices[1].roomName)
        assertEquals("", decoded.devices[1].wifiSsid) // Soft-AP SSID stripped
        assertEquals("", decoded.devices[1].ipAddress) // Public IP stripped
        assertEquals("", decoded.devices[1].serialNumber) // Placeholder serial stripped

        // Reject unsupported schemaVersion, oversized input, deep nesting, and empty bundles
        assertTrue(runCatching { HouseholdSyncCodec.decodeFromJson("""{"schemaVersion":99,"homes":[],"devices":[]}""") }.isFailure)
        assertTrue(runCatching { HouseholdSyncCodec.decodeFromJson("""{"schemaVersion":1,"homes":[],"devices":[]}""") }.isFailure)
        assertTrue(runCatching { HouseholdSyncCodec.decodeFromJson("{" + " ".repeat(HouseholdSyncCodec.MAX_JSON_BYTES + 10) + "}") }.isFailure)
        assertTrue(runCatching { HouseholdSyncCodec.decodeFromJson("[".repeat(20) + "]".repeat(20)) }.isFailure)
    }

    @Test
    fun cryptoEnvelopeAuthenticatesPinAndRejectsTampering() {
        val pin = HouseholdSyncCrypto.generatePairingCode()
        assertEquals(6, pin.length)
        val formatted = HouseholdSyncCrypto.formatPairingCode(pin)
        assertEquals(7, formatted.length)

        val payload = """{"schemaVersion":1,"homes":[],"devices":[{"name":"Porch"}]}"""
        val envelope = HouseholdSyncCrypto.encryptEnvelope(payload, formatted, senderLabel = "Pixel Phone")
        assertFalse("Ciphertext envelope must not contain plaintext payload", envelope.contains("Porch"))
        assertFalse("Ciphertext envelope must not leak pairing code", envelope.contains(pin))

        val (sender, decrypted) = HouseholdSyncCrypto.decryptEnvelope(envelope, pin)
        assertEquals("Pixel Phone", sender)
        assertEquals(payload, decrypted)

        val wrongPin = if (pin == "000000") "000001" else "000000"
        assertTrue(
            "Wrong PIN must throw SecurityException",
            runCatching { HouseholdSyncCrypto.decryptEnvelope(envelope, wrongPin) }.exceptionOrNull() is SecurityException
        )

        val tampered = envelope.replace("Pixel Phone", "Spoofed")
        assertEquals("Spoofed", HouseholdSyncCrypto.decryptEnvelope(tampered, pin).first)
        val tamperedCt = envelope.replace("\"ct\":\"", "\"ct\":\"AA")
        assertTrue(
            "Tampered ciphertext must fail GCM authentication",
            runCatching { HouseholdSyncCrypto.decryptEnvelope(tamperedCt, pin) }.isFailure
        )
    }

    @Test
    fun mergePreservesLiveStateMatchesThreeTierIdentityAndDoesNotCollideConflictingSerials() = runBlocking {
        val dao = SwitchDatabase.getDatabase(context).switchDao()
        val repo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
        try {
            dao.insertHomeNetwork(
                HomeNetworkEntity(
                    networkKey = "wifi_lan_192_168_1",
                    homeName = "My Home",
                    ssidLabel = "Local LAN",
                    subnetPrefix = "192.168.1",
                    gatewayIp = "192.168.1.1",
                    sortOrder = 0
                )
            )
            val existingSwitchId = dao.insertDevice(
                DeviceEntity(
                    name = "Original Porch",
                    roomName = "Unassigned",
                    networkKey = "wifi_lan_192_168_1",
                    ipAddress = "192.168.1.99",
                    port = 49154,
                    isOn = true,
                    isReachable = true,
                    brightness = 75,
                    serialNumber = "221840K01010",
                    udn = "uuid:Lightswitch-1_0-221840K01010",
                    hardwareRulesCapability = HardwareRulesCapability.VERIFIED_SUPPORTED.name,
                    hardwareRulesVerifiedFirmware = "WeMo_WW_2.00.11532.PVT-OWRT-LS",
                    sortOrder = 5
                )
            ).toInt()
            // Different real serial sharing same IP/name should NEVER be overwritten
            dao.insertDevice(
                DeviceEntity(
                    name = "Kitchen Island",
                    roomName = "Kitchen",
                    networkKey = "wifi_lan_192_168_1",
                    ipAddress = "192.168.1.50",
                    port = 49153,
                    isOn = false,
                    isReachable = true,
                    serialNumber = "221840K99999",
                    sortOrder = 1
                )
            )

            val incoming = HouseholdSyncBundle(
                sourceDeviceLabel = "Main Phone",
                homes = listOf(
                    SyncedHomeProfile(
                        networkKey = "wifi_phone_home",
                        homeName = "Main Residence",
                        ssidLabel = "HomeNet-5G",
                        subnetPrefix = "192.168.1",
                        gatewayIp = "192.168.1.1",
                        sortOrder = 0
                    )
                ),
                devices = listOf(
                    // Matches existingSwitchId via Tier-1 serial despite different IP/port and different home key
                    SyncedDeviceProfile(
                        name = "Front Porch Sconce",
                        roomName = "Entryway",
                        networkKey = "wifi_phone_home",
                        deviceType = DeviceType.SWITCH_PLUG.name,
                        ipAddress = "192.168.1.20",
                        port = 49153,
                        serialNumber = "221840K01010",
                        sortOrder = 0
                    ),
                    // Same IP/name as Kitchen Island but different real serial -> MUST insert separately
                    SyncedDeviceProfile(
                        name = "Kitchen Island",
                        roomName = "Dining Nook",
                        networkKey = "wifi_phone_home",
                        deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                        ipAddress = "192.168.1.50",
                        port = 49153,
                        serialNumber = "221840K88888",
                        sortOrder = 1
                    )
                )
            )

            val summary = repo.importHouseholdBundle(incoming, mode = HouseholdImportMode.MERGE)
            assertEquals(1, summary.homesUpdated)
            assertEquals(0, summary.homesAdded)
            assertEquals(1, summary.devicesUpdated)
            assertEquals(1, summary.devicesAdded)
            assertEquals(1, summary.devicesPreserved)
            assertEquals("wifi_lan_192_168_1", summary.activeNetworkKey)

            val mergedHomes = dao.getAllHomeNetworks().first()
            assertEquals(1, mergedHomes.size)
            assertEquals("Main Residence", mergedHomes.single().homeName)
            assertEquals("HomeNet-5G", mergedHomes.single().ssidLabel)

            val mergedDevices = dao.getAllDevices().first()
            assertEquals(3, mergedDevices.size)
            val updatedPorch = mergedDevices.first { it.id == existingSwitchId }
            assertEquals("Front Porch Sconce", updatedPorch.name)
            assertEquals("Entryway", updatedPorch.roomName)
            assertEquals(0, updatedPorch.sortOrder)
            // Live LAN state and rules cache must be preserved
            assertEquals("192.168.1.99", updatedPorch.ipAddress)
            assertEquals(49154, updatedPorch.port)
            assertTrue(updatedPorch.isOn)
            assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED.name, updatedPorch.hardwareRulesCapability)

            // Replace mode swaps cleanly
            val replaceSummary = repo.importHouseholdBundle(incoming, mode = HouseholdImportMode.REPLACE)
            assertEquals(HouseholdImportMode.REPLACE, replaceSummary.mode)
            assertEquals(2, dao.getAllDevices().first().size)
        } finally {
            repo.close()
        }
    }

    @Test
    fun lanServerAndClientSupportUdpDiscoveryEncryptedPushAndFiveAttemptLockout() = runBlocking {
        val server = HouseholdSyncLanServer(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            discoveryUdpPort = 0,
            sessionTtlMs = 30_000L,
            allowLoopbackForTests = true
        )
        val client = HouseholdSyncLanClient(
            localIpProvider = { "127.0.0.1" },
            allowLoopbackForTests = true
        )
        try {
            val receivedLatch = CountDownLatch(1)
            val receivedRef = AtomicReference<ReceivedHouseholdSync?>(null)
            val session = server.startSession(
                deviceLabel = "Living Room TV",
                isTv = true,
                bindAddress = InetAddress.getByName("127.0.0.1"),
                onPayloadReceived = {
                    receivedRef.set(it)
                    receivedLatch.countDown()
                },
                onSessionExpired = {},
                onSessionLockedOut = {}
            )
            assertNotNull(session)
            assertTrue("Expected ephemeral UDP discovery port > 0", session!!.discoveryUdpPort > 0)

            // 1. UDP peer discovery finds the TV without exposing the PIN
            val peers = client.discoverPeers(discoveryUdpPort = session.discoveryUdpPort, timeoutMs = 450L)
            assertTrue("Expected loopback TV peer to be discovered, found=$peers", peers.isNotEmpty())
            val peer = peers.first { it.port == session.port }
            assertEquals("127.0.0.1", peer.ipAddress)
            assertEquals("Living Room TV", peer.deviceName)
            assertTrue(peer.isTv)

            // 2. Non-POST HTTP verb rejected with 405
            Socket("127.0.0.1", session.port).use { sock ->
                sock.getOutputStream().write("GET /wemi-sync/v1/push HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
                sock.getOutputStream().flush()
                val statusLine = sock.getInputStream().bufferedReader().readLine().orEmpty()
                assertTrue("Expected 405 Method Not Allowed, got $statusLine", statusLine.contains("405"))
            }

            val bundle = HouseholdSyncBundle(
                sourceDeviceLabel = "Pixel Phone",
                homes = listOf(
                    SyncedHomeProfile("wifi_home_192_168_1", "My House", "HomeWiFi", "192.168.1", "192.168.1.1")
                ),
                devices = listOf(
                    SyncedDeviceProfile("Porch Light", "Outdoor", "wifi_home_192_168_1", ipAddress = "192.168.1.33", serialNumber = "221840K00001")
                )
            )

            // 3. Wrong PIN rejected with 401
            val badPin = if (session.pairingCode == "111111") "222222" else "111111"
            val wrongRes = client.pushHouseholdBundle("127.0.0.1", session.port, badPin, bundle)
            assertFalse(wrongRes.success)
            assertEquals(401, wrongRes.httpCode)

            // 4. Correct PIN succeeds with 200 OK and delivers decrypted bundle
            val okRes = client.pushHouseholdBundle("127.0.0.1", session.port, session.pairingCode, bundle)
            assertTrue("Expected 200 OK push, got $okRes", okRes.success)
            assertEquals(200, okRes.httpCode)
            assertTrue("Server callback should fire promptly", receivedLatch.await(3, TimeUnit.SECONDS))
            val received = receivedRef.get()
            assertNotNull(received)
            assertEquals("Pixel Phone", received!!.senderLabel)
            assertEquals("Porch Light", received.bundle.devices.single().name)
            assertEquals("Outdoor", received.bundle.devices.single().roomName)

            // 5. Brute-force lockout after 5 wrong PIN attempts
            val lockoutLatch = CountDownLatch(1)
            val session2 = server.startSession(
                deviceLabel = "Lockout TV",
                isTv = true,
                bindAddress = InetAddress.getByName("127.0.0.1"),
                onPayloadReceived = {},
                onSessionExpired = {},
                onSessionLockedOut = { lockoutLatch.countDown() }
            )!!
            val wrongPin2 = if (session2.pairingCode == "000000") "999999" else "000000"
            repeat(HouseholdSyncLanServer.MAX_FAILED_AUTH_ATTEMPTS - 1) {
                val r = client.pushHouseholdBundle("127.0.0.1", session2.port, wrongPin2, bundle)
                assertEquals(401, r.httpCode)
            }
            val fifth = client.pushHouseholdBundle("127.0.0.1", session2.port, wrongPin2, bundle)
            assertFalse(fifth.success)
            assertEquals(429, fifth.httpCode)
            assertTrue("Lockout callback must fire on 5th bad PIN", lockoutLatch.await(3, TimeUnit.SECONDS))
        } finally {
            server.close()
        }
    }
}
