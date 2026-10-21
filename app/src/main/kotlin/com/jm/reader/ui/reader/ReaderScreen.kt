package com.jm.reader.ui.reader

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavHostController
import com.jm.reader.data.history.BookmarkStore
import com.jm.reader.data.history.ReadingBookmark
import com.jm.reader.data.model.ComicDetail
import com.jm.reader.data.model.ReadData
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalHistoryManager
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.LocalSession
import com.jm.reader.ui.LocalStats
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.util.ImageSaver
import com.jm.reader.util.ReaderImageLoader
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(navController: NavHostController, albumId: String, readId: String) {
    val repo = LocalRepository.current
    val session = LocalSession.current
    val history = LocalHistoryManager.current
    val stats = LocalStats.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Reading time is what the stats screen reports and what flavours "guess you like". Counting
    // only while the screen is RESUMED means a reader left open in the background does not slowly
    // accumulate hours; pausing (or leaving) banks the current span.
    LifecycleResumeEffect(albumId) {
        val started = SystemClock.elapsedRealtime()
        onPauseOrDispose { stats.addReadingTime(albumId, SystemClock.elapsedRealtime() - started) }
    }

    var detail by remember { mutableStateOf<ComicDetail?>(null) }
    var read by remember { mutableStateOf<ReadData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentReadId by rememberSaveable { mutableStateOf(readId.ifBlank { albumId }) }
    var isVertical by rememberSaveable { mutableStateOf(true) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showChapterList by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    var autoScroll by rememberSaveable { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    /** Page to jump to once the chapter it belongs to has loaded (bookmark navigation). */
    var pendingPage by remember { mutableStateOf<Int?>(null) }

    val bookmarks = remember(context) { BookmarkStore(context) }
    val bookmarkItems by bookmarks.items.collectAsState()
    val albumBookmarks = remember(bookmarkItems, albumId) { bookmarkItems.filter { it.albumId == albumId } }

    val listState = rememberLazyListState()
    val pagerState = rememberPagerState(pageCount = { read?.images?.size ?: 1 })
    val focusRequester = remember { FocusRequester() }
    val pageStepPx = with(LocalDensity.current) { 1.2.dp.toPx() }

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
                // This is the moment a work becomes part of the local taste profile: the reader
                // actually opened a chapter (opening the detail page does not count).
                stats.recordChapter(albumId, r.data.id)
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
                // Tags belong to the work; record them here so the reader's stats can weight this
                // work's contribution by how much of it was actually read.
                stats.setMeta(albumId, r.data.name, r.data.tags)
                // How big the work is turns "pages read" into a completion rate. `totalPhotos` is
                // the album-wide page count the API always returns; the per-chapter sum is more
                // precise when the series list is complete, so take whichever is larger.
                val knownPages = r.data.series.sumOf { it.totalPage }
                stats.setScope(
                    albumId = albumId,
                    totalChapters = r.data.series.size,
                    totalPages = maxOf(knownPages, r.data.totalPhotos.toInt()),
                )
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
    // "How much of this work did I read" - the furthest page per chapter, which is what the local
    // stats use as the weight of this work's tags. Re-reading or scrolling back never adds.
    LaunchedEffect(progress, currentReadId) {
        stats.recordPage(albumId, currentReadId, progress + 1)
    }

    /**
     * One screen forward or back.
     *
     * Vertical mode is a continuous column, so "next page" means one viewport of travel, not one
     * index: jumping by index in a column of full-height images can land between two pages on a
     * wide screen. Horizontal mode moves the pager by exactly one page.
     */
    fun step(delta: Int) {
        val data = read ?: return
        if (isVertical) {
            val viewport = listState.layoutInfo.viewportSize.height
            if (viewport <= 0) return
            scope.launch { listState.animateScrollBy(viewport * 0.92f * delta) }
        } else {
            val target = (pagerState.currentPage + delta)
                .coerceIn(0, (data.images.size - 1).coerceAtLeast(0))
            scope.launch { pagerState.animateScrollToPage(target) }
        }
    }

    /** Left third = back, right third = forward, middle = the bars. */
    fun onTapZone(fraction: Float) {
        when {
            fraction < 0.32f -> step(-1)
            fraction > 0.68f -> step(1)
            else -> showControls = !showControls
        }
    }

    // Auto-scroll: a constant drip of pixels per frame while the reader is vertical. `scrollBy`
    // clamps at the end of the list by itself, so this simply idles there instead of overshooting.
    LaunchedEffect(autoScroll, isVertical, read?.id) {
        if (!autoScroll || !isVertical || read == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (!listState.canScrollForward) {
                autoScroll = false
                break
            }
            listState.scrollBy(pageStepPx)
        }
    }

    // Bookmarks are per page; the key is (chapter, page) so the same panel cannot be stored twice.
    val bookmarked = albumBookmarks.any { it.chapterId == currentReadId && it.page == progress + 1 }
    fun toggleBookmark() {
        val added = bookmarks.toggle(
            ReadingBookmark(
                albumId = albumId,
                chapterId = currentReadId,
                chapterName = currentChapterName.orEmpty(),
                page = progress + 1,
            ),
        )
        Toast.makeText(context, if (added) s.reader.bookmarkAdded else s.reader.bookmarkRemoved, Toast.LENGTH_SHORT).show()
    }

    /** Long-press capture: writes the decoded panel to the gallery, outside the download list. */
    fun savePage(bitmap: Bitmap, chapterId: String, page: Int) {
        val ok = ImageSaver.save(context, bitmap, "JM_${albumId}_${chapterId}_$page")
        Toast.makeText(context, if (ok) s.reader.imageSaved else s.reader.imageSaveFailed, Toast.LENGTH_SHORT).show()
    }

    fun jumpTo(bookmark: ReadingBookmark) {
        showBookmarks = false
        if (bookmark.chapterId != currentReadId) {
            pendingPage = bookmark.page
            currentReadId = bookmark.chapterId
        } else {
            val data = read ?: return
            val target = (bookmark.page - 1).coerceIn(0, (data.images.size - 1).coerceAtLeast(0))
            progress = target
            scope.launch {
                if (isVertical) listState.scrollToItem(target) else pagerState.scrollToPage(target)
            }
            showControls = false
        }
    }

    // Runs once the chapter a bookmark points at has actually landed.
    LaunchedEffect(read?.id, pendingPage) {
        val page = pendingPage ?: return@LaunchedEffect
        val data = read ?: return@LaunchedEffect
        if (data.id != currentReadId) return@LaunchedEffect
        val target = (page - 1).coerceIn(0, (data.images.size - 1).coerceAtLeast(0))
        progress = target
        if (isVertical) listState.scrollToItem(target) else pagerState.scrollToPage(target)
        pendingPage = null
    }

    // Volume keys: the physical buttons are the most reliable page turn on a phone, and they keep
    // working when the bars are hidden. The reader takes focus on entry and after a dialog closes.
    LaunchedEffect(showChapterList, showBookmarks) {
        if (!showChapterList && !showBookmarks) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_PAGE_DOWN,
                    -> {
                        step(1)
                        true
                    }

                    AndroidKeyEvent.KEYCODE_VOLUME_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_PAGE_UP,
                    -> {
                        step(-1)
                        true
                    }

                    else -> false
                }
            },
    ) {
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
                                onTapZone = { onTapZone(it) },
                                onLongPress = { bitmap -> savePage(bitmap, data.id, index + 1) },
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
                            onTapZone = { onTapZone(it) },
                            onLongPress = { bitmap -> savePage(bitmap, data.id, index + 1) },
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
                        bookmarked = bookmarked,
                        bookmarkCount = albumBookmarks.size,
                        autoScroll = autoScroll && isVertical,
                        onBack = { navController.popBackStack() },
                        onTitle = { navController.navigate(Routes.comicDetail(albumId)) },
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
                        onToggleBookmark = { toggleBookmark() },
                        onShowBookmarks = { showBookmarks = true },
                        onToggleAutoScroll = { autoScroll = !autoScroll },
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

                if (showBookmarks) {
                    BookmarksDialog(
                        items = albumBookmarks,
                        onSelect = { jumpTo(it) },
                        onDelete = { bookmarks.remove(it) },
                        onDismiss = { showBookmarks = false },
                    )
                }
            }
        }
    }
}

