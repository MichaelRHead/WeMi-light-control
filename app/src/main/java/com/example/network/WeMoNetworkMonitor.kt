package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Locale
import javax.net.SocketFactory

data class LanNetworkSnapshot(
    val isConnected: Boolean = true,
    val localIpv4: String = "192.168.1.50",
    val subnetPrefix: String = "192.168.1",
    val gatewayIp: String = "192.168.1.1",
    val isWemoSetupApSubnet: Boolean = false
)

/**
 * Zero-Location-permission Wi-Fi / LAN subnet monitor using ConnectivityManager.NetworkCallback
 * and LinkProperties.
 * - Detects live IPv4 address, /24 subnetPrefix, and default gateway IP.
 * - Prioritizes TRANSPORT_WIFI networks over Cellular (cm.activeNetwork) so that connecting to an
 *   unconfigured Belkin WeMo Setup AP (WeMo.Light.xxx / 10.22.22.x) with no Internet access is
 *   reliably detected and routed over Wi-Fi even when mobile data is active.
 */
class WeMoNetworkMonitor(
    private val context: Context?,
    private val fallbackIpResolver: () -> String = { "192.168.1.50" }
) {
    private val connectivityManager =
        context?.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _networkState = MutableStateFlow(computeSnapshotFromInterfaces())
    val networkState: StateFlow<LanNetworkSnapshot> = _networkState.asStateFlow()

    private var registeredCallback: ConnectivityManager.NetworkCallback? = null

    fun refreshSnapshot(): LanNetworkSnapshot {
        val snap = computeSnapshot()
        _networkState.value = snap
        return snap
    }

    fun startMonitoring(onSubnetChanged: ((LanNetworkSnapshot) -> Unit)? = null) {
        val cm = connectivityManager ?: return
        if (registeredCallback != null) return

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val prevSubnet = _networkState.value.subnetPrefix
                val next = computeSnapshot(network)
                _networkState.value = next
                if (next.subnetPrefix != prevSubnet) {
                    onSubnetChanged?.invoke(next)
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                val prevSubnet = _networkState.value.subnetPrefix
                val next = computeSnapshotFromLinkProperties(linkProperties) ?: computeSnapshot(network)
                _networkState.value = next
                if (next.subnetPrefix != prevSubnet) {
                    onSubnetChanged?.invoke(next)
                }
            }

            override fun onLost(network: Network) {
                _networkState.value = computeSnapshot()
            }
        }

        runCatching {
            // Note: Do NOT require NET_CAPABILITY_INTERNET so factory-reset WeMo Setup APs (10.22.22.1)
            // without Internet access trigger callbacks immediately.
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, callback)
            registeredCallback = callback
        }
    }

    fun stopMonitoring() {
        val cm = connectivityManager ?: return
        val cb = registeredCallback ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
        registeredCallback = null
    }

    private fun computeSnapshot(network: Network? = null): LanNetworkSnapshot {
        val cm = connectivityManager
        if (cm != null) {
            val targetNet = network ?: findPreferredWifiOrLanNetwork(cm) ?: runCatching { cm.activeNetwork }.getOrNull()
            if (targetNet != null) {
                val lp = runCatching { cm.getLinkProperties(targetNet) }.getOrNull()
                if (lp != null) {
                    computeSnapshotFromLinkProperties(lp)?.let { return it }
                }
            }
        }
        return computeSnapshotFromInterfaces()
    }

    private fun computeSnapshotFromLinkProperties(lp: LinkProperties): LanNetworkSnapshot? {
        val ipv4 = lp.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress && !(it.hostAddress ?: "").startsWith("169.254.") }
            ?.hostAddress ?: return null

        val prefix = ipv4.substringBeforeLast('.', "192.168.1")
        val gateway = lp.routes
            .mapNotNull { it.gateway as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress && (it.hostAddress ?: "0.0.0.0") != "0.0.0.0" }
            ?.hostAddress ?: "$prefix.1"

        return LanNetworkSnapshot(
            isConnected = true,
            localIpv4 = ipv4,
            subnetPrefix = prefix,
            gatewayIp = gateway,
            isWemoSetupApSubnet = prefix == "10.22.22"
        )
    }

    private fun computeSnapshotFromInterfaces(): LanNetworkSnapshot {
        val ip = try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val sorted = interfaces.sortedBy { nif ->
                val name = nif.name.lowercase(Locale.US)
                when {
                    name.startsWith("wlan") -> 0
                    name.startsWith("eth") || name.startsWith("en") -> 1
                    else -> 2
                }
            }
            var found: String? = null
            for (nif in sorted) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val h = addr.hostAddress ?: continue
                        if (!h.startsWith("169.254.")) {
                            found = h
                            break
                        }
                    }
                }
                if (found != null) break
            }
            found ?: fallbackIpResolver()
        } catch (_: Exception) {
            fallbackIpResolver()
        }

        val prefix = ip.substringBeforeLast('.', "192.168.1")
        return LanNetworkSnapshot(
            isConnected = true,
            localIpv4 = ip,
            subnetPrefix = prefix,
            gatewayIp = "$prefix.1",
            isWemoSetupApSubnet = prefix == "10.22.22"
        )
    }

    companion object {
        /**
         * Finds the active Wi-Fi or Ethernet [Network] even when Android has not marked it as the
         * default Internet network (e.g. when connected to a Belkin WeMo Setup AP at 10.22.22.1
         * while mobile data is active).
         */
        @Suppress("DEPRECATION")
        fun findPreferredWifiOrLanNetwork(cm: ConnectivityManager?): Network? {
            if (cm == null) return null
            return runCatching {
                val networks = cm.allNetworks.orEmpty()
                // Prefer a TRANSPORT_WIFI network that has a valid IPv4 address (especially 10.22.22.x or LAN)
                networks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                } ?: networks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                }
            }.getOrNull()
        }

        /**
         * Creates a [SocketFactory] that binds outbound connections to the active Wi-Fi/LAN [Network]
         * when available (bypassing Android's cellular fallback on unvalidated 10.22.22.1 WeMo Setup APs),
         * while always using the standard loopback socket factory for localhost/127.x.x.x.
         */
        fun createWifiBoundSocketFactory(context: Context?): SocketFactory {
            val appCtx = context?.applicationContext
            val isRobolectric = android.os.Build.FINGERPRINT.contains("robolectric", ignoreCase = true) ||
                runCatching { Class.forName("org.robolectric.Robolectric"); true }.getOrDefault(false)

            return object : SocketFactory() {
                private fun resolveWifiNetwork(): Network? {
                    if (isRobolectric) return null
                    val cm = appCtx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    return findPreferredWifiOrLanNetwork(cm)
                }

                private fun delegateForHost(host: String?): SocketFactory {
                    if (host == null || host == "localhost" || host.startsWith("127.")) {
                        return getDefault()
                    }
                    return resolveWifiNetwork()?.socketFactory ?: getDefault()
                }

                override fun createSocket(): Socket {
                    val wifiNet = resolveWifiNetwork() ?: return Socket()
                    return object : Socket() {
                        override fun connect(endpoint: java.net.SocketAddress?, timeout: Int) {
                            val inetAddr = (endpoint as? java.net.InetSocketAddress)?.address
                            if (inetAddr != null && !inetAddr.isLoopbackAddress) {
                                runCatching { wifiNet.bindSocket(this) }
                            }
                            super.connect(endpoint, timeout)
                        }
                    }
                }

                override fun createSocket(host: String?, port: Int): Socket =
                    delegateForHost(host).createSocket(host, port)

                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                    delegateForHost(host).createSocket(host, port, localHost, localPort)

                override fun createSocket(host: InetAddress?, port: Int): Socket =
                    delegateForHost(host?.hostAddress).createSocket(host, port)

                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                    delegateForHost(address?.hostAddress).createSocket(address, port, localAddress, localPort)
            }
        }
    }
}
