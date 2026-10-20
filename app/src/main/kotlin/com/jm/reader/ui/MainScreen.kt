package com.jm.reader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.ui.category.CategoriesScreen
import com.jm.reader.ui.home.HomeScreen
import com.jm.reader.ui.library.LibraryScreen
import com.jm.reader.ui.member.MemberScreen
import com.jm.reader.ui.theme.AppMotion
import com.jm.reader.ui.theme.AppTheme
import com.jm.reader.ui.theme.GlassBar
import com.jm.reader.ui.theme.LocalAppTheme
import com.jm.reader.ui.theme.GlassCapsule
import com.jm.reader.ui.theme.GlassKind
import com.jm.reader.ui.theme.GlassSizes
import com.jm.reader.ui.theme.glassClickable
import kotlin.math.roundToInt

/** Vertical space the floating navigation pill occupies, reserved by scrolling content. */
val BottomNavReserve = 96.dp

/**
 * Tab shell.
 *
 * The page content is recorded into a [GraphicsLayer] and the navigation bar is drawn as a
 * **sibling overlay outside that recording**, so the bar can sample and blur the live page behind
 * it (the "liquid glass" look of the skill's glass bottom bar) without sampling itself.
 */
@Composable
fun MainScreen(navController: NavHostController) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val pageLayer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize()) {
        // The recording box starts at the Compose root origin, so a glass pane's
        // `positionInRoot` maps 1:1 onto the recorded layer when it samples it.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    pageLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(pageLayer)
                },
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                when (selectedTab) {
                    0 -> HomeScreen(navController, Modifier.fillMaxSize())
                    1 -> CategoriesScreen(navController, Modifier.fillMaxSize())
                    2 -> LibraryScreen(navController, Modifier.fillMaxSize())
                    else -> MemberScreen(navController, Modifier.fillMaxSize())
                }
            }
        }

        GlassBottomNav(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            backdropLayer = pageLayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * Floating glass pill with a sliding selection indicator (spring, damped).
 *
 * Refactor notes: the indicator used to be a *solid accent pill* behind the whole tab, which made
 * the bar the loudest thing on screen and left the selected label fighting a saturated orange
 * field. It is now a 14% accent wash with an accent icon/label - the bar recedes, the selected tab
 * is still unmistakable.
 */
@Composable
private fun GlassBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdropLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val accent = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    // Archive marks the active tab with a solid ink block (the masthead's brand block inverted);
    // ember keeps the softer 14 % wash it was tuned with.
    val archive = LocalAppTheme.current == AppTheme.ARCHIVE
    val indicator = if (archive) accent else accent.copy(alpha = 0.14f)
    val activeTint = if (archive) MaterialTheme.colorScheme.onPrimary else accent
    val items = listOf(
        s.home to Icons.Filled.Home,
        s.categories to Icons.Filled.Category,
        s.library to Icons.Filled.LibraryBooks,
        s.member to Icons.Filled.Person,
    )

    GlassBar(
        modifier = modifier,
        // Spec: bars and buttons are capsules, not rounded rectangles.
        shape = GlassCapsule,
        // Content scrolls underneath this bar, so it uses the densest fog.
        kind = GlassKind.BottomBar,
        backdropLayer = backdropLayer,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(GlassSizes.BottomBarHeight)
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val itemWidth = maxWidth / items.size
            val position by animateFloatAsState(
                targetValue = selected.toFloat(),
                animationSpec = AppMotion.spatialFast,
                label = "navIndicator",
            )

            Box(
                Modifier
                    .offset { IntOffset((position * itemWidth.toPx()).roundToInt(), 0) }
                    .width(itemWidth)
                    .height(GlassSizes.BottomTabHeight)
                    .padding(horizontal = 6.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(indicator),
            )

            Row(
                Modifier.fillMaxWidth().height(GlassSizes.BottomTabHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, (label, icon) ->
                    val active = index == selected
                    val tint = if (active) activeTint else idle
                    Column(
                        Modifier
                            .width(itemWidth)
                            .height(GlassSizes.BottomTabHeight)
                            .glassClickable(shape = MaterialTheme.shapes.small, onClick = { onSelect(index) }),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = tint,
                            // 24dp: Material icons are drawn on a 24dp grid.
                            modifier = Modifier.size(23.dp),
                        )
                        Text(
                            label,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
