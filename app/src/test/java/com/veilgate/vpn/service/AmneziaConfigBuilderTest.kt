package com.veilgate.vpn.service

import com.veilgate.vpn.data.model.AwgInterfaceConfig
import com.veilgate.vpn.data.model.AwgPeerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.util.Base64

class AmneziaConfigBuilderTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val iface = AwgInterfaceConfig(
        privateKey = key,
        address = "10.8.1.2/32",
        dns = "1.1.1.1, 2606:4700:4700::1111",
        jc = 5, jmin = 42, jmax = 80, s1 = 19, s2 = 20,
        h1 = 1_234_567_890, h2 = 2_345_678_901, h3 = 3_456_789_012, h4 = 4_123_456_789
    )
    private val peer = AwgPeerConfig(
        publicKey = key,
        presharedKey = key,
        endpoint = "192.0.2.20:51821"
    )

    @Test
    fun ipv4OnlyRejectsIpv6RoutingEvenWhenThePeerAdvertisesIt() {
        val config = AmneziaConfigBuilder.validate(iface, peer)
        assertTrue(config.addresses.single().address is Inet4Address)
        assertTrue(config.dns.single() is Inet4Address)
        assertFalse(config.settings.lineSequence().any { it.startsWith("allowed_ip=") && ':' in it })
    }

    @Test
    fun dualStackKeepsRealIpv6AddressAndResolver() {
        val config = AmneziaConfigBuilder.validate(
            iface.copy(address = "10.8.1.2/32, fd00::2/128"),
            peer.copy(endpoint = "[2001:db8::20]:51821")
        )
        assertTrue(config.addresses.any { it.address is Inet6Address && it.prefix == 128 })
        assertTrue(config.dns.any { it is Inet6Address })
        assertTrue(config.settings.lineSequence().any { it.startsWith("allowed_ip=") && ':' in it })
        assertTrue(config.endpointAddress is Inet6Address)
    }

    @Test
    fun validatesEveryKeyAndRejectsMalformedAddressesAndEndpoints() {
        for (invalid in listOf("", "invalid", Base64.getEncoder().encodeToString(ByteArray(31) { 1 }))) {
            assertThrows(IllegalArgumentException::class.java) {
                AmneziaConfigBuilder.validate(iface.copy(privateKey = invalid), peer)
            }
            assertThrows(IllegalArgumentException::class.java) {
                AmneziaConfigBuilder.validate(iface, peer.copy(publicKey = invalid))
            }
            assertThrows(IllegalArgumentException::class.java) {
                AmneziaConfigBuilder.validate(iface, peer.copy(presharedKey = invalid))
            }
        }
        for (address in listOf("10.0.0.256/32", "10.8.1.2", "10.8.1.2/33", "example.com/32")) {
            assertThrows(IllegalArgumentException::class.java) {
                AmneziaConfigBuilder.validate(iface.copy(address = address), peer)
            }
        }
        for (endpoint in listOf("192.0.2.2", "192.0.2.2:65536", "192.0.2.2:bad", "[2001:db8::20]")) {
            assertThrows(IllegalArgumentException::class.java) {
                AmneziaConfigBuilder.validate(iface, peer.copy(endpoint = endpoint))
            }
        }
    }

    @Test
    fun emitsAmneziaParametersAndKeysWithoutDroppingUnsignedHeaderValues() {
        val config = AmneziaConfigBuilder.validate(iface, peer)
        val settings = config.settings.lineSequence().filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val expected = mapOf("jc" to "5", "jmin" to "42", "jmax" to "80", "s1" to "19",
            "s2" to "20", "h1" to "1234567890", "h2" to "2345678901", "h3" to "3456789012",
            "h4" to "4123456789")
        assertEquals(expected, settings.filterKeys { it in expected.keys })
        assertEquals("01".repeat(32), settings["private_key"])
        assertEquals("01".repeat(32), settings["public_key"])
        assertEquals("01".repeat(32), settings["preshared_key"])
        assertEquals("192.0.2.20:51821", settings["endpoint"])
    }
}
