package org.suppressingfire.wemi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full-stack Jetpack Compose + Loopback UPnP/SOAP E2E test for Step 1 On-Device Switch Rules:
 * adopts a loopback OpenWrt switch (with Long Press, Auto-Off Countdown, Sunset Schedule, and
 * Away Mode rules) and a loopback RTOS Dimmer V2, inspects on-device rules from both the Rules
 * tab and the Devices tab expanded diagnostics drawer, and verifies existing local rules UI
 * stays accessible.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareRulesE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(timeoutMs: Long = 6_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

    @Test
    fun `E2E Rules tab and Device Drawer inspect OpenWrt stored rules and classify RTOS Dimmer V2`() {
        composeTestRule.waitForIdle()
        val workDir = File(composeTestRule.activity.cacheDir, "e2e_hw_rules").apply { mkdirs() }
        val openWrtUdn = "uuid:Lightswitch-3_0-WLS0403E2E99"
        val rtosUdn = "uuid:Dimmer-2_0-WDS060E2E88"
        val sampleZip = FakeHardwareRulesServer.buildSampleOpenWrtRulesZip(
            ownerUdn = openWrtUdn,
            targetUdn = rtosUdn,
            workDir = workDir
        )

        FakeHardwareRulesServer(
            initialFriendlyName = "Porch Entry Switch",
            deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
            modelName = "WLS0403",
            serialNumber = "WLS0403E2E99",
            firmwareVersion = "WeMo_WW_2.00.11573.PVT-OWRT-LIGHTV2",
            udn = openWrtUdn,
            macAddress = "EC1A59CC1101",
            isRtos = false,
            supportsRulesDb = true,
            initialDbVersion = 3,
            initialRulesZipBytes = sampleZip,
            initialDeviceTimeUtc = 946_684_800L // Year 2000 -> triggers clock sync warning
        ).use { openWrtServer ->
            FakeHardwareRulesServer(
                initialFriendlyName = "Master Dimmer V2",
                deviceTypeUrn = "urn:Belkin:device:dimmer:1",
                modelName = "WDS060",
                serialNumber = "WDS060E2E88",
                firmwareVersion = "WeMo_WW_2.00.11564.PVT-RTOS-DimmerV2",
                udn = rtosUdn,
                macAddress = "EC1A59CC1102",
                isRtos = true,
                supportsRulesDb = false
            ).use { rtosServer ->
                // 1. Add both simulated switches via AddOrEditDeviceDialog (blank name -> auto-populates friendlyName, DeviceType, and Tier-1 hints from /setup.xml)
                addLoopbackSwitch(openWrtServer.port)
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasText("Porch Entry Switch"))
                        true
                    }.getOrDefault(false)
                }

                addLoopbackSwitch(rtosServer.port)
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasText("Master Dimmer V2"))
                        true
                    }.getOrDefault(false)
                }

                // 2. Navigate to Rules tab and confirm both On-Device Rules header and existing Add Rule button are visible
                composeTestRule.onNodeWithTag("nav_tab_rules").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("hardware_rules_header_card").assertIsDisplayed()
                composeTestRule.onNodeWithTag("scan_hardware_rules_button").assertIsDisplayed()
                composeTestRule.onNodeWithText("Read-Only").assertExists()
                composeTestRule.onNodeWithText("App-Run").assertExists()
                composeTestRule.onNodeWithTag("add_rule_button").assertIsDisplayed()

                // 3. Tap "Read Rules" to trigger Tier-2 verification + read-only rules.db download across the home
                composeTestRule.onNodeWithTag("scan_hardware_rules_button").performClick()
                awaitLoopbackIo {
                    openWrtServer.rulesDbDownloads.get() >= 1 && rtosServer.scpdFetches.get() >= 1
                }
                composeTestRule.waitForIdle()

                // Verify RTOS Dimmer V2 was fast-rejected via /rulesservice.xml with zero FetchRules calls
                assertTrue("RTOS switch should not receive FetchRules", rtosServer.fetchRulesCalls.get() == 0)

                // 4. Verify decoded OpenWrt rules, resolved target friendly name, clock warning, and solar location render
                awaitLoopbackIo {
                    runCatching {
                        composeTestRule.onNodeWithTag("rules_list")
                            .performScrollToNode(hasText("Hold to Toggle Porch"))
                        true
                    }.getOrDefault(false)
                }
                composeTestRule.onNodeWithText("Hold to Toggle Porch").assertIsDisplayed()
                composeTestRule.onNodeWithText("Toggle: Master Dimmer V2", substring = true).assertExists()
                composeTestRule.onNodeWithText("Switch clock needs sync").assertExists()
                composeTestRule.onNodeWithText("Auto-Off Bathroom Fan").assertExists()
                composeTestRule.onNodeWithText("Auto-Off after 30 min ON", substring = true).assertExists()
                composeTestRule.onNodeWithText("Evening Sunset Porch").assertExists()
                composeTestRule.onNodeWithText("Solar location on switch: Mountain View, CA, United States").assertExists()

                // 5. Verify RTOS Dimmer V2 displays clean unsupported capability readout with Night Mode note
                composeTestRule.onNodeWithTag("rules_list")
                    .performScrollToNode(hasText("Master Dimmer V2"))
                composeTestRule.onNodeWithText("Master Dimmer V2").assertIsDisplayed()
                composeTestRule.onNodeWithText("No On-Device Rules").assertExists()
                composeTestRule.onNodeWithText(
                    "Firmware does not expose on-device rules storage. Dimmer Night Mode is still stored on-device."
                ).assertExists()

                // 5b. Verify user can collapse the switch cards after reading rules so Local App Rules stay immediately reachable
                composeTestRule.onNodeWithTag("rules_list")
                    .performScrollToNode(hasTestTag("toggle_hardware_rules_device_list_button"))
                composeTestRule.onNodeWithText("Hide Switches").assertExists()
                composeTestRule.onNodeWithTag("toggle_hardware_rules_device_list_button").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("rules_list")
                    .performScrollToNode(hasTestTag("toggle_hardware_rules_device_list_button"))
                composeTestRule.onNodeWithTag("toggle_hardware_rules_device_list_button").assertIsDisplayed()
                composeTestRule.onNodeWithText("Show Switches (", substring = true).assertExists()
                composeTestRule.onNodeWithText("on-device rule(s) read across", substring = true).assertExists()
                composeTestRule.onNodeWithTag("rules_list")
                    .performScrollToNode(hasTestTag("add_rule_button"))
                composeTestRule.onNodeWithTag("add_rule_button").assertIsDisplayed()

                // Re-expand without triggering extra downloads and confirm cached rules reappear immediately
                val downloadsBeforeReexpand = openWrtServer.rulesDbDownloads.get()
                composeTestRule.onNodeWithTag("rules_list")
                    .performScrollToNode(hasTestTag("toggle_hardware_rules_device_list_button"))
                composeTestRule.onNodeWithTag("toggle_hardware_rules_device_list_button").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithText("Hide Switches").assertExists()
                assertTrue(
                    "Re-expanding should reuse cached snapshots without re-downloading",
                    openWrtServer.rulesDbDownloads.get() == downloadsBeforeReexpand
                )

                // 6. Switch back to Devices tab, expand Porch Entry Switch drawer, and verify per-switch readout
                composeTestRule.onNodeWithTag("nav_tab_devices").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasText("Porch Entry Switch"))
                composeTestRule.onNodeWithText("Porch Entry Switch").performClick()
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithText("4 rule(s) stored on switch (DB v3)").assertExists()
                composeTestRule.onNodeWithText("On-Device Rules: On-Device Ready • Long Press").assertExists()
                composeTestRule.onNodeWithText("Switch clock needs sync", substring = true).assertExists()
                composeTestRule.onNodeWithText("Toggle: Master Dimmer V2", substring = true).assertExists()
            }
        }
    }

    private fun addLoopbackSwitch(port: Int) {
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("device_name_input")
            .performScrollTo()
            .performTextReplacement("")
        composeTestRule.onNodeWithTag("device_ip_input")
            .performScrollTo()
            .performTextReplacement("127.0.0.1")
        composeTestRule.onNodeWithTag("device_port_input")
            .performScrollTo()
            .performTextReplacement(port.toString())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("save_device_button").performClick()
    }
}
