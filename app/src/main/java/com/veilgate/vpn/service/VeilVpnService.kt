package com.veilgate.vpn.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.veilgate.vpn.R
import com.veilgate.vpn.VeilGateApp
import com.veilgate.vpn.data.model.DisconnectReason
import com.veilgate.vpn.data.model.TunnelLease
import com.veilgate.vpn.data.model.VpnStatus
import com.veilgate.vpn.data.repository.VpnRepository
import com.veilgate.vpn.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class VeilVpnService : VpnService() {
    private class Attempt(val id: String, val nodeId: String, val nodeName: String) {
        @Volatile
        var canceled = false
        var service: VeilVpnService? = null
    }

    companion object {
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_START = "com.veilgate.vpn.START"
        private const val ACTION_STOP = "com.veilgate.vpn.STOP"
        private const val EXTRA_ATTEMPT = "attempt"
        private const val HEALTH_DEADLINE_MS = 25_000L
        private const val HEALTH_INTERVAL_MS = 15_000L
        private const val MAX_HANDSHAKE_AGE_SECONDS = 180L

        private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val connectionLock = Mutex()
        private var currentAttempt: Attempt? = null

        private val _vpnStatusFlow = MutableStateFlow(VpnStatus.DISCONNECTED)
        val vpnStatusFlow = _vpnStatusFlow.asStateFlow()
        private val _remainingSecondsFlow = MutableStateFlow(SessionLimits.DEFAULT_TIME_LIMIT_SECONDS)
        val remainingSecondsFlow = _remainingSecondsFlow.asStateFlow()
        private val _usedBytesFlow = MutableStateFlow(0L)
        val usedBytesFlow = _usedBytesFlow.asStateFlow()
        private val _lastDisconnectReasonFlow = MutableStateFlow<DisconnectReason?>(null)
        val lastDisconnectReasonFlow = _lastDisconnectReasonFlow.asStateFlow()
        private val _errorFlow = MutableStateFlow<String?>(null)
        val errorFlow = _errorFlow.asStateFlow()

        @MainThread
        fun startService(context: Context, nodeId: String, nodeName: String) {
            if (currentAttempt != null) return
            val attempt = Attempt(UUID.randomUUID().toString(), nodeId, nodeName)
            currentAttempt = attempt
            _vpnStatusFlow.value = VpnStatus.CONNECTING
            _lastDisconnectReasonFlow.value = null
            _errorFlow.value = null
            _remainingSecondsFlow.value = SessionLimits.DEFAULT_TIME_LIMIT_SECONDS
            _usedBytesFlow.value = 0

            try {
                checkNotNull(context.startForegroundService(Intent(context, VeilVpnService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ATTEMPT, attempt.id)
                }))
            } catch (_: RuntimeException) {
                currentAttempt = null
                _vpnStatusFlow.value = VpnStatus.ERROR
                _errorFlow.value = "Android couldn't start the VPN. Keep Raven VPN open and tap Protect again."
            }
        }

        @MainThread
        fun stopService() {
            val attempt = currentAttempt ?: return
            attempt.canceled = true
            _vpnStatusFlow.value = VpnStatus.DISCONNECTING
            attempt.service?.finish(attempt, DisconnectReason.USER_MANUAL)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository by lazy { VpnRepository.get(applicationContext) }
    private var attempt: Attempt? = null
    private var tunnelJob: Job? = null
    private var startId = 0

    override fun onStartCommand(intent: Intent?, flags: Int, incomingStartId: Int): Int {
        startId = incomingStartId
        val requestedId = intent?.getStringExtra(EXTRA_ATTEMPT)
        val requested = currentAttempt?.takeIf { it.id == requestedId }
        when (intent?.action) {
            ACTION_START -> {
                if (requested != null && attempt == null) startAttempt(requested)
                else if (attempt == null) stopSelfResult(startId)
            }
            ACTION_STOP -> {
                if (requested != null) stopService()
                else if (attempt == null) stopSelfResult(startId)
            }
            else -> if (attempt == null) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun startAttempt(opening: Attempt) {
        attempt = opening
        opening.service = this

        try {
            // Promote immediately, including when Cancel arrived before onStartCommand.
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(opening, VpnStatus.CONNECTING),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED else 0
            )
        } catch (_: RuntimeException) {
            finish(opening, DisconnectReason.CONNECTION_FAILED,
                "Android couldn't start the VPN. Check VPN permissions and tap Protect again.")
            return
        }
        if (opening.canceled) {
            finish(opening, DisconnectReason.USER_MANUAL)
            return
        }

        // A provision request can create a lease even if the Activity or Service goes away.
        // Keep the bounded request alive so a late lease can be released by its session ID.
        val requests = repository
        processScope.launch {
            connectionLock.withLock {
                if (opening.canceled || currentAttempt !== opening) return@withLock
                val result = requests.requestTunnel(opening.nodeId, opening.id)
                result.onSuccess { lease ->
                    try {
                        if (!opening.canceled && currentAttempt === opening) {
                            opening.service?.runTunnel(opening, lease)?.join()
                        }
                    } finally {
                        requests.releaseLease(lease.sessionId)
                    }
                }.onFailure { error ->
                    opening.service?.finish(opening, DisconnectReason.CONNECTION_FAILED,
                        error.message ?: "Couldn't set up this VPN node. Try again.")
                }
            }
        }
    }

    private fun runTunnel(opening: Attempt, lease: TunnelLease): Job =
        scope.launch {
            try {
                require(lease.remainingSeconds in 1L..Int.MAX_VALUE.toLong() &&
                    lease.dataLimitBytes > 0 && lease.usedBytes >= 0)
                val leaseDeadline = SystemClock.elapsedRealtime() + lease.remainingSeconds * 1_000
                val config = withContext(Dispatchers.IO) {
                    AmneziaConfigBuilder.validate(lease.interfaceConfig, lease.peer)
                }
                runNativeTunnel(opening, lease, leaseDeadline, config)
            } catch (error: CancellationException) {
                throw error
            } catch (_: LinkageError) {
                finish(opening, DisconnectReason.CONNECTION_FAILED,
                    "The Raven VPN engine couldn't load on this device. Check for an app update.")
            } catch (_: IllegalArgumentException) {
                finish(opening, DisconnectReason.CONNECTION_FAILED,
                    "This node sent invalid VPN settings. Choose another node or contact support.")
            } catch (_: Exception) {
                finish(opening, DisconnectReason.CONNECTION_FAILED,
                    "Couldn't open a working VPN tunnel. Choose another node or retry.")
            }
        }.also { tunnelJob = it }

    private suspend fun runNativeTunnel(
        opening: Attempt,
        lease: TunnelLease,
        leaseDeadline: Long,
        config: ValidatedTunnel
    ) {
        var tunnel: NativeTunnel? = null
        try {
            val active = withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    NativeTunnel.open(this@VeilVpnService, opening.nodeName, config) { opening.canceled }
                }.also { tunnel = it }
            } ?: return
            currentCoroutineContext().ensureActive()
            monitor(opening, lease, leaseDeadline, active, config)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { tunnel?.close() }
        }
    }

    private suspend fun monitor(
        opening: Attempt,
        lease: TunnelLease,
        leaseDeadline: Long,
        tunnel: NativeTunnel,
        config: ValidatedTunnel
    ) {
        var healthDeadline = SystemClock.elapsedRealtime() + HEALTH_DEADLINE_MS
        var nextCheck = 0L
        var receivedTraffic = false
        var healthy = false
        var resolverIndex = 0
        val connectivity = checkNotNull(getSystemService(ConnectivityManager::class.java))

        while (!opening.canceled) {
            val now = SystemClock.elapsedRealtime()
            val remaining = (leaseDeadline - now).coerceAtLeast(0)
            _remainingSecondsFlow.value = (remaining + 999) / 1000
            if (remaining == 0L) {
                finish(opening, DisconnectReason.TIME_LIMIT_EXCEEDED)
                return
            }

            val stats = withContext(Dispatchers.IO) { tunnel.stats() }
            _usedBytesFlow.value = lease.usedBytes + stats.bytes.coerceIn(0, Long.MAX_VALUE - lease.usedBytes)
            if (_usedBytesFlow.value >= lease.dataLimitBytes) {
                finish(opening, DisconnectReason.QUOTA_LIMIT_EXCEEDED)
                return
            }

            if (now >= nextCheck) {
                val dnsOk = withContext(Dispatchers.IO) {
                    val resolver = config.dns[resolverIndex % config.dns.size]
                    TunnelHealth.checkDns(connectivity, resolver, 1_000)
                }
                resolverIndex++
                val handshake = withContext(Dispatchers.IO) { tunnel.stats() }.handshakeSeconds
                val handshakeAge = System.currentTimeMillis() / 1000L - handshake
                if (dnsOk && handshake > 0 && handshakeAge in 0..MAX_HANDSHAKE_AGE_SECONDS) {
                    receivedTraffic = true
                    healthy = true
                    healthDeadline = SystemClock.elapsedRealtime() + HEALTH_INTERVAL_MS + HEALTH_DEADLINE_MS
                    nextCheck = SystemClock.elapsedRealtime() + HEALTH_INTERVAL_MS
                    setStatus(opening, VpnStatus.CONNECTED)
                } else {
                    if (healthy) {
                        healthy = false
                        healthDeadline = SystemClock.elapsedRealtime() + HEALTH_DEADLINE_MS
                        setStatus(opening, VpnStatus.RECONNECTING)
                    }
                    if (SystemClock.elapsedRealtime() >= healthDeadline) {
                        val reason = if (receivedTraffic) DisconnectReason.CONNECTION_LOST
                            else DisconnectReason.CONNECTION_FAILED
                        finish(opening, reason,
                            "Couldn't verify traffic through this VPN node. Check your connection or choose another node.")
                        return
                    }
                }
            }
            delay(1_000)
        }
    }

    private fun setStatus(opening: Attempt, status: VpnStatus) {
        if (!opening.canceled && currentAttempt === opening) {
            _vpnStatusFlow.value = status
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, notification(opening, status))
            }
        }
    }

    private fun finish(ending: Attempt, reason: DisconnectReason, message: String? = null) {
        if (attempt !== ending) return
        ending.canceled = true
        ending.service = null
        tunnelJob?.cancel()
        tunnelJob = null
        attempt = null
        if (currentAttempt === ending) {
            currentAttempt = null
            _lastDisconnectReasonFlow.value = reason
            _errorFlow.value = message
            _vpnStatusFlow.value = if (message == null) VpnStatus.DISCONNECTED else VpnStatus.ERROR
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onRevoke() {
        // VpnService callbacks may run on a Binder thread.
        scope.launch { attempt?.let { finish(it, DisconnectReason.SERVER_REVOKED) } }
    }

    override fun onDestroy() {
        attempt?.let { finish(it, DisconnectReason.CONNECTION_LOST) }
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    private fun notification(opening: Attempt, status: VpnStatus): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val disconnect = PendingIntent.getService(
            this, 0, Intent(this, VeilVpnService::class.java).apply {
                action = ACTION_STOP
                data = Uri.Builder().scheme("raven-vpn").authority("disconnect")
                    .appendPath(opening.id).build()
                putExtra(EXTRA_ATTEMPT, opening.id)
            }, PendingIntent.FLAG_IMMUTABLE
        )

        val label = when (status) {
            VpnStatus.CONNECTED -> "Connected"
            VpnStatus.RECONNECTING -> "Reconnecting"
            else -> "Connecting"
        }
        return NotificationCompat.Builder(this, VeilGateApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_lock)
            .setContentTitle("Raven VPN · $label")
            .setContentText(opening.nodeName)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnect)
            .build()
    }
}
