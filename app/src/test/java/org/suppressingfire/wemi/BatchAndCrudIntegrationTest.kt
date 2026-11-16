package org.suppressingfire.wemi

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.data.RuleType
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.NetworkClient

/**
 * Tier 2 Repository Concurrency, Idempotent Batch Fan-Out (`Semaphore(3)`), and Room CRUD
 * (`moveDeviceOrder`, `deleteDevice`, `deleteRule`) Integration Tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BatchAndCrudIntegrationTest {

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
    fun `verify concurrent toggleRoomPower and idempotent setDevicePowerExplicit across 4 loopback RTOS switches under Semaphore 3`() = runBlocking {
        FakeUpnpServer(initialFriendlyName = "Den Light 1", serialNumber = "WLS0403B1", initialRelayOn = true).use { s1 ->
            FakeUpnpServer(initialFriendlyName = "Den Light 2", serialNumber = "WLS0403B2", initialRelayOn = true).use { s2 ->
                FakeUpnpServer(initialFriendlyName = "Den Light 3", serialNumber = "WLS0403B3", initialRelayOn = true).use { s3 ->
                    FakeUpnpServer(initialFriendlyName = "Den Light 4", serialNumber = "WLS0403B4", initialRelayOn = false).use { s4 ->
                        val servers = listOf(s1, s2, s3, s4)
                        val client = NetworkClient(extraCandidatePorts = servers.map { it.port })
                        val repo = SwitchRepository(
                            dao = database.switchDao(),
                            networkClient = client,
                            initialDemoModeOverride = false
                        )
                        val homeKey = repo.ensureSeeded()

                        servers.forEachIndexed { idx, srv ->
                            database.switchDao().insertDevice(
                                DeviceEntity(
                                    name = "Den Light ${idx + 1}",
                                    roomName = "Living Room",
                                    networkKey = homeKey,
                                    ipAddress = "127.0.0.1",
                                    port = srv.port,
                                    isOn = idx < 3,
                                    isReachable = true,
                                    serialNumber = "WLS0403B${idx + 1}",
                                    sortOrder = idx
                                )
                            )
                        }

                        // 1. Concurrent toggleRoomPower(targetOn = false) turns OFF all 4 switches in parallel via Semaphore(3)
                        val countOff = repo.toggleRoomPower("Living Room", targetOn = false, networkKey = homeKey)
                        assertEquals(4, countOff)
                        servers.forEach { srv -> assertFalse(srv.relayStateOn.get()) }

                        // 2. Idempotent setDevicePowerExplicit(targetOn = false) even when stale snapshot says isOn = true
                        //    must NEVER invert an already-OFF switch back ON
                        val staleEntities = database.switchDao().getAllDevices().first().map { it.copy(isOn = true) }
                        coroutineScope {
                            staleEntities.map { stale ->
                                async { repo.setDevicePowerExplicit(stale, targetOn = false) }
                            }.awaitAll()
                        }
                        servers.forEach { srv -> assertFalse(srv.relayStateOn.get()) }
                    }
                }
            }
        }
    }

    @Test
    fun `verify moveDeviceOrder, deleteDevice, and deleteRule Room CRUD persistence`() = runBlocking {
        val repo = SwitchRepository(
            dao = database.switchDao(),
            initialDemoModeOverride = false
        )
        val homeKey = repo.ensureSeeded()

        val idA = database.switchDao().insertDevice(
            DeviceEntity(
                name = "Switch A",
                roomName = "Office",
                networkKey = homeKey,
                ipAddress = "127.0.0.1",
                port = 49153,
                serialNumber = "CRUDSN01",
                sortOrder = 0
            )
        ).toInt()
        val idB = database.switchDao().insertDevice(
            DeviceEntity(
                name = "Switch B",
                roomName = "Office",
                networkKey = homeKey,
                ipAddress = "127.0.0.1",
                port = 49154,
                serialNumber = "CRUDSN02",
                sortOrder = 1
            )
        ).toInt()

        // Swap Switch B up above Switch A
        val entityB = database.switchDao().getDeviceById(idB)!!
        repo.moveDeviceOrder(entityB, moveUp = true)

        val orderedAfterUp = database.switchDao().getAllDevices().first()
        assertEquals(listOf("Switch B", "Switch A"), orderedAfterUp.map { it.name })

        // Swap Switch B back down below Switch A
        val movedB = database.switchDao().getDeviceById(idB)!!
        repo.moveDeviceOrder(movedB, moveUp = false)
        val orderedAfterDown = database.switchDao().getAllDevices().first()
        assertEquals(listOf("Switch A", "Switch B"), orderedAfterDown.map { it.name })

        // Add and delete a RuleEntity
        val ruleId = database.switchDao().insertRule(
            RuleEntity(
                name = "Office Night Off",
                ruleType = RuleType.SCHEDULE.name,
                targetDeviceId = idA,
                targetDeviceName = "Switch A",
                triggerSummary = "Daily at 11:00 PM",
                actionSummary = "Turn OFF Switch A",
                targetStateOn = false,
                isEnabled = true
            )
        ).toInt()
        assertEquals(1, database.switchDao().getRuleCount())
        repo.deleteRule(ruleId)
        assertEquals(0, database.switchDao().getRuleCount())

        // Delete Switch A
        repo.deleteDevice(idA)
        assertNull(database.switchDao().getDeviceById(idA))
        assertEquals(listOf("Switch B"), database.switchDao().getAllDevices().first().map { it.name })
        repo.close()
    }

    @Test
    fun `verify parallel pollActiveHomeStates and deduplicated reconnectAndRediscoverHome across 4 loopback switches`() = runBlocking {
        FakeUpnpServer(initialFriendlyName = "Patio 1", serialNumber = "POLLSN01", initialRelayOn = false).use { s1 ->
            FakeUpnpServer(initialFriendlyName = "Patio 2", serialNumber = "POLLSN02", initialRelayOn = true).use { s2 ->
                FakeUpnpServer(initialFriendlyName = "Patio 3", serialNumber = "POLLSN03", initialRelayOn = false).use { s3 ->
                    FakeUpnpServer(initialFriendlyName = "Patio 4", serialNumber = "POLLSN04", initialRelayOn = true).use { s4 ->
                        val servers = listOf(s1, s2, s3, s4)
                        val client = NetworkClient(extraCandidatePorts = servers.map { it.port })
                        val repo = SwitchRepository(
                            dao = database.switchDao(),
                            networkClient = client,
                            initialDemoModeOverride = false
                        )
                        try {
                            val homeKey = repo.ensureSeeded()
                            servers.forEachIndexed { idx, srv ->
                                database.switchDao().insertDevice(
                                    DeviceEntity(
                                        name = "Patio ${idx + 1}",
                                        roomName = "Porch & Entryway",
                                        networkKey = homeKey,
                                        ipAddress = "127.0.0.1",
                                        port = srv.port,
                                        isOn = false,
                                        isReachable = true,
                                        serialNumber = "POLLSN0${idx + 1}",
                                        sortOrder = idx
                                    )
                                )
                            }

                            // 1. Concurrent pollActiveHomeStates updates all 4 switches in parallel
                            s1.relayStateOn.set(true)
                            s3.relayStateOn.set(true)
                            repo.pollActiveHomeStates(homeKey)
                            val afterPoll = database.switchDao().getAllDevices().first()
                            assertTrue(afterPoll.all { it.isOn && it.isReachable })

                            // 2. Deduplicated reconnectAndRediscoverHome probes remembered devices without redundant pre-SSDP pass
                            servers.forEach { it.receivedSoapActions.clear() }
                            val summary = repo.reconnectAndRediscoverHome(homeKey)
                            assertEquals(4, summary.reconnectedCount)
                            assertEquals(4, summary.totalRememberedCount)
                            servers.forEach { srv ->
                                val getBinaryCalls = srv.receivedSoapActions.count {
                                    it.contains("GetBinaryState", ignoreCase = true)
                                }
                                // 1 call inside Stage B known-device descriptor enrichment + 1 call in parallel reconnect pass (not 3!)
                                assertTrue("Expected <= 2 GetBinaryState calls, got $getBinaryCalls", getBinaryCalls <= 2)
                            }
                        } finally {
                            repo.close()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `verify SwitchViewModel establishes immediate Real LAN GENA subscription on adoptSsdpDevice and resets status banner timer`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val store = androidx.lifecycle.ViewModelStore()
        FakeUpnpServer(
            initialFriendlyName = "Office Desk Plug",
            serialNumber = "GENAADOPTSN01",
            enableSsdpResponder = true,
            registerAsCandidatePort = true
        ).use { server ->
            try {
                val vm = androidx.lifecycle.ViewModelProvider(
                    store,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
                )[org.suppressingfire.wemi.ui.SwitchViewModel::class.java]

                vm.toggleDemoShowroomMode(false)
                val deadlineDemo = System.currentTimeMillis() + 2_500L
                while (vm.isDemoShowroomMode.value && System.currentTimeMillis() < deadlineDemo) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(20)
                }
                assertFalse(vm.isDemoShowroomMode.value)

                vm.adoptSsdpDevice(
                    org.suppressingfire.wemi.data.SsdpDiscoveredDevice(
                        name = "Office Desk Plug",
                        roomName = "Office",
                        ipAddress = "127.0.0.1",
                        port = server.port,
                        deviceType = org.suppressingfire.wemi.data.DeviceType.SWITCH_PLUG,
                        modelName = "WSP080",
                        serialNumber = "GENAADOPTSN01",
                        firmwareVersion = "WeMo_WW_2.00.12000.PVT",
                        usn = "uuid:Socket-1_0-GENAADOPTSN01::urn:Belkin:service:basicevent:1",
                        searchTarget = "urn:Belkin:service:basicevent:1",
                        locationUrl = "http://127.0.0.1:${server.port}/setup.xml",
                        responseTimeMs = 6L
                    )
                )

                val deadlineGena = System.currentTimeMillis() + 2_500L
                while (server.lastGenaCallbackPort.get() <= 0 && System.currentTimeMillis() < deadlineGena) {
                    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                    Thread.sleep(20)
                }
                assertTrue(
                    "Adopting a switch in Real LAN Mode must immediately establish a GENA SUBSCRIBE callback",
                    server.lastGenaCallbackPort.get() > 0
                )

                vm.showTransientStatus("Repeated Banner")
                vm.showTransientStatus("Repeated Banner")
                assertEquals("Repeated Banner", vm.statusBannerMessage.value)
                vm.dismissBanner()
                assertNull(vm.statusBannerMessage.value)
            } finally {
                store.clear()
            }
        }
    }
}

