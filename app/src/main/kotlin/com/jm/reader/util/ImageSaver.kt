package com.jm.reader.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Writes a reader page into the device gallery.
 *
 * On API 29+ this goes through MediaStore with `RELATIVE_PATH`, so no storage permission is needed
 * and the file lands in `Pictures/JMReader`. Below 29 the public Pictures directory still needs
 * `WRITE_EXTERNAL_STORAGE`; the caller already asks for it before downloading, and a denied write
 * simply returns `false` instead of crashing.
 *
 * Deliberately not routed through the download manager: a long-press is a one-off "keep this
 * panel", not a chapter download, and should not appear in the offline list.
 */
object ImageSaver {

    fun save(context: Context, bitmap: Bitmap, baseName: String): Boolean = runCatching {
        val name = sanitize(baseName) + ".jpg"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/JMReader")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching false
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) return@runCatching false
            } ?: return@runCatching false
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "JMReader",
            )
            if (!dir.exists() && !dir.mkdirs()) return@runCatching false
            FileOutputStream(File(dir, name)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
        }
    }.getOrDefault(false)

    /** Keeps gallery file names readable but filesystem-safe. */
    private fun sanitize(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(96).ifBlank { "jm_page" }
}
