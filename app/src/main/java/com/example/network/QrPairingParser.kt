package com.example.network

import java.net.URI
import java.net.URLDecoder

object QrPairingParser {

    fun parse(rawContent: String): PcHost? {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return null

        // 1. JSON format: {"ip":"192.168.1.42","tcp":54546,"udp":54545,"name":"MY-PC"}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val ip = extractJsonString(trimmed, "ip")?.trim().orEmpty()
            if (!isValidHost(ip)) return null
            val tcp = extractJsonInt(trimmed, "tcp")?.takeIf { it in 1..65535 }
                ?: PcHost.DEFAULT_TCP_PORT
            val udp = extractJsonInt(trimmed, "udp")?.takeIf { it in 1..65535 }
                ?: PcHost.DEFAULT_UDP_PORT
            val name = extractJsonString(trimmed, "name")?.trim()?.ifBlank { ip } ?: ip
            return PcHost(name = name, ipAddress = ip, tcpPort = tcp, udpPort = udp)
        }

        // 2. URI format: airtouch://192.168.1.42:54546?u=54545&n=MY-PC&a=192.168.1.43
        if (trimmed.contains("://")) {
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase().orEmpty()
            if (scheme != "airtouch" && scheme != "atpc") return null
            val ip = uri.host?.trim().orEmpty()
            if (!isValidHost(ip)) return null
            val tcp = uri.port.takeIf { it in 1..65535 } ?: PcHost.DEFAULT_TCP_PORT

            var udp = PcHost.DEFAULT_UDP_PORT
            var name = ip
            var alternates = emptyList<String>()

            if (scheme == "airtouch") {
                val query = uri.rawQuery.orEmpty()
                if (query.isNotEmpty()) {
                    for (pair in query.split("&")) {
                        val idx = pair.indexOf('=')
                        if (idx <= 0) continue
                        val k = pair.substring(0, idx)
                        val v = runCatching { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }
                            .getOrDefault(pair.substring(idx + 1))
                        when (k) {
                            "u", "udp" -> v.toIntOrNull()?.takeIf { it in 1..65535 }?.let { udp = it }
                            "n", "name" -> if (v.isNotBlank()) name = v
                            "a", "alts" -> alternates = v.split(",").map { it.trim() }.filter { isValidHost(it) }
                        }
                    }
                }
            } else {
                // ATPC://ip:tcp/udp/name
                val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
                segments.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..65535 }?.let { udp = it }
                segments.getOrNull(1)?.let { decoded ->
                    val cleanName = runCatching { URLDecoder.decode(decoded, "UTF-8") }.getOrDefault(decoded)
                    if (cleanName.isNotBlank()) name = cleanName
                }
            }

            return PcHost(
                name = name,
                ipAddress = ip,
                tcpPort = tcp,
                udpPort = udp,
                alternateIps = alternates,
            )
        }

        // 3. Plain IPv4 or IPv4:port fallback
        val parts = trimmed.split(":")
        if (parts.size == 1 && isValidIpv4(parts[0])) {
            return PcHost(name = parts[0], ipAddress = parts[0])
        }
        if (parts.size == 2 && isValidIpv4(parts[0])) {
            val port = parts[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return PcHost(name = parts[0], ipAddress = parts[0], tcpPort = port)
        }

        return null
    }

    private fun extractJsonString(json: String, key: String): String? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"").find(json)
        return match?.groupValues?.getOrNull(1)
    }

    private fun extractJsonInt(json: String, key: String): Int? {
        val match = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(\\d+)").find(json)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isBlank() || host.contains(" ")) return false
        return isValidIpv4(host) || host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
    }

    private fun isValidIpv4(ip: String): Boolean {
        val octets = ip.split(".")
        if (octets.size != 4) return false
        return octets.all { part ->
            val num = part.toIntOrNull() ?: return@all false
            num in 0..255
        }
    }
}
