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
import kotlinx.coroutines.flow.first
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
            assertTrue(fullMoreExport.contains("[TARGET AP] \"HeadFamily-2.4G\" found in ApList"))
            assertTrue(fullMoreExport.contains("[STEP 6 CLOSE] CloseSetup -> success"))
            assertTrue(fullMoreExport.contains("[HOME] key="))
            assertTrue(fullMoreExport.contains("Stairway 3-Way Switch"))
            assertFalse(fullMoreExport.contains("HomeRouterSecret99"))
        }
    }

    @Test
    fun `verify Unassigned room dimmer fan-out, HomeNetworkEntity sortOrder preservation, and GENA superseded SID eviction`() =
        kotlinx.coroutines.runBlocking {
            val db = androidx.room.Room.inMemoryDatabaseBuilder(
                composeTestRule.activity,
                org.suppressingfire.wemi.data.SwitchDatabase::class.java
            ).allowMainThreadQueries().build()
            FakeUpnpServer(
                initialFriendlyName = "Smart Dimmer",
                deviceTypeUrn = "urn:Belkin:device:dimmer:1",
                modelName = "WDS060",
                serialNumber = "WDS060UNASSIGNED1",
                udn = "uuid:Dimmer-2_0-WDS060UNASSIGNED1",
                isRtos = true,
                initialRelayOn = false,
                initialBrightness = 40,
                registerAsCandidatePort = true
            ).use { server ->
                val client = org.suppressingfire.wemi.network.NetworkClient(extraCandidatePorts = listOf(server.port))
                val repo = org.suppressingfire.wemi.data.SwitchRepository(
                    dao = db.switchDao(),
                    networkClient = client,
                    initialDemoModeOverride = false
                )
                try {
                    val devId = db.switchDao().insertDevice(
                        org.suppressingfire.wemi.data.DeviceEntity(
                            name = "Smart Dimmer",
                            roomName = "",
                            deviceType = org.suppressingfire.wemi.data.DeviceType.LIGHTBULB_DIMMER.name,
                            ipAddress = "127.0.0.1",
                            port = server.port,
                            isOn = false,
                            brightness = 40,
                            serialNumber = "WDS060UNASSIGNED1"
                        )
                    ).toInt()
                    assertEquals(1, repo.toggleRoomPower("Unassigned", targetOn = true))
                    assertTrue(server.relayStateOn.get())
                    assertEquals(1, repo.updateRoomBrightness("Unassigned", newBrightness = 75))
                    assertEquals(75, server.dimmerBrightness.get())

                    db.switchDao().insertHomeNetwork(org.suppressingfire.wemi.data.HomeNetworkEntity("h0", "H0", "S0", "192.168.10", sortOrder = 0))
                    db.switchDao().insertHomeNetwork(org.suppressingfire.wemi.data.HomeNetworkEntity("h1", "H1", "S1", "192.168.11", autoReconnectOnArrival = false, sortOrder = 1))
                    db.switchDao().insertHomeNetwork(org.suppressingfire.wemi.data.HomeNetworkEntity("h2", "H2", "S2", "192.168.12", sortOrder = 2))
                    db.switchDao().deleteHomeNetworkByKey("h0")
                    val updatedHome = repo.saveOrUpdateHomeNetwork("h1", "H1 Renamed", "S1-2G", "192.168.11")
                    assertEquals(1, updatedHome.sortOrder)
                    assertFalse(updatedHome.autoReconnectOnArrival)
                    val insertedHome = repo.saveOrUpdateHomeNetwork(null, "H3", "S3", "192.168.13")
                    assertEquals(3, insertedHome.sortOrder)
                    val autoSubnetHome = repo.resolveOrCreateHomeForSubnet("192.168.86")!!
                    assertEquals(4, autoSubnetHome.sortOrder)

                    val stored = db.switchDao().getDeviceById(devId)!!
                    org.junit.Assert.assertNotNull(repo.genaManager.subscribeOrRenew(stored))
                    assertEquals(1, repo.genaManager.activeSidCount)
                    org.junit.Assert.assertNotNull(repo.genaManager.subscribeOrRenew(stored))
                    assertEquals(1, repo.genaManager.activeSidCount)
                    server.simulate412OnNextGenaRenew.set(true)
                    org.junit.Assert.assertNotNull(repo.genaManager.subscribeOrRenew(stored))
                    assertEquals(1, repo.genaManager.activeSidCount)

                    // Real-LAN provisioning with placeholder setup-AP serial must keep serialNumber/udn empty so Home Wi-Fi SSDP adoption merges without duplicating rows
                    FakeUpnpServer(
                        initialFriendlyName = "Foyer Rocker Switch",
                        modelName = "WLS040",
                        serialNumber = "UNKNOWN",
                        udn = "uuid:Lightswitch-2_0-UNKNOWN",
                        isRtos = true
                    ).use { setupAp ->
                        val provRes = repo.provisionSetupApDevice(
                            apHost = "127.0.0.1",
                            apPort = setupAp.port,
                            ssid = "S3",
                            plainPassword = "HomeRouterPassword88",
                            activeNetworkKey = insertedHome.networkKey
                        )
                        assertTrue(provRes.success)
                        val provisioned = db.switchDao().getAllDevices().first()
                            .single { it.name == "Foyer Rocker Switch" }
                        assertEquals("", provisioned.serialNumber)
                        assertEquals("", provisioned.udn)

                        val merged = repo.adoptDiscoveredDevice(
                            discovered = org.suppressingfire.wemi.data.SsdpDiscoveredDevice(
                                name = "Foyer Rocker Switch",
                                roomName = "Hallway & Landing",
                                ipAddress = "192.168.13.144",
                                port = 49153,
                                deviceType = org.suppressingfire.wemi.data.DeviceType.SWITCH_PLUG,
                                modelName = "WLS040",
                                serialNumber = "221940KREAL888",
                                firmwareVersion = "WeMo_WW_2.00.12000.PVT",
                                usn = "uuid:Lightswitch-2_0-221940KREAL888::urn:Belkin:service:basicevent:1",
                                searchTarget = org.suppressingfire.wemi.network.XmlAndSkuParser.URN_BASIC_EVENT,
                                locationUrl = "http://192.168.13.144:49153/setup.xml",
                                responseTimeMs = 9L,
                                isLiveUdpResponse = true
                            ),
                            activeNetworkKey = insertedHome.networkKey,
                            activeWifiSsid = "S3"
                        )
                        assertEquals(provisioned.id, merged.id)
                        assertEquals("221940KREAL888", merged.serialNumber)
                        assertEquals("192.168.13.144", merged.ipAddress)
                        assertEquals(
                            1,
                            db.switchDao().getAllDevices().first().count { it.networkKey == insertedHome.networkKey }
                        )
                    }
                } finally {
                    repo.close()
                    db.close()
                }
            }
        }
}

