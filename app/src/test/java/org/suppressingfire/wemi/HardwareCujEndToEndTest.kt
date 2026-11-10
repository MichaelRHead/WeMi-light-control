package org.suppressingfire.wemi

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import org.suppressingfire.wemi.network.TelemetryFileExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full-Stack Hardware Critical User Journey (CUJ) End-to-End Tests.
 *
 * Exercises multi-step real-hardware flows across Jetpack Compose UI, `SwitchViewModel`,
 * `SwitchRepository`, `NetworkClient`, `ProvisionerClient`, `GenaSubscriptionManager`,
 * and loopback `FakeUpnpServer` instances:
 * 1. Reset -> SSDP UDP Discovery -> Adopt Switch & Dimmer -> Toggle Switch -> Scale Dimmer Brightness
 *    (Presets, Continuous Slider, Night Mode, Room Master & Android TV D-Pad).
 * 2. Real LAN Offline Revert -> Red `DisconnectedRingButton` -> 9-Port Self-Healing Port-Hop
 *    Recovery -> Live UPnP GENA `SUBSCRIBE` & `NOTIFY` Push -> Live SOAP Telemetry File Export.
 * 3. Zero-Cloud `10.22.22.1` Setup AP Hardware Probe -> `GetApList` 2.4 GHz Chip Selection ->
 *    RTOS Method 2 AES-128-CBC `ConnectHomeNetwork` Provisioning -> Screenshot Telemetry Export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareCujEndToEndTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

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

    private fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
        SemanticsMatcher("TestTag starts with '$prefix'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    @Test
    fun `verify E2E Reset to SSDP Discovery, Adopt Switch and Dimmer, Toggle Switch, and Scale Dimmer Brightness CUJ`() {
        composeTestRule.waitForIdle()

        FakeUpnpServer(
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
            FakeUpnpServer(
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

                // 10. Toggle Dimmer Power OFF -> preserves 20% brightness in OFF standby
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Kitchen Under-Cabinet Dimmer"))
                composeTestRule.onNodeWithContentDescription(
                    "Toggle power for Kitchen Under-Cabinet Dimmer, currently On"
                ).performClick()
                awaitLoopbackIo { !dimmerServer.relayStateOn.get() }
                assertEquals(false, dimmerServer.relayStateOn.get())
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithText("OFF • ${roomMasterBri}%", useUnmergedTree = true).assertIsDisplayed()
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

    @Test
    fun `verify E2E Disconnected switch 9-port self-healing reconnect ring, GENA push notification UI update, and Live SOAP Log export`() {
        composeTestRule.waitForIdle()

        FakeUpnpServer(
            initialFriendlyName = "Master Bedroom Lamp",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403HEAL99",
            udn = "uuid:Lightswitch-3_0-WLS0403HEAL99",
            macAddress = "EC1A59DD8899",
            wifiSsid = "HeadFamily-2.4G",
            initialRelayOn = true,
            enableSsdpResponder = false,
            registerAsCandidatePort = false
        ).use { server ->
            // 1. Reset to clean Real LAN Mode ('My Home (0)')
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

            // 2. Add 'Master Bedroom Lamp' manually on closed port 49159 while server port is not yet discoverable
            composeTestRule.onNodeWithTag("empty_state_add_manual_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("device_name_input")
                .performScrollTo()
                .performTextReplacement("Master Bedroom Lamp")
            composeTestRule.onNodeWithTag("device_ip_input")
                .performScrollTo()
                .performTextReplacement("127.0.0.1")
            composeTestRule.onNodeWithTag("device_port_input")
                .performScrollTo()
                .performTextReplacement("49159")
            composeTestRule.onNodeWithTag("save_device_button").performClick()

            awaitLoopbackIo {
                runCatching {
                    composeTestRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasText("Master Bedroom Lamp"))
                    true
                }.getOrDefault(false)
            }

            // 3. Attempt to toggle 'Master Bedroom Lamp' while 127.0.0.1:49159 is unreachable in Real LAN Mode
            //    -> must revert optimistic toggle and transition row to Disconnected with red WeMoDisconnectedRingButton
            composeTestRule.onNodeWithContentDescription(
                "Toggle power for Master Bedroom Lamp, currently On"
            ).performClick()

            awaitLoopbackIo {
                runCatching {
                    composeTestRule.onNodeWithContentDescription("Reconnect Master Bedroom Lamp")
                        .assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithContentDescription("Reconnect Master Bedroom Lamp").assertIsDisplayed()

            // 4. Bring FakeWeMoUpnpServer's shifted port online and tap the red 'Reconnect Master Bedroom Lamp' ring
            server.setCandidatePortDiscoverable(true)
            composeTestRule.onNodeWithContentDescription("Reconnect Master Bedroom Lamp").performClick()

            awaitLoopbackIo {
                server.receivedSoapActions.any { it.contains("GetBinaryState") } &&
                    runCatching {
                        composeTestRule.onNodeWithContentDescription(
                            "Toggle power for Master Bedroom Lamp, currently On"
                        ).assertIsDisplayed()
                        true
                    }.getOrDefault(false)
            }

            // 5. Trigger Home Reconnect ('reconnect_home_wifi_button') in Real LAN Mode so WeMoGenaSubscriptionManager
            //    subscribes to 127.0.0.1:server.port via SUBSCRIBE /upnp/event/basicevent1
            composeTestRule.onNodeWithTag("devices_list")
                .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
            composeTestRule.onNodeWithTag("reconnect_home_wifi_button").performClick()

            awaitLoopbackIo {
                server.lastGenaCallbackPort.get() > 0
            }
            val genaCallbackPort = server.lastGenaCallbackPort.get()
            assertTrue("Expected GENA SUBSCRIBE callback port > 0, got $genaCallbackPort", genaCallbackPort > 0)

            // 6. Simulate physical wall-switch press OFF via HTTP NOTIFY to embedded GENA server
            server.relayStateOn.set(false)
            val notifyStatus = server.pushGenaNotify(
                callbackPort = genaCallbackPort,
                deviceKey = "WLS0403HEAL99",
                binaryStatePayload = "0"
            )
            assertTrue("Expected HTTP 200 OK from GENA callback server, got $notifyStatus", notifyStatus.contains("200 OK"))

            // Verify Compose UI row transitions automatically from ON to OFF via GENA push
            composeTestRule.onNodeWithTag("devices_list")
                .performScrollToNode(hasText("Master Bedroom Lamp"))
            awaitLoopbackIo {
                runCatching {
                    composeTestRule.onNodeWithContentDescription(
                        "Toggle power for Master Bedroom Lamp, currently Off"
                    ).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            // 7. Navigate to More tab, verify Live UPnP / SOAP Packet Log Inspector, and export telemetry file
            composeTestRule.onNodeWithTag("nav_tab_more").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("more_screen_list")
                .performScrollToNode(hasTestTag("export_telemetry_file_button"))
            composeTestRule.onNodeWithTag("export_telemetry_file_button")
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            val exportedFile = TelemetryFileExporter.latestTelemetryFile(composeTestRule.activity)
            assertTrue("Expected exported telemetry file at ${exportedFile.absolutePath}", exportedFile.exists())
            val exportedText = exportedFile.readText()
            assertTrue(exportedText.contains("GetBinaryState"))
            assertTrue(exportedText.contains("GENA"))

            // Restore Classic Showroom preset
            composeTestRule.onNodeWithTag("more_screen_list")
                .performScrollToNode(hasTestTag("restore_screenshot_preset_button"))
            composeTestRule.onNodeWithTag("restore_screenshot_preset_button")
                .performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun `verify E2E zero-cloud 10_22_22_1 Setup AP hardware scan, AES Method 2 provisioning, and screenshot telemetry export over loopback server`() {
        composeTestRule.waitForIdle()

        FakeUpnpServer(
            initialFriendlyName = "WeMo LightSwitch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403PROV88",
            firmwareVersion = "WeMo_WW_2.00.12055.PVT",
            udn = "uuid:Lightswitch-3_0-WLS0403PROV88",
            macAddress = "EC1A59EE4455",
            isRtos = true,
            wifiSsid = "Wemo.LS3W.880",
            registerAsCandidatePort = true
        ).use { setupServer ->
            // 1. Open AddOrEditDeviceDialog -> launch ProvisionApDialog
            composeTestRule.onNodeWithTag("add_device_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("open_provision_ap_wizard_button").performClick()
            composeTestRule.waitForIdle()

            // 2. Point Setup AP IP to 127.0.0.1 and click 'Scan APs'
            composeTestRule.onNodeWithTag("provision_ap_ip_input")
                .performScrollTo()
                .performTextReplacement("127.0.0.1")
            composeTestRule.onNodeWithTag("scan_setup_ap_button")
                .performScrollTo()
                .performClick()

            // Wait for probeSetupDevice (/setup.xml + GetMetaInfo) and GetApList to complete over loopback HTTP/SOAP
            awaitLoopbackIo {
                setupServer.receivedSoapActions.any { it.contains("GetApList") } &&
                    runCatching {
                        composeTestRule.onNodeWithText("HeadFamily-2.4G (Ch 6 • 91%)")
                            .performScrollTo()
                            .assertIsDisplayed()
                        true
                    }.getOrDefault(false)
            }

            // 3. Enter Wi-Fi password, custom switch name ('Stairway 3-Way Switch'), and enable Keep Dialog Open on Success
            composeTestRule.onNodeWithTag("provision_password_input")
                .performScrollTo()
                .performTextReplacement("HomeRouterSecret99")
            composeTestRule.onNodeWithTag("provision_device_name_input")
                .performScrollTo()
                .performTextReplacement("Stairway 3-Way Switch")
            composeTestRule.onNodeWithTag("keep_provision_dialog_open_chip")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            // 4. Click 'Provision Switch' -> runs ChangeFriendlyName, double ConnectHomeNetwork, GetNetworkStatus polls, CloseSetup & SetSetupDoneStatus
            composeTestRule.onNodeWithTag("confirm_provision_ap_button")
                .performClick()

            awaitLoopbackIo(timeoutMs = 8_000L) {
                setupServer.closeSetupCalled.get() && setupServer.setupDoneCalled.get()
            }

            assertEquals(2, setupServer.connectHomeNetworkCalls.get())
            assertEquals("Stairway 3-Way Switch", setupServer.friendlyName.get())
            assertTrue(setupServer.closeSetupCalled.get())
            assertTrue(setupServer.setupDoneCalled.get())

            // 5. Verify dialog stayed open ('Keep Dialog Open on Success: ON'), export screenshot telemetry via 'Copy', and close dialog
            composeTestRule.onNodeWithTag("copy_provision_debug_button")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()

            val telemetryFile = TelemetryFileExporter.latestTelemetryFile(composeTestRule.activity)
            assertTrue(telemetryFile.exists())
            assertTrue(telemetryFile.readText().contains("[STEP 6 CLOSE] CloseSetup -> success"))

            composeTestRule.onNodeWithText("Cancel").performClick()
            composeTestRule.waitForIdle()

            // 6. Verify 'Stairway 3-Way Switch' was added to the Compose Devices list under 'Hallway & Landing'
            awaitLoopbackIo {
                runCatching {
                    composeTestRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasText("Stairway 3-Way Switch"))
                    true
                }.getOrDefault(false)
            }
            composeTestRule.onNodeWithText("Stairway 3-Way Switch").assertIsDisplayed()
        }
    }
}
