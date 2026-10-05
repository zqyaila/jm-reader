package com.jm.reader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.jm.reader.ui.theme.BrandOrangeDeep
import com.jm.reader.ui.theme.BrandOrangeSoft
import com.jm.reader.ui.theme.DarkBg
import com.jm.reader.ui.theme.GlassBar
import com.jm.reader.ui.theme.GlassKind
import com.jm.reader.ui.theme.GlassShapeLarge
import com.jm.reader.ui.theme.GlassShapeSmall
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
 * Floating glass pill with a sliding selection indicator (spring, damped) - the material from the
 * skill's `LiquidBottomTabs`, simplified to a click-driven indicator.
 */
@Composable
private fun GlassBottomNav(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdropLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val dark = isSystemInDarkTheme()
    // Selected item = a solid accent pill with an explicit on-colour, which is what makes the
    // label legible regardless of what the glass samples: #FFB74D on #121212 is 10:1, and the deep
    // orange on white is 5.6:1. (Tinting the pill instead would leave the label at ~3.4:1.)
    val accent = if (dark) BrandOrangeSoft else BrandOrangeDeep
    val onAccent = if (dark) DarkBg else Color.White
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val items = listOf(
        s.home to Icons.Filled.Home,
        s.categories to Icons.Filled.Category,
        s.library to Icons.Filled.LibraryBooks,
        s.member to Icons.Filled.Person,
    )

    GlassBar(
        modifier = modifier,
        shape = GlassShapeLarge,
        // Content scrolls underneath this bar, so it uses the densest fog.
        kind = GlassKind.BottomBar,
        backdropLayer = backdropLayer,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(6.dp),
        ) {
            val itemWidth = maxWidth / items.size
            // Damped spring, so the indicator glides and settles like a liquid rather than snapping.
            val position by animateFloatAsState(
                targetValue = selected.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "navIndicator",
            )

            // `Modifier.offset { }` works in **pixels**, so the dp-per-item has to be converted.
            // (Using `itemWidth.value` here made the pill travel only 1/density of the way, which
            // is why it appeared not to follow the selection.)
            Box(
                Modifier
                    .offset { IntOffset((position * itemWidth.toPx()).roundToInt(), 0) }
                    .width(itemWidth)
                    .height(50.dp)
                    .clip(GlassShapeSmall)
                    .background(accent),
            )

            Row(
                Modifier.fillMaxWidth().height(50.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, (label, icon) ->
                    val active = index == selected
                    val tint = if (active) onAccent else idle
                    Column(
                        Modifier
                            .width(itemWidth)
                            .clickable { onSelect(index) }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(21.dp),
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
