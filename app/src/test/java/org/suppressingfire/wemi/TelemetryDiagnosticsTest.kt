package org.suppressingfire.wemi

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.ProvisionerClient
import org.suppressingfire.wemi.network.SetupDebugSnapshot
import org.suppressingfire.wemi.network.SharedSoapLogStore
import org.suppressingfire.wemi.network.TelemetryFileExporter
import org.suppressingfire.wemi.ui.components.ProvisionDebugPanel
import org.suppressingfire.wemi.ui.components.isProvisionStatusWarningOrError
import org.suppressingfire.wemi.widget.WeMiSwitchWidgetProvider
import org.suppressingfire.wemi.widget.WidgetActionExecutor
import org.suppressingfire.wemi.widget.WidgetConfigStore
import org.suppressingfire.wemi.widget.WidgetTargetConfig

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelemetryDiagnosticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WeMiSwitchWidgetProvider.broadcastTimeoutMs = 8_500L
        WidgetConfigStore.clearAll(context)
        SharedSoapLogStore.clear(context)
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        WeMiSwitchWidgetProvider.cancelAndJoinPendingWorkForTests()
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WeMiSwitchWidgetProvider.broadcastTimeoutMs = 8_500L
        WidgetConfigStore.clearAll(context)
        SharedSoapLogStore.clear(context)
        SwitchDatabase.closeCurrentInstance()
    }

    @Test
    fun sharedSoapLogStoreStreamsHeadlessWidgetSoapAndOrphanEventsIntoAppRepository() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Porch Lantern",
            modelName = "WLS040",
            serialNumber = "WLS040TELEM01",
            udn = "uuid:Lightswitch-3_0-WLS040TELEM01",
            initialRelayOn = false
        ).use { server ->
            val dao = SwitchDatabase.getDatabase(context).switchDao()
            // Simulate main app repository observing shared SOAP logs for this Application
            val appRepo = SwitchRepository(
                dao = dao,
                context = context,
                initialDemoModeOverride = false
            )
            try {
                appRepo.setDemoShowroomMode(false)
                val saved = appRepo.saveDevice(
                    DeviceEntity(
                        name = "Porch Lantern",
                        roomName = "Porch",
                        networkKey = DEFAULT_MAIN_HOME_KEY,
                        deviceType = DeviceType.SWITCH_PLUG.name,
                        ipAddress = "127.0.0.1",
                        port = server.port,
                        isOn = false,
                        isReachable = true,
                        serialNumber = "WLS040TELEM01"
                    )
                )
                appRepo.clearLogs()
                assertTrue(appRepo.soapLogs.value.isEmpty())

                // 1) Headless widget toggle (no repositoryOverride -> ephemeral repo closed after toggle)
                WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(501, saved))
                assertTrue(WidgetActionExecutor.toggleWidgetTarget(context, 501))
                assertTrue(server.relayStateOn.get())

                val logsAfterToggle = appRepo.soapLogs.value
                assertTrue(
                    "Expected headless SetBinaryState SOAP entry in shared app log, got: $logsAfterToggle",
                    logsAfterToggle.any { it.soapAction.contains("SetBinaryState(1)") && it.isLiveLanSuccess }
                )
                assertTrue(
                    "Expected Widget#Toggle(DEVICE) summary in shared app log, got: $logsAfterToggle",
                    logsAfterToggle.any {
                        it.soapAction == "Widget#Toggle(DEVICE)" &&
                            it.endpoint == "widget://501" &&
                            it.statusText.contains("-> ON")
                    }
                )

                // 2) Unconfigured widget tap & orphaned device/room widget taps emit diagnostic entries
                assertFalse(WidgetActionExecutor.toggleWidgetTarget(context, 599))
                WidgetConfigStore.save(
                    context,
                    WidgetTargetConfig(
                        appWidgetId = 502,
                        targetType = org.suppressingfire.wemi.widget.WidgetTargetType.DEVICE,
                        deviceId = 9999,
                        serialNumber = "WLS040MISSING",
                        roomName = "Garage",
                        networkKey = DEFAULT_MAIN_HOME_KEY,
                        displayName = "Deleted Garage Light",
                        udn = "uuid:Lightswitch-3_0-WLS040MISSING"
                    )
                )
                assertFalse(WidgetActionExecutor.toggleWidgetTarget(context, 502))
                WidgetConfigStore.save(
                    context,
                    WidgetTargetConfig.forRoom(503, "Attic", DEFAULT_MAIN_HOME_KEY)
                )
                assertFalse(WidgetActionExecutor.toggleWidgetTarget(context, 503))

                val allLogs = appRepo.soapLogs.value
                assertTrue(allLogs.any { it.endpoint == "widget://599" && it.statusText.contains("Widget not configured") })
                assertTrue(
                    allLogs.any {
                        it.endpoint == "widget://502" &&
                            it.statusText.contains("Orphaned") &&
                            it.statusText.contains("udn=uuid:Lightswitch-3_0-WLS040MISSING")
                    }
                )
                assertTrue(allLogs.any { it.endpoint == "widget://503" && it.statusText.contains("Orphaned") })
            } finally {
                appRepo.close()
            }
        }
    }

    @Test
    fun widgetProviderLogsBroadcastTimeoutIntoSharedSoapLogStore() = runBlocking {
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { stalledServer ->
            val stalledThread = kotlin.concurrent.thread(isDaemon = true) {
                runCatching {
                    stalledServer.accept().use { sock ->
                        Thread.sleep(600L)
                        sock.close()
                    }
                }
            }
            val dao = SwitchDatabase.getDatabase(context).switchDao()
            val setupRepo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
            try {
                setupRepo.setDemoShowroomMode(false)
                val insertedId = dao.insertDevice(
                    DeviceEntity(
                        name = "Stalled Patio Switch",
                        roomName = "Patio",
                        networkKey = DEFAULT_MAIN_HOME_KEY,
                        ipAddress = "127.0.0.1",
                        port = stalledServer.localPort,
                        isOn = false,
                        isReachable = true,
                        serialNumber = "WLS040STALL1"
                    )
                ).toInt()
                WidgetConfigStore.save(
                    context,
                    WidgetTargetConfig.forDevice(550, dao.getDeviceById(insertedId)!!)
                )
                SharedSoapLogStore.clear(context)

                WeMiSwitchWidgetProvider.broadcastTimeoutMs = 80L
                val provider = WeMiSwitchWidgetProvider()
                provider.onReceive(
                    context,
                    Intent(WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 550)
                    }
                )

                var sawTimeout = false
                repeat(30) {
                    if (SharedSoapLogStore.flowFor(context).value.any {
                            it.soapAction == "Widget#Toggle(550)" &&
                                it.statusText.contains("Timeout • Exceeded 80ms broadcast budget")
                        }
                    ) {
                        sawTimeout = true
                        return@repeat
                    }
                    delay(40L)
                }
                assertTrue("Expected widget broadcast timeout diagnostic log", sawTimeout)
            } finally {
                setupRepo.close()
                stalledThread.join(1_000L)
            }
        }
    }

    @Test
    fun telemetryExporterRendersSection5WidgetsAndHomeKeyAcrossResolvedAndOrphanedTargets() {
        val home = HomeNetworkEntity(
            networkKey = DEFAULT_MAIN_HOME_KEY,
            homeName = "Main Home",
            ssidLabel = "HeadFamily-2.4G",
            subnetPrefix = "192.168.86",
            gatewayIp = "192.168.86.1"
        )
        val switchOn = DeviceEntity(
            id = 11,
            name = "Patio String Lights",
            roomName = "Patio",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            ipAddress = "192.168.86.21",
            port = 49153,
            isOn = true,
            isReachable = true,
            serialNumber = "WLS040PAT11"
        )
        val unassignedSwitch = DeviceEntity(
            id = 12,
            name = "Mystery Plug",
            roomName = "",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            ipAddress = "192.168.86.22",
            port = 49153,
            isOn = false,
            isReachable = true,
            serialNumber = "WSP080MYS12",
            udn = "uuid:Socket-1_0-WSP080MYS12"
        )
        val patioOffline = DeviceEntity(
            id = 13,
            name = "Patio Floodlight",
            roomName = "Patio",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            ipAddress = "192.168.86.23",
            port = 49153,
            isOn = false,
            isReachable = false,
            serialNumber = "WLS040PAT13"
        )

        WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(603, switchOn.copy(id = 999, serialNumber = "WLS040GONE")))
        WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(601, switchOn))
        WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(602, "Patio", DEFAULT_MAIN_HOME_KEY))
        WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(604, unassignedSwitch))

        val loadedWidgets = WidgetConfigStore.loadAll(context)
        assertEquals(listOf(601, 602, 603, 604), loadedWidgets.map { it.appWidgetId })

        val report = TelemetryFileExporter.buildFullTelemetryReport(
            setupSnapshot = SetupDebugSnapshot(timestamp = "9:15:00 AM", phoneLocalIp = "192.168.86.10"),
            homes = listOf(home),
            devices = listOf(switchOn, unassignedSwitch, patioOffline),
            widgets = loadedWidgets
        )

        assertTrue(report.contains("[DEV #12] name=\"Mystery Plug\" room=\"Unassigned\" home=$DEFAULT_MAIN_HOME_KEY"))
        assertTrue(report.contains("--- [5. HOME SCREEN WIDGETS (4)] ---"))
        assertTrue(report.contains("[WIDGET #601] type=DEVICE name=\"Patio String Lights\" devId=11 sn=WLS040PAT11 room=\"Patio\" home=$DEFAULT_MAIN_HOME_KEY -> resolved=#11 \"Patio String Lights\" (ON)"))
        assertTrue(report.contains("[WIDGET #602] type=ROOM room=\"Patio\" home=$DEFAULT_MAIN_HOME_KEY -> resolved=1/2 ON (1/2 reachable)"))
        assertTrue(report.contains("[WIDGET #603] type=DEVICE name=\"Patio String Lights\" devId=999 sn=WLS040GONE room=\"Patio\" home=$DEFAULT_MAIN_HOME_KEY -> ORPHANED (no matching switch)"))
        assertTrue(report.contains("[WIDGET #604] type=DEVICE name=\"Mystery Plug\" devId=12 sn=WSP080MYS12 udn=uuid:Socket-1_0-WSP080MYS12 room=\"Unassigned\" home=$DEFAULT_MAIN_HOME_KEY -> resolved=#12 \"Mystery Plug\" (OFF_STANDBY)"))
    }

    @Test
    fun sanitizeXmlSnippetRedactsNamespacedAttributedEntityEscapedAndTruncatedPasswords() {
        val namespaced = "<u:ConnectHomeNetwork><u:password xsi:type=\"xsd:string\">TopSecretWiFi99!</u:password></u:ConnectHomeNetwork>"
        val cleanNamespaced = TelemetryFileExporter.sanitizeXmlSnippet(namespaced)
        assertFalse(cleanNamespaced.contains("TopSecretWiFi99!"))
        assertTrue(cleanNamespaced.contains("<u:password xsi:type=\"xsd:string\">[REDACTED encLen=16]</u:password>"))

        val entityEscaped = "&lt;ConnectHomeNetwork&gt;&lt;password&gt;EntityPass42&lt;/password&gt;&lt;/ConnectHomeNetwork&gt;"
        val cleanEscaped = TelemetryFileExporter.sanitizeXmlSnippet(entityEscaped)
        assertFalse(cleanEscaped.contains("EntityPass42"))
        assertTrue(cleanEscaped.contains("&lt;password&gt;[REDACTED encLen=12]&lt;/password&gt;"))

        val truncatedUnclosed = "<ConnectHomeNetwork><ssid>Home24</ssid><password>TruncatedCipherBytesBeforeClose"
        val cleanTruncated = TelemetryFileExporter.sanitizeXmlSnippet(truncatedUnclosed)
        assertFalse(cleanTruncated.contains("TruncatedCipherBytesBeforeClose"))
        assertTrue(cleanTruncated.contains("<password>[REDACTED encLen=31]"))

        val truncatedEscaped = "&lt;ConnectHomeNetwork&gt;&lt;password&gt;TruncatedEscapedSecret"
        val cleanTruncatedEscaped = TelemetryFileExporter.sanitizeXmlSnippet(truncatedEscaped)
        assertFalse(cleanTruncatedEscaped.contains("TruncatedEscapedSecret"))
        assertTrue(cleanTruncatedEscaped.contains("&lt;password&gt;[REDACTED encLen=22]"))

        val alreadyRedacted = "<ConnectHomeNetwork><password>[REDACTED encLen=64]</password></ConnectHomeNetwork>"
        assertEquals(alreadyRedacted, TelemetryFileExporter.sanitizeXmlSnippet(alreadyRedacted))
    }

    @Test
    fun networkClientReportsOfflineSoapFailureCauseAndProvisionerAbortsUnreachableApAfterFirstAttempt() = runBlocking {
        // 1) HTTP 500 SOAP fault surfaces exact ":port=HTTP_500" failure cause in SoapLogEntry.statusText
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { faultServer ->
            val faultPort = faultServer.localPort
            val serverThread = kotlin.concurrent.thread(isDaemon = true) {
                repeat(4) {
                    runCatching {
                        faultServer.accept().use { sock ->
                            sock.soTimeout = 1000
                            val input = sock.getInputStream()
                            val reqBuf = StringBuilder()
                            val chunk = ByteArray(2048)
                            var bodyStart = -1
                            var contentLength = 0
                            while (reqBuf.length < 8192) {
                                val n = input.read(chunk)
                                if (n <= 0) break
                                reqBuf.append(String(chunk, 0, n, Charsets.ISO_8859_1))
                                if (bodyStart < 0) {
                                    val idx = reqBuf.indexOf("\r\n\r\n")
                                    if (idx >= 0) {
                                        bodyStart = idx + 4
                                        contentLength = Regex("(?i)Content-Length:\\s*(\\d+)")
                                            .find(reqBuf.substring(0, idx))?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                    }
                                }
                                if (bodyStart >= 0 && reqBuf.length >= bodyStart + contentLength) break
                            }
                            val body = "<s:Fault><faultstring>UPnPError 501</faultstring></s:Fault>"
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            val resp = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            sock.getOutputStream().write(resp.toByteArray(Charsets.US_ASCII))
                            sock.getOutputStream().write(bytes)
                            sock.getOutputStream().flush()
                            runCatching { sock.shutdownOutput() }
                        }
                    }
                }
            }
            val client = NetworkClient(extraCandidatePorts = emptyList())
            val faultDevice = DeviceEntity(
                id = 88,
                name = "Faulty Switch",
                ipAddress = "127.0.0.1",
                port = faultPort,
                serialNumber = "WLS040FAULT88"
            )
            val faultResult = client.setBinaryState(faultDevice, targetOn = true)
            serverThread.join(1_000L)
            assertFalse(faultResult.reachableOnLan)
            assertTrue(
                "Expected HTTP_500 failure cause in statusText, got: ${faultResult.logEntry.statusText}",
                faultResult.logEntry.statusText.contains("Offline • Unreachable on LAN") &&
                    faultResult.logEntry.statusText.contains(":$faultPort=HTTP_500")
            )
        }

        // 2) Closed port surfaces ConnectException + 9-port probe failure cause, and ProvisionerClient stops after SEND #1
        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val client = NetworkClient(extraCandidatePorts = emptyList())
        val closedDevice = DeviceEntity(
            id = 89,
            name = "Unplugged Switch",
            ipAddress = "127.0.0.1",
            port = closedPort,
            serialNumber = "WLS040CLOSED89"
        )
        val closedResult = client.setBinaryState(closedDevice, targetOn = true)
        assertFalse(closedResult.reachableOnLan)
        assertTrue(
            "Expected ConnectException + 9-port probe cause, got: ${closedResult.logEntry.statusText}",
            closedResult.logEntry.statusText.contains("ConnectException") &&
                closedResult.logEntry.statusText.contains("9-port probe: no match")
        )

        val provisioner = ProvisionerClient(defaultApHost = "127.0.0.1")
        val provResult = provisioner.provisionDeviceToHomeWifi(
            apHost = "127.0.0.1",
            apPort = closedPort,
            ssid = "HeadFamily-2.4G",
            plainPassword = "ValidHomePassword24",
            maxStatusPolls = 1,
            statusPollDelayMs = 10L
        )
        assertFalse(provResult.success)
        assertTrue(provResult.debugTraceLines.any { it.contains("[STEP 5 SEND #1] ERROR:") })
        assertFalse(
            "Unreachable Setup AP (descriptor == null) must break after attempt #1",
            provResult.debugTraceLines.any { it.contains("[STEP 5 SEND #2]") }
        )
    }

    @Test
    fun provisionDebugPanelClassifierAndAutoExpandRecoveryAfterManualHide() {
        // 1. Healthy/in-progress messages must never false-positive (e.g., "Entertainment Center" containing "enter")
        assertFalse(isProvisionStatusWarningOrError(null))
        assertFalse(
            isProvisionStatusWarningOrError(
                "Detected Entertainment Center (WLS040 • Port 49152 • Auto Method 2) • Found 3 nearby 2.4 GHz Wi-Fi network(s)"
            )
        )
        assertFalse(isProvisionStatusWarningOrError("Provisioning switch on 10.22.22.1 to 'CenterNet'..."))
        assertFalse(
            isProvisionStatusWarningOrError(
                "Provisioned 'Error Lamp' to 'CenterNet' (Status=1, Method 2) • Added to switches"
            )
        )

        // 2. Real setup failures and validation prompts must classify as warning/error
        assertTrue(isProvisionStatusWarningOrError("No response from Setup AP at 10.22.22.1 (:49152=TCP_TIMEOUT)"))
        assertTrue(isProvisionStatusWarningOrError("Password rejected by switch (Status=2): Wi-Fi password must be at least 8 characters"))
        assertTrue(isProvisionStatusWarningOrError("Switch did not join 'MyHome' (NetworkStatus=0 after polling)"))
        assertTrue(isProvisionStatusWarningOrError("Setup AP (10.22.22.1:49152) unreachable — connect phone Wi-Fi first"))
        assertTrue(isProvisionStatusWarningOrError("Please enter a valid 2.4 GHz Wi-Fi SSID"))

        // 3. ProvisionDebugPanel stays compact while idle, toggles via Details/Hide, and auto-re-expands on new errors/traces
        var snapshot by mutableStateOf(SetupDebugSnapshot(timestamp = "9:00:00 AM", phoneLocalIp = "10.22.22.2"))
        var status by mutableStateOf<String?>(
            "Detected Entertainment Center (WLS040 • Port 49152) • Found 2 nearby 2.4 GHz Wi-Fi network(s)"
        )
        composeTestRule.setContent {
            ProvisionDebugPanel(
                debugSnapshot = snapshot,
                selectedSsid = "CenterNet",
                keepOpenOnSuccess = false,
                hasWarningOrError = isProvisionStatusWarningOrError(status),
                statusMessage = status,
                onToggleKeepOpen = {}
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provision_debug_text").assertDoesNotExist()

        // User expands Details and then clicks Hide while idle
        composeTestRule.onNodeWithTag("toggle_provision_debug_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provision_debug_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_provision_debug_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provision_debug_text").assertDoesNotExist()

        // New provisioning failure must auto-re-expand despite prior Hide
        composeTestRule.runOnIdle {
            status = "Password or auth rejected by switch (Status=2, Method=2, lengths=false)."
            snapshot = snapshot.copy(
                timestamp = "9:00:15 AM",
                traceLines = listOf("[POLL #1] GetNetworkStatus -> Status=2 (14ms)", "[STEP 6 HOLD] Skipping CloseSetup")
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provision_debug_text").assertIsDisplayed()

        // User can manually Hide the displayed failure without the UI fighting them
        composeTestRule.onNodeWithTag("toggle_provision_debug_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("provision_debug_text").assertDoesNotExist()
    }
}
