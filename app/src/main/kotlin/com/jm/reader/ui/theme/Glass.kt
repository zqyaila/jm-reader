package com.jm.reader.ui.theme

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Surfaces.
 *
 * Design direction ("ink & ember"): **solid beats frosted**. The previous build wrapped every card,
 * chip and panel in a translucent pane with a 45° specular sheen and a coloured shadow. On a
 * screen full of cover art those panes fought the artwork and multiplied visual noise.
 *
 * What is left:
 *
 *  - [glassSurface] - a quiet, solid card: fill, 1dp hairline, very soft shadow. Used by every
 *    screen that previously asked for glass, so they all calm down together.
 *  - [liquidGlass] - the real frosted recipe, now used *only* by the floating bottom navigation,
 *    which is the one place where content genuinely scrolls underneath and the blur has a job.
 *
 * The contrast guarantee (WCAG AA over white/black artwork under the bar) is unchanged; see
 * GlassContrastTest.
 */
val GlassShape: Shape = RoundedCornerShape(GlassSizes.RadiusStandard)
val GlassShapeLarge: Shape = RoundedCornerShape(GlassSizes.RadiusHeavy)
val GlassShapeSmall: Shape = RoundedCornerShape(GlassSizes.RadiusInner)
val GlassCapsule: Shape = RoundedCornerShape(percent = 50)

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
fun backdropBrush(): Brush {
    val archive = LocalAppTheme.current == AppTheme.ARCHIVE
    val dark = isSystemInDarkTheme()
    return when {
        archive && dark -> ArchiveBackdropDark
        archive -> ArchiveBackdropLight
        dark -> EmberBackdropDark
        else -> EmberBackdropLight
    }
}

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
    val dark = isSystemInDarkTheme()
    // Archive fogs with its own paper/graphite surface, so the dock reads as a pane of the page;
    // ember keeps the near-white / near-black fog it was tuned against.
    val base = if (LocalAppTheme.current == AppTheme.ARCHIVE) {
        MaterialTheme.colorScheme.surface
    } else if (dark) {
        Color(0xFF121215)
    } else {
        Color(0xFFFBFAF7)
    }
    val alpha = when (kind) {
        GlassKind.Panel -> if (dark) GlassPanelFogAlphaDark else GlassPanelFogAlphaLight
        GlassKind.TopBar -> if (dark) GlassTopBarFogAlphaDark else GlassTopBarFogAlphaLight
        GlassKind.BottomBar -> if (dark) GlassBottomBarFogAlphaDark else GlassBottomBarFogAlphaLight
    }
    return base.copy(alpha = alpha)
}

@Composable
fun glassRimBrush(): Brush = if (isSystemInDarkTheme()) {
    Brush.verticalGradient(listOf(GlassRimTop, GlassRimBottom))
} else {
    Brush.verticalGradient(listOf(GlassRimTopLight, GlassRimBottomLight))
}

/** Hairline that separates two rows/surfaces without drawing a visible box. */
@Composable
fun hairlineColor(): Color = MaterialTheme.colorScheme.outlineVariant

// ---------------------------------------------------------------------------
// Backdrop sampling
// ---------------------------------------------------------------------------

/**
 * Background-only layer: draws the page backdrop at this node's position and blurs it. The node's
 * own content is never inside this layer, so it stays sharp.
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
// Core recipe (bottom bar)
// ---------------------------------------------------------------------------

/**
 * The frosted bar recipe: shadow -> sampled backdrop -> fog -> hairline rim.
 *
 * The diagonal specular sheen was removed on purpose. At the density the bar needs for legibility
 * it stopped reading as glass and started reading as a smudge over the covers, so what is left is
 * blur + fog + a single rim line.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = GlassShape,
    kind: GlassKind = GlassKind.Panel,
    backdropLayer: GraphicsLayer? = null,
    blurRadius: Dp = GlassBlurRadius,
    elevation: Dp = 16.dp,
    fog: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    val fogColor = fog ?: glassFogColor(kind)
    val rim = glassRimBrush()

    var base = this
    if (elevation > 0.dp) {
        val shadowTint = Color.Black.copy(alpha = 0.32f)
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

    return base.then(rimModifier)
}

// ---------------------------------------------------------------------------
// Public surfaces
// ---------------------------------------------------------------------------

/**
 * Card / panel.
 *
 * A solid fill plus a hairline and an almost invisible shadow. `tint` still wins when a screen
 * wants a tinted surface (for example an accent wash behind an icon).
 */
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
    return base
        .clip(shape)
        .background(fill)
        .border(borderWidth, border, shape)
}

