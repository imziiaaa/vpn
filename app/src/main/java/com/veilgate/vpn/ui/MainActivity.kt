package com.veilgate.vpn.ui

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.veilgate.vpn.ui.screens.MainScreen
import com.veilgate.vpn.ui.theme.VeilGateTheme
import com.veilgate.vpn.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Activity result launcher for VpnService system permission dialog
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.toggleConnection()
        } else {
            Toast.makeText(this, "VPN permission is required to create tunnel.", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launcher for POST_NOTIFICATIONS on Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission granted/denied
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            VeilGateTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestVpnPermission = { prepareVpnAndConnect() }
                )
            }
        }
    }

    private fun prepareVpnAndConnect() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            // Already prepared, proceed directly to connection
            viewModel.toggleConnection()
        }
    }
}
