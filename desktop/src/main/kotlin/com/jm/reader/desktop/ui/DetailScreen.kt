package com.jm.reader.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ComicDetail
import com.jm.reader.desktop.core.HtmlText
import com.jm.reader.desktop.core.JmId
import com.jm.reader.desktop.core.RepoResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Comic detail: header (cover, title, JM number, stats, tags, description), the chapter list, and
 * the related-works strip.
 *
 * A work with a single chapter (`series` empty) shows one "start reading" action instead of a
 * one-row chapter list, which is what the phone client does too.
 */
@Composable
fun DetailScreen(
    id: String,
    onBack: () -> Unit,
    onOpenComic: (String) -> Unit,
    onRead: (chapterId: String, chapterName: String) -> Unit,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var detail by remember(id) { mutableStateOf<ComicDetail?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        error = null
        when (val r = repo.getAlbum(id)) {
            is RepoResult.Ok -> detail = r.data
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    LaunchedEffect(id) { load() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = detail?.name?.takeIf { it.isNotBlank() } ?: s.comicDetail,
            subtitle = JmId.display(id),
            onBack = onBack,
            actions = {
                IconButton(onClick = {
                    copyToClipboard(JmId.display(id))
                    copied = true
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = s.workIdCopied)
                }
            },
        )

        if (copied) {
            LaunchedEffect(Unit) {
                delay(1600)
                copied = false
            }
            Text(
                s.workIdCopied,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = AppSizes.ContentPadding, top = 6.dp)
                    .clip(GlassCapsule)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            val d = detail
            when {
                loading && d == null -> LoadingView(label = s.loading)
                error != null && d == null -> ErrorView(error.orEmpty()) { scope.launch { load() } }
                d == null -> EmptyView(s.loadFail)
                else -> DetailContent(
                    detail = d,
                    onOpenComic = onOpenComic,
                    onRead = onRead,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    detail: ComicDetail,
    onOpenComic: (String) -> Unit,
    onRead: (String, String) -> Unit,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val cover = repo.comicCoverDetail(detail.id, detail.addtime)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = AppSizes.ContentPadding,
            end = AppSizes.ContentPadding,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().widthIn(max = AppSizes.DetailMaxWidth).glassSurface(
                    shape = GlassShapeLarge,
                    elevation = 12.dp,
                ).padding(AppSizes.ContentPadding),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                RemoteImage(
                    url = cover,
                    contentDescription = detail.name,
                    modifier = Modifier
                        .width(220.dp)
                        .aspectRatio(3f / 4f)
                        .clip(GlassShapeInner),
                )

                Column(Modifier.weight(1f)) {
                    Text(
                        detail.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    val subtitle = buildList {
                        detail.authors.firstOrNull()?.takeIf { it.isNotBlank() }?.let { add("${s.authorLabel}: $it") }
                        JmId.display(detail.id).takeIf { it.isNotEmpty() }?.let { add(it) }
                    }.joinToString("  ·  ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    Row(
                        Modifier.padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatTile(s.views, compact(detail.totalViews))
                        StatTile(s.pages, detail.totalPhotos.toString())
                        StatTile(s.likes, compact(detail.likes))
                    }

                    if (detail.tags.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(top = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            detail.tags.take(14).forEach { tag -> TagChip(tag) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    val first = detail.series.minByOrNull { it.sort } ?: detail.series.firstOrNull()
                    Button(
                        onClick = {
                            if (first != null) {
                                onRead(first.id, first.name)
                            } else {
                                // Single-file work: the album id *is* the chapter id.
                                onRead(detail.id, detail.name)
                            }
                        },
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(
                            s.startReading,
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(if (detail.series.isEmpty()) s.singleFileWork else s.chapterList)
        }

        if (detail.series.isNotEmpty()) {
            items(detail.series.sortedBy { it.sort }, key = { it.id }) { chapter ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = AppSizes.DetailMaxWidth)
                        .glassClickable(shape = GlassCapsule) { onRead(chapter.id, chapter.name) }
                        .glassSurface(shape = GlassCapsule, elevation = 3.dp)
                        .height(AppSizes.ItemHeight)
                        .padding(horizontal = 16.dp)
                        .padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        chapter.name.ifBlank { s.chapterFmt.format(chapter.sort) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (chapter.totalPage > 0) {
                        Text(
                            "${chapter.totalPage} ${s.pages}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(s.descriptionLabel)
        }
        item {
            Text(
                HtmlText.strip(detail.description).ifBlank { s.noDescription },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().widthIn(max = AppSizes.DetailMaxWidth),
            )
        }

        if (detail.relatedList.isNotEmpty()) {
            item { SectionHeader(s.relatedWorks) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(detail.relatedList, key = { it.id }) { item ->
                        Box(Modifier.width(160.dp)) {
                            ComicCard(
                                item = item,
                                repo = repo,
                                onClick = { onOpenComic(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 12345 -> "12.3k": stat tiles have no room for six digits. */
private fun compact(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fk", value / 1_000.0)
    else -> value.toString()
}
