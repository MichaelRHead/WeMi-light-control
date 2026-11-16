package org.suppressingfire.wemi

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import kotlin.math.abs
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
 * Full-stack Jetpack Compose + Robolectric E2E validation for UI layout & Android TV D-Pad CUJs:
 * 1. Compact single-row mobile/tablet `TopHeader` horizontal alignment, `>= 48.dp` Play Console
 *    Accessibility touch targets, `Role.Button` + `ContentDescription` semantics, `"All Off"` button,
 *    and wordmark toggle.
 * 2. Native Android TV (`UI_MODE_TYPE_TELEVISION`) 3-column room-grouped grid, deterministic
 *    alphabetical adoption ordering (`Living Room Dimmer` Col 0, `Living Room Switch` Col 1),
 *    `"Unassigned"`-last room ordering, Up/Down Room Bar navigation across columns, focusable Dimmer
 *    On/Off Toggle vs. focusable Dimmer Slider, and Room Master SOAP control over loopback servers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TvAndHeaderLayoutE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

    @Test
    fun `verify v23_0 compact single-row TopHeader layout, 48dp accessibility touch targets, and brand wordmark toggle`() {
        composeTestRule.waitForIdle()

        val wordmarkNode = composeTestRule.onNodeWithTag("brand_wordmark_text").assertIsDisplayed()
        val allOffNode = composeTestRule.onNodeWithTag("all_off_devices_button").assertIsDisplayed()
        val refreshNode = composeTestRule.onNodeWithTag("refresh_devices_button").assertIsDisplayed()
        val editNode = composeTestRule.onNodeWithTag("edit_devices_button").assertIsDisplayed()
        val addNode = composeTestRule.onNodeWithTag("add_device_button").assertIsDisplayed()

        // Legacy multi-line "LAN SWITCHES" subtitle must not exist in the compact single-row TopHeader
        composeTestRule.onNodeWithText("LAN SWITCHES").assertDoesNotExist()
        composeTestRule.onNodeWithText("All Off").assertIsDisplayed()

        val headerControls = listOf(
            "brand_wordmark_text" to wordmarkNode.fetchSemanticsNode(),
            "all_off_devices_button" to allOffNode.fetchSemanticsNode(),
            "refresh_devices_button" to refreshNode.fetchSemanticsNode(),
            "edit_devices_button" to editNode.fetchSemanticsNode(),
            "add_device_button" to addNode.fetchSemanticsNode()
        )
        val minTouchHeightPx = with(composeTestRule.density) { 48.dp.toPx() } - 0.5f

        headerControls.forEach { (tag, semNode) ->
            assertTrue(
                "Expected $tag height >= 48.dp ($minTouchHeightPx px), got ${semNode.boundsInRoot.height}",
                semNode.boundsInRoot.height >= minTouchHeightPx
            )
            assertEquals(
                "Expected $tag to expose Role.Button for TalkBack & Play Console Accessibility Scanner",
                Role.Button,
                semNode.config.getOrNull(SemanticsProperties.Role)
            )
            val descriptions = semNode.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue(
                "Expected $tag to expose a non-blank accessibility ContentDescription",
                descriptions.any { it.isNotBlank() }
            )
        }

        val wordmarkBounds = headerControls[0].second.boundsInRoot
        val allOffBounds = headerControls[1].second.boundsInRoot
        val refreshBounds = headerControls[2].second.boundsInRoot
        val editBounds = headerControls[3].second.boundsInRoot
        val addBounds = headerControls[4].second.boundsInRoot

        // All 5 controls must share the same single horizontal header row (vertical centers within 10px)
        assertTrue(
            "Expected All Off button aligned vertically with wordmark ($wordmarkBounds vs $allOffBounds)",
            abs(allOffBounds.center.y - wordmarkBounds.center.y) <= 10f
        )
        assertTrue(
            "Expected refresh button aligned vertically with wordmark ($wordmarkBounds vs $refreshBounds)",
            abs(refreshBounds.center.y - wordmarkBounds.center.y) <= 10f
        )
        assertTrue(
            "Expected edit button aligned vertically with wordmark ($wordmarkBounds vs $editBounds)",
            abs(editBounds.center.y - wordmarkBounds.center.y) <= 10f
        )
        assertTrue(
            "Expected add button aligned vertically with wordmark ($wordmarkBounds vs $addBounds)",
            abs(addBounds.center.y - wordmarkBounds.center.y) <= 10f
        )

        // Strict left-to-right horizontal ordering across the single row: wordmark -> All Off -> Refresh -> Edit -> Add
        assertTrue(
            "Expected strict left-to-right header ordering: wordmark -> all_off -> refresh -> edit -> add",
            wordmarkBounds.right <= allOffBounds.left + 8f &&
                allOffBounds.center.x < refreshBounds.center.x &&
                refreshBounds.center.x < editBounds.center.x &&
                editBounds.center.x < addBounds.center.x
        )

        // Clicking wordmark toggles brand display and clicking again restores "wemi"
        composeTestRule.onNodeWithText("wemi").assertIsDisplayed()
        wordmarkNode.performClick()
        composeTestRule.waitForIdle()
        wordmarkNode.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("wemi").assertIsDisplayed()

        // Clicking "All Off" next to the Refresh button turns OFF all reachable controllable switches on the active home
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        allOffNode.performClick()
        awaitLoopbackIo {
            vm.togglingDeviceIds.value.isEmpty() &&
                vm.devices.value
                    .filter { it.networkKey == vm.activeNetworkKey.value && it.isReachable && it.parsedType != org.suppressingfire.wemi.data.DeviceType.MOTION_SENSOR }
                    .none { it.isOn }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Config(sdk = [36], qualifiers = "television")
    fun `verify v23_0 Android TV 3-column room-grouped dashboard D-Pad hardware KeyEvents DIM mode and loopback SOAP control`() {
        composeTestRule.waitForIdle()

        FakeUpnpServer(
            initialFriendlyName = "Living Room Switch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403TVSW01",
            udn = "uuid:Lightswitch-3_0-WLS0403TVSW01",
            macAddress = "EC1A59770001",
            isRtos = true,
            initialRelayOn = false,
            enableSsdpResponder = true,
            registerAsCandidatePort = true
        ).use { switchServer ->
            FakeUpnpServer(
                initialFriendlyName = "Living Room Dimmer",
                deviceTypeUrn = "urn:Belkin:device:dimmer:1",
                modelName = "WDS060",
                serialNumber = "WDS060TVDIM02",
                udn = "uuid:Dimmer-2_0-WDS060TVDIM02",
                macAddress = "EC1A59770002",
                isRtos = true,
                initialRelayOn = true,
                initialBrightness = 50,
                enableSsdpResponder = true,
                registerAsCandidatePort = true
            ).use { dimmerServer ->
                FakeUpnpServer(
                    initialFriendlyName = "Spare Mystery Plug",
                    deviceTypeUrn = "urn:Belkin:device:controllee:1",
                    modelName = "Socket",
                    serialNumber = "WSP080TVPLUG03",
                    udn = "uuid:Socket-1_0-WSP080TVPLUG03",
                    macAddress = "EC1A59770003",
                    isRtos = true,
                    initialRelayOn = false,
                    enableSsdpResponder = true,
                    registerAsCandidatePort = true
                ).use { _ ->
                    val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]

                    // 1. Reset LAN homes empty, then trigger TV background Rescan over loopback UDP SSDP
                    composeTestRule.runOnUiThread {
                        vm.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
                    }
                    awaitLoopbackIo {
                        !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() && !vm.isRefreshing.value
                    }

                    composeTestRule.onNodeWithTag("tv_scan_button").assertIsDisplayed().performClick()
                    awaitLoopbackIo {
                        vm.devices.value.size == 3 && !vm.isRefreshing.value
                    }

                    val adopted = vm.devices.value
                    val switchEntity = adopted.first { it.serialNumber == "WLS0403TVSW01" }
                    val dimmerEntity = adopted.first { it.serialNumber == "WDS060TVDIM02" }
                    val unassignedEntity = adopted.first { it.serialNumber == "WSP080TVPLUG03" }
                    assertEquals("Living Room", switchEntity.roomName)
                    assertEquals("Living Room", dimmerEntity.roomName)
                    assertEquals("Unassigned", unassignedEntity.roomName)
                    assertTrue(switchEntity.sortOrder < dimmerEntity.sortOrder)

                    // 2. Verify "Living Room" section renders above "Unassigned" and Switch (Col 0) left of Dimmer (Col 1)
                    composeTestRule.onNodeWithTag("tv_device_grid")
                        .performScrollToNode(hasTestTag("tv_room_header_Living Room"))
                    composeTestRule.waitForIdle()
                    val livingHeaderBounds = composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val unassignedHeaderBounds = composeTestRule.onNodeWithTag("tv_room_header_Unassigned")
                        .assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val switchCardBounds = composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val dimmerCardBounds = composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        "Expected 'Living Room' header above 'Unassigned' ($livingHeaderBounds vs $unassignedHeaderBounds)",
                        livingHeaderBounds.top < unassignedHeaderBounds.top
                    )
                    assertTrue(
                        "Expected deterministic registration column order: Switch (Col 0) left of Dimmer (Col 1)",
                        switchCardBounds.left < dimmerCardBounds.left
                    )

                    // 3. Focus leftmost switch widget (Column 1: Living Room Switch) and verify Up/Down
                    //    always selects the Room Bar (tv_room_header_Living Room / tv_room_header_Unassigned)
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}").assertIsFocused()

                    // Pressing Up from leftmost switch widget selects the Living Room Bar above it
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionUp) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_room_header_Living Room").assertIsFocused()

                    // Pressing Down from Living Room Bar returns focus to the leftmost switch widget
                    composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .performKeyInput { pressKey(Key.DirectionDown) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}").assertIsFocused()

                    // Pressing Down from leftmost switch widget selects the Unassigned Room Bar below it
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionDown) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_room_header_Unassigned").assertIsFocused()

                    // Pressing Up from Unassigned Room Bar returns focus to the leftmost switch widget
                    composeTestRule.onNodeWithTag("tv_room_header_Unassigned")
                        .performKeyInput { pressKey(Key.DirectionUp) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}").assertIsFocused()

                    // Hardware D-Pad Key.DirectionCenter on Living Room Switch toggles relay ON over loopback SOAP
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionCenter) }
                    awaitLoopbackIo {
                        switchServer.relayStateOn.get() && vm.togglingDeviceIds.value.isEmpty()
                    }
                    assertTrue(switchServer.relayStateOn.get())

                    // 4. Navigate Right to Living Room Dimmer's On/Off Toggle; verify Left/Right moves between
                    //    switch cards, and navigating Down once more focuses the Dimmer Slider
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionRight) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}").assertIsFocused()

                    // Moving Left from Dimmer's On/Off Toggle moves back to the switch card on the left
                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionLeft) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}").assertIsFocused()

                    // Move Right back onto the Dimmer's On/Off Toggle, then Down once more to focus the Dimmer Slider
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionRight) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}").assertIsFocused()

                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionDown) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_card_dim_mode_${dimmerEntity.id}").assertIsFocused()
                    composeTestRule.onNodeWithText("DIM -/+", useUnmergedTree = true).assertIsDisplayed()
                    composeTestRule.onNodeWithText("Done Dimming", useUnmergedTree = true).assertIsDisplayed()

                    // Pressing Right and Left on the focused Dimmer Slider raises (+10%) and lowers (-10%) brightness
                    composeTestRule.onNodeWithTag("tv_card_dim_mode_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionRight) }
                    awaitLoopbackIo {
                        dimmerServer.dimmerBrightness.get() == 60 && vm.togglingDeviceIds.value.isEmpty()
                    }
                    assertEquals(60, dimmerServer.dimmerBrightness.get())
                    composeTestRule.onNodeWithTag("tv_card_dim_mode_${dimmerEntity.id}").assertIsFocused()

                    composeTestRule.onNodeWithTag("tv_card_dim_mode_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionLeft) }
                    awaitLoopbackIo {
                        dimmerServer.dimmerBrightness.get() == 50 && vm.togglingDeviceIds.value.isEmpty()
                    }
                    assertEquals(50, dimmerServer.dimmerBrightness.get())

                    // Navigating Down from the Dimmer Slider moves to the widget below (Unassigned Room Bar)
                    composeTestRule.onNodeWithTag("tv_card_dim_mode_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionDown) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_room_header_Unassigned").assertIsFocused()
                    composeTestRule.onNodeWithText("DIM -/+", useUnmergedTree = true).assertDoesNotExist()

                    // 5. Focus Living Room section header and press DirectionCenter to trigger Room Master OFF across both switches
                    composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .performKeyInput { pressKey(Key.DirectionCenter) }

                    awaitLoopbackIo {
                        !switchServer.relayStateOn.get() &&
                            !dimmerServer.relayStateOn.get() &&
                            vm.togglingRooms.value.isEmpty() &&
                            vm.togglingDeviceIds.value.isEmpty()
                    }
                    assertFalse(switchServer.relayStateOn.get())
                    assertFalse(dimmerServer.relayStateOn.get())
                }
            }
        }
    }
}
