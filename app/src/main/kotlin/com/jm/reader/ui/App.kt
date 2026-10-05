package com.jm.reader.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.jm.reader.ui.splash.SplashScreen
import com.jm.reader.ui.theme.AppBackdrop
import com.jm.reader.ui.week.WeekScreen

@Composable
fun JMRoot(app: JMApplication) {
    val language by app.languageManager.language.collectAsState()
    val strings = remember(language) { AppStrings.forLanguage(language) }
    CompositionLocalProvider(
        LocalRepository provides app.repository,
        LocalSession provides app.session,
        LocalLanguageManager provides app.languageManager,
        LocalDownloadManager provides app.downloadManager,
        LocalHistoryManager provides app.historyManager,
        LocalAppStrings provides strings,
    ) {
        val navController = rememberNavController()
        // One backdrop for the whole app: every translucent glass surface refracts this layer.
        Box(Modifier.fillMaxSize()) {
            AppBackdrop()
            AppNavHost(navController)
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
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) },
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
        composable(Routes.WEEK) { WeekScreen(navController) }
        composable(Routes.DAILY) { DailyScreen(navController) }
        composable(Routes.HOT_TAGS) { SearchScreen(navController, initialHotTagsOnly = true) }
        composable(Routes.NOVELS) { NovelsScreen(navController) }
        composable(Routes.MOVIES) { MoviesScreen(navController) }
        composable(Routes.GAMES) { GamesScreen(navController) }
        composable(Routes.BLOGS) { BlogsScreen(navController) }
        composable(Routes.FORUM) { ForumScreen(navController) }

        composable(
            Routes.COMIC_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
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
