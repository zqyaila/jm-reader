package com.jm.reader.ui.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import kotlinx.coroutines.launch

/**
 * Keeps a paged list (and its scroll position) alive while its detail screen is on top.
 *
 * Same bug class as the weekly chart: `remember` state dies with the composition when a detail
 * destination covers this one. The ViewModel is scoped to this destination's back-stack entry, so
 * it survives for exactly as long as the list does.
 */
internal class ComicListViewModel : ViewModel() {
    val items = mutableStateOf<List<ComicListItem>>(emptyList())
    val loading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    val page = mutableIntStateOf(1)
    val endReached = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)

    /** The (type, id) the held list was fetched for. */
    var loadedKey: String? = null

    var firstVisibleIndex = 0
    var firstVisibleOffset = 0
}

/**
 * Generic paged comic list. `type`:
 *  - "category"      -> categories/filter with c = [id]
 *  - "promote"       -> promote_list with id = [id]
 *  - "serialization" -> serialization
 *  - anything else   -> latest
 */
@Composable
fun ComicListScreen(
    navController: NavHostController,
    type: String,
    title: String,
    id: String,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val vm: ComicListViewModel = viewModel()
    var items by vm.items
    var loading by vm.loading
    var error by vm.error
    var page by vm.page
    var endReached by vm.endReached
    var loadingMore by vm.loadingMore

    val key = "$type|$id"

    suspend fun fetch(pageNo: Int): RepoResult<List<ComicListItem>> = when (type) {
        "category" -> repo.categoriesFilter(id, "", pageNo)
        "promote" -> repo.getPromoteList(id, pageNo)
        "serialization" -> repo.getSerializationMore(null, null, pageNo)
        else -> repo.getLatest(pageNo)
    }

    suspend fun loadInitial() {
        loading = true
        error = null
        page = 1
        when (val r = fetch(1)) {
            is RepoResult.Ok -> {
                items = r.data
                endReached = r.data.isEmpty()
            }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    suspend fun loadMore() {
        if (loadingMore || endReached) return
        loadingMore = true
        val next = page + 1
        when (val r = fetch(next)) {
            is RepoResult.Ok -> {
                items = (items + r.data).distinctBy { it.id }
                page = next
                endReached = r.data.isEmpty()
            }
            is RepoResult.Err -> Unit
        }
        loadingMore = false
    }

    LaunchedEffect(key) {
        // Retry after a failure; otherwise a re-entry (back from a comic) keeps what is on screen.
        if (vm.loadedKey == key && error == null) return@LaunchedEffect
        vm.loadedKey = key
        vm.firstVisibleIndex = 0
        vm.firstVisibleOffset = 0
        loadInitial()
    }

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

    LaunchedEffect(gridState, items.size) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && !loading && !loadingMore && last >= items.size - 8) {
                    scope.launch { loadMore() }
                }
            }
    }

    Scaffold(topBar = { AppTopBar(title.ifBlank { s.list }, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && items.isEmpty() -> LoadingView()
                error != null && items.isEmpty() -> ErrorView(error!!, onRetry = { scope.launch { loadInitial() } })
                items.isEmpty() -> ErrorView(s.noResult, onRetry = { scope.launch { loadInitial() } })
                else -> ComicGrid(
                    items = items,
                    repo = repo,
                    state = gridState,
                    onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                )
            }
            if (loadingMore) {
                Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
        }
    }
}
