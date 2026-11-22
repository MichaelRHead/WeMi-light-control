package org.suppressingfire.wemi.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.suppressingfire.wemi.network.HardwareRulesCapability
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
    private val nowFormatted: () -> String
) {
    private val adoptMutex = Mutex()

    private fun isLoopbackHost(ipAddress: String): Boolean =
        ipAddress == "127.0.0.1" || ipAddress.equals("localhost", ignoreCase = true)

    fun findMatchingExistingDevice(
        existingDevices: List<DeviceEntity>,
        discovered: SsdpDiscoveredDevice,
        activeNetworkKey: String
    ): DeviceEntity? {
        if (!LanRuntimeUtils.isPlaceholderSerial(discovered.serialNumber)) {
            existingDevices.find {
                !LanRuntimeUtils.isPlaceholderSerial(it.serialNumber) &&
                    it.serialNumber.equals(discovered.serialNumber, ignoreCase = true)
            }?.let { return it }
        }
        val homeDevices = existingDevices.filter { it.networkKey == activeNetworkKey }
        val cleanDiscoveredUdn = LanRuntimeUtils.canonicalHardwareUdn(discovered.usn)
        if (cleanDiscoveredUdn.isNotEmpty()) {
            homeDevices.find {
                LanRuntimeUtils.canonicalHardwareUdn(it.udn).equals(cleanDiscoveredUdn, ignoreCase = true) &&
                    it.matchesDiscovered(discovered, requireSamePortWhenBlankSerial = true)
            }?.let { return it }
        }
        homeDevices.find {
            it.ipAddress == discovered.ipAddress && it.port == discovered.port &&
                it.matchesDiscovered(discovered, requireSamePortWhenBlankSerial = true)
        }?.let { return it }
        if (!isLoopbackHost(discovered.ipAddress)) {
            homeDevices.find {
                it.ipAddress == discovered.ipAddress &&
                    it.matchesDiscovered(discovered, requireSamePortWhenBlankSerial = false)
            }?.let { return it }
        }
        return homeDevices.find {
            it.matchesDiscovered(discovered, requireSamePortWhenBlankSerial = true)
        }
    }

    private fun isDiscoveredClaimedByOtherDevice(
        discovered: SsdpDiscoveredDevice,
        existing: DeviceEntity,
        homeDevices: List<DeviceEntity>,
        includeEndpointClaim: Boolean
    ): Boolean {
        val discSerialReal = !LanRuntimeUtils.isPlaceholderSerial(discovered.serialNumber)
        val discUdn = LanRuntimeUtils.canonicalHardwareUdn(discovered.usn)
        return homeDevices.any { other ->
            (other.id != existing.id && (existing.id != 0 || other !== existing)) && (
                (discSerialReal && !LanRuntimeUtils.isPlaceholderSerial(other.serialNumber) &&
                    other.serialNumber.equals(discovered.serialNumber, ignoreCase = true)) ||
                    (discUdn.isNotEmpty() &&
                        LanRuntimeUtils.canonicalHardwareUdn(other.udn).equals(discUdn, ignoreCase = true)) ||
                    (includeEndpointClaim &&
                        other.ipAddress == discovered.ipAddress && other.port == discovered.port)
                )
        }
    }

    fun findMatchingDiscoveredDevice(
        existing: DeviceEntity,
        discoveredDevices: List<SsdpDiscoveredDevice>,
        homeDevices: List<DeviceEntity> = emptyList(),
        requireSamePortWhenBlankSerial: Boolean = true
    ): SsdpDiscoveredDevice? {
        if (!LanRuntimeUtils.isPlaceholderSerial(existing.serialNumber)) {
            discoveredDevices.find {
                !LanRuntimeUtils.isPlaceholderSerial(it.serialNumber) &&
                    it.serialNumber.equals(existing.serialNumber, ignoreCase = true)
            }?.let { return it }
        }
        val cleanExistingUdn = LanRuntimeUtils.canonicalHardwareUdn(existing.udn)
        if (cleanExistingUdn.isNotEmpty()) {
            discoveredDevices.find {
                LanRuntimeUtils.canonicalHardwareUdn(it.usn).equals(cleanExistingUdn, ignoreCase = true) &&
                    existing.matchesDiscovered(it, requireSamePortWhenBlankSerial)
            }?.let { return it }
        }
        discoveredDevices.find {
            it.ipAddress == existing.ipAddress &&
                (!requireSamePortWhenBlankSerial || it.port == existing.port) &&
                existing.matchesDiscovered(it, requireSamePortWhenBlankSerial) &&
                !isDiscoveredClaimedByOtherDevice(it, existing, homeDevices, includeEndpointClaim = false)
        }?.let { return it }
        if (requireSamePortWhenBlankSerial && !isLoopbackHost(existing.ipAddress)) {
            discoveredDevices.find {
                it.ipAddress == existing.ipAddress &&
                    existing.matchesDiscovered(it, requireSamePortWhenBlankSerial = false) &&
                    !isDiscoveredClaimedByOtherDevice(it, existing, homeDevices, includeEndpointClaim = true)
            }?.let { return it }
        }
        return discoveredDevices.find {
            existing.matchesDiscovered(it, requireSamePortWhenBlankSerial) &&
                !isDiscoveredClaimedByOtherDevice(it, existing, homeDevices, includeEndpointClaim = true)
        }
    }

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
                roomName = normalizeRoomName(discovered.roomName.ifBlank { inferRoomFromName(discovered.name) }),
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
                sortOrder = nextSortOrder,
                hardwareRulesCapability = discovered.rulesCapabilityHint.name,
                hardwareRulesLongPressCapable = discovered.longPressCapableHint
            )
            val newId = dao.insertDevice(newEntity).toInt()
            syncHomeSubnetFromIp(activeNetworkKey, discovered.ipAddress)
            newEntity.copy(id = newId) to true
        } else {
            val resolvedName = discovered.name.ifBlank { existing.name }
            val resolvedRoom = resolveDeviceRoomAssignment(
                existingRoomName = existing.roomName,
                deviceName = resolvedName,
                discoveredRoomName = discovered.roomName,
                isDemoMode = isDemoShowroomMode()
            )
            val nextFw = discovered.firmwareVersion.ifBlank { existing.firmwareVersion }
            val (nextCap, nextLp, nextVerifiedFw) = resolveMergedHardwareRulesState(existing, discovered, nextFw)
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
                firmwareVersion = nextFw,
                hardwareModel = discovered.hardwareSku.ifBlank { existing.hardwareModel },
                macAddress = discovered.macAddress.ifBlank { existing.macAddress },
                udn = cleanDiscoveredUdn.ifBlank { LanRuntimeUtils.canonicalHardwareUdn(existing.udn) },
                signalStrengthPercent = discovered.signalStrengthPercent,
                hardwareRulesCapability = nextCap,
                hardwareRulesLongPressCapable = nextLp,
                hardwareRulesVerifiedFirmware = nextVerifiedFw
            )
            dao.updateDevice(updated)
            syncHomeSubnetFromIp(activeNetworkKey, discovered.ipAddress)
            updated to false
        }
    }

    suspend fun adoptDiscoveredDevices(
        discoveredList: List<SsdpDiscoveredDevice>,
        activeNetworkKey: String = DEFAULT_MAIN_HOME_KEY,
        activeWifiSsid: String = "WeMi-Home-5G"
    ): Int {
        if (discoveredList.isEmpty()) return 0
        val existingSnapshot = dao.getAllDevices().first()
        val homeSnapshot = existingSnapshot.filter { it.networkKey == activeNetworkKey }
        val orderedList = discoveredList.sortedBy { item ->
            val hasRealSerial = !LanRuntimeUtils.isPlaceholderSerial(item.serialNumber)
            val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(item.usn)
            when {
                hasRealSerial && existingSnapshot.any {
                    !LanRuntimeUtils.isPlaceholderSerial(it.serialNumber) &&
                        it.serialNumber.equals(item.serialNumber, ignoreCase = true)
                } -> 0
                cleanUdn.isNotEmpty() && homeSnapshot.any {
                    LanRuntimeUtils.canonicalHardwareUdn(it.udn).equals(cleanUdn, ignoreCase = true)
                } -> 1
                homeSnapshot.any { it.ipAddress == item.ipAddress && it.port == item.port } -> 2
                !isLoopbackHost(item.ipAddress) && homeSnapshot.any { it.ipAddress == item.ipAddress } -> 3
                else -> 4
            }
        }
        var newlyAdded = 0
        for (item in orderedList) {
            if (adoptDiscoveredDeviceWithStatus(item, activeNetworkKey, activeWifiSsid).second) {
                newlyAdded++
            }
        }
        return newlyAdded
    }

    /**
     * Sanitizes any persisted Wi-Fi Homes or devices whose names/SSIDs were polluted by Belkin
     * `GetMetaInfo` soft-AP SSIDs (`WeMo.*` / `Wemo.LS3W.*`), stuck in legacy `"Living Room"`
     * room assignments, or recorded with a ChromeOS ARCVM CGNAT prefix (`100.64.0.0/10`), and
     * resolves (or creates) the active Real LAN Home profile.
     */
    private fun isCgnatLanLabel(label: String): Boolean =
        label.startsWith("LAN ", ignoreCase = true) &&
            LanRuntimeUtils.isCarrierGradeNatIpv4(label.substring(4).trim())

    suspend fun sanitizeAndResolveInitialLanHome(localIp: String): String {
        val isCgnatLocal = LanRuntimeUtils.isCarrierGradeNatIpv4(localIp)
        val subnetPrefix = localIp.substringBeforeLast('.', "192.168.1")

        val rawHomes = dao.getAllHomeNetworks().first()
        val rawDevices = dao.getAllDevices().first()
        val existingHomes = rawHomes.mapIndexed { idx, home ->
            val isCgnatHome = LanRuntimeUtils.isCarrierGradeNatIpv4(home.subnetPrefix)
            val pollutedCgnatName = isCgnatHome && home.homeName.startsWith("Home (", ignoreCase = true) &&
                LanRuntimeUtils.isCarrierGradeNatIpv4(home.homeName.substringAfter("Home (", "").substringBefore(')'))
            val pollutedName = XmlAndSkuParser.isWemoDeviceSetupSsid(home.homeName) ||
                home.homeName.startsWith("Home Wi-Fi (", ignoreCase = true) ||
                pollutedCgnatName
            val pollutedSsid = XmlAndSkuParser.isWemoDeviceSetupSsid(home.ssidLabel) || isCgnatLanLabel(home.ssidLabel)
            if (!pollutedName && !pollutedSsid && !isCgnatHome) {
                home
            } else {
                val inferredPrefix = if (isCgnatHome) {
                    rawDevices.filter { it.networkKey == home.networkKey }
                        .firstNotNullOfOrNull { dev ->
                            LanRuntimeUtils.normalizeSubnetPrefixOrNull(dev.ipAddress)
                                ?.takeIf { LanRuntimeUtils.isPrivateRfc1918Ipv4(it) }
                        }
                } else {
                    null
                }
                val repairedSubnet = if (isCgnatHome) inferredPrefix.orEmpty() else home.subnetPrefix
                val repairedGateway = if (isCgnatHome) inferredPrefix?.let { "$it.1" }.orEmpty() else home.gatewayIp
                val genericLanLabel = if (repairedSubnet.isNotBlank()) "LAN $repairedSubnet.0/24" else "Local LAN"
                val repairedSsid = if (pollutedSsid) genericLanLabel else home.ssidLabel
                val hasOtherMyHome = rawHomes.any {
                    it.networkKey != home.networkKey && it.homeName.equals("My Home", ignoreCase = true)
                }
                val repairedName = when {
                    !pollutedName -> home.homeName
                    repairedSubnet.isBlank() || (idx == 0 && !hasOtherMyHome) -> "My Home"
                    else -> "Home ($repairedSubnet.x)"
                }
                home.copy(
                    homeName = repairedName,
                    ssidLabel = repairedSsid,
                    subnetPrefix = repairedSubnet,
                    gatewayIp = repairedGateway
                ).also { dao.updateHomeNetwork(it) }
            }
        }

        val homeByKey = existingHomes.associateBy { it.networkKey }
        rawDevices.forEach { dev ->
            val cleanSsid = if (XmlAndSkuParser.isWemoDeviceSetupSsid(dev.wifiSsid) || isCgnatLanLabel(dev.wifiSsid)) {
                val homeSsid = homeByKey[dev.networkKey]?.ssidLabel?.takeIf {
                    it.isNotBlank() && !XmlAndSkuParser.isWemoDeviceSetupSsid(it) && !isCgnatLanLabel(it)
                }
                val deviceSubnet = LanRuntimeUtils.normalizeSubnetPrefixOrNull(dev.ipAddress)
                    ?.takeIf { LanRuntimeUtils.isPrivateRfc1918Ipv4(it) }
                homeSsid ?: when {
                    deviceSubnet != null -> "LAN $deviceSubnet.0/24"
                    !isCgnatLocal -> "LAN $subnetPrefix.0/24"
                    else -> "Local LAN"
                }
            } else {
                dev.wifiSsid
            }
            val cleanRoom = resolveDeviceRoomAssignment(dev.roomName, dev.name, isDemoMode = false)
            if (cleanSsid != dev.wifiSsid || cleanRoom != dev.roomName) {
                dao.updateDevice(dev.copy(wifiSsid = cleanSsid, roomName = cleanRoom))
            }
        }

        val activeHome = if (isCgnatLocal) {
            existingHomes.firstOrNull { LanRuntimeUtils.isPrivateRfc1918Ipv4(it.subnetPrefix) }
                ?: existingHomes.firstOrNull()
                ?: HomeNetworkEntity(
                    networkKey = "wifi_lan_chromeos_default", homeName = "My Home", ssidLabel = "Local LAN",
                    subnetPrefix = "", gatewayIp = "", lastConnectedTime = "Connected now",
                    autoReconnectOnArrival = true, sortOrder = 0
                ).also { dao.insertHomeNetwork(it) }
        } else {
            existingHomes.find { it.subnetPrefix == subnetPrefix }
                ?: existingHomes.firstOrNull()
                ?: HomeNetworkEntity(
                    networkKey = "wifi_lan_${subnetPrefix.replace('.', '_')}", homeName = "My Home",
                    ssidLabel = "LAN $subnetPrefix.0/24", subnetPrefix = subnetPrefix, gatewayIp = "$subnetPrefix.1",
                    lastConnectedTime = "Connected now", autoReconnectOnArrival = true, sortOrder = 0
                ).also { dao.insertHomeNetwork(it) }
        }
        return activeHome.networkKey
    }

    /**
     * Zero-Permission Subnet matching:
     * finds or creates a [HomeNetworkEntity] for [subnetPrefix] when on a real RFC 1918 LAN.
     * Ignores Belkin `10.22.22` Setup AP subnets and RFC 6598 CGNAT (`100.64.0.0/10`) ChromeOS
     * ARCVM virtual subnets so guest link updates never create phantom homes.
     */
    suspend fun resolveOrCreateHomeForSubnet(
        subnetPrefix: String,
        gatewayIp: String = "$subnetPrefix.1"
    ): HomeNetworkEntity? {
        if (isDemoShowroomMode() || subnetPrefix == "10.22.22" || LanRuntimeUtils.isCarrierGradeNatIpv4(subnetPrefix)) {
            return null
        }
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

    /**
     * Backfills a Home profile's blank or CGNAT (`100.64.0.0/10`) `subnetPrefix` and `gatewayIp`
     * once a live physical switch is discovered or added on an RFC 1918 LAN IPv4 address.
     */
    suspend fun syncHomeSubnetFromIp(networkKey: String, discoveredIp: String) {
        val prefix = LanRuntimeUtils.normalizeSubnetPrefixOrNull(discoveredIp) ?: return
        if (!LanRuntimeUtils.isPrivateRfc1918Ipv4(prefix)) return
        val home = dao.getAllHomeNetworks().first().find { it.networkKey == networkKey } ?: return
        if (home.subnetPrefix.isNotBlank() && !LanRuntimeUtils.isCarrierGradeNatIpv4(home.subnetPrefix)) return
        val updatedSsid = if (home.ssidLabel.equals("Local LAN", ignoreCase = true) ||
            isCgnatLanLabel(home.ssidLabel)
        ) {
            "LAN $prefix.0/24"
        } else {
            home.ssidLabel
        }
        dao.updateHomeNetwork(
            home.copy(subnetPrefix = prefix, gatewayIp = "$prefix.1", ssidLabel = updatedSsid)
        )
    }

    suspend fun syncHomeSubnetFromDiscoveredDevices(
        networkKey: String,
        discovered: List<SsdpDiscoveredDevice>
    ) {
        discovered
            .firstOrNull { LanRuntimeUtils.isPrivateRfc1918Ipv4(it.ipAddress) }
            ?.let { syncHomeSubnetFromIp(networkKey, it.ipAddress) }
    }

    fun mergeDiscoveredMetadata(
        base: DeviceEntity, discovered: SsdpDiscoveredDevice?, ipAddress: String, port: Int,
        isOn: Boolean, brightness: Int, latencyMs: Int, checkedAt: String, demoMode: Boolean
    ): DeviceEntity {
        val resolvedName = base.name.ifBlank { discovered?.name.orEmpty() }
        val resolvedRoom = resolveDeviceRoomAssignment(base.roomName, resolvedName, discovered?.roomName.orEmpty(), demoMode)
        val cleanSerial = discovered?.serialNumber?.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) } ?: base.serialNumber
        val cleanSsid = discovered?.wifiSsid?.trim()?.takeIf { it.isNotEmpty() && !XmlAndSkuParser.isWemoDeviceSetupSsid(it) }
            ?: base.wifiSsid.takeUnless { XmlAndSkuParser.isWemoDeviceSetupSsid(it) } ?: "Local LAN"
        val cleanUdn = LanRuntimeUtils.canonicalHardwareUdn(discovered?.usn).ifBlank { LanRuntimeUtils.canonicalHardwareUdn(base.udn) }
        val nextFw = discovered?.firmwareVersion?.ifBlank { base.firmwareVersion } ?: base.firmwareVersion
        val (nextCap, nextLp, nextVerifiedFw) = resolveMergedHardwareRulesState(base, discovered, nextFw)
        return base.copy(
            name = resolvedName, roomName = resolvedRoom, ipAddress = ipAddress, port = port,
            isReachable = true, isOn = isOn, brightness = brightness, latencyMs = latencyMs,
            lastCheckedTime = checkedAt, serialNumber = cleanSerial, firmwareVersion = nextFw,
            hardwareModel = discovered?.hardwareSku?.ifBlank { base.hardwareModel } ?: base.hardwareModel,
            macAddress = discovered?.macAddress?.ifBlank { base.macAddress } ?: base.macAddress,
            udn = cleanUdn, wifiSsid = cleanSsid, hardwareRulesCapability = nextCap,
            hardwareRulesLongPressCapable = nextLp,
            hardwareRulesVerifiedFirmware = nextVerifiedFw
        )
    }

    private fun resolveMergedHardwareRulesState(
        base: DeviceEntity,
        discovered: SsdpDiscoveredDevice?,
        nextFw: String
    ): Triple<String, Boolean, String> {
        val keepVerified = base.parsedHardwareRulesCapability.isVerified &&
            base.hardwareRulesVerifiedFirmware.isNotBlank() &&
            base.hardwareRulesVerifiedFirmware.equals(nextFw.trim(), ignoreCase = true)
        return when {
            keepVerified -> Triple(base.hardwareRulesCapability, base.hardwareRulesLongPressCapable, base.hardwareRulesVerifiedFirmware)
            discovered != null && discovered.rulesCapabilityHint != HardwareRulesCapability.UNKNOWN ->
                Triple(discovered.rulesCapabilityHint.name, discovered.longPressCapableHint, "")
            base.parsedHardwareRulesCapability.isVerified ->
                Triple(HardwareRulesCapability.UNKNOWN.name, base.hardwareRulesLongPressCapable, "")
            else -> Triple(base.hardwareRulesCapability, base.hardwareRulesLongPressCapable, "")
        }
    }
}
