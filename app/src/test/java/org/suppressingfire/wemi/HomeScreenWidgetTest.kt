package org.suppressingfire.wemi

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.widget.SwitchWidgetConfigureActivity
import org.suppressingfire.wemi.widget.WeMiSwitchWidgetProvider
import org.suppressingfire.wemi.widget.WidgetActionExecutor
import org.suppressingfire.wemi.widget.WidgetConfigStore
import org.suppressingfire.wemi.widget.WidgetRemoteViewsBuilder
import org.suppressingfire.wemi.widget.WidgetTargetConfig
import org.suppressingfire.wemi.widget.WidgetTargetType
import org.suppressingfire.wemi.widget.WidgetVisualMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreenWidgetTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WidgetConfigStore.clearAll(context)
        SwitchDatabase.closeCurrentInstance()
    }

    @After
    fun tearDown() {
        WeMiSwitchWidgetProvider.actionRunnerForTests = null
        WeMiSwitchWidgetProvider.repositoryProviderForTests = null
        WidgetConfigStore.clearAll(context)
        SwitchDatabase.closeCurrentInstance()
    }

    @Test
    fun widgetConfigStorePersistsRemapsAndDeletesLightAndRoomBindings() {
        val device = DeviceEntity(
            id = 42,
            name = "Kitchen Dimmer",
            roomName = "Kitchen",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.1.42",
            port = 49153,
            serialNumber = "WDS060KIT42"
        )
        val deviceCfg = WidgetTargetConfig.forDevice(appWidgetId = 101, device = device)
        val roomCfg = WidgetTargetConfig.forRoom(
            appWidgetId = 102,
            roomName = "Living Room",
            networkKey = DEFAULT_MAIN_HOME_KEY
        )

        WidgetConfigStore.save(context, deviceCfg)
        WidgetConfigStore.save(context, roomCfg)

        val loadedDev = WidgetConfigStore.load(context, 101)
        assertNotNull(loadedDev)
        assertEquals(WidgetTargetType.DEVICE, loadedDev?.targetType)
        assertEquals(42, loadedDev?.deviceId)
        assertEquals("WDS060KIT42", loadedDev?.serialNumber)

        val loadedRoom = WidgetConfigStore.load(context, 102)
        assertNotNull(loadedRoom)
        assertEquals(WidgetTargetType.ROOM, loadedRoom?.targetType)
        assertEquals("Living Room", loadedRoom?.normalizedRoomName())

        WidgetConfigStore.remapIds(context, intArrayOf(101), intArrayOf(201))
        assertNull(WidgetConfigStore.load(context, 101))
        assertEquals(42, WidgetConfigStore.load(context, 201)?.deviceId)

        WidgetConfigStore.delete(context, 102)
        assertNull(WidgetConfigStore.load(context, 102))
        assertEquals(listOf(201), WidgetConfigStore.allConfiguredWidgetIds(context))
    }

    @Test
    fun widgetDisplayStateSupportsDimmerRoomCountsAndSerialFallback() {
        val dimmer = DeviceEntity(
            id = 99,
            name = "Dining Chandelier",
            roomName = "Dining Room",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.1.50",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 65,
            serialNumber = "WDS060DIN99"
        )
        val porchSwitch = DeviceEntity(
            id = 100,
            name = "Porch Light",
            roomName = "Dining Room",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.1.51",
            port = 49153,
            isOn = false,
            isReachable = true,
            serialNumber = "WLS040DIN100"
        )

        // Simulate row ID changing from 7 -> 99 after re-discovery while serialNumber stays constant
        val staleIdConfig = WidgetTargetConfig(
            appWidgetId = 301,
            targetType = WidgetTargetType.DEVICE,
            deviceId = 7,
            serialNumber = "WDS060DIN99",
            roomName = "Dining Room",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            displayName = "Dining Chandelier"
        )
        val devState = WidgetRemoteViewsBuilder.resolveDisplayState(
            config = staleIdConfig,
            devices = listOf(dimmer, porchSwitch)
        )
        assertEquals("Dining Chandelier", devState.title)
        assertEquals("ON • 65%", devState.subtitle)
        assertEquals(WidgetVisualMode.ON, devState.visualMode)

        val roomConfig = WidgetTargetConfig.forRoom(302, "Dining Room", DEFAULT_MAIN_HOME_KEY)
        val roomState = WidgetRemoteViewsBuilder.resolveDisplayState(
            config = roomConfig,
            devices = listOf(dimmer, porchSwitch)
        )
        assertEquals("Dining Room", roomState.title)
        assertEquals("1/2 ON • Room", roomState.subtitle)
        assertEquals(WidgetVisualMode.ON, roomState.visualMode)
    }

    @Test
    fun headlessWidgetToggleControlsRealLoopbackLightAndRoomOverUpnpSoap() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "Patio String Lights",
            modelName = "WLS040",
            serialNumber = "WLS040WIDGET01",
            udn = "uuid:Lightswitch-3_0-WLS040WIDGET01",
            initialRelayOn = false
        ).use { server1 ->
            FakeUpnpServer(
                initialFriendlyName = "Patio Spotlights",
                modelName = "WLS040",
                serialNumber = "WLS040WIDGET02",
                udn = "uuid:Lightswitch-3_0-WLS040WIDGET02",
                initialRelayOn = false
            ).use { server2 ->
                val dao = SwitchDatabase.getDatabase(context).switchDao()
                val repo = SwitchRepository(
                    dao = dao,
                    context = context,
                    initialDemoModeOverride = false
                )
                try {
                    val saved1 = repo.saveDevice(
                        DeviceEntity(
                            name = "Patio String Lights",
                            roomName = "Patio",
                            networkKey = DEFAULT_MAIN_HOME_KEY,
                            deviceType = DeviceType.SWITCH_PLUG.name,
                            ipAddress = "127.0.0.1",
                            port = server1.port,
                            isOn = false,
                            isReachable = true,
                            serialNumber = "WLS040WIDGET01"
                        )
                    )
                    val saved2 = repo.saveDevice(
                        DeviceEntity(
                            name = "Patio Spotlights",
                            roomName = "Patio",
                            networkKey = DEFAULT_MAIN_HOME_KEY,
                            deviceType = DeviceType.SWITCH_PLUG.name,
                            ipAddress = "127.0.0.1",
                            port = server2.port,
                            isOn = false,
                            isReachable = true,
                            serialNumber = "WLS040WIDGET02"
                        )
                    )

                    // 1) Toggle individual light widget ON via real loopback SOAP
                    WidgetConfigStore.save(context, WidgetTargetConfig.forDevice(401, saved1))
                    assertTrue(WidgetActionExecutor.toggleWidgetTarget(context, 401, repositoryOverride = repo))
                    assertTrue("Expected server1 to receive SetBinaryState(1)", server1.relayStateOn.get())
                    assertFalse("Expected server2 to remain OFF", server2.relayStateOn.get())
                    assertTrue(dao.getDeviceById(saved1.id)?.isOn == true)

                    // 2) Toggle Patio room widget (since 1/2 is ON, room toggle turns ALL Patio lights OFF)
                    WidgetConfigStore.save(
                        context,
                        WidgetTargetConfig.forRoom(402, "Patio", DEFAULT_MAIN_HOME_KEY)
                    )
                    assertTrue(WidgetActionExecutor.toggleWidgetTarget(context, 402, repositoryOverride = repo))
                    assertFalse("Expected room widget to turn server1 OFF", server1.relayStateOn.get())
                    assertFalse("Expected room widget to keep server2 OFF", server2.relayStateOn.get())

                    // 3) Toggle Patio room widget again (all OFF -> turns ALL Patio lights ON)
                    assertTrue(WidgetActionExecutor.toggleWidgetTarget(context, 402, repositoryOverride = repo))
                    assertTrue("Expected room widget to turn server1 ON", server1.relayStateOn.get())
                    assertTrue("Expected room widget to turn server2 ON", server2.relayStateOn.get())

                    val updatedList = repo.allDevices.first()
                    val roomState = WidgetRemoteViewsBuilder.resolveDisplayState(
                        config = WidgetConfigStore.load(context, 402),
                        devices = updatedList
                    )
                    assertEquals("2/2 ON • Room", roomState.subtitle)
                    assertEquals(WidgetVisualMode.ON, roomState.visualMode)
                    assertEquals(saved2.id, dao.getDeviceById(saved2.id)?.id)
                } finally {
                    repo.close()
                }
            }
        }
    }

    @Test
    fun widgetConfigureActivityLetsUserSelectRoomAndReturnsResultOk() {
        val launchIntent = Intent(context, SwitchWidgetConfigureActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 555)
        }
        ActivityScenario.launchActivityForResult<SwitchWidgetConfigureActivity>(launchIntent).use { scenario ->
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                runCatching {
                    composeRule.onNodeWithTag("widget_config_room_Living Room").assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            // Switch to Lights tab and confirm lights list renders
            composeRule.onNodeWithTag("widget_config_tab_lights").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("widget_config_lights_list").assertIsDisplayed()

            // Switch back to Rooms tab and pick Living Room
            composeRule.onNodeWithTag("widget_config_tab_rooms").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("widget_config_room_Living Room").performClick()
            composeRule.waitForIdle()

            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertEquals(
                555,
                result.resultData?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            )
            val saved = WidgetConfigStore.load(context, 555)
            assertNotNull(saved)
            assertEquals(WidgetTargetType.ROOM, saved?.targetType)
            assertEquals("Living Room", saved?.normalizedRoomName())
        }
    }

    @Test
    fun widgetConfigStoreRemapIdsPreservesOverlappingDestinationsAndHealsLearnedSerial() = runBlocking {
        // 1) Overlapping shift [101, 102] -> [102, 103] and swap [102, 103] -> [103, 102] must not clobber configs
        WidgetConfigStore.save(
            context,
            WidgetTargetConfig.forRoom(101, "Kitchen", DEFAULT_MAIN_HOME_KEY)
        )
        WidgetConfigStore.save(
            context,
            WidgetTargetConfig.forRoom(102, "Patio", DEFAULT_MAIN_HOME_KEY)
        )

        WidgetConfigStore.remapIds(context, intArrayOf(101, 102), intArrayOf(102, 103))
        assertNull(WidgetConfigStore.load(context, 101))
        assertEquals("Kitchen", WidgetConfigStore.load(context, 102)?.normalizedRoomName())
        assertEquals("Patio", WidgetConfigStore.load(context, 103)?.normalizedRoomName())

        WidgetConfigStore.remapIds(context, intArrayOf(102, 103), intArrayOf(103, 102))
        assertEquals("Patio", WidgetConfigStore.load(context, 102)?.normalizedRoomName())
        assertEquals("Kitchen", WidgetConfigStore.load(context, 103)?.normalizedRoomName())
        assertEquals(setOf(102, 103), WidgetConfigStore.allConfiguredWidgetIds(context).toSet())

        // 2) Toggling a device widget heals a previously blank/placeholder serialNumber once real hardware reports it
        FakeUpnpServer(
            initialFriendlyName = "Porch Sconce",
            modelName = "WLS040",
            serialNumber = "WLS040HEAL99",
            udn = "uuid:Lightswitch-3_0-WLS040HEAL99",
            initialRelayOn = false
        ).use { server ->
            val dao = SwitchDatabase.getDatabase(context).switchDao()
            val repo = SwitchRepository(
                dao = dao,
                context = context,
                initialDemoModeOverride = false
            )
            try {
                val insertedId = dao.insertDevice(
                    DeviceEntity(
                        name = "Porch Sconce",
                        roomName = "Porch",
                        networkKey = DEFAULT_MAIN_HOME_KEY,
                        deviceType = DeviceType.SWITCH_PLUG.name,
                        ipAddress = "127.0.0.1",
                        port = server.port,
                        isOn = false,
                        isReachable = true,
                        serialNumber = "WLS040HEAL99"
                    )
                ).toInt()
                WidgetConfigStore.save(
                    context,
                    WidgetTargetConfig(
                        appWidgetId = 601,
                        targetType = WidgetTargetType.DEVICE,
                        deviceId = insertedId,
                        serialNumber = "",
                        roomName = "Porch",
                        networkKey = DEFAULT_MAIN_HOME_KEY,
                        displayName = "Porch Sconce"
                    )
                )
                assertTrue(WidgetActionExecutor.toggleWidgetTarget(context, 601, repositoryOverride = repo))
                assertEquals("WLS040HEAL99", WidgetConfigStore.load(context, 601)?.serialNumber)
            } finally {
                repo.close()
            }
        }
    }

    @Test
    fun widgetProviderHandlesPinnedToggleRestoreDeleteAndOfflineRoomWakeup() = runBlocking {
        // 1) RemoteViews display state & buildRemoteViews edge cases (unconfigured, missing, offline)
        val unconfigured = WidgetRemoteViewsBuilder.resolveDisplayState(null, emptyList())
        assertEquals(WidgetVisualMode.UNCONFIGURED, unconfigured.visualMode)
        assertNotNull(WidgetRemoteViewsBuilder.buildRemoteViews(context, 700, null, emptyList()))

        val removedCfg = WidgetTargetConfig(
            appWidgetId = 700,
            targetType = WidgetTargetType.DEVICE,
            deviceId = 999,
            displayName = "Old Porch Lamp"
        )
        val removedState = WidgetRemoteViewsBuilder.resolveDisplayState(removedCfg, emptyList())
        assertEquals(WidgetVisualMode.OFFLINE, removedState.visualMode)
        assertEquals("Tap to reconfigure", removedState.subtitle)
        assertNotNull(WidgetRemoteViewsBuilder.buildRemoteViews(context, 700, removedCfg, emptyList()))

        // 2) Full WeMiSwitchWidgetProvider broadcast lifecycle with deterministic test seams
        FakeUpnpServer(
            initialFriendlyName = "Garage Overhead",
            modelName = "WLS040",
            serialNumber = "WLS040BCAST01",
            udn = "uuid:Lightswitch-3_0-WLS040BCAST01",
            initialRelayOn = false
        ).use { server ->
            val dao = SwitchDatabase.getDatabase(context).switchDao()
            val repo = SwitchRepository(
                dao = dao,
                context = context,
                initialDemoModeOverride = false
            )
            try {
                // Start marked offline so we also test offline Room wake-up via broadcast
                val savedId = dao.insertDevice(
                    DeviceEntity(
                        name = "Garage Overhead",
                        roomName = "Garage",
                        networkKey = DEFAULT_MAIN_HOME_KEY,
                        deviceType = DeviceType.SWITCH_PLUG.name,
                        ipAddress = "127.0.0.1",
                        port = server.port,
                        isOn = false,
                        isReachable = false,
                        serialNumber = "WLS040BCAST01"
                    )
                ).toInt()

                val offlineRoomState = WidgetRemoteViewsBuilder.resolveDisplayState(
                    WidgetTargetConfig.forRoom(702, "Garage", DEFAULT_MAIN_HOME_KEY),
                    repo.allDevices.first()
                )
                assertEquals(WidgetVisualMode.OFFLINE, offlineRoomState.visualMode)
                assertEquals("Offline • Room", offlineRoomState.subtitle)

                val provider = WeMiSwitchWidgetProvider()
                WeMiSwitchWidgetProvider.actionRunnerForTests = { block -> runBlocking { block() } }
                WeMiSwitchWidgetProvider.repositoryProviderForTests = { repo }

                // Pin a Room widget via ACTION_WIDGET_PINNED broadcast
                provider.onReceive(
                    context,
                    Intent(WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 702)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, WidgetTargetType.ROOM.name)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, "Garage")
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_NETWORK_KEY, DEFAULT_MAIN_HOME_KEY)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_DISPLAY_NAME, "Garage")
                    }
                )
                assertEquals("Garage", WidgetConfigStore.load(context, 702)?.normalizedRoomName())

                // Tap the offline Room widget via ACTION_TOGGLE_WIDGET -> wakes offline loopback switch ON
                provider.onReceive(
                    context,
                    Intent(WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 702)
                    }
                )
                assertTrue("Expected offline Room widget tap to wake server ON", server.relayStateOn.get())
                assertTrue(dao.getDeviceById(savedId)?.isReachable == true)
                assertTrue(dao.getDeviceById(savedId)?.isOn == true)

                // Pin a Device widget and toggle it OFF via ACTION_TOGGLE_WIDGET
                provider.onReceive(
                    context,
                    Intent(WeMiSwitchWidgetProvider.ACTION_WIDGET_PINNED).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 701)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_TARGET_TYPE, WidgetTargetType.DEVICE.name)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_DEVICE_ID, savedId)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_SERIAL_NUMBER, "WLS040BCAST01")
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_ROOM_NAME, "Garage")
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_NETWORK_KEY, DEFAULT_MAIN_HOME_KEY)
                        putExtra(WeMiSwitchWidgetProvider.EXTRA_DISPLAY_NAME, "Garage Overhead")
                    }
                )
                provider.onReceive(
                    context,
                    Intent(WeMiSwitchWidgetProvider.ACTION_TOGGLE_WIDGET).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 701)
                    }
                )
                assertFalse("Expected device widget toggle broadcast to turn server OFF", server.relayStateOn.get())

                // Exercise onRestored (overlapping IDs) and onDeleted
                provider.onRestored(context, intArrayOf(701, 702), intArrayOf(702, 703))
                assertNull(WidgetConfigStore.load(context, 701))
                assertEquals(WidgetTargetType.DEVICE, WidgetConfigStore.load(context, 702)?.targetType)
                assertEquals(WidgetTargetType.ROOM, WidgetConfigStore.load(context, 703)?.targetType)

                provider.onDeleted(context, intArrayOf(702, 703))
                assertTrue(WidgetConfigStore.allConfiguredWidgetIds(context).isEmpty())
            } finally {
                repo.close()
            }
        }
    }
}
