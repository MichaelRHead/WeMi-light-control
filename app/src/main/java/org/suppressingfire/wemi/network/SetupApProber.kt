package org.suppressingfire.wemi.network

import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.suppressingfire.wemi.data.DeviceType

/**
 * Probes factory-reset `10.22.22.1` smart-switch Setup APs across all 9 UPnP ports (`49152..49159`)
 * for `/setup.xml` / `/Lightsetup.xml` + `GetMetaInfo` / `GetMacAddr` hardware capabilities and
 * scans nearby 2.4 GHz Wi-Fi APs via `WiFiSetup1#GetApList` / `GetNetworkList` on behalf of
 * [ProvisionerClient].
 */
internal class SetupApProber(
    private val wifiSocketFactory: SocketFactory,
    private val httpClient: LanHttpClient
) {
    private val setupPorts = listOf(49152, 49153, 49154, 49151, 49155, 49156, 49157, 49158, 49159)

    @Volatile
    var lastProbeDiagnosticSummary: String = "Not probed yet"
        private set

    @Volatile
    var lastApScanDiagnosticSummary: String = "Not scanned yet"
        private set

    internal fun extractXmlTag(xml: String, tag: String): String? {
        return Regex("<$tag>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
            .find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Probes the Setup AP at [apHost] across all 9 UPnP ports for `/setup.xml` or `/Lightsetup.xml`,
     * and enriches metadata via `metainfo1#GetMetaInfo` (with `/setup.xml` and `basicevent1#GetMacAddr` fallbacks).
     */
    suspend fun probeSetupDevice(
        apHost: String,
        preferredPort: Int = 49152
    ): SetupDeviceDescriptor? = withContext(Dispatchers.IO) {
        val isLoopback = apHost == "127.0.0.1" || apHost.equals("localhost", ignoreCase = true)
        val loopbackExtraPorts = if (isLoopback) NetworkClient.currentLoopbackCandidatePorts() else emptyList()
        val candidates = (listOf(preferredPort) + loopbackExtraPorts + setupPorts).distinct()
        val paths = listOf("/setup.xml", "/Lightsetup.xml")
        val tcpConnectTimeoutMs = if (isLoopback) 550 else 1500

        var foundPort: Int? = null
        var foundPath = "/setup.xml"
        var setupXml: String? = null
        val portLogs = mutableListOf<String>()

        for (port in candidates) {
            var hostUnreachable = false
            val tcpStartMs = System.currentTimeMillis()
            try {
                wifiSocketFactory.createSocket().use { sock ->
                    sock.connect(InetSocketAddress(apHost, port), tcpConnectTimeoutMs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: ConnectException) {
                portLogs.add(":$port=REFUSED")
                continue
            } catch (_: SocketTimeoutException) {
                val elapsed = System.currentTimeMillis() - tcpStartMs
                portLogs.add(":$port=TCP_TIMEOUT(${elapsed}ms)")
                hostUnreachable = true
            } catch (_: NoRouteToHostException) {
                portLogs.add(":$port=NO_ROUTE")
                hostUnreachable = true
            } catch (e: Exception) {
                portLogs.add(":$port=${e.javaClass.simpleName}")
                continue
            }

            if (hostUnreachable) {
                break
            }

            for (path in paths) {
                val reqStartMs = System.currentTimeMillis()
                try {
                    val resp = httpClient.get(
                        url = "http://$apHost:$port$path",
                        headers = mapOf("User-Agent" to "WeMiSwitchControl/1.0 UPnP/1.1")
                    )
                    val elapsed = System.currentTimeMillis() - reqStartMs
                    val body = resp.body
                    if (resp.isSuccessful && body.isNotBlank() &&
                        (body.contains("<device", ignoreCase = true) ||
                            body.contains("Belkin", ignoreCase = true) ||
                            body.contains("WeMo", ignoreCase = true))
                    ) {
                        foundPort = port
                        foundPath = path
                        setupXml = body
                        portLogs.add(":$port$path=HTTP_200(${elapsed}ms,${body.length}B)")
                        break
                    } else {
                        portLogs.add(":$port$path=HTTP_${resp.code}(${elapsed}ms)")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val elapsed = System.currentTimeMillis() - reqStartMs
                    portLogs.add(":$port$path=${e.javaClass.simpleName}(${elapsed}ms)")
                }
            }
            if (foundPort != null) break
        }

        val traceSummary = portLogs.joinToString(" ")
        lastProbeDiagnosticSummary = traceSummary.ifBlank { "No ports probed" }

        val resolvedPort = foundPort ?: return@withContext null
        val xml = setupXml.orEmpty()

        val friendlyName = extractXmlTag(xml, "friendlyName")?.ifBlank { null } ?: "Smart Switch"
        val modelName = extractXmlTag(xml, "modelName")?.ifBlank { null } ?: "LightSwitch"
        val deviceTypeUrn = extractXmlTag(xml, "deviceType").orEmpty()
        val xmlSerial = extractXmlTag(xml, "serialNumber").orEmpty().trim()
        val xmlMac = extractXmlTag(xml, "macAddress").orEmpty()
            .replace(":", "").replace("-", "").trim()
        val udn = extractXmlTag(xml, "UDN").orEmpty().trim()
        val xmlFirmware = extractXmlTag(xml, "firmwareVersion").orEmpty().trim()

        val isRtos = extractXmlTag(xml, "rtos") == "1"
        val isIot = extractXmlTag(xml, "iot") == "1"
        val binaryOption = extractXmlTag(xml, "binaryOption") == "1"
        val newAlgo = extractXmlTag(xml, "new_algo") == "1"

        val (rawMetaString, meta) = fetchSetupMetaInfo(apHost, resolvedPort)
        val metaMac = meta?.macAddress?.replace(":", "")?.replace("-", "")?.trim()?.ifBlank { null }
        val rawMetaSerial = meta?.serialNumber?.trim()?.ifBlank { null }
        val metaSerial = rawMetaSerial?.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) }
        val validXmlSerial = xmlSerial.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) }

        val basicMac = if (metaMac == null && xmlMac.isBlank()) {
            fetchBasicEventTag(apHost, resolvedPort, "GetMacAddr", "MacAddr")
                ?.replace(":", "")?.replace("-", "")?.trim()?.ifBlank { null }
        } else {
            null
        }
        val basicSerial = if (metaSerial == null && validXmlSerial == null) {
            fetchBasicEventTag(apHost, resolvedPort, "GetSerialNo", "SerialNo")
                ?.trim()
                ?.takeIf { !LanRuntimeUtils.isPlaceholderSerial(it) }
        } else {
            null
        }

        val macSource = when {
            metaMac != null && metaSerial != null -> "GetMetaInfo"
            metaMac != null && validXmlSerial != null -> "GetMetaInfo+setup.xml"
            xmlMac.isNotBlank() && validXmlSerial != null -> "setup.xml"
            basicMac != null || basicSerial != null -> "basicevent1#GetMacAddr"
            metaMac != null || xmlMac.isNotBlank() -> "partial(xml/meta)"
            else -> "MISSING(Fallback!)"
        }

        val mac = metaMac ?: xmlMac.ifBlank { null } ?: basicMac ?: LanRuntimeUtils.DEFAULT_FALLBACK_MAC
        val serial = metaSerial ?: validXmlSerial ?: basicSerial
            ?: rawMetaSerial ?: xmlSerial.ifBlank { null } ?: LanRuntimeUtils.DEFAULT_PLACEHOLDER_SERIAL
        val sku = meta?.deviceSku?.ifBlank { null } ?: modelName
        val firmware = meta?.firmwareVersion?.ifBlank { null } ?: xmlFirmware

        val parsedType = when {
            modelName.contains("Dimmer", ignoreCase = true) ||
                deviceTypeUrn.contains("dimmer", ignoreCase = true) ||
                sku.contains("F7C059", ignoreCase = true) ||
                sku.contains("WDS060", ignoreCase = true) -> DeviceType.LIGHTBULB_DIMMER
            modelName.contains("Sensor", ignoreCase = true) ||
                modelName.contains("Motion", ignoreCase = true) ||
                deviceTypeUrn.contains("sensor", ignoreCase = true) -> DeviceType.MOTION_SENSOR
            else -> DeviceType.SWITCH_PLUG
        }

        val recommendedMethod = when {
            isRtos && !isIot -> 2
            binaryOption -> 3
            newAlgo -> 2
            sku.contains("WLS040", ignoreCase = true) ||
                sku.contains("WDS060", ignoreCase = true) ||
                sku.contains("WSP080", ignoreCase = true) -> 2
            else -> 1
        }
        val recommendedAddLengths = recommendedMethod != 2

        SetupDeviceDescriptor(
            host = apHost,
            port = resolvedPort,
            friendlyName = friendlyName,
            modelName = modelName,
            hardwareSku = sku,
            deviceType = parsedType,
            serialNumber = serial,
            macAddress = mac,
            udn = udn,
            firmwareVersion = firmware,
            isRtos = isRtos,
            isIot = isIot,
            binaryOption = binaryOption,
            recommendedEncryptionMethod = recommendedMethod,
            recommendedAddLengths = recommendedAddLengths,
            newAlgo = newAlgo,
            setupXmlPath = foundPath,
            macAndSerialSource = macSource,
            rawMetaInfo = rawMetaString,
            probeTraceSummary = traceSummary
        )
    }

    private fun fetchSetupMetaInfo(apHost: String, port: Int): Pair<String, SwitchMetaInfo?> {
        val soapBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_META_INFO,
            action = "GetMetaInfo"
        )
        return try {
            val resp = httpClient.postXml(
                url = "http://$apHost:$port/upnp/control/metainfo1",
                soapBody = soapBody,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_META_INFO, "GetMetaInfo")
            )
            if (!resp.isSuccessful) {
                return "HTTP_${resp.code}" to null
            }
            val rawTag = extractXmlTag(resp.body, "MetaInfo").orEmpty()
            rawTag to XmlAndSkuParser.parseMetaInfoSoapResponse(resp.body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "ERR_${e.javaClass.simpleName}" to null
        }
    }

    private fun fetchBasicEventTag(apHost: String, port: Int, action: String, responseTag: String): String? {
        val soapBody = XmlAndSkuParser.buildSoapEnvelope(
            serviceUrn = XmlAndSkuParser.URN_BASIC_EVENT,
            action = action
        )
        return try {
            val resp = httpClient.postXml(
                url = "http://$apHost:$port/upnp/control/basicevent1",
                soapBody = soapBody,
                soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_BASIC_EVENT, action)
            )
            if (!resp.isSuccessful) return null
            extractXmlTag(resp.body, responseTag)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAvailableApList(
        apHost: String,
        apPort: Int = 49152,
        knownDescriptor: SetupDeviceDescriptor? = null
    ): List<ScannedAp> = withContext(Dispatchers.IO) {
        val descriptor = knownDescriptor ?: probeSetupDevice(apHost, apPort)
        val resolvedPort = descriptor?.port ?: apPort
        val actions = listOf("GetApList", "GetNetworkList")
        val actionLogs = mutableListOf<String>()

        for (action in actions) {
            val soapBody = XmlAndSkuParser.buildSoapEnvelope(
                serviceUrn = XmlAndSkuParser.URN_WIFI_SETUP,
                action = action
            )
            val startMs = System.currentTimeMillis()
            try {
                val resp = httpClient.postXml(
                    url = "http://$apHost:$resolvedPort/upnp/control/WiFiSetup1",
                    soapBody = soapBody,
                    soapAction = XmlAndSkuParser.soapActionHeader(XmlAndSkuParser.URN_WIFI_SETUP, action)
                )
                val elapsed = System.currentTimeMillis() - startMs
                val xml = resp.body
                if (resp.isSuccessful) {
                    val parsed = XmlAndSkuParser.parseApListXml(xml)
                    actionLogs.add("$action=HTTP_200(${elapsed}ms,count=${parsed.size})")
                    if (parsed.isNotEmpty()) {
                        lastApScanDiagnosticSummary = actionLogs.joinToString(" ")
                        return@withContext parsed
                    }
                } else {
                    actionLogs.add("$action=HTTP_${resp.code}(${elapsed}ms)")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startMs
                actionLogs.add("$action=${e.javaClass.simpleName}(${elapsed}ms)")
                if (descriptor == null) break
            }
        }
        lastApScanDiagnosticSummary = actionLogs.joinToString(" ").ifBlank { "No AP scan response" }
        emptyList()
    }
}
