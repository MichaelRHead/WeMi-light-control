package org.suppressingfire.wemi

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_CABIN_HOME_KEY
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.widget.SwitchWidgetConfigureActivity
import org.suppressingfire.wemi.widget.WidgetConfigStore
import org.suppressingfire.wemi.widget.WidgetTargetType

/**
 * Full-stack Jetpack Compose UI E2E tests for CUJ 8 (Home Screen Light & Room Toggle Widgets):
 * 1. `SwitchWidgetConfigureActivity` Room picker, Light picker, multi-home reconfiguration across
 *    remembered Wi-Fi Homes, and Cancel (`RESULT_CANCELED`) preserving existing config.
 * 2. `MainActivity` in-app Room (`room_pin_widget_button_*`) and Device (`pin_device_widget_*`)
 *    pinning flows with `status_feedback_banner` verification and motion-sensor exclusion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreenWidgetUiE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WidgetConfigStore.clearAll(context)
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        WidgetConfigStore.clearAll(context)
        SwitchDatabase.closeCurrentInstance()
    }

    private fun configureIntent(appWidgetId: Int): Intent =
        Intent(context, SwitchWidgetConfigureActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

    @Test
    fun widgetConfigureActivitySupportsRoomLightReconfigurationAcrossHomesAndCancel() {
        // 1. Fresh Room widget binding -> returns RESULT_OK with ROOM config
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(555)).use { scenario ->
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNodeWithTag("widget_config_room_Living Room").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("widget_config_room_Living Room").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(
                555,
                scenario.result.resultData?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            )
            val savedRoom = WidgetConfigStore.load(context, 555)
            assertNotNull(savedRoom)
            assertEquals(WidgetTargetType.ROOM, savedRoom?.targetType)
            assertEquals("Living Room", savedRoom?.normalizedRoomName())
            assertEquals(DEFAULT_MAIN_HOME_KEY, savedRoom?.networkKey)
        }

        // 2. Fresh individual Light binding -> switch to Lights tab and select Christmas Tree Living (id = 1)
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(556)).use { scenario ->
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNodeWithTag("widget_config_tab_lights").assertIsDisplayed()
                    composeRule.onNodeWithTag("widget_config_room_Living Room").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("widget_config_tab_lights").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("widget_config_lights_list")
                .performScrollToNode(hasTestTag("widget_config_device_1"))
            composeRule.onNodeWithTag("widget_config_device_1").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            val savedDevice = WidgetConfigStore.load(context, 556)
            assertNotNull(savedDevice)
            assertEquals(WidgetTargetType.DEVICE, savedDevice?.targetType)
            assertEquals(1, savedDevice?.deviceId)
            assertEquals("221517K01018A1", savedDevice?.serialNumber)
            assertEquals(DEFAULT_MAIN_HOME_KEY, savedDevice?.networkKey)
        }

        // 3. Reconfigure widget 556 -> opens pre-selected on Lights tab, switch to Lake Cabin & rebind to id = 9
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(556)).use { scenario ->
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNodeWithTag("widget_config_lights_list").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("widget_config_home_$DEFAULT_CABIN_HOME_KEY")
                .performScrollTo()
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("widget_config_lights_list")
                .performScrollToNode(hasTestTag("widget_config_device_9"))
            composeRule.onNodeWithTag("widget_config_device_9").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            val reconfigured = WidgetConfigStore.load(context, 556)
            assertEquals(WidgetTargetType.DEVICE, reconfigured?.targetType)
            assertEquals(9, reconfigured?.deviceId)
            assertEquals("221517K01050C1", reconfigured?.serialNumber)
            assertEquals(DEFAULT_CABIN_HOME_KEY, reconfigured?.networkKey)
            assertEquals("Cabin Porch Floodlight", reconfigured?.displayName)
        }

        // 4. Cancel during reconfiguration -> returns RESULT_CANCELED and leaves existing binding intact
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(556)).use { scenario ->
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNodeWithTag("widget_config_cancel_button").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("widget_config_cancel_button").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
            assertEquals(9, WidgetConfigStore.load(context, 556)?.deviceId)
        }
    }

    @Test
    fun mainActivityInAppRoomAndDeviceWidgetPinButtonsDisplayFeedbackAndHideMotionSensors() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasTestTag("room_header_Living Room"))
                    true
                }.getOrDefault(false)
            }

            // 1. Tap one-tap Room Widget Pin button on Living Room header -> displays feedback banner
            composeRule.onNodeWithTag("room_pin_widget_button_Living Room")
                .assertIsDisplayed()
                .performClick()
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNode(
                        hasTestTag("status_feedback_banner") and
                            hasAnyDescendant(hasText("Living Room", substring = true))
                    ).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            // 2. Expand Christmas Tree Living (id = 1) drawer and tap Add Widget -> displays feedback banner
            composeRule.onNodeWithTag("devices_list")
                .performScrollToNode(hasTestTag("device_row_1"))
            composeRule.onNodeWithText("Christmas Tree Living").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("pin_device_widget_1")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNode(
                        hasTestTag("status_feedback_banner") and
                            hasAnyDescendant(hasText("Christmas Tree Living", substring = true))
                    ).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            // 3. Expand Bedroom Light Motion (id = 3, MOTION_SENSOR) and verify no power-widget pin chip is rendered
            composeRule.onNodeWithTag("devices_list")
                .performScrollToNode(hasTestTag("device_row_3"))
            composeRule.onNodeWithText("Bedroom Light Motion").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("pin_device_widget_3").assertDoesNotExist()
        }
    }
}
