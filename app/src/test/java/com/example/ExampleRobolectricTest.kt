package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    fun `verify classic screenshot devices load and interactive controls work`() {
        composeTestRule.waitForIdle()

        // Verify top header wordmark and buttons exist
        composeTestRule.onNodeWithTag("brand_wordmark_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("refresh_devices_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_devices_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_device_button").assertIsDisplayed()

        // Verify screenshot devices appear in the list
        composeTestRule.onNodeWithText("Christmas Tree Living").assertIsDisplayed()
        composeTestRule.onNodeWithText("Christmas Candle").assertIsDisplayed()

        // Toggle power on Christmas Tree Living
        composeTestRule.onNodeWithTag("power_button_1").performClick()
        composeTestRule.waitForIdle()

        // ScrollLazyColumn to verify lower items from screenshot ("Christmas Tree Kitchen", "Lightbulb Landing")
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Christmas Tree Kitchen"))
        composeTestRule.onNodeWithText("Christmas Tree Kitchen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasText("Lightbulb Landing"))
        composeTestRule.onNodeWithText("Lightbulb Landing").assertIsDisplayed()

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
