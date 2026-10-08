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

/**
 * Hint / secondary text is a step brighter than a typical dark theme: it has to stay readable on
 * glass that sits over full-bleed white cover art (see GlassContrastTest).
 */
val TextSecondaryDark = Color(0xFFC4C4C4)
val TextPrimaryLight = Color(0xFF1A1A1A)
val TextSecondaryLight = Color(0xFF4A4A4A)

val AdFreeAccent = Color(0xFF4CAF50)

// ---------------------------------------------------------------------------
// Liquid glass - tokens follow the design spec of the "skill-liquid-glass" skill
// (https://github.com/JUEMING-006/skill-liquid-glass, built on Kyant0/AndroidLiquidGlass):
//
//   blur        2dp            low blur keeps the underlying content readable
//   lens        12dp / 24dp    refraction + chromatic aberration (approximated, see Glass.kt)
//   base fog    15 % neutral   (light FAFAFA / dark 121212)
//   highlight   0.15           specular sheen
//   shadow      0.08           drop shadow
//   innerShadow 4dp / 0.1      rim shading
//
// Deliberate deviation: that 15 % fog assumes the library's full backdrop + vibrancy pipeline.
// This app scrolls full-bleed white cover art under its bars and Compose has no `vibrancy`, so the
// fog is raised until text keeps WCAG AA over *any* backdrop - GlassContrastTest composites the
// fog over pure white and pure black to prove it.
// ---------------------------------------------------------------------------

/** Spec tokens. */
const val GlassBlurDp = 2f
const val GlassLensRefractionDp = 12f
const val GlassLensDistortionDp = 24f
const val GlassHighlightAlpha = 0.15f
const val GlassShadowAlpha = 0.08f
const val GlassInnerShadowDp = 4f
const val GlassInnerShadowAlpha = 0.10f

/** Specular sheen angle, in degrees (`HighlightStyle.Default.angle`). */
const val GlassHighlightAngle = 45f

/**
 * Fog strength per surface kind (see the deviation note above).
 *
 * The bottom bar is the densest because full-bleed cover art scrolls *underneath* it; panes and
 * top bars only ever sit over the app canvas, which is itself dark/light enough to carry text, so
 * they can stay thin and actually look like glass.
 */
const val GlassBottomBarFogAlphaDark = 0.78f
const val GlassBottomBarFogAlphaLight = 0.80f
const val GlassTopBarFogAlphaDark = 0.52f
const val GlassTopBarFogAlphaLight = 0.58f
const val GlassPanelFogAlphaDark = 0.45f
const val GlassPanelFogAlphaLight = 0.52f

/** Neutral fog bases from the spec (`121212` darkened slightly, see the deviation note). */
val GlassFogDark = Color(0xFF0A0A0A)
val GlassFogLight = Color(0xFFFAFAFA)

/** Canvas opacity: low enough that the backdrop colour reaches every screen. */
const val CanvasAlphaDark = 0.62f
const val CanvasAlphaLight = 0.72f

/** Specular highlight + rim light. */
val GlassSpecular = Color(0xFFFFFFFF)
val GlassRimTop = Color(0x59FFFFFF)
val GlassRimBottom = Color(0x0DFFFFFF)
val GlassRimTopLight = Color(0x99FFFFFF)
val GlassRimBottomLight = Color(0x14000000)

/** Blob colours the glass refracts. */
val GlassAccentBlue = Color(0xFF2F80ED)
val GlassAccentViolet = Color(0xFF7C4DFF)
val GlassAccentTeal = Color(0xFF00BFA5)

/**
 * Accent used for *text and icons* on light-theme glass. The brand orange (#FF6F00) only reaches
 * ~2.9:1 on a white bar, which is fine for a large filled button but not for a nav label.
 */
val BrandOrangeDeep = Color(0xFFBF360C)

/**
 * Backdrop blob strength. These blobs are what the glass refracts; without colour variation behind
 * a pane, "glass" reads as a flat strip.
 */
const val BackdropBlobAlpha = 0.34f

/** Root gradient behind everything. */
val BackdropBrush: Brush = Brush.linearGradient(
    listOf(
        Color(0xFF0B0D16),
        Color(0xFF1B1220),
        Color(0xFF0A1418),
    ),
)

/** Light-theme root gradient. */
val BackdropBrushLight: Brush = Brush.linearGradient(
    listOf(
        Color(0xFFF2F4FF),
        Color(0xFFFFF0E2),
        Color(0xFFEFF6F5),
    ),
)

/** Soft elevation shadow tint used under glass panels. */
val GlassShadow = Color(0x40000000)
