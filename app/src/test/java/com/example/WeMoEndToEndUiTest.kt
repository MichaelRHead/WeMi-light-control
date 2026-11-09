package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-End (E2E) Jetpack Compose + Loopback UPnP/SOAP Hardware UI Tests.
 *
 * Exercises full-stack user flows from Compose UI gestures -> WeMoViewModel -> WeMoRepository ->
 * WeMoNetworkClient / FakeWeMoUpnpServer -> Room v5 Database -> Compose UI state updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeMoEndToEndUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * Pumps the Compose / Robolectric main looper while allowing real wall-clock time for
     * background `Dispatchers.IO` and loopback TCP/HTTP socket threads (`FakeWeMoUpnpServer`)
     * to complete their round-trips and post continuations back to `Dispatchers.Main`.
     */
    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            composeTestRule.waitForIdle()
            if (condition()) return
            Thread.sleep(20)
        }
        composeTestRule.waitForIdle()
        assertTrue("Timed out waiting ${timeoutMs}ms for loopback IO condition", condition())
    }

    @Test
    fun `verify E2E loopback hardware addition, FriendlyName SOAP sync, and live power toggle via Compose UI`() {
        composeTestRule.waitForIdle()

        FakeWeMoUpnpServer(
            initialFriendlyName = "Factory Switch",
            modelName = "WSP080",
            serialNumber = "WSP080E2E001",
            udn = "uuid:Socket-1_0-WSP080E2E001",
            macAddress = "EC1A59DD7788",
            initialRelayOn = false
        ).use { server ->
            // 1. Open AddOrEditWeMoDeviceDialog via add_device_button
            composeTestRule.onNodeWithTag("add_device_button").performClick()
            composeTestRule.waitForIdle()

            // 2. Enter custom name, loopback IP (127.0.0.1), and server port
            composeTestRule.onNodeWithTag("device_name_input")
                .performScrollTo()
                .performTextReplacement("Living Room Exhaust Fan")
            composeTestRule.onNodeWithTag("device_ip_input")
                .performScrollTo()
                .performTextReplacement("127.0.0.1")
            composeTestRule.onNodeWithTag("device_port_input")
                .performScrollTo()
                .performTextReplacement(server.port.toString())
            composeTestRule.waitForIdle()

            // Verify live auto-classified icon preview in dialog shows Fan
            composeTestRule.onNodeWithText("Auto-Classified Icon: Fan")
                .performScrollTo()
                .assertIsDisplayed()

            // 3. Save device -> probes 127.0.0.1:server.port (/setup.xml, GetBinaryState=0) and sends ChangeFriendlyName on Dispatchers.IO
            composeTestRule.onNodeWithTag("save_device_button").performClick()
            awaitLoopbackIo {
                server.friendlyName.get() == "Living Room Exhaust Fan"
            }

            assertEquals("Living Room Exhaust Fan", server.friendlyName.get())
            assertTrue(server.receivedSoapActions.any { it.contains("ChangeFriendlyName") })

            // 4. Scroll to the newly added switch in the Compose list and verify it starts in Accessible OFF Standby
            awaitLoopbackIo {
                runCatching {
                    composeTestRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasText("Living Room Exhaust Fan"))
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("Living Room Exhaust Fan").assertIsDisplayed()
            composeTestRule.onNodeWithContentDescription(
                "Toggle power for Living Room Exhaust Fan, currently Off"
            ).assertIsDisplayed()

            // 5. Tap the power ring button in Compose UI -> sends real SOAP SetBinaryState(1) to FakeWeMoUpnpServer
            composeTestRule.onNodeWithContentDescription(
                "Toggle power for Living Room Exhaust Fan, currently Off"
            ).performClick()
            awaitLoopbackIo {
                server.relayStateOn.get()
            }

            assertTrue(server.relayStateOn.get())
            assertTrue(server.receivedSoapActions.any { it.contains("SetBinaryState") })
            awaitLoopbackIo {
                runCatching {
                    composeTestRule.onNodeWithContentDescription(
                        "Toggle power for Living Room Exhaust Fan, currently On"
                    ).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
        }
    }

    @Test
    fun `verify E2E inline device diagnostics drawer, Ping RTT action, Edit Mode controls, and device rename`() {
        composeTestRule.waitForIdle()

        // 1. Scroll to Christmas Tree Living (id=1), tap the row to expand WeMoDeviceDiagnosticsDrawer, and scroll Ping RTT into view
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_1"))
        composeTestRule.onNodeWithText("Christmas Tree Living").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify Ping RTT chip inside the expanded diagnostics drawer and trigger it
        composeTestRule.onNodeWithTag("ping_device_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ping_device_1").performClick()
        composeTestRule.waitForIdle()

        // 3. Enter Edit Mode via edit_devices_button and open AddOrEditWeMoDeviceDialog for device 1
        composeTestRule.onNodeWithTag("edit_devices_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_1"))
        composeTestRule.onNodeWithTag("edit_device_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("edit_device_1").performClick()
        composeTestRule.waitForIdle()

        // 4. Rename device 1 to "Living Room Holiday Spruce" and save
        composeTestRule.onNodeWithTag("device_name_input")
            .performScrollTo()
            .performTextReplacement("Living Room Holiday Spruce")
        composeTestRule.onNodeWithTag("save_device_button").performClick()
        composeTestRule.waitForIdle()

        // Exit Edit Mode and verify updated name is rendered on its own full-width line above the status/category badges
        composeTestRule.onNodeWithTag("edit_devices_button").performClick()
        awaitLoopbackIo {
            runCatching {
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Living Room Holiday Spruce"))
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Living Room Holiday Spruce").assertIsDisplayed()
        val nameBounds = composeTestRule.onNodeWithTag("device_name_1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val connBadgeBounds = composeTestRule.onNodeWithTag("connection_status_badge_1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val catBadgeBounds = composeTestRule.onNodeWithTag("category_badge_1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected switch name on line above connection badge (nameBounds=$nameBounds, connBadgeBounds=$connBadgeBounds)",
            nameBounds.bottom <= connBadgeBounds.top + 4f
        )
        assertTrue(
            "Expected switch name on line above category badge (nameBounds=$nameBounds, catBadgeBounds=$catBadgeBounds)",
            nameBounds.bottom <= catBadgeBounds.top + 4f
        )

        // 5. Verify 'Living Room' and 'Hallway & Landing' room cards have the exact same rendered height
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("room_header_Living Room"))
        val livingRoomHeight = composeTestRule.onNodeWithTag("room_header_Living Room")
            .fetchSemanticsNode().boundsInRoot.height
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("room_header_Hallway & Landing"))
        val hallwayHeight = composeTestRule.onNodeWithTag("room_header_Hallway & Landing")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(
            "Expected 'Hallway & Landing' room card height ($hallwayHeight) to match 'Living Room' ($livingRoomHeight)",
            kotlin.math.abs(hallwayHeight - livingRoomHeight) <= 2f
        )
    }

    @Test
    fun `verify E2E ProvisionWeMoApDialog validation and Rules tab creation, execution, and toggle`() {
        composeTestRule.waitForIdle()

        // 1. Open ProvisionWeMoApDialog and verify short-password error banner
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_provision_ap_wizard_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("provision_password_input")
            .performScrollTo()
            .performTextReplacement("short")
        composeTestRule.onNodeWithTag("confirm_provision_ap_button").performClick()
        composeTestRule.waitForIdle()

        // Dialog remains open and status box displays validation error for <8 char WPA2 password
        composeTestRule.onNodeWithTag("provision_ap_status_box")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // 2. Navigate to Rules tab, create a new Schedule rule via AddWeMoRuleDialog, and execute it
        composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("add_rule_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("rule_name_input")
            .performScrollTo()
            .performTextReplacement("Late Night Tree Off")
        composeTestRule.onNodeWithTag("save_rule_button").performClick()
        awaitLoopbackIo {
            runCatching {
                composeTestRule.onNodeWithTag("rules_list")
                    .performScrollToNode(hasText("Late Night Tree Off"))
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("Late Night Tree Off").assertIsDisplayed()

        // Execute rule 1 ("Evening Holiday Lights") and toggle its enabled switch
        composeTestRule.onNodeWithTag("rules_list")
            .performScrollToNode(hasTestTag("rule_row_1"))
        composeTestRule.onNodeWithTag("run_rule_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_rule_1").performClick()
        composeTestRule.waitForIdle()
    }
}
