package org.suppressingfire.wemi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.network.TelemetryFileExporter

/**
 * Full-Stack Self-Healing Reconnect, Live UPnP GENA Push & Zero-Cloud `10.22.22.1` Provisioning
 * Critical User Journey (CUJ) End-to-End Tests:
 * 1. Real LAN Offline Revert -> Red `DisconnectedRingButton` -> 9-Port Self-Healing Port-Hop
 *    Recovery -> Live UPnP GENA `SUBSCRIBE` & `NOTIFY` Push -> Live SOAP Telemetry File Export.
 * 2. Zero-Cloud `10.22.22.1` Setup AP Hardware Probe -> `GetApList` 2.4 GHz Chip Selection ->
 *    RTOS Method 2 AES-128-CBC `ConnectHomeNetwork` Provisioning -> Screenshot Telemetry Export.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProvisioningAndGenaCujE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

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
            //    -> must revert optimistic toggle and transition row to Disconnected with red DisconnectedRingButton
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

            // 4. Bring FakeUpnpServer's shifted port online and tap the red 'Reconnect Master Bedroom Lamp' ring
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

            // 5. Trigger Home Reconnect ('reconnect_home_wifi_button') in Real LAN Mode so GenaSubscriptionManager
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
            assertTrue(exportedText.contains("--- [2. REMEMBERED WI-FI HOMES (1)] ---"))
            assertTrue(exportedText.contains("[HOME] key="))
            assertFalse(exportedText.contains("[NET] mode=REAL-LAN ip= "))

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
            val copiedSnapshotText = telemetryFile.readText()
            assertTrue(copiedSnapshotText.contains("[STEP 6 CLOSE] CloseSetup -> success"))
            assertFalse(copiedSnapshotText.contains("HomeRouterSecret99"))

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

            // 7. Export full 4-section telemetry from More -> Save/Share Log and verify session setup trace + homes + redaction
            composeTestRule.onNodeWithTag("nav_tab_more").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("more_screen_list")
                .performScrollToNode(hasTestTag("export_telemetry_file_button"))
            composeTestRule.onNodeWithTag("export_telemetry_file_button").performClick()
            composeTestRule.waitForIdle()

            val fullMoreExport = telemetryFile.readText()
            assertTrue(fullMoreExport.contains("[STEP 6 CLOSE] CloseSetup -> success"))
            assertTrue(fullMoreExport.contains("[HOME] key="))
            assertTrue(fullMoreExport.contains("Stairway 3-Way Switch"))
            assertFalse(fullMoreExport.contains("HomeRouterSecret99"))
        }
    }
}
