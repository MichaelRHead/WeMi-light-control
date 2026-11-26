package org.suppressingfire.wemi.network

import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import org.suppressingfire.wemi.data.DeviceType
import java.util.Locale

/**
 * Stateless UPnP / SSDP / SOAP XML parsers, packet builders, and Belkin WeMo hardware SKU
 * classifiers (`F7C030`, `WLS040`, `WLS0403`, `F7C059`, `WDS060`, `F7C063`, `WSP080`).
 */
object XmlAndSkuParser {

    const val SKU_WLS040_3WAY = "WLS040 (2nd-Gen 3-Way Switch)"
    const val SKU_WDS060_DIMMER_V2 = "WDS060 (Gen-2 Smart Dimmer)"

    const val URN_BASIC_EVENT = "urn:Belkin:service:basicevent:1"
    const val URN_META_INFO = "urn:Belkin:service:metainfo:1"
    const val URN_WIFI_SETUP = "urn:Belkin:service:WiFiSetup:1"
    const val URN_RULES = "urn:Belkin:service:rules:1"
    const val URN_TIMESYNC = "urn:Belkin:service:timesync:1"

    private val BINARY_STATE_REGEX = Regex("<BinaryState>([^<]+)</BinaryState>", RegexOption.IGNORE_CASE)
    private val BRIGHTNESS_REGEX = Regex("<Brightness>(\\d+)</Brightness>", RegexOption.IGNORE_CASE)
    private val NIGHT_MODE_REGEX = Regex("<NightMode>(\\d+)</NightMode>", RegexOption.IGNORE_CASE)
    private val FRIENDLY_NAME_REGEX = Regex("<friendlyName>([^<]+)</friendlyName>", RegexOption.IGNORE_CASE)
    private val SERIAL_NUMBER_REGEX = Regex("<serialNumber>([^<]+)</serialNumber>", RegexOption.IGNORE_CASE)
    private val MODEL_NAME_REGEX = Regex("<modelName>([^<]+)</modelName>", RegexOption.IGNORE_CASE)
    private val MODEL_DESC_REGEX = Regex("<modelDescription>([^<]+)</modelDescription>", RegexOption.IGNORE_CASE)
    private val FIRMWARE_REGEX = Regex("<firmwareVersion>([^<]+)</firmwareVersion>", RegexOption.IGNORE_CASE)
    private val MAC_ADDRESS_REGEX = Regex("<macAddress>([^<]+)</macAddress>", RegexOption.IGNORE_CASE)
    private val UDN_REGEX = Regex("<UDN>([^<]+)</UDN>", RegexOption.IGNORE_CASE)
    private val RTOS_REGEX = Regex("<rtos>\\s*1\\s*</rtos>", RegexOption.IGNORE_CASE)
    private val EXPLICIT_RTOS_FW_REGEX = Regex("-rtos(?:-|$)", RegexOption.IGNORE_CASE)
    private val SERVICE_BLOCK_REGEX = Regex("<service>([\\s\\S]*?)</service>", RegexOption.IGNORE_CASE)
    private val CONTROL_URL_REGEX = Regex("<controlURL>([^<]+)</controlURL>", RegexOption.IGNORE_CASE)
    private val SCPD_URL_REGEX = Regex("<SCPDURL>([^<]+)</SCPDURL>", RegexOption.IGNORE_CASE)
    private val META_INFO_REGEX = Regex("<MetaInfo>([^<]+)</MetaInfo>", RegexOption.IGNORE_CASE)
    private val SIGNAL_STRENGTH_REGEX = Regex("<SignalStrength>([^<]+)</SignalStrength>", RegexOption.IGNORE_CASE)
    private val AP_LIST_REGEX = Regex("<(?:ApList|NetworkList)>([\\s\\S]*?)</(?:ApList|NetworkList)>", RegexOption.IGNORE_CASE)
    private val AP_PAGE_REGEX = Regex("Page:[^$\\n]*\\$?")

    /**
     * Escapes XML special characters (`&`, `<`, `>`, `"`, `'`) in user-supplied strings
     * such as friendly names, SSIDs, and encrypted password payloads.
     */
    fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Decodes standard XML character entities (`&lt;`, `&gt;`, `&quot;`, `&apos;`, `&#39;`,
     * and `&amp;` last to prevent double-unescaping) returned by UPnP `/setup.xml`, `GetMetaInfo`,
     * `GetApList`, and GENA payloads.
     */
    fun unescapeXml(input: String): String {
        if ('&' !in input) return input
        return input
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }

