package com.jm.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid glass, implemented after the **skill-liquid-glass** design spec
 * (https://github.com/JUEMING-006/skill-liquid-glass, built on Kyant0/AndroidLiquidGlass):
 *
 * | token | value |
 * |---|---|
 * | blur | 2 dp (low blur keeps the content readable) |
 * | lens | 12 dp / 24 dp refraction + chromatic aberration |
 * | fog | neutral, light `FAFAFA` / dark `121212` |
 * | highlight | 0.15 specular sheen |
 * | shadow | 0.08 drop shadow |
 * | innerShadow | 4 dp / 0.1 rim |
 *
 * The library itself is a Compose Multiplatform artifact published against Kotlin 2.4.10 +
 * Compose 1.12; this project is on Kotlin 2.2 / Compose 1.8, so instead of upgrading the whole
 * toolchain the same recipe is built from Compose primitives:
 *
 *  1. **Real backdrop sampling** - the page content is recorded once into a [GraphicsLayer]
 *     ([PageBackdropHost]). A glass node that lives *outside* that recording draws the layer,
 *     offset by its own position, into a background-only sub-layer and blurs **that** ([sampled]).
 *     That is genuine "blur what is behind me", without the `Modifier.blur` trap of also blurring
 *     the node's own content.
 *  2. **Fog** - the neutral tint from the spec, raised for contrast (see Color.kt).
 *  3. **Specular highlight** - the diagonal sheen that makes a pane read as glass.
 *  4. **Rim + shadow** - hairline rim light plus a soft drop shadow for lift.
 *
 * Refraction (`lens`) needs an AGSL RuntimeShader, which the library drives through its own
 * `BackdropEffectScope`; here it is approximated by the rim light plus the 1 px inner shading.
 */
val GlassShape: Shape = RoundedCornerShape(20.dp)
val GlassShapeLarge: Shape = RoundedCornerShape(28.dp)
val GlassShapeSmall: Shape = RoundedCornerShape(14.dp)

/** True when the platform can blur at all (RenderEffect, API 31+). */
val supportsBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Spec: 2 dp, low on purpose. */
val GlassBlurRadius: Dp = GlassBlurDp.dp

fun Modifier.platformBlur(radius: Dp, unbounded: Boolean = true): Modifier =
    if (supportsBlur && radius > 0.dp) {
        blur(
            radius,
            if (unbounded) BlurredEdgeTreatment.Unbounded else BlurredEdgeTreatment.Rectangle,
        )
    } else {
        this
    }

// ---------------------------------------------------------------------------
// Theme-aware helpers
// ---------------------------------------------------------------------------

@Composable
fun backdropBrush(): Brush = if (isSystemInDarkTheme()) BackdropBrush else BackdropBrushLight

@Composable
fun glassFogColor(forBar: Boolean = false): Color {
    val dark = isSystemInDarkTheme()
    val base = if (dark) GlassFogDark else GlassFogLight
    val alpha = when {
        dark && forBar -> GlassBarFogAlphaDark
        dark -> GlassPanelFogAlphaDark
        forBar -> GlassBarFogAlphaLight
        else -> GlassPanelFogAlphaLight
    }
    return base.copy(alpha = alpha)
}

@Composable
fun glassRimBrush(): Brush = if (isSystemInDarkTheme()) {
    Brush.verticalGradient(listOf(GlassRimTop, GlassRimBottom))
} else {
    Brush.verticalGradient(listOf(GlassRimTopLight, GlassRimBottomLight))
}

// ---------------------------------------------------------------------------
// Backdrop sampling
// ---------------------------------------------------------------------------

/**
 * Background-only layer: draws the page backdrop at this node's position and blurs it. The node's
 * own content is never inside this layer, so it stays sharp.
 *
 * The caller must own a layer that records the page *and must not itself live inside that
 * recording* - sampling your own recording feeds back on itself (the library warns about the same
 * hazard for `layerBackdrop`). `MainScreen` is the only user: it records the tab content and puts
 * the navigation pill outside the recording.
 */
@Composable
private fun Modifier.sampledBackdrop(layer: GraphicsLayer, blurRadius: Dp): Modifier {
    var offset by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { offset = it.positionInRoot() }
        // Clip the blur so the pane's edges do not smear outside its rounded shape.
        .platformBlur(blurRadius, unbounded = false)
        .drawBehind {
            translate(-offset.x, -offset.y) { drawLayer(layer) }
        }
}

// ---------------------------------------------------------------------------
// Core recipe
// ---------------------------------------------------------------------------

/**
 * The glass surface itself: shadow -> [sampled backdrop] -> fog -> specular -> rim.
 *
 * @param backdropLayer when non-null the pane samples the live page behind it (use for overlays
 *        placed outside [PageBackdropHost]); when null it is a plain fog pane (use inside the page).
 * @param fog overrides the theme fog (used for tinted surfaces such as the home quick links).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassShape,
    forBar: Boolean = false,
    backdropLayer: GraphicsLayer? = null,
    blurRadius: Dp = GlassBlurRadius,
    elevation: Dp = 16.dp,
    fog: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    val fogColor = fog ?: glassFogColor(forBar)
    val rim = glassRimBrush()

    var base = this
    if (elevation > 0.dp) {
        val shadowTint = Color.Black.copy(alpha = GlassShadowAlpha)
        base = base.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowTint,
            spotColor = shadowTint,
        )
    }
    base = base.clip(shape)

    base = if (backdropLayer != null) {
        base
            .sampledBackdrop(backdropLayer, blurRadius)
            .drawBehind { drawRect(fogColor) }
    } else {
        base.background(fogColor)
    }

    val rimModifier = if (borderColor != null) {
        Modifier.border(borderWidth, borderColor, shape)
    } else {
        Modifier.border(borderWidth, rim, shape)
    }

    return base
        .drawBehind {
            // Specular highlight: a diagonal sheen, the signature "pane of glass" cue.
            drawRect(
                Brush.linearGradient(
                    colors = listOf(
                        GlassSpecular.copy(alpha = GlassHighlightAlpha),
                        GlassSpecular.copy(alpha = GlassHighlightAlpha * 0.25f),
                        Color.Transparent,
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.85f, size.height * 1.15f),
                ),
            )
        }
        // Rim light, standing in for the spec's 4 dp inner shadow.
        .then(rimModifier)
}

// ---------------------------------------------------------------------------
// Public surfaces
// ---------------------------------------------------------------------------

/** Panel / card. Fog only: it lives inside the page, so it must not sample the page layer. */
@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassShape,
    tint: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier = liquidGlass(
    shape = shape,
    elevation = 10.dp,
    fog = tint,
    borderColor = borderColor,
    borderWidth = borderWidth,
)

/** Panel container. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    tint: Color? = null,
    blurRadius: Dp = GlassBlurRadius,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.liquidGlass(
            shape = shape,
            blurRadius = blurRadius,
            fog = tint,
        ),
    ) {
        content()
    }
}

/**
 * Bar (top bar / bottom navigation). Full-bleed by default; pass a rounded [shape] plus outer
 * padding for the floating "pill" look.
 *
 * [backdropLayer] is optional on purpose: pass `LocalPageBackdrop.current` when the bar lives
 * outside [PageBackdropHost] to get the real blurred backdrop behind it.
 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color? = null,
    backdropLayer: GraphicsLayer? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .liquidGlass(
                    shape = shape,
                    forBar = true,
                    backdropLayer = backdropLayer,
                    fog = tint,
                ),
        )
        content()
    }
}

/** Full-screen app background: gradient plus soft colour blobs for the glass to refract. */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(backdropBrush())) {
        Box(
            Modifier
                .size(360.dp)
                .offset(x = (-90).dp, y = (-60).dp)
                .platformBlur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(BrandOrange.copy(alpha = BackdropBlobAlpha), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(340.dp)
                .offset(x = 70.dp, y = 100.dp)
                .platformBlur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(GlassAccentBlue.copy(alpha = BackdropBlobAlpha), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(280.dp)
                .offset(x = 130.dp, y = (-40).dp)
                .platformBlur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(GlassAccentViolet.copy(alpha = BackdropBlobAlpha * 0.85f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(240.dp)
                .offset(x = (-60).dp, y = 60.dp)
                .platformBlur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(GlassAccentTeal.copy(alpha = BackdropBlobAlpha * 0.7f), Color.Transparent),
                    ),
                ),
        )
    }
}
