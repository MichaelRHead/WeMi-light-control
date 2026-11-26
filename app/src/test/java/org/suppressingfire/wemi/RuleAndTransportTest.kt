package org.suppressingfire.wemi

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
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
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.data.UNASSIGNED_ROOM
import org.suppressingfire.wemi.data.normalizeRoomName
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.HardwareLongPressAction
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesDbMutator
import org.suppressingfire.wemi.network.HardwareTimeAnchor
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

    @Test
    fun hardwareRuleDraftRoundTripsNativeRulesAndSanitizesSqlText() {
        assertEquals(
            "Evening Porch Sunset",
            HardwareRulesDbMutator.sanitizeSqlText("Evening; \"Porch\" -- Sunset\n\r\u0000")
        )
        assertTrue(HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.any { it.cityName == "Seattle" })

        val solarRule = HardwareRule(
            ruleId = 7,
            name = "Porch Dusk to Dawn",
            type = HardwareRuleType.SCHEDULE_INTERVAL,
            rawFirmwareType = "Time Interval",
            isEnabled = true,
            ruleOrder = 1,
            ownerDeviceUdn = "uuid:Lightswitch-3_0-SN1",
            daysOfWeek = listOf(2, 3, 4, 5, 6),
            startTime = org.suppressingfire.wemi.network.HardwareRuleTimePoint(rawSeconds = 64802, offsetSeconds = -900),
            endTime = org.suppressingfire.wemi.network.HardwareRuleTimePoint(rawSeconds = 21601, offsetSeconds = 1800),
            ruleDurationSeconds = 36000,
            startAction = 1.0,
            endAction = 0.0,
            countdownSeconds = 0,
            sensorDurationSeconds = 0,
            appliesToInspectedSwitch = true
        )
        assertTrue(solarRule.isEditableInApp)
        val solarDraft = checkNotNull(HardwareRuleDraft.fromHardwareRule(solarRule))
        assertEquals(7, solarDraft.existingRuleId)
        assertEquals(HardwareTimeAnchor.SUNSET, solarDraft.startAnchor)
        assertEquals(-15, solarDraft.startOffsetMinutes)
        assertEquals(HardwareTimeAnchor.SUNRISE, solarDraft.endAnchor)
        assertEquals(30, solarDraft.endOffsetMinutes)
        assertEquals(listOf(2, 3, 4, 5, 6), solarDraft.daysOfWeek)

        val longPressRule = HardwareRule(
            ruleId = 2,
            name = "Hold All Off",
            type = HardwareRuleType.LONG_PRESS,
            rawFirmwareType = "Long Press",
            isEnabled = true,
            ruleOrder = 2,
            ownerDeviceUdn = "uuid:Lightswitch-3_0-SN1",
            daysOfWeek = listOf(-1),
            startTime = org.suppressingfire.wemi.network.HardwareRuleTimePoint(60),
            endTime = org.suppressingfire.wemi.network.HardwareRuleTimePoint(86400),
            ruleDurationSeconds = 86340,
            startAction = HardwareLongPressAction.TURN_OFF.code.toDouble(),
            endAction = -1.0,
            countdownSeconds = 0,
            sensorDurationSeconds = 0,
            targetDeviceUdns = listOf("uuid:Dimmer-2_0-SN2"),
            appliesToInspectedSwitch = true
        )
        val lpDraft = checkNotNull(HardwareRuleDraft.fromHardwareRule(longPressRule))
        assertEquals(HardwareLongPressAction.TURN_OFF, lpDraft.longPressAction)
        assertEquals(listOf("uuid:Dimmer-2_0-SN2"), lpDraft.targetDeviceUdns)

        val awayRule = solarRule.copy(ruleId = 9, rawFirmwareType = "Away Mode", type = HardwareRuleType.AWAY_MODE)
        assertFalse("Away Mode stays read-only/toggleable, not recreated in editor", awayRule.isEditableInApp)
        assertNull(HardwareRuleDraft.fromHardwareRule(awayRule))
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
    fun genaServerSurvivesStalledAndResetPeerSocketsWithoutStoppingAcceptLoop() = runBlocking {
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
        FakeUpnpServer(
            initialFriendlyName = "Resilient Switch",
            serialNumber = "WLS040SURVIVE"
        ).use { server ->
            try {
                val sub = manager.subscribeOrRenew(
                    DeviceEntity(
                        id = 7, name = "Resilient Switch", ipAddress = "127.0.0.1",
                        port = server.port, serialNumber = "WLS040SURVIVE"
                    )
                )
                assertNotNull("Expected active GENA subscription", sub)
                val port = manager.activeListenPort
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
                    assertEquals(-1, stalledPeer.getInputStream().read())
                }

                // 3. Subsequent authenticated NOTIFY must still be accepted and dispatched cleanly
                val respLine = server.pushGenaNotify(
                    callbackPort = port,
                    deviceKey = "WLS040SURVIVE",
                    binaryStatePayload = "1",
                    sid = sub!!.sid
                )
                assertEquals("HTTP/1.1 200 OK", respLine)

                assertTrue("GENA accept loop must remain alive after peer reset/timeout", delivered.await(2, TimeUnit.SECONDS))
                assertEquals("WLS040SURVIVE", received.single().deviceKey)
                assertEquals(true, received.single().binaryStateOn)
            } finally {
                manager.close()
            }
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
    fun hardwareRulesLocationInfoUpdatePreservesRulesAndPassesIntegrityCheck() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dbFile = java.io.File.createTempFile("loc_mutator_", ".db", app.cacheDir)
        try {
            org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(dbFile)
            val ownerUdn = "uuid:Lightswitch-3_0-WLS040LOC01"
            val ruleId = HardwareRulesDbMutator.upsertRule(
                sqliteFile = dbFile,
                ownerDeviceUdn = ownerUdn,
                draft = HardwareRuleDraft(
                    name = "Porch Dusk Lights",
                    type = HardwareRuleType.SCHEDULE_INTERVAL,
                    isEnabled = true,
                    daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                    startAnchor = HardwareTimeAnchor.SUNSET,
                    startOffsetMinutes = -15,
                    endAnchor = HardwareTimeAnchor.FIXED_TIME,
                    endHour24 = 23,
                    endMinute = 0,
                    startActionOn = true,
                    endActionOn = false
                )
            )
            assertTrue(ruleId > 0)

            val seattle = HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.first { it.cityName == "Seattle" }
            HardwareRulesDbMutator.updateLocationInfo(dbFile, seattle)

            val (b64, verdict) = org.suppressingfire.wemi.network.HardwareRulesSafetyValidator
                .validateAndPackToBase64Zip(dbFile)
            assertTrue("Packed rules.db with LOCATIONINFO must pass anti-brick validation: ${verdict.issues}", verdict.isSafeToStore)
            assertNotNull(b64)

            val parsed = org.suppressingfire.wemi.network.HardwareRulesDbCodec.parseSqliteFileReadOnly(
                dbFile,
                org.suppressingfire.wemi.network.HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME,
                ownerUdn
            )
            assertEquals(1, parsed.rules.size)
            assertEquals("Seattle", parsed.locationInfo?.cityName)
            assertEquals("47.6062", parsed.locationInfo?.latitude)
            assertEquals("-122.3321", parsed.locationInfo?.longitude)
        } finally {
            runCatching { dbFile.delete() }
            HardwareRulesDbMutator.cleanupSqliteSidecars(dbFile)
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
