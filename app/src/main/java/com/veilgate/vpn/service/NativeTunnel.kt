package com.veilgate.vpn.service

import android.net.VpnService
import android.os.ParcelFileDescriptor
import org.amnezia.awg.GoBackend
import java.net.Inet4Address
import java.net.Inet6Address

internal class NativeTunnel private constructor(private val handle: Int) : AutoCloseable {
    data class Stats(val handshakeSeconds: Long, val bytes: Long)

    fun stats(): Stats {
        val values = GoBackend.awgGetConfig(handle).orEmpty().lineSequence()
            .mapNotNull { line ->
                val separator = line.indexOf('=')
                if (separator < 0) null else line.substring(separator + 1).toLongOrNull()
                    ?.let { line.substring(0, separator) to it }
            }.toMap()
        return Stats(
            values["last_handshake_time_sec"] ?: 0,
            (values["rx_bytes"] ?: 0) + (values["tx_bytes"] ?: 0)
        )
    }

    override fun close() = GoBackend.awgTurnOff(handle)

    companion object {
        fun open(
            service: VpnService,
            name: String,
            config: ValidatedTunnel,
            isCanceled: () -> Boolean
        ): NativeTunnel? {
            if (isCanceled()) return null
            // Resolve the JNI library and an exported method before transferring the descriptor.
            GoBackend.awgVersion()
            val builder = service.Builder()
                .setSession("Raven VPN ($name)")
                .setMtu(config.mtu)
                .setBlocking(true)
            for ((address, prefix) in config.addresses) builder.addAddress(address, prefix)
            if (config.addresses.any { it.address is Inet4Address }) builder.addRoute("0.0.0.0", 0)
            if (config.addresses.any { it.address is Inet6Address }) builder.addRoute("::", 0)
            // Android blocks an address family with no address, route or DNS server in that family.
            config.dns.forEach { builder.addDnsServer(it) }

            val handle = requireNotNull(builder.establish()) {
                "Android VPN permission is unavailable. Open Raven VPN and tap Protect to retry."
            }.use { tun ->
                if (isCanceled()) return null
                val descriptor = tun.detachFd()
                try {
                    // Once JNI dispatch succeeds, Go owns the descriptor even if startup fails.
                    GoBackend.awgTurnOn("raven", descriptor, config.settings)
                } catch (error: LinkageError) {
                    ParcelFileDescriptor.adoptFd(descriptor).close()
                    throw error
                }
            }
            check(handle >= 0) { "The VPN engine couldn't start. Choose a different node or retry." }
            val tunnel = NativeTunnel(handle)
            try {
                val v4 = GoBackend.awgGetSocketV4(handle)
                val v6 = GoBackend.awgGetSocketV6(handle)
                check((v4 < 0 || service.protect(v4)) && (v6 < 0 || service.protect(v6)) &&
                    (if (config.endpointAddress is Inet4Address) v4 >= 0 else v6 >= 0)) {
                    "Android couldn't reserve a connection to the VPN node. Try again."
                }
            } catch (error: Throwable) {
                tunnel.close()
                throw error
            }
            return tunnel
        }
    }
}
