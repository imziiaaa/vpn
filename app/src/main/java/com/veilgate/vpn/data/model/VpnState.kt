package com.veilgate.vpn.data.model

enum class VpnStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

enum class DisconnectReason {
    USER_MANUAL,
    TIME_LIMIT_EXCEEDED,   // Reached 1 hour limit
    QUOTA_LIMIT_EXCEEDED,  // Reached 1 GB limit
    SERVER_REVOKED,
    CONNECTION_FAILED
}

data class VpnUiState(
    val status: VpnStatus = VpnStatus.DISCONNECTED,
    val selectedServer: ServerNode? = null,
    val availableServers: List<ServerNode> = emptyList(),
    val isLoadingServers: Boolean = false,
    val remainingSeconds: Long = 3600L,
    val totalTimeSeconds: Long = 3600L,
    val usedBytes: Long = 0L,
    val quotaLimitBytes: Long = 1073741824L, // 1 GB
    val lastDisconnectReason: DisconnectReason? = null,
    val errorMessage: String? = null
)
