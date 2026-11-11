package org.suppressingfire.wemi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.RuleType
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.XmlAndSkuParser
import org.suppressingfire.wemi.ui.TvScene

/**
 * Fast, zero-Android-framework JVM unit tests for pure protocol helpers (`XmlAndSkuParser`),
 * placeholder serial utilities (`LanRuntimeUtils`), 3-tier device identity matching
 * (`DeviceEntity.matchesDiscovered`), and typed `RuleType` / `TvScene` enums.
 */
class ExampleUnitTest {

    @Test
    fun `verify LanRuntimeUtils placeholder serial detection`() {
        assertTrue(LanRuntimeUtils.isPlaceholderSerial(null))
        assertTrue(LanRuntimeUtils.isPlaceholderSerial(""))
        assertTrue(LanRuntimeUtils.isPlaceholderSerial("   "))
        assertTrue(LanRuntimeUtils.isPlaceholderSerial(LanRuntimeUtils.DEFAULT_PLACEHOLDER_SERIAL))
        assertTrue(LanRuntimeUtils.isPlaceholderSerial(LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL))
        assertTrue(LanRuntimeUtils.isPlaceholderSerial(LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL))
        assertTrue(LanRuntimeUtils.isPlaceholderSerial("221517k01018lan"))
        assertFalse(LanRuntimeUtils.isPlaceholderSerial("WLS0403SN9988"))
    }

    @Test
    fun `verify XmlAndSkuParser SOAP envelope builder, XML escaping, and BinaryState payload parser`() {
        val escaped = XmlAndSkuParser.escapeXml("Porch & Patio <Light> \"A\" 'B'")
        assertEquals("Porch &amp; Patio &lt;Light&gt; &quot;A&quot; &apos;B&apos;", escaped)

        val envelope = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
            action = "ChangeFriendlyName",
            innerXml = "<FriendlyName>$escaped</FriendlyName>"
        )
        assertTrue(envelope.contains("<u:ChangeFriendlyName xmlns:u=\"urn:Belkin:service:basicevent:1\">"))
        assertTrue(envelope.contains("<FriendlyName>$escaped</FriendlyName>"))
        assertEquals(
            "\"urn:Belkin:service:basicevent:1#ChangeFriendlyName\"",
            XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, "ChangeFriendlyName")
        )

        val piped = XmlAndSkuParser.parseBinaryStatePayload("<BinaryState>1|72</BinaryState><NightMode>1</NightMode>")
        assertEquals(true, piped.binaryOn)
        assertEquals(72, piped.brightness)
        assertEquals(true, piped.nightModeEnabled)
        assertFalse(piped.isIdempotentError)

        val escapedGena = XmlAndSkuParser.parseBinaryStatePayload(
            rawXml = "&lt;BinaryState&gt;0|35&lt;/BinaryState&gt;",
            unescapeEntities = true
        )
        assertEquals(false, escapedGena.binaryOn)
        assertEquals(35, escapedGena.brightness)

        val errState = XmlAndSkuParser.parseBinaryStatePayload("<BinaryState>Error</BinaryState>")
        assertNull(errState.binaryOn)
        assertTrue(errState.isIdempotentError)
    }

    @Test
    fun `verify 3-tier DeviceEntity identity matcher and RuleType and TvScene enum parsers`() {
        val discovered = SsdpDiscoveredDevice(
            name = "Porch Switch",
            ipAddress = "192.168.1.188",
            port = 49154,
            deviceType = DeviceType.SWITCH_PLUG,
            modelName = "WLS0403",
            serialNumber = "WLS0403REAL01",
            firmwareVersion = "WeMo_WW_2.00.12055.PVT",
            usn = "uuid:Lightswitch-3_0-WLS0403REAL01::urn:Belkin:service:basicevent:1",
            searchTarget = XmlAndSkuParser.URN_BASIC_EVENT,
            locationUrl = "http://192.168.1.188:49154/setup.xml",
            responseTimeMs = 9L
        )

        // Tier 1: Matches by serialNumber across DHCP IP & port hop
        val bySerial = DeviceEntity(
            name = "Old Name",
            roomName = "Porch & Entryway",
            ipAddress = "192.168.1.120",
            port = 49153,
            serialNumber = "WLS0403REAL01"
        )
        assertTrue(bySerial.matchesDiscovered(discovered))

        // Tier 2: Matches by UDN prefix inside USN when serialNumber is placeholder
        val byUdn = DeviceEntity(
            name = "Old Name",
            roomName = "Porch & Entryway",
            ipAddress = "192.168.1.120",
            port = 49153,
            serialNumber = LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
            udn = "uuid:Lightswitch-3_0-WLS0403REAL01"
        )
        assertTrue(byUdn.matchesDiscovered(discovered))

        // Different real serialNumber on same IP must NOT match (DHCP IP swap guard)
        val swappedSerial = DeviceEntity(
            name = "Porch Switch",
            roomName = "Porch & Entryway",
            ipAddress = "192.168.1.188",
            port = 49154,
            serialNumber = "DIFFERENT_SERIAL_99"
        )
        assertFalse(swappedSerial.matchesDiscovered(discovered))

        assertEquals(RuleType.MOTION_TRIGGER, RuleType.fromRaw("MOTION_TRIGGER"))
        assertEquals(RuleType.AUTO_OFF, RuleType.fromRaw("auto_off"))
        assertEquals(RuleType.SCHEDULE, RuleType.fromRaw("unknown_type"))

        assertEquals(TvScene.MOVIE_MODE, TvScene.fromWireKey("MOVIE_MODE"))
        assertEquals(TvScene.HOLIDAY_ON, TvScene.fromWireKey("holiday_on"))
        assertNull(TvScene.fromWireKey("INVALID_SCENE"))
    }

    @Test
    fun `verify release App Bundle R8 deobfuscation and native symbol Play Console guards`() {
        val cwd = java.io.File(System.getProperty("user.dir") ?: ".")
        val repoRoot = if (java.io.File(cwd, "LICENSE").exists()) cwd else (cwd.parentFile ?: cwd)

        val buildGradle = java.io.File(repoRoot, "app/build.gradle.kts").readText()
        assertTrue(
            "Release buildType must enable R8 minification so Play App Bundles embed proguard.map",
            buildGradle.contains("isMinifyEnabled = true")
        )
        assertTrue(
            "Release buildType must enable resource shrinking alongside R8",
            buildGradle.contains("isShrinkResources = true")
        )
        assertTrue(
            "Release buildType must configure debugSymbolLevel = \"FULL\" for any unstripped native code",
            buildGradle.contains("debugSymbolLevel = \"FULL\"")
        )
        assertTrue(
            "Packaging jniLibs must exclude unused pre-stripped libandroidx.graphics.path.so",
            buildGradle.contains("**/libandroidx.graphics.path.so")
        )

        val proguardRules = java.io.File(repoRoot, "app/proguard-rules.pro").readText()
        assertTrue(
            "proguard-rules.pro must preserve SourceFile,LineNumberTable for Play Console stack traces",
            proguardRules.contains("-keepattributes SourceFile,LineNumberTable")
        )
        assertTrue(
            "proguard-rules.pro must preserve domain enum constant names for Room valueOf lookups",
            proguardRules.contains("-keep enum org.suppressingfire.wemi.**")
        )
    }
}
