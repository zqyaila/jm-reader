package com.jm.reader.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ComicListItem
import com.jm.reader.desktop.core.RepoResult
import com.jm.reader.desktop.core.Repository
import kotlinx.coroutines.launch

/**
 * Home feed: a featured strip plus a paged "latest" grid.
 *
 * The grid is `Adaptive` rather than a fixed column count: this is a resizeable desktop window, so
 * the number of columns has to follow the width instead of assuming a phone.
 */
@Composable
fun HomeScreen(
    onOpenComic: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var promote by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var latest by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadInitial() {
        loading = true
        error = null
        promote = (repo.getPromote() as? RepoResult.Ok)?.data ?: emptyList()
        when (val r = repo.getLatest(1)) {
            is RepoResult.Ok -> {
                latest = r.data
                page = 1
                endReached = r.data.isEmpty()
                error = null
            }
            is RepoResult.Err -> {
                latest = emptyList()
                error = r.message
            }
        }
        loading = false
    }

    suspend fun loadMore() {
        if (loadingMore || endReached || loading) return
        loadingMore = true
        val next = page + 1
        when (val r = repo.getLatest(next)) {
            is RepoResult.Ok -> {
                latest = (latest + r.data).distinctBy { it.id }
                page = next
                endReached = r.data.isEmpty()
            }
            // Silent on load-more failure: the rows already on screen stay useful.
            is RepoResult.Err -> Unit
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) { loadInitial() }

    Box(Modifier.fillMaxSize()) {
        when {
            loading && latest.isEmpty() -> LoadingView(label = s.loading)

            error != null && latest.isEmpty() -> ErrorView(
                message = error.orEmpty(),
                onRetry = { scope.launch { loadInitial() } },
            )

            else -> {
                val gridState = rememberLazyGridState()

                LaunchedEffect(gridState, endReached) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { last ->
                            val total = gridState.layoutInfo.totalItemsCount
                            if (last != null && total > 0 && last >= total - 6) {
                                scope.launch { loadMore() }
                            }
                        }
                }

                Column(Modifier.fillMaxSize()) {
                    AppTopBar(
                        title = s.appName,
                        actions = {
                            IconButton(onClick = onOpenSearch) {
                                Icon(Icons.Filled.Search, contentDescription = s.search)
                            }
                        },
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppSizes.ContentPadding,
                            end = AppSizes.ContentPadding,
                            top = 12.dp,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (promote.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                PromoteStrip(repo, promote, onOpenComic)
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LatestHeader(
                                title = s.latest,
                                onRefresh = { scope.launch { loadInitial() } },
                            )
                        }

                        items(latest, key = { it.id }) { item ->
                            ComicCard(
                                item = item,
                                repo = repo,
                                onClick = { onOpenComic(item.id) },
                            )
                        }

                        if (loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }

                        if (endReached && latest.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    s.noMore,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "最新" heading with a refresh affordance. */
@Composable
private fun LatestHeader(title: String, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "refresh",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Featured strip: wide tiles that scroll horizontally, which is what "promote" payloads are for. */
@Composable
private fun PromoteStrip(
    repo: Repository,
    items: List<ComicListItem>,
    onOpenComic: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
            Column(
                Modifier
                    .width(280.dp)
                    .glassClickable(shape = GlassShapeLarge) { onOpenComic(item.id) }
                    .glassSurface(shape = GlassShapeLarge, elevation = 10.dp)
                    .padding(8.dp),
            ) {
                RemoteImage(
                    url = cover,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(GlassShapeInner),
                )
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
                )
            }
        }
    }
}
