package com.veilgate.vpn.data.model

import com.google.gson.annotations.SerializedName

data class ServerNode(
    val id: String,
    val name: String,
    val location: String,
    val endpointHost: String,
    val endpointPort: Int,
    val activePeers: Int = 0,
    val capacityPeers: Int = 250,
    val loadPercent: Int = 0,
    val status: String = "online"
)

data class PolicyData(
    val sessionDurationMinutes: Int = 60,
    val sessionQuotaMb: Int = 1024,
    val sessionQuotaBytes: Long = 1073741824L
)

data class ServerListResponse(
    val ok: Boolean,
    val policy: PolicyData?,
    val servers: List<ServerNode>?
)

data class ConnectRequest(
    val deviceId: String,
    val nodeId: String?,
    val deviceName: String = "Android Client"
)

data class SessionData(
    val peerId: String,
    val deviceId: String,
    val nodeId: String,
    val nodeName: String,
    val location: String,
    val allocatedIp: String,
    val status: String,
    val expiresAt: String,
    val remainingSeconds: Long,
    val dataLimitBytes: Long,
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val usedBytes: Long = 0,
    val remainingBytes: Long = 0
)

data class AwgInterfaceConfig(
    val privateKey: String,
    val address: String,
    val dns: String?,
    val mtu: Int = 1280,
    val jc: Int = 4,
    val jmin: Int = 40,
    val jmax: Int = 70,
    val s1: Int = 0,
    val s2: Int = 0,
    val h1: Int = 0,
    val h2: Int = 0,
    val h3: Int = 0,
    val h4: Int = 0
)

data class AwgPeerConfig(
    val publicKey: String,
    val presharedKey: String?,
    val endpoint: String,
    val allowedIps: String = "0.0.0.0/0, ::/0",
    val persistentKeepalive: Int = 25
)

data class AwgConfigPayload(
    @SerializedName("interface")
    val interfaceConfig: AwgInterfaceConfig?,
    val peer: AwgPeerConfig?
)

data class ConnectResponse(
    val ok: Boolean,
    val session: SessionData?,
    val awgConfig: AwgConfigPayload?,
    val confContent: String?,
    val error: String?
)

data class DisconnectRequest(
    val deviceId: String,
    val nodeId: String?
)
