package com.jm.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
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

/** One cover in flight: where it starts, what to show, and what to do when it lands. */
data class ShelfFlightRequest(
    val cover: String,
    val start: Rect,
    val onArrive: () -> Unit,
)

/**
 * Hand-off between a shelf and the root overlay.
 *
 * The cover cannot fly inside the shelf: its row is clipped, and it is nested in a LazyColumn item
 * that may scroll away. The request is therefore posted to a controller owned by the app root,
 * which draws the flight above the navigation host - the same trick the reference uses with a Hero
 * tag, without needing the two screens to share a composition.
 */
@Stable
class ShelfFlightController {
    var request by mutableStateOf<ShelfFlightRequest?>(null)
        private set

    fun launch(request: ShelfFlightRequest) {
        this.request = request
    }

    fun clear() {
        request = null
    }
}

val LocalShelfFlight = staticCompositionLocalOf<ShelfFlightController?> { null }

/**
 * Full-screen cover flight.
 *
 * The pill flies from the tapped book to the exact rectangle the detail page's cover occupies, so
 * when the page fades in underneath, the cover is already in place. Navigation is triggered a
 * little before the landing, which is what makes the two look like one movement rather than a
 * flight followed by a page change.
 */
@Composable
fun ShelfFlightOverlay(controller: ShelfFlightController) {
    val request = controller.request ?: return
    val context = LocalContext.current
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()

    val target = remember(statusTop, density) {
        val left = with(density) { ArchiveDetailLayout.Gutter.toPx() }
        val top = statusTop + with(density) { (ArchiveDetailLayout.BarHeight + ArchiveDetailLayout.HeaderTop).toPx() }
        val width = with(density) { ArchiveDetailLayout.CoverWidth.toPx() }
        val height = with(density) { ArchiveDetailLayout.CoverHeight.toPx() }
        Rect(left = left, top = top, right = left + width, bottom = top + height)
    }

    val progress = remember(request) { Animatable(0f) }
    LaunchedEffect(request) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 300,
                easing = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1f),
            ),
        )
        controller.clear()
    }
    // Arrive before the flight ends: the detail page fades in behind the still-moving cover.
    LaunchedEffect(request) {
        delay(150)
        runCatching { request.onArrive() }
    }

    // One request for the whole flight: rebuilding it per frame would restart the Coil load on
    // every animation tick.
    val model = remember(request.cover) {
        ImageRequest.Builder(context)
            .data(request.cover)
            .size(512)
            .build()
    }

    val t = progress.value
    val rect = Rect(
        left = request.start.left + (target.left - request.start.left) * t,
        top = request.start.top + (target.top - request.start.top) * t,
        right = request.start.right + (target.right - request.start.right) * t,
        bottom = request.start.bottom + (target.bottom - request.start.bottom) * t,
    )

    Box(Modifier.fillMaxSize()) {
        // The shelf dims while the book travels; the scrim lifts as the cover nears its slot so it
        // does not wash out the page that is fading in underneath.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * (1f - t).coerceIn(0f, 1f))),
        )
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(
                    width = with(density) { rect.width.toDp() },
                    height = with(density) { rect.height.toDp() },
                ),
        )
    }
}
