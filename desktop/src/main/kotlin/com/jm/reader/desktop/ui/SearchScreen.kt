package com.jm.reader.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ComicListItem
import com.jm.reader.desktop.core.JmId
import com.jm.reader.desktop.core.RepoResult
import com.jm.reader.desktop.core.TagItem
import kotlinx.coroutines.launch

/**
 * Adaptive search: **one box, no mode chooser**.
 *
 * A keyword goes to the site search (which already matches titles, authors and tags); a bare
 * number, `JM123456` or an album URL resolves to an album id via [JmId] and the server answers with
 * `redirect_aid` instead of a list, which we follow straight into the detail page.
 */
@Composable
fun SearchScreen(onOpenComic: (String) -> Unit) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ComicListItem>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var page by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var endReached by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var hotTags by remember { mutableStateOf<List<TagItem>>(emptyList()) }

    val focusRequester = remember { FocusRequester() }

    suspend fun runSearch(raw: String, targetPage: Int) {
        val q = raw.trim()
        if (q.isEmpty()) return
        if (targetPage == 1) {
            loading = true
            error = null
        } else {
            loadingMore = true
        }
        when (val r = repo.adaptiveSearch(q, targetPage)) {
            is RepoResult.Ok -> {
                val p = r.data
                if (p.redirectAid != null) {
                    // The query was an album id: jump straight there instead of showing a list.
                    onOpenComic(p.redirectAid)
                } else {
                    results = if (targetPage == 1) p.items else (results + p.items).distinctBy { it.id }
                    total = p.total
                    page = targetPage
                    submitted = true
                    endReached = p.items.isEmpty()
                    error = null
                }
            }
            is RepoResult.Err -> {
                error = r.message
                if (targetPage == 1) {
                    results = emptyList()
                    submitted = true
                }
            }
        }
        loading = false
        loadingMore = false
    }

    fun submit() = scope.launch { runSearch(query, 1) }

    LaunchedEffect(Unit) {
        hotTags = (repo.hotTags() as? RepoResult.Ok)?.data ?: emptyList()
        runCatching { focusRequester.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = s.search)

        // Search field on its own glass card, capped in width so it does not stretch across a
        // 4K window.
        Box(Modifier.fillMaxWidth().padding(horizontal = AppSizes.ContentPadding, vertical = 12.dp), contentAlignment = Alignment.Center) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(s.searchHint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            results = emptyList()
                            submitted = false
                            error = null
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = s.clear)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            submit()
                            true
                        } else {
                            false
                        }
                    },
            )
        }

        if (JmId.looksLikeId(query)) {
            Text(
                s.searchLooksLikeId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = AppSizes.ContentPadding, vertical = 0.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> LoadingView(label = s.loading)

                !submitted -> HotTags(
                    title = s.hotTagsTitle,
                    tags = hotTags,
                    onPick = {
                        query = it
                        submit()
                    },
                )

                error != null && results.isEmpty() -> ErrorView(
                    message = error.orEmpty(),
                    onRetry = { scope.launch { runSearch(query, 1) } },
                )

                results.isEmpty() -> EmptyView(s.noResult)

                else -> {
                    val gridState = rememberLazyGridState()

                    LaunchedEffect(gridState, endReached) {
                        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                            .collect { last ->
                                val count = gridState.layoutInfo.totalItemsCount
                                if (last != null && count > 0 && last >= count - 6 && !loadingMore && !endReached) {
                                    scope.launch { runSearch(query, page + 1) }
                                }
                            }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 168.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = AppSizes.ContentPadding,
                            end = AppSizes.ContentPadding,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (total > 0) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    s.searchTotalFmt.format(total),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        items(results, key = { it.id }) { item ->
                            ComicCard(
                                item = item,
                                repo = repo,
                                showAuthor = true,
                                onClick = { onOpenComic(item.id) },
                            )
                        }

                        if (loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(26.dp),
                                    )
                                }
                            }
                        }

                        if (endReached && results.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    s.noMore,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotTags(title: String, tags: List<TagItem>, onPick: (String) -> Unit) {
    if (tags.isEmpty()) {
        // Hot tags are a convenience, not a requirement: if the call fails, say so quietly rather
        // than spinning forever.
        EmptyView(LocalAppStrings.current.noResult)
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = AppSizes.ContentPadding)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tags.forEach { tag ->
                Box(
                    Modifier
                        .height(36.dp)
                        .glassClickable(shape = GlassCapsule) { onPick(tag.title) }
                        .glassSurface(shape = GlassCapsule, elevation = 4.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tag.title, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
