package org.suppressingfire.wemi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        assertTrue(LanRuntimeUtils.isPlaceholderSerial("unknown"))
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

        // Tier 2: Matches bidirectionally across bare <UDN> and USN::service suffix when serialNumber is placeholder
        val byUdn = DeviceEntity(
            name = "Old Name",
            roomName = "Porch & Entryway",
            ipAddress = "192.168.1.120",
            port = 49153,
            serialNumber = LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
            udn = "uuid:Lightswitch-3_0-WLS0403REAL01"
        )
        assertTrue(byUdn.matchesDiscovered(discovered))
        assertTrue(
            byUdn.copy(udn = "uuid:Lightswitch-3_0-WLS0403REAL01::urn:Belkin:service:basicevent:1")
                .matchesDiscovered(discovered.copy(usn = "uuid:Lightswitch-3_0-WLS0403REAL01"))
        )

        // Placeholder-synthesized UDNs must return "" from canonicalHardwareUdn and never cross-match distinct switches
        assertEquals("", LanRuntimeUtils.canonicalHardwareUdn("uuid:Lightswitch-1_0-${LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL}"))
        assertEquals("", LanRuntimeUtils.canonicalHardwareUdn("uuid:Lightswitch-1_0-UNKNOWN::urn:Belkin:service:basicevent:1"))
        val placeholderSwitchA = DeviceEntity(
            name = "Switch A",
            ipAddress = "192.168.1.140",
            port = 49153,
            serialNumber = LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
            udn = "uuid:Lightswitch-1_0-${LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL}"
        )
        assertFalse(
            placeholderSwitchA.matchesDiscovered(
                discovered.copy(
                    name = "Switch B",
                    ipAddress = "192.168.1.141",
                    serialNumber = LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL,
                    usn = "uuid:Lightswitch-1_0-${LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL}"
                )
            )
        )

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
        assertTrue(
            "proguard-rules.pro must preserve androidx.activity.EdgeToEdge* API-level impls loaded reflectively",
            proguardRules.contains("-keep class androidx.activity.EdgeToEdge*")
        )

        val manifestXml = java.io.File(repoRoot, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(
            "AndroidManifest.xml must declare android.hardware.wifi with required=false for Ethernet Android TV boxes",
            manifestXml.contains("android.hardware.wifi") && manifestXml.contains("android:required=\"false\"")
        )

        val backupRulesXml = java.io.File(repoRoot, "app/src/main/res/xml/backup_rules.xml").readText()
        assertTrue(
            "backup_rules.xml must exclude ephemeral wemi-telemetry-latest.txt from Auto Backup",
            backupRulesXml.contains("<exclude domain=\"file\" path=\"wemi-telemetry-latest.txt\"")
        )

        val dataExtractionXml = java.io.File(repoRoot, "app/src/main/res/xml/data_extraction_rules.xml").readText()
        assertTrue(
            "data_extraction_rules.xml must exclude ephemeral wemi-telemetry-latest.txt in cloud-backup and device-transfer",
            dataExtractionXml.contains("<cloud-backup>") &&
                dataExtractionXml.contains("<device-transfer>") &&
                Regex("wemi-telemetry-latest\\.txt").findAll(dataExtractionXml).count() >= 2
        )
    }

    @Test
    fun `verify DeviceEntity concurrencyKey isolates placeholder serial switches by endpoint and shares real serial across IP hops`() {
        val manualSwitchA = DeviceEntity(
            name = "Porch Light",
            ipAddress = "192.168.1.115",
            port = 49153,
            serialNumber = LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL
        )
        val manualSwitchB = DeviceEntity(
            name = "Garage Light",
            ipAddress = "192.168.1.116",
            port = 49153,
            serialNumber = LanRuntimeUtils.DEFAULT_NEW_PLACEHOLDER_SERIAL
        )
        assertEquals("192.168.1.115:49153", manualSwitchA.concurrencyKey())
        assertEquals("192.168.1.116:49153", manualSwitchB.concurrencyKey())
        assertFalse(manualSwitchA.concurrencyKey() == manualSwitchB.concurrencyKey())

        val realBeforeDhcpHop = DeviceEntity(
            name = "Hallway 3-Way",
            ipAddress = "192.168.1.120",
            port = 49153,
            serialNumber = "WLS0403SN9988"
        )
        val realAfterDhcpHop = realBeforeDhcpHop.copy(ipAddress = "192.168.1.188", port = 49154)
        assertEquals("WLS0403SN9988", realBeforeDhcpHop.concurrencyKey())
        assertEquals(realBeforeDhcpHop.concurrencyKey(), realAfterDhcpHop.concurrencyKey())

        // Verify SetupApProber skips "UNKNOWN" in GetMetaInfo in favor of real /setup.xml serialNumber
        java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val t = kotlin.concurrent.thread(isDaemon = true) {
                repeat(3) {
                    runCatching {
                        server.accept().use { sock ->
                            sock.soTimeout = 1000
                            val reader = java.io.BufferedReader(
                                java.io.InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1)
                            )
                            val reqLine = reader.readLine().orEmpty()
                            if (reqLine.isBlank()) return@use
                            var contentLength = 0
                            while (true) {
                                val h = reader.readLine() ?: break
                                if (h.isEmpty()) break
                                if (h.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                                }
                            }
                            var rem = contentLength
                            while (rem > 0) {
                                val readCount = reader.read(CharArray(rem))
                                if (readCount <= 0) break
                                rem -= readCount
                            }
                            val body = if (reqLine.contains("/setup.xml")) {
                                "<root><device><friendlyName>Porch Switch</friendlyName><modelName>WLS040</modelName><serialNumber>221840KREAL77</serialNumber><macAddress>EC1A59AA1122</macAddress><rtos>1</rtos></device></root>"
                            } else {
                                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><MetaInfo>EC1A59AA1122|UNKNOWN|WLS040|WeMo_WW_2.00|Wemo.LS3W.777|WLS040</MetaInfo></s:Body></s:Envelope>"
                            }
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            sock.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            sock.getOutputStream().write(bytes)
                            sock.getOutputStream().flush()
                        }
                    }
                }
            }
            val desc = kotlinx.coroutines.runBlocking {
                org.suppressingfire.wemi.network.ProvisionerClient(defaultApHost = "127.0.0.1")
                    .probeSetupDevice("127.0.0.1", server.localPort)
            }
            t.join(1000)
            assertEquals("221840KREAL77", desc?.serialNumber)
            assertEquals("GetMetaInfo+setup.xml", desc?.macAndSerialSource)
        }
    }

    @Test
    fun `verify executable script modes and DeviceDescriptorFetcher placeholder serial guard`() {
        val repoRoot = listOf(java.io.File("."), java.io.File(".."))
            .map { it.canonicalFile }
            .firstOrNull { java.io.File(it, "gradlew").isFile }
        assertNotNull("Expected repository root containing gradlew", repoRoot)

        val requiredExecutables = listOf(
            "gradlew",
            "scripts/advance_release_turn.sh",
            "scripts/await_release_turn.sh",
            ".agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh",
            ".agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh"
        )
        for (relPath in requiredExecutables) {
            val f = java.io.File(repoRoot!!, relPath)
            assertTrue("Expected script to exist: $relPath", f.isFile)
            assertTrue("Expected +x executable permission on $relPath", f.canExecute())
        }
        if (java.io.File(repoRoot!!, ".git").exists()) {
            val proc = ProcessBuilder(listOf("git", "ls-files", "-s") + requiredExecutables)
                .directory(repoRoot)
                .redirectErrorStream(true)
                .start()
            val indexOut = proc.inputStream.bufferedReader().readText()
            if (proc.waitFor() == 0 && indexOut.isNotBlank()) {
                for (line in indexOut.lines().filter { it.isNotBlank() }) {
                    assertTrue(
                        "Expected git index mode 100755 (AI Studio sync must not strip +x): $line",
                        line.startsWith("100755 ")
                    )
                }
            }
        }

        // Verify DeviceDescriptorFetcher skips "UNKNOWN" in GetMetaInfo and preserves real /setup.xml serialNumber
        java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val t = kotlin.concurrent.thread(isDaemon = true) {
                repeat(4) {
                    runCatching {
                        server.accept().use { sock ->
                            sock.soTimeout = 1000
                            val reader = java.io.BufferedReader(
                                java.io.InputStreamReader(sock.getInputStream(), Charsets.ISO_8859_1)
                            )
                            val reqLine = reader.readLine().orEmpty()
                            if (reqLine.isBlank()) return@use
                            var contentLength = 0
                            while (true) {
                                val h = reader.readLine() ?: break
                                if (h.isEmpty()) break
                                if (h.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLength = h.substringAfter(':').trim().toIntOrNull() ?: 0
                                }
                            }
                            var rem = contentLength
                            while (rem > 0) {
                                val readCount = reader.read(CharArray(rem))
                                if (readCount <= 0) break
                                rem -= readCount
                            }
                            val body = when {
                                reqLine.contains("/setup.xml") ->
                                    "<root><device><friendlyName>Master Bath Vanity</friendlyName><modelName>WLS040</modelName><serialNumber>221840KLAN99</serialNumber><macAddress>EC1A59BB2233</macAddress><UDN>uuid:Lightswitch-2_0-221840KLAN99</UDN><rtos>1</rtos></device></root>"
                                reqLine.contains("metainfo1") ->
                                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><MetaInfo>EC1A59BB2233|UNKNOWN|WLS040|WeMo_WW_2.00|HeadFamily-2.4G|WLS040</MetaInfo></s:Body></s:Envelope>"
                                else ->
                                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><BinaryState>1</BinaryState><SignalStrength>-52</SignalStrength></s:Body></s:Envelope>"
                            }
                            val bytes = body.toByteArray(Charsets.UTF_8)
                            sock.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            sock.getOutputStream().write(bytes)
                            sock.getOutputStream().flush()
                        }
                    }
                }
            }
            val desc = kotlinx.coroutines.runBlocking {
                org.suppressingfire.wemi.network.NetworkClient().fetchLiveDeviceDescriptor(
                    locationUrl = "http://127.0.0.1:${server.localPort}/setup.xml",
                    usnHeader = "uuid:Lightswitch-2_0-221840KLAN99::urn:Belkin:service:basicevent:1",
                    stHeader = XmlAndSkuParser.URN_BASIC_EVENT,
                    responseTimeMs = 8L,
                    isLiveUdp = true
                )
            }
            t.join(1000)
            assertEquals("221840KLAN99", desc?.serialNumber)
            assertEquals("HeadFamily-2.4G", desc?.wifiSsid)
        }
    }
}


