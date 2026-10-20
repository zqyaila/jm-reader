package com.jm.reader.ui.novels

import android.text.Html
import android.text.Spanned
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class NovelChapter(val ncid: String, val title: String, val sort: String, val needBuy: Boolean)

@Composable
fun NovelDetailScreen(navController: NavHostController, nid: String) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var chapters by remember { mutableStateOf<List<NovelChapter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var readingChapter by remember { mutableStateOf<NovelChapter?>(null) }
    var chapterContent by remember { mutableStateOf("") }
    var chapterLoading by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        when (val r = repo.novelDetail(nid)) {
            is RepoResult.Ok -> {
                detail = r.data
                chapters = r.data.optJSONArray("series")?.let { ja ->
                    (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let {
                        NovelChapter(
                            ncid = it.optString("NCID").ifBlank { it.optString("id") },
                            title = it.optString("title").ifBlank { it.optString("name") },
                            sort = it.optString("sort"),
                            needBuy = it.optString("is_need_buy_nc") != "0" || it.optBoolean("is_need_buy_nc"),
                        )
                    } }
                } ?: emptyList()
                error = null
            }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }
    LaunchedEffect(nid) { load() }

    Scaffold(topBar = { AppTopBar(s.novelDetail, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && detail == null -> LoadingView()
                error != null && detail == null -> ErrorView(error!!, onRetry = { scope.launch { load() } })
                detail == null -> ErrorView(s.loadFail, onRetry = { scope.launch { load() } })
                else -> {
                    val d = detail!!
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            val coverPath = d.optString("images")
                            val cover = if (coverPath.isNotBlank()) repo.imgUrl(coverPath) else ""
                            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                if (cover.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        item {
                            Column(Modifier.padding(12.dp)) {
                                Text(d.optString("name"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                val author = d.opt("author")
                                val authorStr = when (author) {
                                    is org.json.JSONArray -> (0 until author.length()).joinToString(" / ") { author.optString(it) }
                                    else -> author?.toString() ?: ""
                                }
                                if (authorStr.isNotBlank()) {
                                    Text("${s.authorLabel}$authorStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                }
                                Text(d.optString("description").ifBlank { s.noDescription }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        item {
                            Text("${s.chapters} (${chapters.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 4.dp))
                        }
                        if (chapters.isEmpty()) {
                            item { Text(s.noChapters, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            items(chapters, key = { it.ncid }) { ch ->
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        if (ch.needBuy) {
                                            readingChapter = ch
                                            chapterContent = s.needPurchase
                                        } else {
                                            readingChapter = ch
                                            scope.launch {
                                                chapterLoading = true
                                                when (val r = repo.novelChapters(ch.ncid)) {
                                                    is RepoResult.Ok -> chapterContent = stripHtml(r.data.optString("content"))
                                                    is RepoResult.Err -> chapterContent = "${s.loadFail}：${r.message}"
                                                }
                                                chapterLoading = false
                                            }
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        ch.title.ifBlank { s.chapterFmt.format(ch.sort.toIntOrNull() ?: 0) },
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (ch.needBuy) {
                                        Text(s.needPurchase, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                            }
                        }
                        item { Box(Modifier.padding(bottom = 24.dp)) }
                    }
                }
            }
        }
    }

    readingChapter?.let { ch ->
        Dialog(onDismissRequest = { readingChapter = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.title.ifBlank { s.read }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { readingChapter = null }) { Text(s.close) }
                }
                if (chapterLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingView(Modifier.heightIn(max = 120.dp)) }
                } else {
                    Text(
                        chapterContent,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private fun stripHtml(html: String): String {
    return runCatching {
        val spanned: Spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION") Html.fromHtml(html)
        }
        spanned.toString()
    }.getOrDefault(html)
}
