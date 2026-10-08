package com.jm.reader.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.download.DownloadManager
import com.jm.reader.data.model.ComicDetail
import com.jm.reader.data.model.SeriesItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalDownloadManager
import com.jm.reader.ui.LocalHistoryManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ComicCard
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.ui.theme.GlassShapeSmall
import com.jm.reader.ui.theme.glassSurface
import com.jm.reader.util.JmId
import kotlinx.coroutines.launch

private data class DetailUiState(
    val detail: ComicDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDetailScreen(navController: NavHostController, id: String) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val history = LocalHistoryManager.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var state by remember { mutableStateOf(DetailUiState()) }
    val comments = rememberCommentsController(id)
    val commentsUi = rememberCommentsUi()

    suspend fun load() {
        state = DetailUiState(loading = true)
        state = when (val r = repo.getAlbum(id)) {
            is RepoResult.Ok -> {
                // Opening an album is what the local history tab records.
                if (r.data.id.isNotBlank()) {
                    history.record(r.data.id, r.data.name, r.data.authors.firstOrNull(), r.data.addtime)
                }
                DetailUiState(detail = r.data, loading = false)
            }
            is RepoResult.Err -> DetailUiState(error = r.message, loading = false)
        }
    }
    LaunchedEffect(id) { load() }
    // Comments load independently of the album: a comment-endpoint hiccup must not blank the page.
    LaunchedEffect(id) { comments.load(reset = true) }

    val downloadManager = LocalDownloadManager.current
    val context = LocalContext.current
    val downloadingState by downloadManager.downloading.collectAsState()
    val downloadedAlbums by downloadManager.albums.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scope.launch {
            val ok = downloadManager.downloadAlbum(id)
            snackbar.showSnackbar(if (ok) s.downloadComplete else s.downloadFailed)
        }
    }

    fun startDownload() {
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            scope.launch {
                val ok = downloadManager.downloadAlbum(id)
                snackbar.showSnackbar(if (ok) s.downloadComplete else s.downloadFailed)
            }
        }
    }

    Scaffold(
        // Near-opaque canvas: the action row is the only glass surface on this screen.
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(s.comicDetail, onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detail = state.detail
            when {
                state.loading && detail == null -> LoadingView()
                state.error != null && detail == null -> ErrorView(state.error!!, onRetry = { scope.launch { load() } })
                detail == null -> ErrorView(s.loadFail, onRetry = { scope.launch { load() } })
                else -> {
                    val chapters = if (detail.series.isNotEmpty()) detail.series.sortedBy { it.sort } else emptyList()
                    val readId = chapters.firstOrNull()?.id ?: detail.id
                    val chapterCount = detail.totalPhotos

                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            val cover = if (detail.isPaid) "" else repo.comicCoverDetail(detail.id, detail.addtime)
                            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                                    contentDescription = detail.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                // Gradient scrim rather than a flat band: the title stays readable
                                // over any cover art without a hard edge cutting the image.
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                0.45f to Color.Transparent,
                                                1f to Color(0xE6000000),
                                            ),
                                        ),
                                )
                                Column(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                ) {
                                    Text(
                                        detail.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    detail.authors.firstOrNull()?.takeIf { it.isNotBlank() }?.let { author ->
                                        Text(
                                            author,
                                            color = Color.White.copy(alpha = 0.85f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 2.dp),
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .glassSurface(shape = GlassShapeSmall)
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        if (detail.isPaid) {
                                            scope.launch { snackbar.showSnackbar(s.paidContentNotice) }
                                        } else {
                                            if (session.isLoggedIn) scope.launch { repo.addWatch(readId) }
                                            navController.navigate(Routes.reader(detail.id, readId))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(s.startReading) }

                                FavoriteButton(
                                    isFavorite = detail.isFavorite,
                                    loggedIn = session.isLoggedIn,
                                    onClick = {
                                        if (!session.isLoggedIn) {
                                            scope.launch { snackbar.showSnackbar(s.loginFirst) }
                                        } else {
                                            scope.launch {
                                                // Flip the heart from the write's own result instead of
                                                // re-fetching the album: the refetch blanked the page and,
                                                // when the server had not caught up, came back with the old
                                                // value - which is why the heart only turned red after a
                                                // manual refresh. A failure is now reported instead of
                                                // being swallowed.
                                                when (val r = repo.addFavorite(detail.id)) {
                                                    is RepoResult.Ok -> {
                                                        val nowFavorite = !detail.isFavorite
                                                        state = state.copy(detail = detail.copy(isFavorite = nowFavorite))
                                                        snackbar.showSnackbar(if (nowFavorite) s.addedFavorite else s.removedFavorite)
                                                    }
                                                    is RepoResult.Err -> snackbar.showSnackbar(r.message)
                                                }
                                            }
                                        }
                                    },
                                )

                                DownloadButton(
                                    albumId = detail.id,
                                    progress = downloadingState[detail.id],
                                    downloaded = downloadedAlbums.any { it.albumId == detail.id },
                                    s = s,
                                    onClick = {
                                        when {
                                            downloadingState[detail.id]?.phase == "downloading" -> { /* already running */ }
                                            downloadedAlbums.any { it.albumId == detail.id } ->
                                                navController.navigate(Routes.offlineReader(detail.id))
                                            else -> startDownload()
                                        }
                                    },
                                )
                            }
                        }

                        item {
                            // Meta pane: description / authors / tags / counters share one glass
                            // surface so the detail page reads as layered glass too.
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .glassSurface(shape = GlassShapeSmall)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                // The work's JM number, stated outright and one tap from the
                                // clipboard: readers routinely want to quote or search it.
                                val workId = JmId.display(detail.id)
                                if (workId.isNotEmpty()) {
                                    val clipboard = LocalClipboardManager.current
                                    Row(
                                        Modifier
                                            .padding(bottom = 6.dp)
                                            .clip(GlassShapeSmall)
                                            .clickable {
                                                clipboard.setText(AnnotatedString(workId))
                                                scope.launch { snackbar.showSnackbar(s.workIdCopied) }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            s.workIdLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            workId,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = s.workIdCopied,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                                Text(
                                    detail.description.ifBlank { s.noDescription },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (detail.authors.isNotEmpty()) {
                                    // Tapping an author jumps straight to an author search.
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            s.authorLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        detail.authors.forEach { author ->
                                            Text(
                                                author,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 2.dp).clickable {
                                                    navController.navigate(Routes.search(mode = "author", q = author))
                                                },
                                            )
                                        }
                                    }
                                }
                                if (detail.tags.isNotEmpty()) {
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            s.tagLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        detail.tags.forEach { tag ->
                                            Text(
                                                "#$tag",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 2.dp).clickable {
                                                    navController.navigate(Routes.search(mode = "work", q = tag))
                                                },
                                            )
                                        }
                                    }
                                }
                                Row(
                                    Modifier.padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Text("${s.views} ${detail.totalViews}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${s.pages} ${detail.totalPhotos}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${s.likes} ${detail.likes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        if (detail.isPaid) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                                    Text(s.paidContentLocked, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        item {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(" ${s.chapters} (${chapters.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (chapters.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(24.dp).clickable {
                                        if (!detail.isPaid) navController.navigate(Routes.reader(detail.id, detail.id))
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(s.singleFileWork, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            items(chapters, key = { it.id }) { chapter ->
                                ChapterRow(chapter, readId, repo, s, onClick = {
                                    if (!detail.isPaid) {
                                        if (session.isLoggedIn) scope.launch { repo.addWatch(chapter.id) }
                                        navController.navigate(Routes.reader(detail.id, chapter.id))
                                    } else {
                                        scope.launch { snackbar.showSnackbar(s.paidContentNotice) }
                                    }
                                })
                            }
                        }

                        if (detail.relatedList.isNotEmpty()) {
                            item {
                                Text(s.relatedWorks, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(detail.relatedList.distinctBy { it.id }, key = { it.id }) { rel ->
                                        ComicCard(
                                            item = rel,
                                            repo = repo,
                                            onClick = { navController.navigate(Routes.comicDetail(rel.id)) },
                                            modifier = Modifier.width(110.dp),
                                        )
                                    }
                                }
                            }
                        }

                        commentsSection(
                            controller = comments,
                            ui = commentsUi,
                            loggedIn = session.isLoggedIn,
                            onLoginRequired = {
                                scope.launch { snackbar.showSnackbar(s.commentLoginRequired) }
                                navController.navigate(Routes.login())
                            },
                            onPost = { content, replyTo ->
                                commentsUi.posting = true
                                try {
                                    when (val r = repo.postComment(detail.id, content, replyTo?.cid)) {
                                        is RepoResult.Ok -> {
                                            commentsUi.draft = ""
                                            commentsUi.replyTarget = null
                                            snackbar.showSnackbar(s.commentPosted)
                                            comments.load(reset = true)
                                        }
                                        is RepoResult.Err -> snackbar.showSnackbar(
                                            r.message.ifBlank { s.commentPostFailed },
                                        )
                                    }
                                } finally {
                                    // Cleared even if the coroutine is cancelled, so the Send
                                    // button can never be left permanently disabled and spinning.
                                    commentsUi.posting = false
                                }
                            },
                            onLike = { comment ->
                                // With no CID there is nothing to vote on; the row already
                                // incremented its own display count.
                                if (comment.cid.isNotBlank()) scope.launch {
                                    when (val r = repo.likeComment(comment.cid)) {
                                        is RepoResult.Ok -> snackbar.showSnackbar(s.commentLiked)
                                        is RepoResult.Err -> snackbar.showSnackbar(
                                            r.message.ifBlank { s.commentLikeFailed },
                                        )
                                    }
                                }
                            },
                            onOpenAlbum = { aid -> navController.navigate(Routes.comicDetail(aid)) },
                            onRetry = { scope.launch { comments.load(reset = true) } },
                            onLoadMore = { scope.launch { comments.load() } },
                            // Screen-scoped, so a post survives the composer being scrolled away.
                            scope = scope,
                        )

                        item { Box(Modifier.padding(bottom = 24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteButton(isFavorite: Boolean, loggedIn: Boolean, onClick: () -> Unit) {
    val s = LocalAppStrings.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(
            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = s.favorite,
            tint = if (isFavorite) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
        )
        Text(if (isFavorite) s.favorited else s.favorite, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DownloadButton(
    albumId: String,
    progress: DownloadManager.Progress?,
    downloaded: Boolean,
    s: AppStrings,
    onClick: () -> Unit,
) {
    val downloading = progress?.phase == "downloading"
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            if (downloading) {
                CircularProgressIndicator(
                    Modifier.size(30.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    if (downloaded) Icons.Filled.CheckCircle else Icons.Filled.Download,
                    contentDescription = if (downloaded) s.downloaded else s.download,
                    tint = if (downloaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Text(
            when {
                downloading && progress != null -> "${progress.current}/${progress.total}"
                downloaded -> s.downloaded
                else -> s.download
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: SeriesItem,
    currentReadId: String,
    repo: AppRepository,
    s: AppStrings,
    onClick: () -> Unit,
) {
    val isCurrent = chapter.id == currentReadId
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (chapter.name.isNotBlank()) chapter.name else s.chapterFmt.format(chapter.sort),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chapter.totalPage > 0) {
            Text("${chapter.totalPage}P", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
