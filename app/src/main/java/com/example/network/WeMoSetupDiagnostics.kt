package com.example.network

/**
 * Structured diagnostic snapshot and human/screenshot-readable formatter for zero-cloud
 * `10.22.22.1` Belkin WeMo Setup AP probing, 2.4 GHz AP scanning, and `WiFiSetup1` provisioning.
 */
data class WeMoSetupDebugSnapshot(
    val timestamp: String = "",
    val phoneLocalIp: String = "",
    val phoneGatewayIp: String = "",
    val phoneInterface: String = "wlan0",
    val isWemoSetupSubnet: Boolean = false,
    val isWifiSocketBound: Boolean = false,
    val isDemoMode: Boolean = false,
    val targetApHost: String = "10.22.22.1",
    val probeSummary: String = "Not probed yet",
    val detectedDevice: WeMoSetupDeviceDescriptor? = null,
    val apScanSummary: String = "Not scanned yet",
    val scannedAps: List<WeMoScannedAp> = emptyList(),
    val traceLines: List<String> = emptyList()
) {
    /**
     * Formats all setup telemetry into compact, high-signal lines designed to fit inside a single
     * phone screenshot in `ProvisionWeMoApDialog` (`provision_debug_panel`) or clipboard paste.
     */
    fun toDisplayLines(selectedSsid: String = ""): List<String> {
        val lines = mutableListOf<String>()
        val modeTag = if (isDemoMode) "DEMO" else "REAL-LAN"
        lines.add(
            "[NET] mode=$modeTag ip=$phoneLocalIp ($phoneInterface) gw=$phoneGatewayIp setupSubnet=$isWemoSetupSubnet wifiBound=$isWifiSocketBound"
        )
        lines.add("[PROBE] host=$targetApHost -> $probeSummary")
        if (detectedDevice != null) {
            val d = detectedDevice
            lines.add(
                "[XML] ${d.setupXmlPath} :${d.port} name=\"${d.friendlyName}\" model=${d.modelName} sku=${d.hardwareSku} fw=${d.firmwareVersion}"
            )
            lines.add(
                "[FLAGS] rtos=${if (d.isRtos) 1 else 0} iot=${if (d.isIot) 1 else 0} binaryOption=${if (d.binaryOption) 1 else 0} new_algo=${if (d.newAlgo) 1 else 0} -> autoMethod=${d.recommendedEncryptionMethod} autoLen=${d.recommendedAddLengths}"
            )
            lines.add(
                "[ID] mac=${d.macAddress} sn=${d.serialNumber} (src=${d.macAndSerialSource}) meta=\"${d.rawMetaInfo.ifBlank { "none" }}\""
            )
        } else {
            lines.add("[XML/ID] No WeMo setup descriptor read from $targetApHost")
        }
        lines.add("[SCAN] $apScanSummary")
        if (selectedSsid.isNotBlank() && scannedAps.isNotEmpty()) {
            val match = scannedAps.firstOrNull { it.ssid.equals(selectedSsid.trim(), ignoreCase = true) }
            if (match != null) {
                lines.add(
                    "[TARGET AP] \"${match.ssid}\" found in ApList: Ch=${match.channel} Auth=${match.authMode}/${match.encryptionType} Signal=${match.signalStrengthPercent}%"
                )
            } else {
                val sample = scannedAps.take(4).joinToString(", ") { "${it.ssid}(Ch${it.channel})" }
                lines.add(
                    "[TARGET AP] WARN: \"$selectedSsid\" NOT in ApList! Seen: [$sample]"
                )
            }
        }
        traceLines.forEach { line ->
            lines.add(line)
        }
        return lines
    }

    fun toClipboardText(selectedSsid: String = ""): String {
        return buildString {
            appendLine("=== WeMi Setup AP Diagnostic Report ($timestamp) ===")
            toDisplayLines(selectedSsid).forEach { appendLine(it) }
        }.trim()
    }
}
