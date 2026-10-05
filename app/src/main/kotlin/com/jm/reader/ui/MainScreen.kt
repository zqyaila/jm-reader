package com.jm.reader.ui

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
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jm.reader.ui.category.CategoriesScreen
import com.jm.reader.ui.home.HomeScreen
import com.jm.reader.ui.library.LibraryScreen
import com.jm.reader.ui.member.MemberScreen
import com.jm.reader.ui.theme.GlassBar

@Composable
fun MainScreen(navController: NavHostController) {
    val s = LocalAppStrings.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        // The tab canvas is the (near-opaque) theme background: content must not sit on the
        // gradient. Only the bottom bar below is glass.
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            GlassBar(Modifier.fillMaxWidth()) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
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

/** Selected-tab pill sits on the blurred bar without painting an opaque strip over it. */
@Composable
private fun glassNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
