package org.suppressingfire.wemi.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.suppressingfire.wemi.network.LanRuntimeUtils

const val DEFAULT_MAIN_HOME_KEY = "wifi_home_192_168_1"
const val DEFAULT_CABIN_HOME_KEY = "wifi_cabin_192_168_50"
const val DEFAULT_OFFICE_HOME_KEY = "wifi_office_10_0_10"
const val UNASSIGNED_ROOM = "Unassigned"

/** Canonicalizes a room name so blank or whitespace-only values consistently map to [UNASSIGNED_ROOM]. */
fun normalizeRoomName(rawRoom: String?): String =
    rawRoom?.trim().orEmpty().ifBlank { UNASSIGNED_ROOM }

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
    val autoOffMinutes: Int = 0
) {
    val parsedType: DeviceType
        get() = runCatching { DeviceType.valueOf(deviceType) }
            .getOrDefault(DeviceType.SWITCH_PLUG)

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

enum class RuleType {
    SCHEDULE,
    MOTION_TRIGGER,
    AUTO_OFF;

    companion object {
        fun fromRaw(raw: String): RuleType {
            val normalized = raw.trim().uppercase()
            return when {
                normalized.startsWith("AUTO_OFF") -> AUTO_OFF
                normalized.startsWith("MOTION") -> MOTION_TRIGGER
                else -> SCHEDULE
            }
        }
    }
}

@Entity(tableName = "wemo_rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val ruleType: String, // SCHEDULE, MOTION_TRIGGER, AUTO_OFF
    val targetDeviceId: Int,
    val targetDeviceName: String,
    val triggerSummary: String,
    val actionSummary: String,
    val targetStateOn: Boolean = true,
    val targetBrightness: Int = 100,
    val isEnabled: Boolean = true
) {
    val parsedRuleType: RuleType
        get() = RuleType.fromRaw(ruleType)
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
    val signalStrengthPercent: Int = 85
) {
    val visualCategory: DeviceCategory
        get() = classifyDeviceCategory(name = name, deviceType = deviceType)

    fun matchesEndpointOrSerial(other: SsdpDiscoveredDevice): Boolean {
        val bothRealSerials = !LanRuntimeUtils.isPlaceholderSerial(serialNumber) &&
            !LanRuntimeUtils.isPlaceholderSerial(other.serialNumber)
        return (bothRealSerials && serialNumber.equals(other.serialNumber, ignoreCase = true)) ||
            (ipAddress == other.ipAddress && port == other.port)
    }
}

private val CHRISTMAS_TREE_KEYWORDS = listOf(
    "tree", "xmas", "evergreen", "spruce", "fir ", "garland", "wreath", "tinsel", "noel"
)
private val CHRISTMAS_LIGHT_EXCLUSIONS = listOf("candle", "lamp", "sconce", "lantern")
private val FAN_KEYWORDS = listOf("fan", "blower", "vent", "exhaust", "ventilat", "circulator")
private val LIGHT_KEYWORDS = listOf(
    "lamp", "light", "bulb", "candle", "sconce", "chandelier", "lantern", "pendant",
    "flood", "spot", "downlight", "uplight", "strip", "neon", "torch", "dimmer", "led", "fixture"
)
private val MOTION_KEYWORDS = listOf("motion", "sensor")

/**
 * Classifies a switch's visual device category based on its friendly name and UPnP device type:
 * - Tree lights ("Christmas Tree", "Tree Lights", "Xmas Tree", "Pine Tree") -> CHRISTMAS_TREE
 * - Fans ("Ceiling Fan", "Bedroom Fan", "Exhaust Fan", "Blower") -> FAN
 * - Lamps & Lights ("Lamp", "Light", "Lightbulb", "Candle", "Sconce", "Floodlight", "Lantern", or Dimmer) -> LIGHT
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
    val lower = name.lowercase()
    return when {
        CHRISTMAS_TREE_KEYWORDS.any { it in lower } ||
            ("christmas" in lower && CHRISTMAS_LIGHT_EXCLUSIONS.none { it in lower }) ->
            DeviceCategory.CHRISTMAS_TREE

        FAN_KEYWORDS.any { it in lower } -> DeviceCategory.FAN

        deviceType == DeviceType.LIGHTBULB_DIMMER || LIGHT_KEYWORDS.any { it in lower } ->
            DeviceCategory.LIGHT

        MOTION_KEYWORDS.any { it in lower } -> DeviceCategory.MOTION_SENSOR

        else -> DeviceCategory.SWITCH_PLUG
    }
}

private val CAMEL_CASE_BOUNDARY_REGEX = Regex("([a-z])([A-Z])")
private val DELIMITER_NORMALIZE_REGEX = Regex("[_\\-/.]+")
private val MULTI_SPACE_REGEX = Regex("\\s+")
private val WORD_BED_REGEX = Regex("\\bbed\\b")
private val WORD_GIRL_REGEX = Regex("\\bgirl\\b")
private val WORD_BOY_REGEX = Regex("\\bboy\\b")
private val WORD_KID_REGEX = Regex("\\bkid\\b")
private val WORD_HALL_REGEX = Regex("\\bhall\\b")
private val WORD_DEN_REGEX = Regex("\\bden\\b")
private val WORD_WC_REGEX = Regex("\\bwc\\b")

private data class RoomInferenceRule(
    val roomName: String,
    val keywords: List<String>,
    val wordRegexes: List<Regex> = emptyList()
) {
    fun matches(normalized: String): Boolean =
        keywords.any { it in normalized } || wordRegexes.any { it.containsMatchIn(normalized) }
}

private val ROOM_INFERENCE_RULES = listOf(
    RoomInferenceRule(
        roomName = "Kitchen",
        keywords = listOf(
            "kitchen", "pantry", "island", "breakfast", "nook",
            "undercabinet", "under cabinet", "countertop"
        )
    ),
    RoomInferenceRule(
        roomName = "Dining Room",
        keywords = listOf("dining", "buffet", "hutch", "china cabinet")
    ),
    RoomInferenceRule(
        roomName = "Ensuite",
        keywords = listOf(
            "ensuite", "en suite", "bath", "powder", "vanity", "restroom", "lavatory", "shower"
        ),
        wordRegexes = listOf(WORD_WC_REGEX)
    ),
    RoomInferenceRule(
        roomName = "Hallway & Landing",
        keywords = listOf(
            "hallway", "landing", "stair", "foyer", "entry",
            "vestibule", "mudroom", "mud room", "corridor", "breezeway"
        ),
        wordRegexes = listOf(WORD_HALL_REGEX)
    ),
    RoomInferenceRule(
        roomName = "Bedroom",
        keywords = listOf(
            "bedroom", "bedside", "nightstand", "night stand", "headboard", "dresser", "wardrobe",
            "crib", "cradle", "loft", "master", "primary", "nursery", "guest", "bunk", "dorm",
            "girls", "boys", "kids", "children"
        ),
        wordRegexes = listOf(WORD_BED_REGEX, WORD_GIRL_REGEX, WORD_BOY_REGEX, WORD_KID_REGEX)
    ),
    RoomInferenceRule(
        roomName = "Outdoor",
        keywords = listOf(
            "patio", "porch", "veranda", "lanai", "outdoor", "outside", "exterior", "garden",
            "dock", "pier", "yard", "deck", "flood", "walkway", "sidewalk", "pathway", "balcony",
            "terrace", "gazebo", "pergola", "trellis", "pool", "fountain", "landscape"
        )
    ),
    RoomInferenceRule(
        roomName = "Garage",
        keywords = listOf("garage", "workshop", "workbench", "driveway", "carport", "shed", "barn")
    ),
    RoomInferenceRule(
        roomName = "Basement",
        keywords = listOf(
            "basement", "cellar", "laundry", "utility", "boiler", "furnace", "sump", "crawlspace",
            "rec room", "game room", "media room", "theater", "theatre"
        )
    ),
    RoomInferenceRule(
        roomName = "Office",
        keywords = listOf("office", "study", "desk", "workstation", "studio", "library")
    ),
    RoomInferenceRule(
        roomName = "Living Room",
        keywords = listOf(
            "living", "family", "lounge", "great room", "parlor", "parlour", "sitting",
            "sunroom", "sun room", "solarium", "mantel", "hearth", "fireplace", "couch", "sofa"
        ),
        wordRegexes = listOf(WORD_DEN_REGEX)
    )
)

/**
 * Normalizes a hardware `<friendlyName>` or user-supplied switch name for room/category inference
 * by splitting CamelCase boundaries (`"StairwayLight"` -> `"stairway light"`,
 * `"MasterBathVanity"` -> `"master bath vanity"`) and converting `_`, `-`, `/`, `.` to spaces.
 */
fun normalizeDeviceNameForInference(deviceName: String): String {
    if (deviceName.isBlank()) return ""
    return deviceName
        .replace(CAMEL_CASE_BOUNDARY_REGEX, "$1 $2")
        .replace(DELIMITER_NORMALIZE_REGEX, " ")
        .lowercase()
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()
}

/**
 * Infers the best household room assignment from a switch's [deviceName] (`<friendlyName>`).
 *
 * Precedence Order:
 * 1. Specific functional rooms (`"Kitchen"`, `"Dining Room"`, `"Ensuite"`) are evaluated **before**
 *    broad modifiers (`"master"`, `"primary"`, `"guest"`, `"kids"`) so compound names like
 *    `"Master Bath Vanity"`, `"Guest Bathroom Light"`, or `"Kids Bath Fan"` resolve to `"Ensuite"`
 *    rather than `"Bedroom"`.
 * 2. Circulation & entry spaces (`"Hallway & Landing"`) match `"stair"` (`"stair"`, `"stairs"`,
 *    `"stairway"`, `"staircase"`, `"upstairs"`, `"downstairs"`), `"landing"`, `"foyer"`, `"entry"`,
 *    `"mudroom"`, `"corridor"`, and word-bounded `"hall"`.
 * 3. Sleeping spaces (`"Bedroom"`) match `"bedroom"`, `"bedside"`, `"nightstand"`, word-bounded `"bed"`,
 *    `"girls"`/`"girl"`, `"boys"`/`"boy"`, `"kids"`/`"kid"`, `"master"`, `"primary"`, `"guest"`, `"nursery"`, etc.
 * 4. Exterior, utility, workspace, and living spaces (`"Outdoor"`, `"Garage"`, `"Basement"`, `"Office"`,
 *    `"Living Room"` with word-bounded `"den"`).
 * 5. Unrecognized names default to `"Unassigned"` (never `"Living Room"`).
 */
fun inferRoomFromName(deviceName: String): String {
    val normalized = normalizeDeviceNameForInference(deviceName)
    if (normalized.isEmpty()) return UNASSIGNED_ROOM
    return ROOM_INFERENCE_RULES.firstOrNull { it.matches(normalized) }?.roomName ?: UNASSIGNED_ROOM
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
    val inferredRoom = discoveredRoomName.takeIf {
        it.isNotBlank() && !it.equals("Unassigned", ignoreCase = true)
    } ?: inferRoomFromName(deviceName)

    if (existingRoomName.isBlank()) return inferredRoom
    if (isDemoMode) return existingRoomName

    val isUnassignedPlaceholder = existingRoomName.equals("Unassigned", ignoreCase = true)
    val isLegacyLivingRoomFallback = existingRoomName.equals("Living Room", ignoreCase = true) &&
        inferredRoom != "Living Room"

    return if (isUnassignedPlaceholder || isLegacyLivingRoomFallback) {
        inferredRoom
    } else {
        existingRoomName
    }
}

