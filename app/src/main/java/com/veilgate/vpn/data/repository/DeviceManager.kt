package com.veilgate.vpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.veilgate.vpn.BuildConfig
import java.util.UUID

class DeviceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("veilgate_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_LAST_SERVER_ID = "last_server_id"
    }

    /**
     * Retrieves or generates a persistent device UUID.
     * Guaranteed to be alphanumeric + dashes, safe for VeilGate control plane.
     */
    fun getDeviceId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            val generated = "vg-" + UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            id = generated
        }
        return id
    }

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, null) ?: BuildConfig.DEFAULT_BASE_URL
    }

    fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getAppKey(): String {
        return prefs.getString(KEY_APP_KEY, null) ?: BuildConfig.DEFAULT_APP_KEY
    }

    fun setAppKey(key: String?) {
        prefs.edit().putString(KEY_APP_KEY, key).apply()
    }

    fun getLastServerId(): String? {
        return prefs.getString(KEY_LAST_SERVER_ID, null)
    }

    fun setLastServerId(serverId: String) {
        prefs.edit().putString(KEY_LAST_SERVER_ID, serverId).apply()
    }
}
