package org.suppressingfire.wemi

import org.suppressingfire.wemi.network.ActiveLanBinding
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.NetworkClient
import org.suppressingfire.wemi.network.NetworkMonitor
import org.suppressingfire.wemi.network.SsdpDiscoveryEngine
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import kotlin.concurrent.thread

/**
 * Hermetic ChromiumOS `patchpanel` + ARCVM virtual-network bridge for full-stack Chromebook E2E
 * tests.
 *
 * Unlike Phone and Android TV tests (which connect directly to `127.0.0.1:<ephemeralPort>` on the
 * same flat subnet), Chromebooks run Android inside a guest VM (`eth1 = 100.115.92.6`, CGNAT
 * `100.64.0.0/10`) separated from physical home switches (`192.168.86.0/24`) by ChromiumOS's
 * `MASQUERADE` SNAT and `MulticastForwarder`:
 * - Exposes a simulated [ActiveLanBinding] (`eth1`, `100.115.92.6`, configurable DNS servers).
 * - Translates outbound TCP connections to mapped physical RFC 1918 endpoints (e.g.
 *   `192.168.86.42:49153`) onto loopback [FakeUpnpServer] sockets while immediately refusing
 *   unmapped RFC 1918 hosts (`ConnectException`) so real 254-host Stage C sweeps run in `< 200ms`.
 * - Records and immediately blocks ANY probe targeting `100.64.0.0/10` ([cgnatProbesAttempted])
 *   so tests can strictly assert WeMi never scans ChromeOS's virtual CGNAT subnet.
 * - Optionally simulates `patchpanel` SSDP multicast forwarding for Belkin switches and/or a
 *   non-Belkin UPnP InternetGatewayDevice router returning RFC 1918 `LOCATION` URLs.
 */
