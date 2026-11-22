package org.suppressingfire.wemi.network

import java.util.Locale

/**
 * Two-tier capability state for a smart switch's native on-device `rules.db` engine
 * (`urn:Belkin:service:rules:1#FetchRules` + `#StoreRules`):
 * - Tier 1 (Zero-RTT during SSDP/unicast scan): inferred directly from `/setup.xml` without
 *   extra HTTP requests ([LIKELY_SUPPORTED] / [LIKELY_UNSUPPORTED] / [UNKNOWN]).
 * - Tier 2 (Authoritative lazy probe, cached per `firmwareVersion`): confirmed via
 *   `GET /rulesservice.xml` + `POST rules1#FetchRules` ([VERIFIED_SUPPORTED] / [VERIFIED_UNSUPPORTED]).
 */
enum class HardwareRulesCapability(val displayLabel: String) {
    UNKNOWN("Not Checked"),
    LIKELY_SUPPORTED("Likely Supported"),
    LIKELY_UNSUPPORTED("Likely Unsupported (RTOS)"),
    VERIFIED_SUPPORTED("On-Device Ready"),
    VERIFIED_UNSUPPORTED("No On-Device Rules");

    val isVerified: Boolean
        get() = this == VERIFIED_SUPPORTED || this == VERIFIED_UNSUPPORTED

    val isSupportedOrLikely: Boolean
        get() = this == VERIFIED_SUPPORTED || this == LIKELY_SUPPORTED

    val isUnsupportedOrLikely: Boolean
        get() = this == VERIFIED_UNSUPPORTED || this == LIKELY_UNSUPPORTED

