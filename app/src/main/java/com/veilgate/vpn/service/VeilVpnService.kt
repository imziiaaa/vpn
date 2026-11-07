package com.veilgate.vpn.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.veilgate.vpn.R
import com.veilgate.vpn.VeilGateApp
import com.veilgate.vpn.data.model.AwgInterfaceConfig
import com.veilgate.vpn.data.model.AwgPeerConfig
import com.veilgate.vpn.data.model.DisconnectReason
import com.veilgate.vpn.data.model.VpnStatus
import com.veilgate.vpn.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.GoBackend
import java.net.InetSocketAddress
import java.net.Socket

class VeilVpnService : VpnService() {

    companion object {
        private const val TAG = "VeilVpnService"
        private const val NOTIFICATION_ID = 7001

        const val ACTION_START = "com.veilgate.vpn.START"
        const val ACTION_STOP = "com.veilgate.vpn.STOP"

        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_SERVER_LOCATION = "extra_server_location"

        // AmneziaWG Interface Params
        const val EXTRA_PRIVATE_KEY = "extra_private_key"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_DNS = "extra_dns"
        const val EXTRA_MTU = "extra_mtu"
        const val EXTRA_JC = "extra_jc"
        const val EXTRA_JMIN = "extra_jmin"
        const val EXTRA_JMAX = "extra_jmax"
        const val EXTRA_S1 = "extra_s1"
        const val EXTRA_S2 = "extra_s2"
        const val EXTRA_H1 = "extra_h1"
        const val EXTRA_H2 = "extra_h2"
        const val EXTRA_H3 = "extra_h3"
        const val EXTRA_H4 = "extra_h4"

        // AmneziaWG Peer Params
        const val EXTRA_PUBLIC_KEY = "extra_public_key"
        const val EXTRA_PRESHARED_KEY = "extra_preshared_key"
        const val EXTRA_ENDPOINT = "extra_endpoint"
        const val EXTRA_ALLOWED_IPS = "extra_allowed_ips"
        const val EXTRA_KEEPALIVE = "extra_keepalive"

        const val EXTRA_REASON = "extra_reason"

        // StateFlow exposed to the UI ViewModel
        private val _vpnStatusFlow = MutableStateFlow(VpnStatus.DISCONNECTED)
        val vpnStatusFlow: StateFlow<VpnStatus> = _vpnStatusFlow.asStateFlow()

        private val _remainingSecondsFlow = MutableStateFlow(SessionLimits.DEFAULT_TIME_LIMIT_SECONDS)
        val remainingSecondsFlow: StateFlow<Long> = _remainingSecondsFlow.asStateFlow()

        private val _usedBytesFlow = MutableStateFlow(0L)
        val usedBytesFlow: StateFlow<Long> = _usedBytesFlow.asStateFlow()

        private val _lastDisconnectReasonFlow = MutableStateFlow<DisconnectReason?>(null)
        val lastDisconnectReasonFlow: StateFlow<DisconnectReason?> = _lastDisconnectReasonFlow.asStateFlow()

        fun startService(
            context: Context,
            serverName: String,
            location: String,
            iface: AwgInterfaceConfig,
            peer: AwgPeerConfig
        ) {
            val intent = Intent(context, VeilVpnService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_NAME, serverName)
                putExtra(EXTRA_SERVER_LOCATION, location)

                // Interface
                putExtra(EXTRA_PRIVATE_KEY, iface.privateKey)
                putExtra(EXTRA_ADDRESS, iface.address)
                putExtra(EXTRA_DNS, iface.dns)
                putExtra(EXTRA_MTU, iface.mtu)
                putExtra(EXTRA_JC, iface.jc)
                putExtra(EXTRA_JMIN, iface.jmin)
                putExtra(EXTRA_JMAX, iface.jmax)
                putExtra(EXTRA_S1, iface.s1)
                putExtra(EXTRA_S2, iface.s2)
                putExtra(EXTRA_H1, iface.h1)
                putExtra(EXTRA_H2, iface.h2)
                putExtra(EXTRA_H3, iface.h3)
                putExtra(EXTRA_H4, iface.h4)

                // Peer
                putExtra(EXTRA_PUBLIC_KEY, peer.publicKey)
                putExtra(EXTRA_PRESHARED_KEY, peer.presharedKey)
                putExtra(EXTRA_ENDPOINT, peer.endpoint)
                putExtra(EXTRA_ALLOWED_IPS, peer.allowedIps)
                putExtra(EXTRA_KEEPALIVE, peer.persistentKeepalive)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context, reason: DisconnectReason = DisconnectReason.USER_MANUAL) {
            val intent = Intent(context, VeilVpnService::class.java).apply {
                action = ACTION_STOP
                putExtra(EXTRA_REASON, reason.name)
            }
            context.startService(intent)
        }
    }

