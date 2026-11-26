package org.suppressingfire.wemi

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.DeviceDescriptorFetcher
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.LanHttpClient
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.SetupApProber
import org.suppressingfire.wemi.ui.HomeAndRulesUiController
import org.suppressingfire.wemi.ui.ProvisionApUiController
import org.suppressingfire.wemi.ui.SwitchViewModel

/**
 * Hermetic regression suite verifying coroutine cancellation propagation, concurrent repository
 * adoption/metadata merging, GENA placeholder SID eviction & brightness-only logs, provisioning
 * identity preservation, and ViewModel/UI controller re-entrancy guards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LifecycleAndConcurrencyTest {

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
    fun `verify cold-start toggleDimmerNightMode awaits Demo Showroom seeding and persists state`() = runBlocking {
        val repo = SwitchRepository(
            dao = database.switchDao(),
            networkClient = NetworkClient(),
            initialDemoModeOverride = true
        )
        // Intentionally do NOT call repo.ensureSeeded() first
        val firstDemoDimmer = SwitchRepository.defaultClassicDevices()
            .first { it.parsedType == DeviceType.LIGHTBULB_DIMMER }
            .copy(id = 1)

        val result = repo.toggleDimmerNightMode(firstDemoDimmer)
        assertTrue(result.nightModeEnabled != firstDemoDimmer.nightModeEnabled)
        assertTrue(database.switchDao().getDeviceCount() >= 14)
    }

    @Test
    fun `verify saveDevice merges live Room metadata on edit and persists healed ChangeFriendlyName port`() = runBlocking {
        val tempSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val closedPort = tempSocket.localPort
        tempSocket.close()

        FakeUpnpServer(
            initialFriendlyName = "Stairway Switch",
            serialNumber = "WLS0403MERGE99",
            udn = "uuid:Lightswitch-3_0-WLS0403MERGE99",
            macAddress = "EC1A59EEFF00",
            firmwareVersion = "WeMo_WW_2.00.12345.PVT",
            registerAsCandidatePort = true
        ).use { server ->
            val repo = SwitchRepository(dao = database.switchDao(), networkClient = NetworkClient(), initialDemoModeOverride = false)
            repo.ensureSeeded()

            val insertedId = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Stairway Switch", roomName = "Hallway & Landing", ipAddress = "127.0.0.1",
                    port = closedPort, isOn = true, isReachable = true,
                    serialNumber = "WLS0403MERGE99", udn = "uuid:Lightswitch-3_0-WLS0403MERGE99",
                    macAddress = "EC1A59EEFF00", firmwareVersion = "WeMo_WW_2.00.12345.PVT",
                    nightModeEnabled = true, sortOrder = 7
                )
            ).toInt()

            // Stale UI edit snapshot missing freshly hydrated metadata and carrying old sortOrder/nightMode
            val staleEdit = DeviceEntity(
                id = insertedId, name = "Upper Stairway Switch", roomName = "Hallway & Landing",
                ipAddress = "127.0.0.1", port = closedPort, isOn = true, isReachable = true,
                serialNumber = "", udn = "", macAddress = "", firmwareVersion = "",
                nightModeEnabled = false, sortOrder = 0
            )

            val saved = repo.saveDevice(staleEdit, syncFriendlyNameToHardware = true)
            assertEquals("Upper Stairway Switch", saved.name)
            assertEquals("Upper Stairway Switch", server.friendlyName.get())
            assertEquals("Healed ChangeFriendlyName port must persist", server.port, saved.port)
            assertEquals("WLS0403MERGE99", saved.serialNumber)
            assertEquals("EC1A59EEFF00", saved.macAddress)
            assertEquals("WeMo_WW_2.00.12345.PVT", saved.firmwareVersion)
            assertEquals("uuid:Lightswitch-3_0-WLS0403MERGE99", saved.udn)
            assertTrue(saved.nightModeEnabled)
            assertEquals(7, saved.sortOrder)

            val persisted = database.switchDao().getDeviceById(insertedId)!!
            assertEquals(server.port, persisted.port)
            assertEquals("EC1A59EEFF00", persisted.macAddress)
            assertEquals(7, persisted.sortOrder)
        }
    }

    @Test
    fun `verify withinRoomOnly moveDeviceOrder trims room names and concurrent adoptDiscoveredDevices counts atomically`() = runBlocking {
        val repo = SwitchRepository(dao = database.switchDao(), networkClient = NetworkClient(), initialDemoModeOverride = false)
        repo.ensureSeeded()

        val k1 = database.switchDao().insertDevice(
            DeviceEntity(name = "Kitchen Sink", roomName = " Kitchen ", ipAddress = "192.168.1.41", serialNumber = "SN-K1", sortOrder = 0)
        ).toInt()
        val o1 = database.switchDao().insertDevice(
            DeviceEntity(name = "Office Lamp", roomName = "Office", ipAddress = "192.168.1.42", serialNumber = "SN-O1", sortOrder = 1)
        ).toInt()
        val k2 = database.switchDao().insertDevice(
            DeviceEntity(name = "Kitchen Island", roomName = "Kitchen", ipAddress = "192.168.1.43", serialNumber = "SN-K2", sortOrder = 2)
        ).toInt()

        repo.moveDeviceOrder(database.switchDao().getDeviceById(k2)!!, moveUp = true, withinRoomOnly = true)
        assertEquals(listOf(k2, o1, k1), database.switchDao().getAllDevices().first().map { it.id })

        val discovered = listOf(
            SsdpDiscoveredDevice(
                name = "Porch Lantern", roomName = "Outdoor", ipAddress = "192.168.1.60", port = 49153,
                deviceType = DeviceType.SWITCH_PLUG, modelName = "WLS0403", serialNumber = "WLS0403CONC1",
                firmwareVersion = "WeMo_WW_2.00.12000.PVT", usn = "uuid:Lightswitch-3_0-WLS0403CONC1::urn:Belkin:service:basicevent:1",
                searchTarget = "urn:Belkin:service:basicevent:1", locationUrl = "http://192.168.1.60:49153/setup.xml", responseTimeMs = 12L
            ),
            SsdpDiscoveredDevice(
                name = "Garage Overhead", roomName = "Garage", ipAddress = "192.168.1.61", port = 49153,
                deviceType = DeviceType.SWITCH_PLUG, modelName = "WLS0403", serialNumber = "WLS0403CONC2",
                firmwareVersion = "WeMo_WW_2.00.12000.PVT", usn = "uuid:Lightswitch-3_0-WLS0403CONC2::urn:Belkin:service:basicevent:1",
                searchTarget = "urn:Belkin:service:basicevent:1", locationUrl = "http://192.168.1.61:49153/setup.xml", responseTimeMs = 14L
            )
        )
        val addedCounts = coroutineScope {
            (1..6).map { async(Dispatchers.Default) { repo.adoptDiscoveredDevices(discovered, activeNetworkKey = DEFAULT_MAIN_HOME_KEY) } }.awaitAll()
        }
        assertEquals("Concurrent adoptions must count each new switch exactly once", 2, addedCounts.sum())
        assertEquals(5, database.switchDao().getDeviceCount())
    }

    @Test
    fun `verify GenaSubscriptionManager strips query path, formats brightness-only log, and evicts stale placeholder SID`() = runBlocking {
        val lastNotification = AtomicReference<GenaEventNotification?>(null)
        val manager = GenaSubscriptionManager(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            onNotification = { lastNotification.set(it) }
        )

        FakeUpnpServer(
            initialFriendlyName = "Bedroom Dimmer",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060REAL77"
        ).use { server ->
            try {
                // 1. Subscribe under placeholder serial -> tracked as dev_77
                val placeholderDevice = DeviceEntity(
                    id = 77, name = "Bedroom Dimmer", roomName = "Bedroom",
                    deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "127.0.0.1", port = server.port,
                    serialNumber = LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL
                )
                assertNotNull(manager.subscribeOrRenew(placeholderDevice))
                assertEquals(setOf("dev_77"), manager.activeSubscriptions.keys)
                assertEquals(1, manager.activeSidCount)

                // 2. Once enriched with real serial, subscribing evicts stale dev_77 subscription + SID
                val realSub = manager.subscribeOrRenew(placeholderDevice.copy(serialNumber = "WDS060REAL77"))
                assertNotNull(realSub)
                assertEquals(setOf("WDS060REAL77"), manager.activeSubscriptions.keys)
                assertEquals(1, manager.activeSidCount)

                // 3. Brightness-only NOTIFY with trailing slash + query string must not log "OFF"
                val xml = """<?xml version="1.0"?><e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"><e:property><Brightness>65</Brightness></e:property></e:propertyset>"""
                val bytes = xml.toByteArray(Charsets.UTF_8)
                Socket("127.0.0.1", manager.activeListenPort).use { sock ->
                    val writer = OutputStreamWriter(sock.getOutputStream(), Charsets.UTF_8)
                    writer.write(
                        "NOTIFY /gena/WDS060REAL77/?seq=4 HTTP/1.1\r\n" +
                            "HOST: 127.0.0.1:${manager.activeListenPort}\r\n" +
                            "SID: ${realSub!!.sid}\r\n" +
                            "CONTENT-LENGTH: ${bytes.size}\r\n\r\n$xml"
                    )
                    writer.flush()
                    sock.getInputStream().read()
                }
                withTimeout(1500L) { while (lastNotification.get() == null) delay(20L) }
                val event = lastNotification.get()!!
                assertEquals("WDS060REAL77", event.deviceKey)
                assertNull(event.binaryStateOn)
                assertEquals(65, event.brightness)
                assertEquals("Push Brightness: 65%", event.logEntry.statusText)
            } finally {
                manager.close()
            }
        }
    }

    @Test
    fun `verify re-provisioning preserves real hardware identity when Setup AP returns UNKNOWN serial`() = runBlocking {
        FakeUpnpServer(initialFriendlyName = "Porch Light", serialNumber = "UNKNOWN", udn = "", macAddress = "").use { server ->
            val repo = SwitchRepository(dao = database.switchDao(), networkClient = NetworkClient(), initialDemoModeOverride = false)
            repo.ensureSeeded()

            val existingId = database.switchDao().insertDevice(
                DeviceEntity(
                    name = "Porch Light", roomName = "Porch", ipAddress = "192.168.1.90", port = 49153,
                    serialNumber = "WLS0403REALPORCH", macAddress = "EC1A59112233",
                    udn = "uuid:Lightswitch-3_0-WLS0403REALPORCH",
                    firmwareVersion = "WeMo_WW_2.00.11900.PVT", hardwareModel = "WLS0403"
                )
            ).toInt()

            val result = repo.provisionSetupApDevice(
                apHost = "127.0.0.1", apPort = server.port, ssid = "HomeWiFi-2.4G",
                plainPassword = "Password123!", customDeviceName = "Porch Light",
                activeNetworkKey = DEFAULT_MAIN_HOME_KEY, maxStatusPolls = 4, statusPollDelayMs = 20L
            )
            assertTrue(result.success)

            val updated = database.switchDao().getDeviceById(existingId)!!
            assertEquals("WLS0403REALPORCH", updated.serialNumber)
            assertEquals("EC1A59112233", updated.macAddress)
            assertEquals("uuid:Lightswitch-3_0-WLS0403REALPORCH", updated.udn)
            assertEquals("HomeWiFi-2.4G", updated.wifiSsid)
        }
    }

    @Test
    fun `verify suspend network clients propagate CancellationException instead of swallowing it`() = runBlocking {
        val cancellingSocketFactory = object : SocketFactory() {
            override fun createSocket(): Socket = throw CancellationException("Simulated cancellation")
            override fun createSocket(host: String?, port: Int): Socket = throw CancellationException("Simulated cancellation")
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                throw CancellationException("Simulated cancellation")
            override fun createSocket(host: InetAddress?, port: Int): Socket = throw CancellationException("Simulated cancellation")
            override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                throw CancellationException("Simulated cancellation")
        }
        val cancellingHttp = LanHttpClient(socketFactory = cancellingSocketFactory, connectTimeoutMs = 200, readTimeoutMs = 200)
        val fetcher = DeviceDescriptorFetcher(
            httpClient = cancellingHttp,
            probeWemoPort = { _, _, _ -> 49153 },
            getBinaryState = { error("Unused") }
        )
        val prober = SetupApProber(wifiSocketFactory = cancellingSocketFactory, httpClient = cancellingHttp)

        suspend fun assertPropagatesCancellation(label: String, block: suspend () -> Unit) {
            try {
                block()
                fail("$label swallowed CancellationException")
            } catch (_: CancellationException) {
                // Expected structured concurrency cancellation propagation
            }
        }

        assertPropagatesCancellation("getMetaInfo") { fetcher.getMetaInfo("127.0.0.1", 49153) }
        assertPropagatesCancellation("getSignalStrength") { fetcher.getSignalStrength("127.0.0.1", 49153) }
        assertPropagatesCancellation("fetchLiveDeviceDescriptor") {
            fetcher.fetchLiveDeviceDescriptor(
                locationUrl = "http://127.0.0.1:49153/setup.xml",
                usnHeader = "uuid:Lightswitch-3_0-SN1::urn:Belkin:service:basicevent:1",
                stHeader = "urn:Belkin:service:basicevent:1",
                responseTimeMs = 10L,
                isLiveUdp = false
            )
        }
        assertPropagatesCancellation("probeSetupDevice") { prober.probeSetupDevice("127.0.0.1", 49152) }
        assertPropagatesCancellation("fetchAvailableApList") { prober.fetchAvailableApList("127.0.0.1", 49152) }
    }

    @Test
    fun `verify ViewModel evicts hardware rules snapshot on delete and controllers serialize scan, provision, and reconnect`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        val extras = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, app)
        }
        val viewModel = ViewModelProvider.create(store, SwitchViewModel.Factory, extras)[SwitchViewModel::class.java]
        try {
            suspend fun awaitMainCondition(predicate: () -> Boolean) {
                withTimeout(4000L) {
                    while (!predicate()) {
                        Shadows.shadowOf(Looper.getMainLooper()).idle()
                        delay(25L)
                    }
                }
            }
            // 1. Delete a switch with an inspected on-device rules snapshot and confirm cache entry is evicted
            awaitMainCondition { viewModel.devices.value.any { it.id > 0 } }
            val target = viewModel.devices.value.first { it.id > 0 && it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR }
            viewModel.inspectDeviceHardwareRules(target)
            awaitMainCondition { target.id in viewModel.hardwareRulesByDeviceId.value }
            viewModel.deleteDevice(target)
            awaitMainCondition { target.id !in viewModel.hardwareRulesByDeviceId.value }
            assertFalse(target.id in viewModel.hardwareRulesByDeviceId.value)

            // 2. ProvisionApUiController cancels superseded scan when provisioning begins and deduplicates concurrent triggers
            val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val repo = SwitchRepository(dao = database.switchDao(), networkClient = NetworkClient(), initialDemoModeOverride = true)
            repo.ensureSeeded()
            val monitor = NetworkMonitor(
                app,
                fallbackIpResolver = { "127.0.0.1" },
                interfaceIpResolver = { "127.0.0.1" to "lo" }
            )
            val provisionCalls = AtomicInteger(0)
            val provisionController = ProvisionApUiController(
                scope = controllerScope, repository = repo, networkMonitor = monitor,
                activeNetworkKeyProvider = { DEFAULT_MAIN_HOME_KEY },
                onShowTransientStatus = {
                    if (it.contains("Demo Showroom", ignoreCase = true) || it.contains("Provisioned", ignoreCase = true)) {
                        provisionCalls.incrementAndGet()
                    }
                }
            )
            val lanRepo = SwitchRepository(dao = database.switchDao(), networkClient = NetworkClient(), initialDemoModeOverride = false)
            lanRepo.ensureSeeded()
            lanRepo.saveOrUpdateHomeNetwork("home_alpha", "Alpha Home", "Alpha-WiFi", "127.0.0")
            lanRepo.saveOrUpdateHomeNetwork("home_beta", "Beta Home", "Beta-WiFi", "127.0.0")
            val homeController = HomeAndRulesUiController(
                scope = controllerScope, repository = lanRepo,
                onClearRoomFilterAndSelection = {}, onShowTransientStatus = {}
            )
            try {
                ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { silentServer ->
                    provisionController.scanSetupApNetworks(apHost = "127.0.0.1")
                    provisionController.provisionSetupAp(apHost = "10.22.22.1", ssid = "WeMi-Home-2.4G", password = "DemoPassword123!")
                    provisionController.provisionSetupAp(apHost = "10.22.22.1", ssid = "WeMi-Home-2.4G", password = "DemoPassword123!")
                    assertFalse("In-flight scan flag must clear when provisioning starts", provisionController.isScanningSetupAp.value)
                    withTimeout(4000L) { while (provisionController.isProvisioningAp.value) delay(25L) }
                    assertEquals("Duplicate concurrent provision must execute only once", 1, provisionCalls.get())
                    assertTrue(silentServer.localPort > 0)
                }

                // Rapid back-to-back home switches cancel superseded job and settle cleanly on latest home
                homeController.selectHomeNetwork("home_alpha") {}
                delay(50L)
                homeController.selectHomeNetwork("home_beta") {}
                withTimeout(8000L) { while (homeController.isReconnectingHome.value) delay(25L) }
                assertEquals("home_beta", homeController.activeNetworkKey.value)
                assertFalse(homeController.isReconnectingHome.value)
            } finally {
                controllerScope.cancel()
                lanRepo.close()
                repo.close()
            }
        } finally {
            store.clear()
        }
    }
}
