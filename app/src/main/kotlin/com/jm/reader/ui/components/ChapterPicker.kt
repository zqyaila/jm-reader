package com.jm.reader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jm.reader.data.history.ChapterReadState
import com.jm.reader.ui.LocalAppStrings

/**
 * One chapter as the picker and the detail page need to draw it.
 *
 * [readState] and [page] come from the on-device progress store, [downloadedPages] from the
 * download manifest; keeping them on one row is what lets the reader mark a chapter as read and
 * as available offline in the same line.
 */
data class ChapterEntry(
    val id: String,
    val sort: Int,
    val name: String,
    val totalPages: Int,
    val readState: ChapterReadState = ChapterReadState.UNREAD,
    val page: Int = 0,
    val downloadedPages: Int = 0,
) {
    val isDownloaded: Boolean get() = downloadedPages > 0
}

/** Label for the read state, or null when there is nothing to say (an untouched chapter). */
fun chapterStateLabel(entry: ChapterEntry, unread: String, reading: String, read: String): String =
    when (entry.readState) {
        ChapterReadState.READ -> read
        ChapterReadState.READING ->
            if (entry.totalPages > 0) "$reading ${entry.page}/${entry.totalPages}" else reading
        ChapterReadState.UNREAD -> unread
    }

/**
 * The chapter list, shared by the online and the offline reader.
 *
 * Rows show what actually matters when picking where to continue in a 400-chapter serial: the
 * episode number, the name, whether it was read, and whether it is on disk.
 */
@Composable
fun ChapterPickerDialog(
    entries: List<ChapterEntry>,
    currentId: String,
    onSelect: (ChapterEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppStrings.current
    val chapterUi = s.chapterUi
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    s.reader.chooseChapter,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(12.dp),
                )
                if (entries.isEmpty()) {
                    Text(
                        s.reader.singleFile,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().size(400.dp)) {
                        itemsIndexed(entries, key = { i, c -> "$i-${c.id}" }) { _, chapter ->
                            val current = chapter.id == currentId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(chapter) }
                                    .padding(horizontal = 16.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    chapter.sort.toString().padStart(3, '0'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 10.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        chapter.name.ifBlank { s.chapterFmt.format(chapter.sort) },
                                        color = if (current) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        chapterStateLabel(
                                            chapter,
                                            chapterUi.unread,
                                            chapterUi.reading,
                                            chapterUi.read,
                                        ),
                                        fontSize = 10.5.sp,
                                        color = if (chapter.readState == ChapterReadState.READING) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                if (chapter.isDownloaded) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = s.downloaded,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                                if (current) {
                                    Text(
                                        s.reader.current,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(s.close) }
            }
        }
    }
}
