package org.suppressingfire.wemi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.HomeProfileSanitizer
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.SwitchDatabase
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.LanSubnetHintResolver
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.SsdpDiscoveryEngine
import org.suppressingfire.wemi.network.SsdpScanReport
import org.suppressingfire.wemi.network.XmlAndSkuParser
import org.suppressingfire.wemi.ui.components.AddWifiHomeNetworkDialog
import org.suppressingfire.wemi.ui.components.SsdpScannerModal
import org.suppressingfire.wemi.ui.theme.WeMiTheme

/**
 * Hermetic unit, integration, loopback-SSDP, and Compose UI tests for ChromeOS VM (ARCVM)
 * local-LAN discovery, physical-subnet Stage C fallback, Home profile hygiene, and GENA safety,
 * while verifying zero regression on Android Phone and Android TV discovery behavior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChromeOsArcVmDiscoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun arcVmAndIpv4ClassifiersSeparateCgnatFromRfc1918AndSetupAp() {
        assertTrue(LanRuntimeUtils.isCarrierGradeNatIpv4("100.64.0.1"))
        assertTrue(LanRuntimeUtils.isCarrierGradeNatIpv4("100.115.92.2"))
        assertTrue(LanRuntimeUtils.isCarrierGradeNatIpv4("100.115.92.6"))
        assertTrue(LanRuntimeUtils.isCarrierGradeNatIpv4("100.115.92"))
        assertTrue(LanRuntimeUtils.isCarrierGradeNatIpv4("100.127.255.254"))

        assertFalse(LanRuntimeUtils.isCarrierGradeNatIpv4("100.63.255.254"))
        assertFalse(LanRuntimeUtils.isCarrierGradeNatIpv4("100.128.0.1"))
        assertFalse(LanRuntimeUtils.isCarrierGradeNatIpv4("192.168.1.50"))
        assertFalse(LanRuntimeUtils.isCarrierGradeNatIpv4("10.0.0.5"))
        assertFalse(LanRuntimeUtils.isCarrierGradeNatIpv4("172.16.0.1"))
        assertFalse(LanRuntimeUtils.isCarrierGradeNatIpv4("127.0.0.1"))

        assertTrue(LanRuntimeUtils.isArcVmManagementIpv4("100.115.92.1"))
        assertTrue(LanRuntimeUtils.isArcVmManagementIpv4("100.115.92.2"))
        assertFalse(LanRuntimeUtils.isArcVmManagementIpv4("100.115.92.6"))

        assertTrue(LanRuntimeUtils.isPrivateRfc1918Ipv4("192.168.86.42"))
        assertTrue(LanRuntimeUtils.isPrivateRfc1918Ipv4("192.168.86"))
        assertTrue(LanRuntimeUtils.isPrivateRfc1918Ipv4("172.16.0.1"))
        assertTrue(LanRuntimeUtils.isPrivateRfc1918Ipv4("172.31.255.1"))
        assertTrue(LanRuntimeUtils.isPrivateRfc1918Ipv4("10.1.2.3"))

        assertFalse(LanRuntimeUtils.isPrivateRfc1918Ipv4("10.0.2.15"))
        assertFalse(LanRuntimeUtils.isPrivateRfc1918Ipv4("10.22.22.1"))
        assertFalse(LanRuntimeUtils.isPrivateRfc1918Ipv4("100.115.92.6"))
        assertFalse(LanRuntimeUtils.isPrivateRfc1918Ipv4("172.32.0.1"))
        assertFalse(LanRuntimeUtils.isPrivateRfc1918Ipv4("8.8.8.8"))
        assertFalse(LanRuntimeUtils.isPrivateRfc1918Ipv4("127.0.0.1"))

        assertEquals("192.168.86", LanRuntimeUtils.normalizeSubnetPrefixOrNull("192.168.86.0/24"))
        assertEquals("192.168.86", LanRuntimeUtils.normalizeSubnetPrefixOrNull("192.168.86.x"))
        assertEquals("192.168.86", LanRuntimeUtils.normalizeSubnetPrefixOrNull("192.168.86.42"))
        assertEquals("192.168.86", LanRuntimeUtils.normalizeSubnetPrefixOrNull(" 192.168.86 "))
        assertNull(LanRuntimeUtils.normalizeSubnetPrefixOrNull("999.1.1"))
        assertNull(LanRuntimeUtils.normalizeSubnetPrefixOrNull("not-an-ip"))

        assertTrue(
            LanRuntimeUtils.isArcVmRuntime(
                hasArcSystemFeature = true,
                buildDevice = "pixel",
                buildProduct = "pixel",
                buildBrand = "google",
                localIpv4 = "192.168.1.50"
            )
        )
        assertTrue(
            LanRuntimeUtils.isArcVmRuntime(
                hasArcSystemFeature = false,
                buildDevice = "pixel",
                buildProduct = "pixel",
                buildBrand = "google",
                localIpv4 = "100.115.92.6"
            )
        )
        assertTrue(
            LanRuntimeUtils.isArcVmRuntime(
                hasArcSystemFeature = false,
                buildDevice = "cheets_x86_64",
                buildProduct = "bertha",
                buildBrand = "chromium",
                localIpv4 = "192.168.1.50",
                isRobolectric = false
            )
        )
        assertFalse(
            LanRuntimeUtils.isArcVmRuntime(
                hasArcSystemFeature = false,
                buildDevice = "panther",
                buildProduct = "panther",
                buildBrand = "google",
                localIpv4 = "192.168.1.50",
                isRobolectric = false
            )
        )
    }

    @Test
    fun subnetHintResolverPrioritizesPhysicalLanOnArcVmAndPreservesDirectLanOnPhoneAndTv() = runBlocking {
        assertEquals("192.168.86.1", LanSubnetHintResolver.extractRfc1918HostIp("http://192.168.86.1:5000/rootDesc.xml"))
        assertNull(LanSubnetHintResolver.extractRfc1918HostIp("http://100.115.92.6:49153/setup.xml"))
        assertNull(LanSubnetHintResolver.extractRfc1918HostIp("http://10.22.22.1:49152/setup.xml"))
        assertNull(LanSubnetHintResolver.extractRfc1918HostIp("http://127.0.0.1:49153/setup.xml"))

        // Phone / Android TV invariant: direct live RFC 1918 interface takes exclusive automatic priority
        val phoneCandidates = LanSubnetHintResolver.resolveCandidateSubnets(
            localIp = "192.168.86.25",
            isArcVm = false,
            activeHomeSubnetPrefix = "192.168.1",
            rememberedDeviceIps = listOf("192.168.1.40"),
            observedSsdpPeerIps = listOf("10.0.0.1"),
            dnsServerIps = listOf("192.168.1.1")
        )
        assertEquals(listOf("192.168.86"), phoneCandidates)

        // ChromeOS ARCVM 5-tier resolution: manual > active home > remembered frequency > SSDP peer > RFC1918 DNS
        val arcManualFirst = LanSubnetHintResolver.resolveCandidateSubnets(
            localIp = "100.115.92.6",
            isArcVm = true,
            manualSubnetOverride = "10.10.20.0/24",
            activeHomeSubnetPrefix = "192.168.86",
            rememberedDeviceIps = listOf("192.168.50.10", "192.168.50.11", "192.168.1.20"),
            observedSsdpPeerIps = listOf("172.16.5.1"),
            dnsServerIps = listOf("8.8.8.8", "100.115.92.5", "192.168.4.1"),
            maxCandidates = 5
        )
        assertEquals(
            listOf("10.10.20", "192.168.86", "192.168.50", "192.168.1", "172.16.5"),
            arcManualFirst
        )
        assertFalse(arcManualFirst.any { LanRuntimeUtils.isCarrierGradeNatIpv4(it) })

        // Fresh Chromebook install with non-Belkin router SSDP LOCATION + private DNS fallback
        val arcPeerAndDns = LanSubnetHintResolver.resolveCandidateSubnets(
            localIp = "100.115.92.6",
            isArcVm = true,
            observedSsdpPeerIps = listOf("192.168.86.1"),
            dnsServerIps = listOf("100.115.92.5", "8.8.8.8", "192.168.1.1"),
            maxCandidates = 2
        )
        assertEquals(listOf("192.168.86", "192.168.1"), arcPeerAndDns)

        // Stage C fallback policy honors explicit manual subnet override outside fast/demo modes
        val sampleUdpHit = SsdpDiscoveredDevice(
            name = "Porch Light", roomName = "Outdoor", ipAddress = "192.168.86.42", port = 49153,
            deviceType = DeviceType.SWITCH_PLUG, modelName = "Lightswitch-1_0", serialNumber = "SN12345",
            firmwareVersion = "2.0", usn = "uuid:Lightswitch-1_0-SN12345", searchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
            locationUrl = "http://192.168.86.42:49153/setup.xml", responseTimeMs = 10L, isOn = true,
            isLiveUdpResponse = true
        )
        assertTrue(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                liveDiscovered = listOf(sampleUdpHit),
                knownLocalDevices = emptyList(),
                allowUnicastSubnetFallback = true,
                fastMode = false,
                includeDemoFallbacks = false,
                onlyWhenKnownMissing = false,
                hasManualSubnetOverride = true
            )
        )
        assertFalse(
            SsdpDiscoveryEngine.shouldRunUnicastSubnetFallback(
                liveDiscovered = listOf(sampleUdpHit),
                knownLocalDevices = emptyList(),
                allowUnicastSubnetFallback = true,
                fastMode = false,
                includeDemoFallbacks = false,
                onlyWhenKnownMissing = false,
                hasManualSubnetOverride = false
            )
        )

        // scanSubnetUnicast must immediately refuse CGNAT 100.115.92 sweeps
        val client = NetworkClient()
        assertTrue(client.scanSubnetUnicast("100.115.92").isEmpty())
    }

    @Test
    fun homeProfileSanitizerBlocksCgnatPhantomHomesRepairsPollutedHomesAndBackfillsPhysicalSubnet() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SwitchDatabase::class.java
        ).allowMainThreadQueries().build()
        val repo = SwitchRepository(
            dao = db.switchDao(),
            networkClient = NetworkClient(),
            initialDemoModeOverride = false
        )
        val sanitizer = HomeProfileSanitizer(
            dao = db.switchDao(),
            isDemoShowroomMode = { false },
            nowFormatted = { "6:00:00 PM" }
        )
        try {
            // 1. NetworkCallback for ChromeOS 100.115.92 must never create or switch to a phantom Home
            assertNull(repo.resolveOrCreateHomeForSubnet("100.115.92", "100.115.92.5"))
            assertTrue(db.switchDao().getAllHomeNetworks().first().isEmpty())

            // 2. Fresh Chromebook launch creates clean My Home with unset physical subnetPrefix
            val initialKey = sanitizer.sanitizeAndResolveInitialLanHome("100.115.92.6")
            assertEquals("wifi_lan_chromeos_default", initialKey)
            val createdHome = db.switchDao().getAllHomeNetworks().first().single()
            assertEquals("My Home", createdHome.homeName)
            assertEquals("Local LAN", createdHome.ssidLabel)
            assertEquals("", createdHome.subnetPrefix)

            // 3. Adopting a discovered switch on 192.168.86.42 automatically backfills physical /24 subnet
            repo.adoptDiscoveredDevice(
                discovered = SsdpDiscoveredDevice(
                    name = "Kitchen Ceiling", roomName = "Kitchen", ipAddress = "192.168.86.42", port = 49153,
                    deviceType = DeviceType.SWITCH_PLUG, modelName = "Lightswitch-1_0", serialNumber = "SN8642",
                    firmwareVersion = "2.0", usn = "uuid:Lightswitch-1_0-SN8642",
                    searchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
                    locationUrl = "http://192.168.86.42:49153/setup.xml",
                    responseTimeMs = 12L, isOn = true, wifiSsid = "Home-WiFi"
                ),
                activeNetworkKey = initialKey,
                activeWifiSsid = createdHome.ssidLabel
            )
            val healedHome = db.switchDao().getAllHomeNetworks().first().single { it.networkKey == initialKey }
            assertEquals("192.168.86", healedHome.subnetPrefix)
            assertEquals("192.168.86.1", healedHome.gatewayIp)

            // 4. Phone / TV RFC 1918 subnet matching still works normally
            val secondHome = repo.resolveOrCreateHomeForSubnet("192.168.99", "192.168.99.1")
            assertNotNull(secondHome)
            assertEquals("192.168.99", secondHome!!.subnetPrefix)

            // 5. Startup sanitization repairs a legacy polluted 100.115.92 home from its saved device IP
            db.switchDao().insertHomeNetwork(
                HomeNetworkEntity(
                    networkKey = "wifi_lan_100_115_92",
                    homeName = "Legacy ChromeOS",
                    ssidLabel = "LAN 100.115.92.0/24",
                    subnetPrefix = "100.115.92",
                    gatewayIp = "100.115.92.5"
                )
            )
            db.switchDao().insertDevice(
                DeviceEntity(
                    name = "Office Lamp",
                    roomName = "Office",
                    networkKey = "wifi_lan_100_115_92",
                    ipAddress = "192.168.50.12",
                    port = 49153,
                    serialNumber = "SN5012"
                )
            )
            sanitizer.sanitizeAndResolveInitialLanHome("100.115.92.6")
            val repairedLegacy = db.switchDao().getAllHomeNetworks().first()
                .single { it.networkKey == "wifi_lan_100_115_92" }
            assertEquals("192.168.50", repairedLegacy.subnetPrefix)
            assertEquals("192.168.50.1", repairedLegacy.gatewayIp)
            assertEquals("LAN 192.168.50.0/24", repairedLegacy.ssidLabel)
        } finally {
            repo.close()
            db.close()
        }
    }

    @Test
    fun genaSkipsUnroutableCgnatCallbackButPreservesLoopbackAndRfc1918Subscriptions() = runBlocking {
        FakeUpnpServer(initialFriendlyName = "Porch Switch", serialNumber = "SNPORCH99").use { server ->
            val arcGena = GenaSubscriptionManager(
                localIpProvider = { "100.115.92.6" },
                preferredListenPort = 0,
                onNotification = {}
            )
            val lanGena = GenaSubscriptionManager(
                localIpProvider = { "192.168.86.25" },
                preferredListenPort = 0,
                onNotification = {}
            )
            try {
                // Non-loopback physical LAN switch under ARCVM CGNAT IP must skip SUBSCRIBE and port binding
                val remoteDevice = DeviceEntity(
                    id = 1, name = "Porch Switch", ipAddress = "192.168.86.42", port = 49153,
                    serialNumber = "SNPORCH99"
                )
                val skipped = arcGena.subscribeOrRenew(remoteDevice)
                assertNull(skipped)
                assertEquals(-1, arcGena.activeListenPort)

                // Loopback test device remains exempted for hermetic E2E testing
                val loopbackDevice = DeviceEntity(
                    id = 2, name = "Porch Switch", ipAddress = "127.0.0.1", port = server.port,
                    serialNumber = "SNPORCH99"
                )
                assertNotNull(arcGena.subscribeOrRenew(loopbackDevice))
                assertTrue(arcGena.activeListenPort > 0)

                // Standard RFC 1918 Phone / TV local IP still subscribes cleanly
                assertNotNull(lanGena.subscribeOrRenew(loopbackDevice))
                assertTrue(lanGena.activeListenPort > 0)
            } finally {
                arcGena.close()
                lanGena.close()
            }
        }
    }

    @Test
    fun loopbackSsdpDiscoveryOnArcVmFindsSwitchAndMarksArcVmReport() = runBlocking {
        FakeUpnpServer(
            initialFriendlyName = "ChromeOS Test Switch",
            serialNumber = "SNCHROME01",
            enableSsdpResponder = true
        ).use { server ->
            val client = NetworkClient(extraCandidatePorts = listOf(server.port))
            val engine = SsdpDiscoveryEngine(
                appContext = null,
                resolveLocalIpv4Address = { "100.115.92.6" },
                probeWemoPort = { ip, port, id -> client.probeWemoPort(ip, port, id) },
                fetchLiveDeviceDescriptor = { loc, usn, st, rtt, isUdp ->
                    client.fetchLiveDeviceDescriptor(loc, usn, st, rtt, isUdp)
                },
                nowTime = { "6:00:00 PM" }
            )
            val report = engine.performSsdpDiscoveryScan(
                searchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
                includeDemoFallbacks = false
            )
            assertTrue(report.isArcVmEnvironment)
            assertEquals("100.115.92.6", report.localInterfaceIp)
            assertTrue(report.logEntry.endpoint.contains("ChromeOS VM"))
            assertTrue(report.discoveredDevices.any { it.serialNumber == "SNCHROME01" && it.isLiveUdpResponse })
        }
    }

    @Test
    fun composeUiShowsArcVmManualSubnetFallbackAndGuardsAddHomeDialogPreFill() {
        var scannedManualSubnet: String? = null
        val arcEmptyReport = SsdpScanReport(
            localInterfaceIp = "100.115.92.6",
            searchTargetUsed = XmlAndSkuParser.URN_BASIC_EVENT,
            mSearchRequestRaw = "M-SEARCH",
            discoveredDevices = emptyList(),
            rawSocketHitCount = 0,
            scanDurationMs = 1800L,
            logEntry = SoapLogEntry(
                timestamp = "6:00:00 PM",
                deviceName = "SSDP",
                endpoint = "udp://239.255.255.250:1900",
                soapAction = "M-SEARCH",
                statusText = "0 found",
                xmlSnippet = "",
                isLiveLanSuccess = false
            ),
            isArcVmEnvironment = true,
            scannedSubnets = emptyList()
        )

        composeTestRule.setContent {
            WeMiTheme {
                SsdpScannerModal(
                    isScanning = false,
                    selectedSearchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
                    scanReport = arcEmptyReport,
                    existingDevices = emptyList(),
                    onSelectSearchTarget = {},
                    onRescan = {},
                    onAdoptDevice = {},
                    onAdoptAll = {},
                    onDismiss = {},
                    onScanManualSubnet = { scannedManualSubnet = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("arcvm_manual_subnet_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Local Interface: 100.115.92.6 (ChromeOS VM)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ssdp_scan_manual_subnet_button").assertIsNotEnabled()

        // Reject CGNAT guest subnet input
        composeTestRule.onNodeWithTag("ssdp_manual_subnet_input").performTextInput("100.115.92")
        composeTestRule.onNodeWithTag("ssdp_scan_manual_subnet_button").assertIsNotEnabled()

        // Accept physical RFC 1918 subnet prefix and invoke manual scan callback
        composeTestRule.onNodeWithTag("ssdp_manual_subnet_input").performTextClearance()
        composeTestRule.onNodeWithTag("ssdp_manual_subnet_input").performTextInput("192.168.86.0/24")
        composeTestRule.onNodeWithTag("ssdp_scan_manual_subnet_button").assertIsEnabled()
        composeTestRule.onNodeWithTag("ssdp_scan_manual_subnet_button").performClick()
        assertEquals("192.168.86", scannedManualSubnet)
    }

    @Test
    fun addWifiHomeDialogOmitsCgnatSubnetPreFillButPreservesRfc1918OnPhone() {
        val dialogState = androidx.compose.runtime.mutableStateOf(true)
        composeTestRule.setContent {
            WeMiTheme {
                if (dialogState.value) {
                    AddWifiHomeNetworkDialog(
                        detectedLocalIp = "100.115.92.6",
                        onDismiss = {},
                        onSaveHome = { _, _, _ -> }
                    )
                } else {
                    AddWifiHomeNetworkDialog(
                        detectedLocalIp = "192.168.1.50",
                        onDismiss = {},
                        onSaveHome = { _, _, _ -> }
                    )
                }
            }
        }

        // On ChromeOS ARCVM, do not pre-fill 100.115.92
        composeTestRule.onNodeWithTag("wifi_home_subnet_input").assertIsDisplayed()
        composeTestRule.onNodeWithText("100.115.92").assertDoesNotExist()

        // On Phone/TV RFC 1918 LAN, preserve automatic 192.168.1 pre-fill
        composeTestRule.runOnUiThread { dialogState.value = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wifi_home_subnet_input").assertTextContains("192.168.1")
    }
}
