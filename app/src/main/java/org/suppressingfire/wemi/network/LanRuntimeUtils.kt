package org.suppressingfire.wemi.network

import android.content.Context
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

/**
 * Shared zero-dependency runtime, network-interface, IPv4 classification, and placeholder-identity
 * helpers used across the UPnP/SOAP client, setup provisioner, network monitor, and Room repository.
 */
object LanRuntimeUtils {

    const val DEFAULT_FALLBACK_MAC = "EC1A59001122"
    const val DEFAULT_PLACEHOLDER_SERIAL = "221517K01018AB"
    const val DEFAULT_LAN_PLACEHOLDER_SERIAL = "221517K01018LAN"
    const val DEFAULT_NEW_PLACEHOLDER_SERIAL = "221517K01018NEW"

    val PLACEHOLDER_SERIALS: Set<String> = setOf(
        "",
        "UNKNOWN",
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
     * Extracts the canonical base UPnP `<UDN>` (stripping any `::urn:...` SSDP `USN` service
     * suffix) and returns `""` when blank or synthesized from a placeholder serial number.
     */
    fun canonicalHardwareUdn(rawUdnOrUsn: String?): String {
        val base = rawUdnOrUsn?.substringBefore("::")?.trim().orEmpty()
        if (base.isEmpty()) return ""
        val upper = base.uppercase(Locale.US)
        val hasPlaceholderSuffix = PLACEHOLDER_SERIALS_UPPER.any { placeholder ->
            placeholder.isNotEmpty() && upper.endsWith("-$placeholder")
        }
        return if (hasPlaceholderSuffix) "" else base
    }

    /**
     * Resolves the highest-priority real hardware identity for endpoint verification
     * (`probeWemoPort`, `executeWithPortHealing`, and `DeviceEntity.concurrencyKey`):
     * returns non-placeholder [serialNumber] first, else non-placeholder canonical [udn], else `""`.
     */
    fun expectedHardwareIdentity(serialNumber: String?, udn: String?): String {
        val cleanSerial = serialNumber?.trim().orEmpty()
        if (!isPlaceholderSerial(cleanSerial)) return cleanSerial
        return canonicalHardwareUdn(udn)
    }

    /**
     * Returns true when executing inside a local JVM Robolectric test runner rather than on
     * physical Android / Android TV hardware.
     */
    fun isRunningInRobolectric(): Boolean {
        return (Build.FINGERPRINT ?: "").contains("robolectric", ignoreCase = true) ||
            runCatching { Class.forName("org.robolectric.Robolectric"); true }.getOrDefault(false)
    }

    private fun parseIpv4OctetsOrNull(raw: String?): List<Int>? {
        val cleaned = raw?.trim()
            ?.substringBefore('/')
            ?.removeSuffix(".x")
            ?.removeSuffix(".X")
            ?.trimEnd('.')
            .orEmpty()
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split('.')
        if (parts.size !in 3..4) return null
        val octets = parts.map { part ->
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            value
        }
        return octets
    }

    /**
     * Normalizes a `/24` subnet prefix or full IPv4 address (e.g. `"192.168.86.0/24"`,
     * `"192.168.86.x"`, `"192.168.86.42"`, or `"192.168.86"`) to its canonical 3-octet prefix
     * `"192.168.86"`, returning `null` for invalid inputs.
     */
    fun normalizeSubnetPrefixOrNull(raw: String?): String? {
        val octets = parseIpv4OctetsOrNull(raw) ?: return null
        return "${octets[0]}.${octets[1]}.${octets[2]}"
    }

    /**
     * Returns true when [ipOrPrefix] falls inside RFC 6598 Carrier-Grade NAT space
     * (`100.64.0.0/10`), which ChromiumOS `patchpanel` uses for ARCVM virtual NICs
     * (`100.115.92.0/24`).
     */
    fun isCarrierGradeNatIpv4(ipOrPrefix: String?): Boolean {
        val octets = parseIpv4OctetsOrNull(ipOrPrefix) ?: return false
        return octets[0] == 100 && octets[1] in 64..127
    }

    /**
     * Returns true when [ip] is the reserved ChromeOS ARCVM `arc0` management/VPN `/30` subnet
     * (`100.115.92.1` or `100.115.92.2`), which has no SSDP `MulticastForwarder` or inbound DNAT.
     */
    fun isArcVmManagementIpv4(ip: String?): Boolean {
        val clean = ip?.trim().orEmpty()
        return clean == "100.115.92.1" || clean == "100.115.92.2"
    }

    /**
     * Returns true when [ipOrPrefix] is a standard home/office RFC 1918 private LAN IPv4 address
     * or `/24` prefix (`192.168.0.0/16`, `172.16.0.0/12`, or `10.0.0.0/8`), excluding Android
     * emulator virtual networking (`10.0.2.*`) and Belkin smart-switch Setup APs (`10.22.22.*`).
     */
    fun isPrivateRfc1918Ipv4(ipOrPrefix: String?): Boolean {
        val octets = parseIpv4OctetsOrNull(ipOrPrefix) ?: return false
        val first = octets[0]
        val second = octets[1]
        val third = octets[2]
        return when {
            first == 192 && second == 168 -> true
            first == 172 && second in 16..31 -> true
            first == 10 -> !(second == 0 && third == 2) && !(second == 22 && third == 22)
            else -> false
        }
    }

    /**
     * Returns true when executing inside ChromeOS's Android runtime (ARCVM / ARC++), detected via
     * ChromiumOS system features, build markers, or `patchpanel`'s `100.115.92.x` guest addressing.
     */
    fun isArcVmRuntime(context: Context? = null, localIpv4: String? = null): Boolean {
        val hasFeature = context?.applicationContext?.packageManager?.let { pm ->
            runCatching {
                pm.hasSystemFeature("org.chromium.arc") ||
                    pm.hasSystemFeature("org.chromium.arc.device_management")
            }.getOrDefault(false)
        } ?: false
        return isArcVmRuntime(
            hasArcSystemFeature = hasFeature,
            buildDevice = Build.DEVICE,
            buildProduct = Build.PRODUCT,
            buildBrand = Build.BRAND,
            localIpv4 = localIpv4,
            isRobolectric = isRunningInRobolectric()
        )
    }

    internal fun isArcVmRuntime(
        hasArcSystemFeature: Boolean,
        buildDevice: String?,
        buildProduct: String?,
        buildBrand: String?,
        localIpv4: String?,
        isRobolectric: Boolean = false
    ): Boolean {
        if (hasArcSystemFeature) return true
        val cleanIp = localIpv4?.trim().orEmpty()
        if (cleanIp.startsWith("100.115.92.")) return true
        if (isRobolectric) return false
        val markers = listOf(buildDevice, buildProduct, buildBrand)
            .mapNotNull { it?.lowercase(Locale.US) }
        return markers.any { value ->
            value.contains("cheets") || value.contains("bertha") || value == "chromium"
        }
    }

    /**
     * Enumerates active non-loopback, non-link-local (`169.254.x.x`) IPv4 interfaces,
     * prioritizing `wlan*` over non-`arc0` `eth*`/`en*` and deprioritizing ChromeOS `arc0`
     * (`100.115.92.2`), VPN tunnels (`tun*`/`tap*`), and cellular (`rmnet*`) interfaces.
     */
    fun resolvePreferredLanInterfaceIpv4(
        fallbackIp: String = "192.168.1.50",
        fallbackInterface: String = "wlan0"
    ): Pair<String, String> {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = mutableListOf<Triple<Int, String, String>>()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase(Locale.US)
                val isDeprioritizedIf = name == "arc0" ||
                    name.startsWith("tun") ||
                    name.startsWith("tap") ||
                    name.startsWith("rmnet") ||
                    name.startsWith("ccmni")
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddr = addr.hostAddress ?: continue
                        if (hostAddr.startsWith("169.254.")) continue
                        val priority = when {
                            isDeprioritizedIf || isArcVmManagementIpv4(hostAddr) -> 3
                            name.startsWith("wlan") -> 0
                            name.startsWith("eth") || name.startsWith("en") -> 1
                            else -> 2
                        }
                        candidates.add(Triple(priority, hostAddr, nif.name))
                    }
                }
            }
            val best = candidates.minByOrNull { it.first }
            if (best != null) best.second to best.third else fallbackIp to fallbackInterface
        } catch (_: Exception) {
            fallbackIp to fallbackInterface
        }
    }
}
