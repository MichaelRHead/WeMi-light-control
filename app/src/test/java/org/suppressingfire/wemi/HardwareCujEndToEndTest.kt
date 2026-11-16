package org.suppressingfire.wemi

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
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full-Stack Hardware Discovery, Adoption, Relay Toggle & Dimmer Scaling CUJ End-to-End Test.
 *
 * Exercises Reset -> SSDP UDP Discovery -> Adopt Switch & Dimmer -> Toggle Switch -> Scale Dimmer
 * Brightness (Presets, Continuous Slider, Hardware Night Mode, and Room Master) against live
 * loopback [FakeUpnpServer] instances.
 *
 * Disconnected 9-port self-healing, live UPnP GENA push, and zero-cloud `10.22.22.1` provisioning
 * CUJs live in [ProvisioningAndGenaCujE2ETest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareCujEndToEndTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

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

                // 4. Toggle Adopted Switch ('Kitchen Island Switch') ON and OFF against FakeUpnpServer
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

                // 10. Toggle Dimmer Power OFF -> preserves brightness in OFF standby
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
}
