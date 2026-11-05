package com.veilgate.vpn.data.repository

import android.content.Context
import com.veilgate.vpn.data.model.ConnectRequest
import com.veilgate.vpn.data.model.ConnectResponse
import com.veilgate.vpn.data.model.DisconnectRequest
import com.veilgate.vpn.data.model.ServerListResponse
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.data.network.NetworkClient
import com.veilgate.vpn.data.network.VeilGateApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VpnRepository(private val context: Context) {

    val deviceManager = DeviceManager(context)

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
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to load servers: HTTP ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
