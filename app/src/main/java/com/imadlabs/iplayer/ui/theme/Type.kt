package com.imadlabs.iplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.imadlabs.iplayer.R

// Brand typography (Graphics/brand/typography): Plus Jakarta Sans for headings, Inter for UI,
// JetBrains Mono for technical readouts. Bundled as variable fonts (SIL OFL).
private fun variable(res: Int, weight: FontWeight) =
    Font(res, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val JakartaSans = FontFamily(
    variable(R.font.plus_jakarta_sans, FontWeight.Medium),
    variable(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variable(R.font.plus_jakarta_sans, FontWeight.Bold),
    variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

val Inter = FontFamily(
    variable(R.font.inter, FontWeight.Normal),
    variable(R.font.inter, FontWeight.Medium),
    variable(R.font.inter, FontWeight.SemiBold),
    variable(R.font.inter, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, FontWeight.Normal),
    variable(R.font.jetbrains_mono, FontWeight.Medium),
    variable(R.font.jetbrains_mono, FontWeight.Bold),
)

private fun heading(size: Int, line: Int, weight: FontWeight, spacing: Double) = TextStyle(
    fontFamily = JakartaSans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = spacing.em,
)

private fun body(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, spacing: Double = 0.0) = TextStyle(
    fontFamily = Inter, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    letterSpacing = spacing.em,
)

fun imadTypography(scale: Float = 1f): Typography {
    fun TextStyle.s() = if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = heading(48, 56, FontWeight.ExtraBold, -0.02).s(),
        displayMedium = heading(40, 48, FontWeight.ExtraBold, -0.02).s(),
        displaySmall = heading(36, 44, FontWeight.ExtraBold, -0.02).s(),
        headlineLarge = heading(30, 38, FontWeight.Bold, -0.015).s(),
        headlineMedium = heading(26, 34, FontWeight.Bold, -0.015).s(),
        headlineSmall = heading(22, 30, FontWeight.Bold, -0.01).s(),
        titleLarge = heading(20, 28, FontWeight.Bold, -0.01).s(),
        titleMedium = heading(16, 24, FontWeight.SemiBold, 0.0).s(),
        titleSmall = heading(14, 20, FontWeight.SemiBold, 0.0).s(),
        bodyLarge = body(16, 24).s(),
        bodyMedium = body(14, 20).s(),
        bodySmall = body(12, 16, spacing = 0.01).s(),
        labelLarge = body(14, 20, FontWeight.SemiBold).s(),
        labelMedium = body(12, 16, FontWeight.Medium, 0.01).s(),
        labelSmall = body(11, 14, FontWeight.Medium, 0.02).s(),
    )
}

val MonoStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
val BadgeStyle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.08.em)
