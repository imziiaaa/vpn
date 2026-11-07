package com.veilgate.vpn.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veilgate.vpn.data.model.DisconnectReason
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.data.model.VpnStatus
import com.veilgate.vpn.data.model.VpnUiState
import com.veilgate.vpn.data.repository.VpnRepository
import com.veilgate.vpn.service.SessionLimits
import com.veilgate.vpn.service.VeilVpnService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VpnRepository(application)
    val deviceManager = repository.deviceManager

    private val _uiState = MutableStateFlow(VpnUiState())
    val uiState: StateFlow<VpnUiState> = _uiState.asStateFlow()

    init {
        // Observe status and limits emitted by VeilVpnService
        viewModelScope.launch {
            VeilVpnService.vpnStatusFlow.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            VeilVpnService.remainingSecondsFlow.collect { seconds ->
                _uiState.update { it.copy(remainingSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            VeilVpnService.usedBytesFlow.collect { bytes ->
                _uiState.update { it.copy(usedBytes = bytes) }
            }
        }
        viewModelScope.launch {
            VeilVpnService.lastDisconnectReasonFlow.collect { reason ->
                _uiState.update { it.copy(lastDisconnectReason = reason) }
            }
        }

        refreshServers()
    }

    fun refreshServers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true, errorMessage = null) }
            val result = repository.fetchServers()
            result.onSuccess { response ->
                val servers = response.servers ?: emptyList()
                val lastId = deviceManager.getLastServerId()
                val selected = servers.firstOrNull { it.id == lastId }
                    ?: servers.minByOrNull { it.loadPercent }
                    ?: servers.firstOrNull()

                _uiState.update {
                    it.copy(
                        availableServers = servers,
                        selectedServer = selected,
                        isLoadingServers = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingServers = false,
                        errorMessage = "Could not load servers: ${err.message}"
                    )
                }
            }
        }
    }

    fun selectServer(server: ServerNode) {
        deviceManager.setLastServerId(server.id)
        _uiState.update { it.copy(selectedServer = server) }
    }

    fun toggleConnection() {
        val currentStatus = _uiState.value.status
        if (currentStatus == VpnStatus.CONNECTED || currentStatus == VpnStatus.CONNECTING || currentStatus == VpnStatus.RECONNECTING) {
            disconnect(DisconnectReason.USER_MANUAL)
        } else {
            connect()
        }
    }

    private fun connect() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    status = VpnStatus.CONNECTING,
                    errorMessage = null,
                    lastDisconnectReason = null
                )
            }

            val targetServer = _uiState.value.selectedServer
            val result = repository.requestTunnel(targetServer)

            result.onSuccess { connectResp ->
                if (!connectResp.ok || connectResp.session == null) {
                    _uiState.update {
                        it.copy(
                            status = VpnStatus.ERROR,
                            errorMessage = connectResp.error ?: "Connection failed to provision on server."
                        )
                    }
                    return@onSuccess
                }

                val session = connectResp.session
                val awg = connectResp.awgConfig
                val iface = awg?.interfaceConfig ?: com.veilgate.vpn.data.model.AwgInterfaceConfig(
                    privateKey = "",
                    address = session.allocatedIp,
                    dns = "1.1.1.1, 8.8.8.8"
                )
                val peer = awg?.peer ?: com.veilgate.vpn.data.model.AwgPeerConfig(
                    publicKey = "",
                    presharedKey = null,
                    endpoint = "${targetServer?.endpointHost}:${targetServer?.endpointPort}"
                )

                // Launch the foreground VPN service with AmneziaWG engine & limits active
                VeilVpnService.startService(
                    context = getApplication(),
                    serverName = session.nodeName.ifEmpty { targetServer?.name ?: "VeilGate Node" },
                    location = session.location.ifEmpty { targetServer?.location ?: "Auto" },
                    iface = iface,
                    peer = peer
                )
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        status = VpnStatus.ERROR,
                        errorMessage = err.message ?: "Failed to negotiate VPN session."
                    )
                }
            }
        }
    }

    fun disconnect(reason: DisconnectReason = DisconnectReason.USER_MANUAL) {
        viewModelScope.launch {
            val serverId = _uiState.value.selectedServer?.id
            VeilVpnService.stopService(getApplication(), reason)
            repository.notifyDisconnect(serverId)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateSettings(baseUrl: String, appKey: String?) {
        deviceManager.setBaseUrl(baseUrl)
        deviceManager.setAppKey(appKey)
        refreshServers()
    }
}
