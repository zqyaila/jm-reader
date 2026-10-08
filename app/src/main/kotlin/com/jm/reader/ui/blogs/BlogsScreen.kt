package com.jm.reader.ui.blogs

import com.jm.reader.util.stripHtml
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.jm.reader.data.model.BlogItem
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.EmptyView
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.theme.GlassShape
import com.jm.reader.ui.theme.GlassShapeLarge
import com.jm.reader.ui.theme.glassClickable
import com.jm.reader.ui.theme.glassSurface
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun BlogsScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<BlogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var viewing by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(reload) {
        loading = true
        when (val r = repo.blogs()) {
            is RepoResult.Ok -> { items = r.data; error = null }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Scaffold(topBar = { AppTopBar(s.blogs, onBack = { navController.popBackStack() }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> LoadingView()
                error != null -> ErrorView(error!!, onRetry = { reload++ })
                items.isEmpty() -> EmptyView(s.emptyBlogs, Modifier.fillMaxSize())
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { blog ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                .glassClickable(shape = GlassShape) {
                                    scope.launch {
                                        when (val r = repo.blogInfo(blog.id)) {
                                            is RepoResult.Ok -> viewing = r.data
                                            is RepoResult.Err -> Unit
                                        }
                                    }
                                }
                                .glassSurface(shape = GlassShape)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(blog.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (blog.image.isNotBlank()) {
                                androidx.compose.foundation.Image(
                                    painter = coil.compose.rememberAsyncImagePainter(
                                        model = repo.imgUrl(blog.image),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { blog ->
        Dialog(onDismissRequest = { viewing = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .glassSurface(shape = GlassShapeLarge)
                    .padding(16.dp),
            ) {
                Text(blog.optString("title").ifBlank { s.article }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Text(
                    stripHtml(blog.optString("content")),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp),
                )
                TextButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.End)) { Text(s.close) }
            }
        }
    }
}
