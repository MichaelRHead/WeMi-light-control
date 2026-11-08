package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

const val DEFAULT_MAIN_HOME_KEY = "wifi_home_192_168_1"
const val DEFAULT_CABIN_HOME_KEY = "wifi_cabin_192_168_50"
const val DEFAULT_OFFICE_HOME_KEY = "wifi_office_10_0_10"

enum class WeMoDeviceType {
    SWITCH_PLUG,
    MOTION_SENSOR,
    LIGHTBULB_DIMMER
}

enum class WeMoConnectionQuality(
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
data class WeMoHomeNetworkEntity(
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
data class WeMoDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val roomName: String = "Living Room",
    val networkKey: String = DEFAULT_MAIN_HOME_KEY,
    val wifiSsid: String = "WeMi-Home-5G",
    val deviceType: String = WeMoDeviceType.SWITCH_PLUG.name,
    val ipAddress: String = "192.168.1.100",
    val port: Int = 49153,
    val isOn: Boolean = true,
    val isReachable: Boolean = true,
    val brightness: Int = 100,
    val latencyMs: Int = 14,
    val lastCheckedTime: String = "Just now",
    val lastMotionTime: String = "4:15:15 PM",
    val serialNumber: String = "221517K01018AB",
    val firmwareVersion: String = "WeMo_WW_2.00.11532.PVT",
    val hardwareModel: String = "LightSwitch",
    val macAddress: String = "",
    val udn: String = "",
    val signalStrengthPercent: Int = 85,
    val nightModeEnabled: Boolean = false,
    val sortOrder: Int = 0,
    val autoOffMinutes: Int = 0
) {
    val parsedType: WeMoDeviceType
        get() = runCatching { WeMoDeviceType.valueOf(deviceType) }
            .getOrDefault(WeMoDeviceType.SWITCH_PLUG)

    val connectionQuality: WeMoConnectionQuality
        get() = when {
            !isReachable || latencyMs <= 0 -> WeMoConnectionQuality.OFFLINE
            latencyMs <= 28 -> WeMoConnectionQuality.EXCELLENT
            latencyMs <= 65 -> WeMoConnectionQuality.GOOD
            else -> WeMoConnectionQuality.SLOW
        }

    val connectionBadgeText: String
        get() = when (connectionQuality) {
            WeMoConnectionQuality.OFFLINE -> "Offline • Timeout"
            WeMoConnectionQuality.EXCELLENT -> "Active • ${latencyMs}ms"
            WeMoConnectionQuality.GOOD -> "Active • ${latencyMs}ms"
            WeMoConnectionQuality.SLOW -> "Slow • ${latencyMs}ms"
        }
}

@Entity(tableName = "wemo_rules")
data class WeMoRuleEntity(
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
)

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
    val deviceType: WeMoDeviceType,
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
)

fun inferRoomFromName(deviceName: String): String {
    val lower = deviceName.lowercase()
    return when {
        "kitchen" in lower || "pantry" in lower || "island" in lower -> "Kitchen"
        "dining" in lower -> "Dining Room"
        "bedroom" in lower || "loft" in lower || "master" in lower || "nursery" in lower || "guest" in lower -> "Bedroom"
        "ensuite" in lower || "bath" in lower || "powder" in lower || "vanity" in lower -> "Ensuite"
        "hall" in lower || "landing" in lower || "stairs" in lower || "foyer" in lower || "entry" in lower -> "Hallway & Landing"
        "patio" in lower || "porch" in lower || "outdoor" in lower || "garden" in lower || "dock" in lower || "yard" in lower || "deck" in lower || "flood" in lower -> "Outdoor"
        "garage" in lower || "workshop" in lower || "driveway" in lower -> "Garage"
        "basement" in lower || "cellar" in lower -> "Basement"
        "office" in lower || "study" in lower || "desk" in lower || "studio" in lower -> "Office"
        "living" in lower || "family" in lower || "den" in lower || "lounge" in lower -> "Living Room"
        else -> "Living Room"
    }
}
