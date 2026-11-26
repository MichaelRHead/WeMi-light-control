package org.suppressingfire.wemi

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
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
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.HardwareLongPressAction
import org.suppressingfire.wemi.network.HardwareRuleDraft
import org.suppressingfire.wemi.network.HardwareRuleType
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HardwareRulesClient
import org.suppressingfire.wemi.network.HardwareRulesDbCodec
import org.suppressingfire.wemi.network.HardwareRulesDbMutator
import org.suppressingfire.wemi.network.HardwareTimeAnchor

/**
 * Step 2 On-Device Switch Rules mutation, armored backup/restore, automatic `timesync1#TimeSync`,
 * Room v6 -> v7 non-destructive `MIGRATION_6_7`, and Demo Showroom CRUD verification suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareRulesMutationTest {

    private lateinit var context: Context
    private lateinit var workDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        workDir = File(context.cacheDir, "hw_rules_step2_test_${System.nanoTime()}").apply { mkdirs() }
        runCatching { File(context.noBackupFilesDir, "hardware_rules_backups").deleteRecursively() }
        runCatching { File(context.filesDir, "hardware_rules_backups").deleteRecursively() }
    }

    @After
    fun tearDown() {
        runCatching { workDir.deleteRecursively() }
        runCatching { File(context.noBackupFilesDir, "hardware_rules_backups").deleteRecursively() }
        runCatching { File(context.filesDir, "hardware_rules_backups").deleteRecursively() }
        SwitchDatabase.closeCurrentInstance()
    }

    @Test
    fun `armored mutateAndStoreRulesDb auto-syncs drifted clock, preserves custom tables and zip entry name, and verifies version`() = runBlocking {
        val ownerUdn = "uuid:Lightswitch-3_0-WLS0403STEP2"
        val targetUdn = "uuid:Dimmer-2_0-WDS060TARGET2"
        val customEntryName = "customPluginRules.db"
        val baseZip = FakeHardwareRulesServer.buildSampleOpenWrtRulesZip(ownerUdn, targetUdn, workDir, customEntryName)
        val extracted = File(workDir, "inject_custom.db")
        val unpacked = checkNotNull(HardwareRulesDbCodec.extractSingleSqliteEntry(baseZip))
        extracted.writeBytes(unpacked.second)
        SQLiteDatabase.openDatabase(extracted.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("CREATE TABLE CUSTOM_FW_METADATA (Key TEXT PRIMARY KEY, Val TEXT)")
            db.execSQL("INSERT INTO CUSTOM_FW_METADATA VALUES ('vendor_flag', 'keep_me')")
        }
        val initialZip = HardwareRulesDbCodec.packFileToZipBytes(extracted, unpacked.first)

        FakeHardwareRulesServer(
            initialFriendlyName = "Porch 3-Way", modelName = "WLS0403", serialNumber = "WLS0403STEP2",
            udn = ownerUdn, initialDbVersion = 3, initialRulesZipBytes = initialZip, initialDeviceTimeUtc = 946_684_800L
        ).use { server ->
            val client = HardwareRulesClient(appContext = context)
            val device = DeviceEntity(
                id = 11, name = "Porch 3-Way", roomName = "Porch", ipAddress = "127.0.0.1", port = server.port,
                serialNumber = "WLS0403STEP2", udn = ownerUdn, hardwareModel = "WLS0403",
                firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2"
            )

            val (addLongPress, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    dbFile, ownerUdn,
                    HardwareRuleDraft(
                        name = "Hold All Off", type = HardwareRuleType.LONG_PRESS, isEnabled = true,
                        longPressAction = HardwareLongPressAction.TURN_OFF, targetDeviceUdns = listOf(targetUdn)
                    )
                )
            }
            assertTrue(addLongPress.message, addLongPress.success)
            assertTrue("Drifted switch clock must be synced automatically", addLongPress.clockSynced)
            assertEquals(1, server.timeSyncCalls.get())
            val snap1 = checkNotNull(addLongPress.snapshot)
            assertEquals(4, snap1.dbVersion)
            assertTrue(snap1.availableBackups.isNotEmpty() && File(snap1.availableBackups.first().filePath).exists())
            assertTrue(snap1.rules.any { it.name == "Hold All Off" && it.type == HardwareRuleType.LONG_PRESS })

            val uploadedZip = checkNotNull(server.currentRulesZipBytes.get())
            assertEquals(customEntryName, ZipInputStream(ByteArrayInputStream(uploadedZip)).use { it.nextEntry?.name })
            val verifyDb = File(workDir, "verify_custom.db").apply { writeBytes(checkNotNull(HardwareRulesDbCodec.extractSingleSqliteEntry(uploadedZip)).second) }
            SQLiteDatabase.openDatabase(verifyDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.rawQuery("SELECT Val FROM CUSTOM_FW_METADATA WHERE Key = 'vendor_flag'", null).use { c ->
                    assertTrue(c.moveToFirst()); assertEquals("keep_me", c.getString(0))
                }
            }

            // Transient trailing reload failure after verified StoreRules preserves verified rules via local fallback
            val (addSchedule, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    dbFile, ownerUdn,
                    HardwareRuleDraft(
                        name = "Porch Dusk Glow", type = HardwareRuleType.SCHEDULE_INTERVAL, isEnabled = true,
                        daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7), startAnchor = HardwareTimeAnchor.SUNSET, startOffsetMinutes = -15,
                        endAnchor = HardwareTimeAnchor.FIXED_TIME, endHour24 = 23, endMinute = 0, startActionOn = true, endActionOn = false
                    )
                )
                server.failNextRulesDbDownload.set(true)
            }
            assertTrue(addSchedule.success)
            assertFalse("Fresh clock should not trigger redundant TimeSync", addSchedule.clockSynced)
            val snap2 = checkNotNull(addSchedule.snapshot)
            assertEquals(5, snap2.dbVersion)
            assertFalse(snap2.hasInspectionError)
            assertTrue(snap2.rules.any { it.name == "Porch Dusk Glow" })

            val lpId = snap2.rules.first { it.name == "Hold All Off" }.ruleId
            assertTrue(client.mutateAndStoreRulesDb(device) { HardwareRulesDbMutator.toggleRuleEnabled(it, lpId, false) }.first.success)
            val london = HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.first { it.cityName == "London" }
            val (located, _) = client.mutateAndStoreRulesDb(device) { HardwareRulesDbMutator.updateLocationInfo(it, london) }
            assertTrue(located.success)
            assertEquals("GB", checkNotNull(located.snapshot).locationInfo?.countryCode)

            val (deleted, _) = client.mutateAndStoreRulesDb(device) { HardwareRulesDbMutator.deleteRule(it, lpId) }
            assertTrue(deleted.success)
            assertTrue(checkNotNull(deleted.snapshot).rules.none { it.ruleId == lpId })
        }
    }

    @Test
    fun `armored write bootstraps empty v0 switch, handles StoreRules faults safely, and restores backups`() = runBlocking {
        val ownerUdn = "uuid:Socket-1_0-F7C063BOOT01"
        FakeHardwareRulesServer(
            initialFriendlyName = "Office Mini Plug", deviceTypeUrn = "urn:Belkin:device:controllee:1",
            modelName = "F7C063", serialNumber = "F7C063BOOT01", udn = ownerUdn, initialDbVersion = 0, initialRulesZipBytes = null
        ).use { server ->
            val client = HardwareRulesClient(appContext = context)
            val device = DeviceEntity(
                id = 22, name = "Office Mini Plug", roomName = "Office", ipAddress = "127.0.0.1", port = server.port,
                serialNumber = "F7C063BOOT01", udn = ownerUdn, hardwareModel = "F7C063"
            )
            val (firstWrite, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    dbFile, ownerUdn,
                    HardwareRuleDraft(
                        name = "Desk Morning ON", type = HardwareRuleType.SIMPLE_TIMER, isEnabled = true,
                        daysOfWeek = listOf(2, 3, 4, 5, 6), startAnchor = HardwareTimeAnchor.FIXED_TIME,
                        startHour24 = 8, startMinute = 0, startActionOn = true
                    )
                )
            }
            assertTrue(firstWrite.message, firstWrite.success)
            val snap1 = checkNotNull(firstWrite.snapshot)
            assertEquals(1, snap1.dbVersion)

            server.rejectNextStoreRules.set(true)
            assertFalse(client.mutateAndStoreRulesDb(device) { HardwareRulesDbMutator.upsertRule(it, ownerUdn, HardwareRuleDraft(name = "Nope", type = HardwareRuleType.COUNTDOWN_TIMER, countdownMinutes = 10)) }.first.success)
            assertEquals(1, server.currentDbVersion.get())

            server.simulateReadBackStaleVersion.set(true)
            assertFalse(client.mutateAndStoreRulesDb(device) { HardwareRulesDbMutator.upsertRule(it, ownerUdn, HardwareRuleDraft(name = "Stale", type = HardwareRuleType.COUNTDOWN_TIMER, countdownMinutes = 15)) }.first.success)

            val ruleId = snap1.rules.single().ruleId
            val (deleted, _) = client.mutateAndStoreRulesDb(device) { HardwareRulesDbMutator.deleteRule(it, ruleId) }
            assertTrue(deleted.success)
            val backupToRestore = checkNotNull(deleted.snapshot).availableBackups.first { it.dbVersion == 1 }

            // Path traversal outside app backup dir must be rejected
            val outsideFile = File(workDir, "outside_attack.zip").apply { writeBytes(File(backupToRestore.filePath).readBytes()) }
            assertFalse(client.restoreRulesFromBackup(device, outsideFile.absolutePath).first.success)

            val (restored, restoreLogs) = client.restoreRulesFromBackup(device, backupToRestore.filePath)
            assertTrue(restored.message, restored.success)
            assertTrue(restoreLogs.any { it.soapAction.startsWith("rules1#RestoreBackup(v") })
            assertEquals("Desk Morning ON", checkNotNull(restored.snapshot).rules.single().name)
        }
    }

    @Test
    fun `legacy Belkin rules, quote sanitization, WAL rejection, ZBCapability, solar midnight-sunrise round-trip, and multi-switch ownership`() = runBlocking {
        val switchA = "uuid:Lightswitch-3_0-MULTI_A"
        val switchB = "uuid:Lightswitch-3_0-MULTI_B"
        val dbFile = File(workDir, "hardened_invariants.db")
        org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(dbFile)

        // 1. Insert untouched legacy Belkin rule types with DayID -1/8 and -1 time sentinels + RULESNOTIFYMESSAGE
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("INSERT INTO RULES VALUES (1, 'Bob''s Motion Sensor', 'Motion Controlled', 0, '12201982', '12201982', '1', 0)")
            db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction) VALUES (1, '$switchA', 8, -1, -1, 1.0, 0.0)")
            db.execSQL("INSERT INTO RULES VALUES (2, 'Multi Switch Schedule', 'Time Interval', 1, '12201982', '12201982', '1', 0)")
            db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction, EndTime) VALUES (2, '$switchA', 2, 64800, 3600, 1.0, 0.0, 68400)")
            db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction, EndTime) VALUES (2, '${switchB.lowercase()}::urn:Belkin:service:basicevent:1', 2, 64800, 3600, 1.0, 0.0, 68400)")
            db.execSQL("INSERT INTO RULES VALUES (3, 'Windowed AutoOff', 'Countdown Rule', 2, '12201982', '12201982', '1', 0)")
            db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, DayID, StartTime, RuleDuration, StartAction, EndAction, CountdownTime, EndTime) VALUES (3, '$switchA', 2, 28800, 50400, 1.0, 0.0, 600, 79200)")
            db.execSQL("INSERT INTO RULESNOTIFYMESSAGE (RuleID, NotifyRuleID, Message, Frequency) VALUES (99, 3, 'Timer done', 60)")
            db.execSQL("INSERT INTO RULESNOTIFYMESSAGE (RuleID, NotifyRuleID, Message, Frequency) VALUES (3, 1, 'Keep motion msg', 60)")
        }

        // 2. Quote & comment sanitization + ZBCapabilityStart/End + Countdown window preservation
        assertEquals("Mike s drop Porch", HardwareRulesDbMutator.sanitizeSqlText("Mike's /* drop */ Porch */"))
        HardwareRulesDbMutator.upsertRule(
            dbFile, switchA,
            HardwareRuleDraft(existingRuleId = 3, name = "Bob's /* Updated */ AutoOff", type = HardwareRuleType.COUNTDOWN_TIMER, countdownMinutes = 25, daysOfWeek = listOf(2, 3))
        )
        // 3. Sunset -> 12:00 AM and Sunset -> Sunrise round-trip (verifying RuleDuration % 10 tags and midnight 0 / 86400)
        val sunsetToMidnightId = HardwareRulesDbMutator.upsertRule(
            dbFile, switchA,
            HardwareRuleDraft(
                name = "Sunset To Midnight", type = HardwareRuleType.SCHEDULE_INTERVAL,
                startAnchor = HardwareTimeAnchor.SUNSET, startOffsetMinutes = 0,
                endAnchor = HardwareTimeAnchor.FIXED_TIME, endHour24 = 0, endMinute = 0
            )
        )
        val sunsetToSunriseId = HardwareRulesDbMutator.upsertRule(
            dbFile, switchA,
            HardwareRuleDraft(
                name = "Dusk To Dawn", type = HardwareRuleType.SCHEDULE_INTERVAL,
                startAnchor = HardwareTimeAnchor.SUNSET, startOffsetMinutes = -15,
                endAnchor = HardwareTimeAnchor.SUNRISE, endOffsetMinutes = 10
            )
        )

        // Legacy rule must NOT block validator when mutating our rules
        val validVerdict = org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.validateSqliteDbForStore(dbFile, inspectedOwnerUdn = switchA)
        assertTrue("Untouched legacy rule must pass validator: ${validVerdict.issues}", validVerdict.isSafeToStore)

        // Verify ZBCapability*, RuleDuration % 10 tags, Countdown window preserved, and NotifyRuleID delete
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("SELECT ZBCapabilityStart, ZBCapabilityEnd, StartTime, RuleDuration, EndTime FROM RULEDEVICES WHERE RuleID = 3 LIMIT 1", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("-1", c.getString(0))
                assertEquals("-1", c.getString(1))
                assertEquals(28800, c.getInt(2))
                assertEquals(50400, c.getInt(3))
                assertEquals(79200, c.getInt(4))
            }
            db.rawQuery("SELECT RuleDuration, EndTime FROM RULEDEVICES WHERE RuleID = $sunsetToMidnightId LIMIT 1", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0, c.getInt(0) % 10)
                assertEquals(0, c.getInt(1))
            }
            db.rawQuery("SELECT RuleDuration FROM RULEDEVICES WHERE RuleID = $sunsetToSunriseId LIMIT 1", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0) % 10)
            }
        }

        // Parse & round-trip Sunset->12:00 AM, Sunset->Sunrise, 86400->00:00, and multi-switch ownership
        val parsed = HardwareRulesDbCodec.parseSqliteFileReadOnly(dbFile, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, switchA)
        val midnightRule = parsed.rules.first { it.ruleId == sunsetToMidnightId }
        assertEquals("12:00 AM", midnightRule.endTime?.formatHuman())
        val midnightDraft = checkNotNull(HardwareRuleDraft.fromHardwareRule(midnightRule))
        assertEquals(HardwareTimeAnchor.FIXED_TIME, midnightDraft.endAnchor)
        assertEquals(0, midnightDraft.endHour24); assertEquals(0, midnightDraft.endMinute)
        assertEquals(0, checkNotNull(HardwareRuleDraft.fromHardwareRule(midnightRule.copy(endTime = org.suppressingfire.wemi.network.HardwareRuleTimePoint(86400)))).endHour24)

        // Also test EndTime = -1 derivation from RuleDuration % 10 (Sunset->Midnight & Sunset->Sunrise)
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("UPDATE RULEDEVICES SET EndTime = -1 WHERE RuleID IN ($sunsetToMidnightId, $sunsetToSunriseId)")
        }
        val parsedNoEnd = HardwareRulesDbCodec.parseSqliteFileReadOnly(dbFile, HardwareRulesDbCodec.DEFAULT_ZIP_ENTRY_NAME, switchA)
        assertEquals("12:00 AM", parsedNoEnd.rules.first { it.ruleId == sunsetToMidnightId }.endTime?.formatHuman())
        assertTrue(parsedNoEnd.rules.first { it.ruleId == sunsetToSunriseId }.endTime?.isSunrise == true)

        val multiRules = parsed.rules.filter { it.ruleId == 2 }
        assertEquals(2, multiRules.size)
        assertTrue(multiRules.all { it.isSharedMultiSwitch && !it.isEditableInApp })
        assertEquals(1, multiRules.count { it.isOwnedByInspectedSwitch })
        assertEquals(1, multiRules.count { !it.isOwnedByInspectedSwitch })

        // Verify deleteRule deletes RULESNOTIFYMESSAGE by NotifyRuleID (keeping RuleID=3 with NotifyRuleID=1!)
        assertTrue(HardwareRulesDbMutator.deleteRule(dbFile, 3))
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.rawQuery("SELECT NotifyRuleID FROM RULESNOTIFYMESSAGE", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)); assertFalse(c.moveToNext())
            }
        }

        // Verify WAL header (bytes 18..19 == 2) is rejected even on raw backup restore, while rollback-journal raw backup passes
        val walDb = File(workDir, "wal_rejected.db").apply { writeBytes(dbFile.readBytes()) }
        java.io.RandomAccessFile(walDb, "rw").use { raf -> raf.seek(18); raf.write(byteArrayOf(2, 2)) }
        assertFalse(org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.validateSqliteDbForStore(walDb).isSafeToStore)
        assertFalse(org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.validateSqliteDbForStore(walDb, isRawBackupRestore = true).isSafeToStore)
        assertTrue(org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.validateSqliteDbForStore(dbFile, isRawBackupRestore = true).isSafeToStore)

        // Verify LOCATIONINFO SQL validation & coordinate fallback summary
        assertEquals("37.7749, -122.4194", org.suppressingfire.wemi.network.HardwareLocationInfo("", "", "", "37.7749", "-122.4194").formatSummary())
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
            db.execSQL("INSERT INTO LOCATIONINFO (cityName, countryName, latitude, longitude, countryCode, region) VALUES ('Coeur''d', 'US', '47.67', '-116.78', 'US', 'ID')")
        }
        assertFalse(org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.validateSqliteDbForStore(dbFile).isSafeToStore)
        assertTrue(org.suppressingfire.wemi.network.HardwareRulesSafetyValidator.validateSqliteDbForStore(dbFile, isRawBackupRestore = true).isSafeToStore)
    }

    @Test
    fun `Room chained MIGRATION_5_6 and MIGRATION_6_7 preserves homes and devices while dropping wemo_rules`() = runBlocking {
        val dbName = "migration_5_6_7_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Build an exact Room v5 database (pre-v6 hardware rules columns + legacy wemo_rules table)
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `wemo_home_networks` (`networkKey` TEXT NOT NULL, `homeName` TEXT NOT NULL, `ssidLabel` TEXT NOT NULL, `subnetPrefix` TEXT NOT NULL, `gatewayIp` TEXT NOT NULL, `lastConnectedTime` TEXT NOT NULL, `autoReconnectOnArrival` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`networkKey`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wemo_devices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `roomName` TEXT NOT NULL, `networkKey` TEXT NOT NULL, `deviceType` TEXT NOT NULL, `ipAddress` TEXT NOT NULL, `port` INTEGER NOT NULL, `isOn` INTEGER NOT NULL, `isReachable` INTEGER NOT NULL, `brightness` INTEGER NOT NULL, `nightModeEnabled` INTEGER NOT NULL, `signalStrengthPercent` INTEGER NOT NULL, `firmwareVersion` TEXT NOT NULL, `hardwareModel` TEXT NOT NULL, `serialNumber` TEXT NOT NULL, `udn` TEXT NOT NULL, `macAddress` TEXT NOT NULL, `wifiSsid` TEXT NOT NULL, `lastMotionTime` TEXT NOT NULL, `autoOffMinutes` INTEGER NOT NULL, `latencyMs` INTEGER NOT NULL, `lastCheckedTime` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wemo_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `ruleType` TEXT NOT NULL, `targetDeviceId` INTEGER NOT NULL, `targetDeviceName` TEXT NOT NULL, `triggerSummary` TEXT NOT NULL, `actionSummary` TEXT NOT NULL, `targetStateOn` INTEGER NOT NULL, `targetBrightness` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)")
            db.execSQL("INSERT INTO wemo_home_networks VALUES ('$DEFAULT_MAIN_HOME_KEY', 'Main Home', 'HomeWiFi', '192.168.1', '192.168.1.1', 'Now', 1, 0)")
            db.execSQL("INSERT INTO wemo_devices VALUES (1, 'Porch Switch', 'Porch', '$DEFAULT_MAIN_HOME_KEY', 'SWITCH_PLUG', '192.168.1.40', 49153, 1, 1, 100, 0, 90, 'WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2', 'WLS0403', 'WLS0403MIG01', 'uuid:Lightswitch-3_0-WLS0403MIG01', 'EC1A59001122', 'HomeWiFi', '', 0, 12, 'Now', 0)")
            db.execSQL("INSERT INTO wemo_rules VALUES (1, 'Legacy App Timer', 'SCHEDULE', 1, 'Porch Switch', 'Daily 7 PM', 'ON', 1, 100, 1)")
            db.version = 5
        }

        val migrated = Room.databaseBuilder(context, SwitchDatabase::class.java, dbName)
            .addMigrations(SwitchDatabase.MIGRATION_5_6, SwitchDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            val homes = migrated.switchDao().getAllHomeNetworks().first()
            val devices = migrated.switchDao().getAllDevices().first()
            assertEquals(1, homes.size); assertEquals("Main Home", homes.single().homeName)
            assertEquals(1, devices.size); assertEquals("Porch Switch", devices.single().name)
            assertEquals(HardwareRulesCapability.UNKNOWN, devices.single().parsedHardwareRulesCapability)
            assertFalse(devices.single().hardwareRulesLongPressCapable)
            migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table' AND name='wemo_rules'").use { c ->
                assertFalse("Retired wemo_rules table must be dropped by chained v5->v6->v7 migration", c.moveToFirst())
            }
        } finally {
            migrated.close()
            runCatching { context.deleteDatabase(dbName) }
        }
    }

    @Test
    fun `SwitchRepository Demo Showroom Mode supports Step 2 CRUD, clock sync, solar location, and backup restore`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SwitchDatabase::class.java).allowMainThreadQueries().build()
        val repo = SwitchRepository(dao = db.switchDao(), context = context, initialDemoModeOverride = true)
        try {
            repo.ensureSeeded()
            val livingSwitch = repo.allDevices.first().first { it.id == 1 }
            val inspected = repo.inspectDeviceHardwareRules(livingSwitch)
            assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED, inspected.capability)
            val initialRuleCount = inspected.rules.size
            assertTrue(initialRuleCount >= 2)

            val saved = repo.saveHardwareRule(livingSwitch, HardwareRuleDraft(name = "Demo Quick Auto-Off", type = HardwareRuleType.COUNTDOWN_TIMER, countdownMinutes = 20))
            assertTrue(saved.success)
            val savedSnap = checkNotNull(saved.snapshot)
            assertEquals(initialRuleCount + 1, savedSnap.rules.size)
            assertTrue(savedSnap.availableBackups.isNotEmpty())

            val created = savedSnap.rules.first { it.name == "Demo Quick Auto-Off" }
            assertFalse(checkNotNull(repo.toggleHardwareRule(livingSwitch, created, false).snapshot).rules.first { it.ruleId == created.ruleId }.isEnabled)
            val denver = HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.first { it.cityName == "Denver" }
            assertEquals("Denver", checkNotNull(repo.updateDeviceSolarLocation(livingSwitch, denver).snapshot).locationInfo?.cityName)
            assertTrue(repo.syncDeviceClock(livingSwitch).clockSynced)

            val delSnap = checkNotNull(repo.deleteHardwareRule(livingSwitch, created).snapshot)
            assertEquals(initialRuleCount, delSnap.rules.size)
            val restored = repo.restoreHardwareRulesBackup(livingSwitch, delSnap.availableBackups.first().filePath)
            assertTrue(restored.success)
            assertTrue(checkNotNull(restored.snapshot).rules.any { it.name == "Demo Quick Auto-Off" })
        } finally {
            repo.close()
            db.close()
        }
    }
}
