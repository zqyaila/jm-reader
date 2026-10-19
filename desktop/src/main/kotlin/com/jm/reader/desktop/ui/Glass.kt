package com.jm.reader.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Liquid glass" for the desktop client.
 *
 * The Android build gets a true backdrop blur by recording the page into a `GraphicsLayer` and
 * sampling it from a sibling overlay. On the desktop the same thing is *possible* but not worth
 * the complexity here, so this is the design's **fog-only** tier — the same one Android falls
 * back to below API 31:
 *
 *   * a neutral translucent fog whose density depends on what the surface sits over,
 *   * the 45° specular sheen that makes a pane read as glass,
 *   * a hairline rim light plus a soft drop shadow for lift,
 *   * damped-spring press feedback (a slight dip + brighter sheen) instead of a ripple.
 *
 * The backdrop *blobs* behind the app are still blurred (they are decoration, so blurring them
 * costs nothing and is what gives the glass something to refract).
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

object GlassSpec {
    const val HighlightAlpha = 0.15f
    const val HighlightAngle = 45f
    const val ShadowAlpha = 0.08f

    const val BottomBarFogAlphaDark = 0.92f
    const val BottomBarFogAlphaLight = 0.94f
    const val TopBarFogAlphaDark = 0.86f
    const val TopBarFogAlphaLight = 0.90f
    const val PanelFogAlphaDark = 0.45f
    const val PanelFogAlphaLight = 0.52f

    val FogDark = Color(0xFF0A0A0A)
    val FogLight = Color(0xFFFAFAFA)

    val Specular = Color(0xFFFFFFFF)
    val RimTop = Color(0x59FFFFFF)
    val RimBottom = Color(0x0DFFFFFF)
    val RimTopLight = Color(0x99FFFFFF)
    val RimBottomLight = Color(0x14000000)

    val Shadow = Color(0x40000000)
}

val GlassShape: Shape = RoundedCornerShape(AppSizes.Radius)
val GlassShapeLarge: Shape = RoundedCornerShape(AppSizes.RadiusLarge)
val GlassShapeInner: Shape = RoundedCornerShape(AppSizes.RadiusInner)
val GlassCapsule: Shape = RoundedCornerShape(percent = 50)

/** Which kind of glass surface, i.e. how dense its fog has to be for legibility. */
enum class GlassKind {
    /** Cards, list rows, form panels: always over the app canvas. */
    Panel,

    /** Top bar: over the canvas, never under scrolling content. */
    TopBar,

    /** Bottom navigation: content scrolls underneath, so it needs the densest fog. */
    BottomBar,
}

@Composable
fun glassFogColor(kind: GlassKind = GlassKind.Panel): Color {
    val dark = LocalDarkTheme.current
    val base = if (dark) GlassSpec.FogDark else GlassSpec.FogLight
    val alpha = when (kind) {
        GlassKind.Panel -> if (dark) GlassSpec.PanelFogAlphaDark else GlassSpec.PanelFogAlphaLight
        GlassKind.TopBar -> if (dark) GlassSpec.TopBarFogAlphaDark else GlassSpec.TopBarFogAlphaLight
        GlassKind.BottomBar -> if (dark) GlassSpec.BottomBarFogAlphaDark else GlassSpec.BottomBarFogAlphaLight
    }
    return base.copy(alpha = alpha)
}

@Composable
fun glassRimBrush(): Brush = if (LocalDarkTheme.current) {
    Brush.verticalGradient(listOf(GlassSpec.RimTop, GlassSpec.RimBottom))
} else {
    Brush.verticalGradient(listOf(GlassSpec.RimTopLight, GlassSpec.RimBottomLight))
}

/** Blurs a node's own content — used only for the decorative backdrop blobs, never for a pane. */
fun Modifier.backdropBlur(radius: Dp): Modifier =
    if (radius > 0.dp) blur(radius, BlurredEdgeTreatment.Unbounded) else this

/**
 * The glass surface itself: shadow -> fog -> specular sheen -> rim light.
 *
 * @param fog overrides the theme fog (used for tinted surfaces such as the home quick links).
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassShape,
    kind: GlassKind = GlassKind.Panel,
    elevation: Dp = 12.dp,
    fog: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    val fogColor = fog ?: glassFogColor(kind)
    val rim = glassRimBrush()

    var base = this
    if (elevation > 0.dp) {
        val shadowTint = Color.Black.copy(alpha = 0.28f)
        base = base.shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowTint,
            spotColor = shadowTint,
        )
    }
    base = base.clip(shape).background(fogColor)

    val rimModifier = if (borderColor != null) {
        Modifier.border(borderWidth, borderColor, shape)
    } else {
        Modifier.border(borderWidth, rim, shape)
    }

    // The 45° specular sheen was removed with the redesign: at the density the bar needs for
    // legibility it read as a smudge over the covers rather than as glass.
    return base.then(rimModifier)
}

/** Panel / card: fog + sheen + rim, with the standard elevation. */
@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassShape,
    tint: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 8.dp,
): Modifier {
    val fill = tint ?: MaterialTheme.colorScheme.surface
    val border = borderColor ?: MaterialTheme.colorScheme.outlineVariant
    var base = this
    if (elevation > 0.dp) {
        base = base.shadow(
            elevation = elevation.coerceAtMost(10.dp) * 0.5f,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.16f),
            spotColor = Color.Black.copy(alpha = 0.16f),
        )
    }
    return base.clip(shape).background(fill).border(borderWidth, border, shape)
}

/**
 * Clickable glass with the spec's press behaviour: the surface dips and its highlight brightens,
 * both on damped springs instead of a ripple that would fight the material it is drawn on.
 */
@Composable
fun Modifier.glassClickable(
    shape: Shape = GlassShape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        label = "glassPress",
    )
    // A fixed 1.5% reads the same across the app's very different surface sizes.
    val scale = 1f - 0.015f * progress
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (progress > 0f) {
                // Clipped to [shape] *inside* the draw call rather than with `Modifier.clip`, so
                // the drop shadow painted outside the bounds survives.
                val outline = shape.createOutline(size, layoutDirection, this)
                val clip = Path().apply { addOutline(outline) }
                clipPath(clip) {
                    drawRect(GlassSpec.Specular.copy(alpha = 0.10f * progress))
                }
            }
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

/** Panel container. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    tint: Color? = null,
    elevation: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.liquidGlass(shape = shape, kind = GlassKind.Panel, elevation = elevation, fog = tint)) {
        content()
    }
}

/** Bar (top bar / bottom navigation). Full-bleed by default; pass a rounded [shape] for a pill. */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    kind: GlassKind = GlassKind.TopBar,
    tint: Color? = null,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .liquidGlass(shape = shape, kind = kind, elevation = elevation, fog = tint),
        )
        content()
    }
}

/**
 * Full-window background: one vertical ink gradient plus a single warm glow in the top-right
 * corner, at ~10% opacity. The old version painted four blurred colour blobs, which on a large
 * window turned into visible coloured haze around the content.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val dark = LocalDarkTheme.current
    Box(
        modifier
            .fillMaxSize()
            .background(if (dark) BackdropBrushDark else BackdropBrushLight)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            BrandOrange.copy(alpha = if (dark) 0.07f else 0f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.82f, size.height * 0.02f),
                        radius = size.width * 0.95f,
                    ),
                )
            },
    )
}
