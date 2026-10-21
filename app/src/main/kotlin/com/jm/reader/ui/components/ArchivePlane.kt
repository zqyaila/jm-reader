package com.jm.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jm.reader.ui.theme.ArchiveMotion
import kotlin.math.min

/**
 * How far the leading edge has travelled: the page width plus the slant, times progress.
 *
 * Split out so the "does it actually cover the page at 1.0" property can be asserted without a
 * device - a previous transition bug was exactly this kind of unit mistake.
 */
internal fun planeEdge(progress: Float, width: Float, slant: Float): Float =
    (width + slant) * progress.coerceIn(0f, 1f)

/** Lean of the cut edge in pixels, capped so a tablet does not get a 45-degree wall. */
internal fun planeSlant(width: Float, slantMax: Float): Float =
    min(slantMax, width * ArchiveMotion.PlaneSlantFraction)

/**
 * The reference's `ArchivePlaneTransition`, for a page the navigation graph cannot animate.
 *
 * Compose's `EnterTransition` only offers fade / slide / scale, so a route change cannot be cut in
 * along a diagonal. The page therefore draws its own exchange: a shallow trapezoid sweeps across
 * from the left while the whole sheet slides 24 dp / 12 dp into place, on the reference's
 * `Cubic(.18, .72, .18, 1)`.
 *
 * Two deliberate differences from a cross-fade:
 *
 *  - nothing is made translucent. The incoming page stays fully opaque and is *clipped*, so the
 *    outgoing page underneath is covered by paper instead of showing through it. Opaque text on an
 *    opaque background stays crisp at every frame, and there is no double-alpha compositing;
 *  - the clip and the translation both run after layout. The page is measured and placed once; the
 *    420 ms only repaints, which is what keeps a heavy screen (a cover, a forty-volume shelf behind
 *    it) from dropping frames during the exchange.
 */
@Composable
fun ArchivePlaneEnter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val enabled = rememberMotionEnabled()
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(progress) {
        if (enabled) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(ArchiveMotion.PlaneMs, easing = ArchiveMotion.Plane),
            )
        }
    }

    // Resolved once: allocating these inside the draw lambda would be the only per-frame cost of
    // an otherwise allocation-free repaint.
    val shiftX = with(density) { ArchiveMotion.PlaneShiftXDp.dp.toPx() }
    val shiftY = with(density) { ArchiveMotion.PlaneShiftYDp.dp.toPx() }
    val slantMax = with(density) { ArchiveMotion.PlaneSlantMaxDp.dp.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                translationX = shiftX * (1f - p)
                translationY = shiftY * (1f - p)
            }
            .drawWithContent {
                val p = progress.value
                if (p >= 1f) {
                    drawContent()
                    return@drawWithContent
                }
                if (p <= 0f) return@drawWithContent
                val slant = planeSlant(size.width, slantMax)
                val edge = planeEdge(p, size.width, slant)
                val cut = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(edge, 0f)
                    lineTo(edge - slant, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(cut) { this@drawWithContent.drawContent() }
            },
    ) {
        content()
    }
}
