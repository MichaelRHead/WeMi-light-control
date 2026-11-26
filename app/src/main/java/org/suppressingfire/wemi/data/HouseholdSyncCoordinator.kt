package org.suppressingfire.wemi.data

import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.suppressingfire.wemi.network.HardwareRulesCapability
import org.suppressingfire.wemi.network.HouseholdImportMode
import org.suppressingfire.wemi.network.HouseholdImportSummary
import org.suppressingfire.wemi.network.HouseholdSyncBundle
import org.suppressingfire.wemi.network.HouseholdSyncCodec
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.SyncedDeviceProfile
import org.suppressingfire.wemi.network.SyncedHomeProfile
import org.suppressingfire.wemi.network.XmlAndSkuParser

/**
 * Coordinates zero-cloud Household Configuration export and identity-safe Room v7 import
 * ([HouseholdImportMode.MERGE] or [HouseholdImportMode.REPLACE]).
 *
 * Preserves WeMi's 3-tier hardware identity hierarchy (`serialNumber` -> canonical `udn` ->
 * endpoint/name), canonical room normalization, soft-AP SSID filtering, and CGNAT exclusion.
 */
internal class HouseholdSyncCoordinator(
    private val dao: SwitchDao,
    private val seedAndModeMutex: Mutex,
    private val isDemoShowroomMode: () -> Boolean,
    private val detectLocalLanIp: () -> String,
    private val onExitDemoModeForImport: () -> Unit,
    private val onResetRuntimeState: () -> Unit,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String
) {
    suspend fun buildExportBundle(sourceDeviceLabel: String = "WeMi Device"): HouseholdSyncBundle {
        val homes = dao.getAllHomeNetworks().first()
        val devices = dao.getAllDevices().first()
        return HouseholdSyncCodec.fromEntities(
            homes = homes,
            devices = devices,
            sourceDeviceLabel = sourceDeviceLabel
        )
    }

    suspend fun exportHouseholdJson(sourceDeviceLabel: String = "WeMi Device"): String =
        HouseholdSyncCodec.encodeToJson(buildExportBundle(sourceDeviceLabel))

    suspend fun importHouseholdBundle(
        bundle: HouseholdSyncBundle,
        mode: HouseholdImportMode = HouseholdImportMode.MERGE,
        onActiveKeyResolved: ((String) -> Unit)? = null
    ): HouseholdImportSummary = seedAndModeMutex.withLock {
        val wasDemoMode = isDemoShowroomMode()
        if (wasDemoMode) {
            onResetRuntimeState()
            dao.deleteAllDevices()
            dao.deleteAllHomeNetworks()
            onExitDemoModeForImport()
        }

        val effectiveMode = if (wasDemoMode) HouseholdImportMode.REPLACE else mode
        val summary = when (effectiveMode) {
            HouseholdImportMode.REPLACE -> applyReplaceLocked(bundle)
            HouseholdImportMode.MERGE -> applyMergeLocked(bundle)
        }
        onActiveKeyResolved?.invoke(summary.activeNetworkKey)
        appendLog(
            SoapLogEntry(
                timestamp = nowFormatted(),
                deviceName = bundle.sourceDeviceLabel,
                endpoint = "local://household-sync",
                soapAction = "HouseholdSync#${effectiveMode.name}",
                statusText = "${summary.homesAdded + summary.homesUpdated} home(s) • +${summary.devicesAdded} added, ${summary.devicesUpdated} updated",
                xmlSnippet = "schema=v${bundle.schemaVersion} mode=${effectiveMode.name} activeHome=${summary.activeNetworkKey}",
                isLiveLanSuccess = true
            )
        )
        summary
    }

    private suspend fun applyReplaceLocked(bundle: HouseholdSyncBundle): HouseholdImportSummary {
        onResetRuntimeState()
        dao.deleteAllDevices()
        dao.deleteAllHomeNetworks()

        val homeEntities = buildNormalizedHomeEntities(bundle)
        dao.insertHomeNetworks(homeEntities)
        val homeKeyByImportedKey = homeEntities.associate { it.networkKey to it.networkKey }
        val defaultHomeKey = homeEntities.firstOrNull()?.networkKey ?: DEFAULT_MAIN_HOME_KEY
        val ssidByHomeKey = homeEntities.associate { it.networkKey to it.ssidLabel }

        val deviceEntities = bundle.devices.mapIndexed { idx, dev ->
            val targetKey = homeKeyByImportedKey[dev.networkKey] ?: defaultHomeKey
            toNewDeviceEntity(
                incoming = dev,
                targetHomeKey = targetKey,
                fallbackSsid = ssidByHomeKey[targetKey] ?: "Local LAN",
                defaultSortOrder = idx
            )
        }
        if (deviceEntities.isNotEmpty()) {
            dao.insertDevices(deviceEntities)
        }

        val activeKey = resolvePreferredActiveHomeKey(homeEntities)
        return HouseholdImportSummary(
            mode = HouseholdImportMode.REPLACE,
            homesAdded = homeEntities.size,
            homesUpdated = 0,
            devicesAdded = deviceEntities.size,
            devicesUpdated = 0,
            devicesPreserved = 0,
            activeNetworkKey = activeKey
        )
    }

    private suspend fun applyMergeLocked(bundle: HouseholdSyncBundle): HouseholdImportSummary {
        val existingHomes = dao.getAllHomeNetworks().first().toMutableList()
        val existingDevices = dao.getAllDevices().first().toMutableList()
        val incomingHomes = buildNormalizedHomeEntities(bundle)

        val importedKeyToLocalKey = LinkedHashMap<String, String>()
        var homesAdded = 0
        var homesUpdated = 0

        // If the receiver only has a single untouched blank-subnet placeholder home with 0 switches,
        // upgrade it cleanly into the first imported real home instead of leaving an empty shell behind.
        val replaceablePlaceholderHome = existingHomes.singleOrNull()?.takeIf { sole ->
            sole.subnetPrefix.isBlank() && existingDevices.none { it.networkKey == sole.networkKey }
        }
        var consumedPlaceholder = false

        for (incoming in incomingHomes) {
            val matched = existingHomes.firstOrNull { it.networkKey == incoming.networkKey }
                ?: incoming.subnetPrefix.takeIf { it.isNotBlank() }?.let { prefix ->
                    existingHomes.firstOrNull { it.subnetPrefix == prefix }
                }
                ?: if (!consumedPlaceholder && replaceablePlaceholderHome != null && incoming.subnetPrefix.isNotBlank()) {
                    consumedPlaceholder = true
                    replaceablePlaceholderHome
                } else {
                    null
                }

            if (matched != null) {
                val mergedSsid = incoming.ssidLabel.takeIf {
                    it.isNotBlank() &&
                        !XmlAndSkuParser.isWemoDeviceSetupSsid(it) &&
                        !it.equals("Local LAN", ignoreCase = true)
                } ?: matched.ssidLabel
                val updated = matched.copy(
                    homeName = incoming.homeName.ifBlank { matched.homeName },
                    ssidLabel = mergedSsid,
                    subnetPrefix = matched.subnetPrefix.ifBlank { incoming.subnetPrefix },
                    gatewayIp = matched.gatewayIp.ifBlank { incoming.gatewayIp },
                    autoReconnectOnArrival = incoming.autoReconnectOnArrival,
                    sortOrder = incoming.sortOrder
                )
                dao.updateHomeNetwork(updated)
                val idx = existingHomes.indexOfFirst { it.networkKey == matched.networkKey }
                if (idx >= 0) existingHomes[idx] = updated
                importedKeyToLocalKey[incoming.networkKey] = updated.networkKey
                homesUpdated++
            } else {
                dao.insertHomeNetwork(incoming)
                existingHomes.add(incoming)
                importedKeyToLocalKey[incoming.networkKey] = incoming.networkKey
                homesAdded++
            }
        }

        val fallbackHomeKey = importedKeyToLocalKey.values.firstOrNull()
            ?: existingHomes.firstOrNull()?.networkKey
            ?: DEFAULT_MAIN_HOME_KEY
        val ssidByHomeKey = existingHomes.associate { it.networkKey to it.ssidLabel }

        val matchedExistingIds = HashSet<Int>()
        var devicesAdded = 0
        var devicesUpdated = 0

        for ((idx, incoming) in bundle.devices.withIndex()) {
            val targetHomeKey = importedKeyToLocalKey[incoming.networkKey] ?: fallbackHomeKey
            val matched = findMatchingExistingDeviceForImport(
                existingDevices = existingDevices,
                incoming = incoming,
                targetHomeKey = targetHomeKey,
                alreadyMatchedIds = matchedExistingIds
            )
            if (matched != null) {
                matchedExistingIds.add(matched.id)
                val mergedSerial = if (!LanRuntimeUtils.isPlaceholderSerial(matched.serialNumber)) {
                    matched.serialNumber
                } else if (!LanRuntimeUtils.isPlaceholderSerial(incoming.serialNumber)) {
                    incoming.serialNumber
                } else {
                    matched.serialNumber
                }
                val mergedUdn = LanRuntimeUtils.canonicalHardwareUdn(matched.udn)
                    .ifEmpty { LanRuntimeUtils.canonicalHardwareUdn(incoming.udn) }
                val mergedSsid = incoming.wifiSsid.takeIf {
                    it.isNotBlank() && !XmlAndSkuParser.isWemoDeviceSetupSsid(it)
                } ?: matched.wifiSsid.ifBlank { ssidByHomeKey[targetHomeKey] ?: "Local LAN" }
                val updated = matched.copy(
                    name = incoming.name,
                    roomName = normalizeRoomName(incoming.roomName),
                    networkKey = targetHomeKey,
                    wifiSsid = mergedSsid,
                    deviceType = incoming.deviceType,
                    ipAddress = matched.ipAddress.ifBlank { incoming.ipAddress },
                    port = if (matched.ipAddress.isNotBlank()) matched.port else incoming.port,
                    serialNumber = mergedSerial,
                    udn = mergedUdn,
                    macAddress = matched.macAddress.ifBlank { incoming.macAddress },
                    hardwareModel = matched.hardwareModel.ifBlank { incoming.hardwareModel },
                    sortOrder = incoming.sortOrder
                )
                dao.updateDevice(updated)
                val pos = existingDevices.indexOfFirst { it.id == matched.id }
                if (pos >= 0) existingDevices[pos] = updated
                devicesUpdated++
            } else {
                val created = toNewDeviceEntity(
                    incoming = incoming,
                    targetHomeKey = targetHomeKey,
                    fallbackSsid = ssidByHomeKey[targetHomeKey] ?: "Local LAN",
                    defaultSortOrder = idx
                )
                val insertedId = dao.insertDevice(created).toInt()
                existingDevices.add(created.copy(id = insertedId))
                matchedExistingIds.add(insertedId)
                devicesAdded++
            }
        }

        val preservedCount = (existingDevices.size - devicesAdded - devicesUpdated).coerceAtLeast(0)
        val activeKey = resolvePreferredActiveHomeKey(
            homes = existingHomes,
            importedHomeKeys = importedKeyToLocalKey.values.toSet(),
            preferredImportedKey = importedKeyToLocalKey.values.firstOrNull()
        )
        return HouseholdImportSummary(
            mode = HouseholdImportMode.MERGE,
            homesAdded = homesAdded,
            homesUpdated = homesUpdated,
            devicesAdded = devicesAdded,
            devicesUpdated = devicesUpdated,
            devicesPreserved = preservedCount,
            activeNetworkKey = activeKey
        )
    }

    private fun buildNormalizedHomeEntities(bundle: HouseholdSyncBundle): List<HomeNetworkEntity> {
        if (bundle.homes.isNotEmpty()) {
            return bundle.homes.map { home: SyncedHomeProfile ->
                HomeNetworkEntity(
                    networkKey = home.networkKey,
                    homeName = home.homeName,
                    ssidLabel = home.ssidLabel.ifBlank { "Local LAN" },
                    subnetPrefix = home.subnetPrefix,
                    gatewayIp = home.gatewayIp,
                    lastConnectedTime = "Synced",
                    autoReconnectOnArrival = home.autoReconnectOnArrival,
                    sortOrder = home.sortOrder
                )
            }
        }
        val inferredKey = bundle.devices.firstOrNull()?.networkKey ?: DEFAULT_MAIN_HOME_KEY
        val inferredIp = bundle.devices.firstOrNull { it.ipAddress.isNotBlank() }?.ipAddress.orEmpty()
        val inferredPrefix = if (inferredIp.count { it == '.' } == 3) inferredIp.substringBeforeLast('.') else ""
        return listOf(
            HomeNetworkEntity(
                networkKey = inferredKey,
                homeName = "My Home",
                ssidLabel = bundle.devices.firstOrNull { it.wifiSsid.isNotBlank() }?.wifiSsid ?: "Local LAN",
                subnetPrefix = inferredPrefix,
                gatewayIp = if (inferredPrefix.isNotEmpty()) "$inferredPrefix.1" else "",
                lastConnectedTime = "Synced",
                autoReconnectOnArrival = true,
                sortOrder = 0
            )
        )
    }

    private fun toNewDeviceEntity(
        incoming: SyncedDeviceProfile,
        targetHomeKey: String,
        fallbackSsid: String,
        defaultSortOrder: Int
    ): DeviceEntity = DeviceEntity(
        id = 0,
        name = incoming.name,
        roomName = normalizeRoomName(incoming.roomName),
        networkKey = targetHomeKey,
        wifiSsid = incoming.wifiSsid.ifBlank { fallbackSsid },
        deviceType = incoming.deviceType,
        ipAddress = incoming.ipAddress,
        port = incoming.port,
        isOn = false,
        isReachable = false,
        brightness = 100,
        latencyMs = 0,
        lastCheckedTime = "Synced • Reconnecting...",
        lastMotionTime = "",
        serialNumber = incoming.serialNumber.ifBlank { LanRuntimeUtils.DEFAULT_PLACEHOLDER_SERIAL },
        firmwareVersion = "",
        hardwareModel = incoming.hardwareModel,
        macAddress = incoming.macAddress,
        udn = incoming.udn,
        signalStrengthPercent = 0,
        nightModeEnabled = false,
        sortOrder = incoming.sortOrder.takeIf { it >= 0 } ?: defaultSortOrder,
        autoOffMinutes = 0,
        hardwareRulesCapability = HardwareRulesCapability.UNKNOWN.name,
        hardwareRulesLongPressCapable = false,
        hardwareRulesVerifiedFirmware = ""
    )

    private fun findMatchingExistingDeviceForImport(
        existingDevices: List<DeviceEntity>,
        incoming: SyncedDeviceProfile,
        targetHomeKey: String,
        alreadyMatchedIds: Set<Int>
    ): DeviceEntity? {
        val candidates = existingDevices.filter { it.id !in alreadyMatchedIds }
        val incomingRealSerial = incoming.serialNumber.takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty()
        val incomingUdn = LanRuntimeUtils.canonicalHardwareUdn(incoming.udn)

        // Tier 1: Real hardware serial match across all homes
        if (incomingRealSerial.isNotEmpty()) {
            candidates.firstOrNull { existing ->
                !LanRuntimeUtils.isPlaceholderSerial(existing.serialNumber) &&
                    existing.serialNumber.equals(incomingRealSerial, ignoreCase = true)
            }?.let { return it }
        }

        val homeCandidates = candidates.filter { it.networkKey == targetHomeKey }

        // Tier 2: Canonical UPnP UDN match within target home (never cross conflicting real serials)
        if (incomingUdn.isNotEmpty()) {
            homeCandidates.firstOrNull { existing ->
                !hasRealIdentityConflict(existing, incomingRealSerial, incomingUdn) &&
                    LanRuntimeUtils.canonicalHardwareUdn(existing.udn).equals(incomingUdn, ignoreCase = true)
            }?.let { return it }
        }

        // Tier 3a: Exact (ipAddress, port) match when identities do not conflict
        if (incoming.ipAddress.isNotBlank()) {
            homeCandidates.firstOrNull { existing ->
                !hasRealIdentityConflict(existing, incomingRealSerial, incomingUdn) &&
                    existing.ipAddress == incoming.ipAddress &&
                    existing.port == incoming.port
            }?.let { return it }

            // Tier 3b: Non-loopback IP match (port hop) when identities do not conflict
            if (incoming.ipAddress != "127.0.0.1") {
                homeCandidates.firstOrNull { existing ->
                    !hasRealIdentityConflict(existing, incomingRealSerial, incomingUdn) &&
                        existing.ipAddress == incoming.ipAddress
                }?.let { return it }
            }
        }

        // Tier 3c: Case-insensitive friendly name within target home when identities do not conflict
        return homeCandidates.firstOrNull { existing ->
            !hasRealIdentityConflict(existing, incomingRealSerial, incomingUdn) &&
                existing.name.lowercase(Locale.US) == incoming.name.lowercase(Locale.US)
        }
    }

    private fun hasRealIdentityConflict(
        existing: DeviceEntity,
        incomingRealSerial: String,
        incomingUdn: String
    ): Boolean {
        val existingRealSerial = existing.serialNumber.takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty()
        if (existingRealSerial.isNotEmpty() && incomingRealSerial.isNotEmpty() &&
            !existingRealSerial.equals(incomingRealSerial, ignoreCase = true)
        ) {
            return true
        }
        val existingUdn = LanRuntimeUtils.canonicalHardwareUdn(existing.udn)
        if (existingUdn.isNotEmpty() && incomingUdn.isNotEmpty() &&
            !existingUdn.equals(incomingUdn, ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    private fun resolvePreferredActiveHomeKey(
        homes: List<HomeNetworkEntity>,
        importedHomeKeys: Set<String> = homes.map { it.networkKey }.toSet(),
        preferredImportedKey: String? = null
    ): String {
        val localIp = detectLocalLanIp().trim()
        val localPrefix = if (LanRuntimeUtils.isPrivateRfc1918Ipv4(localIp)) {
            localIp.substringBeforeLast('.', "")
        } else {
            ""
        }
        if (localPrefix.isNotEmpty()) {
            homes.firstOrNull { it.networkKey in importedHomeKeys && it.subnetPrefix == localPrefix }
                ?.let { return it.networkKey }
        }
        if (!preferredImportedKey.isNullOrBlank() && homes.any { it.networkKey == preferredImportedKey }) {
            return preferredImportedKey
        }
        if (localPrefix.isNotEmpty()) {
            homes.firstOrNull { it.subnetPrefix == localPrefix }?.let { return it.networkKey }
        }
        return homes.firstOrNull()?.networkKey ?: DEFAULT_MAIN_HOME_KEY
    }
}
