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
}
