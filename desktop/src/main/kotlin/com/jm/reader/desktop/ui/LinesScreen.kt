package com.jm.reader.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jm.reader.desktop.core.HostManager
import com.jm.reader.desktop.core.UpdateSources
import kotlinx.coroutines.launch

/**
 * Lines: which API host and which GitHub transport to use.
 *
 * Both tables are measured for real on entry. The desktop startup used to spend its time on a CDN
 * fetch plus a serial probe, so "which host is fast today" was never visible - now it is a number
 * the reader can act on, and the pick is applied immediately.
 */
@Composable
fun LinesScreen(onBack: () -> Unit) {
    val hosts = LocalHostManager.current
    val updates = LocalUpdateSources.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var apiLines by remember { mutableStateOf<List<HostManager.Line>?>(null) }
    var sources by remember { mutableStateOf<List<Pair<UpdateSources.Source, Long?>>?>(null) }
    var testing by remember { mutableStateOf(true) }
    var pickedHost by remember { mutableStateOf(hosts.preferredHost) }
    var pickedSource by remember { mutableStateOf(updates.preferredId) }

    suspend fun runTests() {
        testing = true
        apiLines = hosts.measureAll()
        sources = updates.measure()
        testing = false
    }

    LaunchedEffect(Unit) { runTests() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = s.linesTitle, onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppSizes.ContentPadding,
                end = AppSizes.ContentPadding,
                top = 8.dp,
                bottom = 32.dp,
            ),
        ) {
            item {
                SectionHeader(s.linesApi, modifier = Modifier.padding(bottom = 2.dp))
                Text(
                    s.linesHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                LineRow(s.linesAuto, null, testing, s.linesTimeout, pickedHost.isBlank()) {
                    hosts.use(null)
                    pickedHost = ""
                }
            }
            items(apiLines.orEmpty(), key = { it.host }) { line ->
                LineRow(line.host, line.ms, false, s.linesTimeout, pickedHost == line.host) {
                    hosts.use(line.host)
                    pickedHost = line.host
                }
            }

            item {
                SectionHeader(s.linesUpdate, modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
                Text(
                    s.linesUpdateHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item {
                LineRow(s.linesAuto, null, testing, s.linesTimeout, pickedSource == UpdateSources.AUTO_ID) {
                    updates.preferredId = UpdateSources.AUTO_ID
                    pickedSource = UpdateSources.AUTO_ID
                }
            }
            items(sources.orEmpty(), key = { it.first.id }) { (source, ms) ->
                LineRow(source.label, ms, false, s.linesTimeout, pickedSource == source.id) {
                    updates.preferredId = source.id
                    pickedSource = source.id
                }
            }

            item {
                TextButton(
                    onClick = { scope.launch { runTests() } },
                    enabled = !testing,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Text(
                        if (testing) s.linesTesting else s.linesRetest,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testing) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LineRow(
    label: String,
    detail: Long?,
    testing: Boolean,
    timeoutLabel: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick)
            .height(44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                testing -> "…"
                detail != null -> "$detail ms"
                else -> timeoutLabel
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = when {
                testing || detail == null -> MaterialTheme.colorScheme.onSurfaceVariant
                detail < 400 -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (active) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
