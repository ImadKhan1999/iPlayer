package com.imadlabs.iplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** ImadLabs brand tokens, taken from Graphics/brand/colors/tokens.json. */
object ImadColors {
    val CyberMint = Color(0xFF00F5A0)
    val QuantumCore = Color(0xFF00FFC2)
    val MintDeep = Color(0xFF00D285)
    val ElectricCobalt = Color(0xFF0066FF)
    val SkyDiamond = Color(0xFF38BDF8)
    val DeepCyan = Color(0xFF0284C7)

    val DeepVoid = Color(0xFF070B14)
    val SlateObsidian = Color(0xFF0B0F19)
    val SurfaceCard = Color(0xFF111827)
    val SurfaceHover = Color(0xFF162032)
    val BorderDark = Color(0xFF1E293B)
    val TextPrimaryDark = Color(0xFFF8FAFC)
    val TextSecondaryDark = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val LightBackground = Color(0xFFF8FAFC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightCard = Color(0xFFF1F5F9)
    val LightBorder = Color(0xFFE2E8F0)
    val TextPrimaryLight = Color(0xFF0F172A)
    val TextSecondaryLight = Color(0xFF475569)

    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)

    /** Brand progress gradient (DESIGN_SYSTEM.md: #00F5D4 -> #0066FF). */
    val ProgressGradient = Brush.horizontalGradient(listOf(Color(0xFF00F5D4), ElectricCobalt))
    val BrandGradient = Brush.linearGradient(listOf(Color(0xFF00F5D4), ElectricCobalt))
}

/** Accent options offered in Appearance settings; all are brand colors or calibrated colorways. */
enum class AccentColor(val label: String, val dark: Color, val light: Color) {
    CyberMint("Cyber Mint", ImadColors.CyberMint, Color(0xFF00875A)),
    Cobalt("Electric Cobalt", Color(0xFF4D94FF), ImadColors.ElectricCobalt),
    Sky("Sky Diamond", ImadColors.SkyDiamond, ImadColors.DeepCyan),
    SolarAmber("Solar Amber", Color(0xFFFFB703), Color(0xFFB45309)),
    LaserCyan("Laser Cyan", Color(0xFF00F0FF), Color(0xFF0E7490)),
}
