package com.veilgate.vpn.ui.screens

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.data.model.VpnStatus
import com.veilgate.vpn.ui.theme.CadBg
import com.veilgate.vpn.ui.theme.CadBorder
import com.veilgate.vpn.ui.theme.CadBorderBright
import com.veilgate.vpn.ui.theme.CadRed
import com.veilgate.vpn.ui.theme.CadRedBg
import com.veilgate.vpn.ui.theme.CadRedDim
import com.veilgate.vpn.ui.theme.CadRedGlow
import com.veilgate.vpn.ui.theme.CadSurface
import com.veilgate.vpn.ui.theme.CadTextDim
import com.veilgate.vpn.ui.theme.CadTextMuted
import com.veilgate.vpn.ui.theme.CadTextWhite
import com.veilgate.vpn.ui.viewmodel.MainViewModel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    showServerPicker: Boolean,
    onShowServerPicker: (Boolean) -> Unit,
    onReviewDisclosure: () -> Unit,
    onRequestVpnPermission: (ServerNode) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val isConnected = (uiState.status == VpnStatus.CONNECTED)
    val isConnecting = (uiState.status == VpnStatus.CONNECTING || uiState.status == VpnStatus.RECONNECTING || uiState.status == VpnStatus.DISCONNECTING)

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = CadBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CadBg)
        ) {
            // 1. Subtle CAD Orthographic Grid & Corner Crosshairs (+)
            CadBackgroundGrid()

            // Main Content Layer
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // SECTION 1: Top CAD Header Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    CadTopHeader(
                        status = uiState.status,
                        isConnected = isConnected,
                        isConnecting = isConnecting
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Upper Telemetry Line (above sphere)
                    CadSubheaderTelemetry(isConnected = isConnected)
                }

                // SECTION 2: 3D Wireframe Globe with Radar & Target Lock
                val locationParts = uiState.selectedServer?.location.orEmpty().split(",")
                val serverCity = locationParts.first().trim()
                val serverCountry = locationParts.getOrNull(1)?.trim().orEmpty()
                val serverCode = uiState.selectedServer?.name.orEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .height(290.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CadGlobeRadarVisual(
                        isConnected = isConnected,
                        serverCity = serverCity,
                        serverCountry = serverCountry,
                        serverCode = serverCode
                    )
                }

                // SECTION 3: Lower Sub-Globe Telemetry + Node Selector Pill
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    CadBelowGlobeTelemetry(isConnected = isConnected)

                    Spacer(modifier = Modifier.height(14.dp))

                    // Node Selector Button
                    CadNodeSelectorPill(
                        selectedServerCity = uiState.selectedServer?.let { serverCity },
                        isConnected = isConnected,
                        onClick = {
                            if (!uiState.hasConsent) onReviewDisclosure()
                            else if (!isConnected && !isConnecting) onShowServerPicker(true)
                        }
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Big Headline & Sub-status
                    CadHeadlineStatus(
                        status = uiState.status,
                        isConnected = isConnected,
                        isConnecting = isConnecting
                    )
                }

                // SECTION 4: Bottom Controls (Clean Tap Button)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CadTapActionControl(
                        status = uiState.status,
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        protectLabel = when {
                            !uiState.hasConsent -> "REVIEW VPN DISCLOSURE"
                            uiState.selectedServer == null -> "CHOOSE A NODE"
                            else -> "PROTECT"
                        },
                        onTrigger = {
                            val target = uiState.selectedServer
                            when {
                                isConnected || isConnecting -> viewModel.disconnect()
                                !uiState.hasConsent -> onReviewDisclosure()
                                target == null || uiState.isLoadingServers -> onShowServerPicker(true)
                                else -> onRequestVpnPermission(target)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom Telemetry Footer
                    CadBottomFooter()
                }
            }
        }
    }

    // Server Selection Bottom Sheet
    if (showServerPicker) {
        ServerPickerSheet(
            servers = uiState.availableServers,
            selectedServer = uiState.selectedServer,
            isLoading = uiState.isLoadingServers,
            errorMessage = uiState.serverListError,
            onRefresh = viewModel::refreshServers,
            onSelect = viewModel::selectServer,
            onDismiss = { onShowServerPicker(false) }
        )
    }
}

/**
 * Background drawing CAD coordinate gridlines and corner crosshair markers (+)
 */
