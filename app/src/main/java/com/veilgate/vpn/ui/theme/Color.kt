package com.veilgate.vpn.ui.theme

import androidx.compose.ui.graphics.Color

// CAD // Tactical Cyber Radar Design Tokens
val CadBg = Color(0xFF07080A)
val CadSurface = Color(0xFF101319)
val CadCard = Color(0xFF131720)
val CadBorder = Color(0xFF222836)
val CadBorderBright = Color(0xFF384358)
val CadGridLine = Color(0x1848567A)

// Alert Coral / Tactical Glowing Red
val CadRed = Color(0xFFFF4136)           // High-contrast tactical alert red
val CadRedGlow = Color(0xFFFF3022)       // Neon glowing red for locked orbital ring
val CadRedDim = Color(0xFF5E1E1A)        // Inactive / standby red outline
val CadRedBg = Color(0xFF280E0C)         // Deep dark red surface tint
val CadRedAccent = Color(0xFFFF5247)     // Active reticle & button highlight

val CadTextWhite = Color(0xFFFFFFFF)
val CadTextDim = Color(0xFF7A869A)
val CadTextMuted = Color(0xFF485265)

// Legacy Theme Mappings for Backwards Compatibility
val BgDark = CadBg
val SurfaceDark = CadSurface
val CardDark = CadCard
val BorderDark = CadBorder

val NeonCyan = Color(0xFF38BDF8)
val NeonGreen = Color(0xFF00E676)
val NeonRed = CadRed
val NeonAmber = Color(0xFFFFB300)

val TextPrimary = CadTextWhite
val TextSecondary = CadTextDim
val TextMuted = CadTextMuted