    companion object {
        fun fromRaw(raw: String?): HardwareRulesCapability =
            entries.firstOrNull { it.name.equals(raw?.trim().orEmpty(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * Zero-RTT capability hint extracted directly from `/setup.xml` during discovery.
 */
data class SetupXmlRulesHint(
    val capability: HardwareRulesCapability,
    val longPressCapable: Boolean,
    val hasRulesService: Boolean,
    val hasTimeSyncService: Boolean,
    val rulesControlUrl: String = "/upnp/control/rules1",
    val rulesScpdUrl: String = "/rulesservice.xml"
)

/**
 * Native firmware rule types stored in `RULES.Type` inside `temppluginRules.db`.
 */
enum class HardwareRuleType(val firmwareType: String, val displayLabel: String) {
    LONG_PRESS("Long Press", "Long Press"),
    COUNTDOWN_TIMER("Countdown Rule", "Auto-Off Delay"),
    SCHEDULE_INTERVAL("Time Interval", "Schedule"),
    SIMPLE_TIMER("Simple Switch", "Timed Action"),
    AWAY_MODE("Away Mode", "Away Mode"),
    SENSOR_TRIGGER("Motion Controlled", "Motion / Sensor"),
    MAKER_SENSOR("Maker Sensor Rule", "Sensor Rule"),
    INSIGHT_RULE("Insight Rule", "Power Rule"),
    NOTIFY_ME("Notify Me", "Notification"),
    OTHER("Other", "Hardware Rule");

    companion object {
        fun fromFirmwareType(raw: String?): HardwareRuleType {
            val clean = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it != OTHER && it.firmwareType.equals(clean, ignoreCase = true)
            } ?: when {
                clean.contains("long", ignoreCase = true) -> LONG_PRESS
                clean.contains("countdown", ignoreCase = true) -> COUNTDOWN_TIMER
                clean.contains("interval", ignoreCase = true) -> SCHEDULE_INTERVAL
                clean.contains("simple", ignoreCase = true) -> SIMPLE_TIMER
                clean.contains("away", ignoreCase = true) -> AWAY_MODE
                clean.contains("maker", ignoreCase = true) -> MAKER_SENSOR
                clean.contains("motion", ignoreCase = true) || clean.contains("sensor", ignoreCase = true) ->
                    SENSOR_TRIGGER
                clean.contains("insight", ignoreCase = true) -> INSIGHT_RULE
                clean.contains("notify", ignoreCase = true) -> NOTIFY_ME
                else -> OTHER
            }
        }
    }
}

enum class HardwareLongPressAction(val code: Int, val label: String) {
    TURN_OFF(0, "Turn Off"),
    TURN_ON(1, "Turn On"),
    TOGGLE(2, "Toggle");

    companion object {
        fun fromActionDouble(value: Double?): HardwareLongPressAction = when (value?.toInt()) {
            0 -> TURN_OFF
            1 -> TURN_ON
            else -> TOGGLE
        }
    }
}

/**
 * Encodes a time point inside Belkin's `RULEDEVICES` table:
 * - Fixed wall-clock time: `secondsFromMidnight % 10 == 0` (usually multiples of 60).
 * - On-device Sunrise (`sunriset.c`): `rawSeconds % 10 == 1`, shifted by [offsetSeconds].
 * - On-device Sunset (`sunriset.c`): `rawSeconds % 10 == 2`, shifted by [offsetSeconds].
 */
data class HardwareRuleTimePoint(
    val rawSeconds: Int,
    val offsetSeconds: Int = 0
) {
    val isSunrise: Boolean
        get() = rawSeconds > 0 && rawSeconds % 10 == 1

    val isSunset: Boolean
        get() = rawSeconds > 0 && rawSeconds % 10 == 2

    fun formatHuman(): String {
        if (isSunrise || isSunset) {
            val anchor = if (isSunrise) "Sunrise" else "Sunset"
            val offsetMin = offsetSeconds / 60
            return when {
                offsetMin > 0 -> "$anchor +${offsetMin}m"
                offsetMin < 0 -> "$anchor ${offsetMin}m"
                else -> anchor
            }
        }
        val clamped = rawSeconds.coerceIn(0, 86400)
        val totalMinutes = clamped / 60
        val hour24 = (totalMinutes / 60) % 24
        val minute = totalMinutes % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, amPm)
    }
}

data class HardwareLocationInfo(
    val cityName: String,
    val region: String,
    val countryName: String,
    val latitude: String,
    val longitude: String
) {
    fun formatSummary(): String =
        listOf(cityName, region, countryName).filter { it.isNotBlank() }.joinToString(", ")
}

data class HardwareDeviceTimeInfo(
    val deviceUtcSeconds: Long,
    val driftSeconds: Long,
    val needsTimeSync: Boolean
)

/**
 * Parsed native rule row stored inside a switch's `rules.db` SQLite archive.
 */
data class HardwareRule(
    val ruleId: Int,
    val name: String,
    val type: HardwareRuleType,
    val rawFirmwareType: String,
    val isEnabled: Boolean,
    val ruleOrder: Int,
    val ownerDeviceUdn: String,
    val daysOfWeek: List<Int>, // Belkin DayID 1=Sun .. 7=Sat; empty or [-1] = every day
    val startTime: HardwareRuleTimePoint?,
    val endTime: HardwareRuleTimePoint?,
    val ruleDurationSeconds: Int,
    val startAction: Double?,
    val endAction: Double?,
    val countdownSeconds: Int,
    val sensorDurationSeconds: Int,
    val targetDeviceUdns: List<String> = emptyList(),
    val sensorDeviceUdns: List<String> = emptyList(),
    val appliesToInspectedSwitch: Boolean = true
) {
    fun formatDaysSummary(): String {
        val validDays = daysOfWeek.filter { it in 1..7 }.distinct().sorted()
        if (validDays.isEmpty() || validDays.size == 7) return "Every day"
        if (validDays == listOf(2, 3, 4, 5, 6)) return "Weekdays"
        if (validDays == listOf(1, 7)) return "Weekends"
        val labels = mapOf(1 to "Sun", 2 to "Mon", 3 to "Tue", 4 to "Wed", 5 to "Thu", 6 to "Fri", 7 to "Sat")
        return validDays.mapNotNull { labels[it] }.joinToString(", ")
    }

    fun formatDurationMinutes(seconds: Int): String {
        if (seconds <= 0) return "0 min"
        val mins = seconds / 60
        val hrs = mins / 60
        val remMins = mins % 60
        return when {
            hrs > 0 && remMins > 0 -> "${hrs}h ${remMins}m"
            hrs > 0 -> "${hrs}h"
            mins > 0 -> "$mins min"
            else -> "${seconds}s"
        }
    }

    /**
     * Produces a clear human-readable trigger + action description, mapping target hardware UDNs
     * to known friendly names from [udnToFriendlyName] when available.
     */
    fun formatTriggerAndAction(udnToFriendlyName: Map<String, String> = emptyMap()): Pair<String, String> {
        val resolvedTargets = targetDeviceUdns.map { rawUdn ->
            val clean = LanRuntimeUtils.canonicalHardwareUdn(rawUdn)
            udnToFriendlyName[clean.lowercase(Locale.US)]
                ?: udnToFriendlyName[rawUdn.trim().lowercase(Locale.US)]
                ?: clean.substringAfterLast('-').ifBlank { rawUdn }
        }
        return when (type) {
            HardwareRuleType.LONG_PRESS -> {
                val action = HardwareLongPressAction.fromActionDouble(startAction)
                val targetLabel = if (resolvedTargets.isEmpty()) {
                    "No target switches configured"
                } else {
                    resolvedTargets.joinToString(", ")
                }
                "Hold wall paddle for 2 seconds" to "${action.label}: $targetLabel"
            }
            HardwareRuleType.COUNTDOWN_TIMER -> {
                val dur = formatDurationMinutes(countdownSeconds)
                val window = if (startTime != null && ruleDurationSeconds in 1..86339 && endTime != null) {
                    " (${startTime.formatHuman()} – ${endTime.formatHuman()})"
                } else {
                    ""
                }
                "${formatDaysSummary()}$window" to "Auto-Off after $dur ON"
            }
            HardwareRuleType.SCHEDULE_INTERVAL -> {
                val startStr = startTime?.formatHuman() ?: "Start"
                val endStr = endTime?.formatHuman()
                val startAct = formatBinaryAction(startAction)
                val endAct = formatBinaryAction(endAction)
                val trigger = if (endStr != null) "${formatDaysSummary()} • $startStr – $endStr" else "${formatDaysSummary()} • $startStr"
                val action = if (endAct != null) "$startAct, then $endAct" else (startAct ?: "Scheduled state")
                trigger to action
            }
            HardwareRuleType.SIMPLE_TIMER -> {
                val startStr = startTime?.formatHuman() ?: "Scheduled time"
                "${formatDaysSummary()} at $startStr" to (formatBinaryAction(startAction) ?: "Toggle")
            }
            HardwareRuleType.AWAY_MODE -> {
                val startStr = startTime?.formatHuman() ?: "Evening"
                val endStr = endTime?.formatHuman() ?: "Night"
                val extra = if (resolvedTargets.isNotEmpty()) " (${resolvedTargets.size} switches)" else ""
                "${formatDaysSummary()} • $startStr – $endStr" to "Randomized occupancy simulation$extra"
            }
            HardwareRuleType.SENSOR_TRIGGER, HardwareRuleType.MAKER_SENSOR -> {
                val hold = if (sensorDurationSeconds > 0) " • hold ${formatDurationMinutes(sensorDurationSeconds)}" else ""
                val sensorNames = sensorDeviceUdns.map { raw ->
                    val clean = LanRuntimeUtils.canonicalHardwareUdn(raw)
                    udnToFriendlyName[clean.lowercase(Locale.US)] ?: clean.substringAfterLast('-').ifBlank { raw }
                }.joinToString(", ").ifBlank { "Paired sensor" }
                "When $sensorNames triggers$hold" to (formatBinaryAction(startAction) ?: "Turn ON")
            }
            else -> {
                "${formatDaysSummary()} ($rawFirmwareType)" to (formatBinaryAction(startAction) ?: "Hardware rule")
            }
        }
    }

    private fun formatBinaryAction(value: Double?): String? = when (value?.toInt()) {
        0 -> "Turn OFF"
        1 -> "Turn ON"
        2 -> "Toggle"
        else -> null
    }
}

/**
 * Validation verdict produced before packing or uploading any `rules.db` archive.
 */
data class HardwareRulesValidationResult(
    val isSafeToStore: Boolean,
    val issues: List<String> = emptyList(),
    val tableNames: Set<String> = emptySet(),
    val integrityCheckResult: String = ""
)

/**
 * Complete read-only inspection result for a single switch's on-device rules engine.
 */
data class DeviceHardwareRulesSnapshot(
    val deviceId: Int,
    val deviceName: String,
    val deviceUdn: String,
    val capability: HardwareRulesCapability,
    val longPressCapable: Boolean,
    val dbVersion: Int,
    val zipEntryName: String = "temppluginRules.db",
    val rules: List<HardwareRule> = emptyList(),
    val locationInfo: HardwareLocationInfo? = null,
    val deviceTimeInfo: HardwareDeviceTimeInfo? = null,
    val existingTables: Set<String> = emptySet(),
    val integrityOk: Boolean = true,
    val backupFilePath: String? = null,
    val diagnosticMessage: String = "",
    val inspectedAt: String = ""
) {
    val activeRuleCount: Int
        get() = rules.count { it.isEnabled }
}
