package com.veilgate.vpn.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veilgate.vpn.R
import com.veilgate.vpn.ui.theme.CadBg
import com.veilgate.vpn.ui.theme.CadBorder
import com.veilgate.vpn.ui.theme.CadBorderBright
import com.veilgate.vpn.ui.theme.CadCard
import com.veilgate.vpn.ui.theme.CadRed
import com.veilgate.vpn.ui.theme.CadSurface
import com.veilgate.vpn.ui.theme.CadTextDim
import com.veilgate.vpn.ui.theme.CadTextMuted
import com.veilgate.vpn.ui.theme.CadTextWhite

/**
 * Prominent In-App Disclosure Dialog for VpnService Compliance (Google Play Policy).
 * Displayed before `VpnService.prepare()` is invoked on first tunnel creation attempt
 * or whenever disclosure version is bumped.
 */
@Composable
fun VpnDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadSurface,
        modifier = Modifier
            .border(1.dp, CadBorderBright, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CadCard, RoundedCornerShape(8.dp))
                        .border(1.dp, CadBorderBright, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = CadRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.vpn_disclosure_title),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = CadTextWhite
                    )
                    Text(
                        text = "VpnService · Prominent Disclosure",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = CadTextDim
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.vpn_disclosure_summary),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = CadTextWhite,
                    lineHeight = 18.sp
                )

                // Section 1: Purpose
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CadBg, RoundedCornerShape(8.dp))
                        .border(1.dp, CadBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vpn_disclosure_purpose_title),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = CadRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vpn_disclosure_purpose_text),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.5.sp,
                        color = CadTextDim,
                        lineHeight = 16.sp
                    )
                }

                // Section 2: Data Handling, Transmission & Privacy
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CadBg, RoundedCornerShape(8.dp))
                        .border(1.dp, CadBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vpn_disclosure_data_title),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = CadRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vpn_disclosure_data_text),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.5.sp,
                        color = CadTextDim,
                        lineHeight = 16.sp
                    )
                }

                // Privacy Policy Link
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Read our full Privacy Policy",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.5.sp,
                        color = CadRed,
                        textDecoration = TextDecoration.Underline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open Privacy Policy",
                        tint = CadRed,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Text(
                    text = "By continuing, you acknowledge that Android will display a system confirmation request to configure the VPN connection.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = CadTextMuted,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = CadRed),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.vpn_disclosure_agree),
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CadTextWhite
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.vpn_disclosure_decline),
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = CadTextDim
                )
            }
        }
    )
}
