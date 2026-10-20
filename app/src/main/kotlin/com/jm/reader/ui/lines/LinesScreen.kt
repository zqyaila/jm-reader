package com.jm.reader.ui.lines

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jm.reader.data.net.HostManager
import com.jm.reader.data.update.UpdateManager
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalHostManager
import com.jm.reader.ui.LocalUpdateManager
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.SectionHeader
import kotlinx.coroutines.launch

/**
 * Lines: which API host and which GitHub transport this device should use.
 *
 * Both lists are measured for real on entry - one `GET /setting` per host, one small API request
 * per update source - because "it feels slow" is not something a hard-coded order can fix: which
 * host is fastest depends on the carrier, the province, and the hour.
 *
 * Picking a row applies immediately, not on the next launch.
 */
@Composable
fun LinesScreen(navController: NavHostController) {
    val hosts = LocalHostManager.current
    val updates = LocalUpdateManager.current
    val s = LocalAppStrings.current
    val scope = rememberCoroutineScope()

    var apiLines by remember { mutableStateOf<List<HostManager.Line>?>(null) }
    var sources by remember { mutableStateOf<List<Pair<UpdateManager.Source, Long?>>?>(null) }
    var testing by remember { mutableStateOf(true) }
    var pickedHost by remember { mutableStateOf(hosts.preferredHost) }
    var pickedSource by remember { mutableStateOf(updates.preferredSourceId) }

    suspend fun runTests() {
        testing = true
        apiLines = hosts.measureAll()
        sources = updates.measureSources()
        testing = false
    }

    LaunchedEffect(Unit) { runTests() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { AppTopBar(s.linesTitle, onBack = { navController.popBackStack() }) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        ) {
            item {
                SectionHeader(s.linesApi)
                Hint(s.linesHint)
            }
            item {
                LineRow(
                    label = s.linesAuto,
                    detail = null,
                    testing = testing,
                    timeoutLabel = s.linesTimeout,
                    active = pickedHost.isBlank(),
                ) {
                    hosts.use(null)
                    pickedHost = ""
                }
            }
            items(apiLines.orEmpty(), key = { it.host }) { line ->
                LineRow(
                    label = line.host,
                    detail = line.ms,
                    testing = false,
                    timeoutLabel = s.linesTimeout,
                    active = pickedHost == line.host,
                ) {
                    hosts.use(line.host)
                    pickedHost = line.host
                }
            }

            item {
                SectionHeader(s.linesUpdate, modifier = Modifier.padding(top = 22.dp))
                Hint(s.linesUpdateHint)
            }
            item {
                LineRow(
                    label = s.linesAuto,
                    detail = null,
                    testing = testing,
                    timeoutLabel = s.linesTimeout,
                    active = pickedSource == UpdateManager.AUTO_ID,
                ) {
                    updates.preferredSourceId = UpdateManager.AUTO_ID
                    pickedSource = UpdateManager.AUTO_ID
                }
            }
            items(sources.orEmpty(), key = { it.first.id }) { (source, ms) ->
                LineRow(
                    label = source.label,
                    detail = ms,
                    testing = false,
                    timeoutLabel = s.linesTimeout,
                    active = pickedSource == source.id,
                ) {
                    updates.preferredSourceId = source.id
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
                        fontSize = 13.sp,
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
private fun Hint(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
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
    val shape = MaterialTheme.shapes.small
    Row(
        Modifier
            .fillMaxWidth()
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
            .height(46.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
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
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = when {
                testing -> MaterialTheme.colorScheme.onSurfaceVariant
                detail == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
