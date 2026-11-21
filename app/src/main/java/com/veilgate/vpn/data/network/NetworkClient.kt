package com.veilgate.vpn.data.network

import android.content.Context
import android.util.Log
import com.veilgate.vpn.BuildConfig
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object NetworkClient {

    private const val TAG = "NetworkClient"

    val DEFAULT_BASE_URL: String
        get() = BuildConfig.DEFAULT_BASE_URL

    /**
     * Resilient DNS resolver with fallback for transiently unresolvable
     * or misconfigured host/emulator DNS environments.
     */
    private val resilientDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            // 1. Try standard system DNS first
            try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                if (addresses.isNotEmpty()) {
                    return addresses
                }
            } catch (e: Exception) {
                Log.w(TAG, "System DNS lookup failed for $hostname: ${e.message}. Applying resilient fallback...")
            }

            // 2. Pre-resolved Anycast edge addresses for default Vercel backend
            if (hostname.equals("veilgaurd.vercel.app", ignoreCase = true)) {
                try {
                    val fallbackList = listOf(
                        InetAddress.getByAddress(
                            "veilgaurd.vercel.app",
                            byteArrayOf(216.toByte(), 198.toByte(), 79.toByte(), 195.toByte())
                        ),
                        InetAddress.getByAddress(
                            "veilgaurd.vercel.app",
                            byteArrayOf(64.toByte(), 29.toByte(), 17.toByte(), 195.toByte())
                        )
                    )
                    Log.i(TAG, "Resolved $hostname via Anycast edge fallback: $fallbackList")
                    return fallbackList
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to build fallback address: ${e.message}")
                }
            }

            throw UnknownHostException("Unable to resolve host \"$hostname\": System and fallback DNS exhausted")
        }
    }

    fun createApi(context: Context, baseUrl: String = DEFAULT_BASE_URL, appKey: String? = null): VeilGateApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("X-App-Key")
        }

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val requestBuilder = original.newBuilder()
                .header("User-Agent", "VeilGate-Android/1.0.0")
                .header("Accept", "application/json")

            if (!appKey.isNullOrBlank()) {
                requestBuilder.header("X-App-Key", appKey)
            }

            chain.proceed(requestBuilder.build())
        }

        val okHttpClient = OkHttpClient.Builder()
            .dns(resilientDns)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(VeilGateApi::class.java)
    }
}
