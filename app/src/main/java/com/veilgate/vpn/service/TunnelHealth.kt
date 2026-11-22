package com.veilgate.vpn.service

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random

internal object TunnelHealth {
    fun checkDns(connectivity: ConnectivityManager, resolver: InetAddress, timeoutMs: Int): Boolean {
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false

        val id = Random.nextInt(0x10000)
        val query = byteArrayOf(
            (id shr 8).toByte(), id.toByte(), 1, 0, 0, 1, 0, 0, 0, 0, 0, 0,
            7, 101, 120, 97, 109, 112, 108, 101, 3, 99, 111, 109, 0,
            0, 1, 0, 1
        )
        return try {
            DatagramSocket().use { socket ->
                network.bindSocket(socket)
                socket.soTimeout = timeoutMs
                socket.connect(resolver, 53)
                socket.send(DatagramPacket(query, query.size))
                val buffer = ByteArray(512)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                reply.length >= 12 &&
                    buffer[0] == query[0] && buffer[1] == query[1] &&
                    (buffer[2].toInt() and 0xfa) == 0x80 &&
                    (buffer[3].toInt() and 0x0f) == 0 &&
                    (buffer[6].toInt() != 0 || buffer[7].toInt() != 0)
            }
        } catch (_: IOException) {
            false
        }
    }
}
