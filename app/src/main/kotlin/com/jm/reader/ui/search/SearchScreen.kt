package com.jm.reader.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.model.TagItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSearchHistoryManager
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ComicCard
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.ui.theme.GlassShape
import com.jm.reader.ui.theme.GlassSizes
import com.jm.reader.ui.theme.glassClickable
import com.jm.reader.ui.theme.glassSurface
import com.jm.reader.util.JmId
import kotlinx.coroutines.launch

/**
 * One adaptive search box.
 *
 * There is deliberately **no 作品 / 作者 / ID chooser**: `search_type=site` is the scope the site
 * itself uses for a general query and, verified against the live endpoint, it matches titles,
 * authors and tags in one go. When the text is an album id the API answers with `redirect_aid`
 * and we jump straight to the album; results are grouped by author automatically when the query
 * is clearly an author name.
 */
/**
 * Holds the search screen's state for as long as its navigation entry is on the back stack.
 *
 * This exists for one reason: the reader searches, taps a comic, then presses back. Opening the
 * comic disposes the search screen's composition, so state kept in `remember` did not survive and
 * the search came back blank. A `ViewModel` obtained with `viewModel()` is scoped to the
 * `NavBackStackEntry`, which is retained while the detail screen sits on top of it.
 *
 * The properties are exposed as `MutableState` so the screen can keep using the familiar
 * `var x by vm.x` delegation.
 */