@Composable
private fun CadBackgroundGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val crossSize = 10.dp.toPx()
        val crossStroke = 1.2.dp.toPx()
        val cornerPad = 18.dp.toPx()
        val crossColor = CadTextMuted.copy(alpha = 0.5f)

        // Four Corner Crosses (+)
        val corners = listOf(
            Offset(cornerPad, cornerPad),                 // Top-Left
            Offset(w - cornerPad, cornerPad),             // Top-Right
            Offset(cornerPad, h - cornerPad),             // Bottom-Left
            Offset(w - cornerPad, h - cornerPad)          // Bottom-Right
        )

        corners.forEach { c ->
            drawLine(
                color = crossColor,
                start = Offset(c.x - crossSize / 2, c.y),
                end = Offset(c.x + crossSize / 2, c.y),
                strokeWidth = crossStroke
            )
            drawLine(
                color = crossColor,
                start = Offset(c.x, c.y - crossSize / 2),
                end = Offset(c.x, c.y + crossSize / 2),
                strokeWidth = crossStroke
            )
        }

        // Faint orthographic background grid
        val gridStep = 44.dp.toPx()
        val gridColor = Color(0x0CFFFFFF)
        var x = gridStep
        while (x < w) {
            drawLine(
                color = gridColor,
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 0.8f
            )
            x += gridStep
        }
        var y = gridStep
        while (y < h) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 0.8f
            )
            y += gridStep
        }
    }
}

/**
 * Top CAD Header Bar:
 * CAD//VPN [01]
 * REF: 50.1109° N | SYS.UNLOCKED / SYS.LOCKED +
 * Pill Badge: ⭘ UNSECURED // 0%  or  ● LOCKED // 100%
 */
@Composable
private fun CadTopHeader(
    status: VpnStatus,
    isConnected: Boolean,
    isConnecting: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Brand & Reference Coordinates
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(CadRed, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CAD//VPN ",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = CadTextWhite
                )
                Text(
                    text = "[01]",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isConnected) CadRed else CadTextWhite
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            val refText = when {
                isConnected -> "REF: 50.1109° N | SYS.LOCKED +"
                status == VpnStatus.DISCONNECTING -> "REF: 50.1109° N | SYS.DISENGAGING..."
                isConnecting -> "REF: 50.1109° N | SYS.ENGAGING..."
                else -> "REF: 50.1109° N | SYS.UNLOCKED"
            }
            Text(
                text = refText,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                color = CadTextDim
            )
        }

        // Right: Security Status Pill Badge
        val badgeBorderColor = if (isConnected) CadRed else CadRedDim
        val badgeBgColor = if (isConnected) CadRedBg.copy(alpha = 0.7f) else CadSurface.copy(alpha = 0.6f)

        Box(
            modifier = Modifier
                .border(1.2.dp, badgeBorderColor, RoundedCornerShape(16.dp))
                .background(badgeBgColor, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            val statusString = when {
                isConnected -> "● ENCRYPTED"
                status == VpnStatus.DISCONNECTING -> "○ DISCONNECTING"
                isConnecting -> "◌ CONNECTING"
                else -> "○ DISCONNECTED"
            }
            Text(
                text = statusString,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = CadRed
            )
        }
    }
}

/**
 * Sub-header Telemetry Line (above sphere)
 */
