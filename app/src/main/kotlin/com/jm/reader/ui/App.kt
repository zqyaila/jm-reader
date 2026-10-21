package com.jm.reader.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.jm.reader.JMApplication
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.ui.blogs.BlogsScreen
import com.jm.reader.ui.components.LocalShelfFlight
import com.jm.reader.ui.components.ShelfFlightController
import com.jm.reader.ui.components.ShelfFlightOverlay
import com.jm.reader.ui.category.CategoriesScreen
import com.jm.reader.ui.daily.DailyScreen
import com.jm.reader.ui.detail.ComicDetailScreen
import com.jm.reader.ui.download.OfflineReaderScreen
import com.jm.reader.ui.forum.ForumScreen
import com.jm.reader.ui.games.GamesScreen
import com.jm.reader.ui.home.HomeScreen
import com.jm.reader.ui.library.LibraryScreen
import com.jm.reader.ui.list.ComicListScreen
import com.jm.reader.ui.member.LoginScreen
import com.jm.reader.ui.member.MemberScreen
import com.jm.reader.ui.member.RegisterScreen
import com.jm.reader.ui.movies.MovieDetailScreen
import com.jm.reader.ui.movies.MoviesScreen
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.novels.NovelDetailScreen
import com.jm.reader.ui.novels.NovelsScreen
import com.jm.reader.ui.reader.ReaderScreen
import com.jm.reader.ui.search.SearchScreen
import com.jm.reader.ui.lines.LinesScreen
import com.jm.reader.ui.splash.SplashScreen
import com.jm.reader.ui.stats.StatsScreen
import com.jm.reader.ui.theme.AppBackdrop
import com.jm.reader.ui.theme.ArchiveMotion
import com.jm.reader.ui.theme.AppTheme
import com.jm.reader.ui.theme.JMReaderTheme
import com.jm.reader.ui.theme.LocalAppTheme
import com.jm.reader.ui.week.WeekScreen

@Composable
fun JMRoot(app: JMApplication) {
    val language by app.languageManager.language.collectAsState()
    val theme by app.themeManager.theme.collectAsState()
    val strings = remember(language) { AppStrings.forLanguage(language) }
    // The theme wraps everything, so switching it re-shapes and re-colours the whole tree without
    // recreating the nav graph (the reader stays on the page it was on).
    JMReaderTheme(theme = theme) {
        AppContent(app, language, strings, theme)
    }
}

