package org.suppressingfire.wemi

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HardwareRulesClient
import org.suppressingfire.wemi.network.HardwareRulesDbCodec
import org.suppressingfire.wemi.network.HardwareRulesProtocol
import org.suppressingfire.wemi.network.HardwareRulesSafetyValidator
import org.suppressingfire.wemi.network.XmlAndSkuParser

/**
 * Unit and loopback integration tests for Step 1 On-Device Hardware Rules:
 * - Zero-RTT Tier-1 capability classification across real OpenWrt and RTOS hardware profiles
 * - Authoritative Tier-2 verification via `/rulesservice.xml` + `FetchRules`
 * - Read-only `rules.db` SQLite-in-ZIP extraction, backup rotation, and human-readable decoding
 * - Anti-brick pre-store validator (`HardwareRulesSafetyValidator`) and armored `StoreRules`
 * - Non-destructive Room v5 -> v6 migration (`SwitchDatabase.MIGRATION_5_6`)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareRulesTest {

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun buildSetupXml(
        model: String,
        firmware: String,
        udn: String,
        isRtos: Boolean,
        includeRulesService: Boolean = true
    ): String {
        val rulesBlock = if (includeRulesService) {
            """
            <service>
              <serviceType>urn:Belkin:service:rules:1</serviceType>
              <controlURL>/upnp/control/rules1</controlURL>
              <SCPDURL>/rulesservice.xml</SCPDURL>
            </service>
            """.trimIndent()
        } else {
            ""
        }
        return """
            <root><device>
              <deviceType>urn:Belkin:device:lightswitch:1</deviceType>
              <friendlyName>Test Switch</friendlyName>
              <modelName>$model</modelName>
              <serialNumber>SN123456</serialNumber>
              <firmwareVersion>$firmware</firmwareVersion>
              <UDN>$udn</UDN>
              <rtos>${if (isRtos) 1 else 0}</rtos>
              <serviceList>$rulesBlock</serviceList>
            </device></root>
        """.trimIndent()
    }

    @Test
    fun `zero-RTT Tier-1 setup xml detection classifies OpenWrt vs RTOS hardware accurately`() {
        val cases = listOf(
            // OpenWrt wall switches & dimmer v1 -> LIKELY_SUPPORTED + Long Press
            Triple(
                buildSetupXml("F7C030", "WeMo_WW_2.00.11452.PVT-OWRT-LS", "uuid:Lightswitch-1_0-2215", isRtos = false),
                HardwareRulesCapability.LIKELY_SUPPORTED,
                true
            ),
            Triple(
                buildSetupXml("WLS0403", "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2", "uuid:Lightswitch-3_0-ABC", isRtos = false),
                HardwareRulesCapability.LIKELY_SUPPORTED,
                true
            ),
            Triple(
                buildSetupXml("F7C059", "WeMo_WW_2.00.11453.PVT-OWRT-Dimmer", "uuid:Dimmer-1_0-DIM1", isRtos = false),
                HardwareRulesCapability.LIKELY_SUPPORTED,
                true
            ),
            // OpenWrt smart plug -> LIKELY_SUPPORTED, NO Long Press
            Triple(
                buildSetupXml("F7C063", "WeMo_WW_2.00.11452.PVT-OWRT-SNSV2", "uuid:Socket-1_0-PLG1", isRtos = false),
                HardwareRulesCapability.LIKELY_SUPPORTED,
                false
            ),
            // Late OpenWrt F7C027 emitting deceptive <rtos>1</rtos> -> STILL LIKELY_SUPPORTED!
            Triple(
                buildSetupXml("Socket", "WeMo_WW_2.00.11851.PVT-OWRT-SNS", "uuid:Socket-1_0-OLD1", isRtos = true),
                HardwareRulesCapability.LIKELY_SUPPORTED,
                false
            ),
            // RTOS Dimmer V2 & Mini V2 -> LIKELY_UNSUPPORTED despite advertising rules:1
            Triple(
                buildSetupXml("WDS060", "WeMo_WW_2.00.11564.PVT-RTOS-DimmerV2", "uuid:Dimmer-2_0-DIM2", isRtos = true),
                HardwareRulesCapability.LIKELY_UNSUPPORTED,
                false
            ),
            Triple(
                buildSetupXml("WSP080", "WeMo_WW_4.00.20102901.PVT-RTOS-SNSV4", "uuid:Socket-1_0-WSP8", isRtos = true),
                HardwareRulesCapability.LIKELY_UNSUPPORTED,
                false
            ),
            // Legacy descriptor with no rules:1 service -> LIKELY_UNSUPPORTED
            Triple(
                buildSetupXml("F7C030", "WeMo_US_2.00.2263.PVT", "uuid:Lightswitch-1_0-OLD", isRtos = false, includeRulesService = false),
                HardwareRulesCapability.LIKELY_UNSUPPORTED,
                false
            )
        )

        cases.forEach { (xml, expectedCap, expectedLongPress) ->
            val parsed = XmlAndSkuParser.parseSetupXmlDescriptor(
                xml = xml,
                locationUrl = "http://192.168.1.50:49153/setup.xml",
                usnHeader = "",
                stHeader = "urn:Belkin:service:basicevent:1",
                responseTimeMs = 12L,
                isLiveUdp = true
            )
            assertEquals("Capability mismatch for ${parsed.firmwareVersion}", expectedCap, parsed.rulesCapabilityHint)
            assertEquals("Long Press mismatch for ${parsed.firmwareVersion}", expectedLongPress, parsed.longPressCapableHint)
        }
    }

    @Test
    fun `Tier-2 probe verifies OpenWrt rules, fast-rejects RTOS via rulesservice xml, and never downgrades offline switch`() = runBlocking {
        val workDir = File(appContext().cacheDir, "test_rules_probe").apply { mkdirs() }
        val ownerUdn = "uuid:Lightswitch-3_0-WLS0403RULES01"
        val targetUdn = "uuid:Socket-1_0-PORCHTARGET"
        val sampleZip = FakeHardwareRulesServer.buildSampleOpenWrtRulesZip(ownerUdn, targetUdn, workDir)

        FakeHardwareRulesServer(
            initialFriendlyName = "Hallway 3-Way",
            modelName = "WLS0403",
            firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
            udn = ownerUdn,
            supportsRulesDb = true,
            initialDbVersion = 4,
            initialRulesZipBytes = sampleZip,
            initialDeviceTimeUtc = 946_684_800L // Year 2000 -> needs time sync
        ).use { openWrtServer ->
            FakeHardwareRulesServer(
                initialFriendlyName = "Bedroom Dimmer V2",
                modelName = "WDS060",
                firmwareVersion = "WeMo_WW_2.00.11564.PVT-RTOS-DimmerV2",
                udn = "uuid:Dimmer-2_0-WDS060TEST",
                isRtos = true,
                supportsRulesDb = false
            ).use { rtosServer ->
                val client = HardwareRulesClient(appContext = appContext())
                val openWrtDevice = DeviceEntity(
                    id = 10,
                    name = "Hallway 3-Way",
                    deviceType = DeviceType.SWITCH_PLUG.name,
                    ipAddress = "127.0.0.1",
                    port = openWrtServer.port,
                    udn = ownerUdn,
                    firmwareVersion = openWrtServer.firmwareVersion,
                    hardwareRulesCapability = HardwareRulesCapability.LIKELY_SUPPORTED.name,
                    hardwareRulesLongPressCapable = true
                )
                val (openSnap, openLogs) = client.fetchDeviceRulesSnapshot(openWrtDevice, saveRawBackup = true)
                assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED, openSnap.capability)
                assertTrue(openSnap.longPressCapable)
                assertEquals(4, openSnap.dbVersion)
                assertTrue(openSnap.integrityOk)
                assertEquals(4, openSnap.rules.size)
                assertEquals(HardwareRuleType.LONG_PRESS, openSnap.rules[0].type)
                assertEquals(listOf(targetUdn), openSnap.rules[0].targetDeviceUdns)
                assertEquals(HardwareRuleType.COUNTDOWN_TIMER, openSnap.rules[1].type)
                assertEquals(1800, openSnap.rules[1].countdownSeconds)
                assertEquals(HardwareRuleType.SCHEDULE_INTERVAL, openSnap.rules[2].type)
                assertTrue(openSnap.rules[2].startTime?.formatHuman()?.contains("Sunset") == true)
                assertEquals("Mountain View, CA, United States", openSnap.locationInfo?.formatSummary())
                assertTrue(openSnap.deviceTimeInfo?.needsTimeSync == true)
                assertNotNull("Expected raw backup ZIP to be written", openSnap.backupFilePath)
                assertTrue(File(openSnap.backupFilePath!!).isFile)
                assertTrue(openLogs.any { it.soapAction.contains("FetchRules") && it.isLiveLanSuccess })

                // RTOS Dimmer V2 should be fast-rejected from /rulesservice.xml with ZERO FetchRules calls
                val rtosDevice = DeviceEntity(
                    id = 11,
                    name = "Bedroom Dimmer V2",
                    deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "127.0.0.1",
                    port = rtosServer.port,
                    udn = rtosServer.udn,
                    firmwareVersion = rtosServer.firmwareVersion,
                    hardwareRulesCapability = HardwareRulesCapability.LIKELY_UNSUPPORTED.name
                )
                val (rtosSnap, _) = client.fetchDeviceRulesSnapshot(rtosDevice)
                assertEquals(HardwareRulesCapability.VERIFIED_UNSUPPORTED, rtosSnap.capability)
                assertEquals(1, rtosServer.scpdFetches.get())
                assertEquals(0, rtosServer.fetchRulesCalls.get())

                // Offline/unreachable switch MUST retain prior capability (never falsely marked VERIFIED_UNSUPPORTED)
                val offlineDevice = openWrtDevice.copy(
                    port = 1,
                    hardwareRulesCapability = HardwareRulesCapability.VERIFIED_SUPPORTED.name
                )
                val probeOffline = client.probeRulesCapability(offlineDevice)
                assertNull(probeOffline)
                val (offlineSnap, _) = client.fetchDeviceRulesSnapshot(offlineDevice)
                assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED, offlineSnap.capability)
            }
        }
    }

    @Test
    fun `empty HTTP 404 rules db on supported OpenWrt switch reports VERIFIED_SUPPORTED with zero rules`() = runBlocking {
        FakeHardwareRulesServer(
            supportsRulesDb = true,
            initialDbVersion = 1,
            initialRulesZipBytes = null // triggers HTTP 404 on GET /rules.db
        ).use { server ->
            val client = HardwareRulesClient(appContext = appContext())
            val dev = DeviceEntity(
                id = 20,
                name = "Fresh Switch",
                deviceType = DeviceType.SWITCH_PLUG.name,
                ipAddress = "127.0.0.1",
                port = server.port,
                udn = server.udn
            )
            val (snap, _) = client.fetchDeviceRulesSnapshot(dev)
            assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED, snap.capability)
            assertTrue(snap.rules.isEmpty())
            assertTrue(snap.diagnosticMessage.contains("404"))
        }
    }

    @Test
    fun `anti-brick validator rejects unsafe SQL chars, bitmask DayID, zero duration, path traversal, and blocks StoreRules`() = runBlocking {
        val workDir = File(appContext().cacheDir, "test_antibrick").apply { mkdirs() }

        // 1. ZIP path-traversal guard
        val badZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("../etc/passwd"))
                zos.write("SQLite format 3\u0000dummy".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        assertNull(HardwareRulesDbCodec.extractSingleSqliteEntry(badZip))

        // 2. Build intentionally dangerous DB violating multiple firmware C invariants
        val unsafeDb = File(workDir, "unsafe_rules.db")
        HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(unsafeDb)
        SQLiteDatabase.openDatabase(unsafeDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("INSERT INTO RULES VALUES (1, 'Bad \"Quote\"; --Name', 'Time Interval', 0, '12201982', '12201982', '1', 0)")
            db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction, CountdownTime) VALUES (1, 'uuid:Lightswitch-1_0-OK', 127, 3600, 0, 1.0, 0.0, -1)")
            db.execSQL("INSERT INTO RULES VALUES (2, 'Broken Timer', 'Countdown Rule', 1, '12201982', '12201982', '1', 0)")
            db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction, CountdownTime) VALUES (2, 'uuid:Lightswitch-1_0-OK', 1, 0, 86400, 1.0, 0.0, 0)")
        }
        val badVerdict = HardwareRulesSafetyValidator.validateSqliteDbForStore(unsafeDb)
        assertFalse(badVerdict.isSafeToStore)
        assertTrue(badVerdict.issues.any { it.contains("Unsafe SQL character") })
        assertTrue(badVerdict.issues.any { it.contains("DayID=127") })
        assertTrue(badVerdict.issues.any { it.contains("RuleDuration > 0") })
        assertTrue(badVerdict.issues.any { it.contains("CountdownTime > 0") })

        // 3. Armored StoreRules must block unsafe DB with ZERO network calls, then succeed on valid DB with read-back verification
        FakeHardwareRulesServer(supportsRulesDb = true, initialDbVersion = 2).use { server ->
            val client = HardwareRulesClient(appContext = appContext())
            val dev = DeviceEntity(
                id = 30,
                name = "Armored Switch",
                deviceType = DeviceType.SWITCH_PLUG.name,
                ipAddress = "127.0.0.1",
                port = server.port,
                udn = server.udn
            )
            val blocked = client.storeValidatedRulesDb(dev, unsafeDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, 2)
            assertFalse(blocked.success)
            assertEquals("Unsafe DB must never touch the switch", 0, server.storeRulesCalls.get())

            // Valid DB succeeds and increments version with mandatory read-back check
            val safeDb = File(workDir, "safe_rules.db")
            HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(safeDb)
            SQLiteDatabase.openDatabase(safeDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.execSQL("INSERT INTO RULES VALUES (1, 'Auto Off 20m', 'Countdown Rule', 0, '12201982', '12201982', '1', 0)")
                db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction, CountdownTime) VALUES (1, '${server.udn}', 1, 0, 86400, 1.0, 0.0, 1200)")
            }
            val okRes = client.storeValidatedRulesDb(dev, safeDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, 2)
            assertTrue("Expected StoreRules to pass: ${okRes.errorInfo}", okRes.success)
            assertEquals(3, okRes.verifiedVersion)
            assertEquals(1, server.storeRulesCalls.get())

            // C1: "Storing of rules DB Unsuccessful!" MUST be rejected (not matched by substring "successful")
            assertFalse(HardwareRulesProtocol.isStoreRulesSuccess("Storing of rules DB Unsuccessful!"))
            assertFalse(HardwareRulesProtocol.isStoreRulesSuccess("Failed to store"))
            assertFalse(HardwareRulesProtocol.isStoreRulesSuccess("<s:Fault><faultcode>s:Client</faultcode></s:Fault>"))
            assertTrue(HardwareRulesProtocol.isStoreRulesSuccess("Storing of rules DB Successful!"))
            assertTrue(HardwareRulesProtocol.isStoreRulesSuccess(""))
            server.customStoreRulesErrorInfo.set("Storing of rules DB Unsuccessful!")
            val unsuccRes = client.storeValidatedRulesDb(dev, safeDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, 3)
            assertFalse("Unsuccessful! errorInfo must fail StoreRules", unsuccRes.success)

            // C2: DHCP identity mismatch aborts BOTH storeValidatedRulesDb and syncDeviceTime with zero LAN calls
            val wrongSwitchDev = dev.copy(serialNumber = "OTHER_SWITCH_999", udn = "uuid:Lightswitch-3_0-OTHER999")
            val callsBeforeMismatch = server.storeRulesCalls.get()
            val tsBeforeMismatch = server.timeSyncCalls.get()
            assertFalse(client.storeValidatedRulesDb(wrongSwitchDev, safeDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, 3).success)
            assertFalse(client.syncDeviceTime(wrongSwitchDev).isLiveLanSuccess)
            assertEquals("DHCP mismatch must not call StoreRules", callsBeforeMismatch, server.storeRulesCalls.get())
            assertEquals("DHCP mismatch must not call TimeSync", tsBeforeMismatch, server.timeSyncCalls.get())

            // Read-back stale version detection catches silent firmware drop and logs expected/got versions
            server.simulateReadBackStaleVersion.set(true)
            val staleRes = client.storeValidatedRulesDb(dev, safeDb, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, 3)
            assertFalse(staleRes.success)
            assertTrue(staleRes.errorInfo.contains("expected >= v4"))
            assertTrue(staleRes.logEntry.statusText.contains("expected >= v4"))

            // TimeSync sends standard rawOffset (-08.00) in BOTH PST and PDT with Belkin's inverted <dst> (1 in PST, 0 in PDT)
            assertEquals("-08.00", HardwareRulesProtocol.formatFirmwareTimezoneOffset(-480))
            assertEquals("05.50", HardwareRulesProtocol.formatFirmwareTimezoneOffset(330))
            val laTz = TimeZone.getTimeZone("America/Los_Angeles")
            val pstMillis = 1_768_478_400_000L // 2026-01-15 UTC (PST, not in DST)
            val pdtMillis = 1_784_116_800_000L // 2026-07-15 UTC (PDT, in DST)
            val pstLog = client.syncDeviceTime(dev, nowUtcMillis = pstMillis, timeZone = laTz)
            assertTrue(pstLog.isLiveLanSuccess)
            assertEquals("-08.00", server.lastTimeSyncTimezone.get())
            assertEquals("1", server.lastTimeSyncDst.get())
            assertEquals("1", server.lastTimeSyncDstSupported.get())
            val pdtLog = client.syncDeviceTime(dev, nowUtcMillis = pdtMillis, timeZone = laTz)
            assertTrue(pdtLog.isLiveLanSuccess)
            assertEquals("-08.00", server.lastTimeSyncTimezone.get())
            assertEquals("0", server.lastTimeSyncDst.get())
            assertEquals(pdtMillis / 1000L, server.deviceTimeUtc.get())

            // Protocol hardening: empty <ruleDbVersion/> -> 0, SSRF-safe resolveRuleDbUrl, generic 500 non-caching, /Lightsetup.xml longPress=false
            server.emptyVersionTagInFetchRules.set(true)
            assertEquals(0, HardwareRulesProtocol.parseFetchRulesResponse("<u:FetchRulesResponse><ruleDbVersion/><ruleDbPath>/rules.db</ruleDbPath></u:FetchRulesResponse>")?.ruleDbVersion)
            assertEquals("http://192.168.1.50:49153/rules.db", HardwareRulesProtocol.resolveRuleDbUrl("192.168.1.50", 49153, "https://evil.example.com:8443/rules.db"))
            server.simulateGenericFetchRules500.set(true)
            val (err500Snap, _) = client.fetchDeviceRulesSnapshot(dev.copy(hardwareRulesCapability = HardwareRulesCapability.LIKELY_SUPPORTED.name))
            assertEquals("Generic HTTP 500 must not cache VERIFIED_UNSUPPORTED", HardwareRulesCapability.LIKELY_SUPPORTED, err500Snap.capability)
            assertTrue(err500Snap.hasInspectionError)
            server.serveLightSetupXmlOnly.set(true)
            val (lightSetupSnap, _) = client.fetchDeviceRulesSnapshot(dev.copy(hardwareRulesCapability = HardwareRulesCapability.LIKELY_SUPPORTED.name, hardwareRulesLongPressCapable = false))
            assertFalse("/Lightsetup.xml must not override longPressCapable to true", lightSetupSnap.longPressCapable)
            assertEquals(HardwareRulesCapability.LIKELY_UNSUPPORTED, XmlAndSkuParser.inferRulesCapabilityFromSetupXml("<root><serviceList><service><serviceType>urn:Belkin:service:rules:1</serviceType></service></serviceList></root>", "http://192.168.1.10:49153/setup.xml", "uuid:Socket-1_0-X", "WeMo_WW_4.00.20102901.PVT-RTOS").capability)
        }

        // 4. Backup rotation & refresh deduplication: identical consecutive refreshes never evict older distinct backups
        val backupDir = File(workDir, "backups_rotation").apply { deleteRecursively(); mkdirs() }
        val distinctA = byteArrayOf(1, 2, 3, 10)
        val distinctB = byteArrayOf(1, 2, 3, 20)
        val savedA = HardwareRulesDbCodec.saveRawZipBackup(backupDir, "DEV_DEDUP", 1, distinctA, maxRetainedPerDevice = 3)
        savedA?.setLastModified(1_700_000_001_000L)
        repeat(5) {
            HardwareRulesDbCodec.saveRawZipBackup(backupDir, "DEV_DEDUP", 2, distinctB, maxRetainedPerDevice = 3)
        }
        val dedupFiles = File(backupDir, "DEV_DEDUP").listFiles()?.filter { it.extension == "zip" }.orEmpty()
        assertEquals("5 identical refreshes must deduplicate to 1 file while retaining prior distinct backup", 2, dedupFiles.size)

        repeat(7) { idx ->
            val saved = HardwareRulesDbCodec.saveRawZipBackup(backupDir, "DEV_ROTATE", idx + 1, byteArrayOf(1, 2, 3, idx.toByte()), maxRetainedPerDevice = 3)
            saved?.setLastModified(1_700_000_000_000L + idx * 1000L)
        }
        assertEquals(3, File(backupDir, "DEV_ROTATE").listFiles()?.filter { it.extension == "zip" }.orEmpty().size)
    }

    @Test
    fun `Room MIGRATION_5_6 preserves existing devices and adds default hardware rules columns`() {
        val dbFile = File(appContext().cacheDir, "migration_5_6_test.db").apply { delete() }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(appContext()).name(dbFile.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE wemo_devices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, ipAddress TEXT NOT NULL, port INTEGER NOT NULL, isOn INTEGER NOT NULL, brightness INTEGER NOT NULL, isReachable INTEGER NOT NULL)")
                        db.execSQL("INSERT INTO wemo_devices (id, name, type, ipAddress, port, isOn, brightness, isReachable) VALUES (1, 'Kitchen Lights', 'SWITCH_PLUG', '192.168.1.40', 49153, 1, 100, 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            val db = helper.writableDatabase
            SwitchDatabase.MIGRATION_5_6.migrate(db)
            db.query("SELECT name, hardwareRulesCapability, hardwareRulesLongPressCapable, hardwareRulesVerifiedFirmware FROM wemo_devices WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Kitchen Lights", c.getString(0))
                assertEquals("UNKNOWN", c.getString(1))
                assertEquals(0, c.getInt(2))
                assertEquals("", c.getString(3))
            }
        } finally {
            helper.close()
            dbFile.delete()
        }
    }

    @Test
    fun `HardwareRulesCoordinator caches VERIFIED_UNSUPPORTED per firmware and skips repeat probes unless forced`() = runBlocking {
        FakeHardwareRulesServer(
            initialFriendlyName = "RTOS Dimmer", modelName = "WDS060",
            firmwareVersion = "WeMo_WW_2.00.11564.PVT-RTOS-DimmerV2", udn = "uuid:Dimmer-2_0-CACHE01",
            isRtos = true, supportsRulesDb = false
        ).use { rtosServer ->
            val db = SwitchDatabase.getDatabase(appContext())
            val dao = db.switchDao()
            dao.deleteAllDevices()
            val insertedId = dao.insertDevice(
                DeviceEntity(
                    name = "RTOS Dimmer", deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "127.0.0.1", port = rtosServer.port, udn = rtosServer.udn,
                    firmwareVersion = rtosServer.firmwareVersion,
                    hardwareRulesCapability = HardwareRulesCapability.LIKELY_UNSUPPORTED.name
                )
            ).toInt()
            val saved = dao.getDeviceById(insertedId)!!
            val coordinator = org.suppressingfire.wemi.data.HardwareRulesCoordinator(
                dao = dao, hardwareRulesClient = HardwareRulesClient(appContext = appContext()),
                lanSemaphore = kotlinx.coroutines.sync.Semaphore(3),
                deviceMutexes = java.util.concurrent.ConcurrentHashMap(),
                isDemoShowroomMode = { false }, appendLog = {}, nowFormatted = { "12:00:00 PM" }
            )
            assertEquals(HardwareRulesCapability.VERIFIED_UNSUPPORTED, coordinator.inspectDeviceHardwareRules(saved, false).capability)
            assertEquals(1, rtosServer.scpdFetches.get())

            val persisted = dao.getDeviceById(insertedId)!!
            assertTrue(persisted.isHardwareRulesCapabilityFresh)
            assertEquals(HardwareRulesCapability.VERIFIED_UNSUPPORTED, coordinator.inspectDeviceHardwareRules(persisted, false).capability)
            assertEquals("Cached firmware verdict must skip LAN probe", 1, rtosServer.scpdFetches.get())
            coordinator.inspectDeviceHardwareRules(persisted, forceRecheck = true)
            assertEquals(2, rtosServer.scpdFetches.get())

            val sanitizer = org.suppressingfire.wemi.data.HomeProfileSanitizer(dao, { false }, { "12:00:00 PM" })
            val sameFwUnknownHint = org.suppressingfire.wemi.data.SsdpDiscoveredDevice(
                name = "RTOS Dimmer", ipAddress = "127.0.0.1", port = rtosServer.port,
                deviceType = DeviceType.LIGHTBULB_DIMMER, modelName = "WDS060", serialNumber = "",
                firmwareVersion = rtosServer.firmwareVersion, usn = rtosServer.udn,
                searchTarget = "urn:Belkin:service:basicevent:1", locationUrl = "http://127.0.0.1:${rtosServer.port}/setup.xml",
                responseTimeMs = 5L, rulesCapabilityHint = HardwareRulesCapability.UNKNOWN, longPressCapableHint = false
            )
            val keptVerified = sanitizer.adoptDiscoveredDevice(sameFwUnknownHint, persisted.networkKey, "HomeWiFi")
            assertEquals(HardwareRulesCapability.VERIFIED_UNSUPPORTED, keptVerified.parsedHardwareRulesCapability)
            assertTrue(keptVerified.isHardwareRulesCapabilityFresh)

            val upgradedFw = sanitizer.mergeDiscoveredMetadata(
                keptVerified, sameFwUnknownHint.copy(firmwareVersion = "WeMo_WW_3.00.99999.PVT"),
                "127.0.0.1", rtosServer.port, false, 100, 5, "12:01:00 PM", false
            )
            assertEquals(HardwareRulesCapability.UNKNOWN, upgradedFw.parsedHardwareRulesCapability)
            assertEquals("", upgradedFw.hardwareRulesVerifiedFirmware)

            val tier1Base = persisted.copy(hardwareRulesCapability = HardwareRulesCapability.LIKELY_SUPPORTED.name, hardwareRulesLongPressCapable = true, hardwareRulesVerifiedFirmware = "")
            val keptTier1 = sanitizer.mergeDiscoveredMetadata(
                tier1Base, sameFwUnknownHint, "127.0.0.1", rtosServer.port, false, 100, 5, "12:02:00 PM", false
            )
            assertEquals(HardwareRulesCapability.LIKELY_SUPPORTED, keptTier1.parsedHardwareRulesCapability)
            assertTrue(keptTier1.hardwareRulesLongPressCapable)

            SwitchDatabase.closeCurrentInstance()
        }
    }
}
