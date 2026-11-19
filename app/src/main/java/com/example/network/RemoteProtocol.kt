package com.example.network

enum class MouseButton(val wireValue: String) {
    LEFT("left"),
    RIGHT("right"),
    MIDDLE("middle"),
}

enum class ClickAction(val wireValue: String) {
    TAP("tap"),
    DOUBLE("double"),
    DOWN("down"),
    UP("up"),
}

data class PcHost(
    val name: String,
    val ipAddress: String,
    val tcpPort: Int = DEFAULT_TCP_PORT,
    val udpPort: Int = DEFAULT_UDP_PORT,
    val lastConnectedMs: Long = 0L,
    val alternateIps: List<String> = emptyList(),
) {
    val displayLabel: String
        get() = if (name.isNotBlank() && name != ipAddress) name else ipAddress

    val candidateIps: List<String>
        get() = (listOf(ipAddress) + alternateIps)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    companion object {
        const val DEFAULT_UDP_PORT = 54545
        const val DEFAULT_TCP_PORT = 54546
    }
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val host: PcHost) : ConnectionState
    data class Connected(val host: PcHost, val latencyMs: Long? = null) : ConnectionState
    data class Error(val message: String, val host: PcHost? = null) : ConnectionState
}
