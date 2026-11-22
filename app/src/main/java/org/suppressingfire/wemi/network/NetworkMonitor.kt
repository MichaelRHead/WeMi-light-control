package org.suppressingfire.wemi.network

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
import java.net.Socket
import javax.net.SocketFactory

data class ActiveLanBinding(
    val network: Network?,
    val interfaceName: String,
    val localIpv4: String,
    val dnsServersIpv4: List<String> = emptyList()
)

data class LanNetworkSnapshot(
    val isConnected: Boolean = true,
    val localIpv4: String = "192.168.1.50",
    val subnetPrefix: String = "192.168.1",
    val gatewayIp: String = "192.168.1.1",
    val isWemoSetupApSubnet: Boolean = false,
    val interfaceName: String = "wlan0",
    val isWifiSocketBound: Boolean = false,
    val dnsServersIpv4: List<String> = emptyList(),
    val isArcVmNatSubnet: Boolean = false
)

/**
 * Zero-Location-permission Wi-Fi / LAN subnet monitor using ConnectivityManager.NetworkCallback
 * and LinkProperties.
 * - Detects live IPv4 address, /24 subnetPrefix, default gateway IP, and IPv4 DNS hints.
 * - Prioritizes TRANSPORT_WIFI networks over Cellular (cm.activeNetwork) so that connecting to an
 *   unconfigured Belkin WeMo Setup AP (WeMo.Light.xxx / 10.22.22.x) with no Internet access is
 *   reliably detected and routed over Wi-Fi even when mobile data is active.
 * - Excludes VPN transports and ChromeOS ARCVM's `arc0` management bridge (`100.115.92.2`) when
 *   a shill-backed physical Wi-Fi or Ethernet network is available.
 */
