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
import com.jm.reader.desktop.core.HostManager
import com.jm.reader.desktop.core.ReadingStats
import com.jm.reader.desktop.core.UpdateSources
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

// Archive palette, identical to the phone build: warm paper, ink, no accent hue at all. The only
// saturated rectangle on screen is a cover.
val BrandOrange = Color(0xFF181818)
val BrandOrangeDark = Color(0xFF181818)
val BrandOrangeSoft = Color(0xFF646B62)

/** "Accent" is ink in the light theme and off-white in the dark one. */
val BrandOrangeDeep = Color(0xFF181818)

val DarkBg = Color(0xFF191919)
val DarkSurface = Color(0xFF242424)
val DarkSurfaceVariant = Color(0xFF2D2D2D)
val DarkOutline = Color(0xFF3C3C3C)

val LightBg = Color(0xFFF4F3EF)
val LightSurface = Color(0xFFFDFCFA)
val LightSurfaceVariant = Color(0xFFECEAE4)
val LightOutline = Color(0xFFD8DBD5)

val TextPrimaryDark = Color(0xFFF2F2F2)
val TextSecondaryDark = Color(0xFFB7B7B7)
val TextPrimaryLight = Color(0xFF181818)
val TextSecondaryLight = Color(0xFF646B62)

/** Canvas is opaque on purpose: translucent chrome behind every card was the old problem. */
const val CanvasAlphaDark = 1f
const val CanvasAlphaLight = 1f

/** Quiet root gradient: one vertical wash, no coloured blobs. */
val BackdropBrushDark: Brush = Brush.verticalGradient(listOf(DarkBg, DarkBg))
val BackdropBrushLight: Brush = Brush.verticalGradient(listOf(LightBg, LightBg))

/** Kept for the legacy backdrop helper; no longer painted as shapes. */
val GlassAccentBlue = Color(0xFF2F80ED)
val GlassAccentViolet = Color(0xFF7C4DFF)
val GlassAccentTeal = Color(0xFF00BFA5)
const val BackdropBlobAlpha = 0.34f

private val DarkColors = darkColorScheme(
    primary = TextPrimaryDark,
    onPrimary = DarkBg,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = TextPrimaryDark,
    secondary = TextSecondaryDark,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF7A7A7A),
    outlineVariant = DarkOutline,
    error = Color(0xFFFF8A80),
    onError = DarkBg,
)

private val LightColors = lightColorScheme(
    primary = TextPrimaryLight,
    onPrimary = LightSurface,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = TextPrimaryLight,
    secondary = TextSecondaryLight,
    onSecondary = LightSurface,
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
    outlineVariant = LightOutline,
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
    val Radius: Dp = 4.dp
    val RadiusLarge: Dp = 6.dp
    val RadiusInner: Dp = 3.dp
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

/** API host probing/selection. */
val LocalHostManager = staticCompositionLocalOf<HostManager> { error("LocalHostManager not provided") }

/** GitHub transports + the reader's pick, for the lines screen and the update card. */
val LocalUpdateSources = staticCompositionLocalOf<UpdateSources> { error("LocalUpdateSources not provided") }
