package com.example

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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

        // 1. Open ProvisionWeMoApDialog and verify short-password error banner + screenshot debug panel
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("open_provision_ap_wizard_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("provision_len_auto_chip")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("provision_password_input")
            .performScrollTo()
            .performTextReplacement("short")
        composeTestRule.onNodeWithTag("confirm_provision_ap_button")
            .performClick()
        composeTestRule.waitForIdle()

        // Dialog remains open and status box + screenshot debug panel display validation error & diagnostics
        composeTestRule.onNodeWithTag("provision_ap_status_box")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("provision_debug_panel")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("copy_provision_debug_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_provision_debug_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("keep_provision_dialog_open_chip")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("provision_debug_text", useUnmergedTree = true)
            .assertExists()
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

    @Test
    fun `verify E2E Reset Homes Switches and Rooms dialog, More tab reset button, Disconnected ring button, and long switch name auto-resizing`() {
        composeTestRule.waitForIdle()

        // 1. Verify Disconnected ring button ("Reconnect Hall Lightbulb") on unreachable device 5
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_5"))
        composeTestRule.onNodeWithTag("reconnect_button_5").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Reconnect Hall Lightbulb").assertIsDisplayed()

        // 2. Enter Edit Mode, rename device 1 to a 58-character name, and verify AutoResizingDeviceNameText layout
        val longName = "Upstairs Master Bedroom Recessed Ceiling Chandelier Switch"
        composeTestRule.onNodeWithTag("edit_devices_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("device_row_1"))
        composeTestRule.onNodeWithTag("edit_device_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("device_name_input")
            .performScrollTo()
            .performTextReplacement(longName)
        composeTestRule.onNodeWithTag("save_device_button").performClick()
        composeTestRule.waitForIdle()

        // Exit Edit Mode and verify the long name is visible on its own line above the status and category badges
        composeTestRule.onNodeWithTag("edit_devices_button").performClick()
        awaitLoopbackIo {
            runCatching {
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText(longName))
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText(longName).assertIsDisplayed()
        val longNameBounds = composeTestRule.onNodeWithTag("device_name_1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val connBounds = composeTestRule.onNodeWithTag("connection_status_badge_1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected long auto-resized name above connection badge (longNameBounds=$longNameBounds, connBounds=$connBounds)",
            longNameBounds.bottom <= connBounds.top + 4f
        )

        // 3. Scroll back to top Wi-Fi Home Bar, click Reset chip, and verify confirmation dialog
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
        composeTestRule.onNodeWithTag("reset_lan_data_chip")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Reset Homes, Switches & Rooms?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_reset_and_rescan_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_reset_empty_button").assertIsDisplayed()

        // 4. Confirm 'Reset Empty' and verify switches & extra homes are cleared into a clean 'My Home (0)' with EmptyDevicesState
        composeTestRule.onNodeWithTag("confirm_reset_empty_button").performClick()
        awaitLoopbackIo {
            runCatching {
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasTestTag("empty_devices_state_card"))
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithTag("empty_devices_state_card").assertIsDisplayed()

        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
        composeTestRule.onNodeWithText("My Home (0)")
            .performScrollTo()
            .assertIsDisplayed()

        // 5. Navigate to More tab, verify 'Reset Homes, Discovered Switches & Rooms' button is present, and restore preset
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_reset_lan_homes_switches_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("restore_screenshot_preset_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
        SemanticsMatcher("TestTag starts with '$prefix'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    @Test
    fun `verify E2E Reset to SSDP Discovery, Adopt Switch and Dimmer, Toggle Switch, and Scale Dimmer Brightness CUJ`() {
        composeTestRule.waitForIdle()

        FakeWeMoUpnpServer(
            initialFriendlyName = "Kitchen Island Switch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403CUJ001",
            udn = "uuid:Lightswitch-3_0-WLS0403CUJ001",
            macAddress = "EC1A59BB1101",
            wifiSsid = "Wemo.LS3W.220",
            initialRelayOn = false,
            enableSsdpResponder = true
        ).use { switchServer ->
            FakeWeMoUpnpServer(
                initialFriendlyName = "Kitchen Under-Cabinet Dimmer",
                deviceTypeUrn = "urn:Belkin:device:dimmer:1",
                modelName = "WDS060",
                serialNumber = "WDS060CUJ002",
                udn = "uuid:Dimmer-2_0-WDS060CUJ002",
                macAddress = "EC1A59BB2202",
                wifiSsid = "HeadFamily-2.4G",
                initialRelayOn = false,
                initialBrightness = 35,
                enableSsdpResponder = false
            ).use { dimmerServer ->
                // 1. Reset all Demo Showroom homes/switches/rooms into a clean Real LAN 'My Home (0)'
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
                composeTestRule.onNodeWithTag("reset_lan_data_chip")
                    .performScrollTo()
                    .performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("confirm_reset_empty_button").performClick()

                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasTestTag("empty_devices_state_card"))
                        true
                    }.getOrDefault(false)
                }
                composeTestRule.onNodeWithTag("empty_devices_state_card").assertIsDisplayed()

                // 2. Trigger SSDP Discovery from EmptyDevicesState -> discovers & auto-adopts Kitchen Island Switch over UDP M-SEARCH + HTTP/SOAP
                composeTestRule.onNodeWithTag("empty_state_scan_ssdp_button")
                    .performScrollTo()
                    .performClick()

                awaitLoopbackIo {
                    switchServer.receivedSsdpSearches.isNotEmpty() &&
                        runCatching {
                            composeTestRule.onNodeWithTag("ssdp_discovered_list")
                                .performScrollToNode(hasTestTag("ssdp_item_Kitchen Island Switch"))
                            true
                        }.getOrDefault(false)
                }
                composeTestRule.onNodeWithTag("ssdp_item_Kitchen Island Switch")
                    .performScrollTo()
                    .assertIsDisplayed()
                composeTestRule.onNodeWithTag("close_ssdp_scanner_button")
                    .performScrollTo()
                    .performClick()
                composeTestRule.waitForIdle()

                // Verify Kitchen Island Switch is in 'My Home (1)' and soft-AP SSID 'Wemo.LS3W.220' did not rename 'My Home'
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasText("Kitchen Island Switch"))
                        true
                    }.getOrDefault(false)
                }
                composeTestRule.onNodeWithText("Kitchen Island Switch").assertIsDisplayed()
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
                composeTestRule.onNodeWithText("My Home (1)")
                    .performScrollTo()
                    .assertIsDisplayed()

                // 3. Second hardware device (Kitchen Under-Cabinet Dimmer) comes online -> open SSDP Scanner -> click 'Adopt'
                dimmerServer.setSsdpDiscoverable(true)
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasTestTag("ssdp_discovery_banner"))
                composeTestRule.onNodeWithTag("open_ssdp_scanner_button").performClick()
                composeTestRule.waitForIdle()

                awaitLoopbackIo {
                    dimmerServer.receivedSsdpSearches.isNotEmpty() &&
                        runCatching {
                            composeTestRule.onNodeWithTag("ssdp_discovered_list")
                                .performScrollToNode(hasTestTag("ssdp_item_Kitchen Under-Cabinet Dimmer"))
                            true
                        }.getOrDefault(false)
                }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("adopt_ssdp_device_Kitchen Under-Cabinet Dimmer")
                    .performScrollTo()
                    .performClick()

                awaitLoopbackIo {
                    composeTestRule.onAllNodes(
                        hasTestTag("adopt_ssdp_device_Kitchen Under-Cabinet Dimmer") and hasText("Adopted")
                    ).fetchSemanticsNodes().isNotEmpty()
                }
                composeTestRule.onNodeWithTag("close_ssdp_scanner_button")
                    .performScrollTo()
                    .performClick()
                composeTestRule.waitForIdle()

                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
                        composeTestRule.onNodeWithText("My Home (2)")
                            .performScrollTo()
                            .assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                // 4. Toggle Adopted Switch ('Kitchen Island Switch') ON and OFF against FakeWeMoUpnpServer
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Kitchen Island Switch"))
                assertEquals(false, switchServer.relayStateOn.get())
                composeTestRule.onNodeWithContentDescription(
                    "Toggle power for Kitchen Island Switch, currently Off"
                ).performClick()

                awaitLoopbackIo { switchServer.relayStateOn.get() }
                assertTrue(switchServer.relayStateOn.get())
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithContentDescription(
                            "Toggle power for Kitchen Island Switch, currently On"
                        ).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                composeTestRule.onNodeWithContentDescription(
                    "Toggle power for Kitchen Island Switch, currently On"
                ).performClick()
                awaitLoopbackIo { !switchServer.relayStateOn.get() }
                assertEquals(false, switchServer.relayStateOn.get())

                // 5. Toggle Adopted Dimmer ('Kitchen Under-Cabinet Dimmer') ON from 35% Standby
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Kitchen Under-Cabinet Dimmer"))
                composeTestRule.onNodeWithText("OFF • 35%", useUnmergedTree = true).assertIsDisplayed()
                assertEquals(false, dimmerServer.relayStateOn.get())

                composeTestRule.onNodeWithContentDescription(
                    "Toggle power for Kitchen Under-Cabinet Dimmer, currently Off"
                ).performClick()
                awaitLoopbackIo { dimmerServer.relayStateOn.get() }
                assertTrue(dimmerServer.relayStateOn.get())
                assertEquals(35, dimmerServer.dimmerBrightness.get())
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithText("ON • 35%", useUnmergedTree = true).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                // 6. Expand Dimmer Drawer -> Scale Brightness via Preset Chips (75% & 21%)
                composeTestRule.onNodeWithText("Kitchen Under-Cabinet Dimmer").performClick()
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("75%")
                    .performScrollTo()
                    .performClick()
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == 75 }
                assertEquals(75, dimmerServer.dimmerBrightness.get())
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithText("ON • 75%", useUnmergedTree = true).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                composeTestRule.onNodeWithText("21%")
                    .performScrollTo()
                    .performClick()
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == 21 }
                assertEquals(21, dimmerServer.dimmerBrightness.get())
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithText("ON • 21%", useUnmergedTree = true).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                // 7. Scale Dimmer Brightness via Continuous Slider Touch Gesture (~88%)
                composeTestRule.onNode(hasTestTagPrefix("brightness_slider_"))
                    .performScrollTo()
                    .performTouchInput { click(percentOffset(0.88f, 0.5f)) }
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() >= 80 }
                val sliderBrightness = dimmerServer.dimmerBrightness.get()
                assertTrue("Expected slider brightness >= 80, got $sliderBrightness", sliderBrightness >= 80)
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithText("ON • ${sliderBrightness}%", useUnmergedTree = true).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                // 8. Toggle Hardware Night Mode (20% Cap via ConfigureNightMode SOAP)
                composeTestRule.onNodeWithText("Night Mode (20%)")
                    .performScrollTo()
                    .performClick()
                awaitLoopbackIo { dimmerServer.nightModeEnabled.get() }
                assertTrue(dimmerServer.nightModeEnabled.get())
                assertTrue(dimmerServer.receivedSoapActions.any { it.contains("ConfigureNightMode") })

                // 9. Scale Dimmer via Room Master Brightness Slider (room_dimmer_button_Kitchen -> room_dimmer_slider_Kitchen)
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasTestTag("room_header_Kitchen"))
                composeTestRule.onNodeWithTag("room_dimmer_button_Kitchen").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("room_dimmer_slider_Kitchen")
                    .performScrollTo()
                    .performTouchInput { click(percentOffset(0.5f, 0.5f)) }
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() in 40..60 }
                val roomMasterBri = dimmerServer.dimmerBrightness.get()
                assertTrue("Expected room master dimmer brightness in 40..60, got $roomMasterBri", roomMasterBri in 40..60)

                // 10. Scale Dimmer in 10-Foot Android TV Mode (+10% / -10% Step Buttons & Movie Mode 20% Scene)
                composeTestRule.onNodeWithTag("header_tv_mode_button").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("tv_dashboard_screen").assertIsDisplayed()

                // Move TV D-Pad focus to Kitchen Under-Cabinet Dimmer (device 2 of 2)
                composeTestRule.onNodeWithTag("tv_remote_dpad_next")
                    .performScrollTo()
                    .performClick()
                composeTestRule.waitForIdle()

                val beforeTvPlus = dimmerServer.dimmerBrightness.get()
                composeTestRule.onNodeWithTag("tv_remote_dpad_dim_up")
                    .performScrollTo()
                    .performClick()
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == (beforeTvPlus + 10).coerceAtMost(100) }
                assertEquals((beforeTvPlus + 10).coerceAtMost(100), dimmerServer.dimmerBrightness.get())

                composeTestRule.onNodeWithTag("tv_remote_dpad_dim_down")
                    .performScrollTo()
                    .performClick()
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == beforeTvPlus }
                assertEquals(beforeTvPlus, dimmerServer.dimmerBrightness.get())

                composeTestRule.onNodeWithTag("tv_scene_movie")
                    .performScrollTo()
                    .performClick()
                awaitLoopbackIo { dimmerServer.dimmerBrightness.get() == 20 }
                assertEquals(20, dimmerServer.dimmerBrightness.get())

                composeTestRule.onNodeWithTag("tv_exit_button").performClick()
                composeTestRule.waitForIdle()

                // 11. Toggle Dimmer Power OFF -> preserves 20% brightness in OFF standby
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Kitchen Under-Cabinet Dimmer"))
                composeTestRule.onNodeWithContentDescription(
                    "Toggle power for Kitchen Under-Cabinet Dimmer, currently On"
                ).performClick()
                awaitLoopbackIo { !dimmerServer.relayStateOn.get() }
                assertEquals(false, dimmerServer.relayStateOn.get())
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithText("OFF • 20%", useUnmergedTree = true).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
                }

                // Restore Classic Showroom preset at end of CUJ
                composeTestRule.onNodeWithTag("nav_tab_more").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("restore_screenshot_preset_button")
                    .performScrollTo()
                    .performClick()
                composeTestRule.waitForIdle()
            }
        }
    }
}
