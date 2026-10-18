package com.jm.reader.ui.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jm.reader.ui.components.SectionHeader
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.util.JmId
import kotlinx.coroutines.launch

private data class DetailUiState(
    val detail: ComicDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Comic detail.
 *
 * Refactor notes:
 *  - the page used to open with a 3:4 cover **cropped to 4:3** and the title burnt into it, then a
 *    frosted action bar, then a frosted metadata pane, then a frosted chapter list. Four different
 *    surfaces in the first screen. It now has one header (blurred cover wash + poster + title), one
 *    solid action row, one stats strip and plain rows - the hierarchy is carried by type and
 *    spacing instead of by stacking translucent rectangles.
 *  - the chapter list is a normal list with hairline dividers. A card per chapter made a 48-chapter
 *    work look like 48 separate objects.
 */
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
    // Comments load independently of the album: an endpoint hiccup must not blank the page.
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
        containerColor = Color.Transparent,
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
                    val downloaded = downloadedAlbums.any { it.albumId == detail.id }

                    LazyColumn(Modifier.fillMaxSize()) {
                        item { DetailHeader(detail, repo) }

                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.startReading, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                ActionIcon(
                                    onClick = {
                                        if (detail.isPaid) {
                                            scope.launch { snackbar.showSnackbar(s.paidContentNotice) }
                                        } else if (downloaded) {
                                            navController.navigate(Routes.offlineReader(detail.id))
                                        } else {
                                            startDownload()
                                        }
                                    },
                                    label = when {
                                        downloadingState[detail.id]?.phase == "downloading" -> {
                                            val p = downloadingState[detail.id]
                                            "${p?.current ?: 0}/${p?.total ?: 0}"
                                        }
                                        downloaded -> s.downloaded
                                        else -> s.download
                                    },
                                ) {
                                    if (downloadingState[detail.id]?.phase == "downloading") {
                                        CircularProgressIndicator(
                                            Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            if (downloaded) Icons.Filled.CheckCircle else Icons.Filled.Download,
                                            contentDescription = s.download,
                                            tint = if (downloaded) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }

                                ActionIcon(
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
                                    label = if (detail.isFavorite) s.favorited else s.favorite,
                                ) {
                                    Icon(
                                        if (detail.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = s.favorite,
                                        tint = if (detail.isFavorite) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        item { StatsStrip(detail, s) }

                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                                val workId = JmId.display(detail.id)
                                if (workId.isNotEmpty()) {
                                    val clipboard = LocalClipboardManager.current
                                    Row(
                                        Modifier
                                            .padding(bottom = 10.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                clipboard.setText(AnnotatedString(workId))
                                                scope.launch { snackbar.showSnackbar(s.workIdCopied) }
                                            }
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Text(
                                            "${s.workIdLabel} $workId",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Icon(
                                            Icons.Filled.ContentCopy,
                                            contentDescription = s.workIdCopied,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                Text(
                                    detail.description.ifBlank { s.noDescription },
                                    fontSize = 13.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (detail.tags.isNotEmpty()) {
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        detail.tags.forEach { tag ->
                                            Text(
                                                "#$tag",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable {
                                                        navController.navigate(Routes.search(mode = "work", q = tag))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                                if (detail.authors.isNotEmpty()) {
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            s.authorLabel,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        detail.authors.forEach { author ->
                                            Text(
                                                author,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    navController.navigate(Routes.search(mode = "author", q = author))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (detail.isPaid) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(14.dp),
                                ) {
                                    Text(s.paidContentLocked, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.5.sp)
                                }
                            }
                        }

                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    s.chapters,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    chapters.size.toString(),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (chapters.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(20.dp).clickable {
                                        if (!detail.isPaid) navController.navigate(Routes.reader(detail.id, detail.id))
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(s.singleFileWork, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                                }
                            }
                        } else {
                            items(chapters, key = { it.id }) { chapter ->
                                ChapterRow(chapter, readId, s, onClick = {
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
                                SectionHeader(
                                    title = s.relatedWorks,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                )
                            }
                            item {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(detail.relatedList.distinctBy { it.id }, key = { it.id }) { rel ->
                                        ComicCard(
                                            item = rel,
                                            repo = repo,
                                            onClick = { navController.navigate(Routes.comicDetail(rel.id)) },
                                            modifier = Modifier.width(104.dp),
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

                        item { Box(Modifier.padding(bottom = 32.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * Header: the cover blown up and blurred behind, the poster in front, title beside it.
 *
 * The blur is only used as a *wash* (55% alpha under a gradient), so devices below API 31 - where
 * `Modifier.blur` is a no-op - still get a readable gradient instead of a sharp, noisy crop.
 */
@Composable
private fun DetailHeader(detail: ComicDetail, repo: AppRepository) {
    val s = LocalAppStrings.current
    val cover = if (detail.isPaid) "" else repo.comicCoverDetail(detail.id, detail.addtime)

    Box(Modifier.fillMaxWidth().height(286.dp)) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                    alpha = 0.5f
                }
                .blur(30.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x55000000),
                        0.42f to Color(0x1A000000),
                        0.72f to MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                        1f to MaterialTheme.colorScheme.background,
                    ),
                ),
        )

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                contentDescription = detail.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            )
            Column(Modifier.padding(start = 14.dp, bottom = 4.dp)) {
                Text(
                    detail.name,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                detail.authors.firstOrNull()?.takeIf { it.isNotBlank() }?.let { author ->
                    Text(
                        author,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Text(
                    "${s.chapters} ${detail.series.size} · ${s.pages} ${detail.totalPhotos}",
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** One line of numbers. No card: the header already provides the visual anchor. */
@Composable
private fun StatsStrip(detail: ComicDetail, s: AppStrings) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Stat(compact(detail.likes), s.likes)
        Stat(compact(detail.totalPhotos), s.pages)
        Stat(compact(detail.totalViews), s.views)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** 1234 -> 1.2k, 1234567 -> 123.5万 (the app is Chinese-first; English users still read numbers). */
private fun compact(n: Long): String = when {
    n >= 10_000 -> String.format("%.1f", n / 10_000.0).removeSuffix(".0") + "万"
    else -> n.toString()
}

/** Square action button with a caption under it, used for download and favourite. */
@Composable
private fun ActionIcon(onClick: () -> Unit, label: String, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            label,
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: SeriesItem,
    currentReadId: String,
    s: AppStrings,
    onClick: () -> Unit,
) {
    val isCurrent = chapter.id == currentReadId
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (chapter.name.isNotBlank()) chapter.name else s.chapterFmt.format(chapter.sort),
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chapter.totalPage > 0) {
                Text(
                    "${chapter.totalPage}P",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            thickness = 0.6.dp,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}
