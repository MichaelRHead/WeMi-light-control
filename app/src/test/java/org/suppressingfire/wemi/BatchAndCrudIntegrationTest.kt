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
    }
}
