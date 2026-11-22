package org.suppressingfire.wemi

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.ui.SwitchViewModel
import org.suppressingfire.wemi.ui.components.WEMI_RELEASE_HISTORY

/**
 * Full-stack Jetpack Compose + Robolectric E2E validation for the mobile/tablet `TopHeader`,
 * in-app `ReleaseHistoryDialog` & catalog integrity, and UX hygiene invariants:
 * 1. Compact single-row `TopHeader` horizontal alignment, `>= 48.dp` Play Console accessibility
 *    touch targets, static `wemi` wordmark, `"All Off"` master action, and version pill placement
 *    across standard (`412dp`) and compact (`320dp`) phone viewports.
 * 2. `ReleaseHistoryDialog` display, `"Installed"` badge, top-right `Close` icon, footer `Close`,
 *    `BackHandler` dismissal, and `WEMI_RELEASE_HISTORY` catalog invariants.
 * 3. UX hygiene regressions: auto-hidden `SsdpDiscoveryBanner`, removed `PlayStoreAssetsCard` &
 *    manual reachability toggle, `"Simulate Motion"` (Demo) vs. `"Refresh Motion"` (Real LAN),
 *    and single-line non-squished timestamps in `SoapPacketLogCard`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeaderAndReleaseHistoryE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

    @Test
    fun `verify v23_0 compact single-row TopHeader layout, 48dp accessibility touch targets, and static brand wordmark`() {
        composeTestRule.waitForIdle()

        val wordmarkNode = composeTestRule.onNodeWithTag("brand_wordmark_text").assertIsDisplayed()
        val allOffNode = composeTestRule.onNodeWithTag("all_off_devices_button").assertIsDisplayed()
        val refreshNode = composeTestRule.onNodeWithTag("refresh_devices_button").assertIsDisplayed()
        val editNode = composeTestRule.onNodeWithTag("edit_devices_button").assertIsDisplayed()
        val addNode = composeTestRule.onNodeWithTag("add_device_button").assertIsDisplayed()

        // Legacy multi-line "LAN SWITCHES" subtitle must not exist in the compact single-row TopHeader
        composeTestRule.onNodeWithText("LAN SWITCHES").assertDoesNotExist()
        composeTestRule.onNodeWithText("All Off").assertIsDisplayed()

        val wordmarkSem = wordmarkNode.fetchSemanticsNode()
        assertEquals(
            "Expected static brand_wordmark_text not to expose Role.Button",
            null,
            wordmarkSem.config.getOrNull(SemanticsProperties.Role)
        )
        assertEquals(
            "Expected static brand_wordmark_text not to expose OnClick action",
            null,
            wordmarkSem.config.getOrNull(SemanticsActions.OnClick)
        )
        assertTrue(
            "Expected brand_wordmark_text to expose a non-blank accessibility ContentDescription",
            wordmarkSem.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
        )

        val headerActionControls = listOf(
            "all_off_devices_button" to allOffNode.fetchSemanticsNode(),
            "refresh_devices_button" to refreshNode.fetchSemanticsNode(),
            "edit_devices_button" to editNode.fetchSemanticsNode(),
            "add_device_button" to addNode.fetchSemanticsNode()
        )
        val minTouchHeightPx = with(composeTestRule.density) { 48.dp.toPx() } - 0.5f

        headerActionControls.forEach { (tag, semNode) ->
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

        val wordmarkBounds = wordmarkSem.boundsInRoot
        val allOffBounds = headerActionControls[0].second.boundsInRoot
        val refreshBounds = headerActionControls[1].second.boundsInRoot
        val editBounds = headerActionControls[2].second.boundsInRoot
        val addBounds = headerActionControls[3].second.boundsInRoot

        // All 5 elements must share the same single horizontal header row (vertical centers within 10px)
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

        // Subtle low-contrast version button stays inside the unused left brand zone without overlapping All Off or expanding header height
        val versionNode = composeTestRule.onNodeWithTag("app_version_button").assertIsDisplayed()
        val versionSem = versionNode.fetchSemanticsNode()
        val versionBounds = versionSem.boundsInRoot
        assertEquals(
            "Expected app_version_button to expose Role.Button",
            Role.Button,
            versionSem.config.getOrNull(SemanticsProperties.Role)
        )
        assertTrue(
            "Expected app_version_button to expose a non-blank accessibility ContentDescription",
            versionSem.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.isNotBlank() }
        )
        assertTrue(
            "Expected app_version_button to stay left of All Off ($versionBounds vs $allOffBounds)",
            versionBounds.right <= allOffBounds.left + 2f
        )
        assertTrue(
            "Expected app_version_button to stay vertically inside the single-row TopHeader band ($versionBounds vs $allOffBounds)",
            versionBounds.top >= allOffBounds.top - 2f && versionBounds.bottom <= allOffBounds.bottom + 2f
        )

        // Tapping the subtle version button opens the user-friendly Release History dialog with version numbers, dates, and summaries
        versionNode.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_history_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Release History").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_entry_v${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nov 21, 2026").assertIsDisplayed()
        composeTestRule.onNodeWithTag("release_history_list")
            .performScrollToNode(hasTestTag("release_entry_v1.0"))
        composeTestRule.onNodeWithTag("release_entry_v1.0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_release_history_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_history_dialog").assertDoesNotExist()

        // Static "wemi" wordmark is displayed, and the removed legacy wordmark toggle is absent on the More tab
        composeTestRule.onNodeWithText("wemi").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Header Logo Wordmark").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_devices").performClick()
        composeTestRule.waitForIdle()

        // Clicking "All Off" next to the Refresh button turns OFF all reachable controllable switches on the active home
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        allOffNode.performClick()
        awaitLoopbackIo {
            vm.togglingDeviceIds.value.isEmpty() &&
                vm.devices.value
                    .filter { it.networkKey == vm.activeNetworkKey.value && it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR }
                    .none { it.isOn }
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w412dp-h915dp-port-xxhdpi")
    fun `verify standard phone TopHeader places low-contrast version pill in unused gap beside wemi without shifting buttons`() {
        composeTestRule.waitForIdle()

        val wordmarkBounds = composeTestRule.onNodeWithTag("brand_wordmark_text")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val versionBounds = composeTestRule.onNodeWithTag("app_version_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val allOffBounds = composeTestRule.onNodeWithTag("all_off_devices_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val refreshBounds = composeTestRule.onNodeWithTag("refresh_devices_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val editBounds = composeTestRule.onNodeWithTag("edit_devices_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val addBounds = composeTestRule.onNodeWithTag("add_device_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot

        // On 412dp phones, version pill sits cleanly inside the unused gap between "wemi" and "All Off" on the same row
        assertTrue(
            "Expected app_version_button between wordmark and All Off on 412dp phone ($wordmarkBounds | $versionBounds | $allOffBounds)",
            wordmarkBounds.right <= versionBounds.left + 2f &&
                versionBounds.right <= allOffBounds.left + 2f &&
                allOffBounds.right <= refreshBounds.left + 2f &&
                refreshBounds.right <= editBounds.left + 2f &&
                editBounds.right <= addBounds.left + 2f
        )
        assertTrue(
            "Expected version button to share exact single-row height without expanding TopHeader ($versionBounds vs $allOffBounds)",
            abs(versionBounds.center.y - allOffBounds.center.y) <= 4f &&
                abs(versionBounds.height - allOffBounds.height) <= 4f
        )
    }

    @Test
    @Config(sdk = [36], qualifiers = "w320dp-h640dp-port-hdpi")
    fun `verify compact 320dp TopHeader tucks version pill, ReleaseHistoryDialog supports icon and Back close, and catalog invariants hold`() {
        composeTestRule.waitForIdle()

        // 1. Validate WEMI_RELEASE_HISTORY catalog invariants (unique LazyColumn keys, descending order, current version present)
        assertTrue("Expected non-empty release history catalog", WEMI_RELEASE_HISTORY.isNotEmpty())
        val versions = WEMI_RELEASE_HISTORY.map { it.version }
        assertEquals(
            "Expected unique version keys in WEMI_RELEASE_HISTORY so LazyColumn keys never collide",
            versions.size,
            versions.distinct().size
        )
        assertEquals(
            "Expected newest WEMI_RELEASE_HISTORY entry to match BuildConfig.VERSION_NAME",
            "v${BuildConfig.VERSION_NAME}",
            WEMI_RELEASE_HISTORY.first().version
        )
        val numericVersions = WEMI_RELEASE_HISTORY.map { entry ->
            assertTrue("Version must start with 'v': ${entry.version}", entry.version.startsWith("v"))
            assertTrue("Release date must be non-blank for ${entry.version}", entry.releaseDate.isNotBlank())
            assertTrue("Headline must be non-blank for ${entry.version}", entry.headline.isNotBlank())
            assertTrue("Summary must be non-blank for ${entry.version}", entry.summary.isNotBlank())
            assertFalse("Summary must not leak 'WeMo' trademark: ${entry.version}", entry.summary.contains("wemo", ignoreCase = true))
            entry.version.removePrefix("v").toDouble()
        }
        assertEquals(
            "Expected strictly descending release history ordering",
            numericVersions.sortedDescending(),
            numericVersions
        )

        // 2. On compact 320dp viewports, tucked version pill stays strictly left of All Off inside the 48.dp header band
        val versionBounds = composeTestRule.onNodeWithTag("app_version_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val allOffBounds = composeTestRule.onNodeWithTag("all_off_devices_button")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected compact tucked version pill left of All Off ($versionBounds vs $allOffBounds)",
            versionBounds.right <= allOffBounds.left + 2f
        )
        assertTrue(
            "Expected compact tucked version pill vertically within single-row TopHeader band ($versionBounds vs $allOffBounds)",
            versionBounds.top >= allOffBounds.top - 2f && versionBounds.bottom <= allOffBounds.bottom + 2f
        )

        // 3. Open dialog, confirm Installed badge & top-right X dismissal, then reopen and verify BackHandler dismissal
        composeTestRule.onNodeWithTag("app_version_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_history_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Installed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("close_release_history_icon_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_history_dialog").assertDoesNotExist()

        composeTestRule.onNodeWithTag("app_version_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_history_dialog").assertIsDisplayed()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("release_history_dialog").assertDoesNotExist()
    }

    @Test
    fun `verify UX hygiene for Demo vs Real LAN motion drawer action, auto-hidden SSDP banner, edit dialog, and single-line SOAP timestamps`() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]

        // 1. In Demo Showroom Mode with devices present and idle, SsdpDiscoveryBanner auto-hides and PlayStoreAssetsCard is absent
        composeTestRule.onNodeWithTag("ssdp_discovery_banner").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Google Play Store Assets Ready").assertDoesNotExist()
        composeTestRule.onNodeWithTag("nav_tab_devices").performClick()
        composeTestRule.waitForIdle()

        // 2. Demo Motion Sensor drawer displays "Simulate Motion"
        val demoMotion = vm.devices.value.first {
            it.networkKey == vm.activeNetworkKey.value && it.parsedType == DeviceType.MOTION_SENSOR
        }
        composeTestRule.runOnUiThread { vm.toggleExpandDevice(demoMotion.id) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("devices_list")
            .performScrollToNode(hasAnyDescendant(hasTestTag("trigger_motion_${demoMotion.id}")))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("trigger_motion_${demoMotion.id}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Simulate Motion").assertIsDisplayed()

        // 3. AddOrEditDeviceDialog omits the removed manual "Detected on Local Wi-Fi" toggle
        composeTestRule.onNodeWithTag("add_device_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Detected on Local Wi-Fi").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // 4. Switch to Real LAN Mode over loopback servers: Motion Sensor shows "Refresh Motion",
        //    and a long-named switch SOAP entry keeps its timestamp single-line horizontal (width > height)
        FakeUpnpServer(
            initialFriendlyName = "Hallway Motion Sensor",
            deviceTypeUrn = "urn:Belkin:device:sensor:1",
            modelName = "Sensor",
            serialNumber = "SENSORUX001",
            udn = "uuid:Sensor-1_0-SENSORUX001",
            macAddress = "EC1A59990011",
            initialRelayOn = false,
            enableSsdpResponder = true,
            registerAsCandidatePort = true
        ).use { _ ->
            FakeUpnpServer(
                initialFriendlyName = "Downstairs Hallway Architectural Dimmer Control Switch",
                deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
                modelName = "WLS0403",
                serialNumber = "LONGSWUX002",
                udn = "uuid:Lightswitch-3_0-LONGSWUX002",
                macAddress = "EC1A59990022",
                isRtos = true,
                initialRelayOn = false,
                enableSsdpResponder = true,
                registerAsCandidatePort = true
            ).use { longSwitchServer ->
                composeTestRule.runOnUiThread {
                    vm.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
                }
                awaitLoopbackIo {
                    !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() && !vm.isRefreshing.value
                }

                composeTestRule.runOnUiThread { vm.refreshDevicesFromNetwork() }
                awaitLoopbackIo {
                    vm.devices.value.size == 2 && !vm.isRefreshing.value
                }

                val realMotion = vm.devices.value.first { it.serialNumber == "SENSORUX001" }
                val longSwitch = vm.devices.value.first { it.serialNumber == "LONGSWUX002" }

                composeTestRule.runOnUiThread { vm.toggleExpandDevice(realMotion.id) }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasAnyDescendant(hasTestTag("trigger_motion_${realMotion.id}")))
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("trigger_motion_${realMotion.id}").assertIsDisplayed()
                composeTestRule.onNodeWithText("Refresh Motion").assertIsDisplayed()
                composeTestRule.onNodeWithText("Simulate Motion").assertDoesNotExist()

                // Toggle the long-named switch and verify More -> SOAP Packet Log timestamp stays single-line horizontal
                composeTestRule.onNodeWithTag("devices_list")
                    .performScrollToNode(hasTestTag("device_row_${longSwitch.id}"))
                composeTestRule.onNodeWithTag("power_button_${longSwitch.id}").performClick()
                awaitLoopbackIo {
                    longSwitchServer.relayStateOn.get() && vm.togglingDeviceIds.value.isEmpty()
                }

                val latestEntry = vm.soapLogs.value.first { it.deviceName == longSwitch.name }
                composeTestRule.onNodeWithTag("nav_tab_more").performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("more_screen_list")
                    .performScrollToNode(hasAnyDescendant(hasTestTag("clear_soap_logs_button")))
                composeTestRule.waitForIdle()

                val titleBounds = composeTestRule.onNodeWithText(
                    "${latestEntry.deviceName} • ${latestEntry.soapAction}"
                ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val timestampNode = composeTestRule.onAllNodes(
                    androidx.compose.ui.test.hasText(latestEntry.timestamp)
                )[0].assertIsDisplayed()
                val timestampBounds = timestampNode.fetchSemanticsNode().boundsInRoot
                val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
                timestampNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getResult ->
                    getResult(textLayouts)
                }
                assertTrue("Expected TextLayoutResult for SOAP timestamp", textLayouts.isNotEmpty())
                val layout = textLayouts.first()
                assertEquals(
                    "Expected SOAP log timestamp '${latestEntry.timestamp}' to stay on 1 single line",
                    1,
                    layout.lineCount
                )
                assertTrue(
                    "Expected SOAP log timestamp '${latestEntry.timestamp}' glyphs (${layout.getLineRight(0)}) to fit within box width (${layout.size.width})",
                    layout.getLineRight(0) <= layout.size.width + 1f
                )
                assertTrue(
                    "Expected weighted SOAP header title ($titleBounds) to sit cleanly left of timestamp ($timestampBounds)",
                    titleBounds.right <= timestampBounds.left + 1f &&
                        abs(titleBounds.top - timestampBounds.top) <= 2f
                )
            }
        }
    }
}
