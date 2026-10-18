package com.jm.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Colour schemes.
 *
 * Both schemes are *opaque* now. Translucency is reserved for the one floating bar; a page canvas
 * that let a coloured gradient show through every card was the reason the old build looked busy.
 *
 * Primary is theme-dependent on purpose: ember `#FF8A4C` is legible on ink but only ~2.4:1 on
 * paper, so the light scheme uses `#C2410C` for anything drawn as text or an icon. Filled buttons
 * therefore read correctly in both themes without a second "button colour" token.
 */
internal val DarkColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color(0xFF1A0D06),
    primaryContainer = Color(0xFF3A1D10),
    onPrimaryContainer = BrandOrangeSoft,
    secondary = BrandOrangeSoft,
    onSecondary = Color(0xFF1A0D06),
    secondaryContainer = Color(0xFF2A211C),
    onSecondaryContainer = BrandOrangeSoft,
    tertiary = BrandOrangeSoft,
    onTertiary = Color(0xFF1A0D06),
    background = DarkBg,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkOutline,
    outlineVariant = HairlineDark,
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0D06),
)

internal val LightColors = lightColorScheme(
    primary = BrandOrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D2),
    onPrimaryContainer = Color(0xFF5A1C00),
    secondary = BrandOrangeDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBDD),
    onSecondaryContainer = Color(0xFF5A1C00),
    tertiary = BrandOrangeDark,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    outlineVariant = HairlineLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun JMReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        // M3 Expressive hierarchy + shape scale, wired in exactly one place so every screen
        // inherits it. See Type.kt / Shapes.kt for the per-token rationale.
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
