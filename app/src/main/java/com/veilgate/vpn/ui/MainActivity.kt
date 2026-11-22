package com.veilgate.vpn.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.ui.screens.MainScreen
import com.veilgate.vpn.ui.screens.VpnDisclosureDialog
import com.veilgate.vpn.ui.theme.VeilGateTheme
import com.veilgate.vpn.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var pendingNodeId: String? = null
    private var pendingNodeName: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (pendingNodeId != null) {
            if (result.resultCode == RESULT_OK) {
                lifecycleScope.launch { lifecycle.withResumed { requestNotificationsOrConnect() } }
            } else {
                clearPendingTarget()
                viewModel.showError("VPN permission was declined. Tap Protect to request it again.")
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        lifecycleScope.launch { lifecycle.withResumed { connectToPendingNode() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNodeId = savedInstanceState?.getString("pending_node_id")
        pendingNodeName = savedInstanceState?.getString("pending_node_name")

        setContent {
            VeilGateTheme {
                var showDisclosure by rememberSaveable { mutableStateOf(false) }
                var showServerPicker by rememberSaveable { mutableStateOf(false) }

                MainScreen(
                    viewModel = viewModel,
                    showServerPicker = showServerPicker,
                    onShowServerPicker = { showServerPicker = it },
                    onReviewDisclosure = { showDisclosure = true },
                    onRequestVpnPermission = ::prepareVpnAndConnect
                )

                if (showDisclosure) {
                    VpnDisclosureDialog(
                        onAccept = {
                            showDisclosure = false
                            if (viewModel.acceptDisclosure()) showServerPicker = true
                        },
                        onDismiss = { showDisclosure = false }
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_node_id", pendingNodeId)
        outState.putString("pending_node_name", pendingNodeName)
        super.onSaveInstanceState(outState)
    }

    private fun prepareVpnAndConnect(server: ServerNode) {
        if (!viewModel.uiState.value.hasConsent || pendingNodeId != null) return

        pendingNodeId = server.id
        pendingNodeName = server.name
        try {
            val intent = VpnService.prepare(this)
            if (intent != null) vpnPermissionLauncher.launch(intent)
            else requestNotificationsOrConnect()
        } catch (_: RuntimeException) {
            clearPendingTarget()
            viewModel.showError("Android couldn't open VPN permissions. Check VPN settings and try again.")
        }
    }

    private fun requestNotificationsOrConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !getPreferences(MODE_PRIVATE).getBoolean("requested_vpn_notifications", false)
        ) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("requested_vpn_notifications", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            connectToPendingNode()
        }
    }

    private fun connectToPendingNode() {
        val nodeId = pendingNodeId ?: return
        val nodeName = pendingNodeName ?: "VPN node"
        clearPendingTarget()
        viewModel.connect(nodeId, nodeName)
    }

    private fun clearPendingTarget() {
        pendingNodeId = null
        pendingNodeName = null
    }
}
