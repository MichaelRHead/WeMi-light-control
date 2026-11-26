package org.suppressingfire.wemi.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice

/**
 * Fetches and enriches live UPnP `/setup.xml` / `/Lightsetup.xml` device descriptors along with
 * `metainfo1#GetMetaInfo` and `basicevent1#GetSignalStrength` hardware telemetry on behalf of
 * [NetworkClient].
 */
internal class DeviceDescriptorFetcher(
    private val httpClient: LanHttpClient,
    private val probeWemoPort: suspend (
        ipAddress: String,
        preferredPort: Int,
        expectedSerialOrUdn: String?
    ) -> Int?,
    private val getBinaryState: suspend (DeviceEntity) -> SoapCallResult
) {
    /**
     * Queries `urn:Belkin:service:metainfo:1#GetMetaInfo` on `/upnp/control/metainfo1`.
     */
    suspend fun getMetaInfo(ipAddress: String, port: Int): SwitchMetaInfo? = withContext(Dispatchers.IO) {
        val soapBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_META_INFO,
            action = "GetMetaInfo"
        )
        try {
            val resp = httpClient.postXml(
                url = "http://$ipAddress:$port/upnp/control/metainfo1",
                soapBody = soapBody,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_META_INFO, "GetMetaInfo")
            )
            if (!resp.isSuccessful) return@withContext null
            XmlAndSkuParser.parseMetaInfoSoapResponse(resp.body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Queries `urn:Belkin:service:basicevent:1#GetSignalStrength` on `/upnp/control/basicevent1`.
     */
    suspend fun getSignalStrength(ipAddress: String, port: Int): Int? = withContext(Dispatchers.IO) {
        val soapBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
            action = "GetSignalStrength"
        )
        try {
            val resp = httpClient.postXml(
                url = "http://$ipAddress:$port/upnp/control/basicevent1",
                soapBody = soapBody,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, "GetSignalStrength")
            )
            if (!resp.isSuccessful) return@withContext null
            XmlAndSkuParser.parseSignalStrengthSoapResponse(resp.body)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fetches a full [SsdpDiscoveredDevice] descriptor from a known `IP:port` and enriches it with
     * live `GetBinaryState`, `GetMetaInfo` (`SKU` + 2.4 GHz `SSID`), and `GetSignalStrength` telemetry.
     */
    suspend fun fetchLiveDeviceDescriptor(
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ): SsdpDiscoveredDevice? = withContext(Dispatchers.IO) {
        try {
            val resp = httpClient.get(locationUrl)
            if (!resp.isSuccessful) return@withContext null
            val xml = resp.body
            val isBelkinTarget = xml.contains("Belkin", ignoreCase = true) ||
                xml.contains("WeMo", ignoreCase = true) ||
                xml.contains("WeMi", ignoreCase = true) ||
                xml.contains("basicevent", ignoreCase = true) ||
                stHeader.contains("Belkin", ignoreCase = true)
            if (!isBelkinTarget) return@withContext null

            val base = XmlAndSkuParser.parseSetupXmlDescriptor(
                xml = xml,
                locationUrl = locationUrl,
                usnHeader = usnHeader,
                stHeader = stHeader,
                responseTimeMs = responseTimeMs,
                isLiveUdp = isLiveUdp
            )

            val probeEntity = DeviceEntity(
                name = base.name,
                ipAddress = base.ipAddress,
                port = base.port,
                serialNumber = base.serialNumber,
                deviceType = base.deviceType.name
            )
            val stateProbe = getBinaryState(probeEntity)
            val meta = getMetaInfo(base.ipAddress, base.port)
            val rssi = getSignalStrength(base.ipAddress, base.port)

            val enrichedSku = if (meta != null && meta.deviceSku.isNotBlank()) {
                XmlAndSkuParser.classifyHardwareSku(
                    modelName = base.modelName,
                    udn = base.usn,
                    serialNumber = base.serialNumber,
                    metaSku = meta.deviceSku
                )
            } else {
                base.hardwareSku
            }

            val enrichedFirmware = meta?.firmwareVersion?.takeIf { it.isNotBlank() } ?: base.firmwareVersion
            val refreshedRulesHint = if (enrichedFirmware != base.firmwareVersion) {
                XmlAndSkuParser.inferRulesCapabilityFromSetupXml(
                    xml = xml,
                    locationUrl = locationUrl,
                    udn = base.usn,
                    firmwareVersion = enrichedFirmware
                )
            } else {
                null
            }

            base.copy(
                isOn = stateProbe.binaryStateOn ?: base.isOn,
                brightness = stateProbe.brightness ?: base.brightness,
                serialNumber = meta?.serialNumber?.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) } ?: base.serialNumber,
                firmwareVersion = enrichedFirmware,
                hardwareSku = enrichedSku,
                macAddress = meta?.macAddress?.takeIf { it.isNotBlank() } ?: base.macAddress,
                wifiSsid = meta?.accessPointSsid.orEmpty(),
                signalStrengthPercent = rssi ?: 85,
                rulesCapabilityHint = refreshedRulesHint?.capability ?: base.rulesCapabilityHint,
                longPressCapableHint = refreshedRulesHint?.longPressCapable ?: base.longPressCapableHint
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Probes a single IP address across all 9 switch ports (`probeWemoPort`) and fetches its full
     * `/setup.xml` (or `/Lightsetup.xml`), `GetBinaryState`, `GetMetaInfo`, and `GetSignalStrength`
     * descriptor so newly added devices learn their real port, serial number, MAC, SKU, and state.
     */
    suspend fun probeAndFetchDeviceDescriptor(
        ipAddress: String,
        preferredPort: Int = 49153,
        expectedSerialOrUdn: String? = null
    ): SsdpDiscoveredDevice? = withContext(Dispatchers.IO) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isEmpty()) return@withContext null
        val startMs = System.currentTimeMillis()
        val resolvedPort = probeWemoPort(cleanIp, preferredPort, expectedSerialOrUdn) ?: return@withContext null
        val rtt = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
            val desc = fetchLiveDeviceDescriptor(
                locationUrl = "http://$cleanIp:$resolvedPort$path",
                usnHeader = "",
                stHeader = XmlAndSkuParser.URN_BASIC_EVENT,
                responseTimeMs = rtt,
                isLiveUdp = false
            )
            if (desc != null) return@withContext desc
        }
        null
    }
}
