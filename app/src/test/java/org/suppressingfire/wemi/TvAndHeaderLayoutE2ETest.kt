package org.suppressingfire.wemi

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
import kotlin.math.abs

/**
 * Full-stack Jetpack Compose + Robolectric E2E validation for `v23.0` UI layout & Android TV CUJs:
 * 1. Compact single-row mobile/tablet `TopHeader` horizontal alignment and wordmark toggle.
 * 2. Native Android TV (`UI_MODE_TYPE_TELEVISION`) 3-column room-grouped grid, `"Unassigned"`-last
 *    room ordering, hardware D-Pad `KeyEvent` dispatch (`DirectionCenter`, `MediaPlayPause`,
 *    `DirectionRight`), `"DIM -/+"` mode lifecycle, and Room Master SOAP control over live loopback
 *    [FakeUpnpServer] instances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TvAndHeaderLayoutE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeTestRule.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        composeTestRule.waitForIdle()
        assertTrue("Timed out after ${timeoutMs}ms waiting for loopback I/O condition", condition())
    }

    @Test
    fun `verify v23_0 compact single-row TopHeader layout and brand wordmark toggle`() {
        composeTestRule.waitForIdle()

        val wordmarkNode = composeTestRule.onNodeWithTag("brand_wordmark_text").assertIsDisplayed()
        val refreshNode = composeTestRule.onNodeWithTag("refresh_devices_button").assertIsDisplayed()
        val editNode = composeTestRule.onNodeWithTag("edit_devices_button").assertIsDisplayed()
        val addNode = composeTestRule.onNodeWithTag("add_device_button").assertIsDisplayed()

        // Legacy multi-line "LAN SWITCHES" subtitle must not exist in the compact single-row TopHeader
        composeTestRule.onNodeWithText("LAN SWITCHES").assertDoesNotExist()

        val wordmarkBounds = wordmarkNode.fetchSemanticsNode().boundsInRoot
        val refreshBounds = refreshNode.fetchSemanticsNode().boundsInRoot
        val editBounds = editNode.fetchSemanticsNode().boundsInRoot
        val addBounds = addNode.fetchSemanticsNode().boundsInRoot

        // All 4 controls must share the same single horizontal header row (vertical centers within 10px)
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

        // Strict left-to-right horizontal ordering across the single row
        assertTrue(
            "Expected strict left-to-right header ordering: wordmark -> refresh -> edit -> add",
            wordmarkBounds.right <= refreshBounds.left + 8f &&
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
                        !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty()
                    }

                    composeTestRule.onNodeWithTag("tv_scan_button").assertIsDisplayed().performClick()
                    awaitLoopbackIo {
                        vm.devices.value.size == 3
                    }

                    val adopted = vm.devices.value
                    val switchEntity = adopted.first { it.serialNumber == "WLS0403TVSW01" }
                    val dimmerEntity = adopted.first { it.serialNumber == "WDS060TVDIM02" }
                    val unassignedEntity = adopted.first { it.serialNumber == "WSP080TVPLUG03" }
                    assertEquals("Living Room", switchEntity.roomName)
                    assertEquals("Living Room", dimmerEntity.roomName)
                    assertEquals("Unassigned", unassignedEntity.roomName)

                    // 2. Verify "Living Room" section renders above "Unassigned" in the 3-column TV grid
                    composeTestRule.onNodeWithTag("tv_device_grid")
                        .performScrollToNode(hasTestTag("tv_room_header_Unassigned"))
                    composeTestRule.waitForIdle()
                    val livingHeaderBounds = composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    val unassignedHeaderBounds = composeTestRule.onNodeWithTag("tv_room_header_Unassigned")
                        .assertIsDisplayed()
                        .fetchSemanticsNode().boundsInRoot
                    assertTrue(
                        "Expected 'Living Room' header above 'Unassigned' ($livingHeaderBounds vs $unassignedHeaderBounds)",
                        livingHeaderBounds.top < unassignedHeaderBounds.top
                    )

                    // 3. Hardware D-Pad Key.DirectionCenter on Living Room Switch toggles relay ON over loopback SOAP
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${switchEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionCenter) }

                    awaitLoopbackIo {
                        switchServer.relayStateOn.get()
                    }
                    assertTrue(switchServer.relayStateOn.get())

                    // 4. Focus Living Room Dimmer, press MediaPlayPause to enter "DIM -/+" mode, step +10% with DirectionRight, and exit with DirectionCenter
                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.MediaPlayPause) }
                    composeTestRule.waitForIdle()

                    composeTestRule.onNodeWithText("DIM -/+", useUnmergedTree = true).assertIsDisplayed()
                    composeTestRule.onNodeWithText("Done Dimming", useUnmergedTree = true).assertIsDisplayed()

                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionRight) }
                    awaitLoopbackIo {
                        dimmerServer.dimmerBrightness.get() == 60
                    }
                    assertEquals(60, dimmerServer.dimmerBrightness.get())

                    composeTestRule.onNodeWithTag("tv_device_card_${dimmerEntity.id}")
                        .performKeyInput { pressKey(Key.DirectionCenter) }
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithText("DIM -/+", useUnmergedTree = true).assertDoesNotExist()

                    // 5. Focus Living Room section header and press DirectionCenter to trigger Room Master OFF across both switches
                    composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .performSemanticsAction(SemanticsActions.RequestFocus)
                    composeTestRule.waitForIdle()
                    composeTestRule.onNodeWithTag("tv_room_header_Living Room")
                        .performKeyInput { pressKey(Key.DirectionCenter) }

                    awaitLoopbackIo {
                        !switchServer.relayStateOn.get() && !dimmerServer.relayStateOn.get()
                    }
                    assertFalse(switchServer.relayStateOn.get())
                    assertFalse(dimmerServer.relayStateOn.get())
                }
            }
        }
    }
}
