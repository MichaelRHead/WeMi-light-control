package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ApplicationProvider
import com.example.data.WeMoDeviceType
import com.example.data.inferRoomFromName
import com.example.network.WeMoNetworkClient
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
    fun `verify SSDP packet builder, UPnP setup xml parser, and room inference`() {
        val client = WeMoNetworkClient()
        val mSearch = client.buildSsdpMSearchPacket("urn:Belkin:service:basicevent:1")
        assertTrue(mSearch.contains("M-SEARCH * HTTP/1.1"))
        assertTrue(mSearch.contains("HOST: 239.255.255.250:1900"))
        assertTrue(mSearch.contains("ST: urn:Belkin:service:basicevent:1"))

        assertEquals("Kitchen", inferRoomFromName("Christmas Tree Kitchen"))
        assertEquals("Bedroom", inferRoomFromName("Lightbulb Bedroom"))
        assertEquals("Hallway & Landing", inferRoomFromName("Lightbulb Landing"))

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
        assertEquals(WeMoDeviceType.SWITCH_PLUG, discovered.deviceType)
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

        // Verify Living Room devices and their real-time connection/latency indicators are displayed
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Tree Living"))
        composeTestRule.onNodeWithText("Christmas Tree Living").assertIsDisplayed()
        composeTestRule.onNodeWithTag("connection_status_badge_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Candle"))
        composeTestRule.onNodeWithText("Christmas Candle").assertIsDisplayed()

        // Trigger pull-to-refresh swipeDown gesture on the devices list
        composeTestRule.onNodeWithTag("devices_pull_to_refresh")
            .performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        // Scroll LazyColumn to verify Kitchen room group and devices
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Tree Kitchen"))
        composeTestRule.onNodeWithText("Christmas Tree Kitchen").assertIsDisplayed()

        // Switch to Rules tab and verify rules load
        composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Evening Holiday Lights").assertIsDisplayed()

        // Switch to More tab and verify Master Switch Control & UPnP engine appear
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Master Switch Control").assertIsDisplayed()
        composeTestRule.onNodeWithText("Offline Local UPnP / SOAP Engine").assertIsDisplayed()

        // Launch 10-foot Android TV Remote Mode and verify D-Pad controller and TV scenes
        composeTestRule.onNodeWithTag("header_tv_mode_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_dashboard_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_remote_dpad_ok").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_scene_movie").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_remote_dpad_ok").performClick()
        composeTestRule.waitForIdle()

        // Return to Phone View from Android TV Mode
        composeTestRule.onNodeWithTag("tv_exit_button").performClick()
        composeTestRule.waitForIdle()
    }
}