    private var currentTunnelHandle: Int = -1
    private var monitorJob: Job? = null
    private var healthCheckJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private var serverName: String = "VeilGate Node"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                serverName = intent.getStringExtra(EXTRA_SERVER_NAME) ?: "VeilGate Node"

                val iface = AwgInterfaceConfig(
                    privateKey = intent.getStringExtra(EXTRA_PRIVATE_KEY) ?: "",
                    address = intent.getStringExtra(EXTRA_ADDRESS) ?: "10.8.1.2/32",
                    dns = intent.getStringExtra(EXTRA_DNS) ?: "1.1.1.1, 8.8.8.8",
                    mtu = intent.getIntExtra(EXTRA_MTU, 1280),
                    jc = intent.getIntExtra(EXTRA_JC, 4),
                    jmin = intent.getIntExtra(EXTRA_JMIN, 40),
                    jmax = intent.getIntExtra(EXTRA_JMAX, 70),
                    s1 = intent.getIntExtra(EXTRA_S1, 0),
                    s2 = intent.getIntExtra(EXTRA_S2, 0),
                    h1 = intent.getIntExtra(EXTRA_H1, 0),
                    h2 = intent.getIntExtra(EXTRA_H2, 0),
                    h3 = intent.getIntExtra(EXTRA_H3, 0),
                    h4 = intent.getIntExtra(EXTRA_H4, 0)
                )

                val peer = AwgPeerConfig(
                    publicKey = intent.getStringExtra(EXTRA_PUBLIC_KEY) ?: "",
                    presharedKey = intent.getStringExtra(EXTRA_PRESHARED_KEY),
                    endpoint = intent.getStringExtra(EXTRA_ENDPOINT) ?: "",
                    allowedIps = intent.getStringExtra(EXTRA_ALLOWED_IPS) ?: "0.0.0.0/0, ::/0",
                    persistentKeepalive = intent.getIntExtra(EXTRA_KEEPALIVE, 25)
                )

