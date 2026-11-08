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
import java.net.NetworkInterface
import java.util.Locale

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
 * - Flags when the Android device is connected to a factory-reset Belkin WeMo Setup AP (10.22.22.x).
 * - Pairs with WeMo switches' metainfo1#GetMetaInfo (AccessPointSSID) to populate real Wi-Fi SSID
 *   labels without requiring ACCESS_FINE_LOCATION on Android 10+.
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
                _networkState.value = computeSnapshotFromInterfaces()
            }
        }

        runCatching {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
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
            val targetNet = network ?: runCatching { cm.activeNetwork }.getOrNull()
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
}
