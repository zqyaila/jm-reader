package com.jm.reader.data.download

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.util.ImageDescrambler
import com.jm.reader.data.model.ReadPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One-tap comic downloader.
 *
 * Downloads every page of every chapter of an album, restores JMComic's scrambled images,
 * and writes them to the user's Downloads/JMReader/<albumId>/ folder (MediaStore on
 * API 29+, public downloads dir on older APIs). A small in-app index persists which albums
 * were downloaded. Downloads run on the IO dispatcher, admit only one job per album, are
 * cancellable via [deleteAlbum], and never mark an album complete if any page failed.
 *
 * Pages are fetched [PAGE_PARALLELISM] at a time rather than one by one - the image CDN is the
 * bottleneck, so overlapping requests is what makes a big album finish quickly.
 *
 * **Why the files are named `…jpg.jm` and not `…jpg`:** a JPEG sitting in Downloads is picked up
 * by the system media scanner and turns up in every gallery app, which buries the user's real
 * photos under hundreds of comic pages. Giving the file an extension the scanner does not
 * recognise as an image (and inserting it with [GENERIC_MIME] rather than `image/jpeg`) keeps it
 * out of the gallery while leaving it a completely ordinary JPEG that this app still reads.
 * Files downloaded by older versions keep their plain `.jpg` name and are still listed - see
 * [isPageFile].
 */
