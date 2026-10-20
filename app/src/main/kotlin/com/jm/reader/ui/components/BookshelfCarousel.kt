package com.jm.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.ui.theme.CodeTextStyle
import com.jm.reader.ui.theme.glassClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/** One book as the shelf needs it, independent of which tab (favourites/history/downloads) it came from. */
data class ShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val cover: String,
    /** 0f..1f reading progress, when the source knows it. */
    val progress: Float? = null,
)

/**
 * 3D shelf: a looping carousel of standing books.
 *
 * The reference project builds this as a custom render object with a "wave" rail; on Compose the
 * same feel comes from a pager plus a per-page perspective transform:
 *
 *  - **loop** - the pager is addressed in `Int.MAX_VALUE` pages and items are taken modulo the
 *    list, so it never hits an end in either direction;
 *  - **inertia + snap** - the pager's own fling behaviour; every drag settles on a book;
 *  - **depth** - rotationY/scale/translation derived from the page's signed distance, so the
 *    neighbours lean away instead of sliding flat;
 *  - **haptics** - one tick per settled page, not per frame (a per-frame tick is what makes cheap
 *    implementations feel like a buzzer).
 */
@Composable
fun BookshelfCarousel(
    items: List<ShelfItem>,
    onOpen: (ShelfItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val count = items.size
    val start = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % count
    val state = rememberPagerState(initialPage = start) { Int.MAX_VALUE }
    val haptics = LocalHapticFeedback.current
    val currentItems by rememberUpdatedState(items)

    // A tick per detent. `settledPage` only changes when the pager has actually come to rest.
    LaunchedEffect(state) {
        snapshotFlow { state.settledPage }
            .distinctUntilChanged()
            .collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }

    Box(modifier) {
        // The shelf plank: one hairline and a soft shadow underneath, so the books stand on
        // something instead of floating.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 58.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        HorizontalPager(
            state = state,
            contentPadding = PaddingValues(horizontal = 76.dp),
            pageSpacing = 6.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = currentItems[page % count]
            val distance = state.getOffsetDistanceInPages(page).coerceIn(-2.2f, 2.2f)
            BookOnShelf(
                item = item,
                distance = distance,
                onClick = { onOpen(item) },
            )
        }
    }
}

@Composable
private fun BookOnShelf(
    item: ShelfItem,
    distance: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val absDistance = abs(distance)
    val shape = RoundedCornerShape(3.dp)
    val cover = item.cover

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = 60.dp)
            .graphicsLayer {
                rotationY = distance * -16f
                cameraDistance = 24f * density.density
                val scale = (1f - absDistance * 0.16f).coerceAtLeast(0.7f)
                scaleX = scale
                scaleY = scale
                translationX = distance * 26.dp.toPx()
                alpha = (1f - absDistance * 0.28f).coerceAtLeast(0.35f)
            }
            .glassClickable(shape = shape, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(148.dp)
                .aspectRatio(0.75f)
                .shadow(10.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(cover).crossfade(true).build(),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Spine: a narrow dark strip with a highlight, which is what makes a flat cover read
            // as a standing book at this angle.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val spine = size.width * 0.055f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Black.copy(alpha = 0.42f),
                                0.55f to Color.Black.copy(alpha = 0.12f),
                                1f to Color.Transparent,
                                startX = 0f,
                                endX = spine * 2.2f,
                            ),
                            size = androidx.compose.ui.geometry.Size(spine, size.height),
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.18f),
                            start = Offset(spine, 0f),
                            end = Offset(spine, size.height),
                            strokeWidth = 1f,
                        )
                    },
            )
            item.progress?.takeIf { it > 0f }?.let { progress ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Text(
            item.title,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 6.dp, end = 6.dp),
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = CodeTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp, start = 6.dp, end = 6.dp),
            )
        }
    }
}
