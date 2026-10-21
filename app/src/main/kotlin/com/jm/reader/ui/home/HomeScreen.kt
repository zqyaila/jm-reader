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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.jm.reader.ui.components.ArchiveBookRow
import com.jm.reader.ui.components.ArchiveIndexTile
import com.jm.reader.ui.components.ArchiveTextLink
import com.jm.reader.ui.components.ArchivePlate
import com.jm.reader.ui.components.ArchiveReveal
import com.jm.reader.ui.components.ArchiveSectionHeader
import com.jm.reader.ui.components.ShelfBank
import com.jm.reader.ui.components.ShelfItem
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
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
            // The profile now carries a full per-tag aggregate; the dice only needs the weights.
            val counts = statsSnapshot.tagCounts.mapValues { it.value.weight }
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
                        // A work the reader marked "萎了" is never handed back; skipping it here
                        // (rather than blacklisting the tag) keeps the rest of the tag usable.
                        val disliked = statsSnapshot.dislikedIds
                        val pool = r.data.items.filter {
                            it.id.isNotBlank() && it.id != vm.lastGuessId && it.id !in disliked
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
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = vm.firstVisibleIndex,
        initialFirstVisibleItemScrollOffset = vm.firstVisibleOffset,
    )
    DisposableEffect(listState) {
        onDispose {
            vm.firstVisibleIndex = listState.firstVisibleItemIndex
            vm.firstVisibleOffset = listState.firstVisibleItemScrollOffset
        }
    }
    // Five full-span rows precede the feed: hero, dots, quick links, recent reads, section title.
    val leadingRows = if (recentItems.isEmpty()) 4 else 5
    LaunchedEffect(listState, state.latest.size, recentItems.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !state.loading && last >= leadingRows + state.latest.size - 8) {
                    scope.launch { loadMore() }
                }
            }
    }

    // Replays the stagger when a new page of content lands; a scroll does not change it.
    val revealGeneration = state.latest.firstOrNull()?.id ?: "empty"

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.loading && state.latest.isEmpty() -> LoadingView()
            state.error != null && state.latest.isEmpty() ->
                ErrorView(state.error!!, onRetry = { scope.launch { loadInitial() } })
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                // Bottom reserve lets the last row scroll clear of the floating nav pill.
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = BottomNavReserve),
            ) {
                item(key = "masthead") {
                    ArchiveReveal(index = 0, generation = revealGeneration) {
                        HomeHeader(
                            title = s.homeGreeting,
                            eyebrow = "JM-READER / 18COMIC",
                            onSearch = { navController.navigate(Routes.search()) },
                            onRefresh = { scope.launch { loadInitial() } },
                        )
                    }
                }

                if (state.promote.isNotEmpty()) {
                    item(key = "shelf-header") {
                        ArchiveSectionHeader(code = "01 / FEATURED", title = s.forYou)
                    }
                    item(key = "shelf") {
                        ShelfBank(
                            items = state.promote.take(10).map {
                                ShelfItem(
                                    id = it.id,
                                    title = it.name,
                                    subtitle = listOfNotNull(it.author, it.category?.title)
                                        .filter { part -> part.isNotBlank() }
                                        .joinToString(" · "),
                                    cover = it.image,
                                )
                            },
                            onOpen = { navController.navigate(Routes.comicDetail(it.id)) },
                            animateEntrance = true,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                item(key = "index") {
                    ArchiveReveal(index = 2, generation = revealGeneration) {
                        QuickLinks(navController)
                    }
                }

                item(key = "dice") {
                    ArchiveReveal(index = 3, generation = revealGeneration) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ArchivePlate(
                                title = s.randomComic,
                                hint = s.randomHint,
                                code = "01 / RANDOM",
                                onClick = { scope.launch { surprise() } },
                                modifier = Modifier.weight(1f),
                                trailing = {
                                    if (randomLoading) {
                                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.5.dp)
                                    }
                                },
                            )
                            ArchivePlate(
                                title = s.guessYouLike,
                                hint = s.guessHint,
                                code = "02 / GUESS",
                                onClick = { scope.launch { guess() } },
                                modifier = Modifier.weight(1f),
                                trailing = {
                                    if (guessLoading) {
                                        CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.5.dp)
                                    }
                                },
                            )
                        }
                    }
                }

                if (recentItems.isNotEmpty()) {
                    item(key = "recent-header") {
                        ArchiveSectionHeader(code = "02 / READING LOG", title = s.recentReads)
                    }
                    item(key = "recent") {
                        Column {
                            recentItems.take(4).forEach { entry ->
                                ArchiveBookRow(
                                    id = entry.albumId,
                                    title = entry.name,
                                    author = entry.author.orEmpty().ifBlank { "阅读记录" },
                                    cover = repo.comicCover(entry.albumId, entry.updateAt),
                                    onClick = { navController.navigate(Routes.comicDetail(entry.albumId)) },
                                )
                            }
                        }
                    }
                }

                item(key = "latest-header") {
                    ArchiveSectionHeader(
                        code = "03 / CATALOG",
                        title = s.latest,
                        actionLabel = s.viewAll,
                        onAction = { navController.navigate(Routes.comicList("latest", s.latest)) },
                    )
                }

                items(state.latest, key = { it.id }) { item ->
                    ArchiveBookRow(
                        id = item.id,
                        title = item.name,
                        author = item.author.orEmpty().ifBlank { item.category?.title.orEmpty() },
                        cover = item.image,
                        onClick = { navController.navigate(Routes.comicDetail(item.id)) },
                    )
                }

                if (loadingMore) {
                    item(key = "loading-more") {
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
    // Masthead in the archive vocabulary: a solid ink "JM" block, the greeting, and the catalogue
    // code under it. The brand block is the only filled shape in the header - it is what makes the
    // page read as a register rather than as a feed.
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 30.dp, height = 34.dp)
                .background(MaterialTheme.colorScheme.onBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "JM",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                color = MaterialTheme.colorScheme.background,
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = title,
                fontSize = 23.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.7).sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = eyebrow,
                style = com.jm.reader.ui.theme.CodeTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(3.dp)),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = LocalAppStrings.current.refresh,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onSearch,
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(3.dp)),
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
        Triple(s.quickWeek, "01 / WEEK") { navController.navigate(Routes.WEEK) },
        Triple(s.quickDaily, "02 / DAILY") { navController.navigate(Routes.DAILY) },
        Triple(s.quickHot, "03 / RANKING") { navController.navigate(Routes.HOT_TAGS) },
        Triple(s.quickCategories, "04 / INDEX") { navController.navigate(Routes.CATEGORIES) },
    )
    val secondary = listOf(
        Triple(s.quickNovels, "05") { navController.navigate(Routes.NOVELS) },
        Triple(s.quickMovies, "06") { navController.navigate(Routes.MOVIES) },
        Triple(s.quickGames, "07") { navController.navigate(Routes.GAMES) },
        Triple(s.quickBlogs, "08") { navController.navigate(Routes.BLOGS) },
    )

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            primary.forEach { (label, code, onClick) ->
                ArchiveIndexTile(
                    code = code,
                    label = label,
                    onClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            secondary.forEach { (label, code, onClick) ->
                ArchiveTextLink(code = code, label = label, onClick = onClick)
            }
        }
    }
}