class NetworkMonitor(
    private val context: Context?,
    private val fallbackIpResolver: () -> String = { "192.168.1.50" },
    private val interfaceIpResolver: () -> Pair<String, String> = {
        LanRuntimeUtils.resolvePreferredLanInterfaceIpv4(fallbackIp = fallbackIpResolver())
    }
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
                if (isEligibleLanNetwork(cm, network)) {
                    lastCallbackWifiNetwork = network
                }
                val prevSubnet = _networkState.value.subnetPrefix
                val next = computeSnapshot(if (isEligibleLanNetwork(cm, network)) network else null)
                _networkState.value = next
                if (next.subnetPrefix != prevSubnet) {
                    onSubnetChanged?.invoke(next)
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (isEligibleLanNetwork(cm, network, linkProperties)) {
                    lastCallbackWifiNetwork = network
                } else {
                    return
                }
                val prevSubnet = _networkState.value.subnetPrefix
                val next = computeSnapshotFromLinkProperties(linkProperties, isBound = true) ?: computeSnapshot(network)
                _networkState.value = next
                if (next.subnetPrefix != prevSubnet) {
                    onSubnetChanged?.invoke(next)
                }
            }

            override fun onLost(network: Network) {
                if (lastCallbackWifiNetwork == network) {
                    lastCallbackWifiNetwork = null
                }
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
        activeLanBindingOverrideForTests?.invoke()?.let { binding ->
            return snapshotFromBinding(binding)
        }
        val cm = connectivityManager
        if (cm != null) {
            val targetNet = network ?: findPreferredWifiOrLanNetwork(cm) ?: runCatching { cm.activeNetwork }.getOrNull()
            if (targetNet != null) {
                val lp = runCatching { cm.getLinkProperties(targetNet) }.getOrNull()
                if (lp != null) {
                    computeSnapshotFromLinkProperties(lp, isBound = true)?.let { return it }
                }
            }
        }
        return computeSnapshotFromInterfaces()
    }

    private fun snapshotFromBinding(binding: ActiveLanBinding): LanNetworkSnapshot {
        val prefix = binding.localIpv4.substringBeforeLast('.', "192.168.1")
        return LanNetworkSnapshot(
            isConnected = true,
            localIpv4 = binding.localIpv4,
            subnetPrefix = prefix,
            gatewayIp = "$prefix.1",
            isWemoSetupApSubnet = prefix == "10.22.22",
            interfaceName = binding.interfaceName,
            isWifiSocketBound = true,
            dnsServersIpv4 = binding.dnsServersIpv4,
            isArcVmNatSubnet = LanRuntimeUtils.isCarrierGradeNatIpv4(binding.localIpv4)
        )
    }

    private fun computeSnapshotFromLinkProperties(lp: LinkProperties, isBound: Boolean = true): LanNetworkSnapshot? {
        val ipv4 = extractUsableIpv4(lp) ?: return null

        val prefix = ipv4.substringBeforeLast('.', "192.168.1")
        val gateway = lp.routes
            .mapNotNull { it.gateway as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress && (it.hostAddress ?: "0.0.0.0") != "0.0.0.0" }
            ?.hostAddress ?: "$prefix.1"
        val ifName = lp.interfaceName?.takeIf { it.isNotBlank() } ?: "wlan0"
        val dnsServers = extractIpv4DnsServers(lp)

        return LanNetworkSnapshot(
            isConnected = true,
            localIpv4 = ipv4,
            subnetPrefix = prefix,
            gatewayIp = gateway,
            isWemoSetupApSubnet = prefix == "10.22.22",
            interfaceName = ifName,
            isWifiSocketBound = isBound,
            dnsServersIpv4 = dnsServers,
            isArcVmNatSubnet = LanRuntimeUtils.isCarrierGradeNatIpv4(ipv4)
        )
    }

    private fun computeSnapshotFromInterfaces(): LanNetworkSnapshot {
        activeLanBindingOverrideForTests?.invoke()?.let { binding ->
            return snapshotFromBinding(binding)
        }
        val (ip, resolvedIfName) = interfaceIpResolver()
        val prefix = ip.substringBeforeLast('.', "192.168.1")
        val cm = connectivityManager
        val boundNet = findPreferredWifiOrLanNetwork(cm)
        return LanNetworkSnapshot(
            isConnected = true,
            localIpv4 = ip,
            subnetPrefix = prefix,
            gatewayIp = "$prefix.1",
            isWemoSetupApSubnet = prefix == "10.22.22",
            interfaceName = resolvedIfName,
            isWifiSocketBound = boundNet != null,
            isArcVmNatSubnet = LanRuntimeUtils.isCarrierGradeNatIpv4(ip)
        )
    }

    companion object {
        @Volatile
        private var lastCallbackWifiNetwork: Network? = null

        @Volatile
        internal var activeLanBindingOverrideForTests: (() -> ActiveLanBinding?)? = null

        @Volatile
        internal var socketFactoryOverrideForTests: SocketFactory? = null

        internal fun resetTestOverrides() {
            activeLanBindingOverrideForTests = null
            socketFactoryOverrideForTests = null
            lastCallbackWifiNetwork = null
        }

        private fun extractUsableIpv4(lp: LinkProperties?): String? =
            lp?.linkAddresses
                ?.mapNotNull { it.address as? Inet4Address }
                ?.firstOrNull { !it.isLoopbackAddress && !(it.hostAddress ?: "").startsWith("169.254.") }
                ?.hostAddress

        private fun extractIpv4DnsServers(lp: LinkProperties?): List<String> =
            lp?.dnsServers
                ?.mapNotNull { (it as? Inet4Address)?.takeIf { addr -> !addr.isLoopbackAddress }?.hostAddress }
                .orEmpty()

        private fun isEligibleLanNetwork(
            cm: ConnectivityManager,
            network: Network,
            knownLinkProps: LinkProperties? = null,
            allowArcManagement: Boolean = false
        ): Boolean {
            val caps = runCatching { cm.getNetworkCapabilities(network) }.getOrNull() ?: return false
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
            val isWifiOrEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!isWifiOrEthernet) return false
            if (!allowArcManagement) {
                val lp = knownLinkProps ?: runCatching { cm.getLinkProperties(network) }.getOrNull()
                if (lp?.interfaceName.equals("arc0", ignoreCase = true) ||
                    LanRuntimeUtils.isArcVmManagementIpv4(extractUsableIpv4(lp))
                ) {
                    return false
                }
            }
            return true
        }

        /**
         * Finds the active Wi-Fi or Ethernet [Network] even when Android has not marked it as the
         * default Internet network (e.g. when connected to a Belkin WeMo Setup AP at 10.22.22.1
         * while mobile data is active), while skipping VPNs and ChromeOS ARCVM's `arc0`
         * management bridge (`100.115.92.2`).
         */
        @Suppress("DEPRECATION")
        fun findPreferredWifiOrLanNetwork(cm: ConnectivityManager?): Network? {
            val cached = lastCallbackWifiNetwork
            if (cm == null) return cached
            return runCatching {
                val networks = cm.allNetworks.orEmpty()
                // 1. Highest priority for Phones: TRANSPORT_WIFI on 10.22.22.x Setup AP
                networks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    val lp = cm.getLinkProperties(net)
                    val hasSetupIp = lp?.linkAddresses?.any {
                        (it.address as? Inet4Address)?.hostAddress?.startsWith("10.22.22.") == true
                    } == true
                    caps != null && !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && hasSetupIp
                }?.let { return@runCatching it }

                // 2. System activeNetwork when it is an eligible physical Wi-Fi or Ethernet uplink
                val active = runCatching { cm.activeNetwork }.getOrNull()
                if (active != null && isEligibleLanNetwork(cm, active)) {
                    return@runCatching active
                }

                // 3. Live callback-tracked Wi-Fi/Ethernet network (excluding VPN and arc0)
                if (cached != null && isEligibleLanNetwork(cm, cached)) {
                    return@runCatching cached
                }

                // 4. Any eligible TRANSPORT_WIFI, then any eligible TRANSPORT_ETHERNET
                networks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        isEligibleLanNetwork(cm, net)
                } ?: networks.firstOrNull { net ->
                    val caps = cm.getNetworkCapabilities(net)
                    caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) &&
                        isEligibleLanNetwork(cm, net)
                }
            }.getOrElse { cached }
        }

        /**
         * Resolves the active physical Wi-Fi/Ethernet [Network], interface name, IPv4 address, and
         * IPv4 DNS servers from [ConnectivityManager] directly.
         */
        internal fun resolveActiveLanBindingFromManager(cm: ConnectivityManager?): ActiveLanBinding? {
            if (cm == null) return null
            val targetNet = findPreferredWifiOrLanNetwork(cm) ?: return null
            val lp = runCatching { cm.getLinkProperties(targetNet) }.getOrNull() ?: return null
            val ipv4 = extractUsableIpv4(lp) ?: return null
            val ifName = lp.interfaceName?.takeIf { it.isNotBlank() } ?: "wlan0"
            return ActiveLanBinding(
                network = targetNet,
                interfaceName = ifName,
                localIpv4 = ipv4,
                dnsServersIpv4 = extractIpv4DnsServers(lp)
            )
        }

        /**
         * Resolves the active physical Wi-Fi/Ethernet [Network], interface name, IPv4 address, and
         * IPv4 DNS servers directly from [ConnectivityManager] (skipping Robolectric tests so
         * hermetic JVM suites never depend on host NIC state).
         */
        fun resolveActiveLanBinding(context: Context?): ActiveLanBinding? {
            activeLanBindingOverrideForTests?.invoke()?.let { return it }
            if (context == null || LanRuntimeUtils.isRunningInRobolectric()) return null
            val cm = context.applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            return resolveActiveLanBindingFromManager(cm)
        }

        /**
         * Creates a [SocketFactory] that binds outbound connections to the active Wi-Fi/LAN [Network]
         * when available (bypassing Android's cellular fallback on unvalidated 10.22.22.1 WeMo Setup APs),
         * while always using the standard loopback socket factory for localhost/127.x.x.x.
         */
        fun createWifiBoundSocketFactory(context: Context?): SocketFactory {
            val appCtx = context?.applicationContext
            val isRobolectric = LanRuntimeUtils.isRunningInRobolectric()

            return object : SocketFactory() {
                private fun resolveWifiNetwork(): Network? {
                    if (isRobolectric) return null
                    val cm = appCtx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    return findPreferredWifiOrLanNetwork(cm)
                }

                private fun delegateForHost(host: String?): SocketFactory {
                    socketFactoryOverrideForTests?.let { return it }
                    if (host == null || host == "localhost" || host.startsWith("127.")) {
                        return getDefault()
                    }
                    return resolveWifiNetwork()?.socketFactory ?: getDefault()
                }

                override fun createSocket(): Socket {
                    socketFactoryOverrideForTests?.let { return it.createSocket() }
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
