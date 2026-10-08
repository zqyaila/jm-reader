package com.jm.reader.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ComicListItem
import com.jm.reader.desktop.core.ImageLoader
import com.jm.reader.desktop.core.JmId
import com.jm.reader.desktop.core.Repository

// ---------------------------------------------------------------------------
// Remote images
// ---------------------------------------------------------------------------

/**
 * Loads an image off the UI thread and remembers it. [ImageLoader] keeps its own LRU, so
 * scrolling back over covers that were already fetched resolves without a second request.
 */
@Composable
fun rememberRemoteImage(url: String): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = if (url.isBlank()) null else ImageLoader.load(url)
    }
    return bitmap
}

/**
 * Cover / artwork box. Deliberately **no spinner** for the placeholder: a grid of twenty
 * spinners reads as a broken app, whereas a quiet tinted tile reads as "still arriving".
 */
@Composable
fun RemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap = rememberRemoteImage(url)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bars & states
// ---------------------------------------------------------------------------

/**
 * Frosted top bar. [actions] is laid out at the trailing edge; [subtitle] renders under the title
 * (used by the detail screen to show the JM number without stealing a row from the content).
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    val s = LocalAppStrings.current
    GlassBar(
        modifier = Modifier.fillMaxWidth().height(AppSizes.BarHeight),
        shape = RectangleShapeCompat,
        kind = GlassKind.TopBar,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

/** Kept as a named constant so the bar shape is stated once (a full-bleed bar is not rounded). */
private val RectangleShapeCompat = androidx.compose.ui.graphics.RectangleShape

@Composable
fun LoadingView(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            if (!label.isNullOrBlank()) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalAppStrings.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).glassSurface(shape = GlassShapeLarge, elevation = 10.dp)
                .padding(28.dp),
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text(s.retry, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

/**
 * One grid tile: cover, category chip, JM number and caption on a single glass pane, so a grid of
 * them reads as glass rather than as floating text over the canvas.
 */
@Composable
fun ComicCard(
    item: ComicListItem,
    repo: Repository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAuthor: Boolean = false,
) {
    Column(
        modifier = modifier
            .glassClickable(onClick = onClick, shape = GlassShape)
            .glassSurface(shape = GlassShape, elevation = 6.dp)
            .padding(6.dp),
    ) {
        val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(GlassShapeInner),
        ) {
            RemoteImage(
                url = cover,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
            )
            item.category?.title?.takeIf { it.isNotBlank() }?.let { cat ->
                Badge(text = cat, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            }
            // Every work states which JM number it is, so a reader can identify (and search for)
            // it without opening the detail page.
            JmId.display(item.id).takeIf { it.isNotEmpty() }?.let { label ->
                Badge(text = label, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp, bottom = 2.dp)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAuthor) {
                item.author?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        author,
                        style = MaterialTheme.typography.labelSmall,
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

/** Small dark pill used for the category / JM-number overlays on a cover. */
@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** A single "label: value" statistic used across the detail header. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppSizes.RadiusInner))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(AppSizes.RadiusInner))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Section heading with an optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Vertically stacked chip row (tags), wrapping is handled by the caller's FlowRow. */
@Composable
fun TagChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(GlassCapsule)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), GlassCapsule)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Spacer used to keep the last row of a scrolling list clear of the bottom bar. */
@Composable
fun BottomReserve(height: androidx.compose.ui.unit.Dp = 84.dp) {
    Spacer(Modifier.height(height))
}

/**
 * Puts [text] on the system clipboard.
 *
 * Uses AWT directly rather than a Compose clipboard API: this is a desktop-only app, the AWT
 * clipboard is the real thing the OS pastes from, and it avoids depending on a Compose API whose
 * name is moving between releases.
 */
fun copyToClipboard(text: String) {
    runCatching {
        java.awt.Toolkit.getDefaultToolkit().systemClipboard
            .setContents(java.awt.datatransfer.StringSelection(text), null)
    }
}
