package com.veilgate.vpn.data.network

import com.veilgate.vpn.data.model.ConnectRequest
import com.veilgate.vpn.data.model.ConnectResponse
import com.veilgate.vpn.data.model.DisconnectRequest
import com.veilgate.vpn.data.model.ServerListResponse
import com.veilgate.vpn.data.model.SessionData
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface VeilGateApi {

    @GET("api/app/servers")
    suspend fun getServers(): Response<ServerListResponse>

    @POST("api/app/connect")
    suspend fun connect(
        @Body request: ConnectRequest
    ): Response<ConnectResponse>

    @GET("api/app/session")
    suspend fun getSession(
        @Query("deviceId") deviceId: String,
        @Query("nodeId") nodeId: String?
    ): Response<SessionData>

    @POST("api/app/disconnect")
    suspend fun disconnect(
        @Body request: DisconnectRequest
    ): Response<Map<String, Any>>
}
