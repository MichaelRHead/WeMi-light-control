package org.suppressingfire.wemi.data

/**
 * Showcase Demo Showroom seed fixtures for WeMi Light Control:
 * - 9 standard room presets ([DEFAULT_ROOMS])
 * - 3 remembered Wi-Fi Home profiles ([defaultHomeNetworks])
 * - 14 showcase switches, dimmers, and motion sensors across those 3 homes ([defaultClassicDevices])
 * - 4 showcase schedule, motion-trigger, and auto-off rules ([defaultClassicRules])
 */
object DemoFixtures {

    val DEFAULT_ROOMS = listOf(
        "Living Room",
        "Bedroom",
        "Kitchen",
        "Dining Room",
        "Hallway & Landing",
        "Ensuite",
        "Outdoor",
        "Garage",
        "Basement",
        "Office",
        "Unassigned"
    )

    fun defaultHomeNetworks(): List<HomeNetworkEntity> = listOf(
        HomeNetworkEntity(
            networkKey = DEFAULT_MAIN_HOME_KEY,
            homeName = "Main Home",
            ssidLabel = "WeMi-Home-5G",
            subnetPrefix = "192.168.1",
            gatewayIp = "192.168.1.1",
            lastConnectedTime = "Connected now",
            autoReconnectOnArrival = true,
            sortOrder = 0
        ),
        HomeNetworkEntity(
            networkKey = DEFAULT_CABIN_HOME_KEY,
            homeName = "Lake Cabin",
            ssidLabel = "PineLake-Cabin-WiFi",
            subnetPrefix = "192.168.50",
            gatewayIp = "192.168.50.1",
            lastConnectedTime = "Remembered • 3 switches",
            autoReconnectOnArrival = true,
            sortOrder = 1
        ),
        HomeNetworkEntity(
            networkKey = DEFAULT_OFFICE_HOME_KEY,
            homeName = "Studio Office",
            ssidLabel = "Studio-Guest-LAN",
            subnetPrefix = "10.0.10",
            gatewayIp = "10.0.10.1",
            lastConnectedTime = "Remembered • 2 switches",
            autoReconnectOnArrival = true,
            sortOrder = 2
        )
    )

