package com.jm.reader.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ArchiveBookRow
import com.jm.reader.ui.components.ArchiveSectionHeader
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.FilterPill
import com.jm.reader.ui.components.FilterPillRow
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.components.ShelfBank
import com.jm.reader.ui.components.ShelfItem
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.BottomNavReserve
import kotlinx.coroutines.launch

internal data class WeekOption(val id: String, val title: String)

/** Which chart the screen shows. 0 is the editorial 每周必看, 1-3 are time-based rankings. */
private const val MODE_PICKS = 0
private const val MODE_DAY = 1
private const val MODE_WEEK = 2
private const val MODE_MONTH = 3

/** The API answers 20 rows per page; a short page means there is nothing behind it. */
private const val PAGE_SIZE = 20

/**
 * Weekly / daily / monthly chart state.
 *
 * Kept in a ViewModel owned by the destination so opening a comic and coming back preserves the
 * chart, the selected week and the scroll position instead of reloading page 1.
 */
internal class WeekViewModel : ViewModel() {
    val mode = mutableIntStateOf(MODE_PICKS)
    val categories = mutableStateOf<List<WeekOption>>(emptyList())
    val types = mutableStateOf<List<WeekOption>>(emptyList())
    val selectedCat = mutableStateOf("")
    val selectedType = mutableStateOf("")
    val items = mutableStateOf<List<ComicListItem>>(emptyList())
    // Starts false: `loadPage` refuses to run while a load is in flight, and starting it true
    // made that guard swallow the very first request - the screen sat on its spinner forever.
    val loading = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)

    /** Last page requested. `/week/filter` is 0-based, `/categories/filter` is 1-based. */
    var page = -1
    var endReached = false
    var loaded = false
}

