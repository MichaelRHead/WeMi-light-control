package org.suppressingfire.wemi.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.LanRuntimeUtils

const val DEFAULT_MAIN_HOME_KEY = "wifi_home_192_168_1"
const val DEFAULT_CABIN_HOME_KEY = "wifi_cabin_192_168_50"
const val DEFAULT_OFFICE_HOME_KEY = "wifi_office_10_0_10"
const val UNASSIGNED_ROOM = "Unassigned"

/** Canonicalizes a room name so blank, whitespace-only, or case-variant unassigned values map to [UNASSIGNED_ROOM]. */
fun normalizeRoomName(rawRoom: String?): String {
    val trimmed = rawRoom?.trim().orEmpty()
    return when {
        trimmed.isEmpty() || trimmed.equals(UNASSIGNED_ROOM, ignoreCase = true) -> UNASSIGNED_ROOM
        else -> trimmed
    }
}

/**
 * Groups [devices] by case-insensitive canonical room name (merging variants like `"Kitchen"` and
 * `"kitchen"` into a single display header), preserves user `sortOrder` across rooms, and sorts
 * [UNASSIGNED_ROOM] after all named rooms.
 */
fun groupDevicesByCanonicalRoom(devices: List<DeviceEntity>): Map<String, List<DeviceEntity>> {
    if (devices.isEmpty()) return emptyMap()
    val groupedByKey = devices.groupBy { it.normalizedRoomName.lowercase(Locale.US) }
    val resolvedEntries = groupedByKey.values.map { roomDevices ->
        val rawLabels = roomDevices.map { it.normalizedRoomName }
        val canonicalLabel = when {
            rawLabels.first().equals(UNASSIGNED_ROOM, ignoreCase = true) -> UNASSIGNED_ROOM
            else -> rawLabels.firstOrNull { label -> label != label.lowercase(Locale.US) } ?: rawLabels.first()
        }
        canonicalLabel to roomDevices
    }
    return resolvedEntries
        .sortedWith(
            compareBy<Pair<String, List<DeviceEntity>>> { (room, _) ->
                if (room.equals(UNASSIGNED_ROOM, ignoreCase = true)) 1 else 0
            }.thenBy { (_, roomDevices) ->
                roomDevices.minOfOrNull { it.sortOrder } ?: Int.MAX_VALUE
            }
        )
        .toMap(LinkedHashMap())
}

enum class DeviceType {
    SWITCH_PLUG,
    MOTION_SENSOR,
    LIGHTBULB_DIMMER
}

/**
 * Semantic device category derived from the switch's friendly name (or hardware type),
 * used to render specialized icons in the app (Christmas trees, fans, lamps/lights, motion sensors, or generic switches).
 */
enum class DeviceCategory(val displayName: String) {
    CHRISTMAS_TREE("Tree Lights"),
    FAN("Fan"),
    LIGHT("Light"),
    MOTION_SENSOR("Motion"),
    SWITCH_PLUG("Switch")
}

enum class ConnectionQuality(
    val statusTitle: String,
    val signalBars: Int
) {
    EXCELLENT(statusTitle = "Active", signalBars = 4),
    GOOD(statusTitle = "Active", signalBars = 3),
    SLOW(statusTitle = "High Latency", signalBars = 2),
    OFFLINE(statusTitle = "Offline", signalBars = 0)
}

/**
 * Represents a remembered Wi-Fi Network ("Home") so the app remembers all switches discovered
 * on each Wi-Fi network and automatically reconnects/rediscovers them when returning home.
 */
@Entity(tableName = "wemo_home_networks")
data class HomeNetworkEntity(
    @PrimaryKey val networkKey: String,
    val homeName: String,
    val ssidLabel: String,
    val subnetPrefix: String,
    val gatewayIp: String = "${subnetPrefix}.1",
    val lastConnectedTime: String = "Connected now",
    val autoReconnectOnArrival: Boolean = true,
    val sortOrder: Int = 0
)

