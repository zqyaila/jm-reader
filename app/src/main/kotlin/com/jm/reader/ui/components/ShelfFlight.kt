package com.jm.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.ui.theme.ArchiveMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Measurements the shelf-to-dossier flight has to agree on.
 *
 * The detail page's cover is the flight's destination, so both the page and the overlay read these
 * numbers instead of each keeping its own copy: a one-dp drift shows up as a visible jump at the
 * end of the transition.
 */
object ArchiveDetailLayout {
    val Gutter = 24.dp
    val BarHeight = 52.dp
    val CoverWidth = 112.dp
    val CoverHeight = 149.dp

    /** Space between the top bar and the cover plate. */
    val HeaderTop = 4.dp
}

/**
 * One cover in flight.
 *
 * [decodeWidthPx] is the width the shelf already decoded the file at. Coil keys its memory cache by
 * size, so asking for another width would decode the same JPEG a second time *during* the
 * transition - the one guaranteed way to make a 420 ms animation stutter.
 */
data class ShelfFlightRequest(
    val cover: String,
    val start: Rect,
    val decodeWidthPx: Int,
    val onArrive: () -> Unit,
)

/**
 * Hand-off between a shelf and the root overlay.
 *
 * The cover cannot fly inside the shelf: its row is clipped and it sits in a LazyColumn item that
 * may scroll away. The request is posted to a controller owned by the app root, which draws the
 * flight above the navigation host - the reference's Hero tag, without needing two screens to share
 * a composition.
 */
@Stable
class ShelfFlightController {
    var request by mutableStateOf<ShelfFlightRequest?>(null)
        private set

    /**
     * Set by the destination once its own cover plate has painted the same file.
     *
     * The flight holds its last frame until this flips (or a short timeout), so the hand-off cannot
     * flash the skeleton's grey plate between the flying copy and the page's copy.
     */
    var pageCoverReady by mutableStateOf(false)
        private set

    fun launch(request: ShelfFlightRequest) {
        // A second tap while the first cover is still in the air must not restart the flight.
        if (this.request != null) return
        pageCoverReady = false
        this.request = request
    }

    fun markPageCoverReady() {
        pageCoverReady = true
    }

    fun clear() {
        request = null
        pageCoverReady = false
    }
}

val LocalShelfFlight = staticCompositionLocalOf<ShelfFlightController?> { null }

/**
 * Full-screen cover flight, running on the same clock as the page exchange.
 *
 * The tapped cover travels to the exact rectangle the dossier's plate occupies while the incoming
 * page is cut in along its diagonal, both over [ArchiveMotion.PlaneMs] with the same easing. The
 * destination therefore arrives *with* the page rather than half a beat before it.
 *
 * The travelling rectangle is drawn by scaling one fixed-size image with a layer transform, so a
 * frame costs a matrix update - not a relayout and not a re-decode. The page's own 24 dp / 12 dp
 * entry shift is added to the transform, so the cover and the plate it is heading for move as one
 * body instead of drifting apart by up to 24 dp mid-flight.
 */
@Composable
fun ShelfFlightOverlay(controller: ShelfFlightController) {
    val request = controller.request ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()

    val target = remember(statusTop, density) {
        val left = with(density) { ArchiveDetailLayout.Gutter.toPx() }
        val top = statusTop + with(density) {
            (ArchiveDetailLayout.BarHeight + ArchiveDetailLayout.HeaderTop).toPx()
        }
        val width = with(density) { ArchiveDetailLayout.CoverWidth.toPx() }
        val height = with(density) { ArchiveDetailLayout.CoverHeight.toPx() }
        Rect(left = left, top = top, right = left + width, bottom = top + height)
    }

    val planeShiftX = with(density) { ArchiveMotion.PlaneShiftXDp.dp.toPx() }
    val planeShiftY = with(density) { ArchiveMotion.PlaneShiftYDp.dp.toPx() }

    val progress = remember(request) { Animatable(0f) }
    LaunchedEffect(request) {
        // Navigate on the first frame: the page exchange and the flight then start together, which
        // is what makes the two movements read as one. Waiting (the old 150 ms) left the cover
        // hanging over a page that had not begun to arrive.
        runCatching { request.onArrive() }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(ArchiveMotion.PlaneMs, easing = ArchiveMotion.Plane),
        )
        // Keep the landed frame until the page's plate reports the same bitmap. Capped, so a slow
        // network cannot pin an invisible duplicate above the page.
        withTimeoutOrNull(260) {
            snapshotFlow { controller.pageCoverReady }.first { it }
        }
        delay(40)
        controller.clear()
    }

    // One request for the whole flight, at the width the shelf already decoded.
    val model = remember(request.cover, request.decodeWidthPx) {
        ImageRequest.Builder(context)
            .data(request.cover)
            .size(request.decodeWidthPx, (request.decodeWidthPx * 1.4f).roundToInt())
            .build()
    }

    Box(
        Modifier
            .offset { IntOffset(target.left.roundToInt(), target.top.roundToInt()) }
            .size(ArchiveDetailLayout.CoverWidth, ArchiveDetailLayout.CoverHeight)
            .graphicsLayer {
                val p = progress.value
                // 0 = the shelf rectangle, 1 = this box. Interpolating scale plus translation keeps
                // the drawn size fixed for layout; only the layer matrix changes per frame.
                val sx = (request.start.width / size.width).coerceAtLeast(0.01f)
                val sy = (request.start.height / size.height).coerceAtLeast(0.01f)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = 1f + (sx - 1f) * (1f - p)
                scaleY = 1f + (sy - 1f) * (1f - p)
                // Interpolate towards the plate's *moving* position: the page is still settling
                // from its 24 dp / 12 dp entry shift, so the landing point is target + shift * (1-p).
                // Using the shift directly would take the cover 24 dp to the left of the shelf slot
                // on the very first frame.
                val settle = (1f - p) * p
                translationX = (request.start.left - target.left) * (1f - p) + planeShiftX * settle
                translationY = (request.start.top - target.top) * (1f - p) + planeShiftY * settle
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
