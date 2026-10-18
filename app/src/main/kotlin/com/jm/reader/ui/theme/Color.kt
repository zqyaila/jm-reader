package com.jm.reader.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Palette: "ink & ember".
//
// The previous palette was built around a violet/blue/teal blob backdrop with frosted glass on
// every surface. Over full-bleed cover art that reads as noise: dozens of tinted panels, coloured
// halos and diagonal sheens competing with the artwork. This palette removes the colour from the
// chrome and leaves exactly one accent - ember orange - so covers are the only saturated thing on
// screen.
//
//   dark  : ink       #0C0C0E canvas / #16161A surface / #1D1D22 raised
//   light : paper     #F7F4EF canvas / #FFFFFF surface / #F0EBE3 raised
//   accent: ember     #FF8A4C on ink, #C2410C on paper (both clear AA for text)
// ---------------------------------------------------------------------------

/** Accent for dark surfaces (large type, icons, active states). */
val BrandOrange = Color(0xFFFF8A4C)

/** Accent pressed / container variant. */
val BrandOrangeDark = Color(0xFFD9601F)

/** Lighter accent used for gradients on artwork overlays. */
val BrandOrangeSoft = Color(0xFFFFB37A)

/**
 * Accent used for *text and icons* on light surfaces: the bright ember only reaches ~2.4:1 on
 * paper, which is fine for a filled button but not for a label.
 */
val BrandOrangeDeep = Color(0xFFC2410C)

val DarkBg = Color(0xFF0C0C0E)
val DarkSurface = Color(0xFF16161A)
val DarkSurfaceVariant = Color(0xFF1D1D22)
val DarkOutline = Color(0xFF2E2E35)

val LightBg = Color(0xFFF7F4EF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0EBE3)
val LightOutline = Color(0xFFE2DCD2)

/** Primary text: warm off-white rather than pure white, which reads softer on ink. */
val TextPrimaryDark = Color(0xFFF4F1EC)

/** Secondary text: 7.9:1 on #16161A - comfortable for captions and metadata. */
val TextSecondaryDark = Color(0xFFA29C93)
val TextPrimaryLight = Color(0xFF17130F)
val TextSecondaryLight = Color(0xFF6B645B)

val AdFreeAccent = Color(0xFF4CAF50)

// ---------------------------------------------------------------------------
// Glass tokens
//
// Glass is now the *exception*, not the rule: it survives only in the floating bottom navigation
// (the one place where content genuinely scrolls underneath). Everything else uses a solid
// surface, so these tokens are kept because the bar still samples and blurs the page behind it.
//
// The numeric spec tokens are unchanged on purpose - GlassContrastTest pins them, and the bar's
// legibility guarantee (WCAG AA over pure white / pure black artwork scrolling underneath) still
// depends on the fog being dense.
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
 * Fog strength per surface kind.
 *
 * The bottom bar is nearly opaque now: it floats over cover art mid-scroll, and a translucent bar
 * with a blur behind it was the single biggest source of visual noise in the old design.
 */
const val GlassBottomBarFogAlphaDark = 0.92f
const val GlassBottomBarFogAlphaLight = 0.94f
const val GlassTopBarFogAlphaDark = 0.86f
const val GlassTopBarFogAlphaLight = 0.90f
const val GlassPanelFogAlphaDark = 0.45f
const val GlassPanelFogAlphaLight = 0.52f

/** Neutral fog bases. */
val GlassFogDark = Color(0xFF121215)
val GlassFogLight = Color(0xFFFBFAF7)

/** Canvas opacity: fully opaque - the backdrop is a quiet gradient, not something to see through. */
const val CanvasAlphaDark = 1f
const val CanvasAlphaLight = 1f

/** Rim light for the bar. */
val GlassSpecular = Color(0xFFFFFFFF)
val GlassRimTop = Color(0x1FFFFFFF)
val GlassRimBottom = Color(0x0AFFFFFF)
val GlassRimTopLight = Color(0x66FFFFFF)
val GlassRimBottomLight = Color(0x0F000000)

/** Retained for the legacy backdrop helper; the new backdrop uses a single ember glow. */
val GlassAccentBlue = Color(0xFF2F80ED)
val GlassAccentViolet = Color(0xFF7C4DFF)
val GlassAccentTeal = Color(0xFF00BFA5)

/**
 * Backdrop glow strength. Deliberately a whisper: it should register as "the page has a
 * temperature", not as a coloured shape behind the glass.
 */
const val BackdropBlobAlpha = 0.34f

/** Root gradient behind everything: ink with a single, barely-there warm wash at the top. */
val BackdropBrush: Brush = Brush.verticalGradient(
    listOf(
        Color(0xFF14100F),
        Color(0xFF0C0C0E),
        Color(0xFF0B0B0D),
    ),
)

/** Light-theme root gradient: warm paper, slightly darker at the bottom. */
val BackdropBrushLight: Brush = Brush.verticalGradient(
    listOf(
        Color(0xFFFBF8F3),
        Color(0xFFF7F4EF),
        Color(0xFFF2EEE7),
    ),
)

/** Soft elevation shadow tint used under floating surfaces. */
val GlassShadow = Color(0x40000000)

/** Hairline used between rows and around surfaces. */
val HairlineDark = Color(0xFF23232A)
val HairlineLight = Color(0xFFE6E0D6)
