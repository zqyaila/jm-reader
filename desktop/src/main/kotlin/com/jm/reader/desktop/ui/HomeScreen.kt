package com.jm.reader.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm.reader.desktop.core.ComicListItem
import com.jm.reader.desktop.core.Guess
import com.jm.reader.desktop.core.RepoResult
import com.jm.reader.desktop.core.Repository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Home feed state.
 *
 * Lives in `AppShell` rather than in this composable: opening a comic removes `HomeScreen` from
 * the composition, so `remember` state (and the scroll position) would be rebuilt when the reader
 * comes back. Hoisting it one level up keeps the feed and the position, the desktop equivalent of
 * the Android module's ViewModel-scoped state.
 */
class HomeState {
    val promote = mutableStateOf<List<ComicListItem>>(emptyList())
    val latest = mutableStateOf<List<ComicListItem>>(emptyList())
    val loading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    val page = mutableIntStateOf(1)
    val endReached = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)

    var loaded = false

    var firstVisibleIndex = 0
    var firstVisibleOffset = 0

    var lastRandomId: String? = null
    var lastGuessId: String? = null
}

@Composable
fun HomeScreen(
    state: HomeState,
    onOpenComic: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val stats = LocalStats.current
    val statsSnapshot by stats.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var promote by state.promote
    var latest by state.latest
    var loading by state.loading
    var error by state.error
    var page by state.page
    var endReached by state.endReached
    var loadingMore by state.loadingMore
    var randomLoading by remember { mutableStateOf(false) }
    var guessLoading by remember { mutableStateOf(false) }

    suspend fun loadInitial() {
        loading = true
        error = null
        // Both endpoints are independent; issuing them together saves a round-trip on first paint.
        val (promoteRes, latestRes) = coroutineScope {
            val p = async { repo.getPromote() }
            val l = async { repo.getLatest(1) }
            p.await() to l.await()
        }
        promote = (promoteRes as? RepoResult.Ok)?.data ?: emptyList()
        when (latestRes) {
            is RepoResult.Ok -> {
                latest = latestRes.data
                page = 1
                endReached = latestRes.data.isEmpty()
            }
            is RepoResult.Err -> {
                latest = emptyList()
                error = latestRes.message
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
            is RepoResult.Err -> Unit
        }
        loadingMore = false
    }

    suspend fun surprise() {
        if (randomLoading) return
        randomLoading = true
        try {
            when (val r = repo.randomRecommend()) {
                is RepoResult.Ok -> {
                    val pool = r.data.filter { it.id.isNotBlank() }
                    val pick = pool.filter { it.id != state.lastRandomId }.ifEmpty { pool }.randomOrNull()
                    if (pick == null) snackbar.showSnackbar(s.noResult) else {
                        state.lastRandomId = pick.id
                        onOpenComic(pick.id)
                    }
                }
                is RepoResult.Err -> snackbar.showSnackbar(r.message)
            }
        } finally {
            randomLoading = false
        }
    }

    suspend fun guess() {
        if (guessLoading) return
        guessLoading = true
        try {
            val counts = statsSnapshot.tagCounts.mapValues { it.value.weight }
            if (counts.isEmpty()) {
                snackbar.showSnackbar(s.guessNeedData)
                return
            }
            var chosen: ComicListItem? = null
            val remaining = counts.toMutableMap()
            var attempt = 0
            while (attempt < 3 && chosen == null && remaining.isNotEmpty()) {
                attempt++
                val tag = Guess.pickTag(remaining) ?: break
                remaining.remove(tag)
                when (val r = repo.adaptiveSearch(tag)) {
                    is RepoResult.Ok -> {
                        // Never hand back a work the reader marked 萎了.
                        val disliked = statsSnapshot.dislikedIds
                        val pool = r.data.items.filter {
                            it.id.isNotBlank() && it.id != state.lastGuessId && it.id !in disliked
                        }
                        if (pool.isNotEmpty()) chosen = pool.random()
                    }
                    is RepoResult.Err -> Unit
                }
            }
            if (chosen == null) {
                when (val r = repo.randomRecommend()) {
                    is RepoResult.Ok -> chosen = r.data
                        .filter { it.id.isNotBlank() && it.id != state.lastGuessId }
                        .ifEmpty { r.data }
                        .randomOrNull()
                    is RepoResult.Err -> {
                        snackbar.showSnackbar(r.message)
                        return
                    }
                }
            }
            val pick = chosen
            if (pick == null) snackbar.showSnackbar(s.noResult) else {
                state.lastGuessId = pick.id
                onOpenComic(pick.id)
            }
        } finally {
            guessLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (state.loaded && error == null) return@LaunchedEffect
        state.loaded = true
        loadInitial()
    }

    val gridState = rememberLazyGridState(state.firstVisibleIndex, state.firstVisibleOffset)
    DisposableEffect(gridState) {
        onDispose {
            state.firstVisibleIndex = gridState.firstVisibleItemIndex
            state.firstVisibleOffset = gridState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(gridState, endReached) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                val total = gridState.layoutInfo.totalItemsCount
                if (last != null && total > 0 && last >= total - 8) {
                    scope.launch { loadMore() }
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading && latest.isEmpty() -> LoadingView(label = s.loading)

            error != null && latest.isEmpty() -> ErrorView(
                message = error.orEmpty(),
                onRetry = { scope.launch { loadInitial() } },
            )

            else -> Column(Modifier.fillMaxSize()) {
                AppTopBar(
                    title = s.appName,
                    actions = {
                        IconButton(onClick = { scope.launch { loadInitial() } }) {
                            Icon(Icons.Filled.Refresh, contentDescription = s.refresh)
                        }
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
                        top = 10.dp,
                        bottom = 104.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HomeIntro()
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        QuickActions(
                            randomLoading = randomLoading,
                            guessLoading = guessLoading,
                            onRandom = { scope.launch { surprise() } },
                            onGuess = { scope.launch { guess() } },
                            onStats = onOpenStats,
                        )
                    }

                    if (promote.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = s.forYou)
                        }
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

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
        )
    }
}

/** Editorial header: a small eyebrow, a large statement, and one line of context. */
@Composable
private fun HomeIntro() {
    val s = LocalAppStrings.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Text(
            s.appName.uppercase(),
            fontSize = 10.sp,
            letterSpacing = 2.2.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            s.homeGreeting,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Three actions that are not "browse a list": roll the dice, let the tag profile pick, or look at
 * what the profile currently says. They are cards rather than toolbar buttons so the sentence that
 * explains each one fits.
 */
@Composable
private fun QuickActions(
    randomLoading: Boolean,
    guessLoading: Boolean,
    onRandom: () -> Unit,
    onGuess: () -> Unit,
    onStats: () -> Unit,
) {
    val s = LocalAppStrings.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionCard(
            icon = Icons.Filled.Shuffle,
            title = s.randomComic,
            subtitle = s.randomHint,
            loading = randomLoading,
            onClick = onRandom,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            icon = Icons.Filled.AutoAwesome,
            title = s.guessYouLike,
            subtitle = s.guessHint,
            loading = guessLoading,
            onClick = onGuess,
            modifier = Modifier.weight(1f),
        )
        ActionCard(
            icon = Icons.Filled.Insights,
            title = s.statsTitle,
            subtitle = s.statsTagShare,
            loading = false,
            onClick = onStats,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "最新" heading with a refresh affordance. */
@Composable
private fun LatestHeader(title: String, onRefresh: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "refresh",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Featured strip: wide tiles scrolled horizontally. The frame is gone; the title sits on a gradient
 * over the artwork, which is what makes a 16:9 crop readable without a card around it.
 */
@Composable
private fun PromoteStrip(
    repo: Repository,
    items: List<ComicListItem>,
    onOpenComic: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(items, key = { it.id }) { item ->
            val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
            Box(
                Modifier
                    .width(300.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .glassClickable(shape = RoundedCornerShape(18.dp)) { onOpenComic(item.id) },
            ) {
                RemoteImage(
                    url = cover,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Color(0xD9000000),
                            ),
                        ),
                )
                Text(
                    item.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                )
            }
        }
    }
}