@Composable
private fun AppContent(
    app: JMApplication,
    language: com.jm.reader.ui.strings.UiLanguage,
    strings: AppStrings,
    theme: AppTheme,
) {
    // Owned at the root: a cover flight has to be drawn above the navigation host, and a shelf
    // inside a LazyColumn cannot paint outside its own row.
    val shelfFlight = remember { ShelfFlightController() }
    CompositionLocalProvider(
        LocalShelfFlight provides shelfFlight,
        LocalRepository provides app.repository,
        LocalSession provides app.session,
        LocalLanguageManager provides app.languageManager,
        LocalDownloadManager provides app.downloadManager,
        LocalHistoryManager provides app.historyManager,
        LocalSearchHistoryManager provides app.searchHistoryManager,
        LocalStats provides app.statsManager,
        LocalUpdateManager provides app.updateManager,
        LocalHostManager provides app.hostManager,
        LocalThemeManager provides app.themeManager,
        LocalAppTheme provides theme,
        LocalAppStrings provides strings,
    ) {
        val navController = rememberNavController()
        // One backdrop for the whole app: every translucent glass surface refracts this layer.
        Box(Modifier.fillMaxSize()) {
            AppBackdrop()
            AppNavHost(navController)
            ShelfFlightOverlay(shelfFlight)
        }
        // Show a copyable crash report if the previous run crashed.
        CrashReportOverlay(app)
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        // Route motion follows the reference's Cupertino page transition: the incoming sheet
        // travels its full width (420 ms), while the outgoing one only gives up a third of the
        // stage and stays opaque underneath. That parallax is what makes a stack read as paper.
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(ArchiveMotion.EnterMs, easing = ArchiveMotion.Enter),
                initialOffsetX = { it },
            )
        },
        exitTransition = {
            slideOutHorizontally(
                animationSpec = tween(ArchiveMotion.ExitMs, easing = ArchiveMotion.Exit),
                targetOffsetX = { -it / 3 },
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                animationSpec = tween(ArchiveMotion.EnterMs, easing = ArchiveMotion.Enter),
                initialOffsetX = { -it / 3 },
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(ArchiveMotion.ExitMs, easing = ArchiveMotion.Exit),
                targetOffsetX = { it },
            )
        },
    ) {
        composable(Routes.SPLASH) { SplashScreen(navController) }
        composable(Routes.MAIN) { MainScreen(navController) }

        composable(
            Routes.SEARCH,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "work" },
                navArgument("q") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            SearchScreen(
                navController,
                initialMode = entry.arguments?.getString("mode").orEmpty(),
                initialQuery = entry.arguments?.getString("q").orEmpty(),
            )
        }
        composable(Routes.CATEGORIES) { CategoriesScreen(navController, Modifier) }
        composable(Routes.STATS) { StatsScreen(navController) }
        composable(Routes.LINES) { LinesScreen(navController) }
        composable(Routes.WEEK) { WeekScreen(navController) }
        composable(Routes.DAILY) { DailyScreen(navController) }
        composable(Routes.HOT_TAGS) { SearchScreen(navController, initialHotTagsOnly = true) }
        composable(Routes.NOVELS) { NovelsScreen(navController) }
        composable(Routes.MOVIES) { MoviesScreen(navController) }
        composable(Routes.GAMES) { GamesScreen(navController) }
        composable(Routes.BLOGS) { BlogsScreen(navController) }
        composable(Routes.FORUM) { ForumScreen(navController) }

        // The dossier is entered through a cover flight, so the page itself fades and settles
        // instead of sliding: a slide would drag the cover away from the slot the flight just
        // landed it in. Pushing deeper (reader) and coming back keep the paper-stack slide.
        composable(
            Routes.COMIC_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            enterTransition = {
                fadeIn(tween(220)) + scaleIn(
                    initialScale = 0.985f,
                    animationSpec = tween(220),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    animationSpec = tween(ArchiveMotion.ExitMs, easing = ArchiveMotion.Exit),
                    targetOffsetX = { -it / 3 },
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    animationSpec = tween(ArchiveMotion.EnterMs, easing = ArchiveMotion.Enter),
                    initialOffsetX = { -it / 3 },
                )
            },
            popExitTransition = {
                fadeOut(tween(200)) + scaleOut(
                    targetScale = 0.985f,
                    animationSpec = tween(200),
                )
            },
        ) { entry ->
            ComicDetailScreen(navController, entry.arguments?.getString("id").orEmpty())
        }

        composable(
            Routes.READER,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("readId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ReaderScreen(
                navController,
                albumId = entry.arguments?.getString("id").orEmpty(),
                readId = entry.arguments?.getString("readId").orEmpty(),
            )
        }

        composable(
            Routes.COMIC_LIST,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("id") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            ComicListScreen(
                navController,
                type = entry.arguments?.getString("type").orEmpty(),
                title = entry.arguments?.getString("title").orEmpty(),
                id = entry.arguments?.getString("id").orEmpty(),
            )
        }

        composable(
            Routes.OFFLINE_READER,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            OfflineReaderScreen(navController, entry.arguments?.getString("id").orEmpty())
        }

        composable(
            Routes.NOVEL_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry -> NovelDetailScreen(navController, entry.arguments?.getString("id").orEmpty()) }

        composable(
            Routes.MOVIE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry -> MovieDetailScreen(navController, entry.arguments?.getString("id").orEmpty()) }

        composable(
            Routes.LOGIN,
            arguments = listOf(navArgument("username") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            LoginScreen(navController, initialUsername = entry.arguments?.getString("username").orEmpty())
        }
        composable(Routes.REGISTER) { RegisterScreen(navController) }
    }
}
