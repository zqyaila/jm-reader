package com.jm.reader.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Two palettes.
//
// ARCHIVE (default) - modelled on the Wenku8_Flutter reading archive: warm paper, black ink, no
// accent colour at all. Corners are near-square (3 dp covers, 4 dp panels) and hierarchy is carried
// by weight, hairlines and tiny monospace codes. The only saturated thing on screen is cover art.
//
//   light: paper #F4F3EF canvas / #FDFCFA panel / #FEFDF8 milk  ink #181818, muted #646B62
//   dark : ink   #191919 canvas / #242424 panel / #2D2D2D raised text #F2F2F2, muted #B7B7B7
//
// EMBER (the previous look, kept as a choice) - warm paper / near-black ink with a single ember
// accent, 12/16/24 dp rounded surfaces.
//
// ---------------------------------------------------------------------------
// Archive palette
// ---------------------------------------------------------------------------

/** Primary ink for text, icons and filled controls in the light theme. */
val Ink = Color(0xFF181818)

/** Secondary text: 7.4:1 on paper. */
val InkMuted = Color(0xFF646B62)

val PaperBg = Color(0xFFF4F3EF)
val PaperSurface = Color(0xFFFDFCFA)
val PaperMilk = Color(0xFFFEFDF8)
val PaperRaised = Color(0xFFECEAE4)

val DarkBg = Color(0xFF191919)
val DarkSurface = Color(0xFF242424)
val DarkSurfaceVariant = Color(0xFF2D2D2D)
val DarkOutline = Color(0xFF3C3C3C)

val LightBg = PaperBg
val LightSurface = PaperSurface
val LightSurfaceVariant = PaperRaised
val LightOutline = Color(0xFFB9BDB4)

val TextPrimaryDark = Color(0xFFF2F2F2)
val TextSecondaryDark = Color(0xFFB7B7B7)
val TextPrimaryLight = Ink
val TextSecondaryLight = InkMuted

// ---------------------------------------------------------------------------
// Ember palette (the previous theme, selectable)
// ---------------------------------------------------------------------------

val EmberAccentDark = Color(0xFFFF8A4C)
val EmberAccentSoft = Color(0xFFFFB37A)
val EmberAccentDeep = Color(0xFFC2410C)

val EmberBgDark = Color(0xFF0C0C0E)
val EmberSurfaceDark = Color(0xFF16161A)
val EmberRaisedDark = Color(0xFF1D1D22)
val EmberOutlineDark = Color(0xFF2E2E35)

val EmberBgLight = Color(0xFFF7F4EF)
val EmberSurfaceLight = Color(0xFFFFFFFF)
val EmberRaisedLight = Color(0xFFF0EBE3)
val EmberOutlineLight = Color(0xFFE2DCD2)
val EmberTextDark = Color(0xFFF4F1EC)
val EmberTextMutedDark = Color(0xFFA29C93)
val EmberTextLight = Color(0xFF17130F)
val EmberTextMutedLight = Color(0xFF6B645B)

/** The single semantic colour: used only where "something is wrong", never for emphasis. */
val AdFreeAccent = Color(0xFF2E7D32)

// ---------------------------------------------------------------------------
// Glass tokens
//
// Glass survives only in the floating bottom dock. Its fog is milk rather than white in the archive
// theme, so it reads as a pane of the same paper; ember keeps its near-white / graphite fog.
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

/** Fog strength per surface kind. The dock is nearly opaque: it floats over cover art mid-scroll. */
const val GlassBottomBarFogAlphaDark = 0.94f
const val GlassBottomBarFogAlphaLight = 0.97f
const val GlassTopBarFogAlphaDark = 0.88f
const val GlassTopBarFogAlphaLight = 0.94f
const val GlassPanelFogAlphaDark = 0.45f
const val GlassPanelFogAlphaLight = 0.52f

/** Canvas opacity: fully opaque - the backdrop is paper, not something to see through. */
const val CanvasAlphaDark = 1f
const val CanvasAlphaLight = 1f

/** Rim light for the dock: a hairline, not a sheen. */
val GlassSpecular = Color(0xFFFFFFFF)
val GlassRimTop = Color(0x1FFFFFFF)
val GlassRimBottom = Color(0x0AFFFFFF)
val GlassRimTopLight = Color(0x66000000)
val GlassRimBottomLight = Color(0x0F000000)

/** Retained for the legacy backdrop helper; the archive backdrop draws no coloured shapes. */
val GlassAccentBlue = Color(0xFF2F80ED)
val GlassAccentViolet = Color(0xFF7C4DFF)
val GlassAccentTeal = Color(0xFF00BFA5)

/** Grid / registration strength on the paper. A whisper, not a pattern. */
const val BackdropBlobAlpha = 0.34f

/** Archive root canvas: flat paper (light) / graphite (dark). No gradient, no glow. */
val ArchiveBackdropDark: Brush = Brush.verticalGradient(listOf(DarkBg, DarkBg))
val ArchiveBackdropLight: Brush = Brush.verticalGradient(listOf(PaperBg, PaperBg))

/** Ember root canvas: warm gradient with a whisper of warmth at the top. */
val EmberBackdropDark: Brush = Brush.verticalGradient(
    listOf(Color(0xFF14100F), Color(0xFF0C0C0E), Color(0xFF0B0B0D)),
)
val EmberBackdropLight: Brush = Brush.verticalGradient(
    listOf(Color(0xFFFBF8F3), Color(0xFFF7F4EF), Color(0xFFF2EEE7)),
)

/** Soft elevation shadow tint used under floating surfaces. */
val GlassShadow = Color(0x40000000)

/**
 * Fog bases for the glass bar. Archive fogs with its own paper/graphite surface, so these are the
 * same values [glassFogColor] uses at runtime; kept as named tokens for the contrast tests.
 */
val GlassFogDark = DarkSurface
val GlassFogLight = PaperMilk

/** The previous accent, kept so the contrast tests still have a "brightest backdrop" colour. */
val BrandOrange = Ink
val BrandOrangeSoft = Ink
val BrandOrangeDeep = Ink
val BrandOrangeDark = Ink

/** Hairline used between rows and around surfaces. */
val HairlineDark = Color(0xFF3C3C3C)
val HairlineLight = Color(0xFFD8DBD5)
