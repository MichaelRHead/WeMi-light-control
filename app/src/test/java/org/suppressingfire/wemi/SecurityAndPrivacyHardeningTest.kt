package org.suppressingfire.wemi

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.concurrent.thread
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
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesDbCodec
import org.suppressingfire.wemi.network.HardwareRulesDbMutator
import org.suppressingfire.wemi.network.HardwareRulesSafetyValidator
import org.suppressingfire.wemi.network.HardwareRulesSchemaGuard
import org.suppressingfire.wemi.network.LanEndpointPolicy
import org.suppressingfire.wemi.network.LanHttpClient
import org.suppressingfire.wemi.network.PasswordCrypto
import org.suppressingfire.wemi.network.SharedSoapLogStore
import org.suppressingfire.wemi.network.TelemetryFileExporter
import org.suppressingfire.wemi.network.XmlAndSkuParser
import org.suppressingfire.wemi.widget.SwitchWidgetConfigureActivity
import org.suppressingfire.wemi.widget.WeMiSwitchWidgetProvider
import org.suppressingfire.wemi.widget.WeMiWidgetActionReceiver
import org.suppressingfire.wemi.widget.WidgetActionExecutor
import org.suppressingfire.wemi.widget.WidgetConfigStore
import org.suppressingfire.wemi.widget.WidgetTargetConfig
import org.suppressingfire.wemi.widget.WidgetTargetType
import org.w3c.dom.Element

