package org.suppressingfire.wemi

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import org.suppressingfire.wemi.data.DeviceCategory
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.classifyDeviceCategory
import org.suppressingfire.wemi.data.inferRoomFromName
import org.suppressingfire.wemi.network.NetworkClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `verify app name matches Play Store compliant title`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("WeMi Switch Control", appName)
    }

    @Test
    fun `verify SSDP packet builder, UPnP setup xml parser, room inference, and name-based category classification`() {
        val client = NetworkClient()
        val mSearch = client.buildSsdpMSearchPacket("urn:Belkin:service:basicevent:1")
        assertTrue(mSearch.contains("M-SEARCH * HTTP/1.1"))
        assertTrue(mSearch.contains("HOST: 239.255.255.250:1900"))
        assertTrue(mSearch.contains("ST: urn:Belkin:service:basicevent:1"))

        assertEquals("Kitchen", inferRoomFromName("Christmas Tree Kitchen"))
        assertEquals("Bedroom", inferRoomFromName("Lightbulb Bedroom"))
        assertEquals("Hallway & Landing", inferRoomFromName("Lightbulb Landing"))

        // Verify semantic icon classification from switch name
        assertEquals(DeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Christmas Tree Living"))
        assertEquals(DeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Holiday Tree Lights"))
        assertEquals(DeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Porch Christmas Garland"))
        assertEquals(DeviceCategory.FAN, classifyDeviceCategory("Bedroom Ceiling Fan"))
        assertEquals(DeviceCategory.FAN, classifyDeviceCategory("Attic Exhaust Fan"))
        assertEquals(DeviceCategory.FAN, classifyDeviceCategory("Utility Room Vent"))
        assertEquals(DeviceCategory.LIGHT, classifyDeviceCategory("Living Room Floor Lamp"))
        assertEquals(DeviceCategory.LIGHT, classifyDeviceCategory("Cabin Porch Floodlight"))
        assertEquals(DeviceCategory.LIGHT, classifyDeviceCategory("Kitchen Island Pendant"))
        assertEquals(DeviceCategory.LIGHT, classifyDeviceCategory("Christmas Candle"))
        assertEquals(DeviceCategory.MOTION_SENSOR, classifyDeviceCategory("Bedroom Light Motion", DeviceType.MOTION_SENSOR))
        assertEquals(DeviceCategory.SWITCH_PLUG, classifyDeviceCategory("Office Wall Switch"))

        val rawSsdpResponse = """
            HTTP/1.1 200 OK
            CACHE-CONTROL: max-age=86400
            LOCATION: http://192.168.1.122:49153/setup.xml
            ST: urn:Belkin:service:basicevent:1
            USN: uuid:Lightswitch-1_0-221517K01018FF::urn:Belkin:service:basicevent:1
        """.trimIndent()
        val headers = client.parseSsdpHeaders(rawSsdpResponse)
        assertEquals("http://192.168.1.122:49153/setup.xml", headers["LOCATION"])
        assertEquals("urn:Belkin:service:basicevent:1", headers["ST"])

        val setupXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:Belkin:device-1-0">
              <device>
                <deviceType>urn:Belkin:device:lightswitch:1</deviceType>
                <friendlyName>Office Wall Switch</friendlyName>
                <modelName>LightSwitch</modelName>
                <serialNumber>221517K01018FF</serialNumber>
                <firmwareVersion>WeMo_WW_2.00.11532.PVT</firmwareVersion>
                <UDN>uuid:Lightswitch-1_0-221517K01018FF</UDN>
              </device>
            </root>
        """.trimIndent()
        val discovered = client.parseSetupXmlDescriptor(
            xml = setupXml,
            locationUrl = headers["LOCATION"].orEmpty(),
            usnHeader = headers["USN"].orEmpty(),
            stHeader = headers["ST"].orEmpty(),
            responseTimeMs = 12L,
            isLiveUdp = true
        )
        assertEquals("Office Wall Switch", discovered.name)
        assertEquals("Office", discovered.roomName)
        assertEquals("192.168.1.122", discovered.ipAddress)
        assertEquals(49153, discovered.port)
        assertEquals(DeviceType.SWITCH_PLUG, discovered.deviceType)
        assertEquals(DeviceCategory.SWITCH_PLUG, discovered.visualCategory)
    }

    @Test
    fun `verify Wi-Fi Home network memory, auto-reconnect, Room grouping, and SSDP scanner`() {
        composeTestRule.waitForIdle()

        // Verify Wi-Fi Home selector bar, pull-to-refresh container, and SSDP discovery banner at top
        composeTestRule.onNodeWithTag("wifi_home_selector_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reconnect_home_wifi_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("devices_pull_to_refresh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ssdp_discovery_banner").assertIsDisplayed()

        // Switch to the remembered Lake Cabin Wi-Fi network ("PineLake-Cabin-WiFi") and verify remembered switches load & reconnect
        composeTestRule.onNodeWithTag("home_chip_wifi_cabin_192_168_50").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Cabin Porch Floodlight"))
        composeTestRule.onNodeWithText("Cabin Porch Floodlight").assertIsDisplayed()

        // Switch back to Main Home Wi-Fi network ("WeMi-Home-5G") and trigger one-tap Reconnect
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
        composeTestRule.onNodeWithTag("home_chip_wifi_home_192_168_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("reconnect_home_wifi_button").performClick()
        composeTestRule.waitForIdle()

        // Scroll to Room filter bar and verify Room grouping controls
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("room_filter_bar"))
        composeTestRule.onNodeWithTag("room_filter_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_group_by_room_chip").assertIsDisplayed()

        // Scroll to Living Room group header and verify its master power toggle ring is displayed
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("room_header_Living Room"))
        composeTestRule.onNodeWithTag("room_header_Living Room").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_power_toggle_Living Room").assertIsDisplayed()

        // Toggle all switches in Living Room at once using the Room Master Power Toggle
        composeTestRule.onNodeWithTag("room_power_toggle_Living Room").performClick()
        composeTestRule.waitForIdle()

        // Verify Living Room devices and their real-time connection, power-state, and semantic category badges
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Tree Living"))
        composeTestRule.onNodeWithText("Christmas Tree Living").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connection_status_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_state_badge_1", useUnmergedTree = true).assertIsDisplayed()

        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Candle"))
        composeTestRule.onNodeWithText("Christmas Candle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_badge_2", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_state_badge_2", useUnmergedTree = true).assertIsDisplayed()

        // Scroll to Bedroom Ceiling Fan and verify its Fan category badge and OFF power state badge
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Bedroom Ceiling Fan"))
        composeTestRule.onNodeWithText("Bedroom Ceiling Fan").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_badge_14", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_state_badge_14", useUnmergedTree = true).assertIsDisplayed()

        // Trigger pull-to-refresh swipeDown gesture on the devices list
        composeTestRule.onNodeWithTag("devices_pull_to_refresh")
            .performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        // Scroll LazyColumn to verify Kitchen room group and devices
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Tree Kitchen"))
        composeTestRule.onNodeWithText("Christmas Tree Kitchen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("category_badge_4", useUnmergedTree = true).assertIsDisplayed()

        // Switch to Rules tab and verify rules load
        composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Evening Holiday Lights").assertIsDisplayed()

        // Switch to More tab and verify Master Switch Control & UPnP engine appear
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Master Switch Control").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline Local UPnP / SOAP Engine").assertIsDisplayed()
    }

    @Test
    fun `verify 3-state ON vs OFF Accessible Standby vs Disconnected switch UI and semantic badges`() {
        composeTestRule.waitForIdle()

        // 1. Scroll to Christmas Tree Living (id=1, reachable ON, CHRISTMAS_TREE category)
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_1"))
        composeTestRule.onNodeWithTag("category_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("connection_status_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_state_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_button_1").assertIsDisplayed()

        // Toggle Christmas Tree Living OFF -> stays reachable in Accessible Standby OFF state
        composeTestRule.onNodeWithTag("power_button_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("connection_status_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_state_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_button_1").assertIsDisplayed()

        // 2. Scroll to Bedroom Ceiling Fan (id=14, reachable OFF standby, FAN category)
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_14"))
        composeTestRule.onNodeWithTag("category_badge_14", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("connection_status_badge_14", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_state_badge_14", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("power_button_14").assertIsDisplayed()

        // Toggle Bedroom Ceiling Fan ON -> transitions from OFF standby to ON
        composeTestRule.onNodeWithTag("power_button_14").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("power_state_badge_14", useUnmergedTree = true).assertIsDisplayed()

        // 3. Scroll to Lightbulb Bedroom (id=5, Disconnected / !isReachable, LIGHT category)
        // Verify it renders the Disconnected connection badge and Retry button instead of the ON/OFF power ring
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_5"))
        composeTestRule.onNodeWithTag("category_badge_5", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("connection_status_badge_5", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("reconnect_button_5").assertIsDisplayed()
    }

    @Test
    fun `verify fresh-device setup wizard (ProvisionApDialog) scans APs, previews semantic icon, provisions switch, and adds it to device list`() {
        composeTestRule.waitForIdle()

        // Open Add Device dialog via add_device_button -> launch Setup Factory-Reset Switch AP (10.22.22.1) wizard
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_provision_ap_wizard_button").performClick()
        composeTestRule.waitForIdle()

        // Verify ProvisionApDialog is displayed and shows scanned APs / status box
        composeTestRule.onNodeWithTag("provision_ap_status_box").assertIsDisplayed()
        composeTestRule.onNodeWithTag("provision_status_box", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("provision_ap_ip_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("scan_setup_ap_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("provision_ssid_input").performScrollTo().assertIsDisplayed()

        // Enter a custom name ("Porch Christmas Tree") and Wi-Fi password, then click Provision Switch
        composeTestRule.onNodeWithTag("provision_password_input")
            .performScrollTo()
            .performTextReplacement("HomeWifiPass123")
        composeTestRule.onNodeWithTag("provision_device_name_input")
            .performScrollTo()
            .performTextReplacement("Porch Christmas Tree")
        composeTestRule.waitForIdle()

        // Verify live semantic category preview updates to Tree Lights (DeviceCategory.CHRISTMAS_TREE.displayName)
        composeTestRule.onNodeWithText("Icon Category: Tree Lights")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("confirm_provision_ap_button").performClick()
        composeTestRule.waitForIdle()

        // Verify the newly provisioned switch is added to the device list
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Porch Christmas Tree"))
        composeTestRule.onNodeWithText("Porch Christmas Tree").assertIsDisplayed()
    }

    @Test
    fun `verify EmptyDevicesState onboarding card on empty Wi-Fi Home and Restore Classic Setup action`() {
        composeTestRule.waitForIdle()

        // Create a new empty Wi-Fi Home ("Beach House") via AddWifiHomeNetworkDialog
        composeTestRule.onNodeWithTag("add_wifi_home_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wifi_home_name_input").performTextReplacement("Beach House")
        composeTestRule.onNodeWithTag("wifi_home_ssid_input").performTextReplacement("Beach-WiFi-2.4G")
        composeTestRule.onNodeWithTag("wifi_home_subnet_input").performTextReplacement("192.168.88")
        composeTestRule.onNodeWithTag("save_wifi_home_button").performClick()
        composeTestRule.waitForIdle()

        // Verify the EmptyDevicesState onboarding card and its 4 actions on the new empty Wi-Fi Home
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("empty_devices_state_card"))
        composeTestRule.onNodeWithTag("empty_devices_state_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("devices_list")
            .performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("empty_state_scan_ssdp_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_provision_ap_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_add_manual_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("empty_state_restore_classic_button").assertIsDisplayed()

        // Restore classic showcase switches and verify they repopulate the Devices list on Main Home
        composeTestRule.onNodeWithTag("empty_state_restore_classic_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Tree Living"))
        composeTestRule.onNodeWithText("Christmas Tree Living").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [36], qualifiers = "television")
    fun `verify native Android TV dashboard launches automatically under UI_MODE_TYPE_TELEVISION without manual layout toggle`() {
        composeTestRule.waitForIdle()

        // Verify Android TV 10-foot dashboard launches automatically with 3-column room-grouped grid
        composeTestRule.onNodeWithTag("tv_dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_device_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_room_header_Living Room").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_remote_dpad_ok").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_scene_movie").assertIsDisplayed()

        // Clicking a switch card toggles power directly
        composeTestRule.onNodeWithTag("tv_device_card_1").performClick()
        composeTestRule.waitForIdle()

        // Scroll to reachable LIGHTBULB_DIMMER (id = 8, Lightbulb Bedroom), enter Dimmer Adjust Mode, and exit cleanly
        composeTestRule.onNodeWithTag("tv_device_grid")
            .performScrollToNode(hasTestTag("tv_device_card_8"))
        composeTestRule.onNodeWithTag("tv_card_dim_mode_8").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_dimmer_mode_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_dimmer_mode_toggle").performClick()
        composeTestRule.waitForIdle()

        // Trigger Movie Night scene on Android TV
        composeTestRule.onNodeWithTag("tv_scene_movie").performClick()
        composeTestRule.waitForIdle()

        // Verify Rescan triggers non-blocking refresh without popping up touch SsdpScannerModal
        composeTestRule.onNodeWithTag("tv_scan_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ssdp_scanner_modal").assertDoesNotExist()
    }
}

