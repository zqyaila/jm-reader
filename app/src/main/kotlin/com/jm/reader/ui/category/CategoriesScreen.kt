package com.jm.reader.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jm.reader.data.model.Category
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.BottomNavReserve
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.components.ErrorView
import com.jm.reader.ui.components.LoadingView
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.theme.GlassShape
import com.jm.reader.ui.theme.glassSurface
import kotlinx.coroutines.launch

/**
 * Keeps the category list while the tab's navigation entry is on the back stack, so returning
 * from a comic (or switching tabs and back) does not clear it and re-fetch from scratch.
 */
internal class CategoriesViewModel : ViewModel() {
    val categories = mutableStateOf<List<Category>>(emptyList())
    val loading = mutableStateOf(true)
    val error = mutableStateOf<String?>(null)
    val reloadKey = mutableStateOf(0)

    /** Set once the list has been requested, so re-entering does not blank it out and reload. */
    var loaded = false

    /** The `reloadKey` value the current list was fetched for; a new one means a real retry. */
    var lastLoadedKey = 0
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(navController: NavHostController, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val s = LocalAppStrings.current
    val vm: CategoriesViewModel = viewModel()
    var categories by vm.categories
    var loading by vm.loading
    var error by vm.error
    var reloadKey by vm.reloadKey

    LaunchedEffect(reloadKey) {
        // Only load on the first entry, when the reader asked for a retry (`reloadKey++`), or when
        // the previous attempt failed. Re-entering the tab must not blank the list and re-fetch it.
        if (vm.loaded && reloadKey == vm.lastLoadedKey && error == null) return@LaunchedEffect
        vm.loaded = true
        vm.lastLoadedKey = reloadKey
        loading = true
        when (val r = repo.categories()) {
            is RepoResult.Ok -> { categories = r.data; error = null }
            is RepoResult.Err -> error = r.message
        }
        loading = false
    }

    Box(modifier.fillMaxSize()) {
        when {
            loading && categories.isEmpty() -> LoadingView()
            error != null && categories.isEmpty() -> ErrorView(error!!, onRetry = { reloadKey++ })
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                // Bottom reserve so the last row clears the floating glass nav pill.
                contentPadding = PaddingValues(bottom = BottomNavReserve),
            ) {
                item {
                    Text(
                        s.categories,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                    )
                }
                items(categories, key = { it.slug }) { cat ->
                    // Each group is a glass pane, so the category page reads as layered glass.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .glassSurface(shape = GlassShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            cat.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        if (cat.subCategories.isEmpty()) {
                            SuggestionChip(
                                onClick = { navController.navigate(Routes.comicList("category", cat.title, cat.slug)) },
                                label = { Text(cat.title, maxLines = 1) },
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                cat.subCategories.forEach { sub ->
                                    SuggestionChip(
                                        onClick = {
                                            navController.navigate(Routes.comicList("category", sub.title, sub.slug))
                                        },
                                        label = { Text(sub.title, maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
