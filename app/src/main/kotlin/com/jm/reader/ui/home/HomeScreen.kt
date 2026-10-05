package com.jm.reader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.ComicCard
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.theme.glassSurface
import kotlinx.coroutines.launch

private data class HomeUiState(
    val promote: List<ComicListItem> = emptyList(),
    val latest: List<ComicListItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(HomeUiState()) }
    var page by remember { mutableIntStateOf(1) }
    var endReached by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }

    suspend fun loadInitial() {
        state = state.copy(loading = true, error = null)
        val promoteRes = repo.getPromote()
        val latestRes = repo.getLatest(1)
        val promote = when (promoteRes) {
            is RepoResult.Ok -> promoteRes.data.flatten()
            is RepoResult.Err -> emptyList()
        }
        when (latestRes) {
            is RepoResult.Ok -> {
                state = HomeUiState(promote = promote, latest = latestRes.data, loading = false, error = null)
                page = 1
                endReached = latestRes.data.isEmpty()
            }
            is RepoResult.Err -> {
                state = HomeUiState(promote = promote, latest = emptyList(), loading = false, error = latestRes.message)
            }
        }
    }

    suspend fun loadMore() {
        if (loadingMore || endReached) return
        loadingMore = true
        val next = page + 1
        when (val r = repo.getLatest(next)) {
            is RepoResult.Ok -> {
                state = state.copy(latest = (state.latest + r.data).distinctBy { it.id })
                page = next
                endReached = r.data.isEmpty()
            }
            is RepoResult.Err -> { /* keep silent on load-more failure */ }
        }
        loadingMore = false
    }

    LaunchedEffect(Unit) { loadInitial() }

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !state.loading && last >= state.latest.size - 8) {
                    scope.launch { loadMore() }
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.latest.isEmpty() -> LoadingView()
            state.error != null && state.latest.isEmpty() -> ErrorView(state.error!!, onRetry = { scope.launch { loadInitial() } })
            else -> Column(Modifier.fillMaxSize()) {
                // Search bar - one adaptive box, no 作品 / 作者 / ID chooser.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .glassSurface(shape = RoundedCornerShape(24.dp))
                        .clickable { navController.navigate(Routes.search()) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        s.searchAdaptiveHint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        PromoteCarousel(repo, state.promote, onItemClick = { navController.navigate(Routes.comicDetail(it.id)) })
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        QuickLinks(navController)
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(s.latest, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { loadInitial() } }) {
                                Icon(Icons.Filled.Refresh, contentDescription = s.refresh, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    items(state.latest, key = { it.id }) { item ->
                        ComicCard(item = item, repo = repo, onClick = { navController.navigate(Routes.comicDetail(item.id)) })
                    }
                    if (loadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(3) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PromoteCarousel(
    repo: AppRepository,
    items: List<ComicListItem>,
    onItemClick: (ComicListItem) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { pageIndex ->
        val item = items[pageIndex]
        val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onItemClick(item) },
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(6.dp),
            ) {
                Text(
                    item.name,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickLinks(navController: NavHostController) {
    val s = LocalAppStrings.current
    val links = listOf(
        Triple(s.quickWeek, Icons.Filled.Star, { navController.navigate(Routes.WEEK) }),
        Triple(s.quickDaily, Icons.Filled.CalendarMonth, { navController.navigate(Routes.DAILY) }),
        Triple(s.quickHot, Icons.Filled.Whatshot, { navController.navigate(Routes.HOT_TAGS) }),
        Triple(s.quickNovels, Icons.Filled.Article, { navController.navigate(Routes.NOVELS) }),
        Triple(s.quickMovies, Icons.Filled.LocalMovies, { navController.navigate(Routes.MOVIES) }),
        Triple(s.quickGames, Icons.Filled.SportsEsports, { navController.navigate(Routes.GAMES) }),
        Triple(s.quickBlogs, Icons.Filled.Forum, { navController.navigate(Routes.BLOGS) }),
        Triple(s.quickCategories, Icons.Filled.Category, { navController.navigate(Routes.CATEGORIES) }),
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        links.take(8).forEach { (label, icon, onClick) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .glassSurface(
                            shape = RoundedCornerShape(12.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                }
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
