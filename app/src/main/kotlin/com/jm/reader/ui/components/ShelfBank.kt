package com.jm.reader.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.ui.theme.CodeTextStyle
import com.jm.reader.ui.theme.glassClickable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** One book as a shelf needs it, independent of which tab it came from. */
data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val cover: String,
)

/**
 * The archive bank, ported one-to-one from Wenku8_Flutter's `ArchiveWave`.
 *
 * Nothing here is a rotated cover: every book is a *solid* - spine face, cover face and the top
 * face that joins them - projected with the reference's own numbers. The projection is parallel,
 * so a book never changes size as it travels:
 *
 *     cover face:  x' = 0.66x            y' = -0.30x + y
 *     spine face:  x' = x                y' =  0.10x + y
 *
 * The centre book is pulled out of the row by `pull = 0.84·cover · w · (1.24 − 0.24w)` and lifted
 * by `0.60·height · smoothstep(w)`, where `w = cos²(|d| / 1.48 · π/2)` fades to zero at ±1.48
 * books. Neighbours sit lower by `railY(d) = 2.5d + 0.45d²`. That is what makes the row read as a
 * physical bank with one file extracted, rather than as a carousel of album art.
 *
 * Scrolling is one continuous `focus` measured in books: a drag divides pixels by `pitch`
 * (spine + 11 dp), a fling decays then settles on the nearest book with a light detent on each
 * crossing. Continuous values are read inside layout/draw lambdas, so a drag never recomposes the
 * covers.
 */
