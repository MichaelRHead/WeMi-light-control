package org.suppressingfire.wemi

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.lifecycle.ViewModelProvider
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.network.HouseholdSyncBundle
import org.suppressingfire.wemi.network.HouseholdSyncCodec
import org.suppressingfire.wemi.network.HouseholdSyncLanClient
import org.suppressingfire.wemi.network.HouseholdSyncLanServer
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.ReceivedHouseholdSync
import org.suppressingfire.wemi.network.SyncedDeviceProfile
import org.suppressingfire.wemi.network.SyncedHomeProfile
import org.suppressingfire.wemi.ui.SwitchViewModel

/**
 * Full-stack Jetpack Compose + Robolectric E2E tests for Household Configuration Sync:
 * 1. Phone `More` tab JSON export/import confirmation dialog + encrypted LAN Send to a TV receiver.
 * 2. Phone `Receive` dialog + incoming SPAKE2 push with both `Cancel` and `Replace All` flows.
 * 3. Phone `Send` dialog inline private-IP / TCP-port validation errors and target confirmation.
 * 4. Native Android TV (`w960dp-h540dp-land-television-xhdpi`) `Sync` D-Pad flow receiving a
 *    PIN-encrypted push from a phone, containing Up/Down focus, and applying rooms via OK press.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HouseholdSyncE2ETest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        NetworkMonitor.resetTestOverrides()
    }

    @After
    fun tearDown() {
        NetworkMonitor.resetTestOverrides()
    }

    private fun awaitLoopbackIo(timeoutMs: Long = 5_000L, condition: () -> Boolean) =
        composeTestRule.awaitLoopbackIo(timeoutMs, condition)

    @Test
    fun verifyMoreTabHouseholdSyncJsonImportAndEncryptedLanSendFlow() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]

        // 1. Open More tab and scroll to Household Config Sync card
        composeTestRule.onNodeWithTag("nav_tab_more").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("more_household_sync_card"))
        composeTestRule.onNodeWithTag("more_send_lan_sync_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_receive_lan_sync_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_export_household_json_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("more_import_household_json_button").assertIsDisplayed()

        // 2. Stage a JSON import (simulating SAF OpenDocument read) -> confirm Merge in UI
        val importedBundle = HouseholdSyncBundle(
            sourceDeviceLabel = "Backup JSON",
            homes = listOf(
                SyncedHomeProfile(
                    networkKey = "wifi_cabin_sync",
                    homeName = "Mountain Cabin",
                    ssidLabel = "CabinNet",
                    subnetPrefix = "192.168.55",
                    gatewayIp = "192.168.55.1"
                )
            ),
            devices = listOf(
                SyncedDeviceProfile(
                    name = "Cedar Porch Lantern",
                    roomName = "Sunroom Loft",
                    networkKey = "wifi_cabin_sync",
                    deviceType = DeviceType.SWITCH_PLUG.name,
                    ipAddress = "192.168.55.21",
                    port = 49153,
                    serialNumber = "221840KSYNC01",
                    sortOrder = 0
                )
            )
        )
        val jsonText = HouseholdSyncCodec.encodeToJson(importedBundle)
        composeTestRule.runOnUiThread {
            vm.householdSync.stageJsonImport(jsonText, sourceDescription = "JSON File")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("household_import_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sunroom Loft", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_import_merge_button").performClick()

        awaitLoopbackIo {
            vm.devices.value.any { it.serialNumber == "221840KSYNC01" && it.roomName == "Sunroom Loft" }
        }
        assertEquals("wifi_cabin_sync", vm.activeNetworkKey.value)

        // 3. Push current household over loopback encrypted LAN from the Send to TV dialog
        val tvServer = HouseholdSyncLanServer(
            localIpProvider = { "127.0.0.1" },
            preferredListenPort = 0,
            discoveryUdpPort = 0,
            allowLoopbackForTests = true
        )
        try {
            val receivedRef = AtomicReference<ReceivedHouseholdSync?>(null)
            val session = tvServer.startSession(
                deviceLabel = "Den TV",
                isTv = true,
                bindAddress = InetAddress.getByName("127.0.0.1"),
                onPayloadReceived = { receivedRef.set(it) },
                onSessionExpired = {},
                onSessionLockedOut = {}
            )!!

            composeTestRule.onNodeWithTag("more_send_lan_sync_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("household_sync_send_dialog").assertIsDisplayed()

            composeTestRule.onNodeWithTag("household_sync_target_ip_input").performTextClearance()
            composeTestRule.onNodeWithTag("household_sync_target_ip_input").performTextInput("127.0.0.1")
            composeTestRule.onNodeWithTag("household_sync_target_port_input").performTextClearance()
            composeTestRule.onNodeWithTag("household_sync_target_port_input").performTextInput(session.port.toString())
            composeTestRule.onNodeWithTag("household_sync_pin_input").performTextInput(session.pairingCode)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("household_sync_send_confirm_button").performClick()

            awaitLoopbackIo(timeoutMs = 8_000L) {
                receivedRef.get() != null && !vm.householdSyncState.value.showSendDialog
            }
            val received = receivedRef.get()
            assertNotNull("TV server must receive encrypted household push", received)
            assertTrue(received!!.bundle.devices.any { it.name == "Cedar Porch Lantern" && it.roomName == "Sunroom Loft" })
        } finally {
            tvServer.close()
        }
    }

    @Test
    fun verifyMobileReceiveDialogSupportsCancelAndReplaceAllOverSpake2Lan() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        val initialSerials = vm.devices.value.map { it.serialNumber }.toSet()

        val incomingBundle = HouseholdSyncBundle(
            sourceDeviceLabel = "Partner Pixel",
            homes = listOf(
                SyncedHomeProfile("wifi_replace_home", "Lake House", "LakeWiFi", "192.168.88", "192.168.88.1")
            ),
            devices = listOf(
                SyncedDeviceProfile(
                    name = "Boathouse Flood",
                    roomName = "Dock",
                    networkKey = "wifi_replace_home",
                    deviceType = DeviceType.SWITCH_PLUG.name,
                    ipAddress = "192.168.88.14",
                    port = 49153,
                    serialNumber = "WLS040DOCK01",
                    sortOrder = 0
                )
            )
        )
        val senderClient = HouseholdSyncLanClient(localIpProvider = { "127.0.0.1" }, allowLoopbackForTests = true)

        // 1. Open Receive dialog, receive SPAKE2 push, then Cancel -> prior devices stay intact
        composeTestRule.runOnUiThread {
            vm.householdSync.openReceiveDialog(isTv = false, preferredListenPort = 0, discoveryUdpPort = 0)
        }
        awaitLoopbackIo { vm.householdSyncState.value.activeReceiveSession != null }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("household_sync_receive_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_sync_receive_pin").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_sync_receive_endpoint").assertIsDisplayed()

        val firstSession = vm.householdSyncState.value.activeReceiveSession!!
        val firstPush = runBlocking {
            senderClient.pushHouseholdBundle(
                targetIp = firstSession.ipAddress,
                targetPort = firstSession.port,
                pairingCode = firstSession.pairingCode,
                bundle = incomingBundle
            )
        }
        assertTrue("Expected mobile SPAKE2 receive push to succeed: $firstPush", firstPush.success)
        awaitLoopbackIo { vm.householdSyncState.value.pendingImport != null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("household_import_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_import_cancel_button").performClick()
        awaitLoopbackIo { !vm.householdSyncState.value.hasAnyDialogOpen }
        assertEquals(initialSerials, vm.devices.value.map { it.serialNumber }.toSet())

        // 2. Re-open Receive dialog, receive push again, then confirm Replace All
        composeTestRule.runOnUiThread {
            vm.householdSync.openReceiveDialog(isTv = false, preferredListenPort = 0, discoveryUdpPort = 0)
        }
        awaitLoopbackIo { vm.householdSyncState.value.activeReceiveSession != null }
        val secondSession = vm.householdSyncState.value.activeReceiveSession!!
        val secondPush = runBlocking {
            senderClient.pushHouseholdBundle(
                targetIp = secondSession.ipAddress,
                targetPort = secondSession.port,
                pairingCode = secondSession.pairingCode,
                bundle = incomingBundle
            )
        }
        assertTrue("Expected second mobile SPAKE2 receive push to succeed: $secondPush", secondPush.success)
        awaitLoopbackIo { vm.householdSyncState.value.pendingImport != null }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("household_import_replace_button").performClick()
        awaitLoopbackIo {
            !vm.householdSyncState.value.hasAnyDialogOpen &&
                !vm.householdSyncState.value.isImporting &&
                vm.devices.value.size == 1 &&
                vm.devices.value.single().serialNumber == "WLS040DOCK01"
        }
        assertEquals("wifi_replace_home", vm.activeNetworkKey.value)
        assertEquals("Dock", vm.devices.value.single().roomName)
    }

    @Test
    fun verifySendDialogDisplaysInlineIpAndPortValidationErrors() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]
        composeTestRule.runOnUiThread { vm.householdSync.openSendDialog() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("household_sync_send_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_sync_pin_input").performTextInput("123456")

        // Public IPv4 must show inline error and keep Send disabled
        composeTestRule.onNodeWithTag("household_sync_target_ip_input").performTextInput("8.8.8.8")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("household_sync_ip_validation_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_sync_send_confirm_button").assertIsNotEnabled()

        // Valid LAN IP with invalid port 0 must show port error and keep Send disabled
        composeTestRule.onNodeWithTag("household_sync_target_ip_input").performTextClearance()
        composeTestRule.onNodeWithTag("household_sync_target_ip_input").performTextInput("192.168.1.50")
        composeTestRule.onNodeWithTag("household_sync_target_port_input").performTextClearance()
        composeTestRule.onNodeWithTag("household_sync_target_port_input").performTextInput("0")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("household_sync_port_validation_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_sync_send_confirm_button").assertIsNotEnabled()

        // Valid LAN IP + port enables Send and shows explicit target verification banner
        composeTestRule.onNodeWithTag("household_sync_target_port_input").performTextClearance()
        composeTestRule.onNodeWithTag("household_sync_target_port_input").performTextInput("49280")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("household_sync_target_verification").assertIsDisplayed()
        composeTestRule.onNodeWithTag("household_sync_send_confirm_button").assertIsEnabled()

        composeTestRule.runOnUiThread { vm.householdSync.closeSendDialog() }
        awaitLoopbackIo { !vm.householdSyncState.value.showSendDialog }
        assertFalse(vm.householdSyncState.value.hasAnyDialogOpen)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Config(sdk = [36], qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun verifyAndroidTvSyncOverlayReceivesPhonePushContainsDpadFocusAndAppliesRooms() {
        composeTestRule.waitForIdle()
        val vm = ViewModelProvider(composeTestRule.activity)[SwitchViewModel::class.java]

        // Start empty in Real LAN mode -> initial focus lands on tv_scan_button (index 3)
        composeTestRule.runOnUiThread {
            vm.resetLanHomesSwitchesAndRooms(rediscoverAfterReset = false)
        }
        awaitLoopbackIo {
            !vm.isDemoShowroomMode.value && vm.devices.value.isEmpty() && !vm.isRefreshing.value
        }
        composeTestRule.waitForIdle()

        // Navigate Right from Rescan (index 3) -> Sync (index 4) and press OK
        composeTestRule.onNodeWithTag("tv_scan_button")
            .assertIsDisplayed()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_sync_button").assertIsDisplayed().assertIsFocused()

        // Open TV pairing session (using ephemeral port 0 in Robolectric to avoid port conflicts)
        composeTestRule.runOnUiThread {
            vm.householdSync.openReceiveDialog(isTv = true, preferredListenPort = 0, discoveryUdpPort = 0)
        }
        awaitLoopbackIo { vm.householdSyncState.value.activeReceiveSession != null }
        composeTestRule.waitForIdle()

        val activeSession = vm.householdSyncState.value.activeReceiveSession!!
        composeTestRule.onNodeWithTag("tv_household_sync_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_sync_pairing_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_sync_endpoint").assertIsDisplayed()

        // Simulate a Phone pushing an encrypted household bundle over loopback LAN
        val phoneClient = HouseholdSyncLanClient(localIpProvider = { "127.0.0.1" }, allowLoopbackForTests = true)
        val phoneBundle = HouseholdSyncBundle(
            sourceDeviceLabel = "Family Phone",
            homes = listOf(
                SyncedHomeProfile("wifi_synced_tv", "Main House", "HomeWiFi-5G", "192.168.1", "192.168.1.1")
            ),
            devices = listOf(
                SyncedDeviceProfile(
                    name = "Theater Sconces",
                    roomName = "Media Room",
                    networkKey = "wifi_synced_tv",
                    deviceType = DeviceType.LIGHTBULB_DIMMER.name,
                    ipAddress = "192.168.1.77",
                    port = 49153,
                    serialNumber = "WDS060TVSYNC01",
                    sortOrder = 0
                ),
                SyncedDeviceProfile(
                    name = "Patio Bistro Lights",
                    roomName = "Outdoor",
                    networkKey = "wifi_synced_tv",
                    deviceType = DeviceType.SWITCH_PLUG.name,
                    ipAddress = "192.168.1.78",
                    port = 49153,
                    serialNumber = "WLS040TVSYNC02",
                    sortOrder = 1
                )
            )
        )
        val pushResult = runBlocking {
            phoneClient.pushHouseholdBundle(
                targetIp = activeSession.ipAddress,
                targetPort = activeSession.port,
                pairingCode = activeSession.pairingCode,
                bundle = phoneBundle
            )
        }
        assertTrue("Expected encrypted push to TV to succeed: $pushResult", pushResult.success)

        awaitLoopbackIo { vm.householdSyncState.value.pendingImport != null }
        composeTestRule.waitForIdle()

        // Up/Down D-Pad presses inside the TV modal must stay contained on the focused action button
        composeTestRule.onNodeWithTag("tv_sync_confirm_merge_button")
            .assertIsDisplayed()
            .assertIsFocused()
            .performKeyInput {
                pressKey(Key.DirectionUp)
                pressKey(Key.DirectionDown)
            }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tv_sync_confirm_merge_button")
            .assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }

        awaitLoopbackIo {
            !vm.householdSyncState.value.hasAnyDialogOpen &&
                vm.devices.value.size == 2 &&
                vm.devices.value.any { it.name == "Theater Sconces" && it.roomName == "Media Room" }
        }
        composeTestRule.waitForIdle()

        // Verify synced custom room headers render on the TV 3-column grid
        composeTestRule.onNodeWithTag("tv_device_grid")
            .performScrollToNode(hasTestTag("tv_room_header_Media Room"))
        composeTestRule.onNodeWithTag("tv_room_header_Media Room").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_device_grid")
            .performScrollToNode(hasTestTag("tv_room_header_Outdoor"))
        composeTestRule.onNodeWithTag("tv_room_header_Outdoor").assertIsDisplayed()
    }
}
