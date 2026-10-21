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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.jm.reader.data.model.ComicDetail
import com.jm.reader.data.model.SeriesItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.data.stats.Verdict
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalDownloadManager
import com.jm.reader.ui.LocalHistoryManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.LocalStats
import com.jm.reader.ui.components.ArchiveBookRow
import com.jm.reader.ui.components.ArchiveCode
import com.jm.reader.ui.components.ArchiveDetailLayout
import com.jm.reader.ui.components.ArchivePlate
import com.jm.reader.ui.components.ArchiveReveal
import com.jm.reader.ui.components.ArchiveSectionHeader
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.components.registrationMarks
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.ui.theme.CodeTextStyle
import com.jm.reader.ui.theme.glassClickable
import com.jm.reader.util.JmId
import kotlinx.coroutines.launch

private data class DetailUiState(
    val detail: ComicDetail? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

/** Page gutter. Matches the home register (24dp), not the old 20dp card inset. */
private val Gutter = ArchiveDetailLayout.Gutter

/**
 * Comic detail, in the archive vocabulary.
 *
 * Refactor notes:
 *  - the old page opened with a blurred cover wash and a poster floating over it, then a row of
 *    rounded Material buttons, a card-like stats strip and a rounded chip per tag. Four visual
 *    languages on one screen, and none of them the one the shelf uses.
 *  - it is now a *dossier*: a ruled top bar, a cover plate with registration marks, an ink address
 *    block, a three-cell ledger, a control strip built from squares instead of capsules, and
 *    ruled chapter rows. Hierarchy comes from type, ink and hairlines - the same four devices the
 *    home register uses - so walking from the shelf into a work no longer changes dialect.
 *  - every behaviour is unchanged: paid-work interception, the favourite flip from the write's own
 *    result, the permission-gated download, comment threading, and the copyable work id.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComicDetailScreen(navController: NavHostController, id: String) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val history = LocalHistoryManager.current
    val stats = LocalStats.current
    val statsSnapshot by stats.snapshot.collectAsState()
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
        topBar = { DossierBar(title = s.comicDetail, onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        // Three ruled cells, not floating chips: the verdict is a stamp on the dossier, and the
        // bar stays 38dp no matter what the labels say in English.
        bottomBar = {
            if (state.detail != null) {
                VerdictBar(
                    selected = statsSnapshot.verdicts[id],
                    liked = s.verdictLiked,
                    meh = s.verdictMeh,
                    disliked = s.verdictDisliked,
                    onPick = { stats.setVerdict(id, it) },
                )
            }
        },
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
                    val cover = if (detail.isPaid) "" else repo.comicCoverDetail(detail.id, detail.addtime)
                    val workId = JmId.display(detail.id)
                    val clipboard = LocalClipboardManager.current

                    fun copyId() {
                        if (workId.isNotEmpty()) {
                            clipboard.setText(AnnotatedString(workId))
                            scope.launch { snackbar.showSnackbar(s.workIdCopied) }
                        }
                    }

                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            ArchiveReveal(index = 0, generation = detail.id) {
                                DossierHeader(
                                    detail = detail,
                                    cover = cover,
                                    workId = workId,
                                    onCopyId = { copyId() },
                                )
                            }
                        }

                        item {
                            ActionStrip(
                                detail = detail,
                                s = s,
                                downloaded = downloaded,
                                downloadPhase = downloadingState[detail.id]?.phase,
                                downloadNow = downloadingState[detail.id]?.current ?: 0,
                                downloadTotal = downloadingState[detail.id]?.total ?: 0,
                                onRead = {
                                    if (detail.isPaid) {
                                        scope.launch { snackbar.showSnackbar(s.paidContentNotice) }
                                    } else {
                                        if (session.isLoggedIn) scope.launch { repo.addWatch(readId) }
                                        navController.navigate(Routes.reader(detail.id, readId))
                                    }
                                },
                                onDownload = {
                                    if (detail.isPaid) {
                                        scope.launch { snackbar.showSnackbar(s.paidContentNotice) }
                                    } else if (downloaded) {
                                        navController.navigate(Routes.offlineReader(detail.id))
                                    } else {
                                        startDownload()
                                    }
                                },
                                onFavorite = {
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
                        }

                        item { LedgerStrip(detail, s) }

                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                                ArchiveSectionHeader(code = "01 / SYNOPSIS", title = s.synopsis)
                                Text(
                                    detail.description.ifBlank { s.noDescription },
                                    fontSize = 13.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (detail.tags.isNotEmpty()) {
                                    FlowRow(
                                        Modifier.fillMaxWidth().padding(top = 14.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        detail.tags.forEach { tag ->
                                            Text(
                                                "#$tag",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .border(
                                                        0.7.dp,
                                                        MaterialTheme.colorScheme.outlineVariant,
                                                        RoundedCornerShape(2.dp),
                                                    )
                                                    .glassClickable(shape = RoundedCornerShape(2.dp)) {
                                                        navController.navigate(Routes.search(mode = "work", q = tag))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                                if (detail.authors.isNotEmpty()) {
                                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                        ArchiveCode("AUTHOR")
                                        Spacer(Modifier.width(9.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            detail.authors.forEach { author ->
                                                Text(
                                                    author,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier
                                                        .glassClickable(shape = RoundedCornerShape(2.dp)) {
                                                            navController.navigate(Routes.search(mode = "author", q = author))
                                                        }
                                                        .padding(vertical = 1.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (detail.isPaid) {
                            item {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Gutter, vertical = 6.dp)
                                        .border(
                                            0.7.dp,
                                            MaterialTheme.colorScheme.outlineVariant,
                                            RoundedCornerShape(3.dp),
                                        )
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    ArchiveCode("02 / LOCKED")
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        s.paidContentLocked,
                                        fontSize = 12.5.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                                ArchiveSectionHeader(
                                    code = "03 / CHAPTERS",
                                    title = s.chapters,
                                    trailing = if (chapters.isEmpty()) null else chapters.size.toString(),
                                )
                            }
                        }

                        if (chapters.isEmpty()) {
                            item {
                                Box(Modifier.padding(horizontal = Gutter)) {
                                    ArchivePlate(
                                        title = s.singleFileWork,
                                        hint = s.startReading,
                                        code = "SINGLE / FILE",
                                        onClick = {
                                            if (!detail.isPaid) navController.navigate(Routes.reader(detail.id, detail.id))
                                        },
                                    )
                                }
                            }
                        } else {
                            items(chapters, key = { it.id }) { chapter ->
                                ChapterRow(
                                    chapter = chapter,
                                    currentReadId = readId,
                                    s = s,
                                    onClick = {
                                        if (!detail.isPaid) {
                                            if (session.isLoggedIn) scope.launch { repo.addWatch(chapter.id) }
                                            navController.navigate(Routes.reader(detail.id, chapter.id))
                                        } else {
                                            scope.launch { snackbar.showSnackbar(s.paidContentNotice) }
                                        }
                                    },
                                )
                            }
                        }

                        if (detail.relatedList.isNotEmpty()) {
                            val related = detail.relatedList.distinctBy { it.id }
                            item {
                                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                                    ArchiveSectionHeader(
                                        code = "04 / RELATED",
                                        title = s.relatedWorks,
                                        trailing = related.size.toString(),
                                    )
                                }
                            }
                            items(related, key = { "rel-" + it.id }) { rel ->
                                ArchiveBookRow(
                                    id = rel.id,
                                    title = rel.name,
                                    author = rel.author.orEmpty().ifBlank { rel.category?.title.orEmpty() },
                                    cover = rel.image,
                                    onClick = { navController.navigate(Routes.comicDetail(rel.id)) },
                                    modifier = Modifier.padding(horizontal = Gutter),
                                )
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

/** Ruled top bar: a square back plate, the page name, and the dossier address on the right. */
@Composable
private fun DossierBar(title: String, onBack: () -> Unit) {
    val s = LocalAppStrings.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(ArchiveDetailLayout.BarHeight)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                .glassClickable(shape = RoundedCornerShape(3.dp), onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = s.back,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ArchiveCode("ARCHIVE / DOSSIER")
    }
}

/**
 * Cover plate + address block + title.
 *
 * The cover is inset 3dp inside its rule so the image reads as something *mounted* on the page,
 * not as a floating card; the corner marks are the same printing cue the home masthead uses.
 */
@Composable
private fun DossierHeader(
    detail: ComicDetail,
    cover: String,
    workId: String,
    onCopyId: () -> Unit,
) {
    val s = LocalAppStrings.current
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Row(Modifier.fillMaxWidth().padding(top = ArchiveDetailLayout.HeaderTop, bottom = 16.dp)) {
            Box(
                Modifier
                    .size(
                        width = ArchiveDetailLayout.CoverWidth,
                        height = ArchiveDetailLayout.CoverHeight,
                    )
                    .registrationMarks(hairline)
                    .border(0.7.dp, hairline, RoundedCornerShape(2.dp))
                    .padding(3.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(1.dp)),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                        contentDescription = detail.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(
                    Modifier.glassClickable(shape = RoundedCornerShape(2.dp), onClick = onCopyId),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArchiveCode(
                        text = workId.ifBlank { "JM-______" },
                        inverse = true,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = s.workIdCopied,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp),
                    )
                }
                Text(
                    detail.name,
                    fontSize = 22.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 9.dp),
                )
                detail.authors.joinToString(" · ").takeIf { it.isNotBlank() }?.let { authors ->
                    Text(
                        authors,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    "${detail.series.size} ${s.chapters} · ${detail.totalPhotos} ${s.pages}",
                    style = CodeTextStyle.copy(fontSize = 8.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

/**
 * The control strip: one ink cell for reading, two ruled cells for download and favourite.
 *
 * The old row mixed a 46dp filled button with two 46dp tinted squares *and* a caption under each,
 * so nothing aligned. Equal-height cells in one row fix that; the first cell is the only filled
 * shape, which is what gives it priority without an accent colour.
 */
@Composable
private fun ActionStrip(
    detail: ComicDetail,
    s: AppStrings,
    downloaded: Boolean,
    downloadPhase: String?,
    downloadNow: Int,
    downloadTotal: Int,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onFavorite: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.background
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .weight(1.25f)
                .height(46.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ink)
                .glassClickable(shape = RoundedCornerShape(3.dp), onClick = onRead)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = paper,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                s.startReading,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                color = paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ActionCell(
            label = when {
                downloadPhase == "downloading" -> "$downloadNow/$downloadTotal"
                downloaded -> s.downloaded
                else -> s.download
            },
            onClick = onDownload,
            modifier = Modifier.weight(0.9f),
        ) {
            if (downloadPhase == "downloading") {
                CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 1.5.dp, color = ink)
            } else {
                Icon(
                    if (downloaded) Icons.Filled.CheckCircle else Icons.Filled.Download,
                    contentDescription = s.download,
                    tint = if (downloaded) MaterialTheme.colorScheme.primary else ink,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        ActionCell(
            label = if (detail.isFavorite) s.favorited else s.favorite,
            onClick = onFavorite,
            modifier = Modifier.weight(0.9f),
        ) {
            Icon(
                if (detail.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = s.favorite,
                tint = if (detail.isFavorite) MaterialTheme.colorScheme.primary else ink,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ActionCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .height(46.dp)
            .border(0.7.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
            .glassClickable(shape = RoundedCornerShape(3.dp), onClick = onClick)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Three numbers in a ruled band.
 *
 * The reference prints statistics as a ledger line, not as a card with a tinted background: the
 * hairlines are what separate the cells, so the strip costs no elevation and stays readable on
 * paper and on the dark canvas alike.
 */
@Composable
private fun LedgerStrip(detail: ComicDetail, s: AppStrings) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 16.dp)
            .drawBehind {
                val stroke = 0.7.dp.toPx()
                drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), stroke)
                drawLine(hairline, Offset(0f, size.height), Offset(size.width, size.height), stroke)
            }
            .height(ArchiveDetailLayout.BarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LedgerCell(compact(detail.likes), s.likes, Modifier.weight(1f))
        LedgerRule()
        LedgerCell(compact(detail.totalPhotos), s.pages, Modifier.weight(1f))
        LedgerRule()
        LedgerCell(compact(detail.totalViews), s.views, Modifier.weight(1f))
    }
}

@Composable
private fun LedgerCell(value: String, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = CodeTextStyle.copy(fontSize = 8.sp, letterSpacing = 0.6.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun LedgerRule() {
    Box(
        Modifier
            .width(0.7.dp)
            .height(22.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * 起立 / 平平无奇 / 萎了 - one tap, stored locally, tap again to clear.
 *
 * Numbered cells with vertical rules, like a form to be stamped: the selected one is filled with
 * ink. Equal thirds keep the bar from reflowing when the app is in English.
 */
@Composable
private fun VerdictBar(
    selected: Verdict?,
    liked: String,
    meh: String,
    disliked: String,
    onPick: (Verdict) -> Unit,
) {
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .drawBehind {
                drawLine(
                    hairline,
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    0.7.dp.toPx(),
                )
            }
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerdictCell("01", liked, selected == Verdict.LIKED, Modifier.weight(1f)) { onPick(Verdict.LIKED) }
            VerdictRule()
            VerdictCell("02", meh, selected == Verdict.MEH, Modifier.weight(1f)) { onPick(Verdict.MEH) }
            VerdictRule()
            VerdictCell("03", disliked, selected == Verdict.DISLIKED, Modifier.weight(1f)) { onPick(Verdict.DISLIKED) }
        }
    }
}

@Composable
private fun VerdictCell(
    code: String,
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.background
    Row(
        modifier
            .fillMaxHeight()
            .background(if (active) ink else Color.Transparent)
            .glassClickable(shape = RoundedCornerShape(0.dp), onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            code,
            style = CodeTextStyle.copy(fontSize = 8.sp),
            color = if (active) paper.copy(alpha = 0.66f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) paper else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun VerdictRule() {
    Box(
        Modifier
            .width(0.7.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 1234 -> 1.2万; the app is Chinese-first, English users still read numbers. */
private fun compact(n: Long): String = when {
    n >= 10_000 -> String.format("%.1f", n / 10_000.0).removeSuffix(".0") + "万"
    else -> n.toString()
}

@Composable
private fun ChapterRow(
    chapter: SeriesItem,
    currentReadId: String,
    s: AppStrings,
    onClick: () -> Unit,
) {
    val isCurrent = chapter.id == currentReadId
    val hairline = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .glassClickable(shape = RoundedCornerShape(0.dp), onClick = onClick)
            .drawBehind {
                drawLine(
                    hairline,
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    0.7.dp.toPx(),
                )
            }
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            chapter.sort.toString().padStart(3, '0'),
            style = CodeTextStyle.copy(fontSize = 8.sp),
            color = if (isCurrent) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
        )
        Text(
            if (chapter.name.isNotBlank()) chapter.name else s.chapterFmt.format(chapter.sort),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (chapter.totalPage > 0) {
            Text(
                "${chapter.totalPage}P",
                style = CodeTextStyle.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