@Entity(tableName = "wemo_devices")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val roomName: String = "Living Room",
    val networkKey: String = DEFAULT_MAIN_HOME_KEY,
    val wifiSsid: String = "WeMi-Home-5G",
    val deviceType: String = DeviceType.SWITCH_PLUG.name,
    val ipAddress: String = "192.168.1.100",
    val port: Int = 49153,
    val isOn: Boolean = true,
    val isReachable: Boolean = true,
    val brightness: Int = 100,
    val latencyMs: Int = 14,
    val lastCheckedTime: String = "Just now",
    val lastMotionTime: String = "4:15:15 PM",
    val serialNumber: String = LanRuntimeUtils.DEFAULT_PLACEHOLDER_SERIAL,
    val firmwareVersion: String = "WeMo_WW_2.00.11532.PVT",
    val hardwareModel: String = "LightSwitch",
    val macAddress: String = "",
    val udn: String = "",
    val signalStrengthPercent: Int = 85,
    val nightModeEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val autoOffMinutes: Int = 0,
    val hardwareRulesCapability: String = HardwareRulesCapability.UNKNOWN.name,
    val hardwareRulesLongPressCapable: Boolean = false,
    val hardwareRulesVerifiedFirmware: String = ""
) {
    val parsedType: DeviceType
        get() = runCatching { DeviceType.valueOf(deviceType) }
            .getOrDefault(DeviceType.SWITCH_PLUG)

    val parsedHardwareRulesCapability: HardwareRulesCapability
        get() = HardwareRulesCapability.fromRaw(hardwareRulesCapability)

    val isHardwareRulesCapabilityFresh: Boolean
        get() = parsedHardwareRulesCapability.isVerified &&
            hardwareRulesVerifiedFirmware.isNotBlank() &&
            hardwareRulesVerifiedFirmware.equals(firmwareVersion.trim(), ignoreCase = true)

    val normalizedRoomName: String
        get() = normalizeRoomName(roomName)

    val visualCategory: DeviceCategory
        get() = classifyDeviceCategory(name = name, deviceType = parsedType)

    val connectionQuality: ConnectionQuality
        get() = when {
            !isReachable || latencyMs <= 0 -> ConnectionQuality.OFFLINE
            latencyMs <= 28 -> ConnectionQuality.EXCELLENT
            latencyMs <= 65 -> ConnectionQuality.GOOD
            else -> ConnectionQuality.SLOW
        }

    val connectionBadgeText: String
        get() = when (connectionQuality) {
            ConnectionQuality.OFFLINE -> "Offline • Timeout"
            ConnectionQuality.EXCELLENT -> "Active • ${latencyMs}ms"
            ConnectionQuality.GOOD -> "Active • ${latencyMs}ms"
            ConnectionQuality.SLOW -> "Slow • ${latencyMs}ms"
        }

    /** Canonical per-device mutex key: real `<serialNumber>` or canonical `<UDN>`, else `ipAddress:port`. */
    fun concurrencyKey(): String =
        LanRuntimeUtils.expectedHardwareIdentity(serialNumber, udn).ifEmpty { "$ipAddress:$port" }


    /**
     * 3-tier hardware identity matcher between this persisted [DeviceEntity] and a [discovered]
     * SSDP/unicast node:
     * 1. **Tier 1 (Hardware Serial Identity)**: When both carry a real non-placeholder `<serialNumber>`,
     *    they match if and only if the serial numbers are equal (case-insensitive).
     * 2. **Tier 2 (UPnP UDN / USN Identity)**: When both carry a real non-placeholder canonical `<UDN>`,
     *    they match if and only if the canonical UDNs are equal (case-insensitive).
     * 3. **Tier 3 (Endpoint / Friendly Name Fallback)**: When neither Tier 1 nor Tier 2 has real
     *    hardware identities on both sides, matches by `(ipAddress, port)` (or `ipAddress` when
     *    [requireSamePortWhenBlankSerial] is false) or case-insensitive `<friendlyName>`.
     */
    fun matchesDiscovered(
        discovered: SsdpDiscoveredDevice,
        requireSamePortWhenBlankSerial: Boolean = true
    ): Boolean {
        if (!LanRuntimeUtils.isPlaceholderSerial(serialNumber) &&
            !LanRuntimeUtils.isPlaceholderSerial(discovered.serialNumber)
        ) {
            return serialNumber.equals(discovered.serialNumber, ignoreCase = true)
        }
        val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(udn)
        val cleanDiscoveredUdn = LanRuntimeUtils.canonicalHardwareUdn(discovered.usn)
        if (cleanUdn.isNotEmpty() && cleanDiscoveredUdn.isNotEmpty()) {
            return cleanUdn.equals(cleanDiscoveredUdn, ignoreCase = true)
        }
        val endpointMatch = ipAddress == discovered.ipAddress &&
            (!requireSamePortWhenBlankSerial || port == discovered.port)
        return endpointMatch || name.equals(discovered.name, ignoreCase = true)
    }
}

