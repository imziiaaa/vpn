package com.veilgate.vpn.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.veilgate.vpn.data.model.ConnectRequest
import com.veilgate.vpn.data.model.ConnectResponse
import com.veilgate.vpn.data.model.DisconnectRequest
import com.veilgate.vpn.data.model.PolicyData
import com.veilgate.vpn.data.model.ServerListResponse
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.data.network.NetworkClient
import com.veilgate.vpn.data.network.VeilGateApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VpnRepository(private val context: Context) {

    private val gson = Gson()
    val deviceManager = DeviceManager(context)

    companion object {
        private const val TAG = "VpnRepository"

        val DEFAULT_FALLBACK_SERVERS = listOf(
            ServerNode(
                id = "node-0131abc1",
                name = "🇺🇸 FRA-01",
                location = "New York, USA",
                endpointHost = "169.197.141.248",
                endpointPort = 7088,
                activePeers = 2,
                capacityPeers = 250,
                loadPercent = 1,
                status = "online"
            ),
            ServerNode(
                id = "node-3ec6dd15",
                name = "🇯🇵 TYO-02",
                location = "Tokyo, JP",
                endpointHost = "103.173.178.124",
                endpointPort = 6743,
                activePeers = 1,
                capacityPeers = 250,
                loadPercent = 0,
                status = "online"
            ),
            ServerNode(
                id = "node-c2a327ac",
                name = "🇵🇱 WAW-03",
                location = "Warsaw, PL",
                endpointHost = "51.77.53.215",
                endpointPort = 6284,
                activePeers = 0,
                capacityPeers = 250,
                loadPercent = 0,
                status = "online"
            )
        )
    }

    private fun getApi(): VeilGateApi {
        return NetworkClient.createApi(
            context = context,
            baseUrl = deviceManager.getBaseUrl(),
            appKey = deviceManager.getAppKey()
        )
    }

    suspend fun fetchServers(): Result<ServerListResponse> = withContext(Dispatchers.IO) {
        try {
            val response = getApi().getServers()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Cache server list locally for offline resilience
                try {
                    deviceManager.setCachedServersJson(gson.toJson(body))
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cache servers: ${e.message}")
                }
                Result.success(body)
            } else {
                Log.w(TAG, "Server returned HTTP ${response.code()}: ${response.message()}. Loading cached fallback...")
                Result.success(getCachedOrFallbackResponse())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network exception fetching servers: ${e.message}. Loading cached fallback...")
            val fallback = getCachedOrFallbackResponse()
            Result.success(fallback)
        }
    }

    private fun getCachedOrFallbackResponse(): ServerListResponse {
        val cachedJson = deviceManager.getCachedServersJson()
        if (!cachedJson.isNullOrBlank()) {
            try {
                val cached = gson.fromJson(cachedJson, ServerListResponse::class.java)
                if (cached != null && !cached.servers.isNullOrEmpty()) {
                    return cached
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing cached servers", e)
            }
        }

        return ServerListResponse(
            ok = true,
            policy = PolicyData(sessionDurationMinutes = 60, sessionQuotaMb = 1024, sessionQuotaBytes = 1073741824L),
            servers = DEFAULT_FALLBACK_SERVERS
        )
    }

    suspend fun requestTunnel(selectedServer: ServerNode?): Result<ConnectResponse> = withContext(Dispatchers.IO) {
        try {
            val deviceId = deviceManager.getDeviceId()
            val request = ConnectRequest(
                deviceId = deviceId,
                nodeId = selectedServer?.id,
                deviceName = "Android ${android.os.Build.MODEL ?: "Phone"}"
            )
            val response = getApi().connect(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: ""
                Result.failure(Exception("Connect failed (${response.code()}): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun notifyDisconnect(nodeId: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val deviceId = deviceManager.getDeviceId()
            getApi().disconnect(DisconnectRequest(deviceId = deviceId, nodeId = nodeId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
