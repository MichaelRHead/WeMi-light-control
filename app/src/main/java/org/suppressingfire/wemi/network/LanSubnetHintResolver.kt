package org.suppressingfire.wemi.network

import java.net.URI

/**
 * Pure, deterministic resolver for Stage C `/24` unicast discovery candidate subnets.
 *
 * - On physical Android phones, tablets, and Android TVs (where `localIp` is an RFC 1918 LAN
 *   address), preserves direct `/24` scanning of `localIp`'s subnet (unless an explicit
 *   [manualSubnetOverride] is requested).
 * - Inside ChromeOS ARCVM or other RFC 6598 CGNAT (`100.64.0.0/10`) virtual networks, rejects the
 *   unroutable guest subnet and resolves real physical RFC 1918 `/24` candidates in strict
 *   priority order:
 *   1. Explicit user-supplied [manualSubnetOverride]
 *   2. Active Home's persisted RFC 1918 `subnetPrefix`
 *   3. Remembered switches' IPv4 `/24` prefixes (ordered by frequency)
 *   4. Non-Belkin UPnP peer IPv4s harvested from Stage A SSDP `LOCATION` headers (e.g. router IGD)
 *   5. Private RFC 1918 `LinkProperties.dnsServers`
 */
object LanSubnetHintResolver {

    /**
     * Extracts a private RFC 1918 IPv4 host address from a UPnP SSDP `LOCATION` URL
     * (e.g. `"http://192.168.86.1:5000/rootDesc.xml"` -> `"192.168.86.1"`), returning `null`
     * for non-IPv4, loopback, setup-AP, or CGNAT hosts.
     */
    fun extractRfc1918HostIp(locationUrl: String?): String? {
        val raw = locationUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val host = runCatching { URI(raw).host }.getOrNull()
            ?: raw.substringAfter("://", "")
                .substringBefore('/')
                .substringBefore(':')
        val cleanHost = host.trim()
        if (cleanHost.count { it == '.' } != 3) return null
        return cleanHost.takeIf { LanRuntimeUtils.isPrivateRfc1918Ipv4(it) }
    }

    /**
     * Resolves an ordered, deduplicated list of up to [maxCandidates] RFC 1918 `/24` prefixes
     * eligible for Stage C unicast subnet fallback scanning.
     */
    fun resolveCandidateSubnets(
        localIp: String,
        isArcVm: Boolean,
        manualSubnetOverride: String? = null,
        activeHomeSubnetPrefix: String? = null,
        rememberedDeviceIps: List<String> = emptyList(),
        observedSsdpPeerIps: Collection<String> = emptyList(),
        dnsServerIps: List<String> = emptyList(),
        maxCandidates: Int = 2
    ): List<String> {
        if (maxCandidates <= 0) return emptyList()
        val ordered = LinkedHashSet<String>()

        fun addIfEligible(rawIpOrPrefix: String?) {
            if (ordered.size >= maxCandidates) return
            val normalized = LanRuntimeUtils.normalizeSubnetPrefixOrNull(rawIpOrPrefix) ?: return
            if (LanRuntimeUtils.isCarrierGradeNatIpv4(normalized)) return
            if (!LanRuntimeUtils.isPrivateRfc1918Ipv4(normalized)) return
            ordered.add(normalized)
        }

        fun addByFrequency(ips: Collection<String>) {
            val counts = LinkedHashMap<String, Int>()
            for (ip in ips) {
                val prefix = LanRuntimeUtils.normalizeSubnetPrefixOrNull(ip) ?: continue
                if (LanRuntimeUtils.isCarrierGradeNatIpv4(prefix)) continue
                if (!LanRuntimeUtils.isPrivateRfc1918Ipv4(prefix)) continue
                counts[prefix] = (counts[prefix] ?: 0) + 1
            }
            counts.entries
                .sortedByDescending { it.value }
                .forEach { (prefix, _) -> addIfEligible(prefix) }
        }

        // Tier 1: Explicit user override always takes highest priority.
        addIfEligible(manualSubnetOverride)

        val localIsNatOrArc = isArcVm || LanRuntimeUtils.isCarrierGradeNatIpv4(localIp)
        if (!localIsNatOrArc) {
            // Preserve exact Phone & Android TV behavior: scan only the live LAN interface's /24
            // (plus an explicit manual override if the user requested one).
            addIfEligible(localIp)
            return ordered.toList()
        }

        // ARCVM / RFC 6598 NAT mode: derive physical LAN /24 candidates from Tiers 2..5.
        addIfEligible(activeHomeSubnetPrefix)
        addByFrequency(rememberedDeviceIps)
        addByFrequency(observedSsdpPeerIps)
        dnsServerIps.forEach { addIfEligible(it) }

        return ordered.toList()
    }
}
