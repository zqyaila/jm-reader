package com.jm.reader.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jm.reader.data.stats.GuessEngine
import com.jm.reader.data.stats.ReadingStatsManager
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalStats
import com.jm.reader.ui.components.AppTopBar
import com.jm.reader.ui.components.SectionHeader

/**
 * Local reading stats: works read, time read, and the tag mix that powers "guess you like".
 *
 * Everything here is computed from [ReadingStatsManager] on the device; there is no server call,
 * so the screen also works offline and for readers who never logged in.
 */
@Composable
fun StatsScreen(navController: NavHostController) {
    val stats = LocalStats.current
    val s = LocalAppStrings.current
    val snapshot by stats.snapshot.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar(s.statsTitle, onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NumberCard(
                        value = snapshot.albumCount.toString(),
                        label = s.statsAlbums,
                        modifier = Modifier.weight(1f),
                    )
                    NumberCard(
                        value = formatDuration(snapshot.totalMillis, s.hoursMinutesFmt, s.minutesOnlyFmt),
                        label = s.statsDuration,
                        modifier = Modifier.weight(1.4f),
                        valueSize = 16.sp,
                    )
                    NumberCard(
                        value = "${(snapshot.averageCompletion * 100).toInt()}%",
                        label = s.statsCompletion,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!snapshot.hasData) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.statsEmpty,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (snapshot.tagCounts.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = s.statsTagShare,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                val tags = snapshot.rankedTags().take(10)
                val max = tags.maxOfOrNull { it.value.weight } ?: 1
                items(tags, key = { it.key }) { entry ->
                    TagShareRow(
                        tag = entry.key,
                        // "62% 通读 · 3 小时 20 分" - what the tag actually looks like in use,
                        // rather than a page count that says nothing about how much was read.
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
                        share = GuessEngine.share(entry.value.weight, snapshot.tagTotal),
                        fractionOfTop = entry.value.weight.toFloat() / max.coerceAtLeast(1),
                    )
                }
            }

            if (snapshot.topWorks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = s.statsTopWorks,
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                    )
                }
                items(snapshot.topWorks, key = { it.albumId }) { work ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            work.name.ifBlank { "#${work.albumId}" },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
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
                                            com.jm.reader.data.stats.Verdict.LIKED -> s.verdictLiked
                                            com.jm.reader.data.stats.Verdict.DISLIKED -> s.verdictDisliked
                                            com.jm.reader.data.stats.Verdict.MEH -> s.verdictMeh
                                        },
                                    )
                                }
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                TextButton(
                    onClick = { confirmReset = true },
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text(s.statsReset, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(s.statsReset) },
            text = { Text(s.statsResetConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    stats.clear()
                    confirmReset = false
                }) {
                    Text(s.clear)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(s.close) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun NumberCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueSize: TextUnit = 20.sp,
) {
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
            letterSpacing = (-0.3).sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/**
 * One tag row: name + share, with a bar whose length is relative to the heaviest tag.
 *
 * The bar is scaled to the top tag on purpose - shares of the whole profile are usually 5-20 %,
 * so plotting them literally would render ten nearly invisible stubs. The percentage next to the
 * name is the real, absolute share.
 */
@Composable
private fun TagShareRow(tag: String, meta: String, share: Float, fractionOfTop: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "#$tag",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$meta · ${(share * 100).toInt()}%",
                fontSize = 11.sp,
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
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (fractionOfTop > 0.75f) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    ),
            )
        }
    }
}

/** 3720000 ms -> "1 小时 2 分"; under a minute -> "0 分钟". */
internal fun formatDuration(millis: Long, hoursFmt: String, minutesFmt: String): String {
    val totalMinutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) hoursFmt.format(hours, minutes) else minutesFmt.format(minutes)
}
