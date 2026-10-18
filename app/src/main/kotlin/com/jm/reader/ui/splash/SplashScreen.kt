package com.jm.reader.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jm.reader.JMApplication
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalRepository
import com.jm.reader.ui.nav.Routes
import com.jm.reader.ui.theme.BrandOrange
import kotlinx.coroutines.launch

private sealed class BootState {
    object Loading : BootState()
    object Ready : BootState()
    data class Error(val message: String) : BootState()
}

@Composable
fun SplashScreen(navController: NavHostController) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    var state by remember { mutableStateOf<BootState>(BootState.Loading) }
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(repo, retryKey) {
        state = BootState.Loading
        val app = context.applicationContext as JMApplication

        // Warm start: the last verified host is already persisted, so do not make the reader stare
        // at a spinner while a network probe re-confirms it. Enter the app now and re-validate in
        // the background; if the host moved, the next request retries against the new one.
        //
        // This is the difference between "opens in 200 ms" and "opens when the network feels like
        // it" - a cold launch previously waited for a CDN config fetch, a serial host probe and a
        // settings round-trip before the first screen appeared.
        if (!repo.cachedApiUrl.isNullOrBlank() && retryKey == 0) {
            app.appScope.launch { runCatching { repo.bootstrap() } }
            // Automatic daily check-in: a no-op when nobody is logged in or today is already
            // signed. Fire-and-forget so it can never hold up startup.
            app.appScope.launch { runCatching { repo.autoDailyCheck() } }
            // Re-file albums downloaded before pages were renamed to keep them out of the gallery.
            app.appScope.launch { runCatching { app.downloadManager.migrateLegacyDownloads() } }
            state = BootState.Ready
            return@LaunchedEffect
        }

        state = when (val r = repo.bootstrap()) {
            is RepoResult.Ok -> {
                // Daily check-in and download migration run on the application scope rather than
                // being awaited here: the splash is popped the instant it navigates, which would
                // cancel a screen-scoped coroutine mid-request.
                app.appScope.launch { runCatching { repo.autoDailyCheck() } }
                app.appScope.launch { runCatching { app.downloadManager.migrateLegacyDownloads() } }
                BootState.Ready
            }
            is RepoResult.Err -> BootState.Error(r.message)
        }
    }

    val strs = LocalAppStrings.current
    when (val st = state) {
        BootState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strs.appName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                CircularProgressIndicator(
                    color = BrandOrange,
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
        BootState.Ready -> LaunchedEffect(Unit) {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        }
        is BootState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strs.bootFail, fontWeight = FontWeight.Bold)
                Text(st.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = { retryKey++ },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text(strs.retry) }
            }
        }
    }
}
