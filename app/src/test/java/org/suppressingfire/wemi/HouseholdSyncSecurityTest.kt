package org.suppressingfire.wemi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.HouseholdSyncBundle
import org.suppressingfire.wemi.network.HouseholdSyncCodec
import org.suppressingfire.wemi.network.HouseholdSyncCrypto
import org.suppressingfire.wemi.network.HouseholdSyncLanClient
import org.suppressingfire.wemi.network.HouseholdSyncLanServer
import org.suppressingfire.wemi.network.HouseholdSyncPake
import org.suppressingfire.wemi.network.LanHttpClient
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ReceivedHouseholdSync
import org.suppressingfire.wemi.network.StrictJsonCodec
import org.suppressingfire.wemi.network.SyncedDeviceProfile
import org.suppressingfire.wemi.network.SyncedHomeProfile
import org.suppressingfire.wemi.ui.HouseholdSyncUiController

/**
 * Stage 4 Security & Privacy regression suite for Zero-Cloud Household Config Sync:
 * - SYNC-01: RFC 9382 SPAKE2 over RFC 3526 Group 14 (2048-bit safe prime), subgroup validation,
 *   single-use nonce replay rejection, and zero v1 downgrade on `/wemi-sync/v1/push`.
 * - SYNC-02: Serialized `authLock` lockout enforcement under concurrent wrong-PIN bursts.
 * - SYNC-03: GCM AAD binding of `sender`, `nonce`, `X`, `Y`, and `iv`, plus authenticated bundle label.
 * - SYNC-04: Background `pauseReceiveSessionForBackground()` stops listener while preserving `pendingImport`.
 * - SYNC-05: UDP peer discovery bounded to `<= 16` peers.
 * - SYNC-06: Hardened HTTP framing (`400` duplicate Content-Length / Transfer-Encoding, `431` long header, `413` oversized body).
 * - SYNC-07: `StrictJsonCodec` input-size & total-node caps against CPU/memory exhaustion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HouseholdSyncSecurityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NetworkMonitor.resetTestOverrides()
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        NetworkMonitor.resetTestOverrides()
        SwitchDatabase.closeCurrentInstance()
    }

    @Test
    fun spake2Group14ParametersAndSubgroupGuardsAreMathematicallySound() {
        assertEquals(2048, HouseholdSyncPake.P.bitLength())
        assertEquals(BigInteger.valueOf(7L), HouseholdSyncPake.P.mod(BigInteger.valueOf(8L)))
        assertEquals(BigInteger.ONE, HouseholdSyncPake.G.modPow(HouseholdSyncPake.Q, HouseholdSyncPake.P))
        assertTrue(HouseholdSyncPake.isValidSubgroupElement(HouseholdSyncPake.G))
        assertTrue(HouseholdSyncPake.isValidSubgroupElement(HouseholdSyncPake.M))
        assertTrue(HouseholdSyncPake.isValidSubgroupElement(HouseholdSyncPake.N))
        assertNotEquals(HouseholdSyncPake.M, HouseholdSyncPake.N)
        assertNotEquals(HouseholdSyncPake.G, HouseholdSyncPake.M)

        // Small-subgroup / out-of-range elements must be rejected
        assertFalse(HouseholdSyncPake.isValidSubgroupElement(BigInteger.ZERO))
        assertFalse(HouseholdSyncPake.isValidSubgroupElement(BigInteger.ONE))
        assertFalse(HouseholdSyncPake.isValidSubgroupElement(HouseholdSyncPake.P.subtract(BigInteger.ONE)))
        assertFalse(HouseholdSyncPake.isValidSubgroupElement(HouseholdSyncPake.P))
        assertFalse(HouseholdSyncPake.isValidSubgroupElement(BigInteger.valueOf(-1L)))
        // Verify a quadratic non-residue (order 2q, not q) is rejected
        val nonSubgroup = HouseholdSyncPake.P.subtract(HouseholdSyncPake.G) // -2 mod p has Legendre symbol -1
        assertFalse(HouseholdSyncPake.isValidSubgroupElement(nonSubgroup))
    }

    @Test
    fun spake2PushRejectsV1DowngradeReplayTamperedSenderAndInvalidSubgroupPoint() = runBlocking {
        val server = HouseholdSyncLanServer(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            discoveryUdpPort = -1,
            sessionTtlMs = 30_000L,
            allowLoopbackForTests = true
        )
        val received = AtomicReference<ReceivedHouseholdSync?>(null)
        val receivedLatch = CountDownLatch(1)
        val http = LanHttpClient(allowLoopbackForTests = true)
        try {
            val session = server.startSession(
                deviceLabel = "Bedroom TV",
                isTv = true,
                onPayloadReceived = {
                    received.set(it)
                    receivedLatch.countDown()
                },
                onSessionExpired = {},
                onSessionLockedOut = {}
            )
            assertNotNull(session)
            val s = session!!
            val helloUrl = "http://127.0.0.1:${s.port}${HouseholdSyncLanServer.HELLO_PATH}"
            val pushUrl = "http://127.0.0.1:${s.port}${HouseholdSyncLanServer.PUSH_PATH}"
            val bundle = sampleBundle("Real Phone")
            val json = HouseholdSyncCodec.encodeToJson(bundle)

            // 1. v1 PBKDF2 envelope must be rejected outright (no v1 downgrade)
            val v1Envelope = HouseholdSyncCrypto.encryptEnvelope(json, s.pairingCode, senderLabel = "Legacy")
            val v1Resp = http.execute("POST", pushUrl, mapOf("Content-Type" to "application/json"), v1Envelope)
            assertEquals("v1 downgrade must be rejected with 400", 400, v1Resp.code)

            // 2. Tampered sender on a valid SPAKE2 v2 envelope must fail GCM AAD authentication (401)
            val hello1 = http.execute("POST", helloUrl, mapOf("Content-Type" to "application/json"), "")
            assertEquals(200, hello1.code)
            val validEnv1 = HouseholdSyncPake.encryptPushV2(json, s.pairingCode, "Real Phone", hello1.body)
            val tamperedSenderEnv = validEnv1.replace("\"sender\":\"Real Phone\"", "\"sender\":\"Spoofed\"")
            val tamperResp = http.execute("POST", pushUrl, mapOf("Content-Type" to "application/json"), tamperedSenderEnv)
            assertEquals("Tampered sender must fail AAD auth with 401", 401, tamperResp.code)

            // 3. Replaying the same single-use nonce (even with original valid envelope!) must fail (401)
            val replayResp = http.execute("POST", pushUrl, mapOf("Content-Type" to "application/json"), validEnv1)
            assertEquals("Replayed single-use nonce must fail with 401", 401, replayResp.code)

            // 4. Small-subgroup X=1 injection must fail (401)
            val hello2 = http.execute("POST", helloUrl, mapOf("Content-Type" to "application/json"), "")
            assertEquals(200, hello2.code)
            val validEnv2 = HouseholdSyncPake.encryptPushV2(json, s.pairingCode, "Real Phone", hello2.body)
            val parsedObj = HouseholdSyncCodec.parseJsonObject(validEnv2)
            val badXEnv = buildString {
                append("{\"v\":2,\"nonce\":\"").append(parsedObj["nonce"]).append("\"")
                append(",\"x\":\"AQ==\",\"sender\":\"Attacker\"")
                append(",\"iv\":\"").append(parsedObj["iv"]).append("\"")
                append(",\"ct\":\"").append(parsedObj["ct"]).append("\"}")
            }
            val badXResp = http.execute("POST", pushUrl, mapOf("Content-Type" to "application/json"), badXEnv)
            assertEquals("Small-subgroup X element must fail with 401", 401, badXResp.code)

            // 5. Legitimate client push still succeeds before hitting 5 failures
            val client = HouseholdSyncLanClient(localIpProvider = { "127.0.0.1" }, allowLoopbackForTests = true)
            val ok = client.pushHouseholdBundle("127.0.0.1", s.port, s.pairingCode, bundle, senderLabel = "Header Label")
            assertTrue("Valid SPAKE2 v2 push must succeed: ${ok.statusMessage}", ok.success)
            assertTrue(receivedLatch.await(3, TimeUnit.SECONDS))
            // Authenticated bundle.sourceDeviceLabel takes precedence over outer envelope sender
            assertEquals("Real Phone", received.get()?.senderLabel)
        } finally {
            server.close()
        }
    }

    @Test
    fun concurrentWrongPinBurstLocksOutStrictlyWithoutOverrun() = runBlocking {
        val server = HouseholdSyncLanServer(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            discoveryUdpPort = -1,
            sessionTtlMs = 30_000L,
            allowLoopbackForTests = true
        )
        val lockouts = AtomicInteger(0)
        val lockoutLatch = CountDownLatch(1)
        try {
            val session = server.startSession(
                deviceLabel = "Lockout TV",
                isTv = true,
                onPayloadReceived = {},
                onSessionExpired = {},
                onSessionLockedOut = {
                    lockouts.incrementAndGet()
                    lockoutLatch.countDown()
                }
            )!!
            val wrongPin = if (session.pairingCode == "000000") "000001" else "000000"
            val bundle = sampleBundle("BruteForcer")
            val client = HouseholdSyncLanClient(localIpProvider = { "127.0.0.1" }, allowLoopbackForTests = true)

            // Wave 1 (4 concurrent = MAX_CONCURRENT_CONNECTIONS) advances to 4 failed attempts;
            // Wave 2 (4 concurrent) races directly across the 5th-attempt lockout boundary under authLock.
            val wave1 = (1..4).map {
                async(Dispatchers.IO) {
                    client.pushHouseholdBundle("127.0.0.1", session.port, wrongPin, bundle)
                }
            }.awaitAll()
            val wave2 = (1..4).map {
                async(Dispatchers.IO) {
                    client.pushHouseholdBundle("127.0.0.1", session.port, wrongPin, bundle)
                }
            }.awaitAll()
            val results = wave1 + wave2

            assertTrue("Expected session lockout callback after 5 failed attempts", lockoutLatch.await(5, TimeUnit.SECONDS))
            assertEquals("Session lockout callback must fire exactly once", 1, lockouts.get())
            assertTrue("All wrong-PIN pushes must fail", results.none { it.success })
            val auth401Count = results.count { it.httpCode == 401 }
            assertEquals("Exactly 4 HTTP 401 responses before the 5th attempt triggers lockout", 4, auth401Count)
            assertNull("Session must be closed after lockout", server.currentSession)
        } finally {
            server.close()
        }
    }

    @Test
    fun hardenedHttpFramingRejectsSmugglingOversizedHeadersAndOversizedBody() = runBlocking {
        val server = HouseholdSyncLanServer(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            discoveryUdpPort = -1,
            sessionTtlMs = 30_000L,
            allowLoopbackForTests = true
        )
        try {
            val session = server.startSession(
                deviceLabel = "Framing TV",
                isTv = true,
                onPayloadReceived = {},
                onSessionExpired = {},
                onSessionLockedOut = {}
            )!!

            fun sendRaw(rawHttp: String): String {
                Socket("127.0.0.1", session.port).use { sock ->
                    sock.soTimeout = 3_000
                    OutputStreamWriter(sock.getOutputStream(), Charsets.ISO_8859_1).apply {
                        write(rawHttp)
                        flush()
                    }
                    return sock.getInputStream().bufferedReader().readLine().orEmpty()
                }
            }

            // Duplicate Content-Length -> 400
            val dupCl = sendRaw(
                "POST /wemi-sync/v1/push HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 2\r\nContent-Length: 2\r\n\r\n{}"
            )
            assertTrue("Duplicate Content-Length must return 400, got $dupCl", dupCl.contains("400"))

            // Transfer-Encoding: chunked -> 400
            val teChunked = sendRaw(
                "POST /wemi-sync/v1/push HTTP/1.1\r\nHost: 127.0.0.1\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n"
            )
            assertTrue("Transfer-Encoding must return 400, got $teChunked", teChunked.contains("400"))

            // Oversized header line (> 4096 bytes) -> 431
            val hugeHeader = "X-Huge: " + "A".repeat(4_500)
            val longHdr = sendRaw(
                "POST /wemi-sync/v1/hello HTTP/1.1\r\nHost: 127.0.0.1\r\n$hugeHeader\r\n\r\n"
            )
            assertTrue("Oversized header line must return 431, got $longHdr", longHdr.contains("431"))

            // Oversized Content-Length (> 384 KiB) -> 413
            val hugeBody = sendRaw(
                "POST /wemi-sync/v1/push HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Length: 500000\r\n\r\n"
            )
            assertTrue("Oversized Content-Length must return 413, got $hugeBody", hugeBody.contains("413"))
        } finally {
            server.close()
        }
    }

    @Test
    fun strictJsonCodecRejectsExcessiveNodeCountAndInputSize() {
        val hugeInput = "{\"a\":\"" + "x".repeat(StrictJsonCodec.MAX_JSON_INPUT_CHARS) + "\"}"
        assertTrue(
            "Input exceeding MAX_JSON_INPUT_CHARS must be rejected",
            runCatching { StrictJsonCodec.parseJsonObject(hugeInput) }.exceptionOrNull() is IllegalArgumentException
        )

        // > 16,384 tiny numeric nodes inside a valid 200 KiB array must fail node bound cleanly
        val manyNodes = buildString {
            append("{\"arr\":[")
            repeat(StrictJsonCodec.MAX_TOTAL_NODES + 10) { idx ->
                if (idx > 0) append(',')
                append('1')
            }
            append("]}")
        }
        assertTrue(
            "JSON exceeding MAX_TOTAL_NODES must be rejected",
            runCatching { StrictJsonCodec.parseJsonObject(manyNodes) }.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun peerDiscoveryCapsPeerListAtSixteenEntries() = runBlocking {
        // Flood loopback responder with 25 distinct ports and confirm client caps at MAX_DISCOVERED_PEERS (16)
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { fakeResponder ->
            fakeResponder.soTimeout = 1_500
            val responderThread = Thread {
                val buf = ByteArray(256)
                val pkt = DatagramPacket(buf, buf.size)
                runCatching {
                    fakeResponder.receive(pkt)
                    for (i in 1..25) {
                        val reply = "WEMI-SYNC-PEER v1 port=${9000 + i} role=TV name=FloodTV$i".toByteArray(Charsets.UTF_8)
                        fakeResponder.send(DatagramPacket(reply, reply.size, pkt.address, pkt.port))
                    }
                }
            }.apply { start() }

            val client = HouseholdSyncLanClient(localIpProvider = { "127.0.0.1" }, allowLoopbackForTests = true)
            val peers = client.discoverPeers(discoveryUdpPort = fakeResponder.localPort, timeoutMs = 450L)
            responderThread.join(1_000)

            assertEquals(
                "Peer discovery must cap list at ${HouseholdSyncLanClient.MAX_DISCOVERED_PEERS}",
                HouseholdSyncLanClient.MAX_DISCOVERED_PEERS,
                peers.size
            )
        }
    }

    @Test
    fun pauseReceiveSessionForBackgroundStopsListenerAndPreservesPendingImport() = runBlocking {
        val dao = SwitchDatabase.getDatabase(context).switchDao()
        val repo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val controller = HouseholdSyncUiController(
            scope = scope,
            appContext = context,
            repository = repo,
            onActiveNetworkKeyUpdated = {},
            onAfterImportApplied = {},
            onShowTransientStatus = {}
        )
        try {
            controller.openReceiveDialog(isTv = false, preferredListenPort = 0, discoveryUdpPort = -1)
            val deadline = System.currentTimeMillis() + 3_000L
            while (controller.uiState.value.activeReceiveSession == null && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(25)
            }
            assertNotNull("Receive session should start", controller.uiState.value.activeReceiveSession)

            controller.pauseReceiveSessionForBackground()
            assertNull("Active session must clear when backgrounded", controller.uiState.value.activeReceiveSession)
            assertTrue(
                "Status should prompt user to resume with New Code",
                controller.uiState.value.receiveStatusMessage.orEmpty().contains("paused in background", ignoreCase = true)
            )

            // When pendingImport is already staged, background pause must NOT clear it
            controller.stageJsonImport(HouseholdSyncCodec.encodeToJson(sampleBundle("Staged Phone")), "Test")
            assertNotNull(controller.uiState.value.pendingImport)
            controller.pauseReceiveSessionForBackground()
            assertNotNull("Staged pendingImport must remain intact on background pause", controller.uiState.value.pendingImport)
        } finally {
            controller.close()
            scope.cancel()
            repo.close()
        }
    }

    private fun sampleBundle(sender: String) = HouseholdSyncBundle(
        sourceDeviceLabel = sender,
        homes = listOf(
            SyncedHomeProfile(
                networkKey = "wifi_sec_home",
                homeName = "Sec Home",
                ssidLabel = "SecWifi",
                subnetPrefix = "192.168.1",
                gatewayIp = "192.168.1.1",
                sortOrder = 0
            )
        ),
        devices = listOf(
            SyncedDeviceProfile(
                networkKey = "wifi_sec_home",
                name = "Porch Light",
                roomName = "Outdoor",
                deviceType = "SWITCH_PLUG",
                ipAddress = "192.168.1.40",
                port = 49153,
                serialNumber = "221840K01010",
                udn = "uuid:Lightswitch-1_0-221840K01010",
                sortOrder = 0
            )
        )
    )
}
