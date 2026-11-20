package org.suppressingfire.wemi.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.XmlAndSkuParser

/**
 * Sanitizes persisted Real-LAN Wi-Fi Home profiles and device records (clearing legacy soft-AP
 * `WeMo.*` / `Wemo.LS3W.*` SSIDs and non-destructively re-inferring legacy `"Living Room"` room
 * assignments), resolves zero-permission `/24` subnet Home profiles, synchronizes router SSID
 * labels reported by `GetMetaInfo`, and executes mutex-serialized SSDP device adoption on behalf
 * of [DiscoveryCoordinator].
 */
internal class HomeProfileSanitizer(
    private val dao: SwitchDao,
    private val isDemoShowroomMode: () -> Boolean,
    private val nowFormatted: () -> String,
    private val findMatchingExistingDevice: (
        existingDevices: List<DeviceEntity>,
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String
    ) -> DeviceEntity?
) {
    private val adoptMutex = Mutex()

    suspend fun adoptDiscoveredDevice(
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): DeviceEntity = adoptDiscoveredDeviceWithStatus(discovered, activeNetworkKey, activeWifiSsid).first

    private suspend fun adoptDiscoveredDeviceWithStatus(
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String,
        activeWifiSsid: String
    ): Pair<DeviceEntity, Boolean> = adoptMutex.withLock {
        val currentDevices = dao.getAllDevices().first()
        val existing = findMatchingExistingDevice(currentDevices, discovered, activeNetworkKey)

        val rawDeviceSsid = discovered.wifiSsid.trim()
        val validRouterSsid = rawDeviceSsid.takeIf {
            it.isNotEmpty() && !XmlAndSkuParser.isWemoDeviceSetupSsid(it)
        }
        val existingRouterSsid = existing?.wifiSsid?.trim()?.takeIf {
            it.isNotEmpty() &&
                !XmlAndSkuParser.isWemoDeviceSetupSsid(it) &&
                !it.startsWith("LAN ", ignoreCase = true) &&
                !it.startsWith("Local ", ignoreCase = true)
        }
        val resolvedSsid = validRouterSsid
            ?: existingRouterSsid
            ?: activeWifiSsid.takeIf { !XmlAndSkuParser.isWemoDeviceSetupSsid(it) }
            ?: "Local LAN"
        if (validRouterSsid != null) {
            syncHomeSsidFromDeviceSsid(activeNetworkKey, validRouterSsid)
        }
        val cleanDiscoveredSerial = discovered.serialNumber.takeIf {
            !LanRuntimeUtils.isPlaceholderSerial(it)
        }
        val cleanDiscoveredUdn = LanRuntimeUtils.canonicalHardwareUdn(discovered.usn)
        val checkedAt = nowFormatted()
        if (existing == null) {
            val nextSortOrder = (
                currentDevices
                    .filter { it.networkKey == activeNetworkKey }
                    .maxOfOrNull { it.sortOrder }
                    ?: -1
                ) + 1
            val newEntity = DeviceEntity(
                name = discovered.name,
                roomName = discovered.roomName.ifBlank { inferRoomFromName(discovered.name) },
                networkKey = activeNetworkKey,
                wifiSsid = resolvedSsid,
                deviceType = discovered.deviceType.name,
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                isOn = discovered.isOn,
                isReachable = true,
                brightness = discovered.brightness,
                latencyMs = discovered.responseTimeMs.toInt().coerceAtLeast(1),
                lastCheckedTime = checkedAt,
                serialNumber = cleanDiscoveredSerial.orEmpty(),
                firmwareVersion = discovered.firmwareVersion,
                hardwareModel = discovered.hardwareSku,
                macAddress = discovered.macAddress,
                udn = cleanDiscoveredUdn,
                signalStrengthPercent = discovered.signalStrengthPercent,
                sortOrder = nextSortOrder
            )
            val newId = dao.insertDevice(newEntity).toInt()
            newEntity.copy(id = newId) to true
        } else {
            val resolvedName = discovered.name.ifBlank { existing.name }
            val resolvedRoom = resolveDeviceRoomAssignment(
                existingRoomName = existing.roomName,
                deviceName = resolvedName,
                discoveredRoomName = discovered.roomName,
                isDemoMode = isDemoShowroomMode()
            )
            val updated = existing.copy(
                name = resolvedName,
                roomName = resolvedRoom,
                networkKey = activeNetworkKey,
                wifiSsid = resolvedSsid,
                ipAddress = discovered.ipAddress,
                port = discovered.port,
                isOn = discovered.isOn,
                brightness = discovered.brightness,
                isReachable = true,
                latencyMs = discovered.responseTimeMs.toInt().coerceAtLeast(1),
                lastCheckedTime = checkedAt,
                serialNumber = cleanDiscoveredSerial ?: existing.serialNumber,
                firmwareVersion = discovered.firmwareVersion.ifBlank { existing.firmwareVersion },
                hardwareModel = discovered.hardwareSku.ifBlank { existing.hardwareModel },
                macAddress = discovered.macAddress.ifBlank { existing.macAddress },
                udn = cleanDiscoveredUdn.ifBlank { LanRuntimeUtils.canonicalHardwareUdn(existing.udn) },
                signalStrengthPercent = discovered.signalStrengthPercent
            )
            dao.updateDevice(updated)
            updated to false
        }
    }

    suspend fun adoptDiscoveredDevices(
        discoveredList: List<SsdpDiscoveredDevice>,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): Int {
        var newlyAdded = 0
        for (item in discoveredList) {
            if (adoptDiscoveredDeviceWithStatus(item, activeNetworkKey, activeWifiSsid).second) {
                newlyAdded++
            }
        }
        return newlyAdded
    }

    /**
     * Sanitizes any persisted Wi-Fi Homes or devices whose names/SSIDs were polluted by Belkin
     * `GetMetaInfo` soft-AP SSIDs (`WeMo.*` / `Wemo.LS3W.*`) or stuck in legacy `"Living Room"`
     * room assignments, and resolves (or creates) the active Real LAN Home profile.
     */
    suspend fun sanitizeAndResolveInitialLanHome(localIp: String): String {
        val subnetPrefix = localIp.substringBeforeLast('.', "192.168.1")

        val rawHomes = dao.getAllHomeNetworks().first()
        val existingHomes = rawHomes.mapIndexed { idx, home ->
            val pollutedName = XmlAndSkuParser.isWemoDeviceSetupSsid(home.homeName) ||
                home.homeName.startsWith("Home Wi-Fi (", ignoreCase = true)
            val pollutedSsid = XmlAndSkuParser.isWemoDeviceSetupSsid(home.ssidLabel)
            if (pollutedName || pollutedSsid) {
                val repaired = home.copy(
                    homeName = if (pollutedName) {
                        if (idx == 0) "My Home" else "Home (${home.subnetPrefix}.x)"
                    } else {
                        home.homeName
                    },
                    ssidLabel = if (pollutedSsid) "LAN ${home.subnetPrefix}.0/24" else home.ssidLabel
                )
                dao.updateHomeNetwork(repaired)
                repaired
            } else {
                home
            }
        }

        val homeByKey = existingHomes.associateBy { it.networkKey }
        val rawDevices = dao.getAllDevices().first()
        rawDevices.forEach { dev ->
            val cleanSsid = if (XmlAndSkuParser.isWemoDeviceSetupSsid(dev.wifiSsid)) {
                val homeSsid = homeByKey[dev.networkKey]?.ssidLabel?.takeIf {
                    it.isNotBlank() && !XmlAndSkuParser.isWemoDeviceSetupSsid(it)
                }
                val deviceSubnet = dev.ipAddress.substringBeforeLast('.', "").takeIf {
                    it.count { ch -> ch == '.' } == 2 && it != "127.0.0" && it != "10.22.22"
                }
                homeSsid ?: "LAN ${deviceSubnet ?: subnetPrefix}.0/24"
            } else {
                dev.wifiSsid
            }
            val cleanRoom = resolveDeviceRoomAssignment(
                existingRoomName = dev.roomName,
                deviceName = dev.name,
                isDemoMode = false
            )
            if (cleanSsid != dev.wifiSsid || cleanRoom != dev.roomName) {
                dao.updateDevice(dev.copy(wifiSsid = cleanSsid, roomName = cleanRoom))
            }
        }

        val matchedHome = existingHomes.find { it.subnetPrefix == subnetPrefix }
        val activeHome = when {
            matchedHome != null -> matchedHome
            existingHomes.isNotEmpty() -> existingHomes.first()
            else -> {
                val key = "wifi_lan_${subnetPrefix.replace('.', '_')}"
                val newHome = HomeNetworkEntity(
                    networkKey = key,
                    homeName = "My Home",
                    ssidLabel = "LAN $subnetPrefix.0/24",
                    subnetPrefix = subnetPrefix,
                    gatewayIp = "$subnetPrefix.1",
                    lastConnectedTime = "Connected now",
                    autoReconnectOnArrival = true,
                    sortOrder = 0
                )
                dao.insertHomeNetwork(newHome)
                newHome
            }
        }
        return activeHome.networkKey
    }

    /**
     * Zero-Permission Subnet matching:
     * finds or creates a [HomeNetworkEntity] for [subnetPrefix] when on a real LAN.
     */
    suspend fun resolveOrCreateHomeForSubnet(
        subnetPrefix: String,
        gatewayIp: String = "$subnetPrefix.1"
    ): HomeNetworkEntity? {
        if (isDemoShowroomMode() || subnetPrefix == "10.22.22") return null
        val cleanPrefix = subnetPrefix.trim().ifEmpty { "192.168.1" }
        val existingHomes = dao.getAllHomeNetworks().first()
        val matched = existingHomes.find { it.subnetPrefix == cleanPrefix }
        if (matched != null) return matched

        val key = "wifi_lan_${cleanPrefix.replace('.', '_')}"
        val defaultName = if (existingHomes.isEmpty()) "My Home" else "Home ($cleanPrefix.x)"
        val newHome = HomeNetworkEntity(
            networkKey = key,
            homeName = defaultName,
            ssidLabel = "LAN $cleanPrefix.0/24",
            subnetPrefix = cleanPrefix,
            gatewayIp = gatewayIp,
            lastConnectedTime = "Connected now",
            autoReconnectOnArrival = true,
            sortOrder = (existingHomes.maxOfOrNull { it.sortOrder } ?: -1) + 1
        )
        dao.insertHomeNetwork(newHome)
        return newHome
    }

    /**
     * Updates the active Wi-Fi Home's ssidLabel only when a real router SSID is reported
     * (never a Belkin WeMo soft-AP SSID like `WeMo.Switch.xxx` / `WeMo.Light.xxx`), and never
     * overwrites the Home Name with a WeMo device SSID.
     */
    suspend fun syncHomeSsidFromDeviceSsid(networkKey: String, reportedSsid: String) {
        val cleanSsid = reportedSsid.trim()
        if (cleanSsid.isEmpty() || XmlAndSkuParser.isWemoDeviceSetupSsid(cleanSsid)) return
        val homes = dao.getAllHomeNetworks().first()
        val home = homes.find { it.networkKey == networkKey } ?: return
        val isGenericSsid = home.ssidLabel.startsWith("Local LAN", ignoreCase = true) ||
            home.ssidLabel.startsWith("LAN ", ignoreCase = true) ||
            home.ssidLabel.startsWith("Local Wi-Fi", ignoreCase = true)
        if (isGenericSsid && home.ssidLabel != cleanSsid) {
            dao.updateHomeNetwork(home.copy(ssidLabel = cleanSsid))
        }
    }

    suspend fun syncFirstRouterSsid(
        networkKey: String,
        discovered: List<SsdpDiscoveredDevice>
    ) {
        discovered
            .firstNotNullOfOrNull {
                it.wifiSsid.trim().takeIf { s -> s.isNotEmpty() && !XmlAndSkuParser.isWemoDeviceSetupSsid(s) }
            }
            ?.let { syncHomeSsidFromDeviceSsid(networkKey, it) }
    }

    fun mergeDiscoveredMetadata(
        base: DeviceEntity,
        discovered: SsdpDiscoveredDevice?,
        ipAddress: String,
        port: Int,
        isOn: Boolean,
        brightness: Int,
        latencyMs: Int,
        checkedAt: String,
        demoMode: Boolean
    ): DeviceEntity {
        val resolvedName = base.name.ifBlank { discovered?.name.orEmpty() }
        val resolvedRoom = resolveDeviceRoomAssignment(
            existingRoomName = base.roomName,
            deviceName = resolvedName,
            discoveredRoomName = discovered?.roomName.orEmpty(),
            isDemoMode = demoMode
        )
        val cleanSerial = discovered?.serialNumber
            ?.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) }
            ?: base.serialNumber
        val cleanSsid = discovered?.wifiSsid?.trim()
            ?.takeIf { it.isNotEmpty() && !XmlAndSkuParser.isWemoDeviceSetupSsid(it) }
            ?: base.wifiSsid.takeUnless { XmlAndSkuParser.isWemoDeviceSetupSsid(it) }
            ?: "Local LAN"
        val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(discovered?.usn)
            .ifBlank { LanRuntimeUtils.canonicalHardwareUdn(base.udn) }
        return base.copy(
            name = resolvedName,
            roomName = resolvedRoom,
            ipAddress = ipAddress,
            port = port,
            isReachable = true,
            isOn = isOn,
            brightness = brightness,
            latencyMs = latencyMs,
            lastCheckedTime = checkedAt,
            serialNumber = cleanSerial,
            firmwareVersion = discovered?.firmwareVersion?.ifBlank { base.firmwareVersion } ?: base.firmwareVersion,
            hardwareModel = discovered?.hardwareSku?.ifBlank { base.hardwareModel } ?: base.hardwareModel,
            macAddress = discovered?.macAddress?.ifBlank { base.macAddress } ?: base.macAddress,
            udn = cleanUdn,
            wifiSsid = cleanSsid
        )
    }
}
