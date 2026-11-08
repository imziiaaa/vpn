package com.veilgate.vpn.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.veilgate.vpn.BuildConfig
import java.util.UUID

class DeviceManager(context: Context) {

    private val prefs: SharedPreferences = initializePrefs(context)

    companion object {
        private const val TAG = "DeviceManager"
        private const val OLD_PREFS_NAME = "veilgate_prefs"
        private const val SECURE_PREFS_NAME = "veilgate_encrypted_prefs"

        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_LAST_SERVER_ID = "last_server_id"
        private const val KEY_CACHED_SERVERS = "cached_servers_json"

        private fun initializePrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val securePrefs = EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // Migrate unencrypted legacy preferences if they exist
                val oldPrefs = context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
                val oldAll = oldPrefs.all
                if (oldAll.isNotEmpty()) {
                    val editor = securePrefs.edit()
                    for ((key, value) in oldAll) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                        }
                    }
                    editor.apply()
                    oldPrefs.edit().clear().apply()
                    Log.i(TAG, "Successfully migrated ${oldAll.size} entries to EncryptedSharedPreferences")
                }

                securePrefs
            } catch (e: Exception) {
                Log.e(TAG, "EncryptedSharedPreferences initialization failed; falling back to private prefs", e)
                context.getSharedPreferences(OLD_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * Retrieves or generates a persistent device UUID.
     * Guaranteed to be alphanumeric + dashes, safe for Raven VPN control plane.
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

    fun getCachedServersJson(): String? {
        return prefs.getString(KEY_CACHED_SERVERS, null)
    }

    fun setCachedServersJson(json: String) {
        prefs.edit().putString(KEY_CACHED_SERVERS, json).apply()
    }
}
