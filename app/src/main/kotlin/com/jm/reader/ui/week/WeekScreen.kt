package com.jm.reader.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.FilterPill
import com.jm.reader.ui.components.FilterPillRow
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

internal data class WeekOption(val id: String, val title: String)

/**
 * Weekly chart state, kept in a ViewModel rather than in `remember`.
 *
 * This is the fix for the reported bug: tapping a comic pushed a detail destination on top, which
 * disposed the week screen's composition, so every `remember` - the loaded list, the selected
 * category, and the grid's scroll position - was thrown away. Coming back rebuilt the screen and
 * reloaded page 1 at scroll 0.
 *
 * `viewModel()` inside a navigation destination is scoped to that destination's back-stack entry,
 * so it survives being covered by the detail screen and is cleared only when the week screen is
 * actually popped. Holding the *items* here (not just the index) matters: if the list were still
 * empty on return, `rememberLazyGridState(initialIndex = ...)` would be clamped to 0 before the
 * network response arrived, and the position would be lost anyway.
 */
internal class WeekViewModel : ViewModel() {
    val categories = mutableStateOf<List<WeekOption>>(emptyList())
    val types = mutableStateOf<List<WeekOption>>(emptyList())
    val selectedCat = mutableStateOf("")
    val selectedType = mutableStateOf("")
    val items = mutableStateOf<List<ComicListItem>>(emptyList())
    val loading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)

    /** Set once the first load has been requested, so re-entering does not refetch. */
    var loaded = false

    /** Last visible cell, captured when the screen leaves composition. */
    var firstVisibleIndex = 0
    var firstVisibleOffset = 0
}

@Composable
fun WeekScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val vm: WeekViewModel = viewModel()
    var categories by vm.categories
    var types by vm.types
    var selectedCat by vm.selectedCat
    var selectedType by vm.selectedType
    var items by vm.items
    var loading by vm.loading
    var error by vm.error

    fun options(key: String, data: org.json.JSONObject): List<WeekOption> =
        data.optJSONArray(key)?.let { ja ->
            (0 until ja.length()).mapNotNull { i ->
                ja.optJSONObject(i)?.let { WeekOption(it.optString("id"), it.optString("title")) }
            }
        } ?: emptyList()

    suspend fun loadFilter(cat: String, type: String) {
        loading = true
        when (val r = repo.getWeekFilter(cat, type)) {
            is RepoResult.Ok -> { items = r.data; loading = false; error = null }
            is RepoResult.Err -> { error = r.message; loading = false }
        }
    }

    suspend fun loadWeek() {
        loading = true
        when (val r = repo.getWeek()) {
            is RepoResult.Ok -> {
                val cats = options("categories", r.data)
                val typs = options("type", r.data)
                categories = cats
                types = typs
                selectedCat = cats.firstOrNull()?.id ?: ""
                selectedType = typs.getOrNull(2)?.id ?: typs.firstOrNull()?.id ?: ""
                error = null
                loadFilter(selectedCat, selectedType)
            }
            is RepoResult.Err -> { error = r.message; loading = false }
        }
    }

    LaunchedEffect(Unit) {
        // Coming back from a comic must not reload: the list, filters and scroll are all still here.
        if (vm.loaded && vm.error.value == null) return@LaunchedEffect
        vm.loaded = true
        loadWeek()
    }

    // Seeded with the position captured on the way out, so the reader lands where they left.
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

    Scaffold(topBar = { AppTopBar(s.weekRanking, onBack = { navController.popBackStack() }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = s.weekHint,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )

            if (categories.isNotEmpty()) {
                FilterPillRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    categories.forEach { cat ->
                        FilterPill(
                            text = cat.title,
                            selected = selectedCat == cat.id,
                            onClick = {
                                selectedCat = cat.id
                                scope.launch {
                                    gridState.scrollToItem(0)
                                    loadFilter(cat.id, selectedType)
                                }
                            },
                        )
                    }
                }
            }

            if (types.isNotEmpty()) {
                FilterPillRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    types.forEach { t ->
                        FilterPill(
                            text = t.title,
                            selected = selectedType == t.id,
                            subtle = selectedType != t.id,
                            onClick = {
                                selectedType = t.id
                                scope.launch {
                                    gridState.scrollToItem(0)
                                    loadFilter(selectedCat, t.id)
                                }
                            },
                        )
                    }
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading && items.isEmpty() -> LoadingView()
                    error != null && items.isEmpty() -> ErrorView(
                        error!!,
                        onRetry = { scope.launch { loadFilter(selectedCat, selectedType) } },
                    )
                    items.isEmpty() -> ErrorView(
                        s.noResult,
                        onRetry = { scope.launch { loadFilter(selectedCat, selectedType) } },
                    )
                    else -> ComicGrid(
                        items = items,
                        repo = repo,
                        state = gridState,
                        ranked = true,
                        columnWidth = 100.dp,
                        onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }
}
