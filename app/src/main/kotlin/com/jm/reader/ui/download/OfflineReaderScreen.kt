package com.jm.reader.ui.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jm.reader.data.download.AlbumManifest
import com.jm.reader.data.download.DownloadManager
import com.jm.reader.data.history.chapterReadState
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalDownloadManager
import com.jm.reader.ui.LocalReadingProgress
import com.jm.reader.ui.components.ChapterEntry
import com.jm.reader.ui.components.ChapterPickerDialog
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.LoadingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** One chapter that exists on disk, ready to be shown. */
private data class OfflineChapter(
    val id: String,
    val name: String,
    val sort: Int,
    val pages: List<Uri>,
)

/**
 * Offline reader: a downloaded album straight from local storage, no network.
 *
 * It follows the online reader's shape - one chapter at a time, a chapter list, prev/next - so a
 * serial read on the train does not suddenly become one endless column of pages. Albums written by
 * builds without chapter folders show up as a single "older files" chapter instead of as nothing.
 */
@Composable
fun OfflineReaderScreen(
    navController: NavHostController,
    albumId: String,
    initialChapterId: String = "",
) {
    val downloadManager = LocalDownloadManager.current
    val progressStore = LocalReadingProgress.current
    val s = LocalAppStrings.current
    val chapterUi = s.chapterUi

    var album by remember { mutableStateOf<DownloadManager.DownloadedAlbum?>(null) }
    var manifest by remember { mutableStateOf<AlbumManifest?>(null) }
    var pages by remember { mutableStateOf<Map<String, List<Uri>>>(emptyMap()) }
    var loose by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    val storedProgress by progressStore.state.collectAsState()

    val chapters = remember(manifest, pages, loose) {
        val fromManifest = (manifest?.chapters ?: emptyList())
            .sortedBy { it.sort }
            .mapNotNull { c ->
                pages[c.chapterId]?.takeIf { it.isNotEmpty() }?.let { uris ->
                    OfflineChapter(c.chapterId, c.name, c.sort, uris)
                }
            }
        if (loose.isEmpty()) {
            fromManifest
        } else {
            fromManifest + OfflineChapter(
                id = LEGACY_CHAPTER_ID,
                name = chapterUi.legacyFiles,
                sort = Int.MAX_VALUE,
                pages = loose,
            )
        }
    }

    var currentChapterId by rememberSaveable {
        mutableStateOf(
            initialChapterId.ifBlank {
                progressStore.lastChapter(albumId)?.chapterId.orEmpty()
            },
        )
    }

    val currentIndex = chapters.indexOfFirst { it.id == currentChapterId }
    val current = chapters.getOrNull(currentIndex)
    val currentPages = current?.pages.orEmpty()

    // Only the chapter the screen opened on resumes; a chapter picked from the list starts at its
    // first page, exactly like the online reader.
    var restorePending by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(albumId) {
        withContext(Dispatchers.IO) {
            album = downloadManager.getAlbum(albumId)
            manifest = downloadManager.manifest(albumId)
            val local = downloadManager.localPages(albumId)
            pages = local.chapters
            loose = local.loose
        }
        loaded = true
    }

    // Landed on a chapter that is not on disk (e.g. the saved one was deleted): take the first.
    LaunchedEffect(chapters, loaded) {
        if (!loaded || chapters.isEmpty()) return@LaunchedEffect
        if (chapters.none { it.id == currentChapterId }) {
            currentChapterId = chapters.first().id
        }
    }

    // Every chapter opens at its first page unless a saved position says otherwise; without this
    // the column kept the offset of the previous chapter.
    LaunchedEffect(currentChapterId, currentPages.size) {
        if (currentPages.isEmpty()) return@LaunchedEffect
        val saved = if (restorePending) progressStore.chapter(albumId, currentChapterId) else null
        val target = ((saved?.page ?: 1) - 1).coerceIn(0, currentPages.size - 1)
        restorePending = false
        progress = target
        listState.scrollToItem(target)
    }

    LaunchedEffect(listState, currentPages.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> if (currentPages.isNotEmpty()) progress = idx.coerceIn(0, currentPages.size - 1) }
    }

    LaunchedEffect(currentChapterId, currentPages.size) {
        if (currentPages.isEmpty() || current == null) return@LaunchedEffect
        snapshotFlow { progress }.collectLatest { p ->
            delay(OFFLINE_PROGRESS_SAVE_MS)
            progressStore.record(albumId, current.id, current.name, p + 1, currentPages.size)
        }
    }

    val latest by rememberUpdatedState(
        Triple(current, currentPages.size, progress),
    )
    DisposableEffect(albumId) {
        onDispose {
            val (chapter, total, page) = latest
            if (chapter != null && total > 0) {
                progressStore.record(albumId, chapter.id, chapter.name, page + 1, total)
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            Surface(color = Color(0xCC000000)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back, tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            album?.name ?: "JM$albumId",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (current != null) {
                            Text(
                                current.name.ifBlank { s.chapterFmt.format(current.sort) },
                                color = Color(0xFFBDBDBD),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { showChapters = true }) {
                        Icon(Icons.Filled.MenuBook, contentDescription = s.reader.chapterList, tint = Color.White)
                    }
                }
            }
        },
        bottomBar = {
            if (current != null && currentPages.isNotEmpty()) {
                Surface(color = Color(0xCC000000)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            enabled = currentIndex > 0,
                            onClick = { currentChapterId = chapters[currentIndex - 1].id },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = if (currentIndex > 0) Color.White else Color.Gray,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                s.reader.prevChapter,
                                color = if (currentIndex > 0) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                        Text(
                            "${progress + 1} / ${currentPages.size}",
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        TextButton(
                            enabled = currentIndex in 0 until chapters.size - 1,
                            onClick = { currentChapterId = chapters[currentIndex + 1].id },
                        ) {
                            Text(
                                s.reader.nextChapter,
                                color = if (currentIndex in 0 until chapters.size - 1) Color.White else Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = if (currentIndex in 0 until chapters.size - 1) Color.White else Color.Gray,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            when {
                !loaded -> LoadingView()
                currentPages.isEmpty() -> EmptyView(s.emptyDownloads, Modifier.fillMaxSize())
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(currentPages, key = { i, u -> "${current?.id}-$i-$u" }) { _, uri ->
                        OfflinePageImage(uri)
                    }
                }
            }
        }
    }

    if (showChapters) {
        val readMap = storedProgress[albumId].orEmpty()
        ChapterPickerDialog(
            entries = chapters.map { c ->
                val saved = readMap[c.id]
                ChapterEntry(
                    id = c.id,
                    sort = c.sort,
                    name = c.name.ifBlank { s.chapterFmt.format(c.sort) },
                    totalPages = c.pages.size,
                    readState = chapterReadState(saved, c.pages.size),
                    page = saved?.page ?: 0,
                    downloadedPages = c.pages.size,
                )
            },
            currentId = currentChapterId,
            onSelect = { entry ->
                currentChapterId = entry.id
                showChapters = false
            },
            onDismiss = { showChapters = false },
        )
    }
}

/** How long the offline reader idles before it writes the position. */
private const val OFFLINE_PROGRESS_SAVE_MS = 800L

/** Pseudo chapter id for pages written before downloads were split per chapter. */
private const val LEGACY_CHAPTER_ID = "legacy"

@Composable
private fun OfflinePageImage(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        if (bitmap == null) failed = true
    }

    val bmp = bitmap
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            bmp != null -> androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else 3f / 4f),
            )
            failed -> Text(LocalAppStrings.current.reader.imageLoadFail, color = Color.Gray, modifier = Modifier.padding(24.dp))
            else -> CircularProgressIndicator(Modifier.padding(40.dp))
        }
    }
}
