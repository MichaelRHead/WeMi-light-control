package org.suppressingfire.wemi.network

import java.util.Locale
import org.suppressingfire.wemi.data.DEFAULT_MAIN_HOME_KEY
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.HomeNetworkEntity
import org.suppressingfire.wemi.data.normalizeRoomName

/**
 * User-selected strategy when applying an imported [HouseholdSyncBundle] to Room.
 */
enum class HouseholdImportMode {
    MERGE,
    REPLACE
}

data class SyncedHomeProfile(
    val networkKey: String,
    val homeName: String,
    val ssidLabel: String,
    val subnetPrefix: String,
    val gatewayIp: String,
    val autoReconnectOnArrival: Boolean = true,
    val sortOrder: Int = 0
)

data class SyncedDeviceProfile(
    val name: String,
    val roomName: String,
    val networkKey: String,
    val wifiSsid: String = "",
    val deviceType: String = DeviceType.SWITCH_PLUG.name,
    val ipAddress: String = "",
    val port: Int = 49153,
    val serialNumber: String = "",
    val udn: String = "",
    val macAddress: String = "",
    val hardwareModel: String = "LightSwitch",
    val sortOrder: Int = 0
)

/**
 * Portable zero-cloud household configuration snapshot containing only remembered Wi-Fi Homes,
 * custom switch names, room assignments, ordering, and hardware identity hints.
 *
 * Intentionally excludes Wi-Fi passwords, telemetry logs, volatile power/latency state, and
 * on-device `rules.db` backups.
 */
data class HouseholdSyncBundle(
    val schemaVersion: Int = HouseholdSyncCodec.SCHEMA_VERSION,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val sourceDeviceLabel: String = "WeMi Device",
    val homes: List<SyncedHomeProfile> = emptyList(),
    val devices: List<SyncedDeviceProfile> = emptyList()
) {
    val distinctRoomCount: Int
        get() = devices.map { normalizeRoomName(it.roomName).lowercase(Locale.US) }.toSet().size
}

data class HouseholdImportSummary(
    val mode: HouseholdImportMode,
    val homesAdded: Int,
    val homesUpdated: Int,
    val devicesAdded: Int,
    val devicesUpdated: Int,
    val devicesPreserved: Int,
    val activeNetworkKey: String
)

/**
 * Zero-dependency RFC 8259 JSON codec and strict sanitizer for [HouseholdSyncBundle].
 *
 * Uses pure Kotlin serialization and a bounded recursive-descent parser (no `org.json` or
 * third-party dependencies), enforcing payload size, nesting depth, entity count, RFC 1918
 * subnet/IP validation, soft-AP SSID filtering, and single-line token sanitization.
 */
object HouseholdSyncCodec {
    const val SCHEMA_VERSION = 1
    const val DEFAULT_EXPORT_FILENAME = "wemi-household-config.json"
    const val MAX_JSON_BYTES = 256 * 1024
    const val MAX_JSON_DEPTH = 12
    const val MAX_HOMES = 32
    const val MAX_DEVICES = 256
    private const val MAX_STRING_LEN = 256

