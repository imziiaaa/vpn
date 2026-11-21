package com.veilgate.vpn.service

import android.util.Base64
import com.veilgate.vpn.data.model.AwgInterfaceConfig
import com.veilgate.vpn.data.model.AwgPeerConfig
import java.net.InetAddress

object AmneziaConfigBuilder {

    /**
     * Converts standard Base64 WireGuard / AmneziaWG keys into lowercase hex format expected by awg-go UAPI.
     */
    fun base64ToHex(base64: String?): String {
        if (base64.isNullOrBlank()) return ""
        return try {
            val bytes = Base64.decode(base64.trim(), Base64.DEFAULT)
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Bracket-aware parser for IPv4 ("1.2.3.4:51820", "host:51820")
     * and IPv6 ("[2001:db8::1]:51820", "2001:db8::1") endpoints.
     */
    fun parseHostAndPort(endpoint: String): Pair<String, Int> {
        val trimmed = endpoint.trim()
        if (trimmed.startsWith("[")) {
            val closingBracket = trimmed.indexOf(']')
            if (closingBracket != -1) {
                val host = trimmed.substring(1, closingBracket)
                val after = trimmed.substring(closingBracket + 1)
                val port = if (after.startsWith(":")) after.substring(1).toIntOrNull() ?: 51820 else 51820
                return Pair(host, port)
            }
        }
        val lastColon = trimmed.lastIndexOf(':')
        return if (lastColon != -1 && trimmed.indexOf(':') == lastColon) {
            val host = trimmed.substring(0, lastColon)
            val port = trimmed.substring(lastColon + 1).toIntOrNull() ?: 51820
            Pair(host, port)
        } else {
            Pair(trimmed, 51820)
        }
    }

    /**
     * Resolves an endpoint host to a numeric IP while on the physical network.
     * Emits standard bracketed format for IPv6: "[2001:db8::1]:port", or "1.2.3.4:port" for IPv4.
     */
    fun resolveEndpoint(endpoint: String): String {
        val (host, port) = parseHostAndPort(endpoint)
        val resolvedIp = try {
            InetAddress.getByName(host).hostAddress ?: host
        } catch (e: Exception) {
            host
        }
        return if (resolvedIp.contains(":")) {
            "[$resolvedIp]:$port"
        } else {
            "$resolvedIp:$port"
        }
    }

    /**
     * Constructs the UAPI configuration string parsed by libwg-go.so (awgTurnOn).
     */
    fun buildUapiConfig(
        iface: AwgInterfaceConfig,
        peer: AwgPeerConfig,
        preResolvedEndpoint: String? = null
    ): String {
        val sb = StringBuilder()

        // 1. Interface private key in hex
        val privateKeyHex = base64ToHex(iface.privateKey)
        sb.append("private_key=").append(privateKeyHex).append('\n')

        // 2. Obfuscation parameters (Jc, Jmin, Jmax, S1, S2, H1-H4)
        sb.append("jc=").append(iface.jc).append('\n')
        sb.append("jmin=").append(iface.jmin).append('\n')
        sb.append("jmax=").append(iface.jmax).append('\n')
        sb.append("s1=").append(iface.s1).append('\n')
        sb.append("s2=").append(iface.s2).append('\n')
        sb.append("h1=").append(iface.h1).append('\n')
        sb.append("h2=").append(iface.h2).append('\n')
        sb.append("h3=").append(iface.h3).append('\n')
        sb.append("h4=").append(iface.h4).append('\n')

        // 3. Replace peers flag
        sb.append("replace_peers=true\n")

        // 4. Peer public key in hex
        val publicKeyHex = base64ToHex(peer.publicKey)
        sb.append("public_key=").append(publicKeyHex).append('\n')

        // 5. Resolved endpoint IP:port (bracket-aware)
        val finalEndpoint = preResolvedEndpoint ?: resolveEndpoint(peer.endpoint)
        sb.append("endpoint=").append(finalEndpoint).append('\n')

        // 6. Allowed IPs
        peer.allowedIps.split(",").forEach { cidr ->
            val cleanCidr = cidr.trim()
            if (cleanCidr.isNotEmpty()) {
                sb.append("allowed_ip=").append(cleanCidr).append('\n')
            }
        }

        // 7. Persistent keepalive
        if (peer.persistentKeepalive > 0) {
            sb.append("persistent_keepalive_interval=").append(peer.persistentKeepalive).append('\n')
        }

        // 8. Preshared key in hex (if provided)
        if (!peer.presharedKey.isNullOrBlank()) {
            val pskHex = base64ToHex(peer.presharedKey)
            if (pskHex.isNotEmpty()) {
                sb.append("preshared_key=").append(pskHex).append('\n')
            }
        }

        return sb.toString()
    }
}
