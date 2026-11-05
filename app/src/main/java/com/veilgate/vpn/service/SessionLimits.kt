package com.veilgate.vpn.service

import java.util.Locale

object SessionLimits {
    const val DEFAULT_TIME_LIMIT_SECONDS: Long = 3600L // Exactly 1 hour
    const val DEFAULT_QUOTA_LIMIT_BYTES: Long = 1073741824L // Exactly 1 GB (1024 * 1024 * 1024)

    fun formatSeconds(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val mins = safeSeconds / 60
        val secs = safeSeconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    fun formatBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0)
        val mb = safeBytes.toDouble() / (1024 * 1024)
        return if (mb >= 1000) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
