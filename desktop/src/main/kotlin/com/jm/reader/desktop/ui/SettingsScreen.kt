package com.jm.reader.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.ImageLoader
import com.jm.reader.desktop.core.UiLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings: language, appearance, connection diagnostics, cache, and the build's legal notice.
 *
 * The API host and version are shown deliberately — when a user reports "nothing loads", the host
 * line is the first thing that answers whether discovery picked a real API server or fell back.
 */
@Composable
fun SettingsScreen(dark: Boolean, onToggleDark: () -> Unit) {
    val s = LocalAppStrings.current
    val session = LocalSession.current
    val languageManager = LocalLanguageManager.current
    val language by languageManager.language.collectAsState()
    val scope = rememberCoroutineScope()

    var notice by remember { mutableStateOf<String?>(null) }

    fun flash(message: String) {
        scope.launch {
            notice = message
            delay(1800)
            notice = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = s.settings)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSizes.ContentPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(title = s.language) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UiLanguage.entries.forEach { lang ->
                        val active = lang == language
                        if (active) {
                            Button(onClick = { languageManager.set(lang) }) {
                                Text(lang.displayName)
                            }
                        } else {
                            OutlinedButton(onClick = { languageManager.set(lang) }) {
                                Text(lang.displayName)
                            }
                        }
                    }
                }
            }

            SettingsCard(title = s.darkTheme) {
                Switch(checked = dark, onCheckedChange = { onToggleDark() })
            }

            SettingsCard(title = s.apiHost) {
                Column {
                    ValueLine(s.apiHost, session.apiUrl.orEmpty().ifBlank { "—" })
                    ValueLine(s.appVersion, session.appVersion)
                }
            }

            SettingsCard(title = s.about) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        s.disclaimer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                ImageLoader.clear()
                                flash(s.imageCacheCleared)
                            }
                        }) {
                            Text(s.clearImageCache)
                        }
                        OutlinedButton(onClick = {
                            session.clearAuth()
                            flash(s.sessionCleared)
                        }) {
                            Text(s.resetSession)
                        }
                    }
                    if (notice != null) {
                        Text(
                            notice.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .glassSurface(shape = GlassShapeLarge, elevation = 8.dp)
            .padding(AppSizes.ContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun ValueLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.widthIn(min = 96.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
