package com.jm.reader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.ui.category.CategoriesScreen
import com.jm.reader.ui.home.HomeScreen
import com.jm.reader.ui.library.LibraryScreen
import com.jm.reader.ui.member.MemberScreen
import com.jm.reader.ui.theme.BrandOrangeDeep
import com.jm.reader.ui.theme.GlassBar
import com.jm.reader.ui.theme.GlassShape

@Composable
fun MainScreen(navController: NavHostController) {
    val s = LocalAppStrings.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        // Translucent canvas so the backdrop colour carries through; the tab content still gets its
        // own scrims (cards, rows), and the bar below is the clearest glass surface.
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Floating, rounded, translucent: the page keeps showing around and behind the bar,
            // which is what makes it read as liquid glass rather than a solid strip.
            GlassBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                shape = GlassShape,
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.clip(GlassShape),
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Filled.Home, contentDescription = s.home) },
                        label = { Text(s.home) },
                        colors = glassNavColors(),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Filled.Category, contentDescription = s.categories) },
                        label = { Text(s.categories) },
                        colors = glassNavColors(),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.LibraryBooks, contentDescription = s.library) },
                        label = { Text(s.library) },
                        colors = glassNavColors(),
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Filled.Person, contentDescription = s.member) },
                        label = { Text(s.member) },
                        colors = glassNavColors(),
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        Box(Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> HomeScreen(navController, contentModifier)
                1 -> CategoriesScreen(navController, contentModifier)
                2 -> LibraryScreen(navController, contentModifier)
                else -> MemberScreen(navController, contentModifier)
            }
        }
    }
}

/**
 * Selected-tab colours that stay readable on the glass bar in both themes. The light scheme uses
 * the deep orange for the accent: the brand orange (#FF6F00) only reaches ~2.9:1 on a white bar,
 * which is fine for a large filled button but not for a nav label.
 */
@Composable
private fun glassNavColors(): NavigationBarItemColors {
    val dark = isSystemInDarkTheme()
    val accent = if (dark) MaterialTheme.colorScheme.primary else BrandOrangeDeep
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    return NavigationBarItemDefaults.colors(
        selectedIconColor = accent,
        selectedTextColor = accent,
        indicatorColor = accent.copy(alpha = if (dark) 0.22f else 0.16f),
        unselectedIconColor = idle,
        unselectedTextColor = idle,
    )
}
