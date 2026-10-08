package com.jm.reader.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.RepoResult

/**
 * Startup screen: resolves a working API host before anything else can render.
 *
 * Host discovery is a real network operation (fetch the encrypted config, probe each candidate
 * with `GET /setting`), so it gets its own screen rather than a silent delay — and a failure here
 * is reported with a retry instead of an empty home page.
 */
@Composable
fun SplashScreen(container: AppContainer, onReady: () -> Unit) {
    val s = LocalAppStrings.current
    val repo = LocalRepository.current
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        when (val r = repo.bootstrap()) {
            is RepoResult.Ok -> onReady()
            is RepoResult.Err -> error = r.message
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .glassSurface(shape = RoundedCornerShape(26.dp), elevation = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }

            Text(
                s.appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )

            if (error == null) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.padding(top = 24.dp).size(26.dp),
                )
                Text(
                    s.loading,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 20.dp, start = 48.dp, end = 48.dp),
                )
                Button(
                    onClick = { attempt++ },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(s.retry)
                }
            }
        }
    }
}