                serviceScope.launch {
                    startAmneziaTunnel(iface, peer)
                }
            }
            ACTION_STOP -> {
                val reasonName = intent.getStringExtra(EXTRA_REASON)
                val reason = reasonName?.let {
                    runCatching { DisconnectReason.valueOf(it) }.getOrNull()
                } ?: DisconnectReason.USER_MANUAL
                stopTunnel(reason)
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun startAmneziaTunnel(iface: AwgInterfaceConfig, peer: AwgPeerConfig) {
        try {
            _vpnStatusFlow.value = VpnStatus.CONNECTING
            _lastDisconnectReasonFlow.value = null
            _remainingSecondsFlow.value = SessionLimits.DEFAULT_TIME_LIMIT_SECONDS
            _usedBytesFlow.value = 0L

            withContext(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, buildNotification(SessionLimits.DEFAULT_TIME_LIMIT_SECONDS, 0L))
            }

            // Build TUN interface
            val cleanIp = iface.address.split("/")[0].trim()
            val builder = Builder()
                .setSession("VeilGate ($serverName)")
                .setMtu(iface.mtu.coerceIn(1280, 1420))
                .addAddress(cleanIp, 32)
                .addRoute("0.0.0.0", 0)
                .setBlocking(true)

            // DNS configuration with robust fallback
            val dnsList = if (!iface.dns.isNullOrBlank()) {
                iface.dns.split(",")
            } else {
                listOf("1.1.1.1", "8.8.8.8")
            }

            dnsList.forEach { s ->
                val trimmed = s.trim()
                if (trimmed.isNotEmpty()) {
                    try {
                        builder.addDnsServer(trimmed)
                    } catch (e: Exception) {
                        Log.w(TAG, "Invalid DNS address: $trimmed", e)
                    }
                }
            }

            val pfd = withContext(Dispatchers.Main) {
                builder.establish()
            }
            if (pfd == null) {
                Log.e(TAG, "Failed to establish VPN interface descriptor")
                stopTunnel(DisconnectReason.CONNECTION_FAILED)
                return
            }

            // Detach descriptor to pass ownership to libwg-go.so
            val tunFd = pfd.detachFd()

            // Build AmneziaWG UAPI userspace configuration (runs on IO dispatcher)
            val uapiConfig = withContext(Dispatchers.IO) {
                AmneziaConfigBuilder.buildUapiConfig(iface, peer)
            }
            Log.d(TAG, "Starting AmneziaWG Go core version: ${GoBackend.awgVersion()}")

            val handle = GoBackend.awgTurnOn("awg0", tunFd, uapiConfig)
            if (handle < 0) {
                Log.e(TAG, "AmneziaWG awgTurnOn failed with error code: $handle")
                stopTunnel(DisconnectReason.CONNECTION_FAILED)
                return
            }

            currentTunnelHandle = handle

            // Protect underlying UDP sockets from VPN routing loop
            val sockV4 = GoBackend.awgGetSocketV4(handle)
            val sockV6 = GoBackend.awgGetSocketV6(handle)
            if (sockV4 > 0) protect(sockV4)
            if (sockV6 > 0) protect(sockV6)

            Log.i(TAG, "AmneziaWG tunnel established successfully (handle=$handle, sockV4=$sockV4, sockV6=$sockV6)")
            _vpnStatusFlow.value = VpnStatus.CONNECTED

            // 1. Register physical network monitor (detects cellular/Wi-Fi drops)
            registerNetworkMonitor()

            // 2. Launch active 1-hour & 1-GB limit monitor
            startLimitEnforcementMonitor()

            // 3. Launch watchdog probe to detect dropped tunnel or loss of internet
            startHealthCheckMonitor()

        } catch (e: Exception) {
            Log.e(TAG, "Exception while starting AmneziaWG tunnel", e)
            stopTunnel(DisconnectReason.CONNECTION_FAILED)
        }
    }

    /**
     * Active coroutine loop running every 1 second:
     * 1. Decrements the 1-hour session timer.
     * 2. Calculates monotonic data transferred (rx + tx) against the 1-GB limit.
     * 3. Disconnects immediately if either limit is breached.
     */
    private fun startLimitEnforcementMonitor() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            var secondsLeft = SessionLimits.DEFAULT_TIME_LIMIT_SECONDS
            val myUid = android.os.Process.myUid()

            var lastRx = TrafficStats.getUidRxBytes(myUid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            var lastTx = TrafficStats.getUidTxBytes(myUid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: 0L
            var accumulatedBytes = 0L

            while (isActive && _vpnStatusFlow.value == VpnStatus.CONNECTED) {
                delay(1000L)
                secondsLeft -= 1
                _remainingSecondsFlow.value = secondsLeft.coerceAtLeast(0)

                // 1. Check TIME LIMIT (1 Hour = 3600s)
                if (secondsLeft <= 0) {
                    Log.i(TAG, "Session expired: 1-hour time limit reached.")
                    stopTunnel(DisconnectReason.TIME_LIMIT_EXCEEDED)
                    break
                }

                // 2. Check DATA QUOTA LIMIT (1 GB = 1,073,741,824 bytes) using monotonic deltas
                val curRx = TrafficStats.getUidRxBytes(myUid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: lastRx
                val curTx = TrafficStats.getUidTxBytes(myUid).takeIf { it != TrafficStats.UNSUPPORTED.toLong() } ?: lastTx

                if (curRx >= lastRx) {
                    accumulatedBytes += (curRx - lastRx)
                }
                if (curTx >= lastTx) {
                    accumulatedBytes += (curTx - lastTx)
                }
                lastRx = curRx
                lastTx = curTx

                _usedBytesFlow.value = accumulatedBytes

                if (accumulatedBytes >= SessionLimits.DEFAULT_QUOTA_LIMIT_BYTES) {
                    Log.i(TAG, "Session expired: 1-GB data quota reached ($accumulatedBytes bytes).")
                    stopTunnel(DisconnectReason.QUOTA_LIMIT_EXCEEDED)
                    break
                }

                // Update ongoing status notification
                updateNotification(secondsLeft, accumulatedBytes)
            }
        }
    }

    private fun stopTunnel(reason: DisconnectReason) {
        _vpnStatusFlow.value = VpnStatus.DISCONNECTING
        _lastDisconnectReasonFlow.value = reason

        monitorJob?.cancel()
        monitorJob = null

        healthCheckJob?.cancel()
        healthCheckJob = null

        unregisterNetworkMonitor()

        if (currentTunnelHandle >= 0) {
            try {
                Log.i(TAG, "Turning off AmneziaWG tunnel (handle=$currentTunnelHandle)")
                GoBackend.awgTurnOff(currentTunnelHandle)
            } catch (e: Exception) {
                Log.e(TAG, "Error turning off awg tunnel", e)
            }
            currentTunnelHandle = -1
        }

        _vpnStatusFlow.value = VpnStatus.DISCONNECTED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Active watchdog running every 5-6 seconds:
     * 1. Checks if initial handshake was established within 15 seconds.
     * 2. Tests internet reachability over the tunnel by connecting to anycast DNS (1.1.1.1 / 8.8.8.8) on port 53.
     * 3. Checks if WireGuard handshake became stale (>120s) with failed probes.
     * 4. Automatically tears down tunnel with CONNECTION_LOST if 3 consecutive probes fail.
     */
    private fun startHealthCheckMonitor() {
        healthCheckJob?.cancel()
        healthCheckJob = serviceScope.launch(Dispatchers.IO) {
            var consecutiveFailures = 0
            var secondsElapsed = 0

            // Initial grace period to allow tunnel and initial handshake to settle
            delay(5000L)
            secondsElapsed += 5

            while (isActive && _vpnStatusFlow.value == VpnStatus.CONNECTED) {
                val health = getHandshakeHealth()

                // Initial handshake validation (between 15-20s after connect)
                if (secondsElapsed in 15..22 && health.lastHandshakeSec == 0L) {
                    Log.e(TAG, "Initial WireGuard handshake was never established after 15s. Aborting tunnel.")
                    stopTunnel(DisconnectReason.CONNECTION_FAILED)
                    break
                }

                // Active probe through the VPN tunnel
                val reachable = checkInternetReachability()
                if (reachable) {
                    if (consecutiveFailures > 0) {
                        Log.i(TAG, "Internet connectivity restored via tunnel (was $consecutiveFailures failure(s))")
                    }
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures++
                    Log.w(TAG, "Internet probe through tunnel failed ($consecutiveFailures/3 consecutive failures)")

                    if (consecutiveFailures >= 3) {
                        Log.e(TAG, "Lost internet reachability through VPN tunnel (3 consecutive failed probes). Disconnecting.")
                        stopTunnel(DisconnectReason.CONNECTION_LOST)
                        break
                    }
                }

                // Stale handshake check: if handshake is >120s old and probe failed, peer is dead
                if (health.ageSec > 120 && consecutiveFailures >= 2) {
                    Log.e(TAG, "WireGuard handshake expired (${health.ageSec}s old) and internet unreachable. Disconnecting.")
                    stopTunnel(DisconnectReason.CONNECTION_LOST)
                    break
                }

                delay(6000L)
                secondsElapsed += 6
            }
        }
    }

    /**
     * Probes external internet access through the VPN tunnel without requiring DNS resolution.
     */
    private suspend fun checkInternetReachability(): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            // Direct IP connect to Cloudflare DNS port 53 (TCP) with 2500ms timeout
            socket.connect(InetSocketAddress("1.1.1.1", 53), 2500)
            true
        } catch (e: Exception) {
            // Fallback to Google DNS port 53 (TCP)
            var fallbackSocket: Socket? = null
            try {
                fallbackSocket = Socket()
                fallbackSocket.connect(InetSocketAddress("8.8.8.8", 53), 2500)
                true
            } catch (e2: Exception) {
                false
            } finally {
                runCatching { fallbackSocket?.close() }
            }
        } finally {
            runCatching { socket?.close() }
        }
    }

    private data class HandshakeHealth(
        val lastHandshakeSec: Long,
        val ageSec: Long,
        val rxBytes: Long,
        val txBytes: Long
    )

    private fun getHandshakeHealth(): HandshakeHealth {
        val handle = currentTunnelHandle
        if (handle < 0) return HandshakeHealth(0L, -1L, 0L, 0L)
        val config = try {
            GoBackend.awgGetConfig(handle)
        } catch (e: Exception) {
            null
        } ?: return HandshakeHealth(0L, -1L, 0L, 0L)

        var lastHandshakeSec = 0L
        var rxBytes = 0L
        var txBytes = 0L

        for (line in config.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("last_handshake_time_sec=")) {
                lastHandshakeSec = trimmed.substringAfter("=").trim().toLongOrNull() ?: 0L
            } else if (trimmed.startsWith("rx_bytes=")) {
                rxBytes = trimmed.substringAfter("=").trim().toLongOrNull() ?: 0L
            } else if (trimmed.startsWith("tx_bytes=")) {
                txBytes = trimmed.substringAfter("=").trim().toLongOrNull() ?: 0L
            }
        }

        val nowSec = System.currentTimeMillis() / 1000L
        val ageSec = if (lastHandshakeSec > 0) (nowSec - lastHandshakeSec).coerceAtLeast(0) else -1L
        return HandshakeHealth(lastHandshakeSec, ageSec, rxBytes, txBytes)
    }

    private fun registerNetworkMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                Log.w(TAG, "Device underlying physical network lost.")
                serviceScope.launch {
                    delay(4000L)
                    val active = cm.activeNetwork
                    if (active == null && _vpnStatusFlow.value == VpnStatus.CONNECTED) {
                        Log.e(TAG, "No active physical network after grace period. Auto-disconnecting.")
                        stopTunnel(DisconnectReason.CONNECTION_LOST)
                    }
                }
            }
        }
        networkCallback = callback
        try {
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkMonitor() {
        networkCallback?.let { cb ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            try {
                cm?.unregisterNetworkCallback(cb)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback", e)
            }
            networkCallback = null
        }
    }

    private fun buildNotification(remainingSeconds: Long, usedBytes: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, VeilVpnService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_REASON, DisconnectReason.USER_MANUAL.name)
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = SessionLimits.formatSeconds(remainingSeconds)
        val dataStr = "${SessionLimits.formatBytes(usedBytes)} / 1.00 GB"

        return NotificationCompat.Builder(this, VeilGateApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_lock)
            .setContentTitle("VeilGate · $serverName")
            .setContentText("⏳ $timeStr left   |   📊 $dataStr")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Connected to $serverName\nTime Remaining: $timeStr (1h limit)\nData Used: $dataStr (1GB limit)"
            ))
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(remainingSeconds: Long, usedBytes: Long) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(remainingSeconds, usedBytes))
    }

    override fun onDestroy() {
        stopTunnel(DisconnectReason.USER_MANUAL)
        super.onDestroy()
    }
}
