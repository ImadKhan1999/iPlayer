package com.imadlabs.iplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.imadlabs.iplayer.data.settings.ThemeMode

data class ImadExtras(
    val isDark: Boolean,
    val accent: Color,
    val border: Color,
    val cardElevated: Color,
    val textMuted: Color,
    val animations: Boolean,
)

val LocalImadExtras = staticCompositionLocalOf {
    ImadExtras(true, ImadColors.CyberMint, ImadColors.BorderDark, ImadColors.SurfaceHover, ImadColors.TextMuted, true)
}

private fun darkScheme(accent: AccentColor): ColorScheme = darkColorScheme(
    primary = accent.dark,
    onPrimary = ImadColors.DeepVoid,
    primaryContainer = Color(0xFF0D2A22).takeIf { accent == AccentColor.CyberMint } ?: ImadColors.SurfaceHover,
    onPrimaryContainer = accent.dark,
    secondary = ImadColors.SkyDiamond,
    onSecondary = ImadColors.DeepVoid,
    secondaryContainer = ImadColors.SurfaceHover,
    onSecondaryContainer = ImadColors.TextPrimaryDark,
    tertiary = ImadColors.ElectricCobalt,
    onTertiary = Color.White,
    background = ImadColors.DeepVoid,
    onBackground = ImadColors.TextPrimaryDark,
    surface = ImadColors.DeepVoid,
    onSurface = ImadColors.TextPrimaryDark,
    surfaceVariant = ImadColors.SurfaceCard,
    onSurfaceVariant = ImadColors.TextSecondaryDark,
    surfaceContainerLowest = ImadColors.DeepVoid,
    surfaceContainerLow = ImadColors.SlateObsidian,
    surfaceContainer = ImadColors.SlateObsidian,
    surfaceContainerHigh = ImadColors.SurfaceCard,
    surfaceContainerHighest = ImadColors.SurfaceHover,
    outline = ImadColors.BorderDark,
    outlineVariant = ImadColors.BorderDark,
    error = ImadColors.Error,
    onError = Color.White,
    inverseSurface = ImadColors.TextPrimaryDark,
    inverseOnSurface = ImadColors.DeepVoid,
    scrim = Color.Black,
)

private fun lightScheme(accent: AccentColor): ColorScheme = lightColorScheme(
    primary = accent.light,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9FBEE).takeIf { accent == AccentColor.CyberMint } ?: Color(0xFFE0ECFF),
    onPrimaryContainer = ImadColors.TextPrimaryLight,
    secondary = ImadColors.DeepCyan,
    onSecondary = Color.White,
    secondaryContainer = ImadColors.LightCard,
    onSecondaryContainer = ImadColors.TextPrimaryLight,
    tertiary = ImadColors.ElectricCobalt,
    background = ImadColors.LightBackground,
    onBackground = ImadColors.TextPrimaryLight,
    surface = ImadColors.LightBackground,
    onSurface = ImadColors.TextPrimaryLight,
    surfaceVariant = ImadColors.LightCard,
    onSurfaceVariant = ImadColors.TextSecondaryLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = ImadColors.LightSurface,
    surfaceContainerHigh = ImadColors.LightCard,
    surfaceContainerHighest = ImadColors.LightBorder,
    outline = ImadColors.LightBorder,
    outlineVariant = ImadColors.LightBorder,
    error = Color(0xFFDC2626),
)

@Composable
fun IPlayerTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: AccentColor = AccentColor.CyberMint,
    textScale: Float = 1f,
    animations: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val scheme = if (dark) darkScheme(accent) else lightScheme(accent)
    val extras = ImadExtras(
        isDark = dark,
        accent = if (dark) accent.dark else accent.light,
        border = if (dark) ImadColors.BorderDark else ImadColors.LightBorder,
        cardElevated = if (dark) ImadColors.SurfaceHover else Color.White,
        textMuted = if (dark) ImadColors.TextMuted else ImadColors.TextSecondaryLight,
        animations = animations,
    )
    CompositionLocalProvider(LocalImadExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = imadTypography(textScale), content = content)
    }
}