    /**
     * Builds a standard UTF-8 UPnP SOAP 1.1 envelope for [serviceUrn] and [action],
     * optionally embedding [innerXml] parameter tags inside `<u:$action>`.
     */
    fun buildSoapEnvelope(
        serviceUrn: String,
        action: String,
        innerXml: String = ""
    ): String {
        val trimmedInner = innerXml.trim()
        val bodyContent = if (trimmedInner.isEmpty()) {
            ""
        } else {
            "\n" + trimmedInner.prependIndent("      ")
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$action xmlns:u="$serviceUrn">$bodyContent
                </u:$action>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    /**
     * Formats the quoted HTTP `SOAPACTION` header value `"<serviceUrn>#<action>"`.
     */
    fun soapActionHeader(serviceUrn: String, action: String): String =
        "\"$serviceUrn#$action\""

    /**
     * Parses a `<BinaryState>` (and optional pipe-delimited `|brightness`, `<Brightness>`/`<brightness>`,
     * and `<NightMode>`) XML payload returned by `SetBinaryState`, `GetBinaryState`, or GENA `NOTIFY`.
     */
    fun parseBinaryStatePayload(
        rawXml: String,
        unescapeEntities: Boolean = false
    ): ParsedBinaryState {
        val xml = if (unescapeEntities) unescapeXml(rawXml) else rawXml
        val rawState = BINARY_STATE_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val parts = if (rawState.isEmpty()) emptyList() else rawState.split("|")
        val firstToken = parts.firstOrNull()?.trim().orEmpty()
        val isError = firstToken.equals("Error", ignoreCase = true)
        val binaryOn: Boolean? = when {
            firstToken == "1" || firstToken == "8" || firstToken.equals("true", ignoreCase = true) -> true
            firstToken == "0" || firstToken.equals("false", ignoreCase = true) -> false
            else -> null
        }
        val brightness: Int? = parts.getOrNull(1)?.trim()?.toIntOrNull()
            ?: BRIGHTNESS_REGEX.find(xml)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val nightMode: Boolean? = NIGHT_MODE_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.let { it == "1" }

        return ParsedBinaryState(
            rawFirstToken = firstToken,
            binaryOn = binaryOn,
            brightness = brightness,
            nightModeEnabled = nightMode,
            isIdempotentError = isError
        )
    }

    /**
     * Builds a standard UPnP SSDP M-SEARCH UDP multicast payload for Belkin WeMo / WeMi devices.
     */
    fun buildSsdpMSearchPacket(searchTarget: String = URN_BASIC_EVENT): String {
        val safeTarget = LanEndpointPolicy.sanitizeSingleLineToken(searchTarget).ifEmpty { URN_BASIC_EVENT }
        return buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: 239.255.255.250:1900\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 1\r\n")
            append("ST: $safeTarget\r\n")
            append("USER-AGENT: Android/16 UPnP/1.1 WeMiSwitchControl/1.0\r\n\r\n")
        }
    }

    /**
     * Parses raw SSDP HTTP/1.1 200 OK UDP response headers (LOCATION, USN, ST, SERVER).
     */
    fun parseSsdpHeaders(rawResponse: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        rawResponse.lineSequence().forEach { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim().uppercase(Locale.US)
                val value = line.substring(colonIdx + 1).trim()
                headers[key] = value
            }
        }
        return headers
    }

    /**
     * Classifies the specific Belkin WeMo hardware SKU (`F7C030`, `WLS040`/`WLS0403`,
     * `F7C059`/`WDS060`, `F7C063`/`WSP080`) from UPnP `setup.xml` fields and optional `metainfo1` SKU telemetry.
     */
    fun classifyHardwareSku(
        modelName: String,
        modelDescription: String = "",
        udn: String = "",
        serialNumber: String = "",
        metaSku: String = "",
        isRtos: Boolean = false
    ): String {
        val combined = "$metaSku $modelName $modelDescription $udn $serialNumber"
        return when {
            combined.contains("WLS0403", ignoreCase = true) -> "WLS0403 (3-Way Light Switch)"
            combined.contains("WLS040", ignoreCase = true) ||
                udn.contains("Lightswitch-2_0", ignoreCase = true) ||
                udn.contains("Lightswitch-3_0", ignoreCase = true) ||
                (modelName.equals("LightSwitch", ignoreCase = true) && isRtos) -> SKU_WLS040_3WAY
            combined.contains("WDS060", ignoreCase = true) ||
                udn.contains("Dimmer-2_0", ignoreCase = true) -> SKU_WDS060_DIMMER_V2
            combined.contains("F7C059", ignoreCase = true) ||
                modelName.contains("Dimmer", ignoreCase = true) ||
                udn.contains("Dimmer", ignoreCase = true) -> "F7C059 (Wi-Fi Smart Dimmer)"
            combined.contains("WSP080", ignoreCase = true) -> "WSP080 (Mini Smart Plug Gen-2)"
            combined.contains("F7C063", ignoreCase = true) ||
                modelName.contains("Mini", ignoreCase = true) -> "F7C063 (Mini Smart Plug)"
            combined.contains("Socket", ignoreCase = true) -> "F7C063 (Smart Plug)"
            combined.contains("F7C030", ignoreCase = true) ||
                modelName.contains("LightSwitch", ignoreCase = true) ||
                udn.contains("Lightswitch", ignoreCase = true) -> "F7C030 (1st-Gen Light Switch)"
            else -> modelName.ifBlank { "LightSwitch" }
        }
    }