@Composable
fun ShelfBank(
    items: List<ShelfItem>,
    onOpen: (ShelfItem) -> Unit,
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = false,
) {
    if (items.isEmpty()) return
    val count = items.size
    val motion = rememberMotionEnabled()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reduceMotion = !motion

    var focus by remember { mutableFloatStateOf(0f) }
    // The lane window is driven by this integer, not by a derived read of the float. It is written
    // from the drag callback itself, so a drag that crosses the tenth book mounts the eleventh lane
    // immediately - a `derivedStateOf` on the float only refreshed after the gesture settled, which
    // made the bank look like it stopped at the edge until the finger came up.
    var laneAnchor by remember { mutableIntStateOf(0) }
    // The cover lifts for 160 ms before the route slides in, so the tap reads as "taking the book
    // off the shelf" rather than as the page swapping under the finger.
    var openingSlot by remember { mutableStateOf<Int?>(null) }
    val opening = remember { Animatable(0f) }
    // Guards a second tap while the cover is already in the air.
    var flying by remember { mutableStateOf(false) }
    val flight = LocalShelfFlight.current
    var flingJob by remember { mutableStateOf<Job?>(null) }
    var lastDetent by remember { mutableIntStateOf(0) }

    fun setFocus(value: Float) {
        focus = value
        val lane = value.roundToInt()
        if (lane != laneAnchor) laneAnchor = lane
    }

    // The reference's 720 ms assembly: books slide in from depth along the projection axis, in
    // reading order, while their centre weight arrives later (interval .38 → 1).
    val entrance = remember { Animatable(if (animateEntrance && motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateEntrance && motion) {
            entrance.animateTo(1f, tween(720, easing = LinearEasing))
        }
    }

    /**
     * Pulls an unbounded focus back into `0 until count` once a gesture has finished.
     *
     * The bank is a loop, so `focus = focus ± count` is the same picture: every lane maps back
     * through `floorMod`. Re-seating it keeps the float from drifting (a long fling can cross
     * hundreds of books) and keeps the lane window near zero, which is what makes the loop feel
     * endless instead of running out of precision or slots.
     */
    fun reseat() {
        if (count <= 0) return
        val wrapped = shelfWrapFocus(focus, count)
        if (abs(wrapped - focus) > 0.0001f) {
            setFocus(wrapped)
            lastDetent = wrapped.roundToInt()
        }
    }

    fun settle(velocityDp: Float, pitchDp: Float) {
        if (count < 2) return
        flingJob?.cancel()
        flingJob = scope.launch {
            if (reduceMotion || abs(velocityDp) < 60f) {
                Animatable(focus).animateTo(
                    targetValue = focus.roundToInt().toFloat(),
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 320f),
                ) { setFocus(value) }
                reseat()
                return@launch
            }
            val anim = Animatable(focus)
            anim.animateDecay(
                initialVelocity = velocityDp / pitchDp,
                animationSpec = exponentialDecay(frictionMultiplier = 1.35f),
            ) { setFocus(value) }
            anim.animateTo(
                targetValue = anim.value.roundToInt().toFloat(),
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f),
            ) { setFocus(value) }
            reseat()
        }
    }

    fun step(delta: Int) {
        if (count < 2) return
        flingJob?.cancel()
        val target = (focus + 0.5f).toInt() + delta
        flingJob = scope.launch {
            Animatable(focus).animateTo(
                targetValue = target.toFloat(),
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.2f, 1.06f, 0.35f, 1f),
                ),
            ) { setFocus(value) }
            reseat()
        }
    }

    val focusedIndex = Math.floorMod(laneAnchor, count)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val viewport = min(maxWidth.value, 520f)
            val spine = shelfSpine(viewport)
            val pitch = shelfPitch(viewport)
            val cover = shelfCover(viewport)
            val body = shelfBodyHeight(cover)
            val rowHeight = shelfRowHeight(cover, body)
            val density = LocalDensity.current

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight.dp)
                        .clipToBounds(),
                ) {
                    ShelfScene(
                        items = items,
                        focus = { focus },
                        laneAnchor = laneAnchor,
                        entrance = { entrance.value },
                        viewport = viewport,
                        spine = spine,
                        cover = cover,
                        body = body,
                        openingSlot = openingSlot,
                        opening = { opening.value },
                        onTapSlot = { slot, bounds ->
                            if (abs(slot - focus) < 0.35f) {
                                val book = items[Math.floorMod(slot, count)]
                                when {
                                    // Preferred: hand the cover to the root overlay, which flies it
                                    // to the dossier's cover slot while the page fades in beneath.
                                    flight != null && book.cover.isNotBlank() && bounds.width > 0f &&
                                        !flying -> {
                                        flying = true
                                        flight.launch(
                                            ShelfFlightRequest(book.cover, bounds) {
                                                flying = false
                                                onOpen(book)
                                            },
                                        )
                                    }
                                    // No overlay (preview, or a cover that never loaded): fall back
                                    // to the short lift so the tap still reads as taking the book.
                                    openingSlot == null -> {
                                        openingSlot = slot
                                        scope.launch {
                                            opening.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(
                                                    durationMillis = 160,
                                                    easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f),
                                                ),
                                            )
                                            // Reset before navigating: the composition may be kept
                                            // alive by the back stack, and a stale lift would leave
                                            // the book scaled up when the user comes back.
                                            openingSlot = null
                                            opening.snapTo(0f)
                                            onOpen(book)
                                        }
                                    }
                                }
                            } else {
                                step((slot - focus).roundToInt())
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    if (count < 2) return@rememberDraggableState
                                    flingJob?.cancel()
                                    setFocus(focus - delta / (pitch * density.density))
                                    val lane = focus.roundToInt()
                                    if (lane != lastDetent && abs(focus - lane) < 0.12f) {
                                        lastDetent = lane
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onDragStopped = { velocity ->
                                    settle(-velocity, pitch * density.density)
                                },
                            ),
                    )
                }

                // The extracted file's label and address; tapping opens it, exactly like the
                // reference's `archive-shelf-open` row.
                val book = items[focusedIndex]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(top = 6.dp)
                        .glassClickable(shape = MaterialTheme.shapes.extraSmall) {
                            onOpen(book)
                        }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            book.title,
                            fontSize = 17.sp,
                            lineHeight = 23.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (book.subtitle.isNotBlank()) {
                            Text(
                                book.subtitle,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    Icon(
                        Icons.Filled.NorthEast,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (count > 1) {
                    Row(
                        Modifier.fillMaxWidth().widthIn(max = 520.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = { step(-1) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "上一份档案",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Text(
                            "${(focusedIndex + 1).toString().padStart(2, '0')} / " +
                                count.toString().padStart(2, '0'),
                            style = CodeTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { step(1) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "下一份档案",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Projection
// ---------------------------------------------------------------------------

internal const val ShelfDepthX = 0.66f
internal const val ShelfDepthY = -0.30f
private const val Radius = 1.48f

internal fun shelfSpine(width: Float): Float = (width * 0.043f).coerceIn(14f, 18f)
internal fun shelfPitch(width: Float): Float = shelfSpine(width) + 11f
internal fun shelfCover(width: Float): Float = (width * 0.32f).coerceIn(100f, 128f)
internal fun shelfBodyHeight(cover: Float): Float = (cover - 20f) * 166f / 118f + 54f
internal fun shelfFootprint(spine: Float, cover: Float): Float = spine + cover * ShelfDepthX
internal fun shelfPull(cover: Float): Float = cover * 0.84f
internal fun shelfBaseY(cover: Float, height: Float): Float = -cover * ShelfDepthY + height * 0.60f + 12f
internal fun shelfRowHeight(cover: Float, height: Float): Float = shelfBaseY(cover, height) + height + 60f

/**
 * Maps an unbounded lane coordinate into `0 until count`.
 *
 * The bank repeats every [count] lanes, so subtracting whole laps is invisible - and it is what
 * lets a fling of fifty books end on the same picture a fling of zero would.
 */
internal fun shelfWrapFocus(value: Float, count: Int): Float =
    if (count <= 0) value else ((value % count) + count) % count

internal fun shelfRailY(distance: Float): Float {
    val d = distance.coerceIn(-7f, 7f)
    return d * 2.5f + d * d * 0.45f
}

internal fun shelfWeight(distance: Float): Float {
    val d = abs(distance)
    if (d >= Radius) return 0f
    val value = cos(d / Radius * (PI / 2).toFloat())
    return value * value
}

private fun smoothstep(value: Float): Float {
    val v = value.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

private fun easeOutCubic(value: Float): Float {
    val v = value.coerceIn(0f, 1f)
    val inv = 1f - v
    return 1f - inv * inv * inv
}

private fun interval(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

/** `origin(cover, height, weight)` - how far the extracted file leaves the bank. */
internal fun shelfOrigin(cover: Float, height: Float, weight: Float): Offset {
    val pull = shelfPull(cover) * weight * (1.24f - 0.24f * weight)
    val lift = smoothstep(weight)
    return Offset(
        -pull * ShelfDepthX,
        shelfBaseY(cover, height) - pull * ShelfDepthY - height * 0.60f * lift,
    )
}

private fun faceMatrix(xScale: Float, yShear: Float, dx: Float, dy: Float): Matrix =
    Matrix().apply {
        // Use the named slots, not [row, column]: Compose stores the 4x4 column-major, so
        // `this[1, 0]` lands in the SkewX slot and leans a book sideways instead of sloping it
        // back. SkewY is the "y += k·x" term the projection actually needs.
        values[Matrix.ScaleX] = xScale
        values[Matrix.SkewY] = yShear
        values[Matrix.TranslateX] = dx
        values[Matrix.TranslateY] = dy
    }

private fun Modifier.projectFace(xScale: Float, yShear: Float, dx: Float, dy: Float): Modifier =
    drawWithContent {
        withTransform({ transform(faceMatrix(xScale, yShear, dx, dy)) }) {
            this@drawWithContent.drawContent()
        }
    }

// ---------------------------------------------------------------------------
// Scene
// ---------------------------------------------------------------------------

@Composable
private fun ShelfScene(
    items: List<ShelfItem>,
    focus: () -> Float,
    laneAnchor: Int,
    entrance: () -> Float,
    viewport: Float,
    spine: Float,
    cover: Float,
    body: Float,
    openingSlot: Int?,
    opening: () -> Float,
    onTapSlot: (Int, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = items.size
    val startX = viewport / 2f - shelfFootprint(spine, cover) / 2f + shelfPull(cover) * ShelfDepthX
    val pitch = shelfPitch(viewport)

    // A window of lanes around the centre, not the bare item list: the bank is a loop, so books
    // to the left of the first file are the last files, and the extracted one is always centred.
    // Enough lanes to cover the viewport on both sides: 19 on a phone, more on a wide canvas.
    // A fixed ±9 left gaps at the edges of a 520 dp window.
    val halfLanes = (ceil(viewport / pitch / 2f).toInt() + 2).coerceIn(6, 14)
    val activeSlots = remember(laneAnchor, count, halfLanes) {
        if (count < 2) listOf(0) else ((laneAnchor - halfLanes)..(laneAnchor + halfLanes)).toList()
    }

    Box(
        modifier
            .clipToBounds(),
    ) {
        // Floor shadows first: every file casts one, then the solids are stacked on top.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val book = focus()
                    activeSlots.forEach { slot ->
                        val d = slot - book
                        val weight = shelfWeight(d) * crest(entrance())
                        val origin = shelfOrigin(cover, body, weight)
                        val x = startX + slot * pitch + origin.x
                        val y = shelfRailY(d) + shelfBaseY(cover, body) + body
                        drawShelfFloor(x, y, spine, cover)
                    }
                },
        )

        activeSlots.forEach { slot ->
            ShelfVolume(
                item = items[Math.floorMod(slot, count)],
                slot = slot,
                focus = focus,
                entrance = entrance,
                viewport = viewport,
                spine = spine,
                cover = cover,
                body = body,
                isOpening = slot == openingSlot,
                opening = opening,
                onTap = { bounds -> onTapSlot(slot, bounds) },
            )
        }
    }
}

private fun crest(entrance: Float): Float =
    if (entrance >= 1f) 1f else easeOutCubic(interval(entrance, 0.38f, 1f))

@Composable
private fun ShelfVolume(
    item: ShelfItem,
    slot: Int,
    focus: () -> Float,
    entrance: () -> Float,
    viewport: Float,
    spine: Float,
    cover: Float,
    body: Float,
    isOpening: Boolean,
    opening: () -> Float,
    onTap: (Rect) -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val surface = if (dark) Color(0xFF2D2D2D) else Color(0xFFF2F0E9)
    val milk = if (dark) Color(0xFF242424) else Color(0xFFFEFDF8)
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val startX = viewport / 2f - shelfFootprint(spine, cover) / 2f + shelfPull(cover) * ShelfDepthX
    val pitch = shelfPitch(viewport)
    val density = LocalDensity.current
    val context = LocalContext.current
    val number = Math.floorMod(slot, 1000) + 1
    // The vertical spine title is a layout-only string; rebuilding it on every drag frame was
    // pure waste, so it is pinned to the book rather than to the slot.
    val spineTitle = remember(item.id, item.title) {
        buildString {
            item.title.take(7).forEachIndexed { index, glyph ->
                if (index > 0) append('\n')
                append(glyph)
            }
            if (item.title.length > 7) append("…")
        }
    }
    // Decode at the size actually drawn (≈115 dp wide). Full-resolution covers are 2-3x that,
    // and nineteen of them per frame is what made the drag stutter on mid-range phones.
    val coverPx = with(density) { cover.dp.roundToPx() }
    val coverRequest = remember(item.cover, coverPx) {
        ImageRequest.Builder(context)
            .data(item.cover)
            .size(coverPx, (coverPx * 1.4f).roundToInt())
            .build()
    }

    Box(
        Modifier
            .size((shelfFootprint(spine, cover) + 8f).dp, (body + 8f).dp)
            .offset {
                val d = slot - focus()
                val weight = shelfWeight(d) * crest(entrance())
                val origin = shelfOrigin(cover, body, weight)
                val order = (d + viewport / pitch / 2f + 2f).coerceIn(0f, 16f)
                val assembly = if (entrance() >= 1f) {
                    Offset.Zero
                } else {
                    val start = order * 0.018f
                    val progress = easeOutCubic(interval(entrance(), start, start + 0.52f))
                    Offset(
                        ShelfDepthX * 56f * (1f - progress),
                        ShelfDepthY * 56f * (1f - progress),
                    )
                }
                val x = startX + slot * pitch + origin.x + assembly.x
                val y = shelfRailY(d) + origin.y + assembly.y
                IntOffset((x * density.density).roundToInt(), (y * density.density).roundToInt())
            }
            .graphicsLayer {
                val p = if (isOpening) opening() else 0f
                if (p > 0f) {
                    transformOrigin = TransformOrigin(0.35f, 0.7f)
                    scaleX = 1f + 0.05f * p
                    scaleY = 1f + 0.05f * p
                    alpha = 1f - 0.12f * p
                }
            },
    ) {
        // Front cover: receding up-right by the projection, carrying the file address at top and
        // bottom exactly as the reference prints it.
        Box(
            Modifier
                .offset(x = spine.dp, y = (spine * 0.10f).dp)
                .size(cover.dp, body.dp)
                .projectFace(ShelfDepthX, ShelfDepthY, 0f, 0f)
                .background(milk)
                // Only the cover publishes bounds: the spine shares them so a tap on the spine
                // flies the same rectangle, instead of a 15 dp strip stretching to the dossier.
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .glassClickable(shape = RoundedCornerShape(0.dp)) { onTap(bounds) },
        ) {
            Column(
                Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 8.dp),
            ) {
                Text(
                    "NO.${number.toString().padStart(3, '0')}",
                    style = CodeTextStyle,
                    color = muted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(if (dark) Color(0xFF1E1E1E) else Color(0xFFEDEAE1)),
                ) {
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (item.id.length <= 12) "JM-${item.id}" else "JM-ARCHIVE",
                    style = CodeTextStyle,
                    color = muted,
                    maxLines = 1,
                )
            }
        }

        // Spine: number, vertical title, a dot. Its face is skewed by 10 %, like the reference.
        Box(
            Modifier
                .size(spine.dp, body.dp)
                .projectFace(1f, 0.10f, 0f, 0f)
                .background(surface)
                .glassClickable(shape = RoundedCornerShape(0.dp)) { onTap(bounds) },
        ) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    number.toString().padStart(2, '0'),
                    style = CodeTextStyle.copy(fontSize = 7.sp, letterSpacing = 0.sp),
                    color = muted,
                    maxLines = 1,
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        spineTitle,
                        fontSize = 9.sp,
                        lineHeight = 9.7.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = ink,
                        maxLines = 7,
                        overflow = TextOverflow.Clip,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(3.dp).background(muted, CircleShape))
            }
        }

        // Top face: the same four vertices the two faces meet on, so the solid never opens a seam.
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    val top = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(spine.dp.toPx(), spine.dp.toPx() * 0.10f)
                        lineTo(
                            spine.dp.toPx() + cover.dp.toPx() * ShelfDepthX,
                            spine.dp.toPx() * 0.10f + cover.dp.toPx() * ShelfDepthY,
                        )
                        lineTo(cover.dp.toPx() * ShelfDepthX, cover.dp.toPx() * ShelfDepthY)
                        close()
                    }
                    drawPath(
                        top,
                        Brush.linearGradient(
                            colors = if (dark) {
                                listOf(Color(0xFF3A3A3A), Color(0xFF2D2D2D))
                            } else {
                                listOf(Color(0xFFE3DFD3), Color(0xFFFEFDF8))
                            },
                            start = Offset(0f, 0f),
                            end = Offset(cover.dp.toPx(), 0f),
                        ),
                    )
                    drawPath(
                        top,
                        color = (if (dark) Color(0xFFF6F6F6) else Color(0xFF9A927F)).copy(alpha = 0.24f),
                        style = Stroke(width = 0.4.dp.toPx()),
                    )
                },
        )
    }
}

private fun DrawScope.drawShelfFloor(x: Float, y: Float, spine: Float, cover: Float) {
    val depth = Offset(cover * ShelfDepthX, cover * ShelfDepthY)
    val origin = Offset(x, y)
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        lineTo(origin.x + spine, origin.y + 1.4f)
        lineTo(origin.x + spine + depth.x, origin.y + 1.4f + depth.y)
        lineTo(origin.x + depth.x, origin.y + depth.y)
        lineTo(origin.x + depth.x - 10f, origin.y + depth.y + 10f)
        lineTo(origin.x - 10f, origin.y + 10f)
        close()
    }
    drawPath(
        path,
        Brush.linearGradient(
            colors = listOf(Color(0xFF8A806B).copy(alpha = 0.10f), Color.Transparent),
            start = Offset(origin.x + spine, origin.y),
            end = Offset(origin.x, origin.y + 10f),
        ),
    )
}

private fun Color.luminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f
        else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}
