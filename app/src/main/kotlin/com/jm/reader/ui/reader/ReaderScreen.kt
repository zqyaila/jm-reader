package com.jm.reader.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.jm.reader.data.model.ComicDetail
import com.jm.reader.data.model.ReadData
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalHistoryManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.util.ReaderImageLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(navController: NavHostController, albumId: String, readId: String) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val history = LocalHistoryManager.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<ComicDetail?>(null) }
    var read by remember { mutableStateOf<ReadData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentReadId by rememberSaveable { mutableStateOf(readId.ifBlank { albumId }) }
    var isVertical by rememberSaveable { mutableStateOf(true) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showChapterList by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { read?.images?.size ?: 1 })

    suspend fun loadChapter(chapterId: String) {
        error = null
        read = null
        progress = 0
        when (val r = repo.comicRead(chapterId)) {
            is RepoResult.Ok -> {
                read = r.data
                // Local history works without an account; the server copy is best-effort.
                history.record(albumId, detail?.name.orEmpty(), detail?.authors?.firstOrNull(), detail?.addtime ?: 0L)
                history.recordProgress(albumId, r.data.name, 1)
                if (session.isLoggedIn) repo.addWatch(chapterId)
            }
            is RepoResult.Err -> error = r.message
        }
    }

    LaunchedEffect(currentReadId) { loadChapter(currentReadId) }

    LaunchedEffect(albumId) {
        when (val r = repo.getAlbum(albumId)) {
            is RepoResult.Ok -> {
                detail = r.data
                history.record(albumId, r.data.name, r.data.authors.firstOrNull(), r.data.addtime)
            }
            is RepoResult.Err -> Unit
        }
    }

    val chapters = detail?.series?.sortedBy { it.sort } ?: emptyList()
    val currentIndex = chapters.indexOfFirst { it.id == currentReadId }
    val currentChapterName = chapters.firstOrNull { it.id == currentReadId }?.name ?: read?.name

    // Persist the reading position when the reader is left (mirrors JMComic-qt, which writes its
    // `history` row on ReturnPage / after the chapter page list is loaded).
    val latestProgress by rememberUpdatedState(progress)
    val latestChapter by rememberUpdatedState(currentChapterName)
    DisposableEffect(albumId) {
        onDispose { history.recordProgress(albumId, latestChapter, latestProgress + 1) }
    }

    // Track vertical scroll progress.
    LaunchedEffect(listState, read) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                val total = read?.images?.size ?: 0
                if (total > 0) progress = idx.coerceIn(0, total - 1)
            }
    }
    // Track pager progress.
    LaunchedEffect(pagerState, isVertical) {
        snapshotFlow { pagerState.currentPage }
            .collect { p -> if (!isVertical) progress = p }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            error != null && read == null -> ErrorView(error!!, onRetry = { scope.launch { loadChapter(currentReadId) } })
            read == null -> LoadingView()
            else -> {
                val data = read!!

                if (isVertical) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(data.images, key = { i, _ -> "$i-${data.id}" }) { index, page ->
                            ReaderPageImage(
                                page = page.image,
                                aid = data.id.toLongOrNull() ?: 0L,
                                scrambleId = data.scrambleId,
                                onClick = { showControls = !showControls },
                            )
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { index ->
                        val page = data.images.getOrNull(index) ?: return@HorizontalPager
                        ReaderPageImage(
                            page = page.image,
                            aid = data.id.toLongOrNull() ?: 0L,
                            scrambleId = data.scrambleId,
                            onClick = { showControls = !showControls },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                    ReaderControls(
                        title = data.name,
                        progress = progress,
                        totalPages = data.images.size,
                        isVertical = isVertical,
                        hasPrev = currentIndex > 0,
                        hasNext = chapters.isNotEmpty() && currentIndex in 0 until chapters.size - 1,
                        onBack = { navController.popBackStack() },
                        onToggleMode = { isVertical = !isVertical },
                        onPrev = {
                            if (currentIndex > 0) currentReadId = chapters[currentIndex - 1].id
                        },
                        onNext = {
                            if (chapters.isNotEmpty() && currentIndex in 0 until chapters.size - 1) {
                                currentReadId = chapters[currentIndex + 1].id
                            }
                        },
                        onSeek = { value ->
                            if (data.images.isNotEmpty()) {
                                progress = value
                                if (isVertical) {
                                    scope.launch { listState.scrollToItem(value.coerceIn(0, data.images.size - 1)) }
                                } else {
                                    scope.launch { pagerState.scrollToPage(value.coerceIn(0, data.images.size - 1)) }
                                }
                            }
                        },
                        onChapterList = { showChapterList = true },
                    )
                }

                if (showChapterList) {
                    ChapterListDialog(
                        chapters = chapters,
                        currentId = currentReadId,
                        onSelect = { id ->
                            currentReadId = id
                            showChapterList = false
                        },
                        onDismiss = { showChapterList = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderPageImage(
    page: String,
    aid: Long,
    scrambleId: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(page) {
        bitmap = null
        failed = false
        bitmap = ReaderImageLoader.load(page, aid, scrambleId)
        if (bitmap == null) failed = true
    }

    val bmp = bitmap
    Box(modifier.fillMaxWidth().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        when {
            bmp != null -> androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 3f / 4f),
            )
            failed -> Text(LocalAppStrings.current.imageLoadFail, color = Color.Gray, modifier = Modifier.padding(24.dp))
            else -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(48.dp),
            )
        }
    }
}

@Composable
private fun ReaderControls(
    title: String,
    progress: Int,
    totalPages: Int,
    isVertical: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onToggleMode: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onChapterList: () -> Unit,
) {
    val s = LocalAppStrings.current
    Column(Modifier.fillMaxSize()) {
        // Top bar
        Surface(color = Color(0xCC000000)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back, tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleMode) {
                    Icon(
                        if (isVertical) Icons.Filled.ViewStream else Icons.Filled.SwapVert,
                        contentDescription = s.toggleMode,
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onChapterList) {
                    Icon(Icons.Filled.MenuBook, contentDescription = s.chapterList, tint = Color.White)
                }
            }
        }
        Box(Modifier.weight(1f))

        // Bottom bar
        Surface(color = Color(0xCC000000)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${progress + 1} / $totalPages", color = Color.White, fontSize = 13.sp)
                    Slider(
                        value = progress.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(totalPages - 1).coerceAtLeast(0).toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onPrev, enabled = hasPrev) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = if (hasPrev) Color.White else Color.Gray)
                        Text(s.prevChapter, color = if (hasPrev) Color.White else Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }
                    TextButton(onClick = onNext, enabled = hasNext) {
                        Text(s.nextChapter, color = if (hasNext) Color.White else Color.Gray, modifier = Modifier.padding(end = 4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = if (hasNext) Color.White else Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterListDialog(
    chapters: List<com.jm.reader.data.model.SeriesItem>,
    currentId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(s.chooseChapter, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                if (chapters.isEmpty()) {
                    Text(s.singleFile, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().size(400.dp)) {
                        itemsIndexed(chapters, key = { i, c -> "$i-${c.id}" }) { _, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(c.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (c.name.isNotBlank()) c.name else s.chapterFmt.format(c.sort),
                                    color = if (c.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (c.id == currentId) Text(s.current, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(s.close) }
            }
        }
    }
}
