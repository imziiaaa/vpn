package com.veilgate.vpn.service

import com.veilgate.vpn.data.model.AwgInterfaceConfig
import com.veilgate.vpn.data.model.AwgPeerConfig
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.Base64

internal data class TunnelAddress(val address: InetAddress, val prefix: Int)

internal class ValidatedTunnel(
    val addresses: List<TunnelAddress>,
    val dns: List<InetAddress>,
    val endpointAddress: InetAddress,
    val mtu: Int,
    val settings: String
)

internal object AmneziaConfigBuilder {
    fun validate(iface: AwgInterfaceConfig, peer: AwgPeerConfig): ValidatedTunnel {
        val privateKey = keyHex(iface.privateKey)
        val publicKey = keyHex(peer.publicKey)
        val sharedKey = peer.presharedKey?.let(::keyHex)
        val addresses = fields(iface.address).map(::cidr)
        require(addresses.map { it.address.javaClass }.distinct().size == addresses.size)
        require(addresses.all { it.prefix > 0 && it.address.isUsable })
        val routes = fields(peer.allowedIps).map(::cidr).filter { route ->
            addresses.any { it.address.javaClass == route.address.javaClass }
        }
        require(addresses.all { address ->
            routes.any { it.prefix == 0 && it.address.isAnyLocalAddress &&
                it.address.javaClass == address.address.javaClass }
        })
        val dns = fields(iface.dns ?: "").map(::numericAddress).filter { resolver ->
            addresses.any { it.address.javaClass == resolver.javaClass }
        }
        require(dns.isNotEmpty() && dns.all { it.isUsable })
        require(iface.mtu in 1280..1500 && peer.persistentKeepalive in 0..65535)

        val (endpointAddress, port) = resolveEndpoint(peer.endpoint)
        val endpoint = if (endpointAddress is Inet6Address) {
            "[${endpointAddress.hostAddress}]:$port"
        } else {
            "${endpointAddress.hostAddress}:$port"
        }
        val settings = buildString {
            appendLine("private_key=$privateKey")
            appendLine("jc=${iface.jc}")
            appendLine("jmin=${iface.jmin}")
            appendLine("jmax=${iface.jmax}")
            appendLine("s1=${iface.s1}")
            appendLine("s2=${iface.s2}")
            appendLine("h1=${iface.h1}")
            appendLine("h2=${iface.h2}")
            appendLine("h3=${iface.h3}")
            appendLine("h4=${iface.h4}")
            appendLine("replace_peers=true")
            appendLine("public_key=$publicKey")
            sharedKey?.let { appendLine("preshared_key=$it") }
            appendLine("endpoint=$endpoint")
            routes.forEach { appendLine("allowed_ip=${it.address.hostAddress}/${it.prefix}") }
            if (peer.persistentKeepalive > 0) {
                appendLine("persistent_keepalive_interval=${peer.persistentKeepalive}")
            }
        }
        return ValidatedTunnel(addresses, dns, endpointAddress, iface.mtu, settings)
    }

    private fun fields(value: String): List<String> =
        value.split(',').map(String::trim).also { require(it.none(String::isEmpty)) }

    private fun cidr(value: String): TunnelAddress {
        val parts = value.split('/')
        require(parts.size == 2 && parts[1].all(Char::isDigit))
        val address = numericAddress(parts[0])
        val prefix = parts[1].toInt()
        require(prefix in 0..(address.address.size * 8))
        return TunnelAddress(address, prefix)
    }

    private fun numericAddress(value: String): InetAddress {
        if (':' in value) {
            require(value.matches(Regex("[0-9A-Fa-f:.]+")))
            return InetAddress.getByName(value).also { require(it is Inet6Address) }
        }
        val octets = value.split('.')
        require(octets.size == 4 && octets.all {
            it.length in 1..3 && it.all(Char::isDigit) && it.toInt() in 0..255
        })
        return InetAddress.getByAddress(octets.map { it.toInt().toByte() }.toByteArray())
    }

    private fun resolveEndpoint(endpoint: String): Pair<InetAddress, Int> {
        val match = Regex("""(?:\[([0-9A-Fa-f:.]+)]|([A-Za-z0-9.-]{1,253})):([0-9]{1,5})""")
            .matchEntire(endpoint)
        require(match != null)
        val port = match.groupValues[3].toInt()
        require(port in 1..65535)
        val host = match.groupValues[2]
        val endpoints = when {
            host.isEmpty() -> listOf(numericAddress(match.groupValues[1])).also {
                require(it.single() is Inet6Address)
            }
            host.all { it == '.' || it.isDigit() } -> listOf(numericAddress(host))
            else -> {
                require(host.removeSuffix(".").split('.').all {
                    it.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"))
                })
                InetAddress.getAllByName(host).toList()
            }
        }
        val allowed = endpoints.filter { it.isUsable }
        val resolved = allowed.firstOrNull { it is Inet4Address } ?: allowed.firstOrNull()
        return requireNotNull(resolved) to port
    }

    private fun keyHex(value: String): String {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size == 32 && bytes.any { it != 0.toByte() })
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val InetAddress.isUsable: Boolean
        get() = !isAnyLocalAddress && !isLoopbackAddress && !isMulticastAddress && !isLinkLocalAddress
}
