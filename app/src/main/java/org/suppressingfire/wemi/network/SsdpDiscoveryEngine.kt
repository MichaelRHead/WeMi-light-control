package org.suppressingfire.wemi.network

import android.content.Context
import android.net.wifi.WifiManager
import org.suppressingfire.wemi.data.DemoFixtures
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SsdpDiscoveredDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.CopyOnWriteArraySet
import javax.net.SocketFactory

/**
 * Executes UPnP SSDP multicast discovery (`239.255.255.250:1900`, `MulticastLock` + retransmit
 * burst), unicast known-device verification, `/24` unicast subnet sweeps when multicast is
 * blocked by AP isolation or ChromeOS ARCVM NAT, and optional Demo Showroom fallback synthesis.
 */
internal class SsdpDiscoveryEngine(
    private val appContext: Context?,
    private val wifiSocketFactory: SocketFactory = SocketFactory.getDefault(),
    private val resolveLocalIpv4Address: () -> String,
    private val probeWemoPort: suspend (ipAddress: String, preferredPort: Int, expectedSerialOrUdn: String?) -> Int?,
    private val fetchLiveDeviceDescriptor: suspend (
        locationUrl: String,
        usnHeader: String,
        stHeader: String,
        responseTimeMs: Long,
        isLiveUdp: Boolean
    ) -> SsdpDiscoveredDevice?,
    private val nowTime: () -> String
) {

    /**
     * Sweeps a `/24` IPv4 subnet via unicast TCP port probing on the primary Belkin WeMo ports
     * (`49153`, `49152`, `49154`) when UDP multicast is blocked by wireless AP isolation or IGMP snooping.
     */
    suspend fun scanSubnetUnicast(
        subnetPrefix: String,
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        skipIps: Set<String> = emptySet()
    ): List<SsdpDiscoveredDevice> = coroutineScope {
        val cleanPrefix = subnetPrefix.trim().removeSuffix(".x").removeSuffix(".0/24")
        if (cleanPrefix.isBlank() ||
            cleanPrefix.startsWith("10.0.2") ||
            cleanPrefix.startsWith("10.22.22") ||
            cleanPrefix.startsWith("127.") ||
            LanRuntimeUtils.isCarrierGradeNatIpv4(cleanPrefix)
        ) {
            return@coroutineScope emptyList()
        }
        val sweepSemaphore = Semaphore(24)
        val fastPorts = listOf(49153, 49152, 49154, 49151)
        (1..254).mapNotNull { hostOctet ->
            val ip = "$cleanPrefix.$hostOctet"
            if (ip in skipIps) return@mapNotNull null
            async(Dispatchers.IO) {
                sweepSemaphore.withPermit {
                    for (port in fastPorts) {
                        ensureActive()
                        val start = System.currentTimeMillis()
                        try {
                            wifiSocketFactory.createSocket().use { s ->
                                s.connect(InetSocketAddress(ip, port), 180)
                            }
                            val rtt = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                            for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
                                val locUrl = "http://$ip:$port$path"
                                val desc = fetchLiveDeviceDescriptor(
                                    locUrl,
                                    "",
                                    searchTarget,
                                    rtt,
                                    false
                                )
                                if (desc != null) return@withPermit desc
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: ConnectException) {
                            continue
                        } catch (_: Exception) {
                            break
                        }
                    }
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Executes a full UPnP SSDP multicast scan on `239.255.255.250:1900`, acquiring a
     * `WifiManager.MulticastLock` if available, broadcasting `M-SEARCH` packets with retransmission,
     * parsing `LOCATION`/`USN`/`ST` headers, fetching `setup.xml` descriptors, querying live
     * `GetBinaryState` / `GetMetaInfo` telemetry, and falling back to [scanSubnetUnicast] when
     * UDP multicast is dropped on a real LAN.
     */
    suspend fun performSsdpDiscoveryScan(
        searchTarget: String = XmlAndSkuParser.URN_BASIC_EVENT,
        knownLocalDevices: List<DeviceEntity> = emptyList(),
        includeDemoFallbacks: Boolean = false,
        allowUnicastSubnetFallback: Boolean = true,
        onlySweepWhenKnownMissing: Boolean = false,
        activeHomeSubnetPrefix: String? = null,
        manualSubnetOverride: String? = null
    ): SsdpScanReport = withContext(Dispatchers.IO) {
        val scanStartMs = System.currentTimeMillis()
        val isRobolectric = LanRuntimeUtils.isRunningInRobolectric()
        val loopbackPorts = loopbackSsdpUdpPorts.toList()
        val hasLoopbackTargets = loopbackPorts.isNotEmpty()
        val fastMode = hasLoopbackTargets || isRobolectric
        val activeBinding = if (fastMode) null else NetworkMonitor.resolveActiveLanBinding(appContext)
        val localIp = activeBinding?.localIpv4 ?: resolveLocalIpv4Address()
        val isArcVm = LanRuntimeUtils.isArcVmRuntime(appContext, localIp)
        val liveDiscovered = mutableListOf<SsdpDiscoveredDevice>()
        val observedSsdpPeerIps = LinkedHashSet<String>()
        val primaryMSearch = XmlAndSkuParser.buildSsdpMSearchPacket(searchTarget)
        val searchTargets = listOf(searchTarget, XmlAndSkuParser.URN_BASIC_EVENT, "upnp:rootdevice").distinct()

        val wifiManager = appContext?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("wemi_ssdp_multicast_lock")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        if (!fastMode && isArcVm && multicastLock != null) {
            delay(100L) // Allow ChromeOS patchpanel Mojo IPC to enable bidirectional Wi-Fi multicast
        }

        val receiveTimeoutMs = when {
            hasLoopbackTargets -> 250
            fastMode -> 90
            isArcVm -> 700
            else -> 550
        }
        val retransmitAfterMs = if (!fastMode && isArcVm) 750L else receiveTimeoutMs.toLong()
        val receiveWindowMs = when {
            hasLoopbackTargets -> 1000L
            fastMode -> 220L
            isArcVm -> 2000L
            else -> 1250L
        }
        val probeSemaphore = Semaphore(3)

        try {
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                runCatching { socket.timeToLive = 4 }
                val bindAddr = runCatching {
                    if (!fastMode && !isArcVm && localIp != "192.168.1.50" && !localIp.startsWith("127.")) {
                        InetSocketAddress(InetAddress.getByName(localIp), 0)
                    } else {
                        InetSocketAddress(0)
                    }
                }.getOrElse { InetSocketAddress(0) }
                runCatching { socket.bind(bindAddr) }.onFailure { socket.bind(InetSocketAddress(0)) }
                if (!fastMode) {
                    activeBinding?.network?.let { net -> runCatching { net.bindSocket(socket) } }
                    val targetIf = activeBinding?.interfaceName
                        ?.let { name -> runCatching { NetworkInterface.getByName(name) }.getOrNull() }
                        ?: runCatching { NetworkInterface.getByInetAddress(InetAddress.getByName(localIp)) }.getOrNull()
                    if (targetIf != null) runCatching { socket.networkInterface = targetIf }
                }
                socket.soTimeout = receiveTimeoutMs

                val multicastGroup = InetAddress.getByName("239.255.255.250")
                val loopbackAddr = InetAddress.getByName("127.0.0.1")
                fun sendBurst() {
                    for (st in searchTargets) {
                        val payloadBytes = XmlAndSkuParser.buildSsdpMSearchPacket(st).toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(payloadBytes, payloadBytes.size, multicastGroup, 1900)
                        runCatching { socket.send(packet) }
                        for (udpPort in loopbackPorts) {
                            val localPacket = DatagramPacket(payloadBytes, payloadBytes.size, loopbackAddr, udpPort)
                            runCatching { socket.send(localPacket) }
                        }
                    }
                }

                sendBurst()
                val burstStartMs = System.currentTimeMillis()
                var retransmitted = false
                val receiveBuffer = ByteArray(4096)
                val seenLocations = mutableMapOf<String, Triple<Map<String, String>, Long, Int>>()
                val deadline = burstStartMs + receiveWindowMs

                while (System.currentTimeMillis() < deadline) {
                    ensureActive()
                    if (!retransmitted && System.currentTimeMillis() - burstStartMs >= retransmitAfterMs) {
                        sendBurst()
                        retransmitted = true
                    }
                    try {
                        val respPacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                        socket.receive(respPacket)
                        val elapsed = (System.currentTimeMillis() - burstStartMs).coerceAtLeast(1L)
                        val rawText = String(respPacket.data, 0, respPacket.length, Charsets.UTF_8)
                        val headers = XmlAndSkuParser.parseSsdpHeaders(rawText)
                        val loc = headers["LOCATION"]
                        if (!loc.isNullOrEmpty()) {
                            LanSubnetHintResolver.extractRfc1918HostIp(loc)?.let { observedSsdpPeerIps.add(it) }
                            if (!seenLocations.containsKey(loc)) {
                                seenLocations[loc] = Triple(headers, elapsed, respPacket.port)
                                if (hasLoopbackTargets && seenLocations.size >= loopbackPorts.size) {
                                    break
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: SocketTimeoutException) {
                        if (retransmitted && (!hasLoopbackTargets || seenLocations.isNotEmpty())) {
                            break
                        }
                    } catch (_: Exception) {
                        break
                    }
                }

                val orderedSeen = if (loopbackPorts.isNotEmpty()) {
                    seenLocations.entries.sortedBy { (_, triple) ->
                        val portIdx = loopbackPorts.indexOf(triple.third)
                        if (portIdx >= 0) portIdx else Int.MAX_VALUE
                    }
                } else {
                    seenLocations.entries.toList()
                }

                val stageADescriptors = coroutineScope {
                    orderedSeen.map { (locUrl, triple) ->
                        async(Dispatchers.IO) {
                            probeSemaphore.withPermit {
                                val (headers, latencyMs, _) = triple
                                fetchLiveDeviceDescriptor(
                                    locUrl,
                                    headers["USN"].orEmpty(),
                                    headers["ST"].orEmpty().ifEmpty { searchTarget },
                                    latencyMs,
                                    true
                                )
                            }
                        }
                    }.awaitAll()
                }
                for (desc in stageADescriptors) {
                    if (desc != null && liveDiscovered.none { it.matchesEndpointOrSerial(desc) }) {
                        liveDiscovered.add(desc)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Multicast socket restricted in container/emulator environment
        } finally {
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }

        val unseenKnown = knownLocalDevices.filter { known ->
            liveDiscovered.none {
                (it.ipAddress == known.ipAddress && it.port == known.port) ||
                    (!LanRuntimeUtils.isPlaceholderSerial(known.serialNumber) &&
                        !LanRuntimeUtils.isPlaceholderSerial(it.serialNumber) &&
                        it.serialNumber.equals(known.serialNumber, ignoreCase = true)) ||
                    (LanRuntimeUtils.canonicalHardwareUdn(known.udn).let { cleanKnownUdn ->
                        cleanKnownUdn.isNotEmpty() &&
                            cleanKnownUdn.equals(LanRuntimeUtils.canonicalHardwareUdn(it.usn), ignoreCase = true)
                    })
            }
        }
        val stageBDescriptors = coroutineScope {
            unseenKnown.map { known ->
                async(Dispatchers.IO) {
                    probeSemaphore.withPermit {
                        val startProbe = System.currentTimeMillis()
                        val expectedIdentity = LanRuntimeUtils.expectedHardwareIdentity(
                            known.serialNumber,
                            known.udn
                        )
                        val verifiedPort = probeWemoPort(
                            known.ipAddress,
                            known.port,
                            expectedIdentity
                        ) ?: return@withPermit null
                        val rtt = (System.currentTimeMillis() - startProbe).coerceAtLeast(1L)
                        for (path in listOf("/setup.xml", "/Lightsetup.xml")) {
                            val locUrl = "http://${known.ipAddress}:$verifiedPort$path"
                            val desc = fetchLiveDeviceDescriptor(
                                locUrl,
                                known.udn,
                                searchTarget,
                                rtt,
                                false
                            )
                            if (desc != null) return@withPermit desc
                        }
                        null
                    }
                }
            }.awaitAll()
        }
        for (desc in stageBDescriptors) {
            if (desc != null && liveDiscovered.none { it.matchesEndpointOrSerial(desc) }) {
                liveDiscovered.add(desc)
            }
        }

        val scannedSubnets = mutableListOf<String>()
        val hasManualOverride = !LanRuntimeUtils.normalizeSubnetPrefixOrNull(manualSubnetOverride).isNullOrBlank()
        if (shouldRunUnicastSubnetFallback(
                liveDiscovered = liveDiscovered,
                knownLocalDevices = knownLocalDevices,
                allowUnicastSubnetFallback = allowUnicastSubnetFallback,
                fastMode = fastMode,
                includeDemoFallbacks = includeDemoFallbacks,
                onlyWhenKnownMissing = onlySweepWhenKnownMissing,
                hasManualSubnetOverride = hasManualOverride
            )
        ) {
            val candidateSubnets = LanSubnetHintResolver.resolveCandidateSubnets(
                localIp = localIp,
                isArcVm = isArcVm,
                manualSubnetOverride = manualSubnetOverride,
                activeHomeSubnetPrefix = activeHomeSubnetPrefix,
                rememberedDeviceIps = knownLocalDevices.map { it.ipAddress },
                observedSsdpPeerIps = observedSsdpPeerIps,
                dnsServerIps = activeBinding?.dnsServersIpv4.orEmpty()
            )
            for (prefix in candidateSubnets) {
                scannedSubnets.add(prefix)
                val skipIps = buildSet {
                    add(localIp)
                    liveDiscovered.forEach { add(it.ipAddress) }
                }
                val swept = scanSubnetUnicast(prefix, searchTarget, skipIps = skipIps)
                for (desc in swept) {
                    if (liveDiscovered.none { it.matchesEndpointOrSerial(desc) }) {
                        liveDiscovered.add(desc)
                    }
                }
                if (swept.isNotEmpty()) break
            }
        }

        val combinedList = mutableListOf<SsdpDiscoveredDevice>()
        combinedList.addAll(liveDiscovered)

        if (includeDemoFallbacks) {
            DemoFixtures.appendSsdpDemoFallbacks(
                combinedList = combinedList,
                knownLocalDevices = knownLocalDevices,
                localIp = localIp,
                searchTarget = searchTarget,
                isCgnatSubnet = LanRuntimeUtils.isCarrierGradeNatIpv4(localIp)
            )
        }

        val totalMs = (System.currentTimeMillis() - scanStartMs).coerceAtLeast(1L)
        val endpointLabel = buildString {
            append("udp://239.255.255.250:1900 (")
            if (isArcVm) append("ChromeOS VM • ")
            append("Local IP: $localIp")
            if (scannedSubnets.isNotEmpty()) append(" • Swept: ${scannedSubnets.joinToString(",") { "$it.0/24" }}")
            append(")")
        }
        val log = SoapLogEntry(
            timestamp = nowTime(),
            deviceName = "SSDP M-SEARCH Scanner",
            endpoint = endpointLabel,
            soapAction = "M-SEARCH ST: $searchTarget",
            statusText = "Discovered ${combinedList.size} UPnP smart switch device(s) in ${totalMs}ms",
            xmlSnippet = primaryMSearch.trim(),
            isLiveLanSuccess = liveDiscovered.isNotEmpty() || includeDemoFallbacks
        )

        SsdpScanReport(
            localInterfaceIp = localIp,
            searchTargetUsed = searchTarget,
            mSearchRequestRaw = primaryMSearch.trim(),
            discoveredDevices = combinedList,
            rawSocketHitCount = liveDiscovered.size,
            scanDurationMs = totalMs,
            logEntry = log,
            isArcVmEnvironment = isArcVm,
            scannedSubnets = scannedSubnets.toList()
        )
    }

    companion object {
        private val loopbackSsdpUdpPorts = CopyOnWriteArraySet<Int>()

        fun registerLoopbackSsdpPort(udpPort: Int) { loopbackSsdpUdpPorts.add(udpPort) }

        fun unregisterLoopbackSsdpPort(udpPort: Int) { loopbackSsdpUdpPorts.remove(udpPort) }

        /**
         * Determines whether Stage C (`/24` unicast sweep) should run after Stages A & B:
         * - Skipped when disabled, in fast/loopback/Robolectric mode, or in Demo Showroom mode.
         * - Always runs when [hasManualSubnetOverride] is true (explicit user subnet scan).
         * - Otherwise skipped when UDP multicast already received a live response (`isLiveUdpResponse`),
         *   runs unconditionally during explicit LAN scans (`onlyWhenKnownMissing == false`) or on an
         *   empty home, and runs during home reconnects only when a remembered switch is still missing.
         */
        internal fun shouldRunUnicastSubnetFallback(
            liveDiscovered: List<SsdpDiscoveredDevice>,
            knownLocalDevices: List<DeviceEntity>,
            allowUnicastSubnetFallback: Boolean,
            fastMode: Boolean,
            includeDemoFallbacks: Boolean,
            onlyWhenKnownMissing: Boolean,
            hasManualSubnetOverride: Boolean = false
        ): Boolean {
            if (!allowUnicastSubnetFallback || fastMode || includeDemoFallbacks) return false
            if (hasManualSubnetOverride) return true
            if (liveDiscovered.any { it.isLiveUdpResponse }) return false
            if (!onlyWhenKnownMissing || knownLocalDevices.isEmpty()) return true
            return knownLocalDevices.any { known ->
                liveDiscovered.none { discovered ->
                    known.matchesDiscovered(discovered, requireSamePortWhenBlankSerial = false)
                }
            }
        }
    }
}
