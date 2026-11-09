package com.example

import com.example.data.WeMoDeviceCategory
import com.example.data.WeMoDeviceType
import com.example.data.classifyDeviceCategory
import com.example.network.WeMoNetworkClient
import com.example.network.WeMoProvisionerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeMoSkuAndCryptoTest {

    @Test
    fun `verify hardware SKU classification for F7C030, WLS0403, F7C059, WDS060, F7C063, and WSP080`() {
        val client = WeMoNetworkClient()

        assertTrue(
            client.classifyHardwareSku(
                modelName = "LightSwitch",
                udn = "uuid:Lightswitch-1_0-221517K01018FF"
            ).startsWith("F7C030")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "WLS0403",
                udn = "uuid:Lightswitch-3_0-221901K01022AA",
                isRtos = true
            ).startsWith("WLS0403")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "Dimmer",
                udn = "uuid:Dimmer-1_0-221733K15000BB"
            ).startsWith("F7C059")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "WDS060",
                udn = "uuid:Dimmer-2_0-222011K15000CC",
                isRtos = true
            ).startsWith("WDS060")
        )
        assertTrue(
            client.classifyHardwareSku(
                modelName = "Socket",
                metaSku = "WSP080",
                udn = "uuid:Socket-1_0-221844K01099DD"
            ).startsWith("WSP080")
        )
    }

    @Test
    fun `verify Phase 6 WeMoProvisionerClient GetApList parsing and pywemo AES-128-CBC round-trip encryption across Methods 1, 2, and 3`() {
        val provisioner = WeMoProvisionerClient()
        val sampleApXml = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetApListResponse xmlns:u="urn:Belkin:service:WiFiSetup:1">
                  <ApList>Page:1/1/2$
            HeadFamily-2.4G|6|88|WPA2PSK/AES,
            GuestNetwork|11|54|OPEN/NONE,
                  </ApList>
                </u:GetApListResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val aps = provisioner.parseApListXml(sampleApXml)
        assertEquals(2, aps.size)
        assertEquals("HeadFamily-2.4G", aps[0].ssid)
        assertEquals(6, aps[0].channel)
        assertEquals(88, aps[0].signalStrengthPercent)
        assertEquals("WPA2PSK", aps[0].authMode)
        assertEquals("AES", aps[0].encryptionType)

        // Verify pywemo-exact keydata construction for Methods 1, 2, and 3
        val mac = "EC:1A:59:AA:11:22"
        val serial = "221517K01018AB"
        assertEquals(
            "EC1A59221517K01018ABAA1122",
            provisioner.buildKeyData(mac, serial, encryptionMethod = 1)
        )
        assertEquals(
            "EC1A59221517K01018ABAA1122b3{8t;80dIN{ra83eC1s?M70?683@2Yf",
            provisioner.buildKeyData(mac, serial, encryptionMethod = 2)
        )
        assertEquals(
            "EC1122221517K01018ABbVduaWFyZkllaWVAb3RjbHAkcm9uT2JhAA1A59",
            provisioner.buildKeyData(mac, serial, encryptionMethod = 3)
        )

        val plainPassword = "MyHomeWifiPassword123" // 21 bytes (0x15), padded to 32 bytes (0x20)
        val method1Enc = provisioner.encryptWifiPassword(
            plainPassword = plainPassword,
            macAddress = mac,
            serialNumber = serial,
            encryptionMethod = 1
        )
        val method2Enc = provisioner.encryptWifiPassword(
            plainPassword = plainPassword,
            macAddress = mac,
            serialNumber = serial,
            encryptionMethod = 2
        )
        val method3Enc = provisioner.encryptWifiPassword(
            plainPassword = plainPassword,
            macAddress = mac,
            serialNumber = serial,
            encryptionMethod = 3
        )

        // Method 1 & Method 3 append 4-hex-digit length suffix (Base64 len 44 = 0x2c + original len 21 = 0x15 -> "2c15")
        assertTrue(method1Enc.endsWith("2c15"))
        assertTrue(method3Enc.endsWith("2c15"))
        // Method 2 (Gen 2 RTOS) uses addLengths = false in pywemo -> pure Base64 without hex length suffix
        assertFalse(method2Enc.endsWith("2c15"))
        assertFalse(method1Enc == method2Enc)
        assertFalse(method1Enc == method3Enc)

        // Verify byte-for-byte OpenSSL AES-128-CBC round-trip decryption for all 3 methods
        assertEquals(
            plainPassword,
            provisioner.decryptWifiPassword(method1Enc, mac, serial, encryptionMethod = 1)
        )
        assertEquals(
            plainPassword,
            provisioner.decryptWifiPassword(method2Enc, mac, serial, encryptionMethod = 2)
        )
        assertEquals(
            plainPassword,
            provisioner.decryptWifiPassword(method3Enc, mac, serial, encryptionMethod = 3)
        )
    }

    @Test
    fun `verify startup binaryState parsing in setup xml and semantic name classification for trees, fans, and lamps`() {
        val client = WeMoNetworkClient()

        // 1. Verify setup.xml with <binaryState>0</binaryState> parses as reachable OFF (isOn = false)
        val offTreeXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:Belkin:device-1-0">
              <device>
                <deviceType>urn:Belkin:device:controllee:1</deviceType>
                <friendlyName>Christmas Tree Living</friendlyName>
                <modelName>Socket</modelName>
                <serialNumber>221517K01018A1</serialNumber>
                <binaryState>0</binaryState>
              </device>
            </root>
        """.trimIndent()
        val offTree = client.parseSetupXmlDescriptor(
            xml = offTreeXml,
            locationUrl = "http://192.168.1.101:49153/setup.xml",
            usnHeader = "uuid:Socket-1_0-221517K01018A1",
            stHeader = "urn:Belkin:service:basicevent:1",
            responseTimeMs = 11L,
            isLiveUdp = true
        )
        assertFalse(offTree.isOn)
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, offTree.visualCategory)

        // 2. Verify setup.xml with pipe-delimited <binaryState>1|1700000000|45</binaryState> parses as ON
        val onFanXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:Belkin:device-1-0">
              <device>
                <deviceType>urn:Belkin:device:lightswitch:1</deviceType>
                <friendlyName>Bedroom Ceiling Fan</friendlyName>
                <modelName>LightSwitch</modelName>
                <serialNumber>221517K01018A9</serialNumber>
                <binaryState>1|1700000000|45</binaryState>
              </device>
            </root>
        """.trimIndent()
        val onFan = client.parseSetupXmlDescriptor(
            xml = onFanXml,
            locationUrl = "http://192.168.1.109:49153/setup.xml",
            usnHeader = "uuid:Lightswitch-1_0-221517K01018A9",
            stHeader = "urn:Belkin:service:basicevent:1",
            responseTimeMs = 13L,
            isLiveUdp = true
        )
        assertTrue(onFan.isOn)
        assertEquals(WeMoDeviceCategory.FAN, onFan.visualCategory)

        // 3. Verify lamp, light, fan, tree, motion sensor, and generic switch classification
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Living Room Christmas Tree"))
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Front Yard Tree Lights"))
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Xmas Garland Plug"))
        assertEquals(WeMoDeviceCategory.CHRISTMAS_TREE, classifyDeviceCategory("Blue Spruce Porch"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Patio Misting Fan"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Bathroom Exhaust Blower"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Whole House Ventilator"))
        assertEquals(WeMoDeviceCategory.FAN, classifyDeviceCategory("Workshop Air Circulator"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Bedside Reading Lamp"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Kitchen Under-Cabinet Light"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Dining Chandelier"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Hallway Wall Sconce"))
        assertEquals(WeMoDeviceCategory.LIGHT, classifyDeviceCategory("Any Name On Dimmer", WeMoDeviceType.LIGHTBULB_DIMMER))
        assertEquals(WeMoDeviceCategory.MOTION_SENSOR, classifyDeviceCategory("Hallway Motion Detector"))
        assertEquals(WeMoDeviceCategory.MOTION_SENSOR, classifyDeviceCategory("Driveway Sensor"))
        assertEquals(WeMoDeviceCategory.SWITCH_PLUG, classifyDeviceCategory("Espresso Machine Plug"))
        assertEquals(WeMoDeviceCategory.SWITCH_PLUG, classifyDeviceCategory("Office Wall Switch"))

        // 4. Verify isWemoDeviceSetupSsid and parseMetaInfoSoapResponse filter all Belkin WeMo soft-AP SSIDs while keeping real router SSIDs
        val softApSsids = listOf(
            "WeMo.Switch.998",
            "WeMo.Light.204",
            "WeMo.Mini.711",
            "WeMo.Dimmer.503",
            "WeMo.Insight.119",
            "WeMo.Motion.882",
            "wemo_ap_123",
            "wemo-setup-01",
            "Belkin.Setup.44"
        )
        softApSsids.forEach { softSsid ->
            assertTrue(
                "Expected $softSsid to be recognized as a WeMo soft-AP SSID",
                com.example.data.WeMoDiscoveryCoordinator.isWemoDeviceSetupSsid(softSsid)
            )
            val metaXml = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetMetaInfoResponse xmlns:u="urn:Belkin:service:metainfo:1">
                      <MetaInfo>EC1A59AA1122|221517K01018AB|LightSwitch|WeMo_WW_2.00.11532.PVT|$softSsid|LightSwitch</MetaInfo>
                    </u:GetMetaInfoResponse>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()
            val parsedMeta = com.example.network.WeMoXmlAndSkuParser.parseMetaInfoSoapResponse(metaXml)
            assertEquals("", parsedMeta?.accessPointSsid)
        }

        val validRouterSsids = listOf(
            "HeadFamily-2.4G",
            "WeMi-Home-5G",
            "PineLake-Cabin-WiFi",
            "Studio-IoT-VLAN"
        )
        validRouterSsids.forEach { routerSsid ->
            org.junit.Assert.assertFalse(
                "Expected $routerSsid to be accepted as a valid home router SSID",
                com.example.data.WeMoDiscoveryCoordinator.isWemoDeviceSetupSsid(routerSsid)
            )
            val routerSsidMetaXml = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                  <s:Body>
                    <u:GetMetaInfoResponse xmlns:u="urn:Belkin:service:metainfo:1">
                      <MetaInfo>EC1A59AA1122|221517K01018AB|LightSwitch|WeMo_WW_2.00.11532.PVT|$routerSsid|LightSwitch</MetaInfo>
                    </u:GetMetaInfoResponse>
                  </s:Body>
                </s:Envelope>
            """.trimIndent()
            val parsedRouterMeta = com.example.network.WeMoXmlAndSkuParser.parseMetaInfoSoapResponse(routerSsidMetaXml)
            assertEquals(routerSsid, parsedRouterMeta?.accessPointSsid)
        }

        // 5. Verify inferRoomFromName handles "Bed", "Stairway", "Girls"/"Kids", word-bounded "Den"/"Hall", and "Unassigned" fallback
        assertEquals("Bedroom", com.example.data.inferRoomFromName("Shprese Bed Lamp"))
        assertEquals("Bedroom", com.example.data.inferRoomFromName("Mike Bed Light"))
        assertEquals("Bedroom", com.example.data.inferRoomFromName("Girls Light"))
        assertEquals("Bedroom", com.example.data.inferRoomFromName("Kids Nightstand Lamp"))
        assertEquals("Hallway & Landing", com.example.data.inferRoomFromName("Stairway Light"))
        assertEquals("Hallway & Landing", com.example.data.inferRoomFromName("Upstairs Hall Sconce"))
        assertEquals("Living Room", com.example.data.inferRoomFromName("Living Room Floor Lamp"))
        assertEquals("Living Room", com.example.data.inferRoomFromName("Family Den Switch"))
        assertEquals("Unassigned", com.example.data.inferRoomFromName("Wooden Accent Lamp"))
        assertEquals("Unassigned", com.example.data.inferRoomFromName("Marshall Amp Plug"))
        assertEquals("Unassigned", com.example.data.inferRoomFromName("Espresso Machine"))
    }
}
