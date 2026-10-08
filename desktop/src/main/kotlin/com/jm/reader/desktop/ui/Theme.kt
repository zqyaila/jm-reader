package com.jm.reader.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.AppStrings
import com.jm.reader.desktop.core.LanguageManager
import com.jm.reader.desktop.core.Repository
import com.jm.reader.desktop.core.Session

// ---------------------------------------------------------------------------
// Brand palette — identical values to the Android module's theme/Color.kt, so
// the desktop client and the phone client look like the same product.
// ---------------------------------------------------------------------------

val BrandOrange = Color(0xFFFF6F00)
val BrandOrangeDark = Color(0xFFE65100)
val BrandOrangeSoft = Color(0xFFFFB74D)

/** Accent used for text/icons on light surfaces: brand orange alone only reaches ~2.9:1 there. */
val BrandOrangeDeep = Color(0xFFBF360C)

val DarkBg = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkOutline = Color(0xFF3A3A3A)

val LightBg = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDEDED)

val TextPrimaryDark = Color(0xFFEDEDED)

/** Hint text is a step brighter than a typical dark theme: it sits on glass over cover art. */
val TextSecondaryDark = Color(0xFFC4C4C4)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF4A4A4A)

/** Canvas opacity: low enough that the backdrop gradient reaches every screen. */
const val CanvasAlphaDark = 0.62f
const val CanvasAlphaLight = 0.72f

/** Root gradients behind everything. */
val BackdropBrushDark: Brush = Brush.linearGradient(
    listOf(Color(0xFF0B0D16), Color(0xFF1B1220), Color(0xFF0A1418)),
)
val BackdropBrushLight: Brush = Brush.linearGradient(
    listOf(Color(0xFFF2F4FF), Color(0xFFFFF0E2), Color(0xFFEFF6F5)),
)

/** Blob colours the glass refracts. */
val GlassAccentBlue = Color(0xFF2F80ED)
val GlassAccentViolet = Color(0xFF7C4DFF)
val GlassAccentTeal = Color(0xFF00BFA5)
const val BackdropBlobAlpha = 0.34f

private val DarkColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color.White,
    primaryContainer = BrandOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = BrandOrangeSoft,
    onSecondary = DarkBg,
    background = DarkBg.copy(alpha = CanvasAlphaDark),
    onBackground = TextPrimaryDark,
    surface = DarkSurface.copy(alpha = 0.88f),
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant.copy(alpha = 0.86f),
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkOutline,
)

private val LightColors = lightColorScheme(
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

/**
 * Metric tokens. The Android app reads these from its liquid-glass spec (`GlassSizes`); the
 * desktop build keeps the same numbers so a card has the same proportions on both.
 */
object AppSizes {
    val Radius: Dp = 16.dp
    val RadiusLarge: Dp = 24.dp
    val RadiusInner: Dp = 12.dp
    val ContentPadding: Dp = 16.dp
    val CardPadding: Dp = 8.dp
    val BarHeight: Dp = 56.dp
    val ItemHeight: Dp = 48.dp
    /** Cap on the reading column width on a wide window: prose that spans 2000px is unreadable. */
    val ReaderMaxWidth: Dp = 1100.dp
    val DetailMaxWidth: Dp = 1200.dp
}

@Composable
fun JMReaderTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Composition locals — the desktop twin of the Android module's ui/AppLocals.kt
// ---------------------------------------------------------------------------

val LocalRepository = staticCompositionLocalOf<Repository> { error("LocalRepository not provided") }
val LocalSession = staticCompositionLocalOf<Session> { error("LocalSession not provided") }
val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> { error("LocalLanguageManager not provided") }
val LocalAppStrings = staticCompositionLocalOf<AppStrings> { error("LocalAppStrings not provided") }