data class SoapLogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String,
    val deviceName: String,
    val endpoint: String,
    val soapAction: String,
    val statusText: String,
    val xmlSnippet: String,
    val isLiveLanSuccess: Boolean
)

data class SsdpDiscoveredDevice(
    val name: String,
    val roomName: String = inferRoomFromName(name),
    val ipAddress: String,
    val port: Int,
    val deviceType: DeviceType,
    val modelName: String,
    val serialNumber: String,
    val firmwareVersion: String,
    val usn: String,
    val searchTarget: String,
    val locationUrl: String,
    val responseTimeMs: Long,
    val isOn: Boolean = true,
    val brightness: Int = 100,
    val isLiveUdpResponse: Boolean = false,
    val hardwareSku: String = modelName,
    val macAddress: String = "",
    val wifiSsid: String = "",
    val signalStrengthPercent: Int = 85,
    val rulesCapabilityHint: HardwareRulesCapability = HardwareRulesCapability.UNKNOWN,
    val longPressCapableHint: Boolean = false
) {
    val visualCategory: DeviceCategory
        get() = classifyDeviceCategory(name = name, deviceType = deviceType)

    fun matchesEndpointOrSerial(other: SsdpDiscoveredDevice): Boolean {
        val bothRealSerials = !LanRuntimeUtils.isPlaceholderSerial(serialNumber) &&
            !LanRuntimeUtils.isPlaceholderSerial(other.serialNumber)
        if (bothRealSerials) {
            return serialNumber.equals(other.serialNumber, ignoreCase = true)
        }
        val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(usn)
        val otherUdn = LanRuntimeUtils.canonicalHardwareUdn(other.usn)
        if (cleanUdn.isNotEmpty() && otherUdn.isNotEmpty()) {
            return cleanUdn.equals(otherUdn, ignoreCase = true)
        }
        return ipAddress == other.ipAddress && port == other.port
    }
}

/**
 * Returns true when a controllable switch or dimmer represents seasonal/holiday lighting
 * (e.g. Christmas Tree, Holiday Lights, Diwali/Dewali Lights, Hanukkah, Fairy/String Lights),
 * normalizing CamelCase/delimited names and excluding motion sensors and false-positive substrings
 * such as `"Street Light"`.
 */
fun isHolidayLightDevice(device: DeviceEntity): Boolean {
    if (device.parsedType == DeviceType.MOTION_SENSOR ||
        device.visualCategory == DeviceCategory.MOTION_SENSOR
    ) {
        return false
    }
    if (device.visualCategory == DeviceCategory.CHRISTMAS_TREE) return true
    val normalized = normalizeDeviceNameForInference(device.name)
    return normalized.isNotEmpty() && RoomAndCategoryRules.matchesHolidayLight(normalized)
}

/**
 * Classifies a switch's visual device category based on its normalized friendly name and UPnP device type:
 * - Tree lights ("Christmas Tree", "Tree Lights", "Xmas Tree", "Douglas Fir") -> CHRISTMAS_TREE
 * - Fans ("Ceiling Fan", "Bedroom Fan", "Exhaust Fan", "Bath Vent", "Blower") -> FAN
 * - Lamps & Lights ("Lamp", "Light", "Lightbulb", "Candle", "Sconce", "Floodlight", "LED", or Dimmer) -> LIGHT
 * - Motion sensors -> MOTION_SENSOR
 * - Everything else -> SWITCH_PLUG
 */
fun classifyDeviceCategory(
    name: String,
    deviceType: DeviceType = DeviceType.SWITCH_PLUG
): DeviceCategory {
    if (deviceType == DeviceType.MOTION_SENSOR) {
        return DeviceCategory.MOTION_SENSOR
    }
    val normalized = normalizeDeviceNameForInference(name)
    return when {
        RoomAndCategoryRules.matchesChristmasTree(normalized) -> DeviceCategory.CHRISTMAS_TREE
        RoomAndCategoryRules.matchesFan(normalized) -> DeviceCategory.FAN
        deviceType == DeviceType.LIGHTBULB_DIMMER || RoomAndCategoryRules.matchesLight(normalized) ->
            DeviceCategory.LIGHT
        RoomAndCategoryRules.matchesMotion(normalized) -> DeviceCategory.MOTION_SENSOR
        else -> DeviceCategory.SWITCH_PLUG
    }
}

