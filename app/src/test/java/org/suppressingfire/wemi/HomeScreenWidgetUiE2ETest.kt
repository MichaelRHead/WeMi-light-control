package org.suppressingfire.wemi

import android.app.Activity
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
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
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.suppressingfire.wemi.data.DEFAULT_CABIN_HOME_KEY
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.SharedSoapLogStore
import org.suppressingfire.wemi.ui.SwitchViewModel
import org.suppressingfire.wemi.widget.SwitchWidgetConfigureActivity
import org.suppressingfire.wemi.widget.WeMiSwitchWidgetProvider
import org.suppressingfire.wemi.widget.WeMiWidgetActionReceiver
import org.suppressingfire.wemi.widget.WidgetActionExecutor
import org.suppressingfire.wemi.widget.WidgetConfigStore
import org.suppressingfire.wemi.widget.WidgetTargetType
import org.suppressingfire.wemi.widget.WidgetUpdater

/**
 * Full-stack Jetpack Compose & Android AppWidgetHost E2E tests for CUJ 8 (Home Screen Light & Room
 * Toggle Widgets — installation, rendering, real `PendingIntent` clicking, loopback UPnP SOAP
 * execution, bi-directional app sync, 9-port offline recovery, and removal):
 * 1. `SwitchWidgetConfigureActivity` Room/Light selection, multi-home reconfiguration, and Cancel.
 * 2. In-app one-tap pinning (`AppWidgetManager.requestPinAppWidget` + `ACTION_WIDGET_PINNED`)
 *    installing live Room & Dimmer widgets, tapping rendered `RemoteViews` over loopback SOAP,
 *    bi-directional `MainActivity` sync, and motion-sensor exclusion.
 * 3. Launcher widget drawer installation (`SwitchWidgetConfigureActivity` empty state, invalid ID,
 *    offline port-shifted dimmer placement), headless 9-port self-healing tap, orphaned widget
 *    reconfigure `PendingIntent`, and widget deletion cleanup.
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
        resetWidgetAndPrefsState()
    }

    @After
    fun tearDown() {
        resetWidgetAndPrefsState()
    }

    private fun resetWidgetAndPrefsState() {
        WeMiSwitchWidgetProvider.cancelAndJoinPendingWorkForTests()
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WeMiWidgetActionReceiver.clearPendingPinNoncesForTests()
        WidgetActionExecutor.clearAllWidgetMutexes()
        SharedSoapLogStore.clear(context)
        context.getSharedPreferences("wemi_controller_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        WidgetConfigStore.clearAll(context)
        SwitchDatabase.closeCurrentInstance()
    }

    private fun configureIntent(appWidgetId: Int): Intent =
        Intent(context, SwitchWidgetConfigureActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }

    private fun widgetView(widgetId: Int): View? {
        ShadowLooper.idleMainLooper()
        return Shadows.shadowOf(AppWidgetManager.getInstance(context)).getViewFor(widgetId)
    }

    private fun widgetTitle(widgetId: Int): String? =
        widgetView(widgetId)?.findViewById<TextView>(R.id.widget_title)?.text?.toString()

    private fun widgetSubtitle(widgetId: Int): String? =
        widgetView(widgetId)?.findViewById<TextView>(R.id.widget_subtitle)?.text?.toString()

    private fun clickRenderedWidget(widgetId: Int, viewId: Int = R.id.widget_power_ring) {
        val root = widgetView(widgetId)
        assertNotNull("Expected rendered widget view for id=$widgetId", root)
        val target = root!!.findViewById<View>(viewId)
        assertNotNull("Expected child view $viewId in widget id=$widgetId", target)
        assertTrue("Expected clickable PendingIntent on widget id=$widgetId", target.performClick())
        ShadowLooper.idleMainLooper()
    }

    private fun awaitTagDisplayed(tag: String) {
        composeRule.awaitLoopbackIo {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun awaitBannerContaining(text: String) {
        composeRule.awaitLoopbackIo {
            runCatching {
                composeRule.onNode(
                    hasTestTag("status_feedback_banner") and hasAnyDescendant(hasText(text, substring = true))
                ).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun awaitWidgetState(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            if (condition()) return
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
        assertTrue("Timed out waiting ${timeoutMs}ms for headless widget update", condition())
    }

    private fun insertDevice(
        name: String, room: String, homeKey: String, type: DeviceType,
        port: Int, serial: String, reachable: Boolean = true, brightness: Int = 100
    ): Int = runBlocking {
        SwitchDatabase.getDatabase(context).switchDao().insertDevice(
            DeviceEntity(
                name = name, roomName = room, networkKey = homeKey, deviceType = type.name,
                ipAddress = "127.0.0.1", port = port, isOn = false, isReachable = reachable,
                brightness = brightness, serialNumber = serial
            )
        ).toInt()
    }

    private fun Intent?.widgetIdExtra(): Int =
        this?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID

    @Test
    fun widgetConfigureActivitySupportsRoomLightReconfigurationAcrossHomesAndCancel() {
        // 1. Fresh Room widget binding -> returns RESULT_OK with ROOM config
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(555)).use { scenario ->
            awaitTagDisplayed("widget_config_room_Living Room")
            composeRule.onNodeWithTag("widget_config_room_Living Room").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(555, scenario.result.resultData.widgetIdExtra())
            val savedRoom = WidgetConfigStore.load(context, 555)
            assertEquals(WidgetTargetType.ROOM, savedRoom?.targetType)
            assertEquals("Living Room", savedRoom?.normalizedRoomName())
            assertEquals(DEFAULT_MAIN_HOME_KEY, savedRoom?.networkKey)
        }

        // 2. Fresh Light binding -> Lights tab -> Christmas Tree Living (id = 1)
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(556)).use { scenario ->
            awaitTagDisplayed("widget_config_room_Living Room")
            composeRule.onNodeWithTag("widget_config_tab_lights").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("widget_config_lights_list")
                .performScrollToNode(hasTestTag("widget_config_device_1"))
            composeRule.onNodeWithTag("widget_config_device_1").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            val savedDevice = WidgetConfigStore.load(context, 556)
            assertEquals(WidgetTargetType.DEVICE, savedDevice?.targetType)
            assertEquals(1, savedDevice?.deviceId)
            assertEquals("221517K01018A1", savedDevice?.serialNumber)
            assertEquals(DEFAULT_MAIN_HOME_KEY, savedDevice?.networkKey)
        }

        // 3. Reconfigure widget 556 -> pre-selected on Lights tab -> Lake Cabin -> Cabin Porch Floodlight (id = 9)
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(556)).use { scenario ->
            awaitTagDisplayed("widget_config_lights_list")
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

        // 4. Cancel during reconfiguration -> RESULT_CANCELED, existing binding preserved
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(556)).use { scenario ->
            awaitTagDisplayed("widget_config_cancel_button")
            composeRule.onNodeWithTag("widget_config_cancel_button").performClick()
            composeRule.waitForIdle()

            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
            assertEquals(9, WidgetConfigStore.load(context, 556)?.deviceId)
        }
    }

    @Test
    fun inAppOneTapWidgetPinningInstallsLiveRoomAndDeviceWidgetsAndControlsLoopbackHardware() {
        val shadowWidgetMgr = Shadows.shadowOf(AppWidgetManager.getInstance(context))
        shadowWidgetMgr.setAlwaysRecreateViewsDuringUpdate(true)

        FakeUpnpServer(
            initialFriendlyName = "Patio String Lights",
            modelName = "WLS040",
            serialNumber = "WLS040PIN01",
            udn = "uuid:Lightswitch-3_0-WLS040PIN01",
            initialRelayOn = false,
            enableSsdpResponder = false
        ).use { switchServer ->
            FakeUpnpServer(
                initialFriendlyName = "Patio Edison Dimmer",
                deviceTypeUrn = "urn:Belkin:device:dimmer:1",
                modelName = "WDS060",
                serialNumber = "WDS060PIN02",
                udn = "uuid:Dimmer-1_0-WDS060PIN02",
                initialRelayOn = false,
                initialBrightness = 75,
                enableSsdpResponder = false
            ).use { dimmerServer ->
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    var vm: SwitchViewModel? = null
                    scenario.onActivity { activity ->
                        vm = ViewModelProvider(activity, SwitchViewModel.Factory)[SwitchViewModel::class.java]
                    }

                    // 1. Switch to Real LAN Mode and populate 2 controllable Patio lights + 1 motion sensor
                    vm!!.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
                    composeRule.awaitLoopbackIo {
                        runCatching {
                            composeRule.onNodeWithTag("devices_list")
                                .performScrollToNode(hasTestTag("empty_devices_state_card"))
                            true
                        }.getOrDefault(false)
                    }
                    val activeHome = vm!!.activeNetworkKey.value
                    val switchId = insertDevice(
                        "Patio String Lights", "Patio", activeHome,
                        DeviceType.SWITCH_PLUG, switchServer.port, "WLS040PIN01"
                    )
                    val dimmerId = insertDevice(
                        "Patio Edison Dimmer", "Patio", activeHome,
                        DeviceType.LIGHTBULB_DIMMER, dimmerServer.port, "WDS060PIN02", brightness = 75
                    )
                    val motionId = insertDevice(
                        "Patio Motion Sensor", "Patio", activeHome,
                        DeviceType.MOTION_SENSOR, 49159, "SENSORPIN03"
                    )
                    composeRule.awaitLoopbackIo {
                        runCatching {
                            composeRule.onNodeWithTag("devices_list")
                                .performScrollToNode(hasTestTag("room_header_Patio"))
                            composeRule.onNodeWithTag("room_pin_widget_button_Patio").assertIsDisplayed()
                            true
                        }.getOrDefault(false)
                    }

                    // 2. Unsupported launcher fallback displays manual Long-Press guidance
                    shadowWidgetMgr.setRequestPinAppWidgetSupported(false)
                    composeRule.onNodeWithTag("room_pin_widget_button_Patio").performClick()
                    awaitBannerContaining("Long-press your Home Screen")

                    // 3. Supported launcher one-tap Room Widget pin installs & renders Patio widget
                    shadowWidgetMgr.setRequestPinAppWidgetSupported(true)
                    composeRule.onNodeWithTag("room_pin_widget_button_Patio").performClick()
                    composeRule.awaitLoopbackIo {
                        ShadowLooper.idleMainLooper()
                        val ids = WidgetUpdater.getActiveWidgetIds(context)
                        ids.size == 1 && widgetSubtitle(ids[0]) == "All OFF • 2 lights"
                    }
                    awaitBannerContaining("Confirm on your Home Screen to pin \"Patio\" widget.")
                    val roomWidgetId = WidgetUpdater.getActiveWidgetIds(context).single()
                    assertEquals(WidgetTargetType.ROOM, WidgetConfigStore.load(context, roomWidgetId)?.targetType)
                    assertEquals("Patio", widgetTitle(roomWidgetId))
                    assertTrue(
                        widgetView(roomWidgetId)?.contentDescription?.toString()
                            ?.contains("Tap to turn room on.") == true
                    )

                    // 4. One-tap Dimmer Widget pin installs & renders Dimmer widget; motion sensors excluded
                    composeRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasTestTag("device_row_$dimmerId"))
                    composeRule.onNodeWithText("Patio Edison Dimmer").performClick()
                    composeRule.waitForIdle()
                    composeRule.onNodeWithTag("pin_device_widget_$dimmerId").performScrollTo().performClick()
                    composeRule.awaitLoopbackIo {
                        ShadowLooper.idleMainLooper()
                        val devId = WidgetUpdater.getActiveWidgetIds(context).firstOrNull { it != roomWidgetId }
                        devId != null && widgetSubtitle(devId) == "OFF • Patio"
                    }
                    val deviceWidgetId = WidgetUpdater.getActiveWidgetIds(context).first { it != roomWidgetId }
                    assertEquals("WDS060PIN02", WidgetConfigStore.load(context, deviceWidgetId)?.serialNumber)
                    assertEquals("Patio Edison Dimmer", widgetTitle(deviceWidgetId))

                    composeRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasTestTag("device_row_$motionId"))
                    composeRule.onNodeWithText("Patio Motion Sensor").performClick()
                    composeRule.waitForIdle()
                    composeRule.onNodeWithTag("pin_device_widget_$motionId").assertDoesNotExist()

                    // 5. USE installed Room Widget: tap rendered power ring -> real loopback SOAP turns BOTH ON
                    clickRenderedWidget(roomWidgetId)
                    composeRule.awaitLoopbackIo(timeoutMs = 8_000L) {
                        ShadowLooper.idleMainLooper()
                        switchServer.relayStateOn.get() &&
                            dimmerServer.relayStateOn.get() &&
                            widgetSubtitle(roomWidgetId) == "2/2 ON • Room" &&
                            widgetSubtitle(deviceWidgetId) == "ON • 75%"
                    }
                    composeRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasTestTag("device_row_$switchId"))
                    composeRule.onNodeWithTag("power_state_badge_$switchId", useUnmergedTree = true).assertIsDisplayed()

                    // 6. USE installed Dimmer Widget: tap rendered power ring -> turns dimmer OFF only
                    clickRenderedWidget(deviceWidgetId)
                    composeRule.awaitLoopbackIo(timeoutMs = 8_000L) {
                        ShadowLooper.idleMainLooper()
                        !dimmerServer.relayStateOn.get() &&
                            switchServer.relayStateOn.get() &&
                            widgetSubtitle(deviceWidgetId) == "OFF • Patio" &&
                            widgetSubtitle(roomWidgetId) == "1/2 ON • Room"
                    }

                    // 7. Bi-directional sync: toggle switch OFF inside MainActivity -> widget updates automatically
                    composeRule.onNodeWithTag("power_button_$switchId").performClick()
                    composeRule.awaitLoopbackIo(timeoutMs = 8_000L) {
                        ShadowLooper.idleMainLooper()
                        !switchServer.relayStateOn.get() &&
                            widgetSubtitle(roomWidgetId) == "All OFF • 2 lights"
                    }
                }
            }
        }
    }

    @Test
    fun launcherWidgetDrawerInstallSupportsEmptyStateOfflinePortHealAndReconfigure() {
        val app = context as Application
        val shadowWidgetMgr = Shadows.shadowOf(AppWidgetManager.getInstance(context))
        shadowWidgetMgr.setAlwaysRecreateViewsDuringUpdate(true)
        val providerComponent = ComponentName(context, WeMiSwitchWidgetProvider::class.java)
        val dao = SwitchDatabase.getDatabase(context).switchDao()

        runBlocking {
            val initRepo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
            try {
                initRepo.resetLanHomesSwitchesAndRooms()
            } finally {
                initRepo.close()
            }
        }

        // 1. Negative widget ID exits cleanly inside onCreate with RESULT_CANCELED
        val invalidController = Robolectric.buildActivity(
            SwitchWidgetConfigureActivity::class.java,
            configureIntent(-3)
        ).create()
        assertTrue(invalidController.get().isFinishing)
        assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(invalidController.get()).resultCode)
        invalidController.destroy()

        // 2. Empty Real LAN Mode displays WidgetEmptyPickerCard
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(801)).use { scenario ->
            composeRule.awaitLoopbackIo {
                runCatching {
                    composeRule.onNodeWithText("No Smart Lights Saved Yet").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("widget_config_cancel_button").performClick()
            composeRule.waitForIdle()
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }

        // 3. Install offline port-shifted dimmer via Launcher Widget Drawer -> SwitchWidgetConfigureActivity
        FakeUpnpServer(
            initialFriendlyName = "Porch Sconce",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060DRAWER1",
            udn = "uuid:Dimmer-1_0-WDS060DRAWER1",
            initialRelayOn = false,
            initialBrightness = 60,
            enableSsdpResponder = false,
            registerAsCandidatePort = true
        ).use { server ->
            val porchId = insertDevice(
                name = "Porch Sconce", room = "Porch", homeKey = DEFAULT_MAIN_HOME_KEY,
                type = DeviceType.LIGHTBULB_DIMMER, port = 49159, serial = "WDS060DRAWER1",
                reachable = false, brightness = 60
            )
            shadowWidgetMgr.bindAppWidgetId(801, providerComponent)
            ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(configureIntent(801)).use { scenario ->
                awaitTagDisplayed("widget_config_tab_lights")
                composeRule.onNodeWithTag("widget_config_tab_lights").performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("widget_config_lights_list")
                    .performScrollToNode(hasTestTag("widget_config_device_$porchId"))
                composeRule.onNodeWithTag("widget_config_device_$porchId").performClick()
                composeRule.waitForIdle()

                assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
                assertEquals(801, scenario.result.resultData.widgetIdExtra())
            }
            assertEquals("Porch Sconce", widgetTitle(801))
            assertEquals("Offline • Tap to retry", widgetSubtitle(801))

            // 4. Tap rendered offline widget (no Activity open) -> headless 9-port self-healing turns dimmer ON
            clickRenderedWidget(801)
            awaitWidgetState(timeoutMs = 8_000L) {
                server.relayStateOn.get() && widgetSubtitle(801) == "ON • 60%"
            }
            val healed = runBlocking { dao.getDeviceById(porchId) }
            assertNotNull(healed)
            assertTrue(healed!!.isReachable && healed.isOn)
            assertEquals(server.port, healed.port)

            // 5. Delete switch -> APPWIDGET_UPDATE shows "Tap to reconfigure" -> tap launches picker
            runBlocking { dao.deleteDeviceById(healed.id) }
            context.sendBroadcast(
                Intent(context, WeMiSwitchWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(801))
                }
            )
            awaitWidgetState { widgetSubtitle(801) == "Tap to reconfigure" }

            Shadows.shadowOf(app).clearNextStartedActivities()
            clickRenderedWidget(801, viewId = R.id.widget_root)
            val launchedIntent = Shadows.shadowOf(app).nextStartedActivity
            assertNotNull("Tapping orphaned widget must launch SwitchWidgetConfigureActivity", launchedIntent)
            assertEquals(SwitchWidgetConfigureActivity::class.java.name, launchedIntent.component?.className)
            assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, launchedIntent.action)
            assertEquals(801, launchedIntent.widgetIdExtra())

            // 6. Removing widget from Home Screen (ACTION_APPWIDGET_DELETED carries singular EXTRA_APPWIDGET_ID)
            context.sendBroadcast(
                Intent(context, WeMiSwitchWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_DELETED
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 801)
                }
            )
            ShadowLooper.idleMainLooper()
            assertNull(WidgetConfigStore.load(context, 801))
            assertFalse(WidgetConfigStore.allConfiguredWidgetIds(context).contains(801))
        }
    }
}
