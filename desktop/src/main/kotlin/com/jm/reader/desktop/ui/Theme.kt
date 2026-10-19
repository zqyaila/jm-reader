package com.jm.reader.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm.reader.desktop.core.AppStrings
import com.jm.reader.desktop.core.LanguageManager
import com.jm.reader.desktop.core.ReadingStats
import com.jm.reader.desktop.core.Repository
import com.jm.reader.desktop.core.Session

// ---------------------------------------------------------------------------
// Palette: "ink & ember" - the same values as the Android module's theme/Color.kt.
//
// The previous desktop theme mirrored the old phone build: violet/blue/teal blobs behind frosted
// panes. On a desktop window that reads as coloured haze, because the window is large enough that
// the gradient is always visible around the content. The new palette keeps the chrome neutral and
// leaves one ember accent, so covers carry the colour.
// ---------------------------------------------------------------------------

val BrandOrange = Color(0xFFFF8A4C)
val BrandOrangeDark = Color(0xFFD9601F)
val BrandOrangeSoft = Color(0xFFFFB37A)

/** Accent used for text/icons on light surfaces; the bright ember fails contrast on paper. */
val BrandOrangeDeep = Color(0xFFC2410C)

val DarkBg = Color(0xFF0C0C0E)
val DarkSurface = Color(0xFF16161A)
val DarkSurfaceVariant = Color(0xFF1D1D22)
val DarkOutline = Color(0xFF2E2E35)

val LightBg = Color(0xFFF7F4EF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0EBE3)
val LightOutline = Color(0xFFE2DCD2)

val TextPrimaryDark = Color(0xFFF4F1EC)
val TextSecondaryDark = Color(0xFFA29C93)
val TextPrimaryLight = Color(0xFF17130F)
val TextSecondaryLight = Color(0xFF6B645B)

/** Canvas is opaque on purpose: translucent chrome behind every card was the old problem. */
const val CanvasAlphaDark = 1f
const val CanvasAlphaLight = 1f

/** Quiet root gradient: one vertical wash, no coloured blobs. */
val BackdropBrushDark: Brush = Brush.verticalGradient(
    listOf(Color(0xFF14100F), Color(0xFF0C0C0E), Color(0xFF0B0B0D)),
)
val BackdropBrushLight: Brush = Brush.verticalGradient(
    listOf(Color(0xFFFBF8F3), Color(0xFFF7F4EF), Color(0xFFF2EEE7)),
)

/** Kept for the legacy backdrop helper; no longer painted as shapes. */
val GlassAccentBlue = Color(0xFF2F80ED)
val GlassAccentViolet = Color(0xFF7C4DFF)
val GlassAccentTeal = Color(0xFF00BFA5)
const val BackdropBlobAlpha = 0.34f

private val DarkColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = Color(0xFF1A0D06),
    primaryContainer = Color(0xFF3A1D10),
    onPrimaryContainer = BrandOrangeSoft,
    secondary = BrandOrangeSoft,
    onSecondary = Color(0xFF1A0D06),
    background = DarkBg,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = DarkOutline,
    outlineVariant = Color(0xFF23232A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0D06),
)

private val LightColors = lightColorScheme(
    primary = BrandOrangeDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D2),
    onPrimaryContainer = Color(0xFF5A1C00),
    secondary = BrandOrangeDark,
    onSecondary = Color.White,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    outlineVariant = Color(0xFFE6E0D6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * Type scale: headings carry the hierarchy, body stays out of the way. The desktop default is
 * Material's stock scale, whose titles are only a few pixels larger than body text - on a large
 * window that flattens every section into one grey block.
 */
private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * Metric tokens. The Android app reads these from its liquid-glass spec; the desktop build keeps
 * the same numbers so a card has the same proportions on both.
 */
object AppSizes {
    val Radius: Dp = 16.dp
    val RadiusLarge: Dp = 24.dp
    val RadiusInner: Dp = 12.dp
    val ContentPadding: Dp = 20.dp
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
        typography = AppTypography,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Composition locals — the desktop twin of the Android module's ui/AppLocals.kt
// ---------------------------------------------------------------------------

val LocalRepository = staticCompositionLocalOf<Repository> { error("LocalRepository not provided") }
val LocalSession = staticCompositionLocalOf<Session> { error("LocalSession not provided") }
val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> { error("LanguageManager not provided") }
val LocalAppStrings = staticCompositionLocalOf<AppStrings> { error("AppStrings not provided") }
val LocalStats = staticCompositionLocalOf<ReadingStats> { error("LocalStats not provided") }