@Composable
private fun CadSubheaderTelemetry(isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        if (isConnected) {
            Column {
                Text(
                    text = "GRID // 3D-ORTHO",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CadTextDim
                )
                Text(
                    text = "REF.SCALE 1:1000",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    color = CadTextMuted
                )
            }
            Text(
                text = "LAT 50°06'49\" N ELEV +182M",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
        } else {
            Text(
                text = "GRID // INACTIVE",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
            Text(
                text = "LAT 50°06'49\" N",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
        }
    }
}

/**
 * 3D Wireframe Globe with Radar, Target Reticle, and Floating HUD Callout Box.
 * When CONNECTED, showcases the glowing illuminated red orbital ring!
 */
@Composable
private fun CadGlobeRadarVisual(
    isConnected: Boolean,
    serverCity: String,
    serverCountry: String,
    serverCode: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "GlobeRotation")
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotAngle"
    )

    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.height.coerceAtMost(size.width) / 2f) * 0.78f

            val wireframeColor = if (isConnected) Color(0xFF2E3748) else Color(0xFF1E2330)
            val strokeWidth = 1.dp.toPx()

            // 1. Outer Sphere Circle
            drawCircle(
                color = if (isConnected) Color(0xFF384358) else Color(0xFF222836),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(strokeWidth)
            )

            // 2. Horizontal Latitude Ellipses
            val latAngles = listOf(-60f, -40f, -20f, 0f, 20f, 40f, 60f)
            latAngles.forEach { lat ->
                val rad = Math.toRadians(lat.toDouble())
                val yOffset = radius * sin(rad).toFloat()
                val latRadiusX = radius * cos(rad).toFloat()
                val latRadiusY = latRadiusX * 0.24f

                drawOval(
                    color = wireframeColor,
                    topLeft = Offset(cx - latRadiusX, cy + yOffset - latRadiusY),
                    size = Size(latRadiusX * 2, latRadiusY * 2),
                    style = Stroke(
                        width = 0.9f,
                        pathEffect = if (lat == 0f) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                )
            }

            // 3. Longitudinal Meridian Ellipses (with 3D rotation)
            val meridianAngles = listOf(0f, 30f, 60f, 90f, 120f, 150f)
            meridianAngles.forEach { baseAngle ->
                val totalAngle = (baseAngle + rotationAnim) % 180f
                val rad = Math.toRadians(totalAngle.toDouble())
                val w = (radius * 2 * kotlin.math.abs(cos(rad))).toFloat().coerceAtLeast(1f)

                drawOval(
                    color = wireframeColor,
                    topLeft = Offset(cx - w / 2, cy - radius),
                    size = Size(w, radius * 2),
                    style = Stroke(
                        width = 0.9f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                )
            }

            // 4. CONNECTED STATE: Glowing Red Orbital Ring
            if (isConnected) {
                rotate(degrees = -12f, pivot = Offset(cx, cy)) {
                    val ringRx = radius * 1.18f * pulseGlow
                    val ringRy = radius * 0.34f

                    // Soft radial glow in center of the ring
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x55FF3022),
                                Color(0x18FF3022),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = ringRx
                        ),
                        topLeft = Offset(cx - ringRx, cy - ringRy),
                        size = Size(ringRx * 2, ringRy * 2)
                    )

                    // Sharp glowing red ring perimeter
                    drawOval(
                        color = CadRedGlow,
                        topLeft = Offset(cx - ringRx, cy - ringRy),
                        size = Size(ringRx * 2, ringRy * 2),
                        style = Stroke(width = 2.4.dp.toPx())
                    )
                }
            }

            // 5. Target Reticle on Globe
            val reticleX = cx + radius * 0.45f
            val reticleY = cy - radius * 0.32f

            drawCircle(
                color = CadRed,
                radius = 7.dp.toPx(),
                center = Offset(reticleX, reticleY),
                style = Stroke(1.5.dp.toPx())
            )
            drawCircle(
                color = CadRed,
                radius = 2.5.dp.toPx(),
                center = Offset(reticleX, reticleY)
            )

            val tickLen = 4.dp.toPx()
            drawLine(
                color = CadRed,
                start = Offset(reticleX - 10.dp.toPx(), reticleY),
                end = Offset(reticleX - 10.dp.toPx() + tickLen, reticleY),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = CadRed,
                start = Offset(reticleX + 10.dp.toPx() - tickLen, reticleY),
                end = Offset(reticleX + 10.dp.toPx(), reticleY),
                strokeWidth = 1.2.dp.toPx()
            )

            // 6. Connecting Leader Line to HUD Callout Box
            val path = Path().apply {
                moveTo(reticleX, reticleY)
                lineTo(reticleX + 38.dp.toPx(), reticleY - 26.dp.toPx())
                lineTo(reticleX + 80.dp.toPx(), reticleY - 26.dp.toPx())
            }

            drawPath(
                path = path,
                color = CadRed,
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = if (isConnected) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            )
        }

        // Floating HUD Callout Box
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = 20.dp)
                .border(
                    width = 1.dp,
                    color = if (isConnected) CadRed else CadBorderBright,
                    shape = RoundedCornerShape(4.dp)
                )
                .background(CadSurface.copy(alpha = 0.94f), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column {
                if (isConnected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(CadRed, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${serverCity.uppercase()}, ${serverCountry.uppercase()}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CadTextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = serverCode,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = CadRed
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(CadRed, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (serverCode.isBlank()) "NO NODE SELECTED" else "TARGET // ${serverCode} [OFF]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CadTextWhite
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "STANDBY ROUTE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CadTextMuted
                    )
                }
            }
        }
    }
}

/**
 * Telemetry text line placed below the wireframe globe
 */
