package com.jm.reader.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ApiClient
import com.jm.reader.desktop.core.AppStrings
import com.jm.reader.desktop.core.HostManager
import com.jm.reader.desktop.core.LanguageManager
import com.jm.reader.desktop.core.Repository
import com.jm.reader.desktop.core.Session
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The whole desktop application: dependency container, theme, composition locals and the
 * navigation shell.
 *
 * Navigation is a plain back stack rather than a navigation library. The desktop app has six
 * destinations and no deep links, so a `mutableStateListOf<Screen>` is the entire state machine —
 * and it keeps the build free of another multiplatform dependency to hold in version lockstep.
 */
sealed interface Screen {
    data object Splash : Screen
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Detail(val id: String) : Screen
    data class Reader(val albumId: String, val chapterId: String, val title: String) : Screen
}

/** Top-level tabs, in the order they appear in the bottom navigation. */
private val Tabs = listOf(Screen.Home, Screen.Search, Screen.Settings)

/** Wires the data layer together once per process. */
class AppContainer {
    val session = Session()
    val languageManager = LanguageManager(session)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    val api = ApiClient(session, http)
    val hostManager = HostManager(session, http)
    val repository = Repository(session, api, hostManager)
}

@Composable
fun App(container: AppContainer) {
    // Follow the OS theme on first run; the settings screen can override it for the session.
    //
    // `isSystemInDarkTheme()` is itself @Composable, so it cannot be read from inside the
    // `remember` lambda (that lambda is a plain initialiser, not a composable scope). Read it
    // here and hand it over as the remembered initial value.
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    val language by container.languageManager.language.collectAsState()
    val strings = remember(language) { AppStrings.forLanguage(language) }

    CompositionLocalProvider(
        LocalRepository provides container.repository,
        LocalSession provides container.session,
        LocalLanguageManager provides container.languageManager,
        LocalAppStrings provides strings,
        LocalDarkTheme provides dark,
    ) {
        JMReaderTheme(dark) {
            Box(Modifier.fillMaxSize()) {
                // One backdrop for the whole app: every translucent surface refracts this layer.
                AppBackdrop()
                AppShell(container, dark = dark, onToggleDark = { dark = !dark })
            }
        }
    }
}

@Composable
private fun AppShell(container: AppContainer, dark: Boolean, onToggleDark: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Splash) }
    val current = stack.last()

    val push: (Screen) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    // Switching tabs replaces the stack instead of growing it, so "back" inside a tab never has to
    // walk through every tab the user happened to visit.
    val switchTo: (Screen) -> Unit = { tab ->
        stack.clear()
        stack.add(tab)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                Screen.Splash -> SplashScreen(
                    container = container,
                    onReady = { switchTo(Screen.Home) },
                )

                Screen.Home -> HomeScreen(
                    onOpenComic = { push(Screen.Detail(it)) },
                    onOpenSearch = { switchTo(Screen.Search) },
                )

                Screen.Search -> SearchScreen(
                    onOpenComic = { push(Screen.Detail(it)) },
                )

                Screen.Settings -> SettingsScreen(
                    dark = dark,
                    onToggleDark = onToggleDark,
                )

                is Screen.Detail -> DetailScreen(
                    id = current.id,
                    onBack = pop,
                    onOpenComic = { push(Screen.Detail(it)) },
                    onRead = { chapterId, chapterName ->
                        push(Screen.Reader(current.id, chapterId, chapterName))
                    },
                )

                is Screen.Reader -> ReaderScreen(
                    albumId = current.albumId,
                    chapterId = current.chapterId,
                    chapterName = current.title,
                    onBack = pop,
                    onOpenChapter = { cid, name ->
                        // Replace the reader entry rather than stacking: "back" should leave the
                        // reader entirely, not walk back through every chapter that was opened.
                        stack[stack.size - 1] = Screen.Reader(current.albumId, cid, name)
                    },
                )
            }
        }

        if (current in Tabs) {
            GlassBottomNav(current = current, onSelect = switchTo)
        }
    }
}

/** Floating glass pill with a sliding selection indicator — the desktop twin of the phone shell. */
@Composable
private fun GlassBottomNav(
    current: Screen,
    onSelect: (Screen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    val dark = LocalDarkTheme.current
    // Selected item = a solid accent pill with an explicit on-colour, so the label stays legible
    // no matter what the glass is refracting.
    val accent = if (dark) BrandOrangeSoft else BrandOrangeDeep
    val onAccent = if (dark) DarkBg else Color.White
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    val items: List<Triple<Screen, String, ImageVector>> = listOf(
        Triple(Screen.Home, s.home, Icons.Filled.Home),
        Triple(Screen.Search, s.search, Icons.Filled.Search),
        Triple(Screen.Settings, s.settings, Icons.Filled.Settings),
    )
    val selected = items.indexOfFirst { it.first == current }.coerceAtLeast(0)

    Box(
        modifier.fillMaxWidth().padding(bottom = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier
                .width(400.dp)
                .height(64.dp)
                .glassSurface(shape = GlassCapsule, elevation = 14.dp)
                .padding(4.dp),
        ) {
            val itemWidth = maxWidth / items.size
            // `Modifier.offset { }` works in pixels, so the dp-per-item must be converted.
            val position by animateFloatAsState(
                targetValue = selected.toFloat(),
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 250f),
                label = "navIndicator",
            )

            Box(
                Modifier
                    .offset { IntOffset((position * itemWidth.toPx()).roundToInt(), 0) }
                    .width(itemWidth)
                    .height(56.dp)
                    .clip(GlassCapsule)
                    .background(accent),
            )

            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { index, (tab, label, icon) ->
                    val active = index == selected
                    val tint = if (active) onAccent else idle
                    Row(
                        Modifier
                            .width(itemWidth)
                            .height(56.dp)
                            .glassClickable(shape = GlassCapsule) { onSelect(tab) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            icon,
                            contentDescription = label,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            label,
                            color = tint,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
