package com.jm.reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Colour schemes.
 *
 * Both are monochrome on purpose. `primary` is ink in the light theme and off-white in the dark
 * one, so a filled button, an active tab and a section rule all use the same "printed" colour - the
 * archive vocabulary has no accent hue, and adding one immediately makes the cover art compete with
 * the chrome.
 *
 * Surfaces are opaque: only the floating dock is glass, and it samples the page underneath.
 */
internal val ArchiveDarkColors = darkColorScheme(
    primary = TextPrimaryDark,
    onPrimary = DarkBg,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = TextPrimaryDark,
    secondary = TextSecondaryDark,
    onSecondary = DarkBg,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = TextSecondaryDark,
    onTertiary = DarkBg,
    background = DarkBg,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF7A7A7A),
    outlineVariant = HairlineDark,
    error = Color(0xFFFF8A80),
    onError = DarkBg,
)

internal val ArchiveLightColors = lightColorScheme(
    primary = Ink,
    onPrimary = PaperMilk,
    primaryContainer = PaperRaised,
    onPrimaryContainer = Ink,
    secondary = InkMuted,
    onSecondary = PaperMilk,
    secondaryContainer = PaperRaised,
    onSecondaryContainer = Ink,
    tertiary = InkMuted,
    onTertiary = PaperMilk,
    background = PaperBg,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = InkMuted,
    outline = LightOutline,
    outlineVariant = HairlineLight,
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun JMReaderTheme(
    theme: AppTheme = AppTheme.ARCHIVE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val archive = theme == AppTheme.ARCHIVE
    MaterialTheme(
        colorScheme = when {
            archive && darkTheme -> ArchiveDarkColors
            archive -> ArchiveLightColors
            darkTheme -> EmberDarkColors
            else -> EmberLightColors
        },
        typography = AppTypography,
        shapes = if (archive) ArchiveShapes else EmberShapes,
        content = content,
    )
}

/** Ember (the previous look): ink + a single ember accent, 12/16/24 dp surfaces. */
internal val EmberDarkColors = darkColorScheme(
    primary = EmberAccentDark,
    onPrimary = Color(0xFF1A0D06),
    primaryContainer = Color(0xFF3A1D10),
    onPrimaryContainer = EmberAccentSoft,
    secondary = EmberAccentSoft,
    onSecondary = Color(0xFF1A0D06),
    secondaryContainer = Color(0xFF2A211C),
    onSecondaryContainer = EmberAccentSoft,
    tertiary = EmberAccentSoft,
    onTertiary = Color(0xFF1A0D06),
    background = EmberBgDark,
    onBackground = EmberTextDark,
    surface = EmberSurfaceDark,
    onSurface = EmberTextDark,
    surfaceVariant = EmberRaisedDark,
    onSurfaceVariant = EmberTextMutedDark,
    outline = EmberOutlineDark,
    outlineVariant = Color(0xFF2E2E35),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF1A0D06),
)

internal val EmberLightColors = lightColorScheme(
    primary = EmberAccentDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D2),
    onPrimaryContainer = Color(0xFF5A1C00),
    secondary = Color(0xFFD9601F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBDD),
    onSecondaryContainer = Color(0xFF5A1C00),
    tertiary = Color(0xFFD9601F),
    onTertiary = Color.White,
    background = EmberBgLight,
    onBackground = EmberTextLight,
    surface = EmberSurfaceLight,
    onSurface = EmberTextLight,
    surfaceVariant = EmberRaisedLight,
    onSurfaceVariant = EmberTextMutedLight,
    outline = EmberOutlineLight,
    outlineVariant = Color(0xFFE6E0D6),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/** Archive shape scale: catalogue corners, not pills. */
internal val ArchiveShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

/** Ember shape scale: the previous, softer scale. */
internal val EmberShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Short aliases used by the contrast tests: the archive schemes are the default pair.
internal val DarkColors = ArchiveDarkColors
internal val LightColors = ArchiveLightColors
