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
        runCatching { File(context.filesDir, "hardware_rules_backups").deleteRecursively() }
    }

    @After
    fun tearDown() {
        runCatching { workDir.deleteRecursively() }
        runCatching { File(context.filesDir, "hardware_rules_backups").deleteRecursively() }
        SwitchDatabase.closeCurrentInstance()
    }

    @Test
    fun `armored mutateAndStoreRulesDb auto-syncs drifted clock, preserves custom tables and zip entry name, and verifies version`() = runBlocking {
        val ownerUdn = "uuid:Lightswitch-3_0-WLS0403STEP2"
        val targetUdn = "uuid:Dimmer-2_0-WDS060TARGET2"
        val customEntryName = "customPluginRules.db"
        val baseZip = FakeHardwareRulesServer.buildSampleOpenWrtRulesZip(
            ownerUdn = ownerUdn,
            targetUdn = targetUdn,
            workDir = workDir,
            zipEntryName = customEntryName
        )
        // Inject an unknown firmware-specific table to prove in-place mutation never wipes firmware metadata
        val extracted = File(workDir, "inject_custom.db")
        val unpacked = checkNotNull(HardwareRulesDbCodec.extractSingleSqliteEntry(baseZip))
        extracted.writeBytes(unpacked.second)
        SQLiteDatabase.openDatabase(extracted.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("CREATE TABLE CUSTOM_FW_METADATA (Key TEXT PRIMARY KEY, Val TEXT)")
            db.execSQL("INSERT INTO CUSTOM_FW_METADATA VALUES ('vendor_flag', 'keep_me')")
        }
        val initialZip = HardwareRulesDbCodec.packFileToZipBytes(extracted, unpacked.first)

        FakeHardwareRulesServer(
            initialFriendlyName = "Porch 3-Way",
            modelName = "WLS0403",
            serialNumber = "WLS0403STEP2",
            udn = ownerUdn,
            initialDbVersion = 3,
            initialRulesZipBytes = initialZip,
            initialDeviceTimeUtc = 946_684_800L // Year 2000 -> drifted
        ).use { server ->
            val client = HardwareRulesClient(appContext = context)
            val device = DeviceEntity(
                id = 11,
                name = "Porch 3-Way",
                roomName = "Porch",
                ipAddress = "127.0.0.1",
                port = server.port,
                serialNumber = "WLS0403STEP2",
                udn = ownerUdn,
                hardwareModel = "WLS0403",
                firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2"
            )

            // 1. Create a new Long Press rule -> triggers auto TimeSync + backup v3 + StoreRules v4
            val (addLongPress, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    sqliteFile = dbFile,
                    ownerDeviceUdn = ownerUdn,
                    draft = HardwareRuleDraft(
                        name = "Hold All Off",
                        type = HardwareRuleType.LONG_PRESS,
                        isEnabled = true,
                        longPressAction = HardwareLongPressAction.TURN_OFF,
                        targetDeviceUdns = listOf(targetUdn)
                    )
                )
            }
            assertTrue(addLongPress.message, addLongPress.success)
            assertTrue("Drifted switch clock must be synced automatically", addLongPress.clockSynced)
            assertEquals(1, server.timeSyncCalls.get())
            val snap1 = checkNotNull(addLongPress.snapshot)
            assertEquals(4, snap1.dbVersion)
            assertTrue("Pre-write raw-ZIP backup must be saved", snap1.availableBackups.isNotEmpty())
            assertTrue(File(snap1.availableBackups.first().filePath).exists())
            assertTrue(snap1.rules.any { it.name == "Hold All Off" && it.type == HardwareRuleType.LONG_PRESS })

            // Verify uploaded ZIP preserved original entry name and custom firmware table
            val uploadedZip = checkNotNull(server.currentRulesZipBytes.get())
            val entryName = ZipInputStream(ByteArrayInputStream(uploadedZip)).use { it.nextEntry?.name }
            assertEquals(customEntryName, entryName)
            val verifyPair = checkNotNull(HardwareRulesDbCodec.extractSingleSqliteEntry(uploadedZip))
            val verifyDb = File(workDir, "verify_custom.db").apply { writeBytes(verifyPair.second) }
            SQLiteDatabase.openDatabase(verifyDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT Val FROM CUSTOM_FW_METADATA WHERE Key = 'vendor_flag'", null).use { c ->
                    assertTrue("Custom firmware table row must survive in-place mutation", c.moveToFirst())
                    assertEquals("keep_me", c.getString(0))
                }
            }

            // 2. Add a Sunset Schedule rule -> clock is now fresh so TimeSync is NOT called a second time
            val (addSchedule, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    sqliteFile = dbFile,
                    ownerDeviceUdn = ownerUdn,
                    draft = HardwareRuleDraft(
                        name = "Porch Dusk Glow",
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
            }
            assertTrue(addSchedule.success)
            assertFalse("Fresh clock should not trigger redundant TimeSync", addSchedule.clockSynced)
            assertEquals(1, server.timeSyncCalls.get())
            val snap2 = checkNotNull(addSchedule.snapshot)
            assertEquals(5, snap2.dbVersion)

            // 3. Toggle rule OFF, update Solar Location, and delete the created Long Press rule
            val lpId = snap2.rules.first { it.name == "Hold All Off" }.ruleId
            val (toggled, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.toggleRuleEnabled(dbFile, lpId, false)
            }
            assertTrue(toggled.success)
            val snap3 = checkNotNull(toggled.snapshot)
            assertEquals(6, snap3.dbVersion)
            assertFalse(snap3.rules.first { it.ruleId == lpId }.isEnabled)

            val seattle = HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.first { it.cityName == "Seattle" }
            val (located, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.updateLocationInfo(dbFile, seattle)
            }
            assertTrue(located.success)
            val snap4 = checkNotNull(located.snapshot)
            assertEquals("Seattle", snap4.locationInfo?.cityName)
            assertEquals(7, snap4.dbVersion)

            val (deleted, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.deleteRule(dbFile, lpId)
            }
            assertTrue(deleted.success)
            val snap5 = checkNotNull(deleted.snapshot)
            assertEquals(8, snap5.dbVersion)
            assertTrue(snap5.rules.none { it.ruleId == lpId })
            assertTrue(snap5.rules.any { it.name == "Porch Dusk Glow" })
        }
    }

    @Test
    fun `armored write bootstraps empty v0 switch, handles StoreRules faults safely, and restores backups`() = runBlocking {
        val ownerUdn = "uuid:Socket-1_0-F7C063BOOT01"
        FakeHardwareRulesServer(
            initialFriendlyName = "Office Mini Plug",
            deviceTypeUrn = "urn:Belkin:device:controllee:1",
            modelName = "F7C063",
            serialNumber = "F7C063BOOT01",
            udn = ownerUdn,
            initialDbVersion = 0,
            initialRulesZipBytes = null // Fresh factory switch -> 404 on GET /rules.db
        ).use { server ->
            val client = HardwareRulesClient(appContext = context)
            val device = DeviceEntity(
                id = 22,
                name = "Office Mini Plug",
                roomName = "Office",
                ipAddress = "127.0.0.1",
                port = server.port,
                serialNumber = "F7C063BOOT01",
                udn = ownerUdn,
                hardwareModel = "F7C063"
            )

            // 1. Bootstrap empty v0 switch with a Simple Switch timed action -> DB v1
            val (firstWrite, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    sqliteFile = dbFile,
                    ownerDeviceUdn = ownerUdn,
                    draft = HardwareRuleDraft(
                        name = "Desk Morning ON",
                        type = HardwareRuleType.SIMPLE_TIMER,
                        isEnabled = true,
                        daysOfWeek = listOf(2, 3, 4, 5, 6),
                        startAnchor = HardwareTimeAnchor.FIXED_TIME,
                        startHour24 = 8,
                        startMinute = 0,
                        startActionOn = true
                    )
                )
            }
            assertTrue(firstWrite.message, firstWrite.success)
            val snap1 = checkNotNull(firstWrite.snapshot)
            assertEquals(1, snap1.dbVersion)
            assertEquals(1, snap1.rules.size)

            // 2. Simulate StoreRules rejection -> fails cleanly without bumping version
            server.rejectNextStoreRules.set(true)
            val (rejected, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    sqliteFile = dbFile,
                    ownerDeviceUdn = ownerUdn,
                    draft = HardwareRuleDraft(
                        name = "Should Not Stick",
                        type = HardwareRuleType.COUNTDOWN_TIMER,
                        countdownMinutes = 10
                    )
                )
            }
            assertFalse(rejected.success)
            assertEquals(1, server.currentDbVersion.get())

            // 3. Simulate stale FetchRules read-back version -> flags verification failure
            server.simulateReadBackStaleVersion.set(true)
            val (stale, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.upsertRule(
                    sqliteFile = dbFile,
                    ownerDeviceUdn = ownerUdn,
                    draft = HardwareRuleDraft(
                        name = "Stale Rule",
                        type = HardwareRuleType.COUNTDOWN_TIMER,
                        countdownMinutes = 15
                    )
                )
            }
            assertFalse("Stale FetchRules DB version must fail post-store verification", stale.success)

            // 4. Delete the rule cleanly (creates backup of current rules) -> then restore that v1 backup
            val ruleId = snap1.rules.single().ruleId
            val (deleted, _) = client.mutateAndStoreRulesDb(device) { dbFile ->
                HardwareRulesDbMutator.deleteRule(dbFile, ruleId)
            }
            assertTrue(deleted.success)
            val delSnap = checkNotNull(deleted.snapshot)
            assertTrue(delSnap.rules.isEmpty())
            val backupToRestore = delSnap.availableBackups.first { it.dbVersion == 1 }

            val (restored, _) = client.restoreRulesFromBackup(device, backupToRestore.filePath)
            assertTrue(restored.message, restored.success)
            val restoredSnap = checkNotNull(restored.snapshot)
            assertEquals(1, restoredSnap.rules.size)
            assertEquals("Desk Morning ON", restoredSnap.rules.single().name)
        }
    }

    @Test
    fun `Room MIGRATION_6_7 drops retired wemo_rules table while preserving homes, devices, and cached rules capability`() = runBlocking {
        val dbName = "migration_6_7_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Build an exact Room v6 database with homes, devices (including v6 capability columns), and legacy wemo_rules rows
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wemo_home_networks` (
                    `networkKey` TEXT NOT NULL, `homeName` TEXT NOT NULL, `ssidLabel` TEXT NOT NULL,
                    `subnetPrefix` TEXT NOT NULL, `gatewayIp` TEXT NOT NULL, `lastConnectedTime` TEXT NOT NULL,
                    `autoReconnectOnArrival` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`networkKey`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wemo_devices` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `roomName` TEXT NOT NULL,
                    `networkKey` TEXT NOT NULL, `deviceType` TEXT NOT NULL, `ipAddress` TEXT NOT NULL,
                    `port` INTEGER NOT NULL, `isOn` INTEGER NOT NULL, `isReachable` INTEGER NOT NULL,
                    `brightness` INTEGER NOT NULL, `nightModeEnabled` INTEGER NOT NULL,
                    `signalStrengthPercent` INTEGER NOT NULL, `firmwareVersion` TEXT NOT NULL, `hardwareModel` TEXT NOT NULL,
                    `serialNumber` TEXT NOT NULL, `udn` TEXT NOT NULL, `macAddress` TEXT NOT NULL,
                    `wifiSsid` TEXT NOT NULL, `lastMotionTime` TEXT NOT NULL, `autoOffMinutes` INTEGER NOT NULL,
                    `latencyMs` INTEGER NOT NULL, `lastCheckedTime` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL,
                    `hardwareRulesCapability` TEXT NOT NULL, `hardwareRulesLongPressCapable` INTEGER NOT NULL,
                    `hardwareRulesVerifiedFirmware` TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `wemo_rules` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `ruleType` TEXT NOT NULL,
                    `targetDeviceId` INTEGER NOT NULL, `targetDeviceName` TEXT NOT NULL, `triggerSummary` TEXT NOT NULL,
                    `actionSummary` TEXT NOT NULL, `targetStateOn` INTEGER NOT NULL, `targetBrightness` INTEGER NOT NULL,
                    `isEnabled` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO wemo_home_networks VALUES ('$DEFAULT_MAIN_HOME_KEY', 'Main Home', 'HomeWiFi', '192.168.1', '192.168.1.1', 'Now', 1, 0)"
            )
            db.execSQL(
                """
                INSERT INTO wemo_devices (
                    id, name, roomName, networkKey, deviceType, ipAddress, port, isOn, isReachable, brightness,
                    nightModeEnabled, signalStrengthPercent, firmwareVersion, hardwareModel, serialNumber,
                    udn, macAddress, wifiSsid, lastMotionTime, autoOffMinutes, latencyMs, lastCheckedTime, sortOrder,
                    hardwareRulesCapability, hardwareRulesLongPressCapable, hardwareRulesVerifiedFirmware
                ) VALUES (
                    1, 'Porch Switch', 'Porch', '$DEFAULT_MAIN_HOME_KEY', 'SWITCH_PLUG', '192.168.1.40', 49153, 1, 1, 100,
                    0, 90, 'WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2', 'WLS0403', 'WLS0403MIG01',
                    'uuid:Lightswitch-3_0-WLS0403MIG01', 'EC1A59001122', 'HomeWiFi', '', 0, 12, 'Now', 0,
                    'VERIFIED_SUPPORTED', 1, 'WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2'
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO wemo_rules VALUES (1, 'Legacy App Timer', 'SCHEDULE', 1, 'Porch Switch', 'Daily 7 PM', 'ON', 1, 100, 1)"
            )
            db.version = 6
        }

        val migrated = Room.databaseBuilder(context, SwitchDatabase::class.java, dbName)
            .addMigrations(SwitchDatabase.MIGRATION_5_6, SwitchDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            val homes = migrated.switchDao().getAllHomeNetworks().first()
            val devices = migrated.switchDao().getAllDevices().first()
            assertEquals(1, homes.size)
            assertEquals("Main Home", homes.single().homeName)
            assertEquals(1, devices.size)
            assertEquals("Porch Switch", devices.single().name)
            assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED, devices.single().parsedHardwareRulesCapability)
            assertTrue(devices.single().hardwareRulesLongPressCapable)

            migrated.openHelper.readableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='wemo_rules'"
            ).use { cursor ->
                assertFalse("Retired wemo_rules table must be dropped by MIGRATION_6_7", cursor.moveToFirst())
            }
        } finally {
            migrated.close()
            runCatching { context.deleteDatabase(dbName) }
        }
    }

    @Test
    fun `SwitchRepository Demo Showroom Mode supports Step 2 CRUD, clock sync, solar location, and backup restore`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, SwitchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = SwitchRepository(dao = db.switchDao(), context = context, initialDemoModeOverride = true)
        try {
            repo.ensureSeeded()
            val livingSwitch = repo.allDevices.first().first { it.id == 1 }
            val inspected = repo.inspectDeviceHardwareRules(livingSwitch)
            assertEquals(HardwareRulesCapability.VERIFIED_SUPPORTED, inspected.capability)
            val initialRuleCount = inspected.rules.size
            assertTrue(initialRuleCount >= 2)

            val saved = repo.saveHardwareRule(
                livingSwitch,
                HardwareRuleDraft(name = "Demo Quick Auto-Off", type = HardwareRuleType.COUNTDOWN_TIMER, countdownMinutes = 20)
            )
            assertTrue(saved.success)
            val savedSnap = checkNotNull(saved.snapshot)
            assertEquals(initialRuleCount + 1, savedSnap.rules.size)
            assertTrue(savedSnap.availableBackups.isNotEmpty())

            val created = savedSnap.rules.first { it.name == "Demo Quick Auto-Off" }
            val paused = repo.toggleHardwareRule(livingSwitch, created, false)
            assertTrue(paused.success)
            assertFalse(checkNotNull(paused.snapshot).rules.first { it.ruleId == created.ruleId }.isEnabled)

            val denver = HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.first { it.cityName == "Denver" }
            val locUpdated = repo.updateDeviceSolarLocation(livingSwitch, denver)
            assertTrue(locUpdated.success)
            assertEquals("Denver", checkNotNull(locUpdated.snapshot).locationInfo?.cityName)

            val clockSynced = repo.syncDeviceClock(livingSwitch)
            assertTrue(clockSynced.success)
            assertTrue(clockSynced.clockSynced)

            val deleted = repo.deleteHardwareRule(livingSwitch, created)
            assertTrue(deleted.success)
            val delSnap = checkNotNull(deleted.snapshot)
            assertEquals(initialRuleCount, delSnap.rules.size)

            val latestBackup = delSnap.availableBackups.first()
            val restored = repo.restoreHardwareRulesBackup(livingSwitch, latestBackup.filePath)
            assertTrue(restored.success)
            assertNotNull(restored.snapshot)
            assertTrue(checkNotNull(restored.snapshot).rules.any { it.name == "Demo Quick Auto-Off" })
        } finally {
            repo.close()
            db.close()
        }
    }
}
