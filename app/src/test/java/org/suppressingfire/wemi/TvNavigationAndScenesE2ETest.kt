package org.suppressingfire.wemi

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.ui.SwitchViewModel

/**
 * Full-stack Android TV (`UI_MODE_TYPE_TELEVISION`) E2E tests covering:
 * - Empty-home initial D-Pad focus on `tv_scan_button` (`Rescan`) for zero-touch remote discovery,
 * - Multi-row (`Row 0 <-> Row 1`) 3-column grid D-Pad traversal within a single room,
 * - System `Back` from `tv_card_dim_mode_<id>` returning focus to `tv_device_card_<id>`,
 * - `TvRoomSectionHeader` `Key.DirectionLeft` / `Key.DirectionRight` room dimmer stepping, and
 * - Top-bar 1-press TV scenes (`MOVIE_MODE`, `HOLIDAY_ON`, `BEDTIME_OFF`) over loopback SOAP.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "television")
class TvNavigationAndScenesE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `verify Android TV empty-home Rescan focus, multi-row grid traversal, Room Header D-Pad dimming, Back from slider, and 1-press TV scenes`() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]

        // 1. Reset LAN homes empty -> verify D-Pad focus lands automatically on tv_scan_button ('Rescan')
        composeTestRule.runOnUiThread {
            vm.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
        }
        awaitLoopbackIo {
            !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() && !vm.isRefreshing.value
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_scan_button").assertIsDisplayed().assertIsFocused()

        // 2. Spin up 4 loopback devices in "Living Room" (Row 0: Alpha Dimmer, Bravo Switch, Charlie Switch; Row 1: Delta Christmas Tree)
        FakeUpnpServer(
            initialFriendlyName = "Living Room Alpha Dimmer",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060TVALPHA1",
            udn = "uuid:Dimmer-2_0-WDS060TVALPHA1",
            macAddress = "EC1A59880001",
            isRtos = true,
            initialRelayOn = true,
            initialBrightness = 80,
            enableSsdpResponder = true,
            registerAsCandidatePort = true
        ).use { alphaServer ->
            FakeUpnpServer(
                initialFriendlyName = "Living Room Bravo Switch",
                deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
                modelName = "WLS0403",
                serialNumber = "WLS0403TVBRAVO2",
                udn = "uuid:Lightswitch-3_0-WLS0403TVBRAVO2",
                macAddress = "EC1A59880002",
                isRtos = true,
                initialRelayOn = true,
                enableSsdpResponder = true,
                registerAsCandidatePort = true
            ).use { bravoServer ->
                FakeUpnpServer(
                    initialFriendlyName = "Living Room Charlie Switch",
                    deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
                    modelName = "WLS0403",
                    serialNumber = "WLS0403TVCHARLIE3",
                    udn = "uuid:Lightswitch-3_0-WLS0403TVCHARLIE3",
                    macAddress = "EC1A59880003",
                    isRtos = true,
                    initialRelayOn = false,
                    enableSsdpResponder = true,
                    registerAsCandidatePort = true
                ).use { _ ->
                    FakeUpnpServer(
                        initialFriendlyName = "Living Room Delta Christmas Tree",
                        deviceTypeUrn = "urn:Belkin:device:controllee:1",
                        modelName = "Socket",
                        serialNumber = "WSP080TVDELTA4",
                        udn = "uuid:Socket-1_0-WSP080TVDELTA4",
                        macAddress = "EC1A59880004",
                        isRtos = true,
                        initialRelayOn = false,
                        enableSsdpResponder = true,
                        registerAsCandidatePort = true
                    ).use { deltaServer ->
                        // Press hardware OK directly on focused tv_scan_button (no touch click needed!)
                        composeTestRule.onNodeWithTag("tv_scan_button")
                            .performKeyInput { pressKey(Key.DirectionCenter) }

                        awaitLoopbackIo {
                            vm.devices.value.size == 4 && !vm.isRefreshing.value
                        }
                        composeTestRule.waitForIdle()

                        val adopted = vm.devices.value
                        val alpha = adopted.first { it.serialNumber == "WDS060TVALPHA1" }
                        val delta = adopted.first { it.serialNumber == "WSP080TVDELTA4" }

                        // Focus automatically moves from tv_scan_button onto Row 0 Col 0 (Alpha Dimmer)
                        composeTestRule.onNodeWithTag("tv_device_card_${alpha.id}").assertIsFocused()

                        // 3. Multi-row navigation: Row 0 Col 0 Toggle -> Down to Slider -> Down to Row 1 Col 0 (Delta) -> Up to Row 0 Col 0 Slider
                        composeTestRule.onNodeWithTag("tv_device_card_${alpha.id}")
                            .performKeyInput { pressKey(Key.DirectionDown) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_card_dim_mode_${alpha.id}").assertIsFocused()

                        composeTestRule.onNodeWithTag("tv_card_dim_mode_${alpha.id}")
                            .performKeyInput { pressKey(Key.DirectionDown) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_device_card_${delta.id}").assertIsFocused()

                        composeTestRule.onNodeWithTag("tv_device_card_${delta.id}")
                            .performKeyInput { pressKey(Key.DirectionUp) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_card_dim_mode_${alpha.id}").assertIsFocused()

                        // 4. System BackHandler while on Dimmer Slider returns focus to card's On/Off Toggle
                        composeTestRule.runOnUiThread {
                            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
                        }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_device_card_${alpha.id}").assertIsFocused()

                        // 5. Navigate Up to Living Room Header -> consecutive Left/Right steps room dimmer brightness (-10% x2 / +10% x2)
                        composeTestRule.onNodeWithTag("tv_device_card_${alpha.id}")
                            .performKeyInput { pressKey(Key.DirectionUp) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_room_header_Living Room").assertIsFocused()

                        composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                            .performKeyInput { pressKey(Key.DirectionLeft) }
                        awaitLoopbackIo {
                            alphaServer.dimmerBrightness.get() == 70 &&
                                vm.devices.value.firstOrNull { it.id == alpha.id }?.brightness == 70
                        }
                        composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                            .performKeyInput { pressKey(Key.DirectionLeft) }
                        awaitLoopbackIo {
                            alphaServer.dimmerBrightness.get() == 60 &&
                                vm.devices.value.firstOrNull { it.id == alpha.id }?.brightness == 60
                        }
                        assertEquals(60, alphaServer.dimmerBrightness.get())

                        composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                            .performKeyInput { pressKey(Key.DirectionRight) }
                        awaitLoopbackIo {
                            alphaServer.dimmerBrightness.get() == 70 &&
                                vm.devices.value.firstOrNull { it.id == alpha.id }?.brightness == 70
                        }
                        composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                            .performKeyInput { pressKey(Key.DirectionRight) }
                        awaitLoopbackIo {
                            alphaServer.dimmerBrightness.get() == 80 &&
                                vm.devices.value.firstOrNull { it.id == alpha.id }?.brightness == 80
                        }
                        composeTestRule.waitForIdle()
                        assertEquals(80, alphaServer.dimmerBrightness.get())

                        // 6. Navigate Up from first Room Header to TV Scene Bar: Movie 20% -> Holiday ON -> All OFF
                        composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                            .performKeyInput { pressKey(Key.DirectionUp) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_scene_movie").assertIsFocused()

                        composeTestRule.onNodeWithTag("tv_scene_movie")
                            .performKeyInput { pressKey(Key.DirectionCenter) }
                        awaitLoopbackIo { alphaServer.dimmerBrightness.get() == 20 }
                        assertEquals(20, alphaServer.dimmerBrightness.get())

                        composeTestRule.onNodeWithTag("tv_scene_movie")
                            .performKeyInput { pressKey(Key.DirectionRight) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_scene_holiday").assertIsFocused()
                        composeTestRule.onNodeWithTag("tv_scene_holiday")
                            .performKeyInput { pressKey(Key.DirectionCenter) }
                        awaitLoopbackIo { deltaServer.relayStateOn.get() }
                        assertTrue(deltaServer.relayStateOn.get())

                        composeTestRule.onNodeWithTag("tv_scene_holiday")
                            .performKeyInput { pressKey(Key.DirectionRight) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_scene_bedtime").assertIsFocused()
                        composeTestRule.onNodeWithTag("tv_scene_bedtime")
                            .performKeyInput { pressKey(Key.DirectionCenter) }
                        awaitLoopbackIo {
                            !alphaServer.relayStateOn.get() &&
                                !bravoServer.relayStateOn.get() &&
                                !deltaServer.relayStateOn.get() &&
                                vm.togglingDeviceIds.value.isEmpty()
                        }
                        assertFalse(alphaServer.relayStateOn.get())
                        assertFalse(bravoServer.relayStateOn.get())
                        assertFalse(deltaServer.relayStateOn.get())

                        // 7. Verify TvSceneChip & tv_scan_button rememberUpdatedState(onNavigateDown):
                        //    even though TvTopHeaderBar was mounted while groupedByRoom was empty in Step 1,
                        //    pressing Down from tv_scene_bedtime and tv_scan_button navigates into Living Room Header!
                        composeTestRule.onNodeWithTag("tv_scene_bedtime")
                            .performKeyInput { pressKey(Key.DirectionDown) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_room_header_Living Room").assertIsFocused()

                        composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                            .performKeyInput { pressKey(Key.DirectionUp) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_scene_movie").assertIsFocused()
                        repeat(3) {
                            composeTestRule.onNodeWithTag(
                                when (it) {
                                    0 -> "tv_scene_movie"
                                    1 -> "tv_scene_holiday"
                                    else -> "tv_scene_bedtime"
                                }
                            ).performKeyInput { pressKey(Key.DirectionRight) }
                            composeTestRule.waitForIdle()
                        }
                        composeTestRule.onNodeWithTag("tv_scan_button").assertIsFocused()
                        composeTestRule.onNodeWithTag("tv_scan_button")
                            .performKeyInput { pressKey(Key.DirectionDown) }
                        composeTestRule.waitForIdle()
                        composeTestRule.onNodeWithTag("tv_room_header_Living Room").assertIsFocused()
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `verify Android TV Unassigned room header toggles power and dims blank-roomName dimmer via D-Pad`() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        val dao = org.suppressingfire.wemi.data.SwitchDatabase.getDatabase(composeTestRule.activity.application).switchDao()

        composeTestRule.runOnUiThread {
            vm.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
        }
        composeTestRule.awaitLoopbackIo(
            failureDescription = {
                "demo=${vm.isDemoShowroomMode.value}, devices=${vm.devices.value.size} (${vm.devices.value.map { it.name }}), refreshing=${vm.isRefreshing.value}"
            }
        ) {
            !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() && !vm.isRefreshing.value
        }

        FakeUpnpServer(
            initialFriendlyName = "Smart Dimmer",
            deviceTypeUrn = "urn:Belkin:device:dimmer:1",
            modelName = "WDS060",
            serialNumber = "WDS060TVUNASSIGN1",
            udn = "uuid:Dimmer-2_0-WDS060TVUNASSIGN1",
            isRtos = true,
            initialRelayOn = false,
            initialBrightness = 50,
            registerAsCandidatePort = true
        ).use { dimmerServer ->
            val insertedId = kotlinx.coroutines.runBlocking {
                dao.insertDevice(
                    org.suppressingfire.wemi.data.DeviceEntity(
                        name = "Smart Dimmer",
                        roomName = "",
                        networkKey = vm.activeNetworkKey.value,
                        deviceType = org.suppressingfire.wemi.data.DeviceType.LIGHTBULB_DIMMER.name,
                        ipAddress = "127.0.0.1",
                        port = dimmerServer.port,
                        isOn = false,
                        isReachable = true,
                        brightness = 50,
                        serialNumber = "WDS060TVUNASSIGN1"
                    )
                ).toInt()
            }
            awaitLoopbackIo { vm.devices.value.any { it.id == insertedId } }
            composeTestRule.waitForIdle()

            // Initial focus lands on Row 0 Col 0 (tv_device_card_${insertedId}); navigate Up onto tv_room_header_Unassigned
            composeTestRule.onNodeWithTag("tv_device_card_$insertedId").assertIsDisplayed().assertIsFocused()
            composeTestRule.onNodeWithTag("tv_device_card_$insertedId")
                .performKeyInput { pressKey(Key.DirectionUp) }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("tv_room_header_Unassigned").assertIsDisplayed().assertIsFocused()

            composeTestRule.onNodeWithTag("tv_room_header_Unassigned")
                .performKeyInput { pressKey(Key.DirectionCenter) }
            awaitLoopbackIo { dimmerServer.relayStateOn.get() && vm.togglingRooms.value.isEmpty() }
            assertTrue(dimmerServer.relayStateOn.get())

            composeTestRule.onNodeWithTag("tv_room_header_Unassigned")
                .performKeyInput { pressKey(Key.DirectionRight) }
            awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == 60 }
            assertEquals(60, dimmerServer.dimmerBrightness.get())

            // Navigate Down onto dimmer card -> Down onto inline DIM slider, then fire two back-to-back Right steps without waiting between presses
            composeTestRule.onNodeWithTag("tv_room_header_Unassigned")
                .performKeyInput { pressKey(Key.DirectionDown) }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("tv_device_card_$insertedId").assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("tv_card_dim_mode_$insertedId").assertIsFocused()
                .performKeyInput {
                    pressKey(Key.DirectionRight)
                    pressKey(Key.DirectionRight)
                }
            awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == 80 }
            assertEquals(80, dimmerServer.dimmerBrightness.get())
            assertEquals(80, vm.devices.value.first { it.id == insertedId }.brightness)
        }
    }
}

