package com.jm.reader.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.download.DownloadManager
import com.jm.reader.data.history.HistoryManager
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.BottomNavReserve
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalDownloadManager
import com.jm.reader.ui.LocalHistoryManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.components.ComicGrid
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.util.JmId
import com.jm.reader.ui.theme.GlassBar
import com.jm.reader.ui.theme.GlassShape
import com.jm.reader.ui.theme.glassClickable
import com.jm.reader.ui.theme.glassSurface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 书库 with three independent tabs.
 *
 * Only **收藏** is server-side and therefore needs an account. **历史** and **下载** are local, so
 * the screen opens on 历史 when nobody is logged in instead of walling the whole tab behind a
 * login prompt - that wall was what made the local history look unusable.
 */
@Composable
fun LibraryScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val downloadManager = LocalDownloadManager.current
    val history = LocalHistoryManager.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    // Observed instead of read once: the login state changes while this screen is on screen.
    val loggedIn by session.loggedInFlow.collectAsState()
    // Start on the first tab that actually works for the current login state.
    var tab by rememberSaveable { mutableIntStateOf(if (session.isLoggedIn) TAB_FAVORITES else TAB_HISTORY) }

    Scaffold(
        modifier = modifier,
        // The tab host already applied the status-bar inset, so this inner Scaffold must not add
        // it again.
        contentWindowInsets = WindowInsets(0),
        // Near-opaque canvas so list rows and labels keep their contrast; the tab header above
        // is the glass surface.
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GlassBar(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        s.library,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp),
                    )
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { positions ->
                            if (tab < positions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(positions[tab]),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    ) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(s.favorites) })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(s.history) })
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(s.downloads) })
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                // Favorites are server-side and need an account; history and downloads are local.
                0 -> if (loggedIn) FavoritesList(navController, repo, s) else LoginRequired(navController, s)
                1 -> HistoryList(navController, repo, history, loggedIn, s)
                else -> DownloadsList(navController, downloadManager, s, scope)
            }
        }
    }
}

private const val TAB_FAVORITES = 0
private const val TAB_HISTORY = 1

@Composable
private fun LoginRequired(
    navController: NavHostController,
    s: AppStrings,
    message: String = s.libraryLoginRequired,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { navController.navigate(Routes.login()) }, modifier = Modifier.padding(top = 12.dp)) {
            Text(s.goLogin)
        }
    }
}

/**
 * Favorites survive leaving the library tab (and opening a comic from it) for the same reason the
 * other tabs do: the state lives in a ViewModel owned by the `Routes.MAIN` back-stack entry.
 */
internal class FavoritesViewModel : ViewModel() {
    val items = mutableStateOf<List<ComicListItem>>(emptyList())
    val loading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    val reload = mutableStateOf(0)
    var loaded = false
    var lastLoadedKey = 0
}