class FakeChromeOsPatchpanelBridge(
    val guestInterfaceName: String = "eth1",
    val guestIpv4: String = "100.115.92.6",
    @Volatile var dnsServersIpv4: List<String> = listOf("192.168.86.1")
) : AutoCloseable {

    private val running = AtomicBoolean(true)
    private val virtualLanRoutes = ConcurrentHashMap<Pair<String, Int>, Int>()
    private val udpSockets = CopyOnWriteArrayList<DatagramSocket>()
    private val registeredUdpPorts = CopyOnWriteArrayList<Int>()
    private val extraServerSockets = CopyOnWriteArrayList<ServerSocket>()

    val cgnatProbesAttempted = CopyOnWriteArrayList<String>()
    val snatRoutedConnections = CopyOnWriteArrayList<String>()
    val sweptVirtualHosts = CopyOnWriteArraySet<String>()
    val routerIgdXmlFetches = AtomicInteger(0)

    private val snatSocketFactory: SocketFactory = object : SocketFactory() {
        override fun createSocket(): Socket = SnatBridgedSocket()

        override fun createSocket(host: String?, port: Int): Socket =
            SnatBridgedSocket().apply {
                connect(InetSocketAddress(host ?: "127.0.0.1", port), 750)
            }

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket = createSocket(host, port)

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            SnatBridgedSocket().apply {
                connect(InetSocketAddress(host ?: InetAddress.getLoopbackAddress(), port), 750)
            }

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = createSocket(address, port)
    }

    private inner class SnatBridgedSocket : Socket() {
        override fun connect(endpoint: SocketAddress?, timeout: Int) {
            val inet = endpoint as? InetSocketAddress
            val host = inet?.address?.hostAddress ?: inet?.hostString.orEmpty()
            val port = inet?.port ?: -1

            if (LanRuntimeUtils.isCarrierGradeNatIpv4(host)) {
                cgnatProbesAttempted.add("$host:$port")
                throw ConnectException("ARCVM CGNAT probe rejected by patchpanel bridge: $host:$port")
            }

            val mappedLoopbackPort = virtualLanRoutes[host to port]
            if (mappedLoopbackPort != null) {
                snatRoutedConnections.add("$host:$port")
                super.connect(
                    InetSocketAddress(InetAddress.getByName("127.0.0.1"), mappedLoopbackPort),
                    timeout
                )
                return
            }

            if (host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)) {
                super.connect(endpoint, timeout)
                return
            }

            sweptVirtualHosts.add(host)
            throw ConnectException("Simulated physical LAN host/port closed: $host:$port")
        }
    }

    /** Installs this virtual `patchpanel` bridge into WeMi's network layer for the current test. */
    fun install(): FakeChromeOsPatchpanelBridge {
        NetworkMonitor.activeLanBindingOverrideForTests = {
            ActiveLanBinding(
                network = null,
                interfaceName = guestInterfaceName,
                localIpv4 = guestIpv4,
                dnsServersIpv4 = dnsServersIpv4
            )
        }
        NetworkMonitor.socketFactoryOverrideForTests = snatSocketFactory
        SsdpDiscoveryEngine.allowUnicastFallbackInRobolectricForTests = true
        return this
    }

    fun mapVirtualEndpoint(physicalIp: String, physicalPort: Int, loopbackPort: Int) {
        virtualLanRoutes[physicalIp to physicalPort] = loopbackPort
    }

    fun shiftVirtualEndpointPort(
        physicalIp: String,
        oldPort: Int,
        newPort: Int,
        loopbackPort: Int
    ) {
        virtualLanRoutes.remove(physicalIp to oldPort)
        virtualLanRoutes[physicalIp to newPort] = loopbackPort
    }

    /**
     * Simulates `patchpanel` `MulticastForwarder` + `apply_auto_dnat_to_arc` delivering a switch's
     * SSDP `200 OK` response carrying its physical RFC 1918 `LOCATION` URL into ARCVM.
     */
    fun registerVirtualSwitchSsdp(
        physicalIp: String,
        physicalPort: Int,
        udn: String
    ) {
        registerVirtualSsdpResponder(
            locationUrl = "http://$physicalIp:$physicalPort/setup.xml",
            usnPrefix = udn,
            fixedSearchTarget = null
        )
    }

    /**
     * Simulates a non-Belkin physical home router (e.g. Google Nest Wifi) answering `M-SEARCH`
     * with an `InternetGatewayDevice:1` descriptor at `http://<routerIp>:<routerPort>/rootDesc.xml`.
     * WeMi should harvest `<routerIp>` as a Stage C subnet hint while rejecting the router itself.
     */
    fun registerVirtualRouterIgd(
        routerIp: String = "192.168.86.1",
        routerPort: Int = 5000,
        manufacturer: String = "Google Nest Wifi"
    ) {
        val igdXml = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
                <friendlyName>$manufacturer Router</friendlyName>
                <manufacturer>$manufacturer</manufacturer>
                <modelName>NestWifiPro</modelName>
                <UDN>uuid:nest-router-igd-0001</UDN>
              </device>
            </root>
        """.trimIndent()
        val igdBytes = igdXml.toByteArray(Charsets.UTF_8)
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        extraServerSockets.add(server)
        mapVirtualEndpoint(routerIp, routerPort, server.localPort)

        thread(start = true, isDaemon = true, name = "FakeRouterIgd-${server.localPort}") {
            while (running.get() && !server.isClosed) {
                try {
                    val client = server.accept()
                    thread(start = true, isDaemon = true) {
                        client.use { sock ->
                            sock.soTimeout = 1000
                            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                            val reqLine = reader.readLine().orEmpty()
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                            }
                            if (reqLine.contains("/rootDesc.xml")) {
                                routerIgdXmlFetches.incrementAndGet()
                                val header = buildString {
                                    append("HTTP/1.1 200 OK\r\n")
                                    append("Content-Type: text/xml; charset=\"utf-8\"\r\n")
                                    append("Content-Length: ${igdBytes.size}\r\n")
                                    append("Connection: close\r\n\r\n")
                                }
                                sock.getOutputStream().write(header.toByteArray(Charsets.UTF_8))
                                sock.getOutputStream().write(igdBytes)
                                sock.getOutputStream().flush()
                            } else {
                                val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                sock.getOutputStream().write(notFound.toByteArray(Charsets.UTF_8))
                                sock.getOutputStream().flush()
                            }
                        }
                    }
                } catch (_: Exception) {
                    if (!running.get() || server.isClosed) break
                }
            }
        }

        registerVirtualSsdpResponder(
            locationUrl = "http://$routerIp:$routerPort/rootDesc.xml",
            usnPrefix = "uuid:nest-router-igd-0001",
            fixedSearchTarget = "urn:schemas-upnp-org:device:InternetGatewayDevice:1"
        )
    }

    private fun registerVirtualSsdpResponder(
        locationUrl: String,
        usnPrefix: String,
        fixedSearchTarget: String?
    ) {
        val udp = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val udpPort = udp.localPort
        udpSockets.add(udp)
        registeredUdpPorts.add(udpPort)
        NetworkClient.registerLoopbackSsdpPort(udpPort)

        thread(start = true, isDaemon = true, name = "PatchpanelSsdp-$udpPort") {
            val buf = ByteArray(2048)
            while (running.get() && !udp.isClosed) {
                try {
                    val packet = DatagramPacket(buf, buf.size)
                    udp.receive(packet)
                    val reqText = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (!reqText.contains("M-SEARCH", ignoreCase = true)) continue
                    val requestedSt = Regex("(?m)^ST:\\s*(.+)$", RegexOption.IGNORE_CASE)
                        .find(reqText)?.groupValues?.getOrNull(1)?.trim()
                        ?: "upnp:rootdevice"
                    if (fixedSearchTarget != null &&
                        !requestedSt.equals("upnp:rootdevice", ignoreCase = true) &&
                        !requestedSt.equals("ssdp:all", ignoreCase = true)
                    ) {
                        continue
                    }
                    val stHeader = fixedSearchTarget ?: requestedSt
                    val reply = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("CACHE-CONTROL: max-age=1800\r\n")
                        append("EXT:\r\n")
                        append("LOCATION: $locationUrl\r\n")
                        append("ST: $stHeader\r\n")
                        append("USN: $usnPrefix::$stHeader\r\n\r\n")
                    }.toByteArray(Charsets.UTF_8)
                    udp.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
                } catch (_: Exception) {
                    if (!running.get() || udp.isClosed) break
                }
            }
        }
    }

    override fun close() {
        running.set(false)
        registeredUdpPorts.forEach { NetworkClient.unregisterLoopbackSsdpPort(it) }
        registeredUdpPorts.clear()
        udpSockets.forEach { runCatching { it.close() } }
        udpSockets.clear()
        extraServerSockets.forEach { runCatching { it.close() } }
        extraServerSockets.clear()
        virtualLanRoutes.clear()
        NetworkMonitor.resetTestOverrides()
        SsdpDiscoveryEngine.allowUnicastFallbackInRobolectricForTests = false
    }
}
