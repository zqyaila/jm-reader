package com.jm.reader.ui.forum

import android.text.Html
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.jm.reader.data.model.ForumItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.theme.GlassShapeSmall
import com.jm.reader.ui.theme.glassSurface
import kotlinx.coroutines.launch

@Composable
fun ForumScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    var items by remember { mutableStateOf<List<ForumItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var viewing by remember { mutableStateOf<ForumItem?>(null) }

    LaunchedEffect(reload) {
        loading = true
        when (val r = repo.forum()) {
            is RepoResult.Ok -> { items = r.data; error = null }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Scaffold(topBar = { AppTopBar(s.forum, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingView()
                error != null -> ErrorView(error!!, onRetry = { reload++ })
                items.isEmpty() -> EmptyView(s.emptyForum, Modifier.fillMaxSize())
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.cid }) { post ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .glassSurface(shape = GlassShapeSmall)
                                .clickable { viewing = post }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    post.content.let { stripHtml(it) }.let { if (it.length > 60) it.take(60) + "…" else it },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(Modifier.padding(top = 4.dp)) {
                                    Text(post.nickname, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                    if (post.addtime.isNotBlank()) {
                                        Text(post.addtime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { post ->
        Dialog(onDismissRequest = { viewing = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Text(post.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stripHtml(post.content),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                )
                TextButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.End)) { Text(s.close) }
            }
        }
    }
}

private fun stripHtml(html: String): String {
    return runCatching {
        val spanned = if (android.os.Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            @Suppress("DEPRECATION") Html.fromHtml(html)
        }
        spanned.toString()
    }.getOrDefault(html)
}
