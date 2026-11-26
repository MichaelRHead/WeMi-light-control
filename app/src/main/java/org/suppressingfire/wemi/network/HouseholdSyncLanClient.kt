package org.suppressingfire.wemi.network

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import javax.net.SocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class HouseholdSyncPushResult(
    val success: Boolean,
    val statusMessage: String,
    val httpCode: Int = 0
)

/**
 * Local-LAN client for discovering nearby WeMi devices waiting in Receive/Pair mode (`UDP 49190`)
 * and pushing PIN-encrypted [HouseholdSyncBundle] payloads via [LanHttpClient].
 */
class HouseholdSyncLanClient(
    private val appContext: Context? = null,
    private val wifiSocketFactory: SocketFactory = NetworkMonitor.createWifiBoundSocketFactory(appContext),
    private val localIpProvider: () -> String = { LanRuntimeUtils.resolvePreferredLanInterfaceIpv4().first },
    private val allowLoopbackForTests: Boolean = LanRuntimeUtils.isRunningInRobolectric()
) {
    companion object {
        internal const val MAX_DISCOVERED_PEERS = 16
        internal const val MAX_DISCOVERY_PACKETS = 128
        private val PORT_REGEX = Regex("""\bport=(\d{1,5})\b""")
        private val ROLE_REGEX = Regex("""\brole=(TV|DEVICE)\b""")
    }

    private val httpClient = LanHttpClient(
        socketFactory = wifiSocketFactory,
        connectTimeoutMs = 1_500,
        readTimeoutMs = 5_000,
        allowLoopbackForTests = allowLoopbackForTests
    )

    /**
     * Broadcasts a lightweight `WEMI-SYNC-DISCOVER v1` UDP probe and listens for active WeMi
     * receivers on the local LAN (plus `127.0.0.1` in Robolectric loopback tests).
     */
    suspend fun discoverPeers(
        discoveryUdpPort: Int = HouseholdSyncLanServer.DEFAULT_DISCOVERY_UDP_PORT,
        timeoutMs: Long = 850L
    ): List<HouseholdSyncPeer> = withContext(Dispatchers.IO) {
        if (discoveryUdpPort !in 1..65535) return@withContext emptyList()
        val found = LinkedHashMap<String, HouseholdSyncPeer>()
        var multicastLock: WifiManager.MulticastLock? = null
        try {
            runCatching {
                val wifiMgr = appContext?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiMgr?.createMulticastLock("WeMiSyncDiscoverLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
            val activeBinding = if (allowLoopbackForTests) null else NetworkMonitor.resolveActiveLanBinding(appContext)
            val localIp = localIpProvider().trim()
            MulticastSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                runCatching { socket.timeToLive = 4 }
                socket.bind(InetSocketAddress(0))
                if (!allowLoopbackForTests) {
                    activeBinding?.network?.let { net -> runCatching { net.bindSocket(socket) } }
                    val targetIf = activeBinding?.interfaceName
                        ?.let { name -> runCatching { NetworkInterface.getByName(name) }.getOrNull() }
                        ?: runCatching {
                            if (LanRuntimeUtils.isPrivateRfc1918Ipv4(localIp)) {
                                NetworkInterface.getByInetAddress(InetAddress.getByName(localIp))
                            } else {
                                null
                            }
                        }.getOrNull()
                    if (targetIf != null) runCatching { socket.networkInterface = targetIf }
                }
                socket.soTimeout = 180
                val probeBytes = HouseholdSyncLanServer.DISCOVERY_PROBE_PREFIX.toByteArray(Charsets.UTF_8)
                val targets = buildProbeTargets()
                for (targetIp in targets) {
                    ensureActive()
                    runCatching {
                        val addr = InetAddress.getByName(targetIp)
                        socket.send(DatagramPacket(probeBytes, probeBytes.size, addr, discoveryUdpPort))
                    }
                }

                val deadlineMs = System.currentTimeMillis() + timeoutMs.coerceIn(150L, 3_000L)
                val buf = ByteArray(512)
                var packetsSeen = 0
                while (System.currentTimeMillis() < deadlineMs && packetsSeen < MAX_DISCOVERY_PACKETS) {
                    ensureActive()
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        packetsSeen++
                        val senderIp = packet.address?.hostAddress.orEmpty()
                        if (!LanEndpointPolicy.isAllowedLanHost(senderIp, allowLoopback = allowLoopbackForTests)) {
                            continue
                        }
                        val line = String(packet.data, packet.offset, packet.length, Charsets.UTF_8).trim()
                        parseDiscoveryReply(senderIp, line)?.let { peer ->
                            val peerKey = if (allowLoopbackForTests && senderIp == "127.0.0.1") {
                                "${peer.ipAddress}:${peer.port}"
                            } else {
                                peer.ipAddress
                            }
                            if (peerKey in found || found.size < MAX_DISCOVERED_PEERS) {
                                found[peerKey] = peer
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue until overall scan deadline expires
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort discovery; manual IP:port remains available
        } finally {
            runCatching {
                if (multicastLock?.isHeld == true) multicastLock?.release()
            }
        }
        found.values.sortedWith(compareByDescending<HouseholdSyncPeer> { it.isTv }.thenBy { it.deviceName })
    }

    /**
     * Completes a 2-step SPAKE2 (`v = 2`) key exchange (`/wemi-sync/v1/hello` -> `/wemi-sync/v1/push`)
     * bound to the receiver's one-time 6-digit [pairingCode] and sends [bundle] over the local LAN.
     */
    suspend fun pushHouseholdBundle(
        targetIp: String,
        targetPort: Int,
        pairingCode: String,
        bundle: HouseholdSyncBundle,
        senderLabel: String = bundle.sourceDeviceLabel
    ): HouseholdSyncPushResult = withContext(Dispatchers.IO) {
        val cleanIp = targetIp.trim()
        if (!LanEndpointPolicy.isAllowedLanHost(cleanIp, allowLoopback = allowLoopbackForTests)) {
            return@withContext HouseholdSyncPushResult(
                success = false,
                statusMessage = "Enter a valid local Wi-Fi IPv4 address (for example 192.168.1.42)"
            )
        }
        if (targetPort !in 1..65535) {
            return@withContext HouseholdSyncPushResult(
                success = false,
                statusMessage = "Enter a valid TCP port between 1 and 65535"
            )
        }
        val normalizedPin = HouseholdSyncCrypto.normalizePairingCode(pairingCode)
        if (normalizedPin.length != 6) {
            return@withContext HouseholdSyncPushResult(
                success = false,
                statusMessage = "Enter the 6-digit pairing code shown on the receiving device"
            )
        }

        val plaintextJson = try {
            HouseholdSyncCodec.encodeToJson(bundle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext HouseholdSyncPushResult(
                success = false,
                statusMessage = e.message ?: "Failed to prepare household configuration"
            )
        }

        val helloUrl = "http://$cleanIp:$targetPort${HouseholdSyncLanServer.HELLO_PATH}"
        val pushUrl = "http://$cleanIp:$targetPort${HouseholdSyncLanServer.PUSH_PATH}"
        try {
            val helloResponse = httpClient.execute(
                method = "POST",
                url = helloUrl,
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                body = ""
            )
            if (helloResponse.code != 200) {
                return@withContext mapHttpErrorResult(helloResponse.code)
            }

            val encryptedEnvelope = try {
                HouseholdSyncPake.encryptPushV2(
                    plaintextJson = plaintextJson,
                    pairingCode = normalizedPin,
                    senderLabel = senderLabel,
                    helloResponseJson = helloResponse.body
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext HouseholdSyncPushResult(
                    success = false,
                    statusMessage = e.message ?: "Failed to establish encrypted pairing session"
                )
            }

            val response = httpClient.execute(
                method = "POST",
                url = pushUrl,
                headers = mapOf("Content-Type" to "application/json; charset=utf-8"),
                body = encryptedEnvelope
            )
            if (response.code == 200) {
                HouseholdSyncPushResult(
                    success = true,
                    httpCode = 200,
                    statusMessage = "Sent ${bundle.homes.size} home(s) & ${bundle.devices.size} switch(es) — confirm on the receiving device"
                )
            } else {
                mapHttpErrorResult(response.code)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HouseholdSyncPushResult(
                success = false,
                statusMessage = "Could not reach $cleanIp:$targetPort (${e.message ?: "connection failed"}) — check both devices are on the same Wi-Fi"
            )
        }
    }

    private fun mapHttpErrorResult(code: Int): HouseholdSyncPushResult = when (code) {
        401 -> HouseholdSyncPushResult(
            success = false,
            httpCode = 401,
            statusMessage = "Wrong pairing code — check the 6-digit code shown on the receiver"
        )
        409 -> HouseholdSyncPushResult(
            success = false,
            httpCode = 409,
            statusMessage = "Receiver already has a pending transfer waiting for confirmation"
        )
        410 -> HouseholdSyncPushResult(
            success = false,
            httpCode = 410,
            statusMessage = "Pairing code expired — reopen Sync on the receiver for a new code"
        )
        429 -> HouseholdSyncPushResult(
            success = false,
            httpCode = 429,
            statusMessage = "Receiver closed pairing after too many wrong codes — reopen Sync for a new code"
        )
        else -> HouseholdSyncPushResult(
            success = false,
            httpCode = code,
            statusMessage = "Receiver rejected transfer (HTTP $code)"
        )
    }

    private fun buildProbeTargets(): List<String> = buildList {
        if (allowLoopbackForTests) {
            add("127.0.0.1")
        }
        add(HouseholdSyncLanServer.DISCOVERY_MULTICAST_GROUP)
        add("255.255.255.255")
        val localIp = localIpProvider().trim()
        if (LanRuntimeUtils.isPrivateRfc1918Ipv4(localIp)) {
            val prefix = localIp.substringBeforeLast('.', "")
            if (prefix.isNotEmpty()) add("$prefix.255")
        }
    }.distinct()

    internal fun parseDiscoveryReply(senderIp: String, rawReply: String): HouseholdSyncPeer? {
        if (!rawReply.startsWith(HouseholdSyncLanServer.DISCOVERY_REPLY_PREFIX)) return null
        val rest = rawReply.removePrefix(HouseholdSyncLanServer.DISCOVERY_REPLY_PREFIX).trim()
        val portMatch = PORT_REGEX.find(rest) ?: return null
        val port = portMatch.groupValues[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val role = ROLE_REGEX.find(rest)?.groupValues?.get(1) ?: "DEVICE"
        val namePart = rest.substringAfter("name=", "").trim()
        val cleanName = HouseholdSyncCodec.sanitizeLabel(namePart, maxLen = 48)
            .ifEmpty { if (role == "TV") "WeMi TV" else "WeMi Device" }
        return HouseholdSyncPeer(
            ipAddress = senderIp,
            port = port,
            deviceName = cleanName,
            isTv = role == "TV"
        )
    }
}