/**
 * Normalizes a hardware `<friendlyName>` or user-supplied switch name for room/category inference
 * by splitting CamelCase & acronym boundaries (`"StairwayLight"` -> `"stairway light"`,
 * `"WCLight"` -> `"wc light"`) and converting `_`, `-`, `/`, `.` to spaces.
 */
fun normalizeDeviceNameForInference(deviceName: String): String {
    if (deviceName.isBlank()) return ""
    return deviceName
        .replace(RoomAndCategoryRules.ACRONYM_CAMEL_BOUNDARY_REGEX, "$1 $2")
        .replace(RoomAndCategoryRules.CAMEL_CASE_BOUNDARY_REGEX, "$1 $2")
        .replace(RoomAndCategoryRules.DELIMITER_NORMALIZE_REGEX, " ")
        .lowercase(Locale.US)
        .replace(RoomAndCategoryRules.MULTI_SPACE_REGEX, " ")
        .trim()
}

/**
 * Infers the best household room assignment from a switch's [deviceName] (`<friendlyName>`).
 *
 * Precedence Order:
 * 1. Specific functional rooms (`"Kitchen"`, `"Dining Room"`, `"Ensuite"`) are evaluated **before**
 *    broad modifiers (`"master"`, `"primary"`, `"guest"`, `"kids"`) so compound names like
 *    `"Master Bath Vanity"`, `"Guest Bathroom Light"`, or `"Kids Bath Fan"` resolve to `"Ensuite"`.
 * 2. Circulation & entry spaces (`"Hallway & Landing"`) match word-prefixed `"stair*"` (`"stair"`,
 *    `"stairs"`, `"stairway"`, `"staircase"`), `"landing"`, `"foyer"`, `"entry"`, `"mudroom"`,
 *    `"corridor"`, and word-bounded `"hall"` — while floor modifiers (`"upstairs"`, `"downstairs"`)
 *    fall back to `"Hallway & Landing"` only when no specific room (e.g., `"Upstairs Bedroom"`) matches.
 * 3. Sleeping, exterior, utility, workspace, and living spaces (`"Bedroom"`, `"Outdoor"` with `\bpier\b`,
 *    `"Garage"` with `\bshed\b`, `"Basement"`, `"Office"`, `"Living Room"` with `\bden\b`).
 * 4. Unrecognized names default to `"Unassigned"` (never `"Living Room"`).
 */
fun inferRoomFromName(deviceName: String): String {
    val normalized = normalizeDeviceNameForInference(deviceName)
    if (normalized.isEmpty()) return UNASSIGNED_ROOM
    return RoomAndCategoryRules.ROOM_INFERENCE_RULES
        .firstOrNull { it.matches(normalized) }?.roomName ?: UNASSIGNED_ROOM
}

/**
 * Resolves the room assignment for an existing or newly discovered device:
 * - Preserves intentional user-assigned rooms (e.g., `"Porch & Entryway"` or `"Kids Suite"`).
 * - In Real LAN mode (`!isDemoMode`), automatically migrates devices whose `existingRoomName`
 *   is blank, `"Unassigned"`, or stuck in legacy `"Living Room"` (when `inferredRoom != "Living Room"`).
 */
fun resolveDeviceRoomAssignment(
    existingRoomName: String,
    deviceName: String,
    discoveredRoomName: String = "",
    isDemoMode: Boolean = false
): String {
    val cleanDiscovered = discoveredRoomName.trim()
    val inferredRoom = cleanDiscovered.takeIf {
        it.isNotEmpty() && !it.equals(UNASSIGNED_ROOM, ignoreCase = true)
    } ?: inferRoomFromName(deviceName)

    val cleanExisting = existingRoomName.trim()
    if (cleanExisting.isEmpty()) return inferredRoom
    if (isDemoMode) return normalizeRoomName(cleanExisting)

    val isUnassignedPlaceholder = cleanExisting.equals(UNASSIGNED_ROOM, ignoreCase = true)
    val isLegacyLivingRoomFallback = cleanExisting.equals("Living Room", ignoreCase = true) &&
        inferredRoom != "Living Room"

    return if (isUnassignedPlaceholder || isLegacyLivingRoomFallback) {
        inferredRoom
    } else {
        cleanExisting
    }
}

