package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
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
    fun `verify Room grouping system, master room toggle, pull to refresh, and SSDP scanner`() {
        composeTestRule.waitForIdle()

        // Verify pull-to-refresh container, SSDP discovery banner, and Room filter bar exist
        composeTestRule.onNodeWithTag("devices_pull_to_refresh").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ssdp_discovery_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_filter_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_group_by_room_chip").assertIsDisplayed()

        // Verify Living Room group header and its master power toggle ring are displayed
        composeTestRule.onNodeWithTag("room_header_Living Room").assertIsDisplayed()
        composeTestRule.onNodeWithTag("room_power_toggle_Living Room").assertIsDisplayed()

        // Toggle all switches in Living Room at once using the Room Master Power Toggle
        composeTestRule.onNodeWithTag("room_power_toggle_Living Room").performClick()
        composeTestRule.waitForIdle()

        // Verify Living Room devices are displayed under the group
        composeTestRule.onNodeWithText("Christmas Tree Living").assertIsDisplayed()
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
    }
}
