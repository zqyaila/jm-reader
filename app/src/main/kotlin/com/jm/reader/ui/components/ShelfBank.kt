package com.jm.reader.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.ui.theme.CodeTextStyle
import com.jm.reader.ui.theme.glassClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.roundToInt

/** One book as a shelf needs it, independent of which tab it came from. */
data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val cover: String,
)

/**
 * The stacked shelf, after Wenku8_Flutter's `ArchiveWave`.
 *
 * It is **not** a filmstrip of covers: the books stand spine-out in a row, and only the centred
 * one is pulled forward to show its cover and name. Neighbours sit lower and further back (the
 * reference's `railY(d) = 2.5d + 0.45d²`), which is what makes the row read as a physical bank
 * rather than as a carousel.
 *
 * Scrolling is a snapping [LazyRow] of 24 dp spines: the reference implements its own physics and
 * detents, but a snapping list gives the same one-book-per-drag feel with the platform's fling.
 * A haptic tick fires when the scroll settles, not per frame.
 *
 * This is a *header*, not the whole screen: the ordinary grid/list stays below it.
 */
@Composable
fun ShelfBank(
    items: List<ShelfItem>,
    onOpen: (ShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val count = items.size
    val pitch = 26.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    val haptics = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Continuous position in "books": index + fraction of the way to the next one.
    val position by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf 0f
            first.index + (state.firstVisibleItemScrollOffset.toFloat() / first.size).coerceIn(0f, 1f)
        }
    }
    var focus by remember { mutableIntStateOf(0) }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            // The first emission is the resting state on open - a tick there would buzz for
            // simply looking at the shelf.
            .drop(1)
            .collect { scrolling ->
                if (!scrolling) {
                    focus = position.roundToInt().coerceAtLeast(0)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
    }

    val focused = items[focus % count]

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(214.dp)) {
            // Half a viewport of padding on each side: with fixed 26 dp slots this is what puts
            // the settled spine exactly under the pulled-out cover (and lets the ends scroll to
            // the middle instead of stopping at the edge).
            val side = (maxWidth - pitch) / 2
            // Spines.
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.Start,
                flingBehavior = rememberSnapFlingBehavior(state),
                modifier = Modifier.fillMaxSize().padding(bottom = 44.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(count) { index ->
                    val distance = index - position
                    val clamped = distance.coerceIn(-7f, 7f)
                    val lift = clamped * 2.5f + clamped * clamped * 0.45f
                    val alpha = (1f - abs(distance) / 9f).coerceIn(0.18f, 1f)
                    Box(
                        Modifier
                            .width(pitch)
                            .height(126.dp)
                            .padding(horizontal = 3.dp)
                            .graphicsLayer {
                                translationY = lift.dp.toPx()
                                this.alpha = alpha
                                rotationY = (-distance * 4f).coerceIn(-18f, 18f)
                                cameraDistance = 18f * density.density
                            }
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(items[index].cover)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Spine shading: darker at the hinge, a hairline highlight down the edge.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.42f),
                                            Color.Black.copy(alpha = 0.06f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )
                    }
                }
            }

            // The pulled-out book: cover + title, floating in front of the bank.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .width(132.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = focused,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut()
                    },
                    label = "shelfFocus",
                ) { book ->
                    Column(
                        Modifier
                            .glassClickable(shape = RoundedCornerShape(3.dp)) { onOpen(book) }
                            .padding(top = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .width(104.dp)
                                .height(146.dp)
                                .shadow(14.dp, RoundedCornerShape(3.dp), clip = false)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(book.cover)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = book.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Text(
                            book.title,
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                        )
                        if (book.subtitle.isNotBlank()) {
                            Text(
                                book.subtitle,
                                style = CodeTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // The plank. One hairline is enough; a gradient "shelf" would be furniture, not paper.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            "${(focus % count) + 1} / $count",
            style = CodeTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
        )
    }
}