    fun fromEntities(
        homes: List<HomeNetworkEntity>,
        devices: List<DeviceEntity>,
        sourceDeviceLabel: String = "WeMi Device",
        exportedAtEpochMs: Long = System.currentTimeMillis(),
        allowLoopback: Boolean = LanRuntimeUtils.isRunningInRobolectric()
    ): HouseholdSyncBundle {
        val cleanHomes = deduplicateHomes(
            homes
                .sortedWith(compareBy<HomeNetworkEntity> { it.sortOrder }.thenBy { it.homeName })
                .take(MAX_HOMES)
                .mapIndexed { idx, home ->
                    val cleanSubnet = sanitizeSubnetPrefix(home.subnetPrefix, allowLoopback)
                    val cleanGateway = sanitizeHostOrBlank(home.gatewayIp, allowLoopback)
                        .ifEmpty { if (cleanSubnet.isNotEmpty()) "$cleanSubnet.1" else "" }
                    SyncedHomeProfile(
                        networkKey = sanitizeNetworkKey(home.networkKey, idx),
                        homeName = sanitizeHomeName(home.homeName),
                        ssidLabel = sanitizeSsidLabel(home.ssidLabel),
                        subnetPrefix = cleanSubnet,
                        gatewayIp = cleanGateway,
                        autoReconnectOnArrival = home.autoReconnectOnArrival,
                        sortOrder = home.sortOrder.coerceIn(0, 10_000)
                    )
                }
        )
        val cleanDevices = deduplicateDevices(
            devices
                .sortedWith(compareBy<DeviceEntity> { it.sortOrder }.thenBy { it.id })
                .take(MAX_DEVICES)
                .mapIndexed { idx, dev ->
                    val cleanSerial = LanEndpointPolicy.sanitizeHardwareIdentifier(dev.serialNumber, maxLen = 64)
                        .takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }
                        .orEmpty()
                    val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(
                        LanEndpointPolicy.sanitizeHardwareIdentifier(dev.udn, maxLen = 96)
                    )
                    val cleanMac = LanEndpointPolicy.sanitizeHardwareIdentifier(dev.macAddress, maxLen = 32)
                    val cleanType = runCatching { DeviceType.valueOf(dev.deviceType) }
                        .getOrDefault(DeviceType.SWITCH_PLUG).name
                    SyncedDeviceProfile(
                        name = sanitizeLabel(dev.name, maxLen = 80).ifEmpty { "Smart Switch ${idx + 1}" },
                        roomName = normalizeRoomName(sanitizeLabel(dev.roomName, maxLen = 64)),
                        networkKey = sanitizeNetworkKey(dev.networkKey, 0),
                        wifiSsid = sanitizeSsidLabel(dev.wifiSsid, fallback = ""),
                        deviceType = cleanType,
                        ipAddress = sanitizeHostOrBlank(dev.ipAddress, allowLoopback),
                        port = dev.port.takeIf { it in 1..65535 } ?: 49153,
                        serialNumber = cleanSerial,
                        udn = cleanUdn,
                        macAddress = cleanMac,
                        hardwareModel = sanitizeLabel(dev.hardwareModel, maxLen = 64).ifEmpty { "LightSwitch" },
                        sortOrder = dev.sortOrder.coerceIn(0, 100_000)
                    )
                }
        )
        return HouseholdSyncBundle(
            schemaVersion = SCHEMA_VERSION,
            exportedAtEpochMs = exportedAtEpochMs,
            sourceDeviceLabel = sanitizeLabel(sourceDeviceLabel, maxLen = 64).ifEmpty { "WeMi Device" },
            homes = cleanHomes,
            devices = cleanDevices
        )
    }

    fun encodeToJson(bundle: HouseholdSyncBundle): String {
        val sb = StringBuilder(2048)
        sb.append("{\n")
        sb.append("  \"schemaVersion\": ").append(bundle.schemaVersion).append(",\n")
        sb.append("  \"exportedAtEpochMs\": ").append(bundle.exportedAtEpochMs).append(",\n")
        sb.append("  \"sourceDeviceLabel\": ").append(quoteJson(bundle.sourceDeviceLabel)).append(",\n")
        sb.append("  \"homes\": [")
        if (bundle.homes.isNotEmpty()) sb.append('\n')
        bundle.homes.forEachIndexed { idx, home ->
            sb.append("    {\n")
            sb.append("      \"networkKey\": ").append(quoteJson(home.networkKey)).append(",\n")
            sb.append("      \"homeName\": ").append(quoteJson(home.homeName)).append(",\n")
            sb.append("      \"ssidLabel\": ").append(quoteJson(home.ssidLabel)).append(",\n")
            sb.append("      \"subnetPrefix\": ").append(quoteJson(home.subnetPrefix)).append(",\n")
            sb.append("      \"gatewayIp\": ").append(quoteJson(home.gatewayIp)).append(",\n")
            sb.append("      \"autoReconnectOnArrival\": ").append(home.autoReconnectOnArrival).append(",\n")
            sb.append("      \"sortOrder\": ").append(home.sortOrder).append('\n')
            sb.append("    }")
            if (idx < bundle.homes.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append(if (bundle.homes.isNotEmpty()) "  ],\n" else "],\n")
        sb.append("  \"devices\": [")
        if (bundle.devices.isNotEmpty()) sb.append('\n')
        bundle.devices.forEachIndexed { idx, dev ->
            sb.append("    {\n")
            sb.append("      \"name\": ").append(quoteJson(dev.name)).append(",\n")
            sb.append("      \"roomName\": ").append(quoteJson(dev.roomName)).append(",\n")
            sb.append("      \"networkKey\": ").append(quoteJson(dev.networkKey)).append(",\n")
            sb.append("      \"wifiSsid\": ").append(quoteJson(dev.wifiSsid)).append(",\n")
            sb.append("      \"deviceType\": ").append(quoteJson(dev.deviceType)).append(",\n")
            sb.append("      \"ipAddress\": ").append(quoteJson(dev.ipAddress)).append(",\n")
            sb.append("      \"port\": ").append(dev.port).append(",\n")
            sb.append("      \"serialNumber\": ").append(quoteJson(dev.serialNumber)).append(",\n")
            sb.append("      \"udn\": ").append(quoteJson(dev.udn)).append(",\n")
            sb.append("      \"macAddress\": ").append(quoteJson(dev.macAddress)).append(",\n")
            sb.append("      \"hardwareModel\": ").append(quoteJson(dev.hardwareModel)).append(",\n")
            sb.append("      \"sortOrder\": ").append(dev.sortOrder).append('\n')
            sb.append("    }")
            if (idx < bundle.devices.lastIndex) sb.append(',')
            sb.append('\n')
        }
        sb.append(if (bundle.devices.isNotEmpty()) "  ]\n" else "]\n")
        sb.append("}\n")
        return sb.toString()
    }

    fun decodeFromJson(
        jsonText: String,
        allowLoopback: Boolean = LanRuntimeUtils.isRunningInRobolectric()
    ): HouseholdSyncBundle {
        val rawBytes = jsonText.toByteArray(Charsets.UTF_8)
        require(rawBytes.isNotEmpty()) { "Household configuration file is empty" }
        require(rawBytes.size <= MAX_JSON_BYTES) {
            "Household configuration exceeds ${MAX_JSON_BYTES / 1024} KiB safety limit"
        }
        val root = parseJsonObject(jsonText)
        val version = (root["schemaVersion"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("Missing schemaVersion in household configuration")
        require(version == SCHEMA_VERSION) {
            "Unsupported household configuration schemaVersion=$version (expected $SCHEMA_VERSION)"
        }
        val exportedAt = (root["exportedAtEpochMs"] as? Number)?.toLong() ?: 0L
        val sourceLabel = sanitizeLabel(root["sourceDeviceLabel"] as? String ?: "", maxLen = 64)
            .ifEmpty { "WeMi Device" }

        val rawHomes = (root["homes"] as? List<*>) ?: emptyList<Any?>()
        require(rawHomes.size <= MAX_HOMES) { "Too many homes in configuration (max $MAX_HOMES)" }
        val homes = deduplicateHomes(
            rawHomes.mapIndexed { idx, item ->
                val obj = item as? Map<*, *>
                    ?: throw IllegalArgumentException("Invalid home entry at index $idx")
                val subnet = sanitizeSubnetPrefix(obj["subnetPrefix"] as? String, allowLoopback)
                val gw = sanitizeHostOrBlank(obj["gatewayIp"] as? String, allowLoopback)
                    .ifEmpty { if (subnet.isNotEmpty()) "$subnet.1" else "" }
                SyncedHomeProfile(
                    networkKey = sanitizeNetworkKey(obj["networkKey"] as? String, idx),
                    homeName = sanitizeHomeName(obj["homeName"] as? String),
                    ssidLabel = sanitizeSsidLabel(obj["ssidLabel"] as? String),
                    subnetPrefix = subnet,
                    gatewayIp = gw,
                    autoReconnectOnArrival = (obj["autoReconnectOnArrival"] as? Boolean) ?: true,
                    sortOrder = ((obj["sortOrder"] as? Number)?.toInt() ?: idx).coerceIn(0, 10_000)
                )
            }
        )

        val rawDevices = (root["devices"] as? List<*>) ?: emptyList<Any?>()
        require(rawDevices.size <= MAX_DEVICES) { "Too many switches in configuration (max $MAX_DEVICES)" }
        val defaultKey = homes.firstOrNull()?.networkKey ?: DEFAULT_MAIN_HOME_KEY
        val devices = deduplicateDevices(
            rawDevices.mapIndexed { idx, item ->
                val obj = item as? Map<*, *>
                    ?: throw IllegalArgumentException("Invalid switch entry at index $idx")
                val name = sanitizeLabel(obj["name"] as? String, maxLen = 80)
                require(name.isNotEmpty()) { "Switch entry $idx is missing a friendly name" }
                val rawType = (obj["deviceType"] as? String).orEmpty().trim()
                val cleanType = runCatching { DeviceType.valueOf(rawType) }
                    .getOrDefault(DeviceType.SWITCH_PLUG).name
                val rawSerial = LanEndpointPolicy.sanitizeHardwareIdentifier(obj["serialNumber"] as? String, maxLen = 64)
                val cleanSerial = if (LanRuntimeUtils.isPlaceholderSerial(rawSerial)) "" else rawSerial
                val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(
                    LanEndpointPolicy.sanitizeHardwareIdentifier(obj["udn"] as? String, maxLen = 96)
                )
                SyncedDeviceProfile(
                    name = name,
                    roomName = normalizeRoomName(sanitizeLabel(obj["roomName"] as? String, maxLen = 64)),
                    networkKey = sanitizeNetworkKey(obj["networkKey"] as? String, 0, fallback = defaultKey),
                    wifiSsid = sanitizeSsidLabel(obj["wifiSsid"] as? String, fallback = ""),
                    deviceType = cleanType,
                    ipAddress = sanitizeHostOrBlank(obj["ipAddress"] as? String, allowLoopback),
                    port = ((obj["port"] as? Number)?.toInt() ?: 49153).takeIf { it in 1..65535 } ?: 49153,
                    serialNumber = cleanSerial,
                    udn = cleanUdn,
                    macAddress = LanEndpointPolicy.sanitizeHardwareIdentifier(obj["macAddress"] as? String, maxLen = 32),
                    hardwareModel = sanitizeLabel(obj["hardwareModel"] as? String, maxLen = 64).ifEmpty { "LightSwitch" },
                    sortOrder = ((obj["sortOrder"] as? Number)?.toInt() ?: idx).coerceIn(0, 100_000)
                )
            }
        )

        require(homes.isNotEmpty() || devices.isNotEmpty()) {
            "Household configuration contains no Wi-Fi Homes or switches"
        }

        return HouseholdSyncBundle(
            schemaVersion = version,
            exportedAtEpochMs = exportedAt,
            sourceDeviceLabel = sourceLabel,
            homes = homes,
            devices = devices
        )
    }

    /**
     * Defense-in-depth sanitization and 3-tier deduplication for bundles passed directly in Kotlin
     * without going through [decodeFromJson].
     */
    internal fun deduplicateAndSanitize(
        bundle: HouseholdSyncBundle,
        allowLoopback: Boolean = LanRuntimeUtils.isRunningInRobolectric()
    ): HouseholdSyncBundle {
        val cleanHomes = deduplicateHomes(
            bundle.homes.take(MAX_HOMES).mapIndexed { idx, home ->
                val subnet = sanitizeSubnetPrefix(home.subnetPrefix, allowLoopback)
                val gw = sanitizeHostOrBlank(home.gatewayIp, allowLoopback)
                    .ifEmpty { if (subnet.isNotEmpty()) "$subnet.1" else "" }
                home.copy(
                    networkKey = sanitizeNetworkKey(home.networkKey, idx),
                    homeName = sanitizeHomeName(home.homeName),
                    ssidLabel = sanitizeSsidLabel(home.ssidLabel),
                    subnetPrefix = subnet,
                    gatewayIp = gw,
                    sortOrder = home.sortOrder.coerceIn(0, 10_000)
                )
            }
        )
        val defaultKey = cleanHomes.firstOrNull()?.networkKey ?: DEFAULT_MAIN_HOME_KEY
        val cleanDevices = deduplicateDevices(
            bundle.devices.take(MAX_DEVICES).mapIndexedNotNull { idx, dev ->
                val name = sanitizeLabel(dev.name, maxLen = 80)
                if (name.isEmpty()) return@mapIndexedNotNull null
                val rawSerial = LanEndpointPolicy.sanitizeHardwareIdentifier(dev.serialNumber, maxLen = 64)
                val cleanSerial = if (LanRuntimeUtils.isPlaceholderSerial(rawSerial)) "" else rawSerial
                val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(
                    LanEndpointPolicy.sanitizeHardwareIdentifier(dev.udn, maxLen = 96)
                )
                val cleanType = runCatching { DeviceType.valueOf(dev.deviceType.trim()) }
                    .getOrDefault(DeviceType.SWITCH_PLUG).name
                dev.copy(
                    name = name,
                    roomName = normalizeRoomName(sanitizeLabel(dev.roomName, maxLen = 64)),
                    networkKey = sanitizeNetworkKey(dev.networkKey, 0, fallback = defaultKey),
                    wifiSsid = sanitizeSsidLabel(dev.wifiSsid, fallback = ""),
                    deviceType = cleanType,
                    ipAddress = sanitizeHostOrBlank(dev.ipAddress, allowLoopback),
                    port = dev.port.takeIf { it in 1..65535 } ?: 49153,
                    serialNumber = cleanSerial,
                    udn = cleanUdn,
                    macAddress = LanEndpointPolicy.sanitizeHardwareIdentifier(dev.macAddress, maxLen = 32),
                    hardwareModel = sanitizeLabel(dev.hardwareModel, maxLen = 64).ifEmpty { "LightSwitch" },
                    sortOrder = dev.sortOrder.coerceIn(0, 100_000)
                )
            }
        )
        return bundle.copy(
            sourceDeviceLabel = sanitizeLabel(bundle.sourceDeviceLabel, maxLen = 64).ifEmpty { "WeMi Device" },
            homes = cleanHomes,
            devices = cleanDevices
        )
    }

    internal fun quoteJson(value: String): String = StrictJsonCodec.quoteJson(value)

    internal fun parseJsonObject(input: String): Map<String, Any?> = StrictJsonCodec.parseJsonObject(input)

    internal fun sanitizeLabel(raw: String?, maxLen: Int = 80): String {
        val singleLine = LanEndpointPolicy.sanitizeSingleLineToken(raw.orEmpty(), maxLen = maxLen)
        val filtered = buildString(singleLine.length) {
            for (ch in singleLine) {
                val code = ch.code
                val isControl = code < 0x20 || code == 0x7F
                val isBidi = code in 0x202A..0x202E || code in 0x2066..0x2069
                if (!isControl && !isBidi) append(ch)
            }
        }
        return filtered.trim()
    }

    internal fun isGenericLanSsid(raw: String?): Boolean {
        val clean = raw?.trim().orEmpty()
        return clean.isEmpty() ||
            clean.equals("Local LAN", ignoreCase = true) ||
            clean.equals("Local Wi-Fi", ignoreCase = true) ||
            clean.startsWith("LAN ", ignoreCase = true)
    }

    internal fun isCgnatLabel(raw: String?): Boolean {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return false
        if (clean.startsWith("LAN ", ignoreCase = true)) {
            val suffix = clean.removePrefix("LAN ").removePrefix("lan ").trim().substringBefore('/')
            if (LanRuntimeUtils.isCarrierGradeNatIpv4(suffix)) return true
        }
        val parenToken = clean.substringAfter('(', "").substringBefore(')', "").trim()
        return parenToken.isNotEmpty() && LanRuntimeUtils.isCarrierGradeNatIpv4(parenToken)
    }

    private fun sanitizeHomeName(raw: String?): String {
        val clean = sanitizeLabel(raw, maxLen = 64)
        return if (clean.isEmpty() || XmlAndSkuParser.isWemoDeviceSetupSsid(clean) || isCgnatLabel(clean)) {
            "My Home"
        } else {
            clean
        }
    }

    internal fun sanitizeSsidLabel(raw: String?, fallback: String = "Local LAN"): String {
        val clean = sanitizeLabel(raw, maxLen = 64)
        return if (clean.isEmpty() || XmlAndSkuParser.isWemoDeviceSetupSsid(clean) || isCgnatLabel(clean)) {
            fallback
        } else {
            clean
        }
    }

    internal fun sanitizeHostOrBlank(raw: String?, allowLoopback: Boolean): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty() || LanRuntimeUtils.isCarrierGradeNatIpv4(clean)) return ""
        if (clean.startsWith("10.22.22.") || clean.startsWith("10.0.2.")) return ""
        return if (LanEndpointPolicy.isAllowedLanHost(clean, allowLoopback = allowLoopback)) clean else ""
    }

    internal fun sanitizeSubnetPrefix(raw: String?, allowLoopback: Boolean): String {
        val normalized = LanRuntimeUtils.normalizeSubnetPrefixOrNull(raw) ?: return ""
        val probeHost = "$normalized.1"
        if (LanRuntimeUtils.isCarrierGradeNatIpv4(probeHost)) return ""
        if (LanRuntimeUtils.isPrivateRfc1918Ipv4(normalized)) return normalized
        return if (allowLoopback && normalized == "127.0.0") normalized else ""
    }

    private fun sanitizeNetworkKey(
        raw: String?,
        index: Int,
        fallback: String = "wifi_synced_home_${index + 1}"
    ): String {
        val cleaned = LanEndpointPolicy.sanitizeHardwareIdentifier(raw, maxLen = 64)
            .replace(':', '_')
        return cleaned.ifEmpty { fallback }
    }

    private fun deduplicateHomes(homes: List<SyncedHomeProfile>): List<SyncedHomeProfile> {
        val seenKeys = HashSet<String>(homes.size)
        return homes.filter { seenKeys.add(it.networkKey) }
    }

    private fun deduplicateDevices(devices: List<SyncedDeviceProfile>): List<SyncedDeviceProfile> {
        val seenSerials = HashSet<String>()
        val seenUdns = HashSet<String>()
        val seenEndpoints = HashSet<String>()
        val result = ArrayList<SyncedDeviceProfile>(devices.size)
        for (dev in devices) {
            val serialKey = dev.serialNumber.takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }
                ?.lowercase(Locale.US).orEmpty()
            val udnKey = LanRuntimeUtils.canonicalHardwareUdn(dev.udn)
                .takeIf { it.isNotEmpty() }
                ?.let { "${dev.networkKey}|${it.lowercase(Locale.US)}" }.orEmpty()
            val endpointKey = dev.ipAddress.takeIf { it.isNotBlank() }
                ?.let { "${dev.networkKey}|$it:${dev.port}" }.orEmpty()

            if (serialKey.isNotEmpty() && serialKey in seenSerials) continue
            if (serialKey.isEmpty() && udnKey.isNotEmpty() && udnKey in seenUdns) continue
            if (serialKey.isEmpty() && udnKey.isEmpty() && endpointKey.isNotEmpty() && endpointKey in seenEndpoints) continue

            if (serialKey.isNotEmpty()) seenSerials.add(serialKey)
            if (udnKey.isNotEmpty()) seenUdns.add(udnKey)
            if (endpointKey.isNotEmpty()) seenEndpoints.add(endpointKey)
            result.add(dev)
        }
        return result
    }
}