/**
 * Clickable surface with the spec's press behaviour: the surface dips slightly on a damped spring
 * instead of flashing a ripple across artwork. Used for covers, rows and nav items.
 */
@Composable
fun Modifier.glassClickable(
    shape: Shape = GlassShape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    // Down is a quick 120 ms dip; up is a 420 ms settle with a small overshoot, which is what
    // reads as "jelly" rather than as a linear scale. Same numbers as Wenku8_Flutter.
    val progress by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = if (pressed && enabled) ArchiveMotion.PressDown else ArchiveMotion.PressUp,
        label = "press",
    )
    val scale = 1f - 0.015f * progress
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawWithContent {
            drawContent()
            if (progress > 0f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val clip = Path().apply { addOutline(outline) }
                clipPath(clip) {
                    drawRect(GLASS_PRESS_SHEEN.copy(alpha = 0.05f * progress))
                }
            }
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

private val GLASS_PRESS_SHEEN = Color.White

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
        modifier.glassSurface(shape = shape, tint = tint, elevation = 0.dp),
    ) {
        content()
    }
}

/**
 * Bar (top bar / bottom navigation). Full-bleed by default; pass a rounded [shape] plus outer
 * padding for the floating "pill" look.
 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    kind: GlassKind = GlassKind.TopBar,
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
                    kind = kind,
                    backdropLayer = backdropLayer,
                    fog = tint,
                ),
        )
        content()
    }
}

/**
 * Full-screen app background.
 *
 * Archive: flat paper with a 48 dp registration grid at 4.5 % ink - the printed-catalogue cue from
 * Wenku8_Flutter, and quiet enough that it never competes with a cover.
 * Ember: the previous vertical ink gradient plus one warm glow in the top corner.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    // Captured in composition: the inset extension is composable, the painter is not.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topPx = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(density).toFloat()
    val archive = LocalAppTheme.current == AppTheme.ARCHIVE
    Box(
        modifier
            .fillMaxSize()
            .background(backdropBrush())
            .drawBehind {
                if (archive) {
                    val topInset = topPx
                    // The reference bounds its diagram to the header margin: 240 dp of grid, two
                    // frosted file planes in the same raster, and a pair of registration crosses.
                    // Below that, the reading canvas stays plain paper.
                    val grid = if (dark) Color.White.copy(alpha = 0.035f) else Color(0xFF181818).copy(alpha = 0.045f)
                    val bottom = (240.dp.toPx() + topInset).coerceAtMost(size.height)
                    val filmWhite = if (dark) Color(0xFFF6F6F6) else Color(0xFFFEFDF8)
                    drawPath(
                        Path().apply {
                            moveTo(size.width * 0.42f, topInset)
                            lineTo(size.width, topInset)
                            lineTo(size.width, bottom * 0.82f)
                            lineTo(size.width * 0.78f, bottom * 0.82f)
                            close()
                        },
                        filmWhite.copy(alpha = if (dark) 0.018f else 0.42f),
                    )
                    drawPath(
                        Path().apply {
                            moveTo(size.width * 0.60f, topInset + 18.dp.toPx())
                            lineTo(size.width, topInset + 18.dp.toPx())
                            lineTo(size.width, bottom)
                            lineTo(size.width * 0.92f, bottom)
                            close()
                        },
                        filmWhite.copy(alpha = if (dark) 0.012f else 0.28f),
                    )
                    val step = 48.dp.toPx()
                    var x = 24.dp.toPx()
                    while (x < size.width) {
                        drawLine(grid, Offset(x, 0f), Offset(x, bottom), 0.5.dp.toPx())
                        x += step
                    }
                    var y = topInset + 4.dp.toPx()
                    while (y < bottom) {
                        drawLine(grid, Offset(24.dp.toPx(), y), Offset(size.width - 24.dp.toPx(), y), 0.5.dp.toPx())
                        y += step
                    }
                    val cross = (if (dark) Color(0xFFF6F6F6) else Color(0xFF181818)).copy(alpha = 0.15f)
                    val crossY = topInset + 60.dp.toPx()
                    listOf(24.dp.toPx(), size.width - 24.dp.toPx()).forEach { cx ->
                        drawLine(cross, Offset(cx - 3.dp.toPx(), crossY), Offset(cx + 3.dp.toPx(), crossY), 0.5.dp.toPx())
                        drawLine(cross, Offset(cx, crossY - 3.dp.toPx()), Offset(cx, crossY + 3.dp.toPx()), 0.5.dp.toPx())
                    }
                } else {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(
                                EmberAccentDark.copy(alpha = if (dark) 0.10f else 0.07f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * 0.82f, size.height * 0.02f),
                            radius = size.width * 1.05f,
                        ),
                    )
                }
            },
    )
}