    fun defaultClassicDevices(): List<DeviceEntity> = listOf(
        // --- Main Home Wi-Fi (WeMi-Home-5G • 192.168.1.x) ---
        DeviceEntity(
            id = 1,
            name = "Christmas Tree Living",
            roomName = "Living Room",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.1.101",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 100,
            latencyMs = 11,
            lastCheckedTime = "Just now",
            serialNumber = "221517K01018A1",
            sortOrder = 0
        ),
        DeviceEntity(
            id = 2,
            name = "Christmas Candle",
            roomName = "Living Room",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.1.102",
            port = 49153,
            isOn = false,
            isReachable = true,
            brightness = 100,
            latencyMs = 14,
            lastCheckedTime = "Just now",
            serialNumber = "221517K01018A2",
            sortOrder = 1
        ),
        DeviceEntity(
            id = 3,
            name = "Bedroom Light Motion",
            roomName = "Bedroom",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.MOTION_SENSOR.name,
            ipAddress = "192.168.1.103",
            port = 49153,
            isOn = true,
            isReachable = true,
            latencyMs = 18,
            lastCheckedTime = "Just now",
            lastMotionTime = "4:15:15 PM",
            serialNumber = "221432M02004B9",
            sortOrder = 2
        ),
        DeviceEntity(
            id = 4,
            name = "Christmas Tree Kitchen",
            roomName = "Kitchen",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.1.104",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 100,
            latencyMs = 16,
            lastCheckedTime = "Just now",
            serialNumber = "221517K01018A4",
            sortOrder = 3
        ),
        DeviceEntity(
            id = 5,
            name = "Hall Lightbulb",
            roomName = "Hallway & Landing",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.1.105",
            port = 49153,
            isOn = false,
            isReachable = false,
            brightness = 100,
            latencyMs = 0,
            lastCheckedTime = "Timeout",
            serialNumber = "94103EA2B27801",
            sortOrder = 4
        ),
        DeviceEntity(
            id = 6,
            name = "Lightbulb Landing",
            roomName = "Hallway & Landing",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.1.106",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 21,
            latencyMs = 32,
            lastCheckedTime = "Just now",
            serialNumber = "94103EA2B27802",
            sortOrder = 5
        ),
        DeviceEntity(
            id = 7,
            name = "Lightbulb Ensuite",
            roomName = "Ensuite",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.1.107",
            port = 49153,
            isOn = false,
            isReachable = true,
            brightness = 60,
            latencyMs = 15,
            lastCheckedTime = "Just now",
            serialNumber = "94103EA2B27803",
            sortOrder = 6
        ),
        DeviceEntity(
            id = 8,
            name = "Lightbulb Bedroom",
            roomName = "Bedroom",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.1.108",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 100,
            latencyMs = 12,
            lastCheckedTime = "Just now",
            serialNumber = "94103EA2B27804",
            sortOrder = 7
        ),
        DeviceEntity(
            id = 14,
            name = "Bedroom Ceiling Fan",
            roomName = "Bedroom",
            networkKey = DEFAULT_MAIN_HOME_KEY,
            wifiSsid = "WeMi-Home-5G",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.1.109",
            port = 49153,
            isOn = false,
            isReachable = true,
            brightness = 100,
            latencyMs = 13,
            lastCheckedTime = "Just now",
            serialNumber = "221517K01018A9",
            sortOrder = 8
        ),

        // --- Remembered Devices on Lake Cabin Wi-Fi (PineLake-Cabin-WiFi • 192.168.50.x) ---
        DeviceEntity(
            id = 9,
            name = "Cabin Porch Floodlight",
            roomName = "Outdoor",
            networkKey = DEFAULT_CABIN_HOME_KEY,
            wifiSsid = "PineLake-Cabin-WiFi",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.50.101",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 100,
            latencyMs = 19,
            lastCheckedTime = "Remembered",
            serialNumber = "221517K01050C1",
            sortOrder = 8
        ),
        DeviceEntity(
            id = 10,
            name = "Dock String Lights",
            roomName = "Outdoor",
            networkKey = DEFAULT_CABIN_HOME_KEY,
            wifiSsid = "PineLake-Cabin-WiFi",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "192.168.50.102",
            port = 49153,
            isOn = false,
            isReachable = false,
            brightness = 100,
            latencyMs = 0,
            lastCheckedTime = "Tap Reconnect",
            serialNumber = "221517K01050C2",
            sortOrder = 9
        ),
        DeviceEntity(
            id = 11,
            name = "Loft Dimmer Bulb",
            roomName = "Bedroom",
            networkKey = DEFAULT_CABIN_HOME_KEY,
            wifiSsid = "PineLake-Cabin-WiFi",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "192.168.50.103",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 65,
            latencyMs = 23,
            lastCheckedTime = "Remembered",
            serialNumber = "94103EA2B250C3",
            sortOrder = 10
        ),

        // --- Remembered Devices on Studio Office Wi-Fi (Studio-Guest-LAN • 10.0.10.x) ---
        DeviceEntity(
            id = 12,
            name = "Desk Key Light",
            roomName = "Office",
            networkKey = DEFAULT_OFFICE_HOME_KEY,
            wifiSsid = "Studio-Guest-LAN",
            deviceType = DeviceType.LIGHTBULB_DIMMER.name,
            ipAddress = "10.0.10.42",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 85,
            latencyMs = 10,
            lastCheckedTime = "Remembered",
            serialNumber = "94103EA2B210F1",
            sortOrder = 11
        ),
        DeviceEntity(
            id = 13,
            name = "Studio Signage Plug",
            roomName = "Office",
            networkKey = DEFAULT_OFFICE_HOME_KEY,
            wifiSsid = "Studio-Guest-LAN",
            deviceType = DeviceType.SWITCH_PLUG.name,
            ipAddress = "10.0.10.43",
            port = 49153,
            isOn = true,
            isReachable = true,
            brightness = 100,
            latencyMs = 14,
            lastCheckedTime = "Remembered",
            serialNumber = "221517K01010F2",
            sortOrder = 12
        )
    )

    fun defaultClassicRules(): List<RuleEntity> = listOf(
        RuleEntity(
            id = 1,
            name = "Evening Holiday Lights",
            ruleType = "SCHEDULE",
            targetDeviceId = 1,
            targetDeviceName = "Christmas Tree Living",
            triggerSummary = "Daily at Sunset (5:15 PM)",
            actionSummary = "Turn ON Christmas Tree Living",
            targetStateOn = true,
            targetBrightness = 100,
            isEnabled = true
        ),
        RuleEntity(
            id = 2,
            name = "Bedroom Motion Nightlight",
            ruleType = "MOTION_TRIGGER",
            targetDeviceId = 8,
            targetDeviceName = "Lightbulb Bedroom",
            triggerSummary = "When Bedroom Light Motion senses movement",
            actionSummary = "Turn ON Lightbulb Bedroom (35%)",
            targetStateOn = true,
            targetBrightness = 35,
            isEnabled = true
        ),
        RuleEntity(
            id = 3,
            name = "Landing Night Dimmer",
            ruleType = "SCHEDULE",
            targetDeviceId = 6,
            targetDeviceName = "Lightbulb Landing",
            triggerSummary = "Every night at 10:00 PM",
            actionSummary = "Dim Lightbulb Landing to 21%",
            targetStateOn = true,
            targetBrightness = 21,
            isEnabled = true
        ),
        RuleEntity(
            id = 4,
            name = "Midnight Candle Auto-Off",
            ruleType = "AUTO_OFF",
            targetDeviceId = 2,
            targetDeviceName = "Christmas Candle",
            triggerSummary = "Daily at 11:59 PM",
            actionSummary = "Turn OFF Christmas Candle",
            targetStateOn = false,
            targetBrightness = 0,
            isEnabled = true
        )
    )
}