    /**
     * Parses a Belkin WeMo / WeMi UPnP `setup.xml` document into an [SsdpDiscoveredDevice].
     */
    fun parseSetupXmlDescriptor(
        xml: String,
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice {
        val friendlyName = FRIENDLY_NAME_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.let(::unescapeXml)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "WeMi Smart Switch"
        val rawSerial = SERIAL_NUMBER_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL
        val serial = LanEndpointPolicy.sanitizeHardwareIdentifier(rawSerial)
            .ifBlank { LanRuntimeUtils.DEFAULT_LAN_PLACEHOLDER_SERIAL }
        val modelName = MODEL_NAME_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.let(::unescapeXml)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: "LightSwitch"
        val modelDescription = MODEL_DESC_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.let(::unescapeXml)?.trim().orEmpty()
        val firmware = FIRMWARE_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.trim() ?: "WeMo_WW_2.00.11532.PVT"
        val macAddress = MAC_ADDRESS_REGEX
            .find(xml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val udn = LanRuntimeUtils.canonicalHardwareUdn(
            LanEndpointPolicy.sanitizeHardwareIdentifier(
                UDN_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()
                    ?: usnHeader.ifEmpty {
                        if (!LanRuntimeUtils.isPlaceholderSerial(serial)) "uuid:Lightswitch-1_0-$serial" else ""
                    }
            )
        )
        val isRtos = RTOS_REGEX.containsMatchIn(xml)

        val parsedHostPort = LanEndpointPolicy.parseHostAndPortOrNull(locationUrl, defaultPort = 80)
        val ip = parsedHostPort?.first ?: "192.168.1.101"
        val port = parsedHostPort?.second ?: 49153

        val type = when {
            modelName.contains("Sensor", ignoreCase = true) ||
                modelName.contains("Motion", ignoreCase = true) ||
                udn.contains("Sensor", ignoreCase = true) -> DeviceType.MOTION_SENSOR
            modelName.contains("Dimmer", ignoreCase = true) ||
                modelName.contains("WDS060", ignoreCase = true) ||
                modelName.contains("F7C059", ignoreCase = true) ||
                modelName.contains("Lightbulb", ignoreCase = true) ||
                modelName.contains("Link", ignoreCase = true) ||
                udn.contains("Dimmer", ignoreCase = true) -> DeviceType.LIGHTBULB_DIMMER
            else -> DeviceType.SWITCH_PLUG
        }

        val sku = classifyHardwareSku(
            modelName = modelName,
            modelDescription = modelDescription,
            udn = udn,
            serialNumber = serial,
            isRtos = isRtos
        )

        val parsedBinary = parseBinaryStatePayload(xml)
        val parsedIsOn = parsedBinary.binaryOn ?: true
        val parsedBrightness = parsedBinary.brightness?.coerceIn(1, 100) ?: 100
        val rulesHint = inferRulesCapabilityFromSetupXml(
            xml = xml,
            locationUrl = locationUrl,
            udn = udn,
            firmwareVersion = firmware,
            isRtosTag = isRtos
        )

        return SsdpDiscoveredDevice(
            name = friendlyName,
            ipAddress = ip,
            port = port,
            deviceType = type,
            modelName = modelName,
            serialNumber = serial,
            firmwareVersion = firmware,
            usn = udn,
            searchTarget = stHeader.ifEmpty { URN_BASIC_EVENT },
            locationUrl = locationUrl,
            responseTimeMs = responseTimeMs,
            isOn = parsedIsOn,
            brightness = parsedBrightness,
            isLiveUdpResponse = isLiveUdp,
            hardwareSku = sku,
            macAddress = macAddress,
            rulesCapabilityHint = rulesHint.capability,
            longPressCapableHint = rulesHint.longPressCapable
        )
    }

    /**
     * Tier-1 zero-RTT on-device rules capability inference directly from `/setup.xml` (no extra
     * HTTP requests during SSDP/unicast scans).
     *
     * Note: Some later OpenWrt builds (`PVT-OWRT-SNS` `2.00.11851`) emit `<rtos>1</rtos>` while
     * still running the full OpenWrt `rules.db` engine, whereas true RTOS units (`WDS060`
     * `PVT-RTOS-DimmerV2`, `WSP080` `PVT-RTOS-SNSV4`, `WSP090` `PVT-RTOS-OutdoorV1`) omit
     * `FetchRules` from `/rulesservice.xml`. Authoritative confirmation is deferred lazily to
     * Tier 2 (`HardwareRulesClient.probeRulesCapability`).
     */
    fun inferRulesCapabilityFromSetupXml(
        xml: String,
        locationUrl: String,
        udn: String,
        firmwareVersion: String,
        isRtosTag: Boolean = RTOS_REGEX.containsMatchIn(xml)
    ): SetupXmlRulesHint {
        val hasServiceList = xml.contains("<serviceList", ignoreCase = true)
        var hasRulesService = false
        var hasTimeSyncService = false
        var rulesControlUrl = "/upnp/control/rules1"
        var rulesScpdUrl = "/rulesservice.xml"
        for (serviceMatch in SERVICE_BLOCK_REGEX.findAll(xml)) {
            val block = serviceMatch.groupValues[1]
            if (block.contains(URN_RULES, ignoreCase = true)) {
                hasRulesService = true
                CONTROL_URL_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { rulesControlUrl = it }
                SCPD_URL_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { it.isNotEmpty() }?.let { rulesScpdUrl = it }
            } else if (block.contains(URN_TIMESYNC, ignoreCase = true)) {
                hasTimeSyncService = true
            }
        }
        val fwLower = firmwareVersion.lowercase(Locale.US)
        val isExplicitOpenWrt = fwLower.contains("owrt")
        val isExplicitRtos = EXPLICIT_RTOS_FW_REGEX.containsMatchIn(firmwareVersion) ||
            udn.contains("Dimmer-2_0", ignoreCase = true) ||
            udn.contains("OutdoorPlug", ignoreCase = true) ||
            (isRtosTag && !isExplicitOpenWrt)

        val capability = when {
            isExplicitRtos -> HardwareRulesCapability.LIKELY_UNSUPPORTED
            hasServiceList && !hasRulesService -> HardwareRulesCapability.LIKELY_UNSUPPORTED
            hasRulesService || isExplicitOpenWrt -> HardwareRulesCapability.LIKELY_SUPPORTED
            else -> HardwareRulesCapability.UNKNOWN
        }
        val isLongPressModel = (
            udn.startsWith("uuid:Lightswitch-1_0", ignoreCase = true) &&
                !locationUrl.endsWith("/Lightsetup.xml", ignoreCase = true)
            ) ||
            udn.startsWith("uuid:Lightswitch-2_0", ignoreCase = true) ||
            udn.startsWith("uuid:Lightswitch-3_0", ignoreCase = true) ||
            udn.startsWith("uuid:Dimmer-1_0", ignoreCase = true)
        val longPressCapable = isLongPressModel &&
            capability != HardwareRulesCapability.LIKELY_UNSUPPORTED

        return SetupXmlRulesHint(
            capability = capability,
            longPressCapable = longPressCapable,
            hasRulesService = hasRulesService,
            hasTimeSyncService = hasTimeSyncService,
            rulesControlUrl = rulesControlUrl,
            rulesScpdUrl = rulesScpdUrl
        )
    }

    /**
     * Returns true if [ssid] is a Belkin WeMo hardware soft-AP or device identifier
     * (e.g. `WeMo.Switch.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`, `WeMo.Dimmer.xxx`,
     * `WeMo.Insight.xxx`, `WeMo.Motion.xxx`), which `metainfo1#GetMetaInfo` often returns
     * in field 4 instead of the home router SSID.
     */
    fun isWemoDeviceSetupSsid(ssid: String): Boolean {
        val s = ssid.trim().lowercase(Locale.US)
        if (s.isEmpty()) return false
        return s.startsWith("wemo.") ||
            s.startsWith("wemo_") ||
            s.startsWith("wemo-") ||
            s.contains("wemo.switch") ||
            s.contains("wemo.light") ||
            s.contains("wemo.mini") ||
            s.contains("wemo.dimmer") ||
            s.contains("wemo.insight") ||
            s.contains("wemo.motion") ||
            s.startsWith("belkin.")
    }

    /**
     * Parses pipe-delimited `metainfo1#GetMetaInfo` SOAP response XML:
     * `MAC|SerialNumber|DeviceSKU|FirmwareVersion|AccessPointSSID|ModelName`.
     * Note: On many Belkin WeMo firmwares, field 4 (`AccessPointSSID`) is the switch's own
     * setup soft-AP SSID (e.g. `WeMo.Switch.xxx`, `WeMo.Light.xxx`), which is filtered out here
     * so it is never mistaken for the user's home Wi-Fi router SSID.
     */
    fun parseMetaInfoSoapResponse(body: String): SwitchMetaInfo? {
        val rawMeta = META_INFO_REGEX
            .find(body)?.groupValues?.getOrNull(1)?.let(::unescapeXml)?.trim() ?: return null
        val parts = rawMeta.split("|")
        if (parts.size < 2) return null
        val rawSsid = parts.getOrNull(4)?.trim().orEmpty()
        val routerSsid = rawSsid.takeUnless { isWemoDeviceSetupSsid(it) }.orEmpty()
        return SwitchMetaInfo(
            macAddress = parts.getOrNull(0)?.trim().orEmpty(),
            serialNumber = parts.getOrNull(1)?.trim().orEmpty(),
            deviceSku = parts.getOrNull(2)?.trim().orEmpty(),
            firmwareVersion = parts.getOrNull(3)?.trim().orEmpty(),
            accessPointSsid = routerSsid,
            modelName = parts.getOrNull(5)?.trim().orEmpty()
        )
    }

    /**
     * Parses `basicevent1#GetSignalStrength` SOAP response XML and normalizes dBm/percentage to `0..100%`.
     */
    fun parseSignalStrengthSoapResponse(body: String): Int? {
        val raw = SIGNAL_STRENGTH_REGEX
            .find(body)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
        return if (raw < 0) {
            ((raw + 95) * 2).coerceIn(5, 100)
        } else {
            raw.coerceIn(0, 100)
        }
    }

    /**
     * Parses `WiFiSetup1#GetApList` / `GetNetworkList` pipe-delimited AP response XML:
     * `SSID|Channel|RSSI|AuthMode/EncrypType`.
     */
    fun parseApListXml(xml: String): List<ScannedAp> {
        val directMatch = AP_LIST_REGEX.find(xml)?.groupValues?.getOrNull(1)?.trim()
        val (rawList, alreadyUnescaped) = if (directMatch != null) {
            directMatch to false
        } else {
            val fallback = AP_LIST_REGEX.find(unescapeXml(xml))?.groupValues?.getOrNull(1)?.trim()
                ?: return emptyList()
            fallback to true
        }

        val results = mutableListOf<ScannedAp>()
        val normalizedList = rawList.replace(AP_PAGE_REGEX, "\n")
        for (rawLine in normalizedList.lines()) {
            val clean = rawLine.trim().removeSuffix(",").trim()
            if (clean.isEmpty() || clean.startsWith("Page:") || !clean.contains("|")) continue
            val parts = clean.split("|")
            if (parts.size >= 4) {
                val rawSsid = parts[0].trim()
                val ssid = if (alreadyUnescaped) rawSsid else unescapeXml(rawSsid)
                if (ssid.isEmpty()) continue
                val channel = parts[1].trim().toIntOrNull() ?: 6
                val rssi = parts[2].trim().toIntOrNull()?.coerceIn(1, 100) ?: 75
                val secString = parts.last().trim().removeSuffix(",")
                val secParts = secString.split("/")
                val auth = secParts.getOrNull(0)?.trim().orEmpty().ifEmpty { "WPA2PSK" }
                val enc = secParts.getOrNull(1)?.trim()?.removeSuffix(",").orEmpty().ifEmpty { "AES" }
                if (results.none { it.ssid == ssid }) {
                    results.add(
                        ScannedAp(
                            ssid = ssid,
                            channel = channel,
                            signalStrengthPercent = rssi,
                            authMode = auth,
                            encryptionType = enc
                        )
                    )
                }
            }
        }
        return results
    }
}
