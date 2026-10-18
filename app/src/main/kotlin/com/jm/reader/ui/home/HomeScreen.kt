package com.jm.reader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.history.HistoryManager
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.data.stats.GuessEngine
import com.jm.reader.ui.BottomNavReserve
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalHistoryManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalStats
import com.jm.reader.ui.components.ComicCard
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.components.SectionHeader
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.theme.glassClickable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class HomeUiState(
    val promote: List<ComicListItem> = emptyList(),
    val latest: List<ComicListItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Keeps the home feed alive while its navigation entry is on the back stack.
 *
 * Without this, opening a comic disposed the screen's composition and every `remember` went with
 * it: coming back reloaded the feed from page 1 and jumped to the top. Switching tabs lost it the
 * same way. The ViewModel store belongs to the `Routes.MAIN` entry, which stays put, so all four
 * tabs keep their state - including while a detail screen sits on top of them.
 */
internal class HomeViewModel : ViewModel() {
    val state = mutableStateOf(HomeUiState())
    val page = mutableIntStateOf(1)
    val endReached = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)

    /** Set once the first page has been requested, so re-entering does not reload. */
    var loaded = false

    /** Last known scroll position, captured when the screen leaves composition. */
    var firstVisibleIndex = 0
    var firstVisibleOffset = 0

    /** Hero carousel position, so returning does not jump back to slide 1. */
    var heroIndex = 0

    /** Last work the dice landed on, so tapping it twice does not open the same page again. */
    var lastRandomId: String? = null

    /** Same, for the tag-weighted pick. */
    var lastGuessId: String? = null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val history = LocalHistoryManager.current
    val scope = rememberCoroutineScope()
    val vm: HomeViewModel = viewModel()
    var state by vm.state
    var page by vm.page
    var endReached by vm.endReached
    var loadingMore by vm.loadingMore

    val recent by history.entries.collectAsState()
    // Entries that only carry reading progress can have no title yet; they would render as a blank
    // caption and look like a bug, so they wait until the album has been opened once.
    val recentItems = recent.filter { it.name.isNotBlank() }
    val stats = LocalStats.current
    val statsSnapshot by stats.snapshot.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var randomLoading by remember { mutableStateOf(false) }
    var guessLoading by remember { mutableStateOf(false) }

    suspend fun loadInitial() {
        state = state.copy(loading = true, error = null)
        // Two independent endpoints: firing them in parallel saves one cross-border round-trip on
        // the first screen (they used to run back to back).
        val (promoteRes, latestRes) = coroutineScope {
            val promote = async { repo.getPromote() }
            val latest = async { repo.getLatest(1) }
            promote.await() to latest.await()
        }
        val promote = when (promoteRes) {
            is RepoResult.Ok -> promoteRes.data.flatten().distinctBy { it.id }
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

    /**
     * Dice: one random work, straight to its detail page.
     *
     * `/random_recommend` already returns a randomised set, so picking one client-side keeps this
     * to a single round-trip. [HomeViewModel.lastRandomId] is skipped when there is an alternative,
     * which stops a double tap from landing on the same work twice.
     */
    suspend fun surprise() {
        if (randomLoading) return
        randomLoading = true
        try {
            when (val r = repo.randomRecommend()) {
                is RepoResult.Ok -> {
                    val pool = r.data.filter { it.id.isNotBlank() }
                    val pick = pool.filter { it.id != vm.lastRandomId }.ifEmpty { pool }.randomOrNull()
                    if (pick == null) {
                        snackbar.showSnackbar(s.noResult)
                    } else {
                        vm.lastRandomId = pick.id
                        navController.navigate(Routes.comicDetail(pick.id))
                    }
                }
                is RepoResult.Err -> snackbar.showSnackbar(r.message)
            }
        } finally {
            randomLoading = false
        }
    }

    /**
     * "Guess you like": pick a tag with probability proportional to how many pages the reader has
     * read in works carrying it, search that tag, then pick one of the results at random.
     *
     * Up to three weighted tags are tried before falling back to `/random_recommend`, because a
     * tag can legitimately have no results (or only the work just read). Tag choice and ranking
     * live in [GuessEngine], which is pure and unit-tested.
     */
    suspend fun guess() {
        if (guessLoading) return
        guessLoading = true
        try {
            val counts = statsSnapshot.tagCounts
            if (counts.isEmpty()) {
                snackbar.showSnackbar(s.guessNeedData)
                return
            }
            var chosen: ComicListItem? = null
            // Pick without replacement, so three attempts mean three distinct tastes.
            val remaining = counts.toMutableMap()
            var attempt = 0
            while (attempt < 3 && chosen == null && remaining.isNotEmpty()) {
                attempt++
                val tag = GuessEngine.pickTag(remaining) ?: break
                remaining.remove(tag)
                when (val r = repo.adaptiveSearch(tag)) {
                    is RepoResult.Ok -> {
                        val pool = r.data.items.filter {
                            it.id.isNotBlank() && it.id != vm.lastGuessId
                        }
                        if (pool.isNotEmpty()) chosen = pool.random()
                    }
                    is RepoResult.Err -> Unit // try the next taste instead of failing outright
                }
            }
            // No tag produced a usable page (or only the previous pick): fall back to a blind roll.
            if (chosen == null) {
                when (val r = repo.randomRecommend()) {
                    is RepoResult.Ok -> chosen = r.data
                        .filter { it.id.isNotBlank() && it.id != vm.lastGuessId }
                        .ifEmpty { r.data }
                        .randomOrNull()
                    is RepoResult.Err -> {
                        snackbar.showSnackbar(r.message)
                        return
                    }
                }
            }
            val pick = chosen
            if (pick == null) {
                snackbar.showSnackbar(s.noResult)
            } else {
                vm.lastGuessId = pick.id
                navController.navigate(Routes.comicDetail(pick.id))
            }
        } finally {
            guessLoading = false
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

    LaunchedEffect(Unit) {
        // Keep the feed and the reader's scroll position when coming back from a comic (or from
        // another tab) - unless the previous attempt failed, in which case re-entering retries.
        if (vm.loaded && state.error == null) return@LaunchedEffect
        vm.loaded = true
        loadInitial()
    }

    // Seed from the last known position so switching tabs (which disposes this composition without
    // a save/restore cycle) does not snap the feed back to the top. When returning from a comic the
    // nav entry's own saved state wins and `init` is ignored, which is the same position anyway.
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = vm.firstVisibleIndex,
        initialFirstVisibleItemScrollOffset = vm.firstVisibleOffset,
    )
    DisposableEffect(gridState) {
        onDispose {
            vm.firstVisibleIndex = gridState.firstVisibleItemIndex
            vm.firstVisibleOffset = gridState.firstVisibleItemScrollOffset
        }
    }
    // Five full-span rows precede the feed: hero, dots, quick links, recent reads, section title.
    val leadingRows = if (recentItems.isEmpty()) 4 else 5
    LaunchedEffect(gridState, state.latest.size, recentItems.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !state.loading && last >= leadingRows + state.latest.size - 8) {
                    scope.launch { loadMore() }
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.latest.isEmpty() -> LoadingView()
            state.error != null && state.latest.isEmpty() -> ErrorView(state.error!!, onRetry = { scope.launch { loadInitial() } })
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.fillMaxSize(),
                state = gridState,
                // Bottom reserve lets the last row scroll clear of the floating nav pill.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = BottomNavReserve),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeHeader(
                        title = s.homeGreeting,
                        eyebrow = s.appName.uppercase(),
                        onSearch = { navController.navigate(Routes.search()) },
                        onRefresh = { scope.launch { loadInitial() } },
                    )
                }
                    if (state.promote.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            HeroCarousel(
                                repo = repo,
                                items = state.promote.take(5),
                                initialPage = vm.heroIndex,
                                onPageChanged = { vm.heroIndex = it },
                                onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                            )
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        QuickLinks(navController)
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SurpriseEntry(
                            icon = Icons.Filled.Shuffle,
                            label = s.randomComic,
                            hint = s.randomHint,
                            loading = randomLoading,
                            onClick = { scope.launch { surprise() } },
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SurpriseEntry(
                            icon = Icons.Filled.AutoAwesome,
                            label = s.guessYouLike,
                            hint = s.guessHint,
                            loading = guessLoading,
                            onClick = { scope.launch { guess() } },
                        )
                    }

                    if (recentItems.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                SectionHeader(title = s.recentReads)
                                RecentRow(repo, recentItems.take(10)) { entry ->
                                    navController.navigate(Routes.comicDetail(entry.albumId))
                                }
                            }
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = s.latest,
                            actionLabel = s.viewAll,
                            onAction = { navController.navigate(Routes.comicList("latest", s.latest)) },
                        )
                    }

                    gridItems(state.latest, key = { it.id }) { item ->
                        ComicCard(
                            item = item,
                            repo = repo,
                            onClick = { navController.navigate(Routes.comicDetail(item.id)) },
                        )
                    }

                if (loadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }
            }
        }
        // Above the floating nav pill, so a failed dice roll is visible instead of silent.
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BottomNavReserve),
        )
    }
}

