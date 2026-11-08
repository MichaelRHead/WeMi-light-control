package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.WeMoDatabase
import com.example.data.WeMoDeviceCategory
import com.example.data.WeMoDeviceEntity
import com.example.data.WeMoRepository
import com.example.data.WeMoRuleScheduler
import com.example.network.GenaEventNotification
import com.example.network.WeMoGenaSubscriptionManager
import com.example.network.WeMoNetworkClient
import com.example.network.WeMoProvisionerClient
import kotlinx.coroutines.delay
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeMoGenaProvisioningAndRulesTest {

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
    fun `verify Phase 3 UPnP GENA SUBSCRIBE, HTTP 412 recovery, and embedded NOTIFY propertyset callback`() = runBlocking {
        val receivedNotification = AtomicReference<GenaEventNotification?>(null)
        val genaManager = WeMoGenaSubscriptionManager(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            onNotification = { receivedNotification.set(it) }
        )

        FakeWeMoUpnpServer(
            initialFriendlyName = "Kitchen 3-Way Switch",
            serialNumber = "WLS0403KITCHEN"
        ).use { server ->
            try {
                val device = WeMoDeviceEntity(
                    id = 42,
                    name = "Kitchen 3-Way Switch",
                    roomName = "Kitchen",
                    ipAddress = "127.0.0.1",
                    port = server.port,
                    serialNumber = "WLS0403KITCHEN"
                )

                // 1. Initial SUBSCRIBE
                val sub1 = genaManager.subscribeOrRenew(device, timeoutSeconds = 300)
                assertNotNull(sub1)
                assertEquals("uuid:sub-wls0403-101", sub1?.sid)

                // 2. Simulate HTTP 412 on renewal (e.g. switch rebooted and forgot SID) -> must recover with fresh SID
                server.simulate412OnNextGenaRenew.set(true)
                val sub2 = genaManager.subscribeOrRenew(device, timeoutSeconds = 300)
                assertNotNull(sub2)
                assertEquals("uuid:sub-wls0403-102", sub2?.sid)

                // 3. Push a physical wall-switch flip via HTTP NOTIFY to the embedded GENA callback server
                val callbackPort = genaManager.activeListenPort
                assertTrue(callbackPort > 0)
                val respLine = server.pushGenaNotify(
                    callbackPort = callbackPort,
                    deviceKey = "WLS0403KITCHEN",
                    binaryStatePayload = "1|78",
                    sid = "uuid:sub-wls0403-102"
                )
                assertTrue(respLine.contains("200 OK"))

                delay(100)
                val event = receivedNotification.get()
                assertNotNull(event)
                assertEquals("WLS0403KITCHEN", event?.deviceKey)
                assertEquals(true, event?.binaryStateOn)
                assertEquals(78, event?.brightness)
            } finally {
                genaManager.stopServer()
            }
        }
    }

    @Test
    fun `verify Phase 5 WeMoRuleScheduler schedule time parsing and Auto-Off countdown timer`() = runBlocking {
        assertEquals(Pair(19, 0), WeMoRuleScheduler.parseScheduleTime24h("Daily at 7:00 PM"))
        assertEquals(Pair(17, 15), WeMoRuleScheduler.parseScheduleTime24h("Daily at Sunset (5:15 PM)"))
        assertEquals(Pair(22, 0), WeMoRuleScheduler.parseScheduleTime24h("Every night at 10:00 PM"))
        assertEquals(Pair(6, 30), WeMoRuleScheduler.parseScheduleTime24h("Daily at Sunrise (6:30 AM)"))
        assertEquals(30, WeMoRuleScheduler.parseMinutesFromSummary("After 30 minutes ON"))

        val expiredDeviceId = AtomicInteger(-1)
        val scheduler = WeMoRuleScheduler(
            onAutoOffExpired = { id -> expiredDeviceId.set(id) },
            onScheduleRuleTriggered = {}
        )
        try {
            scheduler.startAutoOffCountdownSeconds(deviceId = 77, durationSeconds = 1)
            assertTrue(scheduler.activeCountdownsSec.value.containsKey(77))
            delay(1300L)
            assertEquals(77, expiredDeviceId.get())
            assertFalse(scheduler.activeCountdownsSec.value.containsKey(77))
        } finally {
            scheduler.stopAll()
        }
    }

    @Test
    fun `verify full loopback fresh-device WiFiSetup1 provisioning with GetNetworkList fallback, RTOS Method 2 auto-detection, status polling, CloseSetup, SetSetupDoneStatus, and Room insertion`() = runBlocking {
        FakeWeMoUpnpServer(
            initialFriendlyName = "WeMo LightSwitch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS040",
            serialNumber = "WLS040FRESH99",
            firmwareVersion = "WeMo_WW_2.00.12055.PVT",
            udn = "uuid:Lightswitch-2_0-WLS040FRESH99",
            macAddress = "EC1A59BB3344",
            isRtos = true,
            wifiSsid = "WeMo.Light.F99"
        ).use { server ->
            server.failSetupXml404.set(true)
            server.failGetApList500.set(true)
            val setupPort = server.port

            val client = WeMoNetworkClient(extraCandidatePorts = listOf(setupPort))
            val provisioner = WeMoProvisionerClient()
            val repo = WeMoRepository(
                dao = database.wemoDao(),
                networkClient = client,
                provisionerClient = provisioner,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            // 1. Verify scanSetupApNetworks discovers the setup device on setupPort via /Lightsetup.xml
            //    and falls back from GetApList to GetNetworkList
            val scannedAps = repo.scanSetupApNetworks(apHost = "127.0.0.1", apPort = setupPort)
            assertEquals(2, scannedAps.size)
            assertEquals("HeadFamily-2.4G", scannedAps[0].ssid)

            // 2. Provision the switch using encryptionMethod = 0 (Auto-Detect -> should select Method 2 because <rtos>1</rtos>)
            val result = repo.provisionSetupApDevice(
                apHost = "127.0.0.1",
                apPort = setupPort,
                ssid = "HeadFamily-2.4G",
                plainPassword = "SuperSecretHomePass!24",
                channel = 6,
                authMode = "WPA2PSK",
                encryptionType = "AES",
                encryptionMethod = 0, // Auto-Detect from /Lightsetup.xml (<rtos>1</rtos> -> Method 2)
                customDeviceName = "Porch Christmas Tree",
                roomName = "Outdoor / Porch",
                activeNetworkKey = "wifi_home_192_168_1",
                statusPollDelayMs = 20L
            )

            assertTrue(result.success)
            assertEquals(1, result.networkStatusCode)
            assertEquals(2, result.effectiveEncryptionMethod)
            assertEquals(2, server.connectHomeNetworkCalls.get()) // Double-send verified
            assertTrue(server.networkStatusPolls.get() >= 3)      // Polled through 0 -> 3 -> 1
            assertTrue(server.closeSetupCalled.get())             // CloseSetup called
            assertTrue(server.setupDoneCalled.get())              // SetSetupDoneStatus called
            assertEquals("Porch Christmas Tree", server.friendlyName.get())

            // Verify the switch hardware can decrypt the exact password using Method 2
            val decryptedByHardware = provisioner.decryptWifiPassword(
                encryptedPayload = server.receivedEncryptedPassword.get(),
                macAddress = "EC1A59BB3344",
                serialNumber = "WLS040FRESH99",
                encryptionMethod = 2
            )
            assertEquals("SuperSecretHomePass!24", decryptedByHardware)

            // Verify the newly provisioned switch was automatically saved into Room for the active Wi-Fi Home
            val savedDevices = database.wemoDao().getAllDevices().first()
            assertEquals(1, savedDevices.size)
            val provisionedSwitch = savedDevices.single()
            assertEquals("Porch Christmas Tree", provisionedSwitch.name)
            assertEquals("Outdoor / Porch", provisionedSwitch.roomName)
            assertEquals("WLS040FRESH99", provisionedSwitch.serialNumber)
            assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, provisionedSwitch.visualCategory)
        }
    }
}
