package org.suppressingfire.wemi

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Calendar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_CABIN_HOME_KEY
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.data.RuleScheduler
import org.suppressingfire.wemi.data.RuleType
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.data.UNASSIGNED_ROOM
import org.suppressingfire.wemi.data.normalizeRoomName
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.LanHttpClient
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.ProvisionerClient
import org.suppressingfire.wemi.network.XmlAndSkuParser
import org.suppressingfire.wemi.ui.SwitchViewModel
import org.suppressingfire.wemi.widget.WeMiSwitchWidgetProvider
import org.suppressingfire.wemi.widget.WidgetActionExecutor
import org.suppressingfire.wemi.widget.WidgetConfigStore
import org.suppressingfire.wemi.widget.WidgetTargetConfig

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RuleAndTransportTest {

    private fun calendarAt(year: Int, dayOfYear: Int, hour24: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.DAY_OF_YEAR, dayOfYear)
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun ruleSchedulerEvaluatesClockDeterministicallyDeduplicatesMinuteAndIsolatesFailures() = runBlocking {
        // 1. Validate parseScheduleTime24h valid formats and strict out-of-range rejection
        assertEquals(Pair(19, 0), RuleScheduler.parseScheduleTime24h("Daily at 7:00 PM"))
        assertEquals(Pair(12, 0), RuleScheduler.parseScheduleTime24h("Daily at 12:00 PM"))
        assertEquals(Pair(0, 0), RuleScheduler.parseScheduleTime24h("Daily at 12:00 AM"))
        assertEquals(Pair(23, 59), RuleScheduler.parseScheduleTime24h("Daily at 23:59"))
        assertEquals(Pair(17, 30), RuleScheduler.parseScheduleTime24h("Daily at Sunset"))
        assertEquals(Pair(6, 30), RuleScheduler.parseScheduleTime24h("Daily at Sunrise"))
        assertEquals(Pair(0, 0), RuleScheduler.parseScheduleTime24h("Daily at Midnight"))
        assertNull(RuleScheduler.parseScheduleTime24h("Daily at 25:00"))
        assertNull(RuleScheduler.parseScheduleTime24h("Daily at 13:00 PM"))
        assertNull(RuleScheduler.parseScheduleTime24h("Daily at 0:30 AM"))
        assertNull(RuleScheduler.parseScheduleTime24h("Daily at 10:65 PM"))

        // 2. Deterministic schedule evaluation with injected clock, dedup, next-day rollover & fault isolation
        val triggeredIds = mutableListOf<Int>()
        var currentClock = calendarAt(2026, dayOfYear = 100, hour24 = 19, minute = 0)
        val scheduler = RuleScheduler(
            onAutoOffExpired = {},
            onScheduleRuleTriggered = { rule ->
                triggeredIds.add(rule.id)
                if (rule.id == 1) throw IllegalStateException("Simulated rule 1 dispatch error")
            },
            calendarProvider = { currentClock }
        )

        val rules = listOf(
            RuleEntity(1, "Failing Rule", RuleType.SCHEDULE.name, 1, "Lamp 1", "Daily at 7:00 PM", "ON", true, 100, true),
            RuleEntity(2, "Healthy Rule", RuleType.SCHEDULE.name, 2, "Lamp 2", "Daily at 7:00 PM", "ON", true, 100, true),
            RuleEntity(3, "Disabled Rule", RuleType.SCHEDULE.name, 3, "Lamp 3", "Daily at 7:00 PM", "ON", true, 100, false),
            RuleEntity(4, "Auto Off Rule", RuleType.AUTO_OFF.name, 4, "Lamp 4", "Daily at 7:00 PM", "OFF", false, 100, true),
            RuleEntity(5, "Different Time", RuleType.SCHEDULE.name, 5, "Lamp 5", "Daily at 8:15 PM", "ON", true, 100, true)
        )

        // First tick at 19:00 fires rules 1 and 2 (rule 1 throws, rule 2 still runs!)
        assertEquals(2, scheduler.evaluateScheduledRulesNow(rules))
        assertEquals(listOf(1, 2), triggeredIds)

        // Second tick in the same minute deduplicates completely
        assertEquals(0, scheduler.evaluateScheduledRulesNow(rules))
        assertEquals(listOf(1, 2), triggeredIds)

        // Next day at 19:00 fires rules 1 and 2 again
        currentClock = calendarAt(2026, dayOfYear = 101, hour24 = 19, minute = 0)
        assertEquals(2, scheduler.evaluateScheduledRulesNow(rules))
        assertEquals(listOf(1, 2, 1, 2), triggeredIds)
        scheduler.stopAll()
    }

    @Test
    fun networkClientClampsBrightnessAndUsesInjectedSocketFactory() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Dining Dimmer",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060CLAMP01",
            udn = "uuid:Dimmer-2_0-WDS060CLAMP01",
            isRtos = true,
            initialRelayOn = true,
            initialBrightness = 50
        ).use { server ->
            val createdSockets = AtomicInteger(0)
            val countingFactory = object : SocketFactory() {
                private val delegate = getDefault()
                override fun createSocket(): Socket {
                    createdSockets.incrementAndGet()
                    return delegate.createSocket()
                }
                override fun createSocket(host: String?, port: Int): Socket = delegate.createSocket(host, port)
                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                    delegate.createSocket(host, port, localHost, localPort)
                override fun createSocket(host: InetAddress?, port: Int): Socket = delegate.createSocket(host, port)
                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                    delegate.createSocket(address, port, localAddress, localPort)
            }

            val client = NetworkClient(
                extraCandidatePorts = listOf(server.port),
                socketFactoryOverride = countingFactory
            )
            val dimmer = DeviceEntity(
                id = 1,
                name = "Dining Dimmer",
                roomName = "Dining Room",
                deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                ipAddress = "127.0.0.1",
                port = server.port,
                serialNumber = "WDS060CLAMP01",
                udn = "uuid:Dimmer-2_0-WDS060CLAMP01"
            )

            val highRes = client.setBinaryState(dimmer, targetOn = true, targetBrightness = 145)
            assertTrue(highRes.reachableOnLan)
            assertEquals(100, highRes.brightness)
            assertTrue(highRes.logEntry.soapAction.contains("lvl=100%"))

            val nightRes = client.configureNightMode(dimmer, enabled = true, nightBrightness = 0)
            assertTrue(nightRes.reachableOnLan)
            assertTrue(nightRes.logEntry.soapAction.contains("1%"))
            assertTrue("Expected injected SocketFactory to be invoked", createdSockets.get() >= 2)
        }
    }

    @Test
    fun lanHttpClientHandlesNoContentChunkExtensionsMalformedStatusAndMissingHost() {
        val client = LanHttpClient(connectTimeoutMs = 500, readTimeoutMs = 500)

        // Missing host throws IllegalArgumentException
        var missingHostThrew = false
        try {
            client.get("http:///no-host")
        } catch (_: IllegalArgumentException) {
            missingHostThrew = true
        }
        assertTrue("Expected IllegalArgumentException for missing host", missingHostThrew)

        // 204 No Content, chunked with extension + trailers, and malformed status line
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { ss ->
            val worker = thread(isDaemon = true) {
                repeat(3) { idx ->
                    ss.accept().use { sock ->
                        val reader = sock.getInputStream().bufferedReader()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isEmpty()) break
                        }
                        val rawResp = when (idx) {
                            0 -> "HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n"
                            1 -> "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
                                "5;ext=1\r\nHello\r\n6\r\n World\r\n0\r\nX-Trailer: ok\r\n\r\n"
                            else -> "GARBAGE_STATUS_LINE\r\n\r\n"
                        }
                        sock.getOutputStream().write(rawResp.toByteArray(Charsets.US_ASCII))
                        sock.getOutputStream().flush()
                    }
                }
            }

            val noContent = client.get("http://127.0.0.1:${ss.localPort}/empty")
            assertEquals(204, noContent.code)
            assertEquals("", noContent.body)

            val chunked = client.get("http://127.0.0.1:${ss.localPort}/chunked")
            assertEquals(200, chunked.code)
            assertEquals("Hello World", chunked.body)

            var malformedThrew = false
            try {
                client.get("http://127.0.0.1:${ss.localPort}/bad")
            } catch (_: EOFException) {
                malformedThrew = true
            }
            assertTrue("Expected EOFException on malformed HTTP status line", malformedThrew)
            worker.join(2000)
        }
    }

    @Test
    fun switchViewModelAcceptsInjectedRepositoryAndNormalizesRoomNamesConsistently() = runBlocking {
        assertEquals(UNASSIGNED_ROOM, normalizeRoomName(null))
        assertEquals(UNASSIGNED_ROOM, normalizeRoomName("   "))
        assertEquals("Kitchen", normalizeRoomName("  Kitchen  "))
        assertEquals(
            UNASSIGNED_ROOM,
            DeviceEntity(name = "Spare Plug", roomName = "   ").normalizedRoomName
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        SwitchDatabase.closeCurrentInstance()
        val dao = SwitchDatabase.getDatabase(app).switchDao()
        val injectedRepo = SwitchRepository(
            dao = dao,
            context = app,
            initialDemoModeOverride = false
        )
        val vm = SwitchViewModel(app, repositoryOverride = injectedRepo)
        try {
            assertFalse("Injected Real-LAN repository override must flow through SwitchViewModel", vm.isDemoShowroomMode.value)
            dao.insertDevice(
                DeviceEntity(
                    name = "Studio Accent Lamp",
                    roomName = "  ",
                    networkKey = DEFAULT_MAIN_HOME_KEY,
                    ipAddress = "127.0.0.1",
                    port = 49153,
                    isOn = false,
                    isReachable = false,
                    serialNumber = "INJECTSN01"
                )
            )
            val stored = injectedRepo.allDevices.first()
            assertEquals(1, stored.size)
            assertEquals(UNASSIGNED_ROOM, stored.first().normalizedRoomName)
        } finally {
            vm.clearSoapLogs()
            injectedRepo.close()
            SwitchDatabase.closeCurrentInstance()
        }
    }

    @Test
    fun genaServerSurvivesStalledAndResetPeerSocketsWithoutStoppingAcceptLoop() {
        val received = CopyOnWriteArrayList<GenaEventNotification>()
        val delivered = CountDownLatch(1)
        val manager = GenaSubscriptionManager(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            onNotification = { event ->
                received.add(event)
                delivered.countDown()
            }
        )
        try {
            val port = manager.startServer(InetAddress.getByName("127.0.0.1"))
            assertTrue("Expected bound ephemeral GENA port", port > 0)

            // 1. Peer resets mid-request (RST via SO_LINGER=0)
            Socket("127.0.0.1", port).use { resetPeer ->
                resetPeer.setSoLinger(true, 0)
                resetPeer.getOutputStream().write("NOTIFY /gena/BAD HTTP/1.1\r\nContent-Length: 400\r\n\r\n<part".toByteArray())
                resetPeer.getOutputStream().flush()
            }

            // 2. Peer opens a silent socket that triggers server read timeout (SocketTimeoutException)
            Socket("127.0.0.1", port).use { stalledPeer ->
                stalledPeer.soTimeout = 3000
                // Wait until server closes socket after its 1500ms soTimeout
                assertEquals(-1, stalledPeer.getInputStream().read())
            }

            // 3. Subsequent valid NOTIFY must still be accepted and dispatched cleanly
            val body = "<e:propertyset><e:property><BinaryState>1</BinaryState></e:property></e:propertyset>"
            val bodyBytes = body.toByteArray(Charsets.UTF_8)
            Socket("127.0.0.1", port).use { goodPeer ->
                goodPeer.soTimeout = 2000
                val req = "NOTIFY /gena/WLS040SURVIVE HTTP/1.1\r\n" +
                    "Host: 127.0.0.1:$port\r\n" +
                    "SID: uuid:gena-survive\r\n" +
                    "Content-Length: ${bodyBytes.size}\r\n\r\n"
                goodPeer.getOutputStream().write(req.toByteArray(Charsets.US_ASCII))
                goodPeer.getOutputStream().write(bodyBytes)
                goodPeer.getOutputStream().flush()
                val statusLine = goodPeer.getInputStream().bufferedReader().readLine()
                assertEquals("HTTP/1.1 200 OK", statusLine)
            }

            assertTrue("GENA accept loop must remain alive after peer reset/timeout", delivered.await(2, TimeUnit.SECONDS))
            assertEquals("WLS040SURVIVE", received.single().deviceKey)
            assertEquals(true, received.single().binaryStateOn)
        } finally {
            manager.close()
        }
    }

    @Test
    fun xmlEntityUnescapingRoundTripsFriendlyNamesAndSsidsAcrossSetupMetaAndApList() = runBlocking {
        assertEquals("Ben & Jerry's \"Lamp\"", XmlAndSkuParser.unescapeXml("Ben &amp; Jerry&apos;s &quot;Lamp&quot;"))
        assertEquals("A&lt;B", XmlAndSkuParser.unescapeXml("A&amp;lt;B"))

        val meta = XmlAndSkuParser.parseMetaInfoSoapResponse(
            "<MetaInfo>AA11BB22CC33|SN1|WLS040|fw|Mike&apos;s &amp; Co WiFi|LightSwitch</MetaInfo>"
        )
        assertEquals("Mike's & Co WiFi", meta?.accessPointSsid)

        val directAps = XmlAndSkuParser.parseApListXml(
            "<ApList>Mike&apos;s &amp; Co|6|82|WPA2PSK/AES\nA&amp;lt;B|1|70|WPA2PSK/AES</ApList>"
        )
        assertEquals(listOf("Mike's & Co", "A&lt;B"), directAps.map { it.ssid })

        val escapedAps = XmlAndSkuParser.parseApListXml(
            "&lt;ApList&gt;Tom&apos;s WiFi|11|90|WPA2PSK/AES&lt;/ApList&gt;"
        )
        assertEquals(listOf("Tom's WiFi"), escapedAps.map { it.ssid })

        // End-to-end wire round-trip: ChangeFriendlyName escapes XML -> /setup.xml returns escaped -> parsers decode
        FakeUpnpServer(
            initialFriendlyName = "Plain Switch",
            modelName = "WLS040",
            serialNumber = "WLS040XML01",
            udn = "uuid:Lightswitch-3_0-WLS040XML01"
        ).use { server ->
            val client = NetworkClient(extraCandidatePorts = listOf(server.port))
            val device = DeviceEntity(
                id = 1, name = "Plain Switch", ipAddress = "127.0.0.1",
                port = server.port, serialNumber = "WLS040XML01"
            )
            val specialName = "Ben & Jerry's \"Lamp\""
            val renameRes = client.changeFriendlyName(device, specialName)
            assertTrue(renameRes.reachableOnLan)

            val fetched = client.probeAndFetchDeviceDescriptor("127.0.0.1", server.port)
            assertEquals(specialName, fetched?.name)

            val setupDesc = ProvisionerClient().probeSetupDevice("127.0.0.1", server.port)
            assertEquals(specialName, setupDesc?.friendlyName)
        }
    }

    @Test
    fun autoOffAndMotionRulesIsolateSameNamedSwitchesAcrossWifiHomes() = runBlocking {
        // 1. RuleScheduler AUTO_OFF isolates same-named switches across homes while keeping deleted-ID fallback
        val scheduler = RuleScheduler(onAutoOffExpired = {}, onScheduleRuleTriggered = {})
        try {
            val mainPorch = DeviceEntity(id = 1, name = "Porch Light", networkKey = DEFAULT_MAIN_HOME_KEY, isOn = true, isReachable = true)
            val cabinPorch = DeviceEntity(id = 10, name = "Porch Light", networkKey = DEFAULT_CABIN_HOME_KEY, isOn = true, isReachable = true)
            val mainOnlyRule = listOf(
                RuleEntity(
                    id = 5, name = "Main Porch Auto-Off", ruleType = RuleType.AUTO_OFF.name,
                    targetDeviceId = 1, targetDeviceName = "Porch Light",
                    triggerSummary = "After 15 minutes ON", actionSummary = "Turn OFF Porch Light",
                    targetStateOn = false, isEnabled = true
                )
            )
            val bothActive = setOf(1, 10)
            scheduler.onDeviceStateUpdated(cabinPorch, mainOnlyRule, bothActive)
            assertFalse("Foreign-home AUTO_OFF rule must not start timer on same-named device", scheduler.activeCountdownsSec.value.containsKey(10))

            scheduler.onDeviceStateUpdated(mainPorch, mainOnlyRule, bothActive)
            assertEquals(900, scheduler.activeCountdownsSec.value[1])

            // When targetDeviceId=1 no longer exists in activeDeviceIds, fallback by name still works
            scheduler.onDeviceStateUpdated(cabinPorch, mainOnlyRule, setOf(10))
            assertEquals(900, scheduler.activeCountdownsSec.value[10])
        } finally {
            scheduler.stopAll()
        }

        // 2. Motion-trigger rule targeting Cabin's "Porch Light" must NOT hijack Main Home's "Porch Light"
        val app = ApplicationProvider.getApplicationContext<Application>()
        SwitchDatabase.closeCurrentInstance()
        val dao = SwitchDatabase.getDatabase(app).switchDao()
        FakeUpnpServer(initialFriendlyName = "Porch Light", serialNumber = "WLS040MAINP1", initialRelayOn = false).use { mainServer ->
            FakeUpnpServer(initialFriendlyName = "Porch Light", serialNumber = "WLS040CABP2", initialRelayOn = false).use { cabinServer ->
                val repo = SwitchRepository(dao = dao, context = app, initialDemoModeOverride = false)
                try {
                    val mainPorchId = dao.insertDevice(
                        DeviceEntity(
                            name = "Porch Light", roomName = "Porch", networkKey = DEFAULT_MAIN_HOME_KEY,
                            ipAddress = "127.0.0.1", port = mainServer.port, isOn = false, isReachable = true,
                            serialNumber = "WLS040MAINP1"
                        )
                    ).toInt()
                    val cabinPorchId = dao.insertDevice(
                        DeviceEntity(
                            name = "Porch Light", roomName = "Porch", networkKey = DEFAULT_CABIN_HOME_KEY,
                            ipAddress = "127.0.0.1", port = cabinServer.port, isOn = false, isReachable = true,
                            serialNumber = "WLS040CABP2"
                        )
                    ).toInt()
                    val mainSensor = DeviceEntity(
                        name = "Main Porch Motion", roomName = "Porch", networkKey = DEFAULT_MAIN_HOME_KEY,
                        deviceType = DeviceType.MOTION_SENSOR.name, ipAddress = "127.0.0.1", port = 49159
                    ).let { it.copy(id = dao.insertDevice(it).toInt()) }

                    dao.insertRule(
                        RuleEntity(
                            name = "Cabin Porch Motion", ruleType = RuleType.MOTION_TRIGGER.name,
                            targetDeviceId = cabinPorchId, targetDeviceName = "Porch Light",
                            triggerSummary = "Cabin motion", actionSummary = "Turn ON Porch Light",
                            targetStateOn = true, isEnabled = true
                        )
                    )

                    repo.triggerMotionEvent(mainSensor)
                    assertFalse("Main Home same-named switch must not be hijacked by Cabin rule", mainServer.relayStateOn.get())
                    assertFalse("Cabin Home switch must stay isolated from Main Home sensor", cabinServer.relayStateOn.get())

                    // Deleting Cabin switch allows same-home name fallback to recover
                    dao.deleteDeviceById(cabinPorchId)
                    repo.triggerMotionEvent(mainSensor)
                    assertTrue("Deleted targetDeviceId should fall back by name within triggering home", mainServer.relayStateOn.get())
                    assertTrue(dao.getDeviceById(mainPorchId)?.isOn == true)
                } finally {
                    repo.close()
                    SwitchDatabase.closeCurrentInstance()
                }
            }
        }
    }

    @Test
    fun widgetConfigStoreRejectsNonPositiveIdsAndProviderCleansWidgetMutexes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WidgetConfigStore.clearAll(context)
        try {
            WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(0, "Kitchen", DEFAULT_MAIN_HOME_KEY))
            WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(-4, "Kitchen", DEFAULT_MAIN_HOME_KEY))
            assertTrue(WidgetConfigStore.allConfiguredWidgetIds(context).isEmpty())

            WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(777, "Kitchen", DEFAULT_MAIN_HOME_KEY))
            WidgetConfigStore.remapIds(context, intArrayOf(777), intArrayOf(-1))
            assertNotNull("Invalid negative destination ID must not wipe existing binding", WidgetConfigStore.load(context, 777))

            // Allocate a per-widget Mutex, remap 777 -> 888, then delete 888 and verify cleanup
            val repo = SwitchRepository(
                dao = SwitchDatabase.getDatabase(context).switchDao(),
                context = context,
                initialDemoModeOverride = true
            )
            try {
                WidgetActionExecutor.toggleWidgetTarget(context, 777, repositoryOverride = repo)
                assertTrue(WidgetActionExecutor.hasMutexFor(777))

                val provider = WeMiSwitchWidgetProvider()
                provider.onRestored(context, intArrayOf(777), intArrayOf(888))
                assertFalse("Retired widget ID mutex should be evicted on onRestored", WidgetActionExecutor.hasMutexFor(777))
                assertNotNull(WidgetConfigStore.load(context, 888))

                WidgetActionExecutor.toggleWidgetTarget(context, 888, repositoryOverride = repo)
                assertTrue(WidgetActionExecutor.hasMutexFor(888))

                provider.onDeleted(context, intArrayOf(888))
                assertFalse("Deleted widget mutex should be evicted on onDeleted", WidgetActionExecutor.hasMutexFor(888))
                assertNull(WidgetConfigStore.load(context, 888))
            } finally {
                repo.close()
                SwitchDatabase.closeCurrentInstance()
            }
        } finally {
            WidgetConfigStore.clearAll(context)
        }
    }
}
