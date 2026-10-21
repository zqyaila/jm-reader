package com.jm.reader.ui.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.data.download.DownloadManager
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalDownloadManager
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.LoadingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Offline reader: displays a downloaded album straight from local storage, no network. */
@Composable
fun OfflineReaderScreen(navController: NavHostController, albumId: String) {
    val downloadManager = LocalDownloadManager.current
    val s = LocalAppStrings.current
    var album by remember { mutableStateOf<DownloadManager.DownloadedAlbum?>(null) }
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(albumId) {
        withContext(Dispatchers.IO) {
            album = downloadManager.getAlbum(albumId)
            uris = downloadManager.albumImageUris(albumId)
        }
        loaded = true
    }

    LaunchedEffect(listState, uris.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx -> if (uris.isNotEmpty()) progress = idx.coerceIn(0, uris.size - 1) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                (album?.name ?: "JM$albumId") + "  (${progress + 1}/${uris.size})",
                onBack = { navController.popBackStack() },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            when {
                !loaded -> LoadingView()
                uris.isEmpty() -> EmptyView(s.emptyDownloads, Modifier.fillMaxSize())
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(uris, key = { _, u -> u.toString() }) { _, uri ->
                        OfflinePageImage(uri)
                    }
                }
            }
        }
    }
}

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