class DownloadManager(
    private val context: Context,
    private val repository: AppRepository,
) {

    data class DownloadedAlbum(
        val albumId: String,
        val name: String,
        val chapterCount: Int,
        val pageCount: Int,
        val timestamp: Long,
    )

    data class Progress(
        val albumId: String,
        val current: Int,
        val total: Int,
        val phase: String, // "downloading" | "done" | "failed"
        val error: String? = null,
    )

    private val prefs = context.applicationContext.getSharedPreferences("jm_downloads", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _downloading = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val downloading: StateFlow<Map<String, Progress>> = _downloading.asStateFlow()

    private val _albums = MutableStateFlow(loadIndex())
    val albums: StateFlow<List<DownloadedAlbum>> = _albums.asStateFlow()

    private val downloadMutex = Mutex()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()

    /** "Download/JMReader/<albumId>/" - MediaStore stores RELATIVE_PATH with a trailing slash. */
    private fun selectionPath(albumId: String): String = "Download/JMReader/$albumId/"

    fun relativePath(albumId: String): String = selectionPath(albumId)

    fun isDownloaded(albumId: String): Boolean = _albums.value.any { it.albumId == albumId }

    fun getAlbum(albumId: String): DownloadedAlbum? = _albums.value.firstOrNull { it.albumId == albumId }

    fun isIdle(albumId: String): Boolean = _downloading.value[albumId]?.phase != "downloading"

    suspend fun downloadAlbum(albumId: String): Boolean = withContext(Dispatchers.IO) {
        val admitted = downloadMutex.withLock {
            if (_downloading.value[albumId]?.phase == "downloading") {
                false
            } else {
                cancelFlags.remove(albumId)
                _downloading.update { it + (albumId to Progress(albumId, 0, 1, "downloading")) }
                true
            }
        }
        if (!admitted) return@withContext false

        var ok = false
        try {
            val detail = when (val r = repository.getAlbum(albumId)) {
                is RepoResult.Ok -> r.data
                is RepoResult.Err -> throw IllegalStateException(r.message)
            }
            val chapterIds = if (detail.series.isNotEmpty()) {
                detail.series.sortedBy { it.sort }.map { it.id }
            } else {
                listOf(albumId)
            }

            // Per-chapter read payloads: (chapterId, scrambleId, pages). Fetched concurrently -
            // a 40-chapter album would otherwise make 40 sequential round trips before the first
            // page is even requested.
            val chapters: List<Triple<String, Long, List<ReadPage>>> = coroutineScope {
                chapterIds.map { cid ->
                    async {
                        when (val r = repository.comicRead(cid)) {
                            is RepoResult.Ok -> Triple(r.data.id, r.data.scrambleId, r.data.images)
                            is RepoResult.Err -> throw IllegalStateException(r.message)
                        }
                    }
                }.awaitAll()
            }
            val totalPages = chapters.sumOf { it.third.size }
            if (totalPages == 0) throw IllegalStateException("no pages")

            // One task per page. The file name carries the chapter and page number, so the order
            // the tasks happen to finish in has no effect on what lands on disk.
            val tasks = chapters.flatMapIndexed { ci, ch ->
                ch.third.map { page ->
                    PageTask(
                        name = "%02d_%03d".format(ci + 1, page.page),
                        url = page.image,
                        aid = ch.first.toLongOrNull() ?: 0L,
                        scrambleId = ch.second,
                    )
                }
            }

            val completed = AtomicInteger(0)
            val outcomes: List<Boolean> = coroutineScope {
                val gate = Semaphore(PAGE_PARALLELISM)
                tasks.map { task ->
                    async {
                        gate.withPermit {
                            var ok = false
                            var attempt = 0
                            // A retry matters more here than before: pages now contend for
                            // bandwidth, so a transient failure is likelier.
                            while (!ok && attempt < PAGE_ATTEMPTS && cancelFlags[albumId] != true) {
                                attempt++
                                ok = savePage(albumId, task.name, task.url, task.aid, task.scrambleId)
                            }
                            val n = completed.incrementAndGet()
                            _downloading.update {
                                it + (albumId to Progress(albumId, n, totalPages, "downloading"))
                            }
                            ok
                        }
                    }
                }.awaitAll()
            }
            val failed = outcomes.any { !it } || cancelFlags[albumId] == true

            cancelFlags.remove(albumId)
            when {
                failed -> {
                    cleanupAlbum(albumId)
                    _downloading.update { it + (albumId to Progress(albumId, 0, 1, "failed", "部分頁面下載失敗")) }
                }
                else -> {
                    updateIndex(
                        DownloadedAlbum(
                            albumId = albumId,
                            name = detail.name.ifBlank { "JM$albumId" },
                            chapterCount = chapterIds.size,
                            pageCount = totalPages,
                            timestamp = System.currentTimeMillis(),
                        )
                    )
                    _downloading.update { it + (albumId to Progress(albumId, totalPages, totalPages, "done")) }
                    ok = true
                }
            }
        } catch (e: Exception) {
            // Capture the flag before clearing it: a cancelled job can leave half an album on
            // disk, and the index never marked it complete, so the files go too.
            val cancelled = cancelFlags.remove(albumId) == true
            if (cancelled) cleanupAlbum(albumId)
            _downloading.update { it + (albumId to Progress(albumId, 0, 1, "failed", e.message)) }
        }
        ok
    }

    /** Downloads one page, de-scrambles it (if the album requires it) and writes a JPEG. */
    private fun savePage(albumId: String, name: String, url: String, aid: Long, scrambleId: Long): Boolean {
        val bytes = try {
            http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
        } catch (_: Exception) {
            null
        } ?: return false

        val src = runCatching { decodeSampled(bytes, MAX_SAVE_DIM) }.getOrNull() ?: return false

        val out = if (ImageDescrambler.needsDescramble(aid, scrambleId, url)) {
            val num = ImageDescrambler.sliceCount(aid, fileName(url))
            val d = ImageDescrambler.descramble(src, num)
            if (d !== src) src.recycle()
            d
        } else {
            src
        }

        return writeJpeg(albumId, name, out)
    }

    private fun writeJpeg(albumId: String, name: String, bmp: Bitmap): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                // Idempotent: remove any previous file with the same name before inserting.
                runCatching {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(selectionPath(albumId), downloadPageName(name)),
                    )
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, downloadPageName(name))
                    // Deliberately NOT "image/jpeg": MediaStore files anything with an image MIME
                    // as a picture, which is what made downloads show up in the gallery.
                    put(MediaStore.MediaColumns.MIME_TYPE, PAGE_MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, selectionPath(albumId))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                try {
                    val stream = resolver.openOutputStream(uri)
                    if (stream == null) {
                        resolver.delete(uri, null, null)
                        return false
                    }
                    stream.use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                    true
                } catch (_: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    false
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, downloadPageName(name))
                FileOutputStream(f).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Lists the local image Uris of a downloaded album, sorted by chapter/page name. */
    fun albumImageUris(albumId: String): List<Uri> {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
                val selArgs = arrayOf(selectionPath(albumId))
                val uris = mutableListOf<Pair<String, Uri>>()
                resolver.query(collection, projection, selection, selArgs, null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val name = c.getString(nameCol) ?: ""
                        // Only page files, so anything else that lands in the folder is ignored.
                        if (!isDownloadedPageFile(name)) continue
                        val id = c.getLong(idCol)
                        uris.add(name to Uri.withAppendedPath(collection, id.toString()))
                    }
                }
                uris.sortedBy { it.first }.map { it.second }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (!dir.exists()) return emptyList()
                dir.listFiles()
                    ?.filter { it.isFile && isDownloadedPageFile(it.name) }
                    ?.sortedBy { it.name }
                    ?.map { Uri.fromFile(it) }
                    ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun deleteAlbum(albumId: String) {
        cancelFlags[albumId] = true
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val resolver = context.contentResolver
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                        arrayOf(selectionPath(albumId)),
                    )
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "JMReader/$albumId",
                    )
                    if (dir.exists()) runCatching { dir.delete() }
                } else {
                    val dir = File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "JMReader/$albumId",
                    )
                    dir.listFiles()?.forEach { it.delete() }
                    dir.delete()
                }
            } catch (_: Exception) {
            }
            removeIndex(albumId)
        }
    }

    /** Removes all downloaded files for an album (used after a cancelled/partial download). */
    private fun cleanupAlbum(albumId: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(selectionPath(albumId)),
                )
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (dir.exists()) runCatching { dir.delete() }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        } catch (_: Exception) {
        }
        removeIndex(albumId)
    }

    /**
     * One-off re-filing of albums downloaded before pages were named `…jpg.jm`.
     *
     * Those files are plain `.jpg` and so still fill the gallery. Rather than making the reader
     * delete and re-download the lot, the existing entries are renamed in place: on API 29+
     * MediaProvider performs a `DISPLAY_NAME`/`MIME_TYPE` update as a rename of the underlying
     * file, so no image data is rewritten. Album folders downloaded on older APIs are renamed
     * directly.
     *
     * Runs at most once, and is entirely best-effort: anything that fails keeps its old name,
     * which [isDownloadedPageFile] still accepts, so the offline reader is unaffected either way.
     */
    suspend fun migrateLegacyDownloads() = withContext(Dispatchers.IO) {
        if (prefs.getBoolean(KEY_LEGACY_MIGRATED, false)) return@withContext
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                )
                val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? " +
                    "AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
                // Only this app's own folder, and only names that still end in .jpg.
                val args = arrayOf("Download/JMReader/%", "%$PAGE_IMAGE_EXT")
                resolver.query(collection, projection, selection, args, null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (c.moveToNext()) {
                        val name = c.getString(nameCol) ?: continue
                        val uri = Uri.withAppendedPath(collection, c.getLong(idCol).toString())
                        runCatching {
                            resolver.update(
                                uri,
                                ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, downloadPageName(name.removeSuffix(PAGE_IMAGE_EXT)))
                                    put(MediaStore.MediaColumns.MIME_TYPE, PAGE_MIME)
                                },
                                null,
                                null,
                            )
                        }
                    }
                }
            } else {
                val root = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader",
                )
                root.listFiles()?.forEach { albumDir ->
                    albumDir.listFiles()
                        ?.filter { it.isFile && it.name.endsWith(PAGE_IMAGE_EXT) }
                        ?.forEach { f ->
                            runCatching {
                                f.renameTo(File(f.parentFile, downloadPageName(f.name.removeSuffix(PAGE_IMAGE_EXT))))
                            }
                        }
                }
            }
        }.isSuccess
        // Only latch on success, so a transient failure is retried on the next launch.
        if (ok) prefs.edit().putBoolean(KEY_LEGACY_MIGRATED, true).apply()
    }

    // -----------------------------------------------------------------------
    // Index persistence
    // -----------------------------------------------------------------------

    private fun loadIndex(): List<DownloadedAlbum> {
        val raw = prefs.getString("albums", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                DownloadedAlbum(
                    albumId = o.optString("albumId"),
                    name = o.optString("name"),
                    chapterCount = o.optInt("chapterCount"),
                    pageCount = o.optInt("pageCount"),
                    timestamp = o.optLong("timestamp"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persistIndex() {
        val arr = JSONArray()
        _albums.value.forEach { a ->
            arr.put(
                JSONObject()
                    .put("albumId", a.albumId)
                    .put("name", a.name)
                    .put("chapterCount", a.chapterCount)
                    .put("pageCount", a.pageCount)
                    .put("timestamp", a.timestamp)
            )
        }
        // commit() is synchronous and durable; we run on the IO dispatcher.
        prefs.edit().putString("albums", arr.toString()).commit()
    }

    private fun updateIndex(album: DownloadedAlbum) {
        _albums.update { it.filter { a -> a.albumId != album.albumId } + album }
        persistIndex()
    }

    private fun removeIndex(albumId: String) {
        _albums.update { it.filter { a -> a.albumId != albumId } }
        persistIndex()
    }

    /** Decodes with an inSampleSize bounding the largest dimension (prevents OOM on huge pages). */
    private fun decodeSampled(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        return BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun fileName(url: String): String {
        val path = url.substringBefore('?')
        val seg = path.substringAfterLast('/')
        return seg.substringBeforeLast('.').ifBlank { seg }
    }

    private companion object {
        const val MAX_SAVE_DIM = 2560

        /**
         * Pages fetched at once. Kept deliberately small: each in-flight page holds a decoded
         * bitmap (up to ~26 MB for a 2560x2560 page), and descrambling briefly holds two, so a
         * higher number buys little throughput while risking an OOM on a modest device.
         */
        const val PAGE_PARALLELISM = 3

        /** Attempts per page (1 try + 1 retry) before the album is declared failed. */
        const val PAGE_ATTEMPTS = 2

        const val KEY_LEGACY_MIGRATED = "legacyNamingMigrated"
    }
}

/** Extension of the JPEG stored on disk. */
internal const val PAGE_IMAGE_EXT = ".jpg"

/**
 * Marker appended after the image extension, e.g. `01_001.jpg.jm`.
 *
 * The media scanner classifies a file by its extension, so a name that no longer *ends* in an
 * image extension is not indexed as a picture - which is how these stay out of the gallery.
 */
internal const val GALLERY_HIDDEN_SUFFIX = ".jm"

/** MIME the pages are registered under, so MediaStore does not file them as pictures. */
internal const val PAGE_MIME = "application/octet-stream"

/** On-disk name of one page, e.g. `01_001` -> `01_001.jpg.jm`. */
internal fun downloadPageName(base: String): String = "$base$PAGE_IMAGE_EXT$GALLERY_HIDDEN_SUFFIX"

/**
 * True for a page this app wrote.
 *
 * Both the current hidden name and the plain `.jpg` written by earlier versions count, so albums
 * downloaded before the rename still open instead of reading as empty.
 */
internal fun isDownloadedPageFile(fileName: String): Boolean =
    fileName.endsWith(GALLERY_HIDDEN_SUFFIX) || fileName.endsWith(PAGE_IMAGE_EXT)

/** One page to fetch: its output name and everything [DownloadManager.savePage] needs. */
private data class PageTask(
    val name: String,
    val url: String,
    val aid: Long,
    val scrambleId: Long,
)
