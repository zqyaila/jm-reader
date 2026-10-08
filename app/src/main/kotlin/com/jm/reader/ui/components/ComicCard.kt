package com.jm.reader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.ui.theme.AppSpacing
import com.jm.reader.ui.theme.GlassShape
import com.jm.reader.ui.theme.glassClickable
import com.jm.reader.ui.theme.glassSurface
import com.jm.reader.util.JmId

@Composable
fun ComicCard(
    item: ComicListItem,
    repo: AppRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Show the author under the title (used by the search grids). */
    showAuthor: Boolean = false,
) {
    // The whole tile is one glass pane: cover, category chip and caption all sit on the same
    // frosted surface, which is what makes a grid of them read as glass rather than as floating
    // text over the canvas. Pressing it dips the pane on a spring (spec press feedback) rather than
    // flashing a ripple across the corner art.
    Column(
        modifier = modifier
            .glassClickable(onClick = onClick, shape = GlassShape)
            .glassSurface(shape = GlassShape, elevation = 6.dp)
            .padding(6.dp),
    ) {
        val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
        // Shape roles come from the theme instead of a literal radius: `small` (12dp) is the
        // nested-media role and `extraSmall` (8dp) is the badge role, so a future tweak to
        // `AppShapes` moves every card and every badge in the app together. See theme/Shapes.kt.
        val coverShape = MaterialTheme.shapes.small
        val badgeShape = MaterialTheme.shapes.extraSmall
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(coverShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(cover)
                    .crossfade(true)
                    .build(),
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                contentScale = ContentScale.Crop,
            )
            item.category?.let { cat ->
                Text(
                    text = cat.title.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(AppSpacing.Inline)
                        .clip(badgeShape)
                        .background(Color(0x99000000))
                        .padding(horizontal = AppSpacing.Inline, vertical = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Album id, opposite the category chip: every work states which JM number it is, so a
            // reader can identify (and search for) it without opening the detail page.
            JmId.display(item.id).takeIf { it.isNotEmpty() }?.let { label ->
                Text(
                    text = label,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AppSpacing.Inline)
                        .clip(badgeShape)
                        .background(Color(0x99000000))
                        .padding(horizontal = AppSpacing.Inline, vertical = 1.dp),
                    maxLines = 1,
                )
            }
        }
        // Caption: inside the same pane, so it only needs padding (not a second glass plate).
        Column(Modifier.fillMaxWidth().padding(top = 5.dp, start = 2.dp, end = 2.dp, bottom = 1.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                // Medium weight lifts the title off the `labelSmall` author line below it; at
                // Regular the two lines read as one block of grey on a busy cover.
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAuthor) {
                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}