class SearchViewModel : ViewModel() {
    val query = mutableStateOf("")
    val submitted = mutableStateOf("")
    val hotTags = mutableStateOf<List<TagItem>>(emptyList())
    val random = mutableStateOf<List<ComicListItem>>(emptyList())
    val results = mutableStateOf<List<ComicListItem>>(emptyList())
    val total = mutableIntStateOf(0)
    val page = mutableIntStateOf(1)
    val loading = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)
    val endReached = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val opening = mutableStateOf(false)
    val groupByAuthor = mutableStateOf(false)

    /** Set once the entry has been seeded, so returning from a detail does not wipe the query. */
    var seeded = false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    navController: NavHostController,
    initialHotTagsOnly: Boolean = false,
    @Suppress("UNUSED_PARAMETER") initialMode: String = "work",
    initialQuery: String = "",
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val searchHistory = LocalSearchHistoryManager.current
    val recentSearches by searchHistory.entries.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val gridState = rememberLazyGridState()
    val snackbar = remember { SnackbarHostState() }

    // Search state lives in a ViewModel scoped to this navigation entry rather than in `remember`.
    //
    // Opening a comic disposes this screen's composition, so every `remember` was thrown away:
    // coming back from a detail page re-created the screen with a blank query and no results, and
    // the seeding effect below ran again and overwrote everything. The navigation entry stays on
    // the back stack while the detail screen is open, so its ViewModel survives the round trip
    // (and a configuration change too).
    val vm: SearchViewModel = viewModel()
    var query by vm.query
    var submitted by vm.submitted
    var hotTags by vm.hotTags
    var random by vm.random
    var results by vm.results
    var total by vm.total
    var page by vm.page
    var loading by vm.loading
    var loadingMore by vm.loadingMore
    var endReached by vm.endReached
    var error by vm.error
    var opening by vm.opening
    var groupByAuthor by vm.groupByAuthor

    /** Digits only, or a `JM123456` style id, or a link containing one. */
    fun idCandidate(raw: String): String? = JmId.parse(raw)

    suspend fun loadDiscover() {
        when (val h = repo.hotTags()) { is RepoResult.Ok -> hotTags = h.data; is RepoResult.Err -> Unit }
        when (val rr = repo.randomRecommend()) { is RepoResult.Ok -> random = rr.data; is RepoResult.Err -> Unit }
    }

    /** An author query is one where at least half the hits are credited to the typed text. */
    fun looksLikeAuthor(keyword: String, items: List<ComicListItem>): Boolean {
        if (items.size < 3) return false
        val named = items.count { it.author?.trim().equals(keyword.trim(), ignoreCase = true) }
        return named * 2 >= items.size
    }

    suspend fun runSearch(keyword: String) {
        // Remember what was actually searched, so the search box can offer it back.
        searchHistory.record(keyword)
        submitted = keyword
        results = emptyList()
        total = 0
        page = 1
        endReached = false
        error = null
        loading = true
        when (val r = repo.adaptiveSearch(keyword, 1)) {
            is RepoResult.Ok -> {
                val data = r.data
                val redirect = data.redirectAid
                if (redirect != null) {
                    // The server itself resolved the query to an album id.
                    submitted = ""
                    loading = false
                    navController.navigate(Routes.comicDetail(redirect))
                    return
                }
                results = data.items
                total = if (data.total > 0) data.total else data.items.size
                groupByAuthor = looksLikeAuthor(keyword, data.items)
                endReached = data.items.isEmpty() || results.size >= total
                loading = false
            }
            is RepoResult.Err -> {
                error = r.message
                loading = false
            }
        }
    }

    /** Appends the next page; stops paging when the API stops returning new comics. */
    suspend fun loadMore() {
        if (loading || loadingMore || endReached || submitted.isBlank()) return
        loadingMore = true
        when (val r = repo.adaptiveSearch(submitted, page + 1)) {
            is RepoResult.Ok -> {
                val merged = (results + r.data.items).distinctBy { it.id }
                if (merged.size == results.size) {
                    endReached = true
                } else {
                    results = merged
                    page += 1
                    if (r.data.total > 0) total = r.data.total
                    endReached = merged.size >= total
                }
            }
            is RepoResult.Err -> endReached = true
        }
        loadingMore = false
    }

    /** Direct album open, used when a numeric query is not redirected by the server. */
    fun openById(raw: String) {
        val id = idCandidate(raw)
        if (id == null) {
            scope.launch { snackbar.showSnackbar(s.searchIdInvalid) }
            return
        }
        keyboard?.hide()
        scope.launch {
            opening = true
            val result = repo.getAlbum(id)
            opening = false
            when (result) {
                is RepoResult.Ok ->
                    if (result.data.id.isBlank()) snackbar.showSnackbar(s.searchIdNotFound)
                    else navController.navigate(Routes.comicDetail(id))
                is RepoResult.Err -> snackbar.showSnackbar(result.message)
            }
        }
    }

    fun submit() {
        val keyword = query.trim()
        if (keyword.isBlank()) return
        keyboard?.hide()
        scope.launch { runSearch(keyword) }
    }

    LaunchedEffect(initialQuery) {
        // Only on the first composition of this navigation entry. Returning from a comic re-enters
        // this effect (the screen is re-created), and re-running it would clear the query the
        // reader is coming back to.
        if (vm.seeded) return@LaunchedEffect
        vm.seeded = true
        loadDiscover()
        if (initialQuery.isNotBlank()) {
            query = initialQuery
            runSearch(initialQuery)
        }
    }

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !loading && !loadingMore && !endReached &&
                    submitted.isNotBlank() && last >= gridState.layoutInfo.totalItemsCount - 4
                ) {
                    loadMore()
                }
            }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(s.search, onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!initialHotTagsOnly) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(s.searchAdaptiveHint) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = s.clear)
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        singleLine = true,
                        shape = GlassShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { submit() }, enabled = query.isNotBlank() && !loading) {
                        Text(s.search)
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> LoadingView()
                    error != null && results.isEmpty() ->
                        ErrorView(error!!, onRetry = { scope.launch { runSearch(submitted) } })
                    submitted.isNotBlank() && results.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                    ) {
                        val id = idCandidate(submitted)
                        if (id != null) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AssistChip(
                                    onClick = { openById(id) },
                                    label = { Text(s.searchJumpIdFmt.format(id)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                )
                            }
                        }
                        EmptyView(s.noResult, Modifier.fillMaxSize())
                    }
                    submitted.isNotBlank() && groupByAuthor -> AuthorResultGrid(
                        results = results,
                        total = total,
                        loadingMore = loadingMore,
                        endReached = endReached,
                        repo = repo,
                        s = s,
                        gridState = gridState,
                        onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                    )
                    submitted.isNotBlank() -> WorkResultGrid(
                        results = results,
                        total = total,
                        loadingMore = loadingMore,
                        endReached = endReached,
                        repo = repo,
                        s = s,
                        gridState = gridState,
                        idCandidate = idCandidate(query),
                        opening = opening,
                        onOpenId = { openById(it) },
                        onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                    )
                    else -> DiscoverPane(
                        hotTags = hotTags,
                        recent = recentSearches,
                        random = random,
                        repo = repo,
                        onTagClick = { tag ->
                            query = tag
                            scope.launch { runSearch(tag) }
                        },
                        onRecentClick = { term ->
                            query = term
                            keyboard?.hide()
                            scope.launch { runSearch(term) }
                        },
                        onClearRecent = { searchHistory.clear() },
                        onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                    )
                }
            }
        }
    }
}

