package com.jm.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// `internal` (not private) so GlassContrastTest can assert the canvas opacity: the page canvas
// must stay near-opaque, otherwise body text ends up sitting on the gradient again.
internal val DarkColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = BrandOrangeSoft,
    onSecondary = DarkBg,
    // The page canvas stays almost opaque: lists, grids and body text must never sit directly on
    // the gradient. The "glass" look lives in the bars and panels (see Glass.kt), not here.
    background = DarkBg.copy(alpha = 0.92f),
    onBackground = TextPrimaryDark,
    surface = DarkSurface.copy(alpha = 0.95f),
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant.copy(alpha = 0.93f),
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkOutline,
)

internal val LightColors = lightColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeSoft,
    onPrimaryContainer = LightBg,
    secondary = BrandOrangeDark,
    onSecondary = Color.White,
    background = LightBg.copy(alpha = 0.94f),
    onBackground = TextPrimaryLight,
    surface = LightSurface.copy(alpha = 0.96f),
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant.copy(alpha = 0.94f),
    onSurfaceVariant = TextSecondaryLight,
)

@Composable
fun JMReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