@Composable
private fun FavoritesList(
    navController: NavHostController,
    repo: AppRepository,
    s: AppStrings,
) {
    val vm: FavoritesViewModel = viewModel()
    var items by vm.items
    var loading by vm.loading
    var error by vm.error
    var reload by vm.reload

    LaunchedEffect(reload) {
        // First entry, an explicit retry, or recovering from a failure - not merely re-entering
        // the tab.
        if (vm.loaded && reload == vm.lastLoadedKey && error == null) return@LaunchedEffect
        vm.loaded = true
        vm.lastLoadedKey = reload
        loading = true
        when (val r = repo.favorites(1)) {
            is RepoResult.Ok -> { items = r.data; error = null }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingView()
            // A real retry control: the only recovery used to be leaving and re-entering the tab.
            error != null -> ErrorView(error!!, onRetry = { reload++ }, modifier = Modifier.fillMaxSize())
            items.isEmpty() -> EmptyView(s.emptyFavorites, Modifier.fillMaxSize())
            else -> ComicGrid(
                items = items,
                repo = repo,
                onItemClick = { navController.navigate(Routes.comicDetail(it.id)) },
                // Clears the floating glass nav pill.
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = BottomNavReserve),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Which history the reader is looking at: this device, or the account's server-side copy. */
private const val HISTORY_LOCAL = 0
private const val HISTORY_CLOUD = 1

internal class HistoryViewModel : ViewModel() {
    /** Remembered so returning to the tab keeps the reader on the source they picked. */
    val source = mutableIntStateOf(HISTORY_LOCAL)
}

/** Paged cloud history, from `GET /watch_list`. */
internal class CloudHistoryViewModel : ViewModel() {
    val items = mutableStateOf<List<ComicListItem>>(emptyList())
    val total = mutableIntStateOf(0)
    val page = mutableIntStateOf(0)
    val loading = mutableStateOf(false)
    val loadingMore = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val endReached = mutableStateOf(false)
    var loaded = false
}

/**
 * History, split by where it lives.
 *
 * **本机记录** is the on-device list ([HistoryManager]): written on every open, works offline and
 * without an account, and can be cleared. **云端记录** is the account's own list on the server
 * (`GET /watch_list`), which the app has always *written* to via `addWatch` but never read back -
 * which is why it looked permanently empty.
 */
@Composable
private fun HistoryList(
    navController: NavHostController,
    repo: AppRepository,
    history: HistoryManager,
    loggedIn: Boolean,
    s: AppStrings,
) {
    val vm: HistoryViewModel = viewModel()
    var source by vm.source

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = source == HISTORY_LOCAL,
                onClick = { source = HISTORY_LOCAL },
                label = { Text(s.historyLocal) },
            )
            FilterChip(
                selected = source == HISTORY_CLOUD,
                onClick = { source = HISTORY_CLOUD },
                label = { Text(s.historyCloud) },
            )
        }
        Box(Modifier.fillMaxSize()) {
            if (source == HISTORY_LOCAL) {
                LocalHistory(navController, repo, history, s)
            } else {
                CloudHistory(navController, repo, loggedIn, s)
            }
        }
    }
}

