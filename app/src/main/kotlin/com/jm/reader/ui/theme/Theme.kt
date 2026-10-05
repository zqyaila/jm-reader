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
    // Bring the backdrop up to every screen: text surfaces carry their own fog, so the canvas can
    // be quite translucent (GlassContrastTest checks canvas text over the brightest blob).
    background = DarkBg.copy(alpha = CanvasAlphaDark),
    onBackground = TextPrimaryDark,
    surface = DarkSurface.copy(alpha = 0.88f),
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant.copy(alpha = 0.86f),
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
    background = LightBg.copy(alpha = CanvasAlphaLight),
    onBackground = TextPrimaryLight,
    surface = LightSurface.copy(alpha = 0.90f),
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant.copy(alpha = 0.88f),
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
