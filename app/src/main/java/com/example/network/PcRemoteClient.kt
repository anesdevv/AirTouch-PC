package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

class PcRemoteClient(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<PcHost>>(emptyList())
    val discoveredHosts: StateFlow<List<PcHost>> = _discoveredHosts.asStateFlow()

    private val _phoneWifiIp = MutableStateFlow<String?>(null)
    val phoneWifiIp: StateFlow<String?> = _phoneWifiIp.asStateFlow()

    @Volatile
    private var wifiNetwork: Network? = null

    @Volatile
    private var activeHost: PcHost? = null

    private var udpSocket: DatagramSocket? = null
    private var tcpSocket: Socket? = null
    private var tcpWriter: BufferedWriter? = null

    private var udpOutgoingChannel: Channel<String>? = null
    private var tcpOutgoingChannel: Channel<String>? = null

    private var sessionJob: Job? = null
    private var discoveryJob: Job? = null

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetwork = network
            updatePhoneWifiIp(network)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
            if (wifiNetwork == network || wifiNetwork == null) {
                wifiNetwork = network
                val ipv4 = linkProperties.linkAddresses
                    .mapNotNull { it.address as? Inet4Address }
                    .firstOrNull { !it.isLoopbackAddress }
                    ?.hostAddress
                _phoneWifiIp.value = ipv4
            }
        }

        override fun onLost(network: Network) {
            if (wifiNetwork == network) {
                wifiNetwork = null
                _phoneWifiIp.value = null
            }
        }
    }

    private fun updatePhoneWifiIp(network: Network) {
        val props = connectivityManager?.getLinkProperties(network) ?: return
        val ipv4 = props.linkAddresses
            .mapNotNull { it.address as? Inet4Address }
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
        _phoneWifiIp.value = ipv4
    }

    init {
        runCatching {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(req, wifiCallback)
        }
    }

    fun discoverHosts(udpPort: Int = PcHost.DEFAULT_UDP_PORT) {
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            val previousState = _connectionState.value
            if (previousState is ConnectionState.Disconnected || previousState is ConnectionState.Error) {
                _connectionState.value = ConnectionState.Discovering
            }
            val found = linkedMapOf<String, PcHost>()
            var probeSocket: DatagramSocket? = null
            try {
                probeSocket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 350
                }
                wifiNetwork?.let { net -> runCatching { net.bindSocket(probeSocket) } }

                val payload = DISCOVER_PROBE.toByteArray(Charsets.UTF_8)
                val targets = resolveBroadcastTargets()
                for (target in targets) {
                    runCatching {
                        probeSocket.send(DatagramPacket(payload, payload.size, target, udpPort))
                    }
                }

                val deadline = System.currentTimeMillis() + DISCOVERY_WINDOW_MS
                val buffer = ByteArray(1024)
                while (isActive && System.currentTimeMillis() < deadline) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        probeSocket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        val json = runCatching { JSONObject(text) }.getOrNull() ?: continue
                        if (json.optString("service") != "airtouch-pc") continue
                        val ip = packet.address.hostAddress ?: continue
                        val host = PcHost(
                            name = json.optString("name", ip),
                            ipAddress = ip,
                            tcpPort = json.optInt("tcp", PcHost.DEFAULT_TCP_PORT),
                            udpPort = json.optInt("udp", udpPort),
                        )
                        found[ip] = host
                        _discoveredHosts.value = found.values.toList()
                    } catch (_: SocketTimeoutException) {
                        // Continue listening within window
                    }
                }
            } catch (_: Exception) {
                // Ignore socket errors during scan
            } finally {
                runCatching { probeSocket?.close() }
                if (_connectionState.value is ConnectionState.Discovering) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    fun connect(
        host: PcHost,
        onConnected: (PcHost) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        sessionJob?.cancel()
        closeSockets()

        _connectionState.value = ConnectionState.Connecting(host)

        sessionJob = scope.launch {
            var connectedSocket: Socket? = null
            var connectedAddress: InetAddress? = null
            var connectedIp = host.ipAddress
            var lastException: Exception? = null

            try {
                val candidates = host.candidateIps
                val boundWifi = wifiNetwork

                for (candidateIp in candidates) {
                    if (!isActive) return@launch
                    val inetAddr = InetAddress.getByName(candidateIp)
                    val factories = if (boundWifi != null) {
                        listOf(
                            { boundWifi.socketFactory.createSocket() },
                            { Socket() },
                        )
                    } else {
                        listOf({ Socket() })
                    }
                    for (createSock in factories) {
                        try {
                            val attemptSocket = createSock()
                            attemptSocket.apply {
                                tcpNoDelay = true
                                soTimeout = 7000
                                connect(InetSocketAddress(inetAddr, host.tcpPort), CONNECT_TIMEOUT_MS)
                            }
                            connectedSocket = attemptSocket
                            connectedAddress = inetAddr
                            connectedIp = candidateIp
                            break
                        } catch (e: Exception) {
                            lastException = e
                        }
                    }
                    if (connectedSocket != null) break
                }

                val socket = connectedSocket ?: throw (lastException ?: SocketTimeoutException("Connection timed out"))
                val inetAddr = connectedAddress ?: InetAddress.getByName(connectedIp)
                val udp = DatagramSocket()
                boundWifi?.let { net -> runCatching { net.bindSocket(udp) } }

                tcpSocket = socket
                udpSocket = udp
                tcpWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

                val udpChan = Channel<String>(capacity = 96, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                val tcpChan = Channel<String>(capacity = 128)
                udpOutgoingChannel = udpChan
                tcpOutgoingChannel = tcpChan

                val helloJson = JSONObject().apply {
                    put("t", "hello")
                    put("client", "Android")
                }.toString()
                tcpWriter?.apply {
                    write(helloJson)
                    write("\n")
                    flush()
                }

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val welcomeLine = reader.readLine()
                val resolvedName = if (!welcomeLine.isNullOrBlank()) {
                    val welcome = runCatching { JSONObject(welcomeLine) }.getOrNull()
                    welcome?.optString("name").orEmpty().ifBlank { host.name }
                } else {
                    host.name
                }
                val resolvedHost = host.copy(
                    name = resolvedName,
                    ipAddress = connectedIp,
                    lastConnectedMs = System.currentTimeMillis(),
                )

                activeHost = resolvedHost
                _connectionState.value = ConnectionState.Connected(resolvedHost, latencyMs = null)
                withContext(Dispatchers.Main) {
                    onConnected(resolvedHost)
                }

                val udpJob = launch {
                    val reusablePacket = DatagramPacket(ByteArray(0), 0, inetAddr, resolvedHost.udpPort)
                    for (firstMsg in udpChan) {
                        var msgToSend = firstMsg
                        if (msgToSend.startsWith("M,")) {
                            var totalDx = 0
                            var totalDy = 0
                            var validMove = false
                            val p0 = msgToSend.split(',')
                            if (p0.size == 3) {
                                val x0 = p0[1].toIntOrNull()
                                val y0 = p0[2].toIntOrNull()
                                if (x0 != null && y0 != null) {
                                    totalDx = x0
                                    totalDy = y0
                                    validMove = true
                                }
                            }
                            if (validMove) {
                                while (true) {
                                    val next = udpChan.tryReceive().getOrNull() ?: break
                                    if (next.startsWith("M,")) {
                                        val pn = next.split(',')
                                        val nx = pn.getOrNull(1)?.toIntOrNull()
                                        val ny = pn.getOrNull(2)?.toIntOrNull()
                                        if (nx != null && ny != null) {
                                            totalDx += nx
                                            totalDy += ny
                                        }
                                    } else {
                                        // Flush accumulated move first, then send non-move packet
                                        if (totalDx != 0 || totalDy != 0) {
                                            val mb = "M,$totalDx,$totalDy".toByteArray(Charsets.UTF_8)
                                            reusablePacket.setData(mb, 0, mb.size)
                                            runCatching { udp.send(reusablePacket) }
                                            totalDx = 0
                                            totalDy = 0
                                        }
                                        val nb = next.toByteArray(Charsets.UTF_8)
                                        reusablePacket.setData(nb, 0, nb.size)
                                        runCatching { udp.send(reusablePacket) }
                                    }
                                }
                                if (totalDx == 0 && totalDy == 0) continue
                                msgToSend = "M,$totalDx,$totalDy"
                            }
                        }
                        val bytes = msgToSend.toByteArray(Charsets.UTF_8)
                        reusablePacket.setData(bytes, 0, bytes.size)
                        runCatching { udp.send(reusablePacket) }
                    }
                }

                val tcpWriteJob = launch {
                    val writer = tcpWriter ?: return@launch
                    for (line in tcpChan) {
                        writer.write(line)
                        writer.write("\n")
                        writer.flush()
                    }
                }

                val pingJob = launch {
                    while (isActive) {
                        delay(2000L)
                        val pingPayload = JSONObject().apply {
                            put("t", "ping")
                            put("ts", System.currentTimeMillis())
                        }.toString()
                        tcpChan.trySend(pingPayload)
                    }
                }

                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val msg = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        if (msg.optString("t") == "pong") {
                            val sentTs = msg.optLong("ts", 0L)
                            if (sentTs > 0L) {
                                val rtt = (System.currentTimeMillis() - sentTs).coerceAtLeast(1L)
                                val current = activeHost ?: resolvedHost
                                _connectionState.value = ConnectionState.Connected(current, latencyMs = rtt)
                            }
                        }
                    }
                } finally {
                    udpJob.cancel()
                    tcpWriteJob.cancel()
                    pingJob.cancel()
                }

                if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            } catch (e: Exception) {
                if (isActive) {
                    val reason = buildDiagnosticMessage(e, host)
                    _connectionState.value = ConnectionState.Error(reason, host)
                    withContext(Dispatchers.Main) {
                        onError(reason)
                    }
                }
            } finally {
                closeSockets()
            }
        }
    }

    private fun buildDiagnosticMessage(e: Exception, host: PcHost): String {
        val vpnActive = isVpnActive()
        val wifiAvailable = wifiNetwork != null
        val target = "${host.ipAddress}:${host.tcpPort}"
        return when {
            !wifiAvailable ->
                "Phone Wi-Fi is disconnected. Connect both devices to the same Wi-Fi network."
            vpnActive ->
                "Timed out reaching $target. A VPN is active on your phone and may block local LAN traffic."
            e is SocketTimeoutException ->
                "Timed out reaching $target. Allow TCP ${host.tcpPort} & UDP ${host.udpPort} in Windows Firewall and verify both devices are on the same Wi-Fi."
            else ->
                "Could not reach $target (${e.localizedMessage ?: "network error"}). Check Windows Firewall & PC script."
        }
    }

    private fun isVpnActive(): Boolean {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        closeSockets()
        _connectionState.value = ConnectionState.Disconnected
    }

    fun sendMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        udpOutgoingChannel?.trySend("M,$dx,$dy")
    }

    fun sendScroll(vx: Int, vy: Int) {
        if (vx == 0 && vy == 0) return
        udpOutgoingChannel?.trySend("W,$vx,$vy")
    }

    fun sendClick(button: MouseButton, action: ClickAction = ClickAction.TAP) {
        val payload = JSONObject().apply {
            put("t", "click")
            put("btn", button.wireValue)
            put("act", action.wireValue)
        }.toString()
        tcpOutgoingChannel?.trySend(payload)
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val payload = JSONObject().apply {
            put("t", "text")
            put("s", text)
        }.toString()
        tcpOutgoingChannel?.trySend(payload)
    }

    fun sendKey(
        keyName: String,
        modifiers: List<String> = emptyList(),
        action: ClickAction = ClickAction.TAP,
    ) {
        val payload = JSONObject().apply {
            put("t", "key")
            put("k", keyName)
            if (modifiers.isNotEmpty()) {
                put("mods", JSONArray(modifiers))
            }
            if (action != ClickAction.TAP) {
                put("act", action.wireValue)
            }
        }.toString()
        tcpOutgoingChannel?.trySend(payload)
    }

    fun sendLaser(active: Boolean) {
        val payload = JSONObject().apply {
            put("t", "laser")
            put("active", active)
        }.toString()
        tcpOutgoingChannel?.trySend(payload)
    }

    fun clearError() {
        if (_connectionState.value is ConnectionState.Error) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    fun shutdown() {
        runCatching { connectivityManager?.unregisterNetworkCallback(wifiCallback) }
        discoveryJob?.cancel()
        disconnect()
    }

    private fun closeSockets() {
        activeHost = null
        udpOutgoingChannel?.close()
        tcpOutgoingChannel?.close()
        udpOutgoingChannel = null
        tcpOutgoingChannel = null
        runCatching { tcpWriter?.close() }
        runCatching { tcpSocket?.close() }
        runCatching { udpSocket?.close() }
        tcpWriter = null
        tcpSocket = null
        udpSocket = null
    }

    private fun resolveBroadcastTargets(): List<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        runCatching { targets.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ifaceAddr in ni.interfaceAddresses) {
                    ifaceAddr.broadcast?.let { targets.add(it) }
                }
            }
        }
        return targets.toList()
    }

    companion object {
        private const val DISCOVER_PROBE = "AIRTOUCH_DISCOVER"
        private const val DISCOVERY_WINDOW_MS = 1800L
        private const val CONNECT_TIMEOUT_MS = 2800
    }
}
