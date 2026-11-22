package com.veilgate.vpn.data.network

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

internal object NetworkClient {
    // The deployment snapshot exposes only the shared-key legacy API.
    // Install a wire adapter here once the versioned mobile contract is available.
    fun mobileApi(): VeilGateApi? = null

    fun forOrigin(origin: HttpUrl): OkHttpClient {
        require(origin.isHttps && origin.username.isEmpty() && origin.password.isEmpty())
        return OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url
                if (!url.isHttps || origin.host != url.host || origin.port != url.port ||
                    url.username.isNotEmpty() || url.password.isNotEmpty()) {
                    throw IOException("Mobile API request left the configured HTTPS origin.")
                }
                chain.proceed(request)
            }
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
