package com.veilgate.vpn

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class VeilGateApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "veilgate_vpn_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Raven VPN connection"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the VPN connection status and lets you disconnect."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
