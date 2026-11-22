package com.veilgate.vpn.data.network

import com.veilgate.vpn.data.model.ServerListResponse
import com.veilgate.vpn.data.model.TunnelLease
import okhttp3.HttpUrl

internal class MobileCredentials(val accessToken: String, val refreshToken: String) {
    init {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank())
    }
}

/** Client operations. The wire adapter maps the deployed mobile contract to these types. */
internal interface VeilGateApi {
    val origin: HttpUrl

    suspend fun enroll(installationId: String, disclosureVersion: Int): MobileCredentials
    suspend fun refresh(refreshToken: String): MobileCredentials
    suspend fun getServers(accessToken: String): ServerListResponse
    suspend fun connect(accessToken: String, nodeId: String, attemptId: String): TunnelLease
    suspend fun disconnect(accessToken: String, sessionId: String)
}

internal class AccessTokenExpired : Exception()
internal class RefreshTokenRejected : Exception()
