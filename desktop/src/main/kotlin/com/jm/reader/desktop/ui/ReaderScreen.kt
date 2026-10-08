package com.jm.reader.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
// `animateScrollBy` is a *top-level extension* on ScrollableState in `foundation.gestures`, not a
// member of LazyListState — without this import it does not resolve.
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ImageLoader
import com.jm.reader.desktop.core.JmId
import com.jm.reader.desktop.core.ReadData
import com.jm.reader.desktop.core.RepoResult
import com.jm.reader.desktop.core.SeriesItem
import kotlinx.coroutines.launch

/**
 * Vertical reader.
 *
 * Pages are laid out in a single scrolling column and each one is de-scrambled on decode (see
 * [ImageLoader]). PageUp/PageDown, the arrow keys and the mouse wheel all scroll, and the reading
 * column is capped in width so a long chapter stays readable on a wide monitor.
 */
@Composable
fun ReaderScreen(
    albumId: String,
    chapterId: String,
    chapterName: String,
    onBack: () -> Unit,
    onOpenChapter: (chapterId: String, chapterName: String) -> Unit,
) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var read by remember(chapterId) { mutableStateOf<ReadData?>(null) }
    var loading by remember(chapterId) { mutableStateOf(true) }
    var error by remember(chapterId) { mutableStateOf<String?>(null) }
    var series by remember(albumId) { mutableStateOf<List<SeriesItem>>(emptyList()) }

    LaunchedEffect(albumId) {
        // The chapter list is what makes "next chapter" possible; failing to get it only costs
        // that button, so a failure is swallowed rather than blocking the reader.
        series = (repo.getAlbum(albumId) as? RepoResult.Ok)?.data?.series.orEmpty()
    }

    LaunchedEffect(chapterId) {
        loading = true
        error = null
        when (val r = repo.comicRead(chapterId)) {
            is RepoResult.Ok -> read = r.data
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(chapterId) {
        // Start each chapter at the top.
        listState.scrollToItem(0)
        runCatching { focus.requestFocus() }
    }

    val sorted = remember(series) { series.sortedBy { it.sort } }
    val index = sorted.indexOfFirst { it.id == chapterId }
    val prev = sorted.getOrNull(index - 1).takeIf { index > 0 }
    val next = if (index >= 0) sorted.getOrNull(index + 1) else null

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = read?.name?.takeIf { it.isNotBlank() } ?: chapterName,
            subtitle = read?.let { "${it.images.size} ${s.pages}" } ?: JmId.display(chapterId),
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = { prev?.let { onOpenChapter(it.id, it.name) } },
                    enabled = prev != null,
                ) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = s.prevChapter)
                }
                IconButton(
                    onClick = { next?.let { onOpenChapter(it.id, it.name) } },
                    enabled = next != null,
                ) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = s.nextChapter)
                }
            },
        )

        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val viewport = listState.layoutInfo.viewportSize.height.takeIf { it > 0 } ?: 800
                        when (event.key) {
                            Key.PageDown, Key.Spacebar, Key.DirectionDown -> {
                                scope.launch { listState.animateScrollBy(viewport * 0.9f) }
                                true
                            }
                            Key.PageUp, Key.DirectionUp -> {
                                scope.launch { listState.animateScrollBy(-viewport * 0.9f) }
                                true
                            }
                            else -> false
                        }
                    }
                },
        ) {
            when {
                loading && read == null -> LoadingView(label = s.loading)

                // Named arguments on purpose: `ErrorView`'s last parameter is `modifier`, so a
                // trailing lambda would bind to it instead of to `onRetry`.
                error != null && read == null -> ErrorView(
                    message = error.orEmpty(),
                    onRetry = {
                        scope.launch {
                            loading = true
                            error = null
                            when (val r = repo.comicRead(chapterId)) {
                                is RepoResult.Ok -> read = r.data
                                is RepoResult.Err -> error = r.message
                            }
                            loading = false
                        }
                    },
                )

                read == null -> EmptyView(s.loadFail)

                else -> {
                    val data = read!!
                    val aid = data.id.toLongOrNull() ?: 0L
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(data.images, key = { it.page }) { page ->
                            ReaderPage(
                                url = repo.imgUrl(page.image),
                                aid = aid,
                                scrambleId = data.scrambleId,
                            )
                        }
                        item {
                            Row(
                                Modifier.fillMaxWidth().padding(28.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    s.noMore,
                                    style = MaterialTheme.typography.bodySmall,
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

/**
 * One reader page.
 *
 * The placeholder is sized (not zero-height) so the scrollbar does not jump around while pages
 * arrive, and the aspect ratio is taken from the decoded bitmap so the column reflows exactly once
 * per page.
 */
@Composable
private fun ReaderPage(url: String, aid: Long, scrambleId: Long) {
    val s = LocalAppStrings.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val loaded = ImageLoader.load(url, aid, scrambleId)
        bitmap = loaded
        failed = loaded == null
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val b = bitmap
        when {
            b != null -> Image(
                bitmap = b,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = AppSizes.ReaderMaxWidth)
                    .aspectRatio(b.width.toFloat() / b.height.toFloat()),
            )

            failed -> Box(
                Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.imageLoadFail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> Box(
                Modifier.fillMaxWidth().height(420.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
