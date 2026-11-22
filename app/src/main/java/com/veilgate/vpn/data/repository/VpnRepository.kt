package com.veilgate.vpn.data.repository

import android.content.Context
import com.veilgate.vpn.data.network.AccessTokenExpired
import com.veilgate.vpn.data.network.MobileCredentials
import com.veilgate.vpn.data.network.NetworkClient
import com.veilgate.vpn.data.network.RefreshTokenRejected
import com.veilgate.vpn.data.network.VeilGateApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

class VpnRepository private constructor(context: Context, private val api: VeilGateApi?) {
    val deviceManager = DeviceManager(context)
    private val vault = api?.let { CredentialVault(context, it.origin) }
    private val authLock = Mutex()
    private val leaseLock = Mutex()
    private var credentials: MobileCredentials? = null
    private val activeSessions = mutableSetOf<String>()
    private val leaseJournal = context.getSharedPreferences("raven_lease_journal", Context.MODE_PRIVATE)
    private val leaseKey = api?.origin?.let { "${it.scheme}://${it.host}:${it.port}" }

    companion object {
        @Volatile
        private var instance: VpnRepository? = null

        fun get(context: Context): VpnRepository = instance ?: synchronized(this) {
            instance ?: VpnRepository(context.applicationContext, NetworkClient.mobileApi())
                .also { instance = it }
        }
    }

    suspend fun fetchServers() = operation { client ->
        authenticated(client) { client.getServers(it) }.also { result ->
            check(result.ok && result.servers != null &&
                result.servers.all { it.id.isNotBlank() && it.name.isNotBlank() } &&
                result.servers.distinctBy { it.id }.size == result.servers.size) {
                "Raven VPN received an invalid node list. Refresh to try again."
            }
        }
    }

    suspend fun requestTunnel(nodeId: String, attemptId: String) = operation { client ->
        val lease = authenticated(client) { client.connect(it, nodeId, attemptId) }
        check(lease.sessionId.isNotBlank()) { "The VPN service returned an invalid session ID." }
        val recorded = leaseLock.withLock {
            val sessions = savedSessions() + lease.sessionId
            writeSessions(sessions).also { if (it) activeSessions += lease.sessionId }
        }
        if (!recorded) {
            withContext(NonCancellable) {
                runCatching { authenticated(client) { client.disconnect(it, lease.sessionId) } }
            }
            error("Raven VPN couldn't save the session safely. Check available storage and retry.")
        }
        lease
    }

    suspend fun releaseLease(sessionId: String) = withContext(Dispatchers.IO) {
        leaseLock.withLock { activeSessions -= sessionId }
        repeat(3) { attempt ->
            if (attempt > 0) delay(attempt * 2_000L)
            if (releaseIfInactive(sessionId)) return@withContext
        }
    }

    suspend fun recoverLeases() = withContext(Dispatchers.IO) {
        if (leaseKey != null && deviceManager.isVpnDisclosureAccepted()) {
            val orphans = leaseLock.withLock { savedSessions() - activeSessions }
            for (session in orphans) releaseIfInactive(session)
        }
    }

    private suspend fun releaseIfInactive(sessionId: String): Boolean = leaseLock.withLock {
        if (sessionId in activeSessions || sessionId !in savedSessions()) return@withLock true
        val result = operation { client ->
            authenticated(client) { client.disconnect(it, sessionId) }
        }
        result.isSuccess && writeSessions(savedSessions() - sessionId)
    }

    private fun savedSessions(): Set<String> =
        leaseJournal.getStringSet(checkNotNull(leaseKey), emptySet()).orEmpty().toSet()

    private fun writeSessions(sessions: Set<String>): Boolean =
        leaseJournal.edit().putStringSet(checkNotNull(leaseKey), sessions).commit()

    private suspend fun <T> operation(block: suspend (VeilGateApi) -> T): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                check(deviceManager.isVpnDisclosureAccepted()) {
                    "Review the VPN disclosure before setting up access."
                }
                deviceManager.migrateLegacyPreferences()
                val client = api ?: error(
                    "Raven VPN mobile access is unavailable. Try Refresh later or contact support."
                )
                Result.success(block(client))
            } catch (error: CancellationException) {
                throw error
            } catch (_: AccessTokenExpired) {
                Result.failure(IllegalStateException("Raven VPN couldn't verify mobile access. Try again."))
            } catch (_: IOException) {
                Result.failure(IllegalStateException("Couldn't reach Raven VPN. Check your internet connection and retry."))
            } catch (error: CredentialStorageException) {
                Result.failure(error)
            } catch (error: IllegalStateException) {
                Result.failure(error)
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Raven VPN couldn't complete the request. Try again."))
            }
        }

    private suspend fun <T> authenticated(client: VeilGateApi, request: suspend (String) -> T): T {
        val used = authLock.withLock {
            credentials ?: vault!!.read()?.also { credentials = it } ?: enroll(client)
        }

        return try {
            request(used.accessToken)
        } catch (_: AccessTokenExpired) {
            val replacement = authLock.withLock {
                credentials?.takeIf { it !== used } ?: withContext(NonCancellable) {
                    // Rotation and durable storage finish together, even if a screen is dismissed.
                    val rotated = try {
                        client.refresh(used.refreshToken)
                    } catch (_: RefreshTokenRejected) {
                        return@withContext enroll(client)
                    }
                    persist(rotated)
                }
            }
            request(replacement.accessToken)
        }
    }

    private suspend fun enroll(client: VeilGateApi): MobileCredentials = withContext(NonCancellable) {
        persist(client.enroll(
            deviceManager.getOrCreateInstallationId(),
            DeviceManager.CURRENT_DISCLOSURE_VERSION
        ))
    }

    private fun persist(tokens: MobileCredentials): MobileCredentials {
        try {
            vault!!.write(tokens)
        } catch (error: CredentialStorageException) {
            credentials = null
            throw error
        }
        credentials = tokens
        return tokens
    }
}
