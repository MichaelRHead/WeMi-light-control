package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class WeMoDeviceType {
    SWITCH_PLUG,
    MOTION_SENSOR,
    LIGHTBULB_DIMMER
}

@Entity(tableName = "wemo_devices")
data class WeMoDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val deviceType: String = WeMoDeviceType.SWITCH_PLUG.name,
    val ipAddress: String = "192.168.1.100",
    val port: Int = 49153,
    val isOn: Boolean = true,
    val isReachable: Boolean = true,
    val brightness: Int = 100,
    val lastMotionTime: String = "4:15:15 PM",
    val serialNumber: String = "221517K01018AB",
    val firmwareVersion: String = "WeMo_WW_2.00.11532.PVT",
    val sortOrder: Int = 0,
    val autoOffMinutes: Int = 0
) {
    val parsedType: WeMoDeviceType
        get() = runCatching { WeMoDeviceType.valueOf(deviceType) }
            .getOrDefault(WeMoDeviceType.SWITCH_PLUG)
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
    val isLiveUdpResponse: Boolean = false
)
