package com.jm.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.ui.theme.glassClickable
import com.jm.reader.util.JmId

/**
 * Cover tile used by every grid in the app.
 *
 * Refactor notes:
 *  - the tile is **not** a glass card any more. A frosted frame around a 110dp cover produced a
 *    halo of tinted blur on all four sides and made a 3-column grid look like a tray of stickers.
 *    The cover now sits directly on the canvas with a 12dp corner, which lets the artwork carry
 *    the grid.
 *  - the category chip was removed from the cover: it was the third badge on a small tile and the
 *    least useful one. The JM number stays (readers quote it), moved to a quiet corner chip.
 *  - [rank] draws the weekly-ranking numeral, so the 每周必看 grid reads as a chart rather than as
 *    another copy of the latest list.
 */
@Composable
fun ComicCard(
    item: ComicListItem,
    repo: AppRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Show the author under the title (used by the search grids). */
    showAuthor: Boolean = false,
    /** 1-based ranking position; when set, a numeral is drawn on the cover. */
    rank: Int? = null,
    /** 0f..1f reading progress; when set, a hairline bar is drawn at the bottom of the cover. */
    progress: Float? = null,
) {
    val coverShape = RoundedCornerShape(3.dp)
    Column(
        modifier = modifier.glassClickable(onClick = onClick, shape = coverShape),
    ) {
        val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cover)
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (rank != null) {
                Text(
                    text = rank.toString().padStart(2, '0'),
                    color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (rank <= 3) MaterialTheme.colorScheme.primary
                            else Color.Black.copy(alpha = 0.55f),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 7.dp, start = 1.dp, end = 1.dp)) {
            Text(
                text = item.name,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // The catalogue mark sits under the title, not on the artwork: a badge on a 110 dp
            // cover competes with the image, while a mono line below reads as a record entry.
            JmId.display(item.id).takeIf { it.isNotEmpty() }?.let { label ->
                Text(
                    text = label,
                    style = com.jm.reader.ui.theme.CodeTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                )
            }
            if (showAuthor) {
                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Row(
                        Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = author,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
