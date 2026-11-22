package com.veilgate.vpn.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.util.UUID

class DeviceManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("raven_preferences", Context.MODE_PRIVATE)

    companion object {
        const val CURRENT_DISCLOSURE_VERSION = 2
        private const val DEVICE_ID = "device_id"
        private const val LAST_SERVER_ID = "last_server_id"
        private const val DISCLOSURE_VERSION = "vpn_disclosure_version"
        private const val LEGACY_MIGRATED = "legacy_migrated"
        private const val PLAIN_LEGACY = "veilgate_prefs"
        private const val ENCRYPTED_LEGACY = "veilgate_encrypted_prefs"
    }

    @Synchronized
    fun migrateLegacyPreferences() {
        if (prefs.getBoolean(LEGACY_MIGRATED, false)) return

        val plain = appContext.getSharedPreferences(PLAIN_LEGACY, Context.MODE_PRIVATE)
        val encryptedExists = File(appContext.dataDir, "shared_prefs/$ENCRYPTED_LEGACY.xml").exists() ||
            File(appContext.dataDir, "shared_prefs/$ENCRYPTED_LEGACY.xml.bak").exists()
        val encryptedSelection = if (encryptedExists) try {
            val key = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_LEGACY,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).getString(LAST_SERVER_ID, null)
        } catch (_: Exception) {
            // This affects only the previous node selection. Enrollment uses a new installation ID.
            null
        } else null

        val selection = encryptedSelection ?: plain.getString(LAST_SERVER_ID, null)
        if (getLastServerId() == null && selection != null) setLastServerId(selection)

        // Remove the legacy endpoint and key, even if the previous Keystore key is unusable.
        check(!encryptedExists || appContext.deleteSharedPreferences(ENCRYPTED_LEGACY)) {
            "Raven VPN couldn't remove old access settings. Restart the app and try again."
        }
        check(plain.edit().remove("app_key").remove("base_url").commit()) {
            "Raven VPN couldn't remove old access settings. Restart the app and try again."
        }
        check(prefs.edit().putBoolean(LEGACY_MIGRATED, true).commit()) {
            "Raven VPN couldn't save the settings upgrade. Check available storage and retry."
        }
    }

    fun getOrCreateInstallationId(): String = prefs.getString(DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { id ->
            check(prefs.edit().putString(DEVICE_ID, id).commit()) {
                "Raven VPN couldn't save its installation ID. Check available storage and retry."
            }
        }

    fun getLastServerId(): String? = prefs.getString(LAST_SERVER_ID, null)

    fun setLastServerId(serverId: String) {
        prefs.edit().putString(LAST_SERVER_ID, serverId).apply()
    }

    fun isVpnDisclosureAccepted(): Boolean =
        prefs.getInt(DISCLOSURE_VERSION, 0) == CURRENT_DISCLOSURE_VERSION

    fun acceptVpnDisclosure(): Boolean {
        val version = prefs.getInt(DISCLOSURE_VERSION, 0)
        if (prefs.edit().putInt(DISCLOSURE_VERSION, CURRENT_DISCLOSURE_VERSION).commit()) return true
        prefs.edit().putInt(DISCLOSURE_VERSION, version).apply()
        return false
    }
}
