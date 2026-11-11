package org.suppressingfire.wemi.network

import org.suppressingfire.wemi.data.DeviceType
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice

/**
 * Immutable wire-level data transfer models returned by [NetworkClient], [SsdpDiscoveryEngine],
 * [ProvisionerClient], and [XmlAndSkuParser].
 */
data class SoapCallResult(
    val reachableOnLan: Boolean,
    val binaryStateOn: Boolean?,
    val brightness: Int?,
    val resolvedPort: Int,
    val latencyMs: Int,
    val logEntry: SoapLogEntry,
    val nightModeEnabled: Boolean? = null
)

data class SwitchMetaInfo(
    val macAddress: String,
    val serialNumber: String,
    val deviceSku: String,
    val firmwareVersion: String,
    val accessPointSsid: String,
    val modelName: String
)

data class SsdpScanReport(
    val localInterfaceIp: String,
    val searchTargetUsed: String,
    val mSearchRequestRaw: String,
    val discoveredDevices: List<SsdpDiscoveredDevice>,
    val rawSocketHitCount: Int,
    val scanDurationMs: Long,
    val logEntry: SoapLogEntry
)

data class ScannedAp(
    val ssid: String,
    val channel: Int,
    val signalStrengthPercent: Int,
    val authMode: String,
    val encryptionType: String
)

data class SetupDeviceDescriptor(
    val host: String,
    val port: Int,
    val friendlyName: String,
    val modelName: String,
    val hardwareSku: String,
    val deviceType: DeviceType,
    val serialNumber: String,
    val macAddress: String,
    val udn: String,
    val firmwareVersion: String,
    val isRtos: Boolean,
    val isIot: Boolean,
    val binaryOption: Boolean,
    val recommendedEncryptionMethod: Int,
    val recommendedAddLengths: Boolean,
    val newAlgo: Boolean = false,
    val setupXmlPath: String = "/setup.xml",
    val macAndSerialSource: String = "GetMetaInfo",
    val rawMetaInfo: String = "",
    val probeTraceSummary: String = ""
)

data class ProvisionResult(
    val success: Boolean,
    val networkStatusCode: Int,
    val statusMessage: String,
    val encryptedPasswordPayload: String,
    val logEntry: SoapLogEntry,
    val closeSetupStatus: String = "",
    val effectiveEncryptionMethod: Int = 1,
    val addedLengthSuffix: Boolean = true,
    val setupDevice: SetupDeviceDescriptor? = null,
    val debugTraceLines: List<String> = emptyList()
)

/**
 * Normalized result of parsing a `<BinaryState>` (and optional `<brightness>` / `<NightMode>`)
 * payload from `SetBinaryState`, `GetBinaryState`, or GENA `<e:propertyset>` XML.
 */
data class ParsedBinaryState(
    val rawFirstToken: String,
    val binaryOn: Boolean?,
    val brightness: Int?,
    val nightModeEnabled: Boolean? = null,
    val isIdempotentError: Boolean = false
)
