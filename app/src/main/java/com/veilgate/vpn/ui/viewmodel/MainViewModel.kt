package com.veilgate.vpn.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.data.model.VpnUiState
import com.veilgate.vpn.data.repository.VpnRepository
import com.veilgate.vpn.service.VeilVpnService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VpnRepository.get(application)
    private val deviceManager = repository.deviceManager
    private val _uiState = MutableStateFlow(VpnUiState(hasConsent = deviceManager.isVpnDisclosureAccepted()))
    val uiState = _uiState.asStateFlow()
    private var serverRefreshJob: Job? = null

    init {
        viewModelScope.launch {
            VeilVpnService.vpnStatusFlow.collect { status -> _uiState.update { it.copy(status = status) } }
        }
        viewModelScope.launch {
            VeilVpnService.remainingSecondsFlow.collect { seconds ->
                _uiState.update { it.copy(remainingSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            VeilVpnService.usedBytesFlow.collect { bytes -> _uiState.update { it.copy(usedBytes = bytes) } }
        }
        viewModelScope.launch {
            VeilVpnService.lastDisconnectReasonFlow.collect { reason ->
                _uiState.update { it.copy(lastDisconnectReason = reason) }
            }
        }
        viewModelScope.launch {
            VeilVpnService.errorFlow.collect { message -> if (message != null) showError(message) }
        }
        refreshServers()
    }

    fun refreshServers() {
        if (!_uiState.value.hasConsent || serverRefreshJob?.isActive == true) return
        viewModelScope.launch { repository.recoverLeases() }
        serverRefreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true, serverListError = null) }
            repository.fetchServers().onSuccess { response ->
                val servers = response.servers.orEmpty()
                val lastId = deviceManager.getLastServerId()
                _uiState.update { state ->
                    state.copy(
                        availableServers = servers,
                        selectedServer = servers.firstOrNull { it.id == lastId },
                        isLoadingServers = false
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        availableServers = emptyList(),
                        selectedServer = null,
                        isLoadingServers = false,
                        serverListError = error.message ?: "Could not load servers. Tap Refresh to retry."
                    )
                }
            }
        }
    }

    fun selectServer(server: ServerNode) {
        deviceManager.setLastServerId(server.id)
        _uiState.update { it.copy(selectedServer = server) }
    }

    fun connect(nodeId: String, nodeName: String) {
        if (!_uiState.value.hasConsent) return
        VeilVpnService.startService(getApplication(), nodeId, nodeName)
    }

    fun disconnect() = VeilVpnService.stopService()

    fun showError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun acceptDisclosure(): Boolean {
        if (!deviceManager.acceptVpnDisclosure()) {
            showError("Raven VPN couldn't save your choice. Check available storage and retry.")
            return false
        }
        _uiState.update { it.copy(hasConsent = true) }
        refreshServers()
        return true
    }
}
