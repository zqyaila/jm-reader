package com.jm.reader.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import com.jm.reader.data.update.UpdateManager
import com.jm.reader.ui.LocalAppStrings
import com.jm.reader.ui.LocalUpdateManager
import kotlinx.coroutines.launch

/**
 * "Check for updates" dialog.
 *
 * States are explicit because the flow crosses a network check, an optional multi-minute download
 * and a hand-off to the system installer - a single boolean would leave the user staring at a
 * spinner with no idea which of those is happening.
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val s = LocalAppStrings.current
    val manager = LocalUpdateManager.current
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    // Total bytes, taken from the release first and then corrected by the response itself; the
    // dialog used to print the metadata figure forever, even when the server sent another size.
    var totalBytes by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf<String?>(null) }
    var upToDate by remember { mutableStateOf(false) }
    var installBlocked by remember { mutableStateOf(false) }
    // Kept so "grant permission, come back, tap retry" does not download 19 MB again.
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    suspend fun check() {
        checking = true
        error = null
        upToDate = false
        when (val r = manager.check()) {
            is UpdateManager.CheckResult.Available -> {
                info = r.info
                totalBytes = r.info.sizeBytes
            }
            UpdateManager.CheckResult.Latest -> upToDate = true
            is UpdateManager.CheckResult.Failed -> error = s.updateFailedFmt.format(r.message)
        }
        checking = false
    }

    LaunchedEffect(Unit) { check() }

    fun startDownload() {
        val target = info ?: return
        downloading = true
        progress = 0f
        error = null
        scope.launch {
            when (val r = manager.download(target, onProgress = { progress = it }, onTotal = { totalBytes = it })) {
                is UpdateManager.DownloadResult.Done -> {
                    downloading = false
                    downloadedFile = r.file
                    if (manager.install(r.file)) {
                        onDismiss()
                    } else {
                        installBlocked = true
                    }
                }
                is UpdateManager.DownloadResult.Failed -> {
                    downloading = false
                    error = s.updateFailedFmt.format(r.message)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                when {
                    downloading -> s.updateDownloadingFmt.format((progress * 100).toInt().coerceIn(0, 100))
                    upToDate -> s.updateLatest
                    info != null -> s.updateAvailable
                    else -> s.updateCheck
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    checking -> Text(s.updateChecking, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    downloading -> {
                        val total = if (totalBytes > 0L) totalBytes else (info?.sizeBytes ?: 0L)
                        Text(
                            s.updateSizeFmt.format(formatSize(total)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            CircularProgressIndicator(Modifier.padding(4.dp))
                        }
                        Text(
                            s.updateDownloadingFmt.format((progress * 100).toInt().coerceIn(0, 100)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (total > 0L && progress >= 0f) {
                            Text(
                                s.updateProgressFmt.format(
                                    formatSize((total * progress).toLong()),
                                    formatSize(total),
                                ),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    installBlocked -> Text(s.updateInstallBlocked, fontSize = 13.sp)

                    error != null -> Text(error!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)

                    upToDate -> Text(s.updateLatest, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    info != null -> {
                        Text(
                            s.updateSizeFmt.format(formatSize(totalBytes.takeIf { it > 0 } ?: info!!.sizeBytes)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (info!!.notes.isNotBlank()) {
                            Text(
                                s.updateNotes,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Text(
                                info!!.notes,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                info != null && !downloading && !installBlocked -> {
                    TextButton(onClick = { startDownload() }) { Text(s.updateDownload) }
                }
                installBlocked && downloadedFile != null -> {
                    TextButton(onClick = {
                        val file = downloadedFile ?: return@TextButton
                        if (manager.install(file)) onDismiss()
                    }) { Text(s.retry) }
                }
                error != null -> {
                    TextButton(onClick = { scope.launch { check() } }) { Text(s.retry) }
                }
                else -> Unit
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        },
    )
}

/** 19595232 -> "18.7 MB". */
internal fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes >= 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format("%.0f KB", bytes / 1024.0)
}