@Composable
fun WeekScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val vm: WeekViewModel = viewModel()
    var mode by vm.mode
    var categories by vm.categories
    var types by vm.types
    var selectedCat by vm.selectedCat
    var selectedType by vm.selectedType
    var items by vm.items
    var loading by vm.loading
    var loadingMore by vm.loadingMore
    var error by vm.error

    fun options(key: String, data: org.json.JSONObject): List<WeekOption> =
        data.optJSONArray(key)?.let { ja ->
            (0 until ja.length()).mapNotNull { i ->
                ja.optJSONObject(i)?.let { o ->
                    // `/week` calls the label `time` ("2026-10-13 ~ 10-19"); the type tabs use
                    // `title`. Reading only `title` is why the week boxes were blank.
                    WeekOption(
                        id = o.optString("id"),
                        title = o.optString("time").ifBlank { o.optString("title") },
                    )
                }
            }
        } ?: emptyList()

    suspend fun loadPage(reset: Boolean) {
        if (loading || loadingMore) return
        if (!reset && vm.endReached) return
        if (reset) {
            vm.page = if (mode == MODE_PICKS) -1 else 0
            vm.endReached = false
            items = emptyList()
            error = null
            loading = true
        } else {
            loadingMore = true
        }
        // The two endpoints count pages differently: `/week/filter` starts at 0 (that is what the
        // reference client sends), `/categories/filter` starts at 1.
        val next = if (mode == MODE_PICKS) vm.page + 1 else maxOf(vm.page + 1, 1)
        val result = if (mode == MODE_PICKS) {
            repo.getWeekFilter(selectedCat, selectedType, next)
        } else {
            repo.ranking(
                when (mode) {
                    MODE_DAY -> "d"
                    MODE_WEEK -> "w"
                    else -> "m"
                },
                next,
            )
        }
        when (result) {
            is RepoResult.Ok -> {
                val page = result.data
                items = if (reset) page else (items + page).distinctBy { it.id }
                vm.page = next
                // A full page may still be the last one; the next request returning empty will
                // flip the flag. A short page is definitive.
                vm.endReached = page.size < PAGE_SIZE
                error = null
            }
            is RepoResult.Err -> if (reset) error = result.message
        }
        loading = false
        loadingMore = false
    }

    suspend fun loadConfig() {
        when (val r = repo.getWeek()) {
            is RepoResult.Ok -> {
                categories = options("categories", r.data)
                types = options("type", r.data)
                selectedCat = categories.firstOrNull()?.id.orEmpty()
                selectedType = types.firstOrNull()?.id.orEmpty()
                loadPage(reset = true)
            }
            is RepoResult.Err -> {
                error = r.message
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (vm.loaded && vm.error.value == null) return@LaunchedEffect
        vm.loaded = true
        loadConfig()
    }

    val listState = rememberLazyListState()
    // Auto-load when the tail is within three rows, so a 20+ list is continuous instead of
    // requiring a tap on every page.
    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !loading && !loadingMore && !vm.endReached &&
                    last >= items.size + 4
                ) {
                    scope.launch { loadPage(reset = false) }
                }
            }
    }

    Scaffold(topBar = { AppTopBar(s.weekRanking, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && items.isEmpty() -> LoadingView()
                error != null && items.isEmpty() -> ErrorView(
                    error!!,
                    onRetry = { scope.launch { if (mode == MODE_PICKS && categories.isEmpty()) loadConfig() else loadPage(true) } },
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = BottomNavReserve,
                    ),
                ) {
                    item(key = "modes") {
                        FilterPillRow(contentPadding = PaddingValues(vertical = 6.dp)) {
                            val modes = listOf(
                                MODE_PICKS to s.weekRanking,
                                MODE_DAY to s.rankDaily,
                                MODE_WEEK to s.rankWeekly,
                                MODE_MONTH to s.rankMonthly,
                            )
                            modes.forEach { (value, label) ->
                                FilterPill(
                                    text = label,
                                    selected = mode == value,
                                    onClick = {
                                        if (mode == value) return@FilterPill
                                        mode = value
                                        scope.launch { loadPage(reset = true) }
                                    },
                                )
                            }
                        }
                    }

                    if (mode == MODE_PICKS) {
                        if (categories.isNotEmpty()) {
                            item(key = "weeks") {
                                FilterPillRow(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    categories.forEach { cat ->
                                        FilterPill(
                                            text = cat.title.ifBlank { "—" },
                                            selected = selectedCat == cat.id,
                                            subtle = selectedCat != cat.id,
                                            onClick = {
                                                selectedCat = cat.id
                                                scope.launch { loadPage(reset = true) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (types.isNotEmpty()) {
                            item(key = "types") {
                                FilterPillRow(contentPadding = PaddingValues(vertical = 4.dp)) {
                                    types.forEach { t ->
                                        FilterPill(
                                            text = t.title.ifBlank { t.id },
                                            selected = selectedType == t.id,
                                            subtle = selectedType != t.id,
                                            onClick = {
                                                selectedType = t.id
                                                scope.launch { loadPage(reset = true) }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "where") {
                        Text(
                            text = when (mode) {
                                MODE_PICKS -> s.weekHint
                                MODE_DAY -> s.rankDaily
                                MODE_WEEK -> s.rankWeekly
                                else -> s.rankMonthly
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                        )
                    }

                    if (items.isNotEmpty()) {
                        item(key = "shelf") {
                            ShelfBank(
                                items = items.take(10).map {
                                    ShelfItem(
                                        id = it.id,
                                        title = it.name,
                                        subtitle = listOfNotNull(it.author, it.category?.title)
                                            .filter { part -> part.isNotBlank() }
                                            .joinToString(" · "),
                                        cover = it.image.takeIf { url -> url.startsWith("http") }
                                            ?: repo.comicCover(it.id, it.updateAt),
                                    )
                                },
                                onOpen = { navController.navigate(Routes.comicDetail(it.id)) },
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        item(key = "list-title") {
                            val weekLabel = categories.firstOrNull { it.id == selectedCat }?.title.orEmpty()
                            val title = when (mode) {
                                MODE_PICKS -> if (weekLabel.isBlank()) s.weekRanking else weekLabel
                                MODE_DAY -> s.rankDaily
                                MODE_WEEK -> s.rankWeekly
                                else -> s.rankMonthly
                            }
                            ArchiveSectionHeader(
                                code = if (mode == MODE_PICKS) "01 / WEEK PICKS" else "02 / RANKING",
                                title = title,
                                trailing = items.size.toString(),
                            )
                        }
                        val rows = items
                        items(rows, key = { "w-" + it.id }) { item ->
                            ArchiveBookRow(
                                id = item.id,
                                title = item.name,
                                author = item.author.orEmpty().ifBlank { item.category?.title.orEmpty() },
                                cover = item.image.takeIf { url -> url.startsWith("http") }
                                    ?: repo.comicCover(item.id, item.updateAt),
                                onClick = { navController.navigate(Routes.comicDetail(item.id)) },
                            )
                        }
                    }

                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            when {
                                loadingMore -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                !vm.endReached -> TextButton(onClick = { scope.launch { loadPage(false) } }) {
                                    Text(s.loadMore)
                                }
                                items.isNotEmpty() -> Text(
                                    s.noMore,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
