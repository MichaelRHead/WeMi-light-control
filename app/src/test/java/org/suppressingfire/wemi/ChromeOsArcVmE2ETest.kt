package org.suppressingfire.wemi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.util.ReflectionHelpers
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.SsdpDiscoveryEngine
import org.suppressingfire.wemi.ui.SwitchViewModel
import org.suppressingfire.wemi.widget.WeMiSwitchWidgetProvider
import java.net.InetAddress

/**
 * Full-stack Chromebook / ChromeOS ARCVM End-to-End tests running [MainActivity] in a desktop
 * window (`w1280dp-h800dp-land-desk-xhdpi`, `org.chromium.arc` feature) across a split-subnet
 * [FakeChromeOsPatchpanelBridge] (`eth1 = 100.115.92.6` CGNAT guest VM -> `192.168.86.0/24`
 * physical home LAN).
 *
 * Structurally different from Phone and Android TV E2E suites (which talk directly to `127.0.0.1`
 * on a flat subnet with active GENA push callbacks):
 * 1. Normal Home Router: `patchpanel` multicast forwarding -> RFC 1918 auto-adoption & home
 *    subnet backfill -> SNAT toggle -> zero GENA `SUBSCRIBE` -> physical wall-paddle flip +
 *    UPnP port shift (`49153 -> 49154`) convergence via SOAP polling.
 * 2. Strict Mesh Router: Belkin multicast blocked + public DNS `8.8.8.8` -> non-Belkin Router
 *    IGD `192.168.86.1:5000` harvested -> real 254-host Stage C sweep discovers switch & new
 *    dimmer -> SNAT dimmer brightness control (`75%`) with zero `100.115.92.*` probes.
 * 3. Total AP Isolation + Public DNS: clean `"Local LAN"` home -> `SsdpScannerModal` manual
 *    ChromeOS `/24` escape hatch rejects `100.115.92`, sweeps `192.168.86`, hides fallback card,
 *    backfills `192.168.86.0/24`, and keeps `AddWifiHomeNetworkDialog` & subnet callbacks clean.
 * 4. Multi-NIC `ShadowConnectivityManager`: verifies `eth1` (`100.115.92.6`) wins over `arc0`
 *    (`100.115.92.2`) and `tun0` VPN while Phone `10.22.22.x` Setup AP priority stays intact.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h800dp-land-desk-xhdpi")
class ChromeOsArcVmE2ETest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetChromeOsTestEnvironment()
        Shadows.shadowOf(context.packageManager).setSystemFeature("org.chromium.arc", true)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEMO_SHOWROOM_MODE, false)
            .commit()
    }

    @After
    fun tearDown() {
        resetChromeOsTestEnvironment()
    }

    private fun resetChromeOsTestEnvironment() {
        WeMiSwitchWidgetProvider.cancelAndJoinPendingWorkForTests()
        NetworkMonitor.resetTestOverrides()
        SsdpDiscoveryEngine.allowUnicastFallbackInRobolectricForTests = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        SwitchDatabase.closeCurrentInstance()
    }

    private fun awaitNode(timeoutMs: Long = 10_000L, check: () -> Unit) {
        composeRule.awaitLoopbackIo(timeoutMs = timeoutMs) {
            runCatching {
                check()
                true
            }.getOrDefault(false)
        }
    }

    private fun newWallSwitchServer(serial: String, ssid: String): FakeUpnpServer = FakeUpnpServer(
        initialFriendlyName = "Office Wall Switch",
        deviceTypeUrn = "urn:Belkin:device:lightswitch:1",
        modelName = "WLS0403",
        serialNumber = serial,
        udn = "uuid:Lightswitch-3_0-$serial",
        wifiSsid = ssid,
        initialRelayOn = false,
        enableSsdpResponder = false
    )

    private fun newDimmerServer(
        serial: String,
        ssid: String,
        initialOn: Boolean = false,
        initialBrightness: Int = 35
    ): FakeUpnpServer = FakeUpnpServer(
        initialFriendlyName = "Studio Desk Dimmer",
        deviceTypeUrn = "urn:Belkin:device:dimmer:1",
        modelName = "WDS060",
        serialNumber = serial,
        udn = "uuid:Dimmer-2_0-$serial",
        wifiSsid = ssid,
        initialRelayOn = initialOn,
        initialBrightness = initialBrightness,
        enableSsdpResponder = false
    )

    @Test
    fun chromebookDesktop_patchpanelMulticastColdStart_controlsOverSnat_skipsGenaAndPollsWallPaddle() {
        newWallSwitchServer("CHROS001WLS", "ChromebookHome-2.4G").use { switchServer ->
            FakeChromeOsPatchpanelBridge(
                guestInterfaceName = "eth1",
                guestIpv4 = "100.115.92.6",
                dnsServersIpv4 = listOf("192.168.86.1")
            ).install().use { bridge ->
                bridge.mapVirtualEndpoint("192.168.86.42", 49153, switchServer.port)
                bridge.registerVirtualSwitchSsdp("192.168.86.42", 49153, switchServer.udn)

                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    // 1. Desktop ChromeOS renders adaptive Main UI (NOT Android TV 10-foot UI)
                    awaitNode { composeRule.onNodeWithTag("devices_list").assertIsDisplayed() }
                    composeRule.onNodeWithTag("tv_dashboard_screen").assertDoesNotExist()

                    // 2. Cold-start discovers 192.168.86.42:49153 via patchpanel SSDP & backfills Home subnet
                    awaitNode {
                        composeRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasText("Office Wall Switch"))
                        composeRule.onNodeWithText("Office Wall Switch").assertIsDisplayed()
                    }
                    awaitNode {
                        composeRule.onNodeWithTag("devices_list")
                            .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
                        composeRule.onNodeWithText("My Home (1)").performScrollTo().assertIsDisplayed()
                        composeRule.onNodeWithText("1/1 Active • 192.168.86.0/24").assertIsDisplayed()
                    }
                    assertTrue(bridge.snatRoutedConnections.contains("192.168.86.42:49153"))
                    assertTrue("Stage C sweep must stay skipped when multicast succeeds", bridge.sweptVirtualHosts.isEmpty())
                    assertTrue("Must never probe 100.115.92.x CGNAT range", bridge.cgnatProbesAttempted.isEmpty())

                    // 3. Toggle switch ON across SNAT and prove GENA SUBSCRIBE was skipped under CGNAT
                    composeRule.onNodeWithTag("devices_list")
                        .performScrollToNode(hasText("Office Wall Switch"))
                    composeRule.onNodeWithContentDescription(
                        "Toggle power for Office Wall Switch, currently Off"
                    ).performClick()
                    composeRule.awaitLoopbackIo { switchServer.relayStateOn.get() }
                    awaitNode {
                        composeRule.onNodeWithContentDescription(
                            "Toggle power for Office Wall Switch, currently On"
                        ).assertIsDisplayed()
                    }
                    assertEquals(0, switchServer.lastGenaCallbackPort.get())

                    // 4. Physical wall-paddle flip (ON -> OFF) + physical UPnP port shift (49153 -> 49154):
                    //    ChromeOS converges via SOAP polling + 9-port healing over SNAT without GENA push
                    var switchRowId = 0
                    scenario.onActivity { activity ->
                        val vm = ViewModelProvider(activity)[SwitchViewModel::class.java]
                        val row = vm.devices.value.first { it.name == "Office Wall Switch" }
                        assertEquals("192.168.86.42", row.ipAddress)
                        assertEquals(49153, row.port)
                        switchRowId = row.id
                    }
                    assertTrue(switchRowId > 0)

                    switchServer.relayStateOn.set(false)
                    bridge.shiftVirtualEndpointPort("192.168.86.42", 49153, 49154, switchServer.port)

                    composeRule.onNodeWithText("Office Wall Switch").performClick()
                    awaitNode {
                        composeRule.onNodeWithTag("ping_device_$switchRowId").performScrollTo().assertIsDisplayed()
                    }
                    composeRule.onNodeWithTag("ping_device_$switchRowId").performClick()

                    awaitNode {
                        composeRule.onNodeWithContentDescription(
                            "Toggle power for Office Wall Switch, currently Off"
                        ).assertIsDisplayed()
                    }
                    assertTrue(bridge.snatRoutedConnections.contains("192.168.86.42:49154"))
                    assertEquals(0, switchServer.lastGenaCallbackPort.get())
                    assertTrue(bridge.cgnatProbesAttempted.isEmpty())
                }
            }
        }
    }

    @Test
    fun chromebookStrictMeshRouter_harvestsRouterIgdAndSweepsPhysicalSubnet_discoversAndDimsOverSnat() {
        newWallSwitchServer("CHROS101WLS", "MeshHome-2.4G").use { switchServer ->
            newDimmerServer("CHROS102DIM", "MeshHome-2.4G", initialOn = false, initialBrightness = 35).use { dimmerServer ->
                // Public DNS 8.8.8.8 gives no LAN hint; Belkin multicast is blocked; only the
                // non-Belkin Router IGD answers SSDP at 192.168.86.1:5000/rootDesc.xml.
                FakeChromeOsPatchpanelBridge(
                    guestInterfaceName = "eth1",
                    guestIpv4 = "100.115.92.6",
                    dnsServersIpv4 = listOf("8.8.8.8")
                ).install().use { bridge ->
                    bridge.registerVirtualRouterIgd("192.168.86.1", 5000, "Google Nest Wifi")
                    bridge.mapVirtualEndpoint("192.168.86.42", 49153, switchServer.port)

                    ActivityScenario.launch(MainActivity::class.java).use {
                        // 1. Cold-start harvests 192.168.86.1 from Router IGD, rejects router, sweeps /24
                        awaitNode {
                            composeRule.onNodeWithTag("devices_list")
                                .performScrollToNode(hasText("Office Wall Switch"))
                            composeRule.onNodeWithText("Office Wall Switch").assertIsDisplayed()
                        }
                        assertTrue("Expected Stage A to fetch Router IGD XML", bridge.routerIgdXmlFetches.get() > 0)
                        assertTrue("Expected real Stage C /24 sweep", bridge.sweptVirtualHosts.size >= 200)
                        assertTrue(bridge.cgnatProbesAttempted.isEmpty())
                        composeRule.onNodeWithText("Google Nest Wifi Router").assertDoesNotExist()

                        // 2. Dimmer comes online (multicast-blocked) -> Rescan via TopHeader ->
                        //    Stage C sweeps 192.168.86.0/24 and finds Studio Desk Dimmer
                        bridge.mapVirtualEndpoint("192.168.86.43", 49153, dimmerServer.port)
                        composeRule.onNodeWithTag("refresh_devices_button").performClick()

                        awaitNode {
                            composeRule.onNodeWithText("Local Interface: 100.115.92.6 (ChromeOS VM)").assertIsDisplayed()
                            composeRule.onNodeWithText("Unicast Subnet Sweep: 192.168.86.0/24").assertIsDisplayed()
                            composeRule.onNodeWithTag("ssdp_discovered_list")
                                .performScrollToNode(hasTestTag("ssdp_item_Studio Desk Dimmer"))
                        }
                        composeRule.onNodeWithTag("arcvm_manual_subnet_card").assertDoesNotExist()
                        composeRule.onNodeWithText("Google Nest Wifi Router").assertDoesNotExist()

                        composeRule.onNodeWithTag("adopt_ssdp_device_Studio Desk Dimmer")
                            .performScrollTo()
                            .performClick()
                        awaitNode {
                            assertTrue(
                                composeRule.onAllNodes(
                                    hasTestTag("adopt_ssdp_device_Studio Desk Dimmer") and hasText("Adopted")
                                ).fetchSemanticsNodes().isNotEmpty()
                            )
                        }
                        composeRule.onNodeWithTag("close_ssdp_scanner_button").performScrollTo().performClick()

                        // 3. Control Studio Desk Dimmer brightness (75%) across SNAT
                        awaitNode {
                            composeRule.onNodeWithTag("devices_list")
                                .performScrollToNode(hasText("Studio Desk Dimmer"))
                            composeRule.onNodeWithText("Studio Desk Dimmer").assertIsDisplayed()
                        }
                        composeRule.onNodeWithText("Studio Desk Dimmer").performClick()
                        awaitNode { composeRule.onNodeWithText("75%").performScrollTo().assertIsDisplayed() }
                        composeRule.onNodeWithText("75%").performClick()
                        composeRule.awaitLoopbackIo {
                            dimmerServer.relayStateOn.get() && dimmerServer.dimmerBrightness.get() == 75
                        }
                        awaitNode {
                            composeRule.onNodeWithText("ON • 75%", useUnmergedTree = true).assertIsDisplayed()
                        }
                        assertEquals(0, dimmerServer.lastGenaCallbackPort.get())
                        assertTrue(bridge.cgnatProbesAttempted.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    fun chromebookIsolatedApPublicDns_manualSubnetEscapeHatchAndCgnatHomeHygiene() {
        newWallSwitchServer("CHROS201WLS", "IsolatedMesh-2.4G").use { switchServer ->
            newDimmerServer("CHROS202DIM", "IsolatedMesh-2.4G", initialOn = true, initialBrightness = 50).use { dimmerServer ->
                // Total AP isolation (zero multicast responders) + public DNS 8.8.8.8
                FakeChromeOsPatchpanelBridge(
                    guestInterfaceName = "eth1",
                    guestIpv4 = "100.115.92.6",
                    dnsServersIpv4 = listOf("8.8.8.8")
                ).install().use { bridge ->
                    bridge.mapVirtualEndpoint("192.168.86.42", 49153, switchServer.port)
                    bridge.mapVirtualEndpoint("192.168.86.43", 49153, dimmerServer.port)

                    ActivityScenario.launch(MainActivity::class.java).use {
                        // 1. Fresh Chromebook starts cleanly with "0/0 Active • Local LAN" (no 100.115.92)
                        awaitNode {
                            composeRule.onNodeWithTag("empty_state_scan_ssdp_button").assertIsDisplayed()
                            composeRule.onNodeWithText("0/0 Active • Local LAN").assertIsDisplayed()
                        }
                        assertTrue(bridge.cgnatProbesAttempted.isEmpty())

                        // 2. Open SSDP Scanner -> ChromeOS manual /24 escape hatch appears
                        composeRule.onNodeWithTag("empty_state_scan_ssdp_button").performClick()
                        awaitNode {
                            composeRule.onNodeWithTag("ssdp_discovered_list")
                                .performScrollToNode(hasTestTag("arcvm_manual_subnet_card"))
                            composeRule.onNodeWithText("ChromeOS Virtual Network Detected (100.115.92.6)")
                                .assertIsDisplayed()
                        }

                        // 3. Rejects CGNAT 100.115.92, accepts RFC 1918 192.168.86 -> sweeps & auto-adopts
                        composeRule.onNodeWithTag("ssdp_manual_subnet_input").performTextInput("100.115.92")
                        composeRule.onNodeWithTag("ssdp_scan_manual_subnet_button").assertIsNotEnabled()

                        composeRule.onNodeWithTag("ssdp_manual_subnet_input").performTextClearance()
                        composeRule.onNodeWithTag("ssdp_manual_subnet_input").performTextInput("192.168.86")
                        composeRule.onNodeWithTag("ssdp_scan_manual_subnet_button")
                            .assertIsEnabled()
                            .performClick()

                        awaitNode {
                            composeRule.onNodeWithText("Unicast Subnet Sweep: 192.168.86.0/24").assertIsDisplayed()
                            composeRule.onNodeWithTag("ssdp_discovered_list")
                                .performScrollToNode(hasTestTag("ssdp_item_Office Wall Switch"))
                        }
                        composeRule.onNodeWithTag("arcvm_manual_subnet_card").assertDoesNotExist()
                        composeRule.onNodeWithTag("close_ssdp_scanner_button").performScrollTo().performClick()

                        // 4. WifiHomeBar backfills 192.168.86.0/24 & AddWifiHomeNetworkDialog omits 100.115.92
                        awaitNode {
                            composeRule.onNodeWithTag("devices_list")
                                .performScrollToNode(hasTestTag("wifi_home_selector_bar"))
                            composeRule.onNodeWithText("My Home (2)").performScrollTo().assertIsDisplayed()
                            composeRule.onNodeWithText("2/2 Active • 192.168.86.0/24").assertIsDisplayed()
                        }
                        composeRule.onNodeWithTag("add_wifi_home_button").performScrollTo().performClick()
                        awaitNode { composeRule.onNodeWithTag("wifi_home_subnet_input").assertIsDisplayed() }
                        val prefilledSubnet = composeRule.onNodeWithTag("wifi_home_subnet_input")
                            .fetchSemanticsNode()
                            .config[SemanticsProperties.EditableText]
                            .text
                        assertEquals("Add Home dialog must not pre-fill CGNAT 100.115.92", "", prefilledSubnet)
                        composeRule.onNodeWithText("Cancel").performClick()

                        // 5. CGNAT subnet callback never creates a bogus LAN 100.115.92.x home
                        val dao = SwitchDatabase.getDatabase(context).switchDao()
                        val repo = SwitchRepository(dao = dao, context = context, initialDemoModeOverride = false)
                        try {
                            runBlocking {
                                assertNull(repo.resolveOrCreateHomeForSubnet("100.115.92", "100.115.92.1"))
                                val homes = dao.getAllHomeNetworks().first()
                                assertEquals(1, homes.size)
                                assertEquals("192.168.86", homes.single().subnetPrefix)
                            }
                        } finally {
                            repo.close()
                        }
                        assertTrue(bridge.cgnatProbesAttempted.isEmpty())
                    }
                }
            }
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun chromebookMultiNicConnectivityManager_selectsEth1OverArc0AndVpn_preservesPhoneSetupApPriority() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)
        shadowCm.clearAllNetworks()

        val arc0Net = registerShadowNetwork(
            shadowCm, 101, NetworkCapabilities.TRANSPORT_ETHERNET, ConnectivityManager.TYPE_DUMMY,
            "arc0", "100.115.92.2", emptyList()
        )
        val vpnNet = registerShadowNetwork(
            shadowCm, 102, NetworkCapabilities.TRANSPORT_VPN, ConnectivityManager.TYPE_VPN,
            "tun0", "10.8.0.2", listOf("8.8.8.8"), makeActive = true
        )
        val eth1Net = registerShadowNetwork(
            shadowCm, 103, NetworkCapabilities.TRANSPORT_WIFI, ConnectivityManager.TYPE_WIFI,
            "eth1", "100.115.92.6", listOf("192.168.86.1")
        )

        // Even when VPN is active and arc0 is present, NetworkMonitor selects shill-backed eth1
        val selected = NetworkMonitor.findPreferredWifiOrLanNetwork(cm)
        assertEquals(eth1Net, selected)
        assertTrue(selected != arc0Net && selected != vpnNet)

        val binding = NetworkMonitor.resolveActiveLanBindingFromManager(cm)
        assertNotNull(binding)
        assertEquals("eth1", binding?.interfaceName)
        assertEquals("100.115.92.6", binding?.localIpv4)
        assertEquals(listOf("192.168.86.1"), binding?.dnsServersIpv4)

        // Active Ethernet uplink overrides non-active Wi-Fi when switched on Chromebook dock
        val eth2Net = registerShadowNetwork(
            shadowCm, ConnectivityManager.TYPE_ETHERNET, NetworkCapabilities.TRANSPORT_ETHERNET, ConnectivityManager.TYPE_ETHERNET,
            "eth2", "100.115.92.10", listOf("192.168.86.1"), makeActive = true
        )
        assertEquals(eth2Net, cm.activeNetwork)
        assertEquals(eth2Net, NetworkMonitor.findPreferredWifiOrLanNetwork(cm))

        // Non-regression for Phone 10.22.22.x Setup AP: Setup AP Wi-Fi still takes highest priority
        val setupApNet = registerShadowNetwork(
            shadowCm, 104, NetworkCapabilities.TRANSPORT_WIFI, ConnectivityManager.TYPE_WIFI,
            "wlan0", "10.22.22.10", listOf("10.22.22.1")
        )
        assertEquals(setupApNet, NetworkMonitor.findPreferredWifiOrLanNetwork(cm))

        // When only arc0 remains, findPreferredWifiOrLanNetwork returns null (never leaks stale cache)
        shadowCm.clearAllNetworks()
        shadowCm.setActiveNetworkInfo(null)
        registerShadowNetwork(
            shadowCm, ConnectivityManager.TYPE_DUMMY, NetworkCapabilities.TRANSPORT_ETHERNET, ConnectivityManager.TYPE_DUMMY,
            "arc0", "100.115.92.2", emptyList(), makeActive = true
        )
        assertNull(NetworkMonitor.findPreferredWifiOrLanNetwork(cm))
    }

    @Suppress("DEPRECATION")
    private fun registerShadowNetwork(
        shadowCm: ShadowConnectivityManager,
        netId: Int,
        transport: Int,
        legacyType: Int,
        interfaceName: String,
        ipv4: String,
        dnsServers: List<String>,
        makeActive: Boolean = false
    ): Network {
        val network = ShadowNetwork.newInstance(netId)
        val info = ShadowNetworkInfo.newInstance(
            android.net.NetworkInfo.DetailedState.CONNECTED,
            legacyType,
            0,
            true,
            android.net.NetworkInfo.State.CONNECTED
        )
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addTransportType(transport)
        val linkProps = LinkProperties().apply {
            this.interfaceName = interfaceName
            val linkAddr = ReflectionHelpers.callConstructor(
                LinkAddress::class.java,
                ReflectionHelpers.ClassParameter.from(InetAddress::class.java, InetAddress.getByName(ipv4)),
                ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType, 24)
            )
            ReflectionHelpers.callInstanceMethod<Boolean>(
                this,
                "addLinkAddress",
                ReflectionHelpers.ClassParameter.from(LinkAddress::class.java, linkAddr)
            )
            setDnsServers(dnsServers.map { InetAddress.getByName(it) })
        }
        shadowCm.addNetwork(network, info)
        shadowCm.setNetworkCapabilities(network, caps)
        shadowCm.setLinkProperties(network, linkProps)
        if (makeActive) {
            shadowCm.setActiveNetworkInfo(info)
        }
        return network
    }

    companion object {
        private const val PREFS_NAME = "wemi_controller_prefs"
        private const val KEY_DEMO_SHOWROOM_MODE = "demo_showroom_mode"
    }
}