/**
 * One page, with pinch-zoom and the tap zones.
 *
 * Two gesture detectors, on purpose: taps need the whole surface, while zoom must not steal a
 * single-finger drag from the `LazyColumn`/pager above. The pinch detector therefore only consumes
 * when a second finger is down (or when the page is already zoomed, where panning wins anyway).
 */
@Composable
private fun ReaderPageImage(
    page: String,
    aid: Long,
    scrambleId: Long,
    onTapZone: (Float) -> Unit,
    onLongPress: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    var scale by remember(page) { mutableFloatStateOf(1f) }
    var offset by remember(page) { mutableStateOf(Offset.Zero) }
    val latestBitmap by rememberUpdatedState(bitmap)

    LaunchedEffect(page) {
        bitmap = null
        failed = false
        bitmap = ReaderImageLoader.load(page, aid, scrambleId)
        if (bitmap == null) failed = true
    }

    val bmp = bitmap
    Box(
        modifier
            .fillMaxWidth()
            .pointerInput(page) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val next = (scale * zoomChange).coerceIn(1f, 4f)
                                val maxX = size.width * (next - 1f) / 2f
                                val maxY = size.height * (next - 1f) / 2f
                                offset = if (next <= 1f) {
                                    Offset.Zero
                                } else {
                                    Offset(
                                        (offset.x + panChange.x).coerceIn(-maxX, maxX),
                                        (offset.y + panChange.y).coerceIn(-maxY, maxY),
                                    )
                                }
                                scale = next
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(page) {
                detectTapGestures(
                    onTap = { onTapZone(if (size.width > 0) it.x / size.width else 0.5f) },
                    onDoubleTap = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    onLongPress = { latestBitmap?.let(onLongPress) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            bmp != null -> androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 3f / 4f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            failed -> Text(
                LocalAppStrings.current.reader.imageLoadFail,
                color = Color.Gray,
                modifier = Modifier.padding(24.dp),
            )

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
    bookmarked: Boolean,
    bookmarkCount: Int,
    autoScroll: Boolean,
    onBack: () -> Unit,
    onTitle: () -> Unit,
    onToggleMode: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
    onChapterList: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    onToggleAutoScroll: () -> Unit,
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
                    modifier = Modifier.weight(1f).clickable(onClick = onTitle),
                )
                IconButton(onClick = onToggleMode) {
                    Icon(
                        if (isVertical) Icons.Filled.ViewStream else Icons.Filled.SwapVert,
                        contentDescription = s.reader.toggleMode,
                        tint = Color.White,
                    )
                }
                IconButton(onClick = onChapterList) {
                    Icon(Icons.Filled.MenuBook, contentDescription = s.reader.chapterList, tint = Color.White)
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
                        Text(s.reader.prevChapter, color = if (hasPrev) Color.White else Color.Gray, modifier = Modifier.padding(start = 4.dp))
                    }
                    TextButton(onClick = onNext, enabled = hasNext) {
                        Text(s.reader.nextChapter, color = if (hasNext) Color.White else Color.Gray, modifier = Modifier.padding(end = 4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = if (hasNext) Color.White else Color.Gray)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ReaderAction(
                        icon = { tint ->
                            Icon(
                                if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = s.reader.bookmark,
                                tint = tint,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        label = s.reader.bookmark,
                        active = bookmarked,
                        onClick = onToggleBookmark,
                    )
                    ReaderAction(
                        icon = { tint ->
                            Icon(
                                Icons.Filled.FormatListBulleted,
                                contentDescription = s.reader.bookmarkList,
                                tint = tint,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        label = "${s.reader.bookmarkList} $bookmarkCount",
                        active = false,
                        onClick = onShowBookmarks,
                    )
                    ReaderAction(
                        icon = { tint ->
                            Icon(
                                if (autoScroll) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = s.reader.autoScroll,
                                tint = tint,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                        label = if (autoScroll) s.reader.autoScrollStop else s.reader.autoScroll,
                        active = autoScroll,
                        onClick = onToggleAutoScroll,
                    )
                }
            }
        }
    }
}

/** A small icon+label action; the reader bar has no accent colour, so `active` is plain white. */
@Composable
private fun ReaderAction(
    icon: @Composable (Color) -> Unit,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (active) Color.White else Color(0xFFBDBDBD)
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        icon(tint)
        Text(
            label,
            color = tint,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
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
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(s.reader.chooseChapter, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                if (chapters.isEmpty()) {
                    Text(s.reader.singleFile, modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().size(400.dp)) {
                        itemsIndexed(chapters, key = { i, c -> "$i-${c.id}" }) { _, c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(c.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    c.sort.toString().padStart(3, '0'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Text(
                                    if (c.name.isNotBlank()) c.name else s.chapterFmt.format(c.sort),
                                    color = if (c.id == currentId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (c.id == currentId) Text(s.reader.current, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(s.close) }
            }
        }
    }
}

@Composable
private fun BookmarksDialog(
    items: List<ReadingBookmark>,
    onSelect: (ReadingBookmark) -> Unit,
    onDelete: (ReadingBookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(s.reader.bookmarkList, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                if (items.isEmpty()) {
                    Text(
                        s.reader.bookmarkEmpty,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().size(width = 300.dp, height = 360.dp)) {
                        itemsIndexed(items, key = { _, b -> "${b.chapterId}-${b.page}" }) { _, bookmark ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(bookmark) }
                                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        bookmark.chapterName.ifBlank { s.reader.singleFile },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                    )
                                    Text(
                                        s.reader.pageFmt.format(bookmark.page),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                    )
                                }
                                IconButton(onClick = { onDelete(bookmark) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = s.reader.bookmarkDelete,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(s.close) }
            }
        }
    }
}
