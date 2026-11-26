package org.suppressingfire.wemi

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.network.HardwareRulesDbCodec
import org.suppressingfire.wemi.network.HardwareRulesSafetyValidator
import org.suppressingfire.wemi.ui.SwitchViewModel

/**
 * Full-stack Jetpack Compose + Loopback UPnP/SOAP E2E test for Step 2 On-Device Switch Rules:
 * adopts a loopback OpenWrt switch (with Long Press, Auto-Off Countdown, Sunset Schedule, and
 * Away Mode rules plus a year-2000 drifted clock) and a loopback RTOS Dimmer V2, inspects rules,
 * creates/toggles/edits/deletes native on-device rules with automatic `timesync1#TimeSync`, updates
 * Solar Location (`LOCATIONINFO`), restores a saved pre-write backup, and verifies the expanded
 * Devices-tab diagnostics drawer readout, validation guards, shared RuleID keys, and error states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareRulesE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        File(ctx.cacheDir, "e2e_hw_rules_step2").deleteRecursively()
        File(ctx.cacheDir, "e2e_hw_rules_shared").deleteRecursively()
        File(ctx.filesDir, "hardware_rules_backups").deleteRecursively()
    }

    private fun awaitLoopbackIo(timeoutMs: Long = 6_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

    private fun awaitRulesListNode(tagOrText: String, isTag: Boolean = true) {
        val matcher = if (isTag) {
            hasTestTag(tagOrText) or hasAnyDescendant(hasTestTag(tagOrText))
        } else {
            hasText(tagOrText, substring = true)
        }
        awaitLoopbackIo {
            runCatching {
                composeTestRule.onNodeWithTag("rules_list").performScrollToNode(matcher)
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun `E2E full Step 2 On-Device Rules CRUD, auto clock sync, solar location, backup restore, and RTOS classification`() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        val workDir = File(composeTestRule.activity.cacheDir, "e2e_hw_rules_step2").apply { mkdirs() }
        val openWrtUdn = "uuid:Lightswitch-3_0-WLS0403E2E99"
        val rtosUdn = "uuid:Dimmer-2_0-WDS060E2E88"
        val sampleZip = FakeHardwareRulesServer.buildSampleOpenWrtRulesZip(
            ownerUdn = openWrtUdn,
            targetUdn = rtosUdn,
            workDir = workDir
        )

        FakeHardwareRulesServer(
            initialFriendlyName = "Porch Entry Switch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403E2E99",
            firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
            udn = openWrtUdn,
            macAddress = "EC1A59CC1101",
            isRtos = false,
            supportsRulesDb = true,
            initialDbVersion = 3,
            initialRulesZipBytes = sampleZip,
            initialDeviceTimeUtc = 946_684_800L // Year 2000 -> triggers clock sync warning + auto-sync on first write
        ).use { openWrtServer ->
            FakeHardwareRulesServer(
                initialFriendlyName = "Master Dimmer V2",
                deviceTypeUrn = "urn:Belkin:device:dimmer:1",
                modelName = "WDS060",
                serialNumber = "WDS060E2E88",
                firmwareVersion = "WeMo_WW_2.00.11564.PVT-RTOS-DimmerV2",
                udn = rtosUdn,
                macAddress = "EC1A59CC1102",
                isRtos = true,
                supportsRulesDb = false
            ).use { rtosServer ->
                // 1. Add both simulated switches via AddOrEditDeviceDialog (blank name -> auto-populates from /setup.xml)
                addLoopbackSwitch(openWrtServer.port)
                awaitLoopbackIo { vm.devices.value.any { it.serialNumber == "WLS0403E2E99" } }
                val openWrtDeviceId = vm.devices.value.first { it.serialNumber == "WLS0403E2E99" }.id

                addLoopbackSwitch(rtosServer.port)
                awaitLoopbackIo { vm.devices.value.any { it.serialNumber == "WDS060E2E88" } }
                val rtosDeviceId = vm.devices.value.first { it.serialNumber == "WDS060E2E88" }.id

                // 2. Navigate to Rules tab: confirm On-Switch pill, New Rule & Read Rules buttons, and retirement of Read-Only / App-Run UI
                composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("hardware_rules_header_card").assertIsDisplayed()
                composeTestRule.onNodeWithTag("add_hardware_rule_button").assertIsDisplayed()
                composeTestRule.onNodeWithTag("scan_hardware_rules_button").assertIsDisplayed()
                composeTestRule.onNodeWithText("On-Switch").assertExists()
                composeTestRule.onNodeWithText("Read-Only").assertDoesNotExist()
                composeTestRule.onNodeWithText("App-Run").assertDoesNotExist()

                // 3. Tap "Read Rules" to verify Tier-2 probe + read-only download
                composeTestRule.onNodeWithTag("scan_hardware_rules_button").performClick()
                awaitLoopbackIo {
                    openWrtServer.rulesDbDownloads.get() >= 1 && rtosServer.scpdFetches.get() >= 1
                }
                composeTestRule.waitForIdle()
                assertTrue("RTOS switch must not receive FetchRules", rtosServer.fetchRulesCalls.get() == 0)

                // 4. Verify decoded OpenWrt rules, resolved target friendly name, clock warning, and solar location render
                awaitRulesListNode("Hold to Toggle Porch", isTag = false)
                composeTestRule.onNodeWithText("Hold to Toggle Porch").assertIsDisplayed()
                composeTestRule.onNodeWithText("Toggle: Master Dimmer V2", substring = true).assertExists()
                awaitRulesListNode("hardware_rules_device_card_$openWrtDeviceId", isTag = true)
                composeTestRule.onNodeWithText("Switch clock needs sync").assertExists()
                composeTestRule.onNodeWithText("Solar location on switch: Mountain View, CA, United States").assertExists()
                awaitRulesListNode("Auto-Off Bathroom Fan", isTag = false)
                composeTestRule.onNodeWithText("Auto-Off Bathroom Fan").assertIsDisplayed()
                awaitRulesListNode("Evening Sunset Porch", isTag = false)
                composeTestRule.onNodeWithText("Evening Sunset Porch").assertIsDisplayed()

                // Verify RTOS Dimmer V2 displays clean unsupported capability readout
                awaitRulesListNode("hardware_rules_device_card_$rtosDeviceId", isTag = true)
                composeTestRule.onNodeWithText("No On-Device Rules").assertExists()
                composeTestRule.onNodeWithText(
                    "Firmware does not expose on-device rules storage. Dimmer Night Mode is still stored on-device."
                ).assertExists()

                // 5. Create a new Auto-Off Delay rule on Porch Entry Switch -> auto-syncs drifted clock + writes DB v4
                awaitRulesListNode("add_device_hardware_rule_$openWrtDeviceId", isTag = true)
                composeTestRule.onNodeWithTag("add_device_hardware_rule_$openWrtDeviceId").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("hardware_rule_name_input")
                    .performScrollTo()
                    .performTextReplacement("Porch Quick Timer")
                composeTestRule.onNodeWithTag("hardware_rule_type_countdown")
                    .performScrollTo()
                    .performClick()
                composeTestRule.onNodeWithTag("countdown_preset_15")
                    .performScrollTo()
                    .performClick()
                composeTestRule.onNodeWithTag("save_hardware_rule_button").performClick()

                awaitLoopbackIo {
                    openWrtServer.storeRulesCalls.get() == 1 &&
                        openWrtServer.currentDbVersion.get() == 4 &&
                        vm.hardwareRulesByDeviceId.value[openWrtDeviceId]?.rules?.any { it.name == "Porch Quick Timer" } == true
                }
                assertEquals("Drifted clock must auto-sync before first write", 1, openWrtServer.timeSyncCalls.get())
                val createdRuleId = vm.hardwareRulesByDeviceId.value[openWrtDeviceId]!!
                    .rules.first { it.name == "Porch Quick Timer" }.ruleId

                // 6. Toggle the created rule OFF -> writes DB v5
                awaitRulesListNode("toggle_hardware_rule_${openWrtDeviceId}_$createdRuleId", isTag = true)
                composeTestRule.onNodeWithTag("toggle_hardware_rule_${openWrtDeviceId}_$createdRuleId").performClick()
                awaitLoopbackIo {
                    openWrtServer.currentDbVersion.get() == 5 &&
                        vm.hardwareRulesByDeviceId.value[openWrtDeviceId]?.rules?.find { it.ruleId == createdRuleId }?.isEnabled == false
                }

                // 7. Edit the created rule -> rename to "Porch 30m Timer" (30 min) -> writes DB v6
                awaitRulesListNode("edit_hardware_rule_${openWrtDeviceId}_$createdRuleId", isTag = true)
                composeTestRule.onNodeWithTag("edit_hardware_rule_${openWrtDeviceId}_$createdRuleId").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("hardware_rule_name_input")
                    .performScrollTo()
                    .performTextReplacement("Porch 30m Timer")
                composeTestRule.onNodeWithTag("countdown_preset_30")
                    .performScrollTo()
                    .performClick()
                composeTestRule.onNodeWithTag("save_hardware_rule_button").performClick()
                awaitLoopbackIo {
                    openWrtServer.currentDbVersion.get() == 6 &&
                        vm.hardwareRulesByDeviceId.value[openWrtDeviceId]?.rules?.any { it.name == "Porch 30m Timer" } == true
                }

                // 8. Update Solar Location via HardwareLocationDialog (Seattle preset) -> writes DB v7
                awaitRulesListNode("edit_solar_location_$openWrtDeviceId", isTag = true)
                composeTestRule.onNodeWithTag("edit_solar_location_$openWrtDeviceId").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("solar_preset_seattle")
                    .performScrollTo()
                    .performClick()
                composeTestRule.onNodeWithTag("save_solar_location_button").performClick()
                awaitLoopbackIo {
                    openWrtServer.currentDbVersion.get() == 7 &&
                        vm.hardwareRulesByDeviceId.value[openWrtDeviceId]?.locationInfo?.cityName == "Seattle"
                }
                awaitRulesListNode("Solar location on switch: Seattle, WA, United States", isTag = false)

                // 9. Delete "Porch 30m Timer" via DeleteHardwareRuleConfirmDialog -> writes DB v8
                awaitRulesListNode("delete_hardware_rule_${openWrtDeviceId}_$createdRuleId", isTag = true)
                composeTestRule.onNodeWithTag("delete_hardware_rule_${openWrtDeviceId}_$createdRuleId").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("confirm_delete_hardware_rule_button").performClick()
                awaitLoopbackIo {
                    openWrtServer.currentDbVersion.get() == 8 &&
                        vm.hardwareRulesByDeviceId.value[openWrtDeviceId]?.rules?.none { it.ruleId == createdRuleId } == true
                }

                // 10. Restore the latest pre-delete backup (v7) via HardwareRulesBackupDialog -> writes DB v9 & restores "Porch 30m Timer"
                awaitRulesListNode("open_backup_restore_$openWrtDeviceId", isTag = true)
                composeTestRule.onNodeWithTag("open_backup_restore_$openWrtDeviceId").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("restore_backup_button_0").performScrollTo().performClick()
                awaitLoopbackIo {
                    openWrtServer.currentDbVersion.get() == 9 &&
                        vm.hardwareRulesByDeviceId.value[openWrtDeviceId]?.rules?.any { it.name == "Porch 30m Timer" } == true
                }

                // 11. Switch to Devices tab, expand Porch Entry Switch drawer, verify live DB v9 + Add Rule + Manage Rules bridge
                composeTestRule.onNodeWithTag("nav_tab_devices").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Porch Entry Switch"))
                composeTestRule.onNodeWithText("Porch Entry Switch").performClick()
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("5 rule(s) stored on switch (DB v9)").assertExists()
                composeTestRule.onNodeWithText("On-Device Rules: On-Device Ready • Long Press").assertExists()
                composeTestRule.onNodeWithTag("drawer_add_hardware_rule_$openWrtDeviceId").assertExists()
                composeTestRule.onNodeWithTag("drawer_open_rules_tab_$openWrtDeviceId")
                    .performScrollTo()
                    .performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("hardware_rules_header_card").assertIsDisplayed()
            }
        }
    }

    @Test
    fun `E2E Rules UX guards empty home plus tap, Go to Devices bridge, schedule ±1m steppers, solar validation, and negative coordinates`() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        vm.toggleDemoShowroomMode(false)
        awaitLoopbackIo { !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() }

        // 1. In fresh empty Real LAN home, Rules tab shows Go to Devices button and '+' guards against empty dialog
        composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
        composeTestRule.waitForIdle()
        awaitRulesListNode("hardware_rules_empty_state", isTag = true)
        composeTestRule.onNodeWithTag("hardware_rules_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hardware_rule_name_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("status_feedback_banner").assertIsDisplayed()

        // 2. Tap Go to Devices -> returns to Devices tab
        awaitRulesListNode("rules_empty_go_to_devices_button", isTag = true)
        composeTestRule.onNodeWithTag("rules_empty_go_to_devices_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("devices_list").assertIsDisplayed()

        // 3. Add a loopback switch, verify negative coordinates, ±1m off-grid steppers, and identical solar anchor validation
        val openWrtUdn = "uuid:Lightswitch-3_0-WLS0403UX01"
        FakeHardwareRulesServer(
            initialFriendlyName = "Patio Entry Switch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403UX01",
            firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
            udn = openWrtUdn,
            macAddress = "EC1A59DD2201",
            isRtos = false,
            supportsRulesDb = true,
            initialDbVersion = 1
        ).use { server ->
            addLoopbackSwitch(server.port)
            awaitLoopbackIo { vm.devices.value.any { it.serialNumber == "WLS0403UX01" } }
            val switchId = vm.devices.value.first { it.serialNumber == "WLS0403UX01" }.id

            composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("scan_hardware_rules_button").performClick()
            awaitLoopbackIo { vm.hardwareRulesByDeviceId.value[switchId] != null }
            composeTestRule.waitForIdle()

            // Verify negative longitude & latitude entry works in Solar Location dialog
            awaitRulesListNode("edit_solar_location_$switchId", isTag = true)
            composeTestRule.onNodeWithTag("edit_solar_location_$switchId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("solar_lat_input").performScrollTo().performTextReplacement("-33.8688")
            composeTestRule.onNodeWithTag("solar_lon_input").performScrollTo().performTextReplacement("-122.4194")
            composeTestRule.onNodeWithTag("save_solar_location_button").performClick()
            awaitLoopbackIo {
                vm.hardwareRulesByDeviceId.value[switchId]?.locationInfo?.longitude == "-122.4194"
            }

            // Open Schedule editor -> verify -15m/+5m/+1m/-1m steppers and identical solar anchor rejection + clear on edit
            awaitRulesListNode("add_hardware_rule_button", isTag = true)
            composeTestRule.onNodeWithTag("add_hardware_rule_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("hardware_rule_type_schedule").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("start_step_-15").performScrollTo().performClick()
            composeTestRule.onNodeWithText("6:15 PM").assertExists()
            composeTestRule.onNodeWithTag("start_step_5").performScrollTo().performClick()
            composeTestRule.onNodeWithText("6:20 PM").assertExists()
            composeTestRule.onNodeWithTag("start_step_1").performScrollTo().performClick()
            composeTestRule.onNodeWithText("6:21 PM").assertExists()
            composeTestRule.onNodeWithTag("start_step_-1").performScrollTo().performClick()
            composeTestRule.onNodeWithText("6:20 PM").assertExists()

            composeTestRule.onNodeWithTag("start_anchor_SUNSET").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("end_anchor_SUNSET").performScrollTo().performClick()
            composeTestRule.onNodeWithTag("save_hardware_rule_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("hardware_rule_validation_error").assertIsDisplayed()
            composeTestRule.onNodeWithText("Start time and end time must be different").assertIsDisplayed()

            composeTestRule.onNodeWithTag("end_offset_15").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("hardware_rule_validation_error").assertDoesNotExist()
            composeTestRule.onNodeWithText("Cancel").performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun `E2E multi-switch shared RuleID renders without LazyColumn duplicate key crash and drawer surfaces inspection errors`() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        vm.toggleDemoShowroomMode(false)
        awaitLoopbackIo { !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() }

        val workDir = File(composeTestRule.activity.cacheDir, "e2e_hw_rules_shared").apply { mkdirs() }
        val ownerUdn = "uuid:Lightswitch-3_0-WLS0403SHR01"
        val peerUdn = "uuid:Lightswitch-3_0-WLS0403SHR02"
        val sharedZip = buildSharedRuleIdZip(ownerUdn, peerUdn, workDir)

        FakeHardwareRulesServer(
            initialFriendlyName = "Shared Hall Switch",
            modelName = "WLS0403",
            serialNumber = "WLS0403SHR01",
            firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
            udn = ownerUdn,
            macAddress = "EC1A59EE3301",
            isRtos = false,
            supportsRulesDb = true,
            initialDbVersion = 2,
            initialRulesZipBytes = sharedZip
        ).use { sharedServer ->
            FakeHardwareRulesServer(
                initialFriendlyName = "Corrupt Rules Switch",
                modelName = "WLS0403",
                serialNumber = "WLS0403ERR02",
                firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
                udn = peerUdn,
                macAddress = "EC1A59EE3302",
                isRtos = false,
                supportsRulesDb = true,
                initialDbVersion = 2,
                initialRulesZipBytes = "not-a-valid-zip".toByteArray()
            ).use { corruptServer ->
                addLoopbackSwitch(sharedServer.port)
                awaitLoopbackIo { vm.devices.value.any { it.serialNumber == "WLS0403SHR01" } }
                val sharedId = vm.devices.value.first { it.serialNumber == "WLS0403SHR01" }.id

                addLoopbackSwitch(corruptServer.port)
                awaitLoopbackIo { vm.devices.value.any { it.serialNumber == "WLS0403ERR02" } }
                val corruptId = vm.devices.value.first { it.serialNumber == "WLS0403ERR02" }.id

                // Read all rules on Rules tab: shared RuleID renders without LazyColumn duplicate-key crash
                composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("scan_hardware_rules_button").performClick()
                awaitLoopbackIo {
                    vm.hardwareRulesByDeviceId.value[sharedId] != null &&
                        vm.hardwareRulesByDeviceId.value[corruptId] != null
                }
                awaitRulesListNode("Shared Entry Schedule", isTag = false)
                composeTestRule.onAllNodes(hasText("Shared Entry Schedule")).onFirst().assertIsDisplayed()
                composeTestRule.onAllNodes(hasTestTag("delete_hardware_rule_${sharedId}_10")).onFirst().assertIsNotEnabled()
                composeTestRule.onAllNodes(hasTestTag("toggle_hardware_rule_${sharedId}_10")).onFirst().assertIsNotEnabled()

                // Expand Corrupt Rules Switch in Devices tab: drawer surfaces diagnostic error instead of '0 rule(s) stored'
                composeTestRule.onNodeWithTag("nav_tab_devices").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Corrupt Rules Switch"))
                composeTestRule.onNodeWithText("Corrupt Rules Switch").performClick()
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("not a valid rules.db ZIP", substring = true).assertExists()
                composeTestRule.onNodeWithText("0 rule(s) stored on switch", substring = true).assertDoesNotExist()
                composeTestRule.onNodeWithTag("drawer_open_rules_tab_$corruptId").assertExists()
            }
        }
    }

    private fun buildSharedRuleIdZip(ownerUdn: String, peerUdn: String, workDir: File): ByteArray {
        val dbFile = File.createTempFile("shared_rules_", ".db", workDir.apply { mkdirs() })
        return try {
            HardwareRulesSafetyValidator.createEmptyBelkinSchemaDb(dbFile)
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                db.execSQL("INSERT INTO RULES VALUES (10, 'Shared Entry Schedule', 'Time Interval', 0, '12201982', '12201982', '1', 0)")
                db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, GroupID, DayID, StartTime, RuleDuration, StartAction, EndAction, SensorDuration, Type, Value, Level, OnModeOffset, OffModeOffset, CountdownTime, EndTime) VALUES (10, '$ownerUdn', 0, 1, 64800, 14400, 1.0, 0.0, -1, -1, -1, -1, 0, 0, -1, 79200)")
                db.execSQL("INSERT INTO RULEDEVICES (RuleID, DeviceID, GroupID, DayID, StartTime, RuleDuration, StartAction, EndAction, SensorDuration, Type, Value, Level, OnModeOffset, OffModeOffset, CountdownTime, EndTime) VALUES (10, '$peerUdn', 0, 1, 64800, 14400, 1.0, 0.0, -1, -1, -1, -1, 0, 0, -1, 79200)")
                db.execSQL("INSERT INTO TARGETDEVICES (RuleID, DeviceID, DeviceIndex) VALUES (10, '$ownerUdn', 0)")
            } finally {
                db.close()
            }
            HardwareRulesDbCodec.packFileToZipBytes(dbFile)
        } finally {
            runCatching { dbFile.delete() }
        }
    }

    private fun addLoopbackSwitch(port: Int) {
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_name_input")
            .performScrollTo()
            .performTextReplacement("")
        composeTestRule.onNodeWithTag("device_ip_input")
            .performScrollTo()
            .performTextReplacement("127.0.0.1")
        composeTestRule.onNodeWithTag("device_port_input")
            .performScrollTo()
            .performTextReplacement(port.toString())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("save_device_button").performClick()
    }
}
