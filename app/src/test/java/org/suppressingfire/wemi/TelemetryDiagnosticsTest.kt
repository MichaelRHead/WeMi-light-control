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
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import javax.xml.parsers.DocumentBuilderFactory
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
import org.suppressingfire.wemi.network.ActiveLanBinding
import org.suppressingfire.wemi.network.DeviceHardwareRulesSnapshot
import org.suppressingfire.wemi.network.HardwareDeviceTimeInfo
import org.suppressingfire.wemi.network.HardwareLocationInfo
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesBackupEntry
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.LanNetworkSnapshot
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.NetworkMonitor
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
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelemetryDiagnosticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        NetworkMonitor.resetTestOverrides()
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
        NetworkMonitor.resetTestOverrides()
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
            initialFriendlyName = "Porch Lantern", modelName = "WLS040",
            serialNumber = "WLS040TELEM01", udn = "uuid:Lightswitch-3_0-WLS040TELEM01", initialRelayOn = false
        ).use { server ->
            val dao = SwitchDatabase.getDatabase(context).switchDao()
            val appRepo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
            try {
                appRepo.setDemoShowroomMode(false)
                val saved = appRepo.saveDevice(
                    DeviceEntity(
                        name = "Porch Lantern", roomName = "Porch", networkKey = DEFAULT_MAIN_HOME_KEY,
                        deviceType = DeviceType.SWITCH_PLUG.name, ipAddress = "127.0.0.1", port = server.port,
                        isOn = false, isReachable = true, serialNumber = "WLS040TELEM01"
                    )
                )
                appRepo.clearLogs()
                assertTrue(appRepo.soapLogs.value.isEmpty())

                WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(501, saved))
                assertTrue(WidgetActionExecutor.toggleWidgetTarget(context, 501))
                assertTrue(server.relayStateOn.get())

                val logsAfterToggle = appRepo.soapLogs.value
                assertTrue(logsAfterToggle.any { it.soapAction.contains("SetBinaryState(1)") && it.isLiveLanSuccess })
                assertTrue(
                    logsAfterToggle.any {
                        it.soapAction == "Widget#Toggle(DEVICE)" && it.endpoint == "widget://501" && it.statusText.contains("-> ON")
                    }
                )

                // Unconfigured & orphaned device/room widget taps emit diagnostic entries
                assertFalse(WidgetActionExecutor.toggleWidgetTarget(context, 599))
                WidgetConfigStore.save(
                    context,
                    WidgetTargetConfig(
                        appWidgetId = 502, targetType = org.suppressingfire.wemi.widget.WidgetTargetType.DEVICE,
                        deviceId = 9999, serialNumber = "WLS040MISSING", roomName = "Garage",
                        networkKey = DEFAULT_MAIN_HOME_KEY, displayName = "Deleted Garage Light",
                        udn = "uuid:Lightswitch-3_0-WLS040MISSING"
                    )
                )
                assertFalse(WidgetActionExecutor.toggleWidgetTarget(context, 502))
                WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(503, "Attic", DEFAULT_MAIN_HOME_KEY))
                assertFalse(WidgetActionExecutor.toggleWidgetTarget(context, 503))

                val allLogs = appRepo.soapLogs.value
                assertTrue(allLogs.any { it.endpoint == "widget://599" && it.statusText.contains("Widget not configured") })
                assertTrue(allLogs.any { it.endpoint == "widget://502" && it.statusText.contains("Orphaned") && it.statusText.contains("udn=uuid:Lightswitch-3_0-WLS040MISSING") })
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
                runCatching { stalledServer.accept().use { sock -> Thread.sleep(600L); sock.close() } }
            }
            val dao = SwitchDatabase.getDatabase(context).switchDao()
            val setupRepo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
            try {
                setupRepo.setDemoShowroomMode(false)
                val insertedId = dao.insertDevice(
                    DeviceEntity(
                        name = "Stalled Patio Switch", roomName = "Patio", networkKey = DEFAULT_MAIN_HOME_KEY,
                        ipAddress = "127.0.0.1", port = stalledServer.localPort,
                        isOn = false, isReachable = true, serialNumber = "WLS040STALL1"
                    )
                ).toInt()
                WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(550, dao.getDeviceById(insertedId)!!))
                SharedSoapLogStore.clear(context)

                WeMiSwitchWidgetProvider.broadcastTimeoutMs = 80L
                org.robolectric.Shadows.shadowOf(AppWidgetManager.getInstance(context)).bindAppWidgetId(
                    550,
                    android.content.ComponentName(context, WeMiSwitchWidgetProvider::class.java)
                )
                org.suppressingfire.wemi.widget.WeMiWidgetActionReceiver().onReceive(
                    context,
                    Intent(WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 550)
                    }
                )

                var sawTimeout = false
                repeat(30) {
                    if (SharedSoapLogStore.flowFor(context).value.any {
                            it.soapAction == "Widget#Toggle(550)" && it.statusText.contains("Timeout • Exceeded 80ms broadcast budget")
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
    fun telemetryExporterRendersSection3RulesSummarySection5WidgetsAndBackupExclusions() {
        val home = HomeNetworkEntity(DEFAULT_MAIN_HOME_KEY, "Main Home", "HeadFamily-2.4G", "192.168.86", "192.168.86.1")
        val switchOn = DeviceEntity(
            id = 11, name = "Patio String Lights", roomName = "Patio", networkKey = DEFAULT_MAIN_HOME_KEY,
            ipAddress = "192.168.86.21", port = 49153, isOn = true, isReachable = true, serialNumber = "WLS040PAT11",
            hardwareRulesCapability = HardwareRulesCapability.VERIFIED_SUPPORTED.name,
            hardwareRulesLongPressCapable = true, hardwareRulesVerifiedFirmware = "WeMo_WW_2.00.11532.PVT"
        )
        val unassignedSwitch = DeviceEntity(
            id = 12, name = "Mystery Plug", roomName = "", networkKey = DEFAULT_MAIN_HOME_KEY,
            ipAddress = "192.168.86.22", port = 49153, isOn = false, isReachable = true,
            serialNumber = "WSP080MYS12", udn = "uuid:Socket-1_0-WSP080MYS12"
        )
        val patioOffline = DeviceEntity(
            id = 13, name = "Patio Floodlight", roomName = "Patio", networkKey = DEFAULT_MAIN_HOME_KEY,
            ipAddress = "192.168.86.23", port = 49153, isOn = false, isReachable = false, serialNumber = "WLS040PAT13"
        )
        val rulesSnapshots = mapOf(
            11 to DeviceHardwareRulesSnapshot(
                deviceId = 11, deviceName = "Patio String Lights", deviceUdn = "uuid:Lightswitch-3_0-WLS040PAT11",
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = true, dbVersion = 7,
                rules = listOf(HardwareRule(1, "Sunset", HardwareRuleType.SCHEDULE_INTERVAL, "Time Interval", true, 1, "uuid:Lightswitch-3_0-WLS040PAT11", listOf(1, 2, 3, 4, 5, 6, 7), null, null, 0, 1.0, 0.0, 0, 0)),
                locationInfo = HardwareLocationInfo("Mountain View", "CA", "United States", "37.3861", "-122.0839"),
                deviceTimeInfo = HardwareDeviceTimeInfo(1_700_000_000L, driftSeconds = 185L, needsTimeSync = true),
                integrityOk = true, availableBackups = listOf(HardwareRulesBackupEntry("/tmp/b1.zip", 6, "Nov 26", 4096L))
            ),
            12 to DeviceHardwareRulesSnapshot(
                deviceId = 12, deviceName = "Mystery Plug", deviceUdn = "uuid:Socket-1_0-WSP080MYS12",
                capability = HardwareRulesCapability.VERIFIED_SUPPORTED, longPressCapable = false, dbVersion = 0, locationInfo = null
            )
        )

        WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(603, switchOn.copy(id = 999, serialNumber = "WLS040GONE")))
        WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(601, switchOn))
        WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(602, "Patio", DEFAULT_MAIN_HOME_KEY))
        WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(604, unassignedSwitch))

        val loadedWidgets = WidgetConfigStore.loadAll(context)
        assertEquals(listOf(601, 602, 603, 604), loadedWidgets.map { it.appWidgetId })

        val redactedDefault = TelemetryFileExporter.buildFullTelemetryReport(
            setupSnapshot = SetupDebugSnapshot(timestamp = "9:15:00 AM", phoneLocalIp = "192.168.86.10"),
            homes = listOf(home), devices = listOf(switchOn, unassignedSwitch, patioOffline),
            widgets = loadedWidgets, hardwareRulesByDeviceId = rulesSnapshots
        )
        assertFalse("Default report must redact home SSID", redactedDefault.contains("HeadFamily-2.4G"))
        assertFalse("Default report must redact full serial", redactedDefault.contains("WLS040PAT11"))
        assertTrue(redactedDefault.contains("sn=***AT11") && redactedDefault.contains("udn=uuid:Socket-1_0-***YS12"))

        val report = TelemetryFileExporter.buildFullTelemetryReport(
            setupSnapshot = SetupDebugSnapshot(timestamp = "9:15:00 AM", phoneLocalIp = "192.168.86.10"),
            homes = listOf(home), devices = listOf(switchOn, unassignedSwitch, patioOffline),
            widgets = loadedWidgets, hardwareRulesByDeviceId = rulesSnapshots,
            includeSensitiveIdentifiers = true
        )

        assertTrue(report.contains("[DEV #11]"))
        assertTrue(report.contains("rulesCap=VERIFIED_SUPPORTED lp=1 rulesFw=WeMo_WW_2.00.11532.PVT"))
        assertTrue(report.contains("  rules: dbV=7 rules=1 integrity=true driftSec=185 needsSync=true backups=1 solarLoc=set"))
        assertTrue(report.contains("  rules: dbV=0 rules=0 integrity=true driftSec=n/a needsSync=unknown backups=0 solarLoc=unset"))
        assertFalse("Exact latitude must never leak into telemetry report", report.contains("37.3861"))
        assertFalse("Exact longitude must never leak into telemetry report", report.contains("-122.0839"))
        assertFalse("Solar city must never leak into telemetry report", report.contains("Mountain View"))
        assertTrue(report.contains("[DEV #12] name=\"Mystery Plug\" room=\"Unassigned\" home=$DEFAULT_MAIN_HOME_KEY"))
        assertTrue(report.contains("--- [5. HOME SCREEN WIDGETS (4)] ---"))
        assertTrue(report.contains("[WIDGET #601] type=DEVICE name=\"Patio String Lights\" devId=11 sn=WLS040PAT11 room=\"Patio\" home=$DEFAULT_MAIN_HOME_KEY -> resolved=#11 \"Patio String Lights\" (ON)"))
        assertTrue(report.contains("[WIDGET #602] type=ROOM room=\"Patio\" home=$DEFAULT_MAIN_HOME_KEY -> resolved=1/2 ON (1/2 reachable)"))
        assertTrue(report.contains("[WIDGET #603] type=DEVICE name=\"Patio String Lights\" devId=999 sn=WLS040GONE room=\"Patio\" home=$DEFAULT_MAIN_HOME_KEY -> ORPHANED (no matching switch)"))
        assertTrue(report.contains("[WIDGET #604] type=DEVICE name=\"Mystery Plug\" devId=12 sn=WSP080MYS12 udn=uuid:Socket-1_0-WSP080MYS12 room=\"Unassigned\" home=$DEFAULT_MAIN_HOME_KEY -> resolved=#12 \"Mystery Plug\" (OFF_STANDBY)"))

        // Verify hardware_rules_backups and wemi-telemetry-latest.txt are excluded in both Android backup XML manifests
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val repoRoot = if (File(cwd, "LICENSE").exists()) cwd else (cwd.parentFile ?: cwd)
        fun excludedPaths(parent: Element): Set<String> {
            val nodes = parent.getElementsByTagName("exclude")
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .filter { it.getAttribute("domain") == "file" }
                .map { it.getAttribute("path").trim().trimEnd('/') }.toSet()
        }
        val dbf = DocumentBuilderFactory.newInstance()
        val backupDoc = dbf.newDocumentBuilder().parse(File(repoRoot, "app/src/main/res/xml/backup_rules.xml"))
        assertTrue(excludedPaths(backupDoc.documentElement).containsAll(setOf("wemi-telemetry-latest.txt", "hardware_rules_backups")))
        val extractDoc = dbf.newDocumentBuilder().parse(File(repoRoot, "app/src/main/res/xml/data_extraction_rules.xml"))
        listOf("cloud-backup", "device-transfer").forEach { tag ->
            val section = extractDoc.getElementsByTagName(tag).item(0) as Element
            assertTrue(excludedPaths(section).containsAll(setOf("wemi-telemetry-latest.txt", "hardware_rules_backups")))
        }
    }

    @Test
    fun arcVmTelemetryRendersCgnatDnsUnsetHomeAndDeduplicatesGenaSkipLog() = runBlocking {
        val arcLan = LanNetworkSnapshot(
            localIpv4 = "100.115.92.6", subnetPrefix = "100.115.92", gatewayIp = "100.115.92.5",
            interfaceName = "eth1", isWifiSocketBound = true,
            dnsServersIpv4 = listOf("192.168.86.1", "8.8.8.8"), isArcVmNatSubnet = true
        )
        val resolvedArc = TelemetryFileExporter.resolveExportSetupSnapshot(SetupDebugSnapshot(), arcLan, isDemoMode = false)
        val unsetHome = HomeNetworkEntity("wifi_lan_chromeos_default", "My Home", "Local LAN", "", "")
        val redactedArcReport = TelemetryFileExporter.buildFullTelemetryReport(setupSnapshot = resolvedArc, homes = listOf(unsetHome))
        assertTrue(redactedArcReport.contains("arcVmNat=true dns=<redacted:2>"))
        assertFalse(redactedArcReport.contains("8.8.8.8"))
        val report = TelemetryFileExporter.buildFullTelemetryReport(
            setupSnapshot = resolvedArc, homes = listOf(unsetHome), includeSensitiveIdentifiers = true
        )
        assertTrue(report.contains("[NET] mode=REAL-LAN ip=100.115.92.6 (eth1) gw=100.115.92.5 setupSubnet=false wifiBound=true arcVmNat=true dns=192.168.86.1,8.8.8.8"))
        assertTrue(report.contains("[HOME] key=wifi_lan_chromeos_default name=\"My Home\" ssid=\"Local LAN\" subnet=unset gw=none"))
        assertFalse(report.contains("subnet=.0/24"))

        val phoneSession = SetupDebugSnapshot(timestamp = "9:10:00 AM", phoneLocalIp = "10.22.22.2", phoneGatewayIp = "10.22.22.1", isWemoSetupSubnet = true, isWifiSocketBound = true)
        val phoneLine = TelemetryFileExporter.resolveExportSetupSnapshot(phoneSession, arcLan, isDemoMode = false).toDisplayLines().first()
        assertFalse(phoneLine.contains("arcVmNat=true") || phoneLine.contains("dns="))

        var activeGuestIp = "100.115.92.6"
        NetworkMonitor.activeLanBindingOverrideForTests = { ActiveLanBinding(null, "eth1", activeGuestIp, listOf("192.168.86.1")) }
        val dao = SwitchDatabase.getDatabase(context).switchDao()
        val repo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
        try {
            repo.setDemoShowroomMode(false)
            dao.insertDevice(DeviceEntity(name = "Desk Switch", networkKey = DEFAULT_MAIN_HOME_KEY, ipAddress = "192.168.86.42", port = 49153, isReachable = true, serialNumber = "SNARC01"))
            repo.clearLogs()
            assertEquals(0, repo.refreshGenaSubscriptionsForHome(DEFAULT_MAIN_HOME_KEY))
            assertEquals(0, repo.refreshGenaSubscriptionsForHome(DEFAULT_MAIN_HOME_KEY))
            val skipLogs = repo.soapLogs.value.filter { it.soapAction == "GENA#SUBSCRIBE (Skipped)" }
            assertEquals(1, skipLogs.size)
            assertTrue(skipLogs.single().statusText.contains("100.115.92.6") && skipLogs.single().isLiveLanSuccess)

            activeGuestIp = "100.115.92.10"
            assertEquals(0, repo.refreshGenaSubscriptionsForHome(DEFAULT_MAIN_HOME_KEY))
            assertEquals(2, repo.soapLogs.value.count { it.soapAction == "GENA#SUBSCRIBE (Skipped)" })
        } finally {
            repo.close()
        }
    }

    @Test
    fun sanitizeXmlSnippetRedactsPasswordsAndRuleDbBodyBlobs() {
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

        val storeRulesXml = "<u:StoreRules><u:ruleDbBody>&lt;![CDATA[UEsDBBQAAAAIRawZipBlob]]&gt;</u:ruleDbBody></u:StoreRules>"
        val cleanRules = TelemetryFileExporter.sanitizeXmlSnippet(storeRulesXml)
        assertFalse(cleanRules.contains("UEsDBBQAAAAIRawZipBlob"))
        assertTrue(cleanRules.contains("<u:ruleDbBody>[REDACTED base64Len="))

        val truncatedRules = "<StoreRules><ruleDbBody><![CDATA[UEsDBTruncatedZipPayload"
        val cleanTruncatedRules = TelemetryFileExporter.sanitizeXmlSnippet(truncatedRules)
        assertFalse(cleanTruncatedRules.contains("UEsDBTruncatedZipPayload"))
        assertTrue(cleanTruncatedRules.contains("<ruleDbBody>[REDACTED base64Len="))

        val escapedRules = "&lt;StoreRules&gt;&lt;ruleDbBody&gt;UEsDBEscapedZip&lt;/ruleDbBody&gt;&lt;/StoreRules&gt;"
        val cleanEscapedRules = TelemetryFileExporter.sanitizeXmlSnippet(escapedRules)
        assertFalse(cleanEscapedRules.contains("UEsDBEscapedZip"))
        assertTrue(cleanEscapedRules.contains("&lt;ruleDbBody&gt;[REDACTED base64Len=15]&lt;/ruleDbBody&gt;"))

        val alreadyRedactedRules = "<StoreRules><ruleDbBody>[REDACTED base64Len=256]</ruleDbBody></StoreRules>"
        assertEquals(alreadyRedactedRules, TelemetryFileExporter.sanitizeXmlSnippet(alreadyRedactedRules))
    }

    @Test
    fun networkClientReportsOfflineSoapFailureCauseAndProvisionerAbortsUnreachableApAfterFirstAttempt() = runBlocking {
        ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).use { faultServer ->
            val faultPort = faultServer.localPort
            val serverThread = kotlin.concurrent.thread(isDaemon = true) {
                repeat(4) {
                    runCatching {
                        faultServer.accept().use { sock ->
                            sock.soTimeout = 1000
                            val reqBuf = StringBuilder()
                            val chunk = ByteArray(2048)
                            var bodyStart = -1; var contentLength = 0
                            while (reqBuf.length < 8192) {
                                val n = sock.getInputStream().read(chunk)
                                if (n <= 0) break
                                reqBuf.append(String(chunk, 0, n, Charsets.ISO_8859_1))
                                if (bodyStart < 0) {
                                    val idx = reqBuf.indexOf("\r\n\r\n")
                                    if (idx >= 0) {
                                        bodyStart = idx + 4
                                        contentLength = Regex("(?i)Content-Length:\\s*(\\d+)").find(reqBuf.substring(0, idx))?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                    }
                                }
                                if (bodyStart >= 0 && reqBuf.length >= bodyStart + contentLength) break
                            }
                            val bytes = "<s:Fault><faultstring>UPnPError 501</faultstring></s:Fault>".toByteArray(Charsets.UTF_8)
                            val resp = "HTTP/1.1 500 Internal Server Error\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                            sock.getOutputStream().apply { write(resp.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
                            runCatching { sock.shutdownOutput() }
                        }
                    }
                }
            }
            val faultResult = NetworkClient(extraCandidatePorts = emptyList()).setBinaryState(
                DeviceEntity(id = 88, name = "Faulty Switch", ipAddress = "127.0.0.1", port = faultPort, serialNumber = "WLS040FAULT88"),
                targetOn = true
            )
            serverThread.join(1_000L)
            assertFalse(faultResult.reachableOnLan)
            assertTrue(faultResult.logEntry.statusText.contains("Offline • Unreachable on LAN") && faultResult.logEntry.statusText.contains(":$faultPort=HTTP_500"))
        }

        val closedPort = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val closedResult = NetworkClient(extraCandidatePorts = emptyList()).setBinaryState(
            DeviceEntity(id = 89, name = "Unplugged Switch", ipAddress = "127.0.0.1", port = closedPort, serialNumber = "WLS040CLOSED89"),
            targetOn = true
        )
        assertFalse(closedResult.reachableOnLan)
        assertTrue(closedResult.logEntry.statusText.contains("ConnectException") && closedResult.logEntry.statusText.contains("9-port probe: no match"))

        val provResult = ProvisionerClient(defaultApHost = "127.0.0.1").provisionDeviceToHomeWifi(
            apHost = "127.0.0.1", apPort = closedPort, ssid = "HeadFamily-2.4G",
            plainPassword = "ValidHomePassword24", maxStatusPolls = 1, statusPollDelayMs = 10L
        )
        assertFalse(provResult.success)
        assertTrue(provResult.debugTraceLines.any { it.contains("[STEP 5 SEND #1] ERROR:") })
        assertFalse(provResult.debugTraceLines.any { it.contains("[STEP 5 SEND #2]") })
    }

    @Test
    fun provisionDebugPanelClassifierAndAutoExpandRecoveryAfterManualHide() {
        // 1. Healthy/in-progress messages must never false-positive (e.g., "Entertainment Center" containing "enter")
        assertFalse(isProvisionStatusWarningOrError(null))
        assertFalse(isProvisionStatusWarningOrError("Detected Entertainment Center (WLS040 • Port 49152 • Auto Method 2) • Found 3 nearby 2.4 GHz Wi-Fi network(s)"))
        assertFalse(isProvisionStatusWarningOrError("Provisioning switch on 10.22.22.1 to 'CenterNet'..."))
        assertFalse(isProvisionStatusWarningOrError("Provisioned 'Error Lamp' to 'CenterNet' (Status=1, Method 2) • Added to switches"))

        // 2. Real setup failures and validation prompts must classify as warning/error
        assertTrue(isProvisionStatusWarningOrError("No response from Setup AP at 10.22.22.1 (:49152=TCP_TIMEOUT)"))
        assertTrue(isProvisionStatusWarningOrError("Password rejected by switch (Status=2): Wi-Fi password must be at least 8 characters"))
        assertTrue(isProvisionStatusWarningOrError("Switch did not join 'MyHome' (NetworkStatus=0 after polling)"))
        assertTrue(isProvisionStatusWarningOrError("Setup AP (10.22.22.1:49152) unreachable — connect phone Wi-Fi first"))
        assertTrue(isProvisionStatusWarningOrError("Please enter a valid 2.4 GHz Wi-Fi SSID"))

        // 3. ProvisionDebugPanel stays compact while idle, toggles via Details/Hide, and auto-re-expands on new errors/traces
        var snapshot by mutableStateOf(SetupDebugSnapshot(timestamp = "9:00:00 AM", phoneLocalIp = "10.22.22.2"))
        var status by mutableStateOf<String?>("Detected Entertainment Center (WLS040 • Port 49152) • Found 2 nearby 2.4 GHz Wi-Fi network(s)")
        composeTestRule.setContent {
            ProvisionDebugPanel(
                debugSnapshot = snapshot, selectedSsid = "CenterNet", keepOpenOnSuccess = false,
                hasWarningOrError = isProvisionStatusWarningOrError(status), statusMessage = status, onToggleKeepOpen = {}
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
