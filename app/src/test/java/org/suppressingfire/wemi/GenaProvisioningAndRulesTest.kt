package org.suppressingfire.wemi

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.DeviceCategory
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.data.RuleScheduler
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.ProvisionerClient
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
class GenaProvisioningAndRulesTest {

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
    fun `verify Phase 3 UPnP GENA SUBSCRIBE, HTTP 412 recovery, and embedded NOTIFY propertyset callback`() = runBlocking {
        val receivedNotification = AtomicReference<GenaEventNotification?>(null)
        val genaManager = GenaSubscriptionManager(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            onNotification = { receivedNotification.set(it) }
        )

        FakeUpnpServer(
            initialFriendlyName = "Kitchen 3-Way Switch",
            serialNumber = "WLS0403KITCHEN"
        ).use { server ->
            try {
                val device = DeviceEntity(
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
    fun `verify Phase 5 RuleScheduler schedule time parsing and Auto-Off countdown timer`() = runBlocking {
        assertEquals(Pair(19, 0), RuleScheduler.parseScheduleTime24h("Daily at 7:00 PM"))
        assertEquals(Pair(17, 15), RuleScheduler.parseScheduleTime24h("Daily at Sunset (5:15 PM)"))
        assertEquals(Pair(22, 0), RuleScheduler.parseScheduleTime24h("Every night at 10:00 PM"))
        assertEquals(Pair(6, 30), RuleScheduler.parseScheduleTime24h("Daily at Sunrise (6:30 AM)"))
        assertEquals(30, RuleScheduler.parseMinutesFromSummary("After 30 minutes ON"))

        val expiredDeviceId = AtomicInteger(-1)
        val scheduler = RuleScheduler(
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
        FakeUpnpServer(
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

            val client = NetworkClient(extraCandidatePorts = listOf(setupPort))
            val provisioner = ProvisionerClient()
            val repo = SwitchRepository(
                dao = database.switchDao(),
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
            val savedDevices = database.switchDao().getAllDevices().first()
            assertEquals(1, savedDevices.size)
            val provisionedSwitch = savedDevices.single()
            assertEquals("Porch Christmas Tree", provisionedSwitch.name)
            assertEquals("Outdoor / Porch", provisionedSwitch.roomName)
            assertEquals("WLS040FRESH99", provisionedSwitch.serialNumber)
            assertEquals(DeviceCategory.CHRISTMAS_TREE, provisionedSwitch.visualCategory)
            assertTrue(result.debugTraceLines.any { it.contains("[STEP 1 PROBE]") })
            assertTrue(result.debugTraceLines.any { it.contains("[STEP 4 CRYPTO] Method=2 addLengths=false") })
            assertTrue(result.debugTraceLines.any { it.contains("[STEP 6 CLOSE] CloseSetup -> success") })
        }
    }

    @Test
    fun `verify WiFiSetup1 provisioning when NetworkStatus remains 0 reports failure, skips CloseSetup to keep Setup AP open, and emits screenshot debug trace`() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "WeMo LightSwitch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "F7C030",
            serialNumber = "221517K01018F7",
            firmwareVersion = "WeMo_WW_2.00.10966.PVT-OWRT-LS",
            udn = "uuid:Lightswitch-1_0-221517K01018F7",
            macAddress = "EC1A59CC7788",
            isRtos = false,
            wifiSsid = "WeMo.Light.788"
        ).use { server ->
            server.forceNetworkStatusZero.set(true)
            val setupPort = server.port

            val client = NetworkClient(extraCandidatePorts = listOf(setupPort))
            val provisioner = ProvisionerClient()
            val repo = SwitchRepository(
                dao = database.switchDao(),
                networkClient = client,
                provisionerClient = provisioner,
                initialDemoModeOverride = false
            )
            repo.ensureSeeded()

            val probe = repo.probeSetupApDevice(apHost = "127.0.0.1", apPort = setupPort)
            val aps = repo.scanSetupApNetworks(apHost = "127.0.0.1", apPort = setupPort)
            val streamedLines = mutableListOf<String>()

            val result = repo.provisionSetupApDevice(
                apHost = "127.0.0.1",
                apPort = setupPort,
                ssid = "HeadFamily-2.4G",
                plainPassword = "WrongRouterPassword99",
                channel = 11,
                authMode = "WPA2PSK",
                encryptionType = "AES",
                encryptionMethod = 1,
                addPasswordLengths = true,
                customDeviceName = "Garage Switch",
                roomName = "Garage",
                activeNetworkKey = "wifi_home_192_168_1",
                maxStatusPolls = 3,
                statusPollDelayMs = 15L,
                onProgress = { streamedLines.add(it) }
            )

            // NetworkStatus=0 must NOT be reported as success and must NOT call CloseSetup
            assertFalse(result.success)
            assertEquals(0, result.networkStatusCode)
            assertEquals("kept_open_for_retry", result.closeSetupStatus)
            assertFalse(server.closeSetupCalled.get())
            assertFalse(server.setupDoneCalled.get())
            assertTrue(streamedLines.any { it.contains("[STEP 6 HOLD] NetworkStatus=0") })

            // Verify auto-matching from ApList overrode manual Ch=11 with ApList's Ch=6 for HeadFamily-2.4G
            assertTrue(result.debugTraceLines.any { it.contains("Matched \"HeadFamily-2.4G\" in ApList: Ch=6") })

            // Verify SetupDebugSnapshot formats all screenshot lines cleanly
            val snapshot = org.suppressingfire.wemi.network.SetupDebugSnapshot(
                timestamp = "10:45:00 PM",
                phoneLocalIp = "10.22.22.142",
                phoneGatewayIp = "10.22.22.1",
                phoneInterface = "wlan0",
                isWemoSetupSubnet = true,
                isWifiSocketBound = true,
                isDemoMode = false,
                targetApHost = "127.0.0.1",
                probeSummary = repo.latestSetupProbeSummary(),
                detectedDevice = probe,
                apScanSummary = repo.latestSetupApScanSummary(),
                scannedAps = aps,
                traceLines = result.debugTraceLines
            )
            val displayLines = snapshot.toDisplayLines("HeadFamily-2.4G")
            assertTrue(displayLines.any { it.contains("[NET] mode=REAL-LAN ip=10.22.22.142") })
            assertTrue(displayLines.any { it.contains("[ID] mac=EC1A59CC7788 sn=221517K01018F7 (src=GetMetaInfo)") })
            assertTrue(displayLines.any { it.contains("[TARGET AP] \"HeadFamily-2.4G\" found in ApList: Ch=6") })

            // Verify TelemetryFileExporter builds and saves wemi-telemetry-latest.txt cleanly
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            val fullReport = org.suppressingfire.wemi.network.TelemetryFileExporter.buildFullTelemetryReport(
                setupSnapshot = snapshot,
                selectedSsid = "HeadFamily-2.4G",
                homes = database.switchDao().getAllHomeNetworks().first(),
                devices = database.switchDao().getAllDevices().first(),
                soapLogs = repo.soapLogs.value
            )
            assertTrue(fullReport.contains("=== WeMi Light Control Device & Network Telemetry Report ==="))
            assertTrue(fullReport.contains("--- [1. SETUP AP & PROVISIONING SNAPSHOT] ---"))
            assertTrue(fullReport.contains("--- [4. UPNP / SOAP PACKET LOG"))
            val savedFile = org.suppressingfire.wemi.network.TelemetryFileExporter.saveTelemetryReportToFile(appContext, fullReport)
            assertTrue(savedFile.exists())
            assertEquals(fullReport, savedFile.readText())

            // Failed setup must not insert a phantom device row into Room
            val savedDevices = database.switchDao().getAllDevices().first()
            assertEquals(0, savedDevices.size)
        }
    }
}