/** Result count line shared by the work / author grids. */
@Composable
private fun ResultSummary(total: Int, shown: Int) {
    val count = if (total > 0) total else shown
    Text(
        text = LocalAppStrings.current.searchTotalFmt.format(count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun ListFooter(loadingMore: Boolean, endReached: Boolean, shown: Int) {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        when {
            loadingMore -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            endReached && shown > 0 -> Text(
                LocalAppStrings.current.noMore,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkResultGrid(
    results: List<ComicListItem>,
    total: Int,
    loadingMore: Boolean,
    endReached: Boolean,
    repo: AppRepository,
    s: AppStrings,
    gridState: LazyGridState,
    idCandidate: String?,
    opening: Boolean,
    onOpenId: (String) -> Unit,
    onItemClick: (ComicListItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (idCandidate != null) {
            item(span = { GridItemSpan(3) }, key = "id-hint") {
                AssistChip(
                    onClick = { onOpenId(idCandidate) },
                    enabled = !opening,
                    label = { Text(s.searchJumpIdFmt.format(idCandidate)) },
                    leadingIcon = {
                        if (opening) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                )
            }
        }
        item(span = { GridItemSpan(3) }, key = "summary") { ResultSummary(total, results.size) }
        gridItems(results, key = { it.id }) { item ->
            ComicCard(item = item, repo = repo, onClick = { onItemClick(item) }, showAuthor = true)
        }
        item(span = { GridItemSpan(3) }, key = "footer") {
            ListFooter(loadingMore = loadingMore, endReached = endReached, shown = results.size)
        }
    }
}

/**
 * Author results, grouped by author name so every matched author keeps their works together.
 * Selected automatically when the query looks like an author name.
 */
@Composable
private fun AuthorResultGrid(
    results: List<ComicListItem>,
    total: Int,
    loadingMore: Boolean,
    endReached: Boolean,
    repo: AppRepository,
    s: AppStrings,
    gridState: LazyGridState,
    onItemClick: (ComicListItem) -> Unit,
) {
    val groups = remember(results, s) {
        results.groupBy { it.author?.takeIf { a -> a.isNotBlank() } ?: s.searchAuthor }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(3) }, key = "summary") { ResultSummary(total, results.size) }
        groups.forEach { (author, works) ->
            item(span = { GridItemSpan(3) }, key = "author-$author") {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            author,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 6.dp),
                        )
                        Text(
                            s.searchTotalFmt.format(works.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                }
            }
            gridItems(works, key = { it.id }) { item ->
                ComicCard(item = item, repo = repo, onClick = { onItemClick(item) }, showAuthor = false)
            }
        }
        item(span = { GridItemSpan(3) }, key = "footer") {
            ListFooter(loadingMore = loadingMore, endReached = endReached, shown = results.size)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverPane(
    hotTags: List<TagItem>,
    recent: List<String>,
    random: List<ComicListItem>,
    repo: AppRepository,
    onTagClick: (String) -> Unit,
    onRecentClick: (String) -> Unit,
    onClearRecent: () -> Unit,
    onItemClick: (ComicListItem) -> Unit,
) {
    val s = LocalAppStrings.current
    LazyColumn(Modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp, 12.dp, 4.dp, 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.searchHistory,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearRecent) { Text(s.searchHistoryClear) }
                }
            }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recent.forEach { term ->
                        SuggestionChip(
                            onClick = { onRecentClick(term) },
                            label = { Text(term, maxLines = 1) },
                        )
                    }
                }
            }
        }
        if (hotTags.isNotEmpty()) {
            item {
                Text(
                    s.hotTagsTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp, 12.dp, 12.dp, 4.dp),
                )
            }
            item {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    hotTags.take(30).forEach { tag ->
                        SuggestionChip(
                            onClick = { onTagClick(tag.title) },
                            label = { Text("#${tag.title}", maxLines = 1) },
                        )
                    }
                }
            }
        }
        if (random.isNotEmpty()) {
            item {
                Text(
                    s.forYou,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp, 16.dp, 12.dp, 4.dp),
                )
            }
            items(random.take(24), key = { it.id }) { item ->
                RandomRow(item, repo, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun RandomRow(
    item: ComicListItem,
    repo: AppRepository,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // Without this the row was inert: `onClick` was accepted as a parameter and then
            // dropped on the floor, so "为你推荐" / 热门 reads never navigated anywhere.
            .glassClickable(shape = GlassShape, onClick = onClick)
            .glassSurface(shape = GlassShape)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val cover = if (item.image.isNotBlank()) item.image else repo.comicCover(item.id, item.updateAt)
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp, 74.dp).clip(RoundedCornerShape(GlassSizes.RadiusInner)),
        )
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(item.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
            item.author?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.category?.title?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
