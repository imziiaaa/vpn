package com.veilgate.vpn.data.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NetworkClientTest {
    private val origin = "https://vpn.example.test/".toHttpUrl()

    @Test
    fun refusesOriginWithCleartextOrEmbeddedCredentials() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkClient.forOrigin("http://vpn.example.test/".toHttpUrl())
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkClient.forOrigin("https://user:password@vpn.example.test/".toHttpUrl())
        }
    }

    @Test
    fun interceptsCrossOriginRequestsBeforeSendingCredentials() {
        val client = NetworkClient.forOrigin(origin)
        for (url in listOf(
            "https://elsewhere.example.test/api",
            "http://vpn.example.test/api",
            "https://vpn.example.test:8443/api"
        )) {
            val request = Request.Builder().url(url).header("Authorization", "Bearer qa-token").build()
            val rejected = assertThrows(IOException::class.java) { client.newCall(request).execute() }
            assertTrue(rejected.message.orEmpty().contains("configured HTTPS origin"))
        }
    }

    @Test
    fun refusesRedirectsAndNetworkRetries() {
        val client = NetworkClient.forOrigin(origin)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }
}
