package com.veilgate.vpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veilgate.vpn.data.model.DisconnectReason
import com.veilgate.vpn.data.model.VpnStatus
import com.veilgate.vpn.service.SessionLimits
import com.veilgate.vpn.ui.theme.BgDark
import com.veilgate.vpn.ui.theme.BorderDark
import com.veilgate.vpn.ui.theme.CardDark
import com.veilgate.vpn.ui.theme.NeonAmber
import com.veilgate.vpn.ui.theme.NeonCyan
import com.veilgate.vpn.ui.theme.NeonGreen
import com.veilgate.vpn.ui.theme.NeonRed
import com.veilgate.vpn.ui.theme.SurfaceDark
import com.veilgate.vpn.ui.theme.TextMuted
import com.veilgate.vpn.ui.theme.TextPrimary
import com.veilgate.vpn.ui.theme.TextSecondary
import com.veilgate.vpn.ui.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestVpnPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showServerPicker by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = BgDark,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                status = uiState.status,
                onSettingsClick = { showSettingsDialog = true }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Session Expiry / Quota Limit Notification Banner
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))
                LimitNoticeBanner(lastReason = uiState.lastDisconnectReason)
            }

            // 2. Central Interactive Power Button & Status Indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                CentralPowerButton(
                    status = uiState.status,
                    onClick = {
                        if (uiState.status == VpnStatus.DISCONNECTED || uiState.status == VpnStatus.ERROR) {
                            onRequestVpnPermission()
                        } else {
                            viewModel.toggleConnection()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                StatusTextLabel(status = uiState.status)
            }

            // 3. Dual Metrics: 1-Hour Timer & 1-GB Quota Meter
            DualMetricsCard(
                status = uiState.status,
                remainingSeconds = uiState.remainingSeconds,
                usedBytes = uiState.usedBytes
            )

            // 4. Server Picker Selector Card
            ServerSelectorCard(
                selectedServer = uiState.selectedServer,
                onClick = { showServerPicker = true }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showServerPicker) {
        ServerPickerSheet(
            servers = uiState.availableServers,
            selectedServer = uiState.selectedServer,
            onSelect = { viewModel.selectServer(it) },
            onDismiss = { showServerPicker = false }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentBaseUrl = viewModel.deviceManager.getBaseUrl(),
            currentAppKey = viewModel.deviceManager.getAppKey(),
            deviceId = viewModel.deviceManager.getDeviceId(),
            onSave = { url, key -> viewModel.updateSettings(url, key) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun TopAppBar(status: VpnStatus, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            VpnStatus.CONNECTED -> NeonGreen
                            VpnStatus.CONNECTING, VpnStatus.RECONNECTING -> NeonAmber
                            VpnStatus.ERROR -> NeonRed
                            else -> TextMuted
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "RAVEN VPN",
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary
            )
        }
    }
}

@Composable
fun LimitNoticeBanner(lastReason: DisconnectReason?) {
    AnimatedVisibility(
        visible = lastReason == DisconnectReason.TIME_LIMIT_EXCEEDED ||
                lastReason == DisconnectReason.QUOTA_LIMIT_EXCEEDED ||
                lastReason == DisconnectReason.CONNECTION_LOST ||
                lastReason == DisconnectReason.CONNECTION_FAILED,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val (title, description, bannerColor) = when (lastReason) {
            DisconnectReason.TIME_LIMIT_EXCEEDED -> Triple(
                "1-Hour Time Limit Reached",
                "Your 60-minute session has ended. Tap Connect to start a fresh 1-hour session.",
                NeonAmber
            )
            DisconnectReason.QUOTA_LIMIT_EXCEEDED -> Triple(
                "1-GB Data Quota Reached",
                "You reached your 1,024 MB data allocation. Tap Connect to renew.",
                NeonAmber
            )
            DisconnectReason.CONNECTION_LOST -> Triple(
                "Connection Lost",
                "Internet access was lost or server stopped responding. Connection disconnected automatically.",
                NeonRed
            )
            DisconnectReason.CONNECTION_FAILED -> Triple(
                "Connection Failed",
                "Could not establish handshake with server. Please try another node.",
                NeonRed
            )
            else -> Triple("", "", NeonAmber)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bannerColor.copy(alpha = 0.12f))
                .border(1.dp, bannerColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = bannerColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = bannerColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun CentralPowerButton(
    status: VpnStatus,
    onClick: () -> Unit
) {
    val isConnected = status == VpnStatus.CONNECTED
    val isConnecting = status == VpnStatus.CONNECTING || status == VpnStatus.RECONNECTING

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnected) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowColor = when (status) {
        VpnStatus.CONNECTED -> NeonGreen
        VpnStatus.CONNECTING, VpnStatus.RECONNECTING -> NeonAmber
        VpnStatus.ERROR -> NeonRed
        else -> NeonCyan.copy(alpha = 0.4f)
    }

    Box(
        modifier = Modifier
            .size(190.dp)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing ring
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )

        // Middle border ring
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .border(2.dp, glowColor.copy(alpha = 0.8f), CircleShape)
                .background(CardDark),
            contentAlignment = Alignment.Center
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(140.dp),
                    color = NeonAmber,
                    strokeWidth = 3.dp
                )
            }

            // Inner button
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) NeonGreen.copy(alpha = 0.15f) else SurfaceDark
                    )
                    .clickable(enabled = !isConnecting, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Toggle VPN",
                    tint = glowColor,
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
fun StatusTextLabel(status: VpnStatus) {
    val (label, color) = when (status) {
        VpnStatus.CONNECTED -> "CONNECTED · SECURE" to NeonGreen
        VpnStatus.CONNECTING -> "VERIFYING TRAFFIC..." to NeonAmber
        VpnStatus.RECONNECTING -> "RECONNECTING · RESTORING..." to NeonAmber
        VpnStatus.DISCONNECTING -> "DISCONNECTING..." to NeonAmber
        VpnStatus.ERROR -> "CONNECTION FAILED" to NeonRed
        VpnStatus.DISCONNECTED -> "DISCONNECTED" to TextSecondary
    }

    Text(
        text = label,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    )
}

@Composable
fun DualMetricsCard(
    status: VpnStatus,
    remainingSeconds: Long,
    usedBytes: Long
) {
    val isConnected = status == VpnStatus.CONNECTED || status == VpnStatus.RECONNECTING
    val timeProgress = (remainingSeconds.toFloat() / SessionLimits.DEFAULT_TIME_LIMIT_SECONDS).coerceIn(0f, 1f)
    val quotaProgress = (usedBytes.toFloat() / SessionLimits.DEFAULT_QUOTA_LIMIT_BYTES).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardDark)
            .border(1.dp, BorderDark, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        // Meter 1: 1-Hour Time Limit
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HourglassBottom,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TIME LIMIT",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (isConnected) "${SessionLimits.formatSeconds(remainingSeconds)} / 60:00" else "60:00 limit",
                color = if (isConnected) NeonCyan else TextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { if (isConnected) timeProgress else 1f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = NeonCyan,
            trackColor = BorderDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Meter 2: 1-GB Data Limit
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DATA USAGE",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = if (isConnected) "${SessionLimits.formatBytes(usedBytes)} / 1.00 GB" else "1.00 GB limit",
                color = if (isConnected) NeonGreen else TextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { if (isConnected) quotaProgress else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = NeonGreen,
            trackColor = BorderDark
        )
    }
}

@Composable
fun ServerSelectorCard(
    selectedServer: com.veilgate.vpn.data.model.ServerNode?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(BorderDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = selectedServer?.name ?: "Auto Select Node",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = selectedServer?.location?.ifEmpty { "Fastest Server" } ?: "Tap to choose server",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Select",
            tint = TextSecondary
        )
    }
}
