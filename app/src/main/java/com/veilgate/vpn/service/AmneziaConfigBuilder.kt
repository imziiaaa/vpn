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
     * Constructs the UAPI configuration string parsed by libwg-go.so (awgTurnOn).
     */
    fun buildUapiConfig(
        iface: AwgInterfaceConfig,
        peer: AwgPeerConfig
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

        // 5. Resolved endpoint IP:port
        val endpointParts = peer.endpoint.split(":")
        val host = endpointParts[0].trim()
        val port = endpointParts.getOrNull(1)?.trim() ?: "51820"

        val resolvedIp = try {
            InetAddress.getByName(host).hostAddress ?: host
        } catch (e: Exception) {
            host
        }
        sb.append("endpoint=").append(resolvedIp).append(':').append(port).append('\n')

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