/**
 * Regression test suite verifying all Security & Privacy hardening invariants (`SEC-01`..`SEC-07`,
 * `PRIV-01`..`PRIV-02`, and `CI-01`..`CI-02`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndPrivacyHardeningTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WeMiSwitchWidgetProvider.cancelAndJoinPendingWorkForTests()
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WeMiWidgetActionReceiver.clearPendingPinNoncesForTests()
        WidgetConfigStore.clearAll(context)
        WidgetActionExecutor.clearAllWidgetMutexes()
        SharedSoapLogStore.clear(context)
        TelemetryFileExporter.deleteTelemetryFile(context)
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        WeMiSwitchWidgetProvider.cancelAndJoinPendingWorkForTests()
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WeMiWidgetActionReceiver.clearPendingPinNoncesForTests()
        WidgetConfigStore.clearAll(context)
        WidgetActionExecutor.clearAllWidgetMutexes()
        SharedSoapLogStore.clear(context)
        TelemetryFileExporter.deleteTelemetryFile(context)
        SwitchDatabase.closeCurrentInstance()
    }

    private fun repoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "LICENSE").exists()) cwd else (cwd.parentFile ?: cwd)
    }

    @Test
    fun sec01AndSec07aWidgetReceiverIsolationPinNoncesAndConfigureOwnership() {
        // 1. Exported WeMiSwitchWidgetProvider must ignore spoofed toggle and pin broadcasts
        val provider = WeMiSwitchWidgetProvider()
        var runnerInvoked = false
        WeMiSwitchWidgetProvider.actionRunnerForTests = { runnerInvoked = true }
        provider.onReceive(
            context,
            Intent(WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 401)
                putExtra(WeMiWidgetActionReceiver.EXTRA_PIN_NONCE, WeMiWidgetActionReceiver.issuePendingPinNonce())
                putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, WidgetTargetType.ROOM.name)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, "Living Room")
            }
        )
        assertNull("Exported AppWidgetProvider must ignore ACTION_WIDGET_PINNED", WidgetConfigStore.load(context, 401))
        provider.onReceive(
            context,
            Intent(WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 401)
            }
        )
        assertFalse("Exported AppWidgetProvider must ignore ACTION_TOGGLE_WIDGET", runnerInvoked)

        // 2. Private WeMiWidgetActionReceiver enforces single-use pin nonce and active widget ID
        val shadowWidgetMgr = Shadows.shadowOf(AppWidgetManager.getInstance(context))
        val providerComponent = ComponentName(context, WeMiSwitchWidgetProvider::class.java)
        shadowWidgetMgr.bindAppWidgetId(401, providerComponent)
        val receiver = WeMiWidgetActionReceiver()

        // Missing or forged nonce must be rejected
        receiver.onReceive(
            context,
            Intent(WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 401)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, WidgetTargetType.ROOM.name)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, "Porch")
            }
        )
        assertNull("Missing pin nonce must be rejected", WidgetConfigStore.load(context, 401))

        // Valid nonce succeeds once, then replay is rejected
        val nonce = WeMiWidgetActionReceiver.issuePendingPinNonce()
        receiver.onReceive(
            context,
            Intent(WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 401)
                putExtra(WeMiWidgetActionReceiver.EXTRA_PIN_NONCE, nonce)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, WidgetTargetType.ROOM.name)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, "Porch")
            }
        )
        assertEquals("Porch", WidgetConfigStore.load(context, 401)?.normalizedRoomName())

        receiver.onReceive(
            context,
            Intent(WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 401)
                putExtra(WeMiWidgetActionReceiver.EXTRA_PIN_NONCE, nonce)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, WidgetTargetType.ROOM.name)
                putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, "Kitchen")
            }
        )
        assertEquals("Replayed pin nonce must not overwrite widget binding", "Porch", WidgetConfigStore.load(context, 401)?.normalizedRoomName())

        // Unbound widget ID 999 must be rejected for toggle & configure activity
        runnerInvoked = false
        receiver.onReceive(
            context,
            Intent(WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 999)
            }
        )
        assertFalse("Unbound widget ID must not execute toggle", runnerInvoked)

        val rogueCfg = Robolectric.buildActivity(
            SwitchWidgetConfigureActivity::class.java,
            Intent(context, SwitchWidgetConfigureActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 999)
            }
        ).create()
        assertTrue("Configure activity must reject unowned widget ID when active widgets exist", rogueCfg.get().isFinishing)
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(rogueCfg.get()).resultCode)
        rogueCfg.destroy()

        // 3. Manifest declares WeMiWidgetActionReceiver as exported=false
        val manifestDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(repoRoot(), "app/src/main/AndroidManifest.xml"))
        val receivers = manifestDoc.getElementsByTagName("receiver")
        val privateReceiver = (0 until receivers.length).mapNotNull { receivers.item(it) as? Element }
            .firstOrNull { it.getAttribute("android:name").endsWith("WeMiWidgetActionReceiver") }
        assertNotNull("WeMiWidgetActionReceiver must be declared in AndroidManifest.xml", privateReceiver)
        assertEquals("false", privateReceiver?.getAttribute("android:exported"))
    }

    @Test
    fun sec02Sec06Sec07cLanEndpointPolicySsrfCrlfHeaderFloodAndPortlessUrl() {
        // 1. Strict RFC 1918 allowlist (loopback disabled for production validation)
        assertTrue(LanEndpointPolicy.isAllowedLanHost("10.22.22.1", allowLoopback = false))
        assertTrue(LanEndpointPolicy.isAllowedLanHost("192.168.86.42", allowLoopback = false))
        assertTrue(LanEndpointPolicy.isAllowedLanHost("172.16.0.9", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanHost("127.0.0.1", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanHost("169.254.169.254", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanHost("8.8.8.8", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanHost("evil.example.com", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanHost("010.0.0.1", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanUrl("https://192.168.1.10/setup.xml", allowLoopback = false))
        assertFalse(LanEndpointPolicy.isAllowedLanUrl("http://user:pass@192.168.1.10/setup.xml", allowLoopback = false))

        // 2. LanHttpClient blocks SSRF and CRLF injection before opening any socket
        val client = LanHttpClient(connectTimeoutMs = 300, readTimeoutMs = 400, allowLoopbackForTests = false)
        listOf(
            "http://169.254.169.254/latest/meta-data/",
            "http://8.8.8.8:80/setup.xml",
            "http://127.0.0.1:49153/setup.xml",
            "http://192.168.1.10:49153/setup.xml\r\nX-Injected: yes"
        ).forEach { badUrl ->
            assertTrue("Expected IllegalArgumentException for $badUrl", runCatching { client.get(badUrl) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(
            runCatching {
                client.execute("GET", "http://192.168.1.10:49153/setup.xml", headers = mapOf("X-Test" to "a\r\nEvil: 1"))
            }.exceptionOrNull() is IllegalArgumentException
        )

        // 3. LanHttpClient rejects header floods (> 64 headers) and oversized Content-Length
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { ss ->
            val t = thread(isDaemon = true) {
                repeat(2) { idx ->
                    runCatching {
                        ss.accept().use { sock ->
                            sock.getInputStream().read(ByteArray(512))
                            val resp = if (idx == 0) {
                                buildString {
                                    append("HTTP/1.1 200 OK\r\n")
                                    repeat(80) { i -> append("X-Flood-$i: value\r\n") }
                                    append("Content-Length: 0\r\n\r\n")
                                }
                            } else {
                                "HTTP/1.1 200 OK\r\nContent-Length: 99999999\r\nConnection: close\r\n\r\n"
                            }
                            sock.getOutputStream().write(resp.toByteArray(Charsets.US_ASCII))
                            sock.getOutputStream().flush()
                        }
                    }
                }
            }
            val loopbackClient = LanHttpClient(connectTimeoutMs = 400, readTimeoutMs = 400, allowLoopbackForTests = true)
            assertTrue(runCatching { loopbackClient.get("http://127.0.0.1:${ss.localPort}/flood") }.exceptionOrNull() is IOException)
            assertTrue(runCatching { loopbackClient.getBytes("http://127.0.0.1:${ss.localPort}/huge") }.exceptionOrNull() is IOException)
            t.join(1500)
        }

        // 4. SSDP M-SEARCH CRLF sanitization + port-less LOCATION URL parsing (SEC-07c)
        val packet = XmlAndSkuParser.buildSsdpMSearchPacket("urn:Belkin:service:basicevent:1\r\nX-Injected: bad")
        assertFalse(packet.contains("X-Injected"))
        val parsed = XmlAndSkuParser.parseSetupXmlDescriptor(
            xml = "<root><device><friendlyName>Porch</friendlyName><serialNumber>SN12\r\n/../BAD</serialNumber></device></root>",
            locationUrl = "http://192.168.1.50/setup.xml",
            usnHeader = "",
            stHeader = XmlAndSkuParser.URN_BASIC_EVENT,
            responseTimeMs = 5L,
            isLiveUdp = true
        )
        assertEquals("192.168.1.50", parsed.ipAddress)
        assertEquals(80, parsed.port)
        assertFalse(parsed.serialNumber.contains("\r") || parsed.serialNumber.contains("/"))
    }

    @Test
    fun sec03AndSec07bUntrustedSqliteTriggersViewsTableNameAndZipBombGuard() {
        val dbFile = File.createTempFile("untrusted_rules_", ".db", context.cacheDir)
        try {
            HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(dbFile)
            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("CREATE TRIGGER evil_trig AFTER INSERT ON RULES BEGIN SELECT 1; END;")
            }

            // Read-only codec, mutator, and validator (even isRawBackupRestore=true) MUST reject triggers
            assertTrue(
                runCatching {
                    HardwareRulesDbCodec.parseSqliteFileReadOnly(dbFile, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, "")
                }.exceptionOrNull() is SecurityException
            )
            assertTrue(
                runCatching {
                    HardwareRulesDbMutator.upsertRule(
                        dbFile,
                        "uuid:Lightswitch-3_0-SN1",
                        HardwareRuleDraft(name = "Test", type = HardwareRuleType.COUNTDOWN_TIMER, countdownMinutes = 5)
                    )
                }.exceptionOrNull() is SecurityException
            )
            val restoreVerdict = HardwareRulesSafetyValidator.validateSqliteDbForStore(dbFile, isRawBackupRestore = true)
            assertFalse("Raw backup restore must still reject untrusted SQLite triggers", restoreVerdict.isSafeToStore)
            assertTrue(restoreVerdict.issues.any { it.contains("trigger", ignoreCase = true) })

            // Table identifier injection must fail closed
            assertTrue(
                runCatching { HardwareRulesSchemaGuard.validateTableIdentifier("RULES); DROP TABLE RULES; --") }
                    .exceptionOrNull() is IllegalArgumentException
            )

            // ZIP with > 32 directory entries must be rejected cleanly (SEC-07b)
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                repeat(40) { i ->
                    zos.putNextEntry(ZipEntry("dir_$i/"))
                    zos.closeEntry()
                }
                zos.putNextEntry(ZipEntry("temppluginRules.db"))
                zos.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
                zos.closeEntry()
            }
            assertNull(HardwareRulesDbCodec.extractSingleSqliteEntry(baos.toByteArray()))
        } finally {
            runCatching { dbFile.delete() }
            HardwareRulesDbMutator.cleanupSqliteSidecars(dbFile)
        }
    }

    @Test
    fun sec04GenaRejectsSpoofedNotifyWrongTokenWrongSidAndWrongMethod() = runBlocking {
        val received = CopyOnWriteArrayList<GenaEventNotification>()
        val deliveredLatch = java.util.concurrent.CountDownLatch(1)
        val manager = GenaSubscriptionManager(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            onNotification = {
                received.add(it)
                deliveredLatch.countDown()
            }
        )
        FakeUpnpServer(initialFriendlyName = "Hallway Switch", serialNumber = "WLS040SEC04").use { server ->
            try {
                val sub = manager.subscribeOrRenew(
                    DeviceEntity(id = 1, name = "Hallway Switch", ipAddress = "127.0.0.1", port = server.port, serialNumber = "WLS040SEC04")
                )
                assertNotNull(sub)
                assertEquals(24, sub!!.callbackToken.length)
                val port = manager.activeListenPort

                // 1. Missing token (legacy path) -> 412 Precondition Failed, 0 events
                val noTokenResp = server.pushGenaNotify(
                    callbackPort = port, deviceKey = "WLS040SEC04", sid = sub.sid, callbackPathOverride = "/gena/WLS040SEC04"
                )
                assertTrue("Expected 412 for missing token, got $noTokenResp", noTokenResp.contains("412"))

                // 2. Wrong token -> 412
                val wrongTokenResp = server.pushGenaNotify(
                    callbackPort = port, deviceKey = "WLS040SEC04", sid = sub.sid, callbackPathOverride = "/gena/WLS040SEC04/000000000000000000000000"
                )
                assertTrue("Expected 412 for wrong token, got $wrongTokenResp", wrongTokenResp.contains("412"))

                // 3. Correct token but wrong SID -> 412
                val wrongSidResp = server.pushGenaNotify(
                    callbackPort = port, deviceKey = "WLS040SEC04", sid = "uuid:forged-sid"
                )
                assertTrue("Expected 412 for wrong SID, got $wrongSidResp", wrongSidResp.contains("412"))

                // 4. Non-NOTIFY HTTP verb -> 405
                Socket("127.0.0.1", port).use { s ->
                    s.getOutputStream().write("GET /gena/WLS040SEC04 HTTP/1.1\r\nHost: 127.0.0.1:$port\r\n\r\n".toByteArray())
                    s.getOutputStream().flush()
                    val line = s.getInputStream().bufferedReader().readLine().orEmpty()
                    assertTrue("Expected 405 for GET, got $line", line.contains("405"))
                }
                assertFalse("Spoofed GENA probes must never emit notifications", deliveredLatch.await(80, java.util.concurrent.TimeUnit.MILLISECONDS))
                assertTrue("Spoofed GENA probes must never emit notifications", received.isEmpty())

                // 5. Authentic NOTIFY (captured token + matching SID + matching source IP) -> 200 OK
                val okResp = server.pushGenaNotify(callbackPort = port, deviceKey = "WLS040SEC04", binaryStatePayload = "1", sid = sub.sid)
                assertTrue("Expected 200 OK for authentic NOTIFY, got $okResp", okResp.contains("200 OK"))
                assertTrue("Expected authentic NOTIFY callback", deliveredLatch.await(2, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(1, received.size)
                assertEquals(true, received.single().binaryStateOn)
            } finally {
                manager.close()
            }
        }
    }

    @Test
    fun sec05Priv01Priv02Sec07dAndCiSupplyChainInvariants() = runBlocking {
        // 1. PasswordCrypto round-trip across Methods 1, 2, 3 after buffer zeroing (SEC-05)
        listOf(1, 2, 3).forEach { method ->
            val enc = PasswordCrypto.encryptWifiPassword("MyWifiSecret!24", "EC1A59AA1122", "221840K01088", method)
            assertEquals("MyWifiSecret!24", PasswordCrypto.decryptWifiPassword(enc, "EC1A59AA1122", "221840K01088", method))
        }

        // 2. Strict local-only cloud Auto Backup exclusions in backup_rules.xml & data_extraction_rules.xml (PRIV-01)
        val dbf = DocumentBuilderFactory.newInstance()
        fun excludedPairs(el: Element): Set<Pair<String, String>> {
            val nodes = el.getElementsByTagName("exclude")
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                .map { it.getAttribute("domain").trim() to it.getAttribute("path").trim().trimEnd('/') }.toSet()
        }
        val backupXml = dbf.newDocumentBuilder().parse(File(repoRoot(), "app/src/main/res/xml/backup_rules.xml")).documentElement
        assertTrue(
            excludedPairs(backupXml).containsAll(
                setOf("database" to ".", "sharedpref" to ".", "file" to "wemi-telemetry-latest.txt", "file" to "hardware_rules_backups")
            )
        )
        val extractXml = dbf.newDocumentBuilder().parse(File(repoRoot(), "app/src/main/res/xml/data_extraction_rules.xml"))
        val cloudBackup = extractXml.getElementsByTagName("cloud-backup").item(0) as Element
        assertTrue(
            excludedPairs(cloudBackup).containsAll(
                setOf("database" to ".", "sharedpref" to ".", "file" to "wemi-telemetry-latest.txt", "file" to "hardware_rules_backups")
            )
        )
        val deviceTransfer = extractXml.getElementsByTagName("device-transfer").item(0) as Element
        val transferExcludes = excludedPairs(deviceTransfer)
        assertTrue(transferExcludes.containsAll(setOf("file" to "wemi-telemetry-latest.txt", "file" to "hardware_rules_backups")))
        assertFalse("Direct cable transfer should preserve Room DB", ("database" to ".") in transferExcludes)

        // 3. Save/Share does not touch clipboard; explicit Copy sets EXTRA_IS_SENSITIVE=true (PRIV-02a)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.clearPrimaryClip()
        TelemetryFileExporter.saveAndShareTelemetryReport(context, "safe-report-body")
        assertFalse("Save/Share must not silently write to clipboard", clipboard.hasPrimaryClip())
        TelemetryFileExporter.copyTelemetryReportToClipboard(context, "explicit-copy-body")
        assertTrue(clipboard.hasPrimaryClip())
        assertEquals(true, clipboard.primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))

        // 4. ReDoS guard on large pathological unclosed tag in sanitizeXmlSnippet (SEC-07d)
        val hugeInput = "&lt;ruleDbBody&gt;" + "A".repeat(40_000)
        val startMs = System.currentTimeMillis()
        val sanitized = TelemetryFileExporter.sanitizeXmlSnippet(hugeInput)
        val elapsedMs = System.currentTimeMillis() - startMs
        assertTrue("Bounded regex sanitization should finish quickly (took ${elapsedMs}ms)", elapsedMs < 500L)
        assertTrue(sanitized.contains("[REDACTED base64Len="))

        // 5. Reset deletes telemetry file, SOAP logs, and Home Screen widget bindings (PRIV-02b)
        val telemetryFile = TelemetryFileExporter.saveTelemetryReportToFile(context, "old-log")
        assertTrue(telemetryFile.exists())
        SharedSoapLogStore.append(
            context,
            SoapLogEntry(
                timestamp = "9:00 AM",
                deviceName = "Porch",
                endpoint = "http://192.168.1.5",
                soapAction = "GetBinaryState",
                statusText = "OK",
                xmlSnippet = "",
                isLiveLanSuccess = true
            )
        )
        WidgetConfigStore.save(context, WidgetTargetConfig.forRoom(888, "Porch", DEFAULT_MAIN_HOME_KEY))
        val repo = SwitchRepository(SwitchDatabase.getDatabase(context).switchDao(), context = context, initialDemoModeOverride = false)
        try {
            repo.resetLanHomesSwitchesAndRooms()
            assertFalse("Reset must delete wemi-telemetry-latest.txt", telemetryFile.exists())
            assertTrue("Reset must clear SharedSoapLogStore", SharedSoapLogStore.flowFor(context).value.isEmpty())
            assertTrue("Reset must erase WidgetConfigStore bindings", WidgetConfigStore.allConfiguredWidgetIds(context).isEmpty())
        } finally {
            repo.close()
        }

        // 6. CI/CD & build supply-chain invariants (CI-01 & CI-02)
        val workflow = File(repoRoot(), ".github/workflows/play-store-promote.yml").readText()
        assertTrue(Regex("actions/checkout@[0-9a-f]{40}").containsMatchIn(workflow))
        assertTrue(Regex("actions/setup-python@[0-9a-f]{40}").containsMatchIn(workflow))
        assertTrue(workflow.contains("--require-hashes -r scripts/requirements-play.txt"))
        assertTrue(File(repoRoot(), "scripts/requirements-play.txt").isFile)
        val wrapper = File(repoRoot(), "gradle/wrapper/gradle-wrapper.properties").readText()
        assertTrue(wrapper.contains("distributionSha256Sum=") && wrapper.contains("validateDistributionUrl=true"))
        val proguard = File(repoRoot(), "app/proguard-rules.pro").readText()
        assertTrue(proguard.contains("-assumenosideeffects class android.util.Log"))
    }
}
