package com.jm.reader.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jm.reader.desktop.core.Guess
import com.jm.reader.desktop.core.Verdict

/**
 * Local reading stats, the desktop twin of the phone screen: works read, time read, pages read,
 * the tag mix and "most read". Everything comes from [com.jm.reader.desktop.core.ReadingStats];
 * no server call, so it works offline.
 */
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val stats = LocalStats.current
    val s = LocalAppStrings.current
    val snapshot by stats.snapshot.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = s.statsTitle, onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSizes.ContentPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NumberCard(snapshot.albumCount.toString(), s.statsAlbums, Modifier.weight(1f))
                NumberCard(
                    formatDuration(snapshot.totalMillis, s.hoursMinutesFmt, s.minutesOnlyFmt),
                    s.statsDuration,
                    Modifier.weight(1.3f),
                    valueSize = 16.sp,
                )
                NumberCard(
                    "${(snapshot.averageCompletion * 100).toInt()}%",
                    s.statsCompletion,
                    Modifier.weight(1f),
                )
            }

            if (!snapshot.hasData) {
                Text(
                    s.statsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 28.dp),
                )
            } else {
                if (snapshot.tagCounts.isNotEmpty()) {
                    SectionHeader(title = s.statsTagShare, modifier = Modifier.padding(top = 10.dp))
                    val tags = snapshot.rankedTags().take(10)
                    val max = tags.maxOfOrNull { it.value.weight } ?: 1
                    Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                        tags.forEach { entry ->
                            TagShareRow(
                                tag = entry.key,
                                meta = buildString {
                                    append(s.completionFmt.format((entry.value.completion * 100).toInt()))
                                    if (entry.value.millis >= 60_000L) {
                                        append(" · ")
                                        append(
                                            formatDuration(
                                                entry.value.millis,
                                                s.hoursMinutesFmt,
                                                s.minutesOnlyFmt,
                                            ),
                                        )
                                    }
                                },
                                share = Guess.share(entry.value.weight, snapshot.tagTotal),
                                fractionOfTop = entry.value.weight.toFloat() / max.coerceAtLeast(1),
                            )
                        }
                    }
                }

                if (snapshot.topWorks.isNotEmpty()) {
                    SectionHeader(title = s.statsTopWorks, modifier = Modifier.padding(top = 10.dp))
                    Column(Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                        snapshot.topWorks.forEach { work ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    work.name.ifBlank { "#${work.albumId}" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    buildString {
                                        append(s.completionFmt.format((work.completion * 100).toInt()))
                                        if (work.millis >= 60_000L) {
                                            append(" · ")
                                            append(formatDuration(work.millis, s.hoursMinutesFmt, s.minutesOnlyFmt))
                                        }
                                        work.verdict?.let { verdict ->
                                            append(" · ")
                                            append(
                                                when (verdict) {
                                                    Verdict.LIKED -> s.verdictLiked
                                                    Verdict.DISLIKED -> s.verdictDisliked
                                                    Verdict.MEH -> s.verdictMeh
                                                },
                                            )
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            TextButton(onClick = { confirmReset = true }, modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    s.statsReset,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(s.statsReset) },
            text = { Text(s.statsResetConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    stats.clear()
                    confirmReset = false
                }) { Text(s.clear) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun NumberCard(value: String, label: String, modifier: Modifier = Modifier, valueSize: androidx.compose.ui.unit.TextUnit = 20.sp) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            value,
            fontSize = valueSize,
            lineHeight = valueSize * 1.2f,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
private fun TagShareRow(tag: String, meta: String, share: Float, fractionOfTop: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#$tag",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$meta · ${(share * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 7.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fractionOfTop.coerceIn(0.04f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (fractionOfTop > 0.75f) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    ),
            )
        }
    }
}

internal fun formatDuration(millis: Long, hoursFmt: String, minutesFmt: String): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) hoursFmt.format(hours, minutes) else minutesFmt.format(minutes)
}
