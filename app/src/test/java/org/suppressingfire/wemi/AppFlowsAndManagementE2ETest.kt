package org.suppressingfire.wemi

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_CABIN_HOME_KEY
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.RuleEntity
import org.suppressingfire.wemi.data.SwitchDao
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.ui.SwitchViewModel

/**
 * Full-stack Jetpack Compose + Robolectric + loopback [FakeUpnpServer] E2E tests closing key
 * interactive management, navigation, and automation flows across phone & 10-inch tablet layouts:
 * 1. Multi-Home creation (`AddWifiHomeNetworkDialog`), Room collapse/expand, Room filter + Flat
 *    List toggle, context-sensitive header actions, and multi-layer `BackHandler` unwinding.
 * 2. Real-LAN hardware NVRAM rename (`ChangeFriendlyName` toggle ON vs. OFF), custom room move,
 *    Edit-Mode `Move Down` reordering, and `Delete Device` pruning active Auto-Off timers.
 * 3. Add-dialog SSDP launcher (`scan_wifi_ssdp_button`), Search Target switching, `Rescan`, bulk
 *    `Adopt All`, More-tab Master ON/OFF, SOAP log `Clear`, and Demo ↔ Real LAN mode toggle.
 * 4. 10-inch Tablet landscape layout (`w840dp-h600dp-land`), Motion-Trigger Dimmer rule execution
 *    (honoring target brightness & Wi-Fi Home isolation over loopback SOAP), and Rule deletion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppFlowsAndManagementE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("wemi_controller_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("wemi_controller_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        SwitchDatabase.closeCurrentInstance()
    }

    private fun dao(): SwitchDao = SwitchDatabase.getDatabase(context).switchDao()

    private fun viewModelFor(scenario: ActivityScenario<MainActivity>): SwitchViewModel {
        var vm: SwitchViewModel? = null
        scenario.onActivity { activity ->
            vm = ViewModelProvider(activity, SwitchViewModel.Factory)[SwitchViewModel::class.java]
        }
        return checkNotNull(vm)
    }

    private fun awaitListTag(listTag: String, tag: String, timeoutMs: Long = 5_000L) {
        val matcher = hasTestTag(tag) or hasAnyDescendant(hasTestTag(tag))
        composeRule.awaitLoopbackIo(timeoutMs = timeoutMs) {
            runCatching {
                composeRule.onNodeWithTag(listTag).performScrollToNode(matcher)
                true
            }.getOrDefault(false)
        }
    }

    private fun awaitTagDisplayed(tag: String, timeoutMs: Long = 5_000L) {
        composeRule.awaitLoopbackIo(timeoutMs = timeoutMs) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun resetToCleanRealLan(scenario: ActivityScenario<MainActivity>): SwitchViewModel {
        val vm = viewModelFor(scenario)
        vm.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
        awaitListTag("devices_list", "empty_devices_state_card")
        return vm
    }

    private fun insertDevice(
        name: String, room: String, homeKey: String, type: DeviceType,
        port: Int, serial: String, isOn: Boolean = false, brightness: Int = 100,
        autoOffMinutes: Int = 0, sortOrder: Int = 0
    ): Int = runBlocking {
        dao().insertDevice(
            DeviceEntity(
                name = name, roomName = room, networkKey = homeKey, deviceType = type.name,
                ipAddress = "127.0.0.1", port = port, isOn = isOn, isReachable = true,
                brightness = brightness, serialNumber = serial, autoOffMinutes = autoOffMinutes,
                sortOrder = sortOrder
            )
        ).toInt()
    }

    @Test
    fun multiHomeCreationRoomCollapseFlatListFilterAndBackNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitListTag("devices_list", "wifi_home_selector_bar")

            // 1. Create a new Wi-Fi Home via AddWifiHomeNetworkDialog -> becomes active & empty
            composeRule.onNodeWithTag("add_wifi_home_button").performScrollTo().performClick()
            awaitTagDisplayed("wifi_home_name_input")
            composeRule.onNodeWithTag("wifi_home_name_input").performTextReplacement("Beach House")
            composeRule.onNodeWithTag("wifi_home_ssid_input").performTextReplacement("Beach-WiFi-5G")
            composeRule.onNodeWithTag("wifi_home_subnet_input").performTextReplacement("192.168.77")
            composeRule.onNodeWithTag("save_wifi_home_button").performClick()

            awaitListTag("devices_list", "empty_devices_state_card")
            awaitListTag("devices_list", "wifi_home_selector_bar")
            composeRule.onNodeWithText("Beach House (0)").performScrollTo().assertIsDisplayed()

            // 2. Switch back to Main Home and collapse/expand Living Room
            composeRule.onNodeWithTag("home_chip_$DEFAULT_MAIN_HOME_KEY").performScrollTo().performClick()
            awaitListTag("devices_list", "device_row_1")
            awaitListTag("devices_list", "room_header_Living Room")
            composeRule.onNodeWithTag("room_collapse_button_Living Room").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("device_row_1").assertDoesNotExist()
            composeRule.onNodeWithTag("room_collapse_button_Living Room").performClick()
            awaitListTag("devices_list", "device_row_1")

            // 3. Filter to Kitchen, switch Grouped -> Flat List -> Grouped, then reset to All Rooms
            awaitListTag("devices_list", "room_filter_bar")
            composeRule.onNodeWithTag("room_filter_Kitchen").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("room_header_Living Room").assertDoesNotExist()
            awaitListTag("devices_list", "room_header_Kitchen")

            awaitListTag("devices_list", "room_filter_bar")
            composeRule.onNodeWithTag("toggle_group_by_room_chip").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("room_header_Kitchen").assertDoesNotExist()
            composeRule.onNodeWithText("Flat List").assertIsDisplayed()

            composeRule.onNodeWithTag("toggle_group_by_room_chip").performScrollTo().performClick()
            awaitListTag("devices_list", "room_header_Kitchen")
            awaitListTag("devices_list", "room_filter_bar")
            composeRule.onNodeWithTag("room_filter_All").performScrollTo().performClick()
            awaitListTag("devices_list", "room_header_Living Room")

            // 4. Context-sensitive header '+' on Rules tab & 'Edit' auto-switching back to Devices
            composeRule.onNodeWithTag("nav_tab_rules").performClick()
            awaitTagDisplayed("rules_list")
            composeRule.onNodeWithTag("add_device_button").performClick()
            awaitTagDisplayed("rule_name_input")
            composeRule.onNodeWithText("Cancel").performClick()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("edit_devices_button").performClick()
            awaitListTag("devices_list", "device_row_1")
            composeRule.onNodeWithTag("delete_device_1").assertIsDisplayed()

            // 5. System BackHandler unwinds Edit Mode -> Room Filter -> Non-Devices Tab
            awaitListTag("devices_list", "room_filter_bar")
            composeRule.onNodeWithTag("room_filter_Kitchen").performScrollTo().performClick()
            composeRule.waitForIdle()

            // Back #1 exits Edit Mode while keeping Kitchen filter
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("delete_device_2").assertDoesNotExist()
            composeRule.onNodeWithTag("room_header_Living Room").assertDoesNotExist()

            // Back #2 clears Kitchen filter back to All Rooms
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitListTag("devices_list", "room_header_Living Room")

            // Back #3 from More tab returns to Devices tab
            composeRule.onNodeWithTag("nav_tab_more").performClick()
            awaitTagDisplayed("more_screen_list")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            awaitTagDisplayed("devices_list")
        }
    }

    @Test
    fun realLanHardwareRenameRoomMoveReorderDownAndDeletePrunesCountdown() {
        FakeUpnpServer(
            initialFriendlyName = "Patio Lamp", modelName = "WLS040",
            serialNumber = "WLS040MGT01", udn = "uuid:Lightswitch-3_0-WLS040MGT01", initialRelayOn = true
        ).use { lampServer ->
            FakeUpnpServer(
                initialFriendlyName = "Patio Fan", modelName = "WLS040",
                serialNumber = "WLS040MGT02", udn = "uuid:Lightswitch-3_0-WLS040MGT02", initialRelayOn = false
            ).use { fanServer ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val vm = resetToCleanRealLan(scenario)
                    val activeHome = vm.activeNetworkKey.value
                    val lampId = insertDevice(
                        "Patio Lamp", "Patio", activeHome, DeviceType.SWITCH_PLUG,
                        lampServer.port, "WLS040MGT01", isOn = true, autoOffMinutes = 15, sortOrder = 0
                    )
                    val fanId = insertDevice(
                        "Patio Fan", "Patio", activeHome, DeviceType.SWITCH_PLUG,
                        fanServer.port, "WLS040MGT02", isOn = false, sortOrder = 1
                    )
                    awaitListTag("devices_list", "device_row_$lampId")
                    composeRule.awaitLoopbackIo {
                        vm.activeAutoOffCountdownsSec.value.containsKey(lampId) &&
                            runCatching {
                                composeRule.onNodeWithTag("auto_off_countdown_$lampId", useUnmergedTree = true)
                                    .assertIsDisplayed()
                                true
                            }.getOrDefault(false)
                    }

                    // 1. Edit Patio Fan with Hardware NVRAM Sync ON + move into custom room 'Sunroom'
                    composeRule.onNodeWithTag("edit_devices_button").performClick()
                    awaitListTag("devices_list", "device_row_$fanId")
                    composeRule.onNodeWithTag("edit_device_$fanId").performClick()
                    awaitTagDisplayed("device_name_input")
                    composeRule.onNodeWithTag("device_name_input").performScrollTo().performTextReplacement("Deck Breeze Fan")
                    composeRule.onNodeWithTag("device_room_input").performScrollTo().performTextReplacement("Sunroom")
                    composeRule.onNodeWithTag("save_device_button").performClick()

                    composeRule.awaitLoopbackIo { fanServer.friendlyName.get() == "Deck Breeze Fan" }
                    awaitListTag("devices_list", "room_header_Sunroom")

                    // 2. Edit again with Hardware NVRAM Sync OFF -> renames locally & moves back to Patio
                    awaitListTag("devices_list", "device_row_$fanId")
                    composeRule.onNodeWithTag("edit_device_$fanId").performClick()
                    awaitTagDisplayed("sync_hardware_name_switch")
                    composeRule.onNodeWithTag("sync_hardware_name_switch").performScrollTo().performClick()
                    composeRule.onNodeWithTag("device_name_input").performScrollTo().performTextReplacement("Local Patio Fan")
                    composeRule.onNodeWithTag("device_room_input").performScrollTo().performTextReplacement("Patio")
                    composeRule.onNodeWithTag("save_device_button").performClick()

                    composeRule.awaitLoopbackIo {
                        runBlocking {
                            val row = dao().getDeviceById(fanId)
                            row?.name == "Local Patio Fan" && row.roomName == "Patio"
                        }
                    }
                    assertEquals("Deck Breeze Fan", fanServer.friendlyName.get())

                    // 3. Move Down on Patio Lamp swaps sortOrder below Local Patio Fan inside Patio
                    awaitListTag("devices_list", "device_row_$lampId")
                    composeRule.onNodeWithTag("move_down_$lampId").performClick()
                    composeRule.awaitLoopbackIo {
                        runBlocking {
                            (dao().getDeviceById(lampId)?.sortOrder ?: -1) >
                                (dao().getDeviceById(fanId)?.sortOrder ?: -1)
                        }
                    }

                    // 4. Delete Patio Lamp via Edit Mode -> removes row & prunes active Auto-Off countdown
                    composeRule.onNodeWithTag("delete_device_$lampId").performClick()
                    composeRule.awaitLoopbackIo {
                        runBlocking { dao().getDeviceById(lampId) == null } &&
                            !vm.activeAutoOffCountdownsSec.value.containsKey(lampId)
                    }
                    composeRule.onNodeWithTag("device_row_$lampId").assertDoesNotExist()
                }
            }
        }
    }

    @Test
    fun realLanBulkSsdpAdoptAllMoreMasterControlsLogClearAndModeSwitch() {
        FakeUpnpServer(
            initialFriendlyName = "Porch Spot", modelName = "WLS040",
            serialNumber = "WLS040BULK01", udn = "uuid:Lightswitch-3_0-WLS040BULK01",
            initialRelayOn = false, enableSsdpResponder = true
        ).use { spotServer ->
            FakeUpnpServer(
                initialFriendlyName = "Porch Accent", modelName = "WLS040",
                serialNumber = "WLS040BULK02", udn = "uuid:Lightswitch-3_0-WLS040BULK02",
                initialRelayOn = false, enableSsdpResponder = true
            ).use { accentServer ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val vm = resetToCleanRealLan(scenario)

                    // 1. Open Add Device dialog -> launch SSDP scanner -> switch Search Target -> Rescan -> Adopt All
                    composeRule.onNodeWithTag("add_device_button").performClick()
                    awaitTagDisplayed("scan_wifi_ssdp_button")
                    composeRule.onNodeWithTag("scan_wifi_ssdp_button").performClick()
                    awaitTagDisplayed("ssdp_scanner_sheet")
                    awaitListTag("ssdp_discovered_list", "ssdp_item_Porch Spot")
                    awaitListTag("ssdp_discovered_list", "ssdp_item_Porch Accent")

                    composeRule.onNodeWithTag("ssdp_discovered_list").performScrollToIndex(0)
                    composeRule.onNodeWithText("All Smart Switch Devices").performScrollTo().performClick()
                    assertEquals("urn:Belkin:device:**", vm.selectedSearchTarget.value)
                    composeRule.awaitLoopbackIo {
                        !vm.isRefreshing.value &&
                            spotServer.receivedSsdpSearches.any { it.contains("urn:Belkin:device:**") } &&
                            accentServer.receivedSsdpSearches.any { it.contains("urn:Belkin:device:**") }
                    }

                    val searchesBeforeRescan = spotServer.receivedSsdpSearches.size
                    composeRule.onNodeWithTag("trigger_ssdp_scan_button").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo {
                        !vm.isRefreshing.value && spotServer.receivedSsdpSearches.size > searchesBeforeRescan
                    }
                    composeRule.onNodeWithTag("sync_all_ssdp_button").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo { runBlocking { dao().getDeviceCount() == 2 } }
                    composeRule.onNodeWithTag("close_ssdp_scanner_button").performScrollTo().performClick()
                    composeRule.waitForIdle()

                    // 2. More tab: All Switches ON / OFF over loopback SOAP + Clear SOAP Log
                    composeRule.onNodeWithTag("nav_tab_more").performClick()
                    awaitListTag("more_screen_list", "all_switches_on_button")
                    composeRule.onNodeWithTag("all_switches_on_button").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo { spotServer.relayStateOn.get() && accentServer.relayStateOn.get() }

                    composeRule.onNodeWithTag("all_switches_off_button").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo { !spotServer.relayStateOn.get() && !accentServer.relayStateOn.get() }

                    awaitListTag("more_screen_list", "clear_soap_logs_button")
                    composeRule.onNodeWithTag("clear_soap_logs_button").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo {
                        runCatching {
                            composeRule.onNodeWithText("UPnP / SOAP Packet Log (0)").assertIsDisplayed()
                            true
                        }.getOrDefault(false)
                    }

                    // 3. Toggle Demo Showroom Mode ON (14 devices) and back OFF (clean Real LAN Mode)
                    awaitListTag("more_screen_list", "demo_showroom_mode_switch")
                    composeRule.onNodeWithTag("demo_showroom_mode_switch").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo { runBlocking { dao().getDeviceCount() == 14 } }
                    composeRule.onNodeWithTag("demo_showroom_mode_switch").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo { runBlocking { dao().getDeviceCount() == 0 } }
                }
            }
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w840dp-h600dp-land")
    fun realLanMotionTriggerDimmerRuleDeletionAndTabletLayout() {
        FakeUpnpServer(
            initialFriendlyName = "Hallway Sconce", deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060", serialNumber = "WDS060MOT01", udn = "uuid:Dimmer-1_0-WDS060MOT01",
            initialRelayOn = false, initialBrightness = 100
        ).use { dimmerServer ->
            FakeUpnpServer(
                initialFriendlyName = "Cabin Porch Light", modelName = "WLS040",
                serialNumber = "WLS040CAB02", udn = "uuid:Lightswitch-3_0-WLS040CAB02", initialRelayOn = false
            ).use { cabinServer ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val vm = resetToCleanRealLan(scenario)
                    val activeHome = vm.activeNetworkKey.value

                    // Verify 10-inch Tablet landscape TopHeader keeps >= 48dp touch target height
                    val addBounds = composeRule.onNodeWithTag("add_device_button").getUnclippedBoundsInRoot()
                    assertTrue(
                        "Expected >= 48dp header touch height on tablet, got ${addBounds.bottom - addBounds.top}",
                        (addBounds.bottom - addBounds.top) >= 48.dp
                    )

                    val dimmerId = insertDevice(
                        "Hallway Sconce", "Hallway & Landing", activeHome, DeviceType.LIGHTBULB_DIMMER,
                        dimmerServer.port, "WDS060MOT01", isOn = false, brightness = 100
                    )
                    val sensorId = insertDevice(
                        "Hallway Motion", "Hallway & Landing", activeHome, DeviceType.MOTION_SENSOR,
                        49159, "SENSORHAL01"
                    )
                    val cabinId = insertDevice(
                        "Cabin Porch Light", "Porch", DEFAULT_CABIN_HOME_KEY, DeviceType.SWITCH_PLUG,
                        cabinServer.port, "WLS040CAB02", isOn = false
                    )
                    // Seed a foreign-home motion rule that must NOT fire when Hallway Motion triggers
                    runBlocking {
                        dao().insertRule(
                            RuleEntity(
                                name = "Foreign Cabin Motion", ruleType = "MOTION_TRIGGER",
                                targetDeviceId = cabinId, targetDeviceName = "Cabin Porch Light",
                                triggerSummary = "Cabin motion", actionSummary = "Turn ON Cabin Porch Light",
                                targetStateOn = true, isEnabled = true
                            )
                        )
                    }

                    // 1. Create a Motion Trigger rule targeting Hallway Sconce dimmer via RuleDialog
                    composeRule.onNodeWithTag("nav_tab_rules").performClick()
                    awaitTagDisplayed("add_rule_button")
                    composeRule.onNodeWithTag("add_rule_button").performClick()
                    awaitTagDisplayed("rule_name_input")
                    composeRule.onNodeWithTag("rule_name_input").performTextReplacement("Hallway Night Glow")
                    composeRule.onNodeWithText("Motion Trigger").performClick()
                    composeRule.waitForIdle()
                    composeRule.onNodeWithTag("rule_brightness_slider").performScrollTo().performTouchInput {
                        click(percentOffset(0.45f, 0.5f))
                    }
                    composeRule.onNodeWithTag("save_rule_button").performClick()

                    var createdRuleId = -1
                    composeRule.awaitLoopbackIo {
                        val rule = runBlocking { dao().getAllRules().first().firstOrNull { it.name == "Hallway Night Glow" } }
                        if (rule != null) { createdRuleId = rule.id; true } else false
                    }

                    // 2. Tap Motion Sensor row -> fires real loopback dimmer SOAP (~45%) & isolates Cabin Home
                    composeRule.onNodeWithTag("nav_tab_devices").performClick()
                    awaitListTag("devices_list", "device_row_$sensorId")
                    composeRule.onNodeWithTag("device_name_$sensorId", useUnmergedTree = true).performClick()

                    composeRule.awaitLoopbackIo {
                        dimmerServer.relayStateOn.get() && dimmerServer.dimmerBrightness.get() in 30..60
                    }
                    assertFalse("Foreign-home motion rule must stay isolated", cabinServer.relayStateOn.get())
                    assertTrue(runBlocking { dao().getDeviceById(dimmerId)?.isOn == true })

                    // 3. Delete the created rule via RulesScreen delete_rule_<id> button
                    composeRule.onNodeWithTag("nav_tab_rules").performClick()
                    awaitListTag("rules_list", "rule_row_$createdRuleId")
                    composeRule.onNodeWithTag("delete_rule_$createdRuleId").performClick()
                    composeRule.awaitLoopbackIo {
                        runBlocking { dao().getAllRules().first().none { it.id == createdRuleId } }
                    }
                    composeRule.onNodeWithTag("rule_row_$createdRuleId").assertDoesNotExist()
                    assertNull(runBlocking { dao().getAllRules().first().find { it.id == createdRuleId } })
                }
            }
        }
    }
}