@Composable
private fun CadBelowGlobeTelemetry(isConnected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isConnected) {
            Text(
                text = "LNG 08°40'48\" E AZIMUTH 224°",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
            Text(
                text = "ALT 112M // RADAR ISO//9002",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
        } else {
            Text(
                text = "BEAM_LOCK: DISENGAGED",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
            Text(
                text = "RADAR: IDLE (OFF)",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = CadTextDim
            )
        }
    }
}

/**
 * Node Selector Button Pill (SELECT NODE // FRANKFURT > or ACTIVE NODE // FRA-04 >)
 */
@Composable
private fun CadNodeSelectorPill(
    selectedServerCity: String?,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    val pillShape = if (isConnected) RoundedCornerShape(6.dp) else RoundedCornerShape(20.dp)

    Box(
        modifier = Modifier
            .border(1.dp, CadBorderBright, pillShape)
            .background(CadSurface.copy(alpha = 0.85f), pillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Public,
                contentDescription = "Server Node",
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            val nodeLabel = when {
                selectedServerCity == null -> "SELECT NODE >"
                isConnected -> "ACTIVE NODE // ${selectedServerCity.uppercase()} >"
                else -> "SELECT NODE // ${selectedServerCity.uppercase()} >"
            }
            Text(
                text = nodeLabel,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = CadTextWhite
            )
        }
    }
}

/**
 * Big Status Headline & Sub-status Description
 */
@Composable
private fun CadHeadlineStatus(
    status: VpnStatus,
    isConnected: Boolean,
    isConnecting: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        when {
            isConnected -> {
                Text(
                    text = "Traffic Encrypted",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = CadTextWhite,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(CadRed, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ENCRYPTED TUNNEL ACTIVE • SECURE ROUTE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = CadRed
                    )
                }
            }
            isConnecting -> {
                val isDisconnecting = (status == VpnStatus.DISCONNECTING)
                val headlineText = if (isDisconnecting) "Disengaging..." else "Connecting tunnel..."
                val subText = if (isDisconnecting) "TEARING DOWN TUNNEL • RESTORING DEFAULT ROUTE" else "CRYPTOGRAPHIC KEY EXCHANGE IN PROGRESS"
                Text(
                    text = headlineText,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = CadTextWhite,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(9.dp),
                        strokeWidth = 1.5.dp,
                        color = CadRed
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = subText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = CadRed
                    )
                }
            }
            else -> {
                Text(
                    text = "SHIELD INACTIVE",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp,
                    color = CadTextWhite,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(CadRed, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VPN DISCONNECTED • DEFAULT ROUTE",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = CadRed
                    )
                }
            }
        }
    }
}

/**
 * Full-width CAD Tap Action Button:
 * - When DISCONNECTED: Bold coral-red button ("TAP TO PROTECT >>>")
 * - When CONNECTED: Deep dark tactical button with glowing red border ("<<< TAP TO DISCONNECT")
 * - When CONNECTING: Progress indicator ("ENGAGING TUNNEL...")
 */
@Composable
private fun CadTapActionControl(
    status: VpnStatus,
    isConnected: Boolean,
    isConnecting: Boolean,
    protectLabel: String,
    onTrigger: () -> Unit
) {
    val isDisconnecting = (status == VpnStatus.DISCONNECTING)
    val buttonShape = if (isConnected) RoundedCornerShape(8.dp) else RoundedCornerShape(28.dp)
    val buttonBg = if (isConnected) CadRedBg.copy(alpha = 0.85f) else CadRed
    val borderColor = if (isConnected) CadRed else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(if (isConnected) 1.2.dp else 0.dp, borderColor, buttonShape)
            .clip(buttonShape)
            .background(buttonBg)
            .clickable(enabled = !isDisconnecting, onClick = onTrigger),
        contentAlignment = Alignment.Center
    ) {
        if (isDisconnecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = CadRed
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "DISENGAGING TUNNEL...",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CadRed,
                    letterSpacing = 0.5.sp
                )
            }
        } else if (isConnecting) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "CANCEL CONNECTION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        } else if (isConnected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Disconnect",
                    tint = CadRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TAP TO DISCONNECT",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CadRed,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = protectLabel,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Protect",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Bottom Monospace Telemetry Footer (Clean cryptographic spec line)
 */
@Composable
private fun CadBottomFooter() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "CRYPT: CHACHA20-POLY1305   PROTOCOL: AMNEZIAWG   SYS.VER 4.19",
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = CadTextMuted
        )
    }
}
