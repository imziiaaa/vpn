package com.veilgate.vpn.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veilgate.vpn.data.model.ServerNode
import com.veilgate.vpn.ui.theme.BorderDark
import com.veilgate.vpn.ui.theme.CardDark
import com.veilgate.vpn.ui.theme.NeonAmber
import com.veilgate.vpn.ui.theme.NeonCyan
import com.veilgate.vpn.ui.theme.NeonGreen
import com.veilgate.vpn.ui.theme.SurfaceDark
import com.veilgate.vpn.ui.theme.TextMuted
import com.veilgate.vpn.ui.theme.TextPrimary
import com.veilgate.vpn.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerPickerSheet(
    servers: List<ServerNode>,
    selectedServer: ServerNode?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onSelect: (ServerNode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Select VPN Node",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRefresh, enabled = !isLoading) {
                    Text("Refresh", color = if (isLoading) TextMuted else NeonCyan)
                }
            }

            if (isLoading || servers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = NeonCyan)
                    } else {
                        Text(
                            text = errorMessage ?: "No VPN nodes are available. Tap Refresh to check again.",
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(servers, key = { it.id }) { server ->
                        val isSelected = server.id == selectedServer?.id
                        ServerItemRow(
                            server = server,
                            isSelected = isSelected,
                            onClick = {
                                onSelect(server)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerItemRow(
    server: ServerNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val loadColor = when {
        server.loadPercent < 50 -> NeonGreen
        server.loadPercent < 80 -> NeonAmber
        else -> Color(0xFFFF5252)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) CardDark else Color(0xFF101623))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(BorderDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Dns,
                contentDescription = null,
                tint = if (isSelected) NeonCyan else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = server.location.ifEmpty { "Global Edge Node" },
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Server Load indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BorderDark.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(loadColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${server.loadPercent}% load",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        if (isSelected) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