/** The on-device list: newest first, offline, clearable. */
@Composable
private fun LocalHistory(
    navController: NavHostController,
    repo: AppRepository,
    history: HistoryManager,
    s: AppStrings,
) {
    val entries by history.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.emptyHistory, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    s.libraryHistoryHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        s.historyCountFmt.format(entries.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { confirmClear = true }) { Text(s.historyClear) }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = BottomNavReserve),
                ) {
                    items(entries, key = { it.albumId }) { entry ->
                        HistoryRow(
                            entry = entry,
                            repo = repo,
                            s = s,
                            onClick = { navController.navigate(Routes.comicDetail(entry.albumId)) },
                            onDelete = { history.remove(entry.albumId) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(s.historyClear) },
            text = { Text(s.historyClearConfirm) },
            confirmButton = {
                TextButton(onClick = { history.clear(); confirmClear = false }) { Text(s.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(s.cancel) }
            },
        )
    }
}

/** The account's server-side history. Needs a session; the server rejects anonymous reads. */
@Composable
private fun CloudHistory(
    navController: NavHostController,
    repo: AppRepository,
    loggedIn: Boolean,
    s: AppStrings,
) {
    if (!loggedIn) {
        // Same affordance as the favorites tab: say why it is empty and offer the way in.
        LoginRequired(navController, s, message = s.cloudHistoryLogin)
        return
    }

    val vm: CloudHistoryViewModel = viewModel()
    var items by vm.items
    var total by vm.total
    var page by vm.page
    var loading by vm.loading
    var loadingMore by vm.loadingMore
    var error by vm.error
    var endReached by vm.endReached
    val scope = rememberCoroutineScope()

    suspend fun load(reset: Boolean) {
        if (loading || loadingMore) return
        if (!reset && endReached) return
        if (reset) {
            page = 0
            items = emptyList()
            endReached = false
            error = null
        }
        val first = page == 0
        if (first) loading = true else loadingMore = true
        try {
            val next = if (first) 1 else page + 1
            when (val r = repo.cloudHistory(next)) {
                is RepoResult.Ok -> {
                    val merged =
                        if (first) r.data.items else (items + r.data.items).distinctBy { it.id }
                    items = merged
                    total = maxOf(r.data.total, merged.size)
                    page = next
                    endReached = r.data.items.isEmpty() || merged.size >= total
                    error = null
                }
                is RepoResult.Err -> if (first) error = r.message
            }
        } finally {
            // Also on cancellation, so the screen cannot be left stuck on a spinner.
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(Unit) {
        // Keep the list when switching tabs / coming back, but retry after a failure.
        if (vm.loaded && error == null) return@LaunchedEffect
        vm.loaded = true
        load(reset = true)
    }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> LoadingView()
            error != null && items.isEmpty() ->
                ErrorView(error!!, onRetry = { scope.launch { load(reset = true) } }, modifier = Modifier.fillMaxSize())
            items.isEmpty() -> EmptyView(s.emptyCloudHistory, Modifier.fillMaxSize())
            else -> {
                val listState = rememberLazyListState()
                // Page in as the reader reaches the end, the same way the home feed does.
                LaunchedEffect(listState, items.size) {
                    snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                        .collect { last ->
                            if (last != null && last >= items.size - 4) load(reset = false)
                        }
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = BottomNavReserve),
                ) {
                    item(key = "cloud-count") {
                        Text(
                            s.historyCountFmt.format(total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        CloudHistoryRow(
                            item = item,
                            repo = repo,
                            s = s,
                            onClick = { navController.navigate(Routes.comicDetail(item.id)) },
                        )
                    }
                    if (loadingMore) {
                        item(key = "cloud-more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A cloud-history row. Unlike the local one it has no progress and nothing to delete. */
@Composable
private fun CloudHistoryRow(
    item: ComicListItem,
    repo: AppRepository,
    s: AppStrings,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .glassClickable(shape = GlassShape, onClick = onClick)
            .glassSurface(shape = GlassShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(repo.comicCover(item.id, item.updateAt))
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp, 70.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                item.name.ifBlank { item.id },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.author?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            JmId.display(item.id).takeIf { it.isNotEmpty() }?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryManager.Entry,
    repo: AppRepository,
    s: AppStrings,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .glassClickable(shape = GlassShape, onClick = onClick)
            .glassSurface(shape = GlassShape)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(repo.comicCover(entry.albumId, entry.updateAt))
                .crossfade(true)
                .build(),
            contentDescription = entry.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp, 70.dp).clip(RoundedCornerShape(10.dp)),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                entry.name.ifBlank { entry.albumId },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            entry.author?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.hasProgress) {
                val resume = listOfNotNull(
                    entry.episodeName?.takeIf { name -> name.isNotBlank() },
                    "P${entry.pageIndex}",
                ).joinToString(" · ")
                Text(
                    s.historyResumeFmt.format(resume),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                formatViewedAt(entry.viewedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = s.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatViewedAt(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

@Composable
private fun DownloadsList(
    navController: NavHostController,
    downloadManager: DownloadManager,
    s: AppStrings,
    scope: CoroutineScope,
) {
    val albums by downloadManager.albums.collectAsState()
    val downloading by downloadManager.downloading.collectAsState()
    var deleteTarget by remember { mutableStateOf<DownloadManager.DownloadedAlbum?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (albums.isEmpty()) {
            EmptyView(s.emptyDownloads, Modifier.fillMaxSize())
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = BottomNavReserve),
            ) {
                items(albums.sortedByDescending { it.timestamp }, key = { it.albumId }) { album ->
                    val prog = downloading[album.albumId]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .glassClickable(shape = GlassShape) { navController.navigate(Routes.offlineReader(album.albumId)) }
                            .glassSurface(shape = GlassShape)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(album.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (prog?.phase == "downloading") {
                                    s.downloadProgressFmt.format(prog.current, prog.total)
                                } else {
                                    "${album.pageCount} P  ·  ${s.downloaded}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(s.offlineRead, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { deleteTarget = album }) {
                            Icon(Icons.Filled.Delete, contentDescription = s.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { album ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(s.delete) },
            text = { Text(s.deleteConfirmFmt.format(album.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { downloadManager.deleteAlbum(album.albumId) }
                    deleteTarget = null
                }) { Text(s.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(s.cancel) }
            },
        )
    }
}
