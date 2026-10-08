package com.jm.reader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.theme.AppSpacing
import com.jm.reader.ui.theme.GlassBar

/**
 * The single top bar every non-tab screen uses.
 *
 * Refactor notes (M3 Expressive pass):
 *  - the title now carries [MaterialTheme.typography]'s `titleLarge` (22sp SemiBold) instead of
 *    Material's default, so the bar titles sit a full step above the body text around them. The
 *    type scale is *not* hardcoded here - change it in `theme/Type.kt` and every bar follows.
 *  - [actions] gives screens a right-hand slot (search, share, overflow) so they stop hand-rolling
 *    their own bars. It defaults to empty, so all existing two-argument call sites are unaffected.
 *  - the bar has no fixed height *on purpose* - see the comment on the `TopAppBar` below.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val s = LocalAppStrings.current
    // Frosted bar: the blurred backdrop shows through instead of an opaque strip.
    GlassBar(Modifier.fillMaxWidth()) {
        // The bar deliberately keeps Material's own height and inset handling.
        //
        // Do NOT put `Modifier.height(...)` on this TopAppBar: `TopAppBar` applies its
        // `windowInsets` (the status-bar strip) *inside* its own measured height, so constraining
        // the outer height squeezes that padding and the 64 dp content row into the same space -
        // which clips the title under the system bar. That is exactly the regression this comment
        // exists to prevent. Container colours stay transparent so the glass behind shows through.
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                if (onBack != null) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = s.back,
                        )
                    }
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        )
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(AppSpacing.Section),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = AppSpacing.ScreenEdge)) {
                Text(s.retry)
            }
        }
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Responsive comic grid.
 *
 * Was `GridCells.Fixed(3)`, which is exactly right on a phone and wrong on everything else - a
 * tablet got three enormous tiles and a foldable got three stretched ones. `Adaptive(108.dp)` still
 * yields 3 columns on every common phone width (411dp / 360dp), so the phone layout is unchanged,
 * but a 600dp tablet now gets 4 and a wide foldable gets 6. That is the "adaptive" half of M3
 * Expressive: one layout that *fits*, rather than one layout plus a breakpoint table.
 */
@Composable
fun ComicGrid(
    items: List<ComicListItem>,
    repo: AppRepository,
    onItemClick: (ComicListItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.Tight),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardInner),
    ) {
        items(items, key = { it.id }) { item ->
            ComicCard(item = item, repo = repo, onClick = { onItemClick(item) })
        }
    }
}