/**
 * "Surprise me" entry: a quiet full-width row under the quick links.
 *
 * Deliberately not a fifth square tile - four tiles is a rhythm, five is a grid that almost fits.
 * As a row it also has space for the one line that explains what the button does.
 */
@Composable
private fun SurpriseEntry(
    icon: ImageVector,
    label: String,
    hint: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                hint,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Screen header: a quiet eyebrow, a large greeting and one circular action.
 *
 * This replaced the old full-width "search bar" strip that sat under the status bar and looked like
 * an input the user could type into but only ever navigated. The search field is now an explicit
 * affordance below the title, where its rounded shape reads as "tap to search".
 */
@Composable
private fun HomeHeader(
    title: String,
    eyebrow: String,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                fontSize = 10.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                fontSize = 29.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = LocalAppStrings.current.refresh,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSearch,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = LocalAppStrings.current.search,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

/**
 * Hero carousel: one large cover per page with the title and the primary action layered over a
 * gradient scrim, plus a line of dots.
 *
 * The old home screen opened with a 16:9 banner strip - too short to show cover art and too wide
 * to read as a "featured work". This is 4:5, which is the shape the source art is actually
 * cropped to.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(
    repo: AppRepository,
    items: List<ComicListItem>,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onItemClick: (ComicListItem) -> Unit,
) {
    val s = LocalAppStrings.current
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        pageCount = { items.size },
    )
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }

    Column {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
            val item = items[index]
            val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .clip(RoundedCornerShape(24.dp))
                    .glassClickable(shape = RoundedCornerShape(24.dp)) { onItemClick(item) },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.30f to Color.Transparent,
                                0.62f to Color(0xB3000000),
                                1f to Color(0xF20A0A0C),
                            ),
                        ),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(18.dp),
                ) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${s.quickWeek} · TOP ${index + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(
                        item.name,
                        fontSize = 26.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    Text(
                        listOfNotNull(
                            item.author?.takeIf { it.isNotBlank() },
                            item.category?.title?.takeIf { it.isNotBlank() },
                        ).joinToString(" · ").ifBlank { s.forYou },
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onItemClick(item) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            s.startReading,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
        if (items.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(items.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 2.5.dp)
                            .height(4.dp)
                            .width(if (active) 16.dp else 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Four primary destinations, then the "more content" links as quiet chips.
 *
 * The old quick-link row was eight identical 44dp icon boxes with 11sp labels - a wall of chrome
 * with no hierarchy. Four tiles get the icon + label treatment; the rest become text chips, which
 * is honest about them being secondary.
 */
@Composable
private fun QuickLinks(navController: NavHostController) {
    val s = LocalAppStrings.current
    val primary = listOf(
        Triple(s.quickWeek, Icons.Filled.Star) { navController.navigate(Routes.WEEK) },
        Triple(s.quickDaily, Icons.Filled.CalendarMonth) { navController.navigate(Routes.DAILY) },
        Triple(s.quickHot, Icons.Filled.Whatshot) { navController.navigate(Routes.HOT_TAGS) },
        Triple(s.quickCategories, Icons.Filled.Category) { navController.navigate(Routes.CATEGORIES) },
    )
    val secondary = listOf(
        Triple(s.quickNovels, Icons.Filled.Article) { navController.navigate(Routes.NOVELS) },
        Triple(s.quickMovies, Icons.Filled.LocalMovies) { navController.navigate(Routes.MOVIES) },
        Triple(s.quickGames, Icons.Filled.SportsEsports) { navController.navigate(Routes.GAMES) },
        Triple(s.quickBlogs, Icons.Filled.Forum) { navController.navigate(Routes.BLOGS) },
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            primary.forEach { (label, icon, onClick) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            secondary.forEach { (label, icon, onClick) ->
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Horizontal strip of recently opened works with their reading progress. */
@Composable
private fun RecentRow(
    repo: AppRepository,
    entries: List<HistoryManager.Entry>,
    onClick: (HistoryManager.Entry) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        listItems(entries, key = { it.albumId }) { entry ->
            Column(
                Modifier
                    .width(112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .glassClickable(shape = RoundedCornerShape(12.dp)) { onClick(entry) },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(repo.comicCover(entry.albumId, entry.updateAt))
                            .crossfade(true)
                            .build(),
                        contentDescription = entry.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (entry.hasProgress) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.White.copy(alpha = 0.18f)),
                        ) {
                            // The reader reports a page, not a fraction, so the bar is a coarse
                            // "somewhere in the middle" cue rather than a measured percentage.
                            Box(
                                Modifier
                                    .fillMaxWidth((((entry.pageIndex % 20) / 20f) + 0.08f).coerceIn(0.08f, 1f))
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
                Text(
                    entry.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    entry.episodeName?.takeIf { it.isNotBlank() } ?: "P${entry.pageIndex.coerceAtLeast(1)}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}
