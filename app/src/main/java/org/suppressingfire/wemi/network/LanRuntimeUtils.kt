package org.suppressingfire.wemi.network

import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Shared zero-dependency runtime, network-interface, and placeholder-identity helpers
 * used across the UPnP/SOAP client, setup provisioner, network monitor, and Room repository.
 */
object LanRuntimeUtils {

    const val DEFAULT_FALLBACK_MAC = "EC1A59001122"
    const val DEFAULT_PLACEHOLDER_SERIAL = "221517K01018AB"
    const val DEFAULT_LAN_PLACEHOLDER_SERIAL = "221517K01018LAN"
    const val DEFAULT_NEW_PLACEHOLDER_SERIAL = "221517K01018NEW"

    val PLACEHOLDER_SERIALS: Set<String> = setOf(
        "",
        DEFAULT_PLACEHOLDER_SERIAL,
        DEFAULT_LAN_PLACEHOLDER_SERIAL,
        DEFAULT_NEW_PLACEHOLDER_SERIAL
    )

    private val PLACEHOLDER_SERIALS_UPPER: Set<String> =
        PLACEHOLDER_SERIALS.map { it.uppercase(Locale.US) }.toSet()

    /**
     * Returns true when [serial] is null, blank, or one of the synthetic placeholder serials
     * assigned before a live hardware descriptor (`setup.xml` / `GetMetaInfo`) has been probed.
     */
    fun isPlaceholderSerial(serial: String?): Boolean {
        val clean = serial?.trim().orEmpty()
        return clean.isEmpty() || clean.uppercase(Locale.US) in PLACEHOLDER_SERIALS_UPPER
    }

    /**
     * Returns true when executing inside a local JVM Robolectric test runner rather than on
     * physical Android / Android TV hardware.
     */
    fun isRunningInRobolectric(): Boolean {
        return (Build.FINGERPRINT ?: "").contains("robolectric", ignoreCase = true) ||
            runCatching { Class.forName("org.robolectric.Robolectric"); true }.getOrDefault(false)
    }

    /**
     * Enumerates active non-loopback, non-link-local (`169.254.x.x`) IPv4 interfaces,
     * prioritizing `wlan*` over `eth*`/`en*`, and returns `(ipv4Address, interfaceName)`.
     */
    fun resolvePreferredLanInterfaceIpv4(
        fallbackIp: String = "192.168.1.50",
        fallbackInterface: String = "wlan0"
    ): Pair<String, String> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val sorted = interfaces.sortedBy { nif ->
                val name = nif.name.lowercase(Locale.US)
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") || name.startsWith("en") -> 1
                    else -> 2
                }
            }
            for (nif in sorted) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (!hostAddr.startsWith("169.254.")) {
                            return hostAddr to nif.name
                        }
                    }
                }
            }
            fallbackIp to fallbackInterface
        } catch (_: Exception) {
            fallbackIp to fallbackInterface
        }
    }
}
