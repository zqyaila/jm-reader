package com.jm.reader.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand palette inspired by the original app (dark + orange accent).
val BrandOrange = Color(0xFFFF6F00)
val BrandOrangeDark = Color(0xFFE65100)
val BrandOrangeSoft = Color(0xFFFFB74D)

val DarkBg = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF2A2A2A)
val DarkOutline = Color(0xFF3A3A3A)

val LightBg = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDEDED)

val TextPrimaryDark = Color(0xFFEDEDED)
val TextSecondaryDark = Color(0xFFB0B0B0)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF666666)

val AdFreeAccent = Color(0xFF4CAF50)

// ---------------------------------------------------------------------------
// Frosted glass ("毛玻璃") palette
//
// Legibility rule for this file: glass fills must be *scrims*, not washes.
//  - In the dark theme a panel is a near-opaque BLACK scrim (it darkens what is behind it, so
//    light text keeps its contrast).
//  - In the light theme it is a near-opaque WHITE scrim (it lightens, so dark text stays dark).
//
// The first version of this feature did the opposite (a translucent white wash over a bright
// gradient with an also-translucent page background), which is exactly why icons and text became
// unreadable. `GlassContrastTest` pins the ratios down.
// ---------------------------------------------------------------------------

/** Second accent used by the backdrop blobs so the glass has some colour to refract. */
val GlassAccentBlue = Color(0xFF2F80ED)

/** Third blob colour - gives the backdrop a slow, "liquid" colour drift. */
val GlassAccentViolet = Color(0xFF7C4DFF)

/**
 * Accent used for *text and icons* on light-theme glass. The brand orange (#FF6F00) only reaches
 * ~2.9:1 on a white bar, which is fine for a large filled button but not for a nav label.
 */
val BrandOrangeDeep = Color(0xFFBF360C)

/** Fill for panels / cards sitting directly on the page. */
val GlassScrimDark = Color(0xD9141418)
val GlassScrimLight = Color(0xE0FFFFFF)

/** Fill for bars (top bar / bottom navigation): a little more see-through than a panel. */
val GlassBarScrimDark = Color(0xCC0C0C10)
val GlassBarScrimLight = Color(0xE6FFFFFF)

/** Hairline highlight that sells the "edge of a pane of glass" look. */
val GlassBorderDark = Color(0x3DFFFFFF)
val GlassBorderLight = Color(0x26000000)

/** Soft sheen drawn inside a panel / bar over the scrim. */
val GlassSheen = Color(0x1FFFFFFF)

/**
 * Backdrop blob strength. Kept moderate: the blobs are what makes the translucent surfaces read as
 * glass, but they must not become a light source behind text (the canvas and every text surface
 * have their own scrim, and `GlassContrastTest` checks the worst-case composite).
 */
const val BackdropBlobAlpha = 0.30f

/** Root gradient behind everything. */
val BackdropBrush: Brush = Brush.linearGradient(
    listOf(
        Color(0xFF101218),
        Color(0xFF1A1412),
        Color(0xFF0D1014),
    ),
)

/** Light-theme root gradient. */
val BackdropBrushLight: Brush = Brush.linearGradient(
    listOf(
        Color(0xFFF7F8FC),
        Color(0xFFFFF3E8),
        Color(0xFFF1F3F8),
    ),
)

/** Soft elevation shadow tint used under glass panels. */
val GlassShadow = Color(0x40000000)
