package com.jm.reader.data.download

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jm.reader.data.model.ReadData
import com.jm.reader.data.model.ReadPage
import com.jm.reader.data.model.SeriesItem
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.repo.RepoResult
import com.jm.reader.util.ImageDescrambler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
 * Chapter-aware comic downloader.
 *
 * Pages are written into `Download/JMReader/<albumId>/<chapterId>/` (MediaStore on API 29+, the
 * public downloads dir on older APIs), de-scrambled on the way in, and listed in an
 * [AlbumManifest] kept in the app's private files directory. That layout is what makes a long
 * serial usable offline:
 *
 *  - a single chapter can be downloaded on its own - the reader no longer has to pull 400
 *    chapters to read today's release on the train;
 *  - the offline reader can show the same chapter list, with the same names and order, as the
 *    online one;
 *  - "已下载" is a per-chapter fact, so the detail page can show a tick next to the chapters that
 *    are on disk.
 *
 * Albums downloaded by earlier versions kept every page directly under the album folder with an
 * `01_001.jpg.jm` name and no manifest. Those are still readable as one loose chapter - see
 * [localPages] - because asking people to re-download everything is not a migration.
 *
 * Downloads run on the IO dispatcher, admit one job per album, are cancellable via [deleteAlbum],
 * and only ever record a chapter once every one of its pages landed.
 *
 * **Why the files are named `…jpg.jm` and not `…jpg`:** a JPEG sitting in Downloads is picked up
 * by the system media scanner and turns up in every gallery app, which buries the user's real
 * photos under hundreds of comic pages. Giving the file an extension the scanner does not
 * recognise as an image (and inserting it with [GENERIC_MIME] rather than `image/jpeg`) keeps it
 * out of the gallery while leaving it a completely ordinary JPEG that this app still reads.
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
        /** Chapter the counter currently belongs to, when the job is a single chapter. */
        val chapterId: String? = null,
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

    /** albumId -> chapterId -> pages on disk. Complete chapters only, plus in-flight ones. */
    private val _chapterPages = MutableStateFlow(loadChapterPages())
    val chapterPages: StateFlow<Map<String, Map<String, Int>>> = _chapterPages.asStateFlow()

    private val manifests = ConcurrentHashMap<String, AlbumManifest>()

    private val downloadMutex = Mutex()
    private val cancelFlags = ConcurrentHashMap<String, Boolean>()

    /** "Download/JMReader/<albumId>/" - MediaStore stores RELATIVE_PATH with a trailing slash. */
    private fun selectionPath(albumId: String): String = "Download/JMReader/$albumId/"

    /** Folder of one chapter: the album folder plus the chapter's own sub-folder. */
    private fun chapterPath(albumId: String, chapterId: String): String =
        selectionPath(albumId) + safeSegment(chapterId) + "/"

    fun relativePath(albumId: String): String = selectionPath(albumId)

    fun isDownloaded(albumId: String): Boolean = _albums.value.any { it.albumId == albumId }

    fun getAlbum(albumId: String): DownloadedAlbum? = _albums.value.firstOrNull { it.albumId == albumId }

    fun isIdle(albumId: String): Boolean = _downloading.value[albumId]?.phase != "downloading"

    // -----------------------------------------------------------------------
    // Manifests
    // -----------------------------------------------------------------------

    private fun manifestDir(): File = File(context.filesDir, "downloads").apply { if (!exists()) mkdirs() }

    private fun manifestFile(albumId: String): File = File(manifestDir(), "${safeSegment(albumId)}.txt")

    fun manifest(albumId: String): AlbumManifest? =
        manifests[albumId] ?: runCatching {
            val f = manifestFile(albumId)
            if (!f.exists()) null else decodeManifest(f.readText())
        }.getOrNull()?.also { manifests[albumId] = it }

    /** Pages on disk for one chapter; `0` means "not downloaded". */
    fun downloadedPages(albumId: String, chapterId: String): Int =
        _chapterPages.value[albumId]?.get(chapterId) ?: 0

    private fun writeManifest(manifest: AlbumManifest) {
        runCatching {
            manifestFile(manifest.albumId).writeText(encodeManifest(manifest))
        }
    }

    private fun updateManifest(
        albumId: String,
        albumName: String,
        chapter: SeriesItem,
        pages: Int,
    ) {
        val base = manifest(albumId) ?: AlbumManifest(albumId, albumName, emptyList())
        val next = base
            .copy(name = albumName.ifBlank { base.name })
            .withChapter(
                DownloadedChapter(
                    chapterId = chapter.id,
                    name = chapter.name,
                    sort = chapter.sort,
                    pageCount = pages,
                ),
            )
        manifests[albumId] = next
        writeManifest(next)
        _chapterPages.update { it + (albumId to (it[albumId].orEmpty() + (chapter.id to pages))) }
    }

    private fun forgetManifest(albumId: String) {
        manifests.remove(albumId)
        runCatching { manifestFile(albumId).delete() }
        _chapterPages.update { it - albumId }
    }

    // -----------------------------------------------------------------------
    // Download jobs
    // -----------------------------------------------------------------------

    /** Downloads every chapter of an album. */
    suspend fun downloadAlbum(albumId: String): Boolean = withContext(Dispatchers.IO) {
        val detail = when (val r = repository.getAlbum(albumId)) {
            is RepoResult.Ok -> r.data
            is RepoResult.Err -> {
                _downloading.update { it + (albumId to Progress(albumId, 0, 1, "failed", r.message)) }
                return@withContext false
            }
        }
        val chapters = if (detail.series.isNotEmpty()) {
            seriesInOrder(detail.series)
        } else {
            // A single-file work has no series: its own id is the chapter id, which is also what
            // the reader uses, so the offline copy lines up with the online one.
            listOf(SeriesItem(id = albumId, sort = 1, name = detail.name))
        }
        runJob(albumId, detail.name.ifBlank { "JM$albumId" }, chapters)
    }

    /** Downloads one chapter of an album. Already-downloaded chapters are skipped. */
    suspend fun downloadChapter(albumId: String, albumName: String, chapter: SeriesItem): Boolean {
        if (chapter.id.isBlank()) return false
        return runJob(albumId, albumName, listOf(chapter))
    }

    private suspend fun runJob(
        albumId: String,
        albumName: String,
        chapters: List<SeriesItem>,
    ): Boolean = withContext(Dispatchers.IO) {
        val admitted = downloadMutex.withLock {
            if (_downloading.value[albumId]?.phase == "downloading") {
                false
            } else {
                cancelFlags.remove(albumId)
                _downloading.update { it + (albumId to Progress(albumId, 0, 0, "downloading")) }
                true
            }
        }
        if (!admitted) return@withContext false

        var ok = true
        try {
            val todo = chapters.filterNot { manifest(albumId)?.chapter(it.id) != null }
            if (todo.isEmpty()) {
                syncIndex(albumId, albumName)
                _downloading.update { it + (albumId to Progress(albumId, 0, 0, "done")) }
                return@withContext true
            }

            // Per-chapter read payloads, fetched concurrently - the chapter list of a 40-chapter
            // album would otherwise cost 40 sequential round trips before the first page is
            // requested. A chapter whose payload fails is skipped instead of failing the album.
            val fetched: List<Pair<SeriesItem, ReadData>?> = coroutineScope {
                // Bounded: a 400-chapter serial would otherwise open 400 sockets at once and get
                // rate-limited, which is exactly the download that used to fail.
                val gate = Semaphore(READ_PARALLELISM)
                todo.map { chapter ->
                    async {
                        gate.withPermit {
                            when (val r = repository.comicRead(chapter.id)) {
                                is RepoResult.Ok -> chapter to r.data
                                is RepoResult.Err -> null
                            }
                        }
                    }
                }.awaitAll()
            }
            if (fetched.any { it == null }) ok = false
            val payloads = fetched.filterNotNull()
            val totalPages = payloads.sumOf { it.second.images.size }
            if (totalPages == 0) throw IllegalStateException("no pages")

            val completed = AtomicInteger(0)
            _downloading.update { it + (albumId to Progress(albumId, 0, totalPages, "downloading")) }

            for ((chapter, data) in payloads) {
                if (cancelFlags[albumId] == true) {
                    ok = false
                    break
                }
                val chapterOk = downloadOneChapter(albumId, chapter, data, completed, totalPages)
                if (chapterOk) {
                    // A delete that started while this chapter was in flight owns the folder now;
                    // recording the chapter would resurrect an album the reader just removed.
                    if (cancelFlags[albumId] == true) {
                        ok = false
                        break
                    }
                    updateManifest(albumId, albumName, chapter, data.images.size)
                    syncIndex(albumId, albumName)
                } else {
                    ok = false
                    if (cancelFlags[albumId] == true) break
                }
            }

            cancelFlags.remove(albumId)
            if (ok) {
                _downloading.update { it + (albumId to Progress(albumId, totalPages, totalPages, "done")) }
            } else {
                _downloading.update {
                    it + (albumId to Progress(albumId, 0, 1, "failed", "部分頁面下載失敗"))
                }
            }
        } catch (e: Exception) {
            // Whatever already landed stays: a completed chapter is a finished download, and the
            // manifest is the only thing that says so. Only the interrupted chapter is removed.
            cancelFlags.remove(albumId)
            _downloading.update { it + (albumId to Progress(albumId, 0, 1, "failed", e.message)) }
            ok = false
        }
        ok
    }

    /** Downloads every page of one chapter; false if any page failed (its folder is then removed). */
    private suspend fun downloadOneChapter(
        albumId: String,
        chapter: SeriesItem,
        data: ReadData,
        completed: AtomicInteger,
        totalPages: Int,
    ): Boolean = coroutineScope {
        val images = data.images
        if (images.isEmpty()) return@coroutineScope false
        val aid = data.id.toLongOrNull() ?: 0L

        // One task per page. The file name carries the page number, so the order tasks happen to
        // finish in has no effect on what lands on disk.
        val tasks = images.mapIndexed { index, page: ReadPage ->
            PageTask(
                name = chapterPageName(index + 1),
                url = page.image,
                aid = aid,
                scrambleId = data.scrambleId,
            )
        }

        val outcomes: List<Boolean> = coroutineScope {
            val gate = Semaphore(PAGE_PARALLELISM)
            tasks.map { task ->
                async {
                    gate.withPermit {
                        var saved = false
                        var attempt = 0
                        while (!saved && attempt < PAGE_ATTEMPTS && cancelFlags[albumId] != true) {
                            attempt++
                            saved = savePage(albumId, chapter.id, task)
                        }
                        val n = completed.incrementAndGet()
                        _downloading.update {
                            it + (albumId to Progress(albumId, n, totalPages, "downloading", chapterId = chapter.id))
                        }
                        saved
                    }
                }
            }.awaitAll()
        }

        val failed = outcomes.any { !it } || cancelFlags[albumId] == true
        if (failed) deleteChapterFolder(albumId, chapter.id)
        !failed
    }

    /** Downloads one page, de-scrambles it (if the album requires it) and writes a JPEG. */
    private fun savePage(albumId: String, chapterId: String, task: PageTask): Boolean {
        val bytes = try {
            http.newCall(Request.Builder().url(task.url).build()).execute().use { it.body?.bytes() }
        } catch (_: Exception) {
            null
        } ?: return false

        val src = runCatching { decodeSampled(bytes, MAX_SAVE_DIM) }.getOrNull() ?: return false

        val out = if (ImageDescrambler.needsDescramble(task.aid, task.scrambleId, task.url)) {
            val num = ImageDescrambler.sliceCount(task.aid, fileName(task.url))
            val d = ImageDescrambler.descramble(src, num)
            if (d !== src) src.recycle()
            d
        } else {
            src
        }

        return writeJpeg(albumId, chapterId, task.name, out)
    }

    private fun writeJpeg(albumId: String, chapterId: String, name: String, bmp: Bitmap): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val path = chapterPath(albumId, chapterId)
                // Idempotent: remove any previous file with the same name before inserting.
                runCatching {
                    resolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                        arrayOf(path, downloadPageName(name)),
                    )
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, downloadPageName(name))
                    // Deliberately NOT "image/jpeg": MediaStore files anything with an image MIME
                    // as a picture, which is what made downloads show up in the gallery.
                    put(MediaStore.MediaColumns.MIME_TYPE, PAGE_MIME)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, path)
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
                    "JMReader/$albumId/${safeSegment(chapterId)}",
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

    // -----------------------------------------------------------------------
    // Local pages
    // -----------------------------------------------------------------------

    /** What is actually on disk for an album, grouped by chapter folder. */
    data class LocalPages(
        /** chapterId -> page Uris in page order. */
        val chapters: Map<String, List<Uri>>,
        /** Pages written directly into the album folder by builds before chapter folders. */
        val loose: List<Uri>,
    ) {
        val isEmpty: Boolean get() = chapters.values.all { it.isEmpty() } && loose.isEmpty()
    }

    fun localPages(albumId: String): LocalPages {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
            )
            val prefix = selectionPath(albumId)
            val rows = mutableListOf<Triple<String, String, Uri>>() // folder, name, uri
            resolver.query(
                collection,
                projection,
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$prefix%"),
                null,
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: ""
                    // Only page files, so anything else that lands in the folder is ignored.
                    if (!isDownloadedPageFile(name)) continue
                    val rel = c.getString(pathCol) ?: continue
                    val folder = rel.removePrefix(prefix).trimEnd('/').substringBefore('/')
                    val uri = Uri.withAppendedPath(collection, c.getLong(idCol).toString())
                    rows.add(Triple(folder, name, uri))
                }
            }
            val sorted = rows.sortedBy { it.second }
            LocalPages(
                chapters = sorted.filter { it.first.isNotEmpty() }
                    .groupBy { it.first }
                    .mapValues { (_, list) -> list.map { it.third } },
                loose = sorted.filter { it.first.isEmpty() }.map { it.third },
            )
        } else {
            val root = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "JMReader/$albumId",
            )
            if (!root.exists()) return emptyListPages()
            val loose = mutableListOf<Uri>()
            val chapters = mutableMapOf<String, List<Uri>>()
            root.listFiles()?.forEach { f ->
                when {
                    f.isFile && isDownloadedPageFile(f.name) -> loose.add(Uri.fromFile(f))
                    f.isDirectory -> {
                        val pages = f.listFiles()
                            ?.filter { it.isFile && isDownloadedPageFile(it.name) }
                            ?.sortedBy { it.name }
                            ?.map { Uri.fromFile(it) }
                            .orEmpty()
                        if (pages.isNotEmpty()) chapters[f.name] = pages
                    }
                }
            }
            LocalPages(chapters = chapters, loose = loose.sortedBy { it.toString() })
            }
        } catch (_: Exception) {
            emptyListPages()
        }
    }

    private fun emptyListPages() = LocalPages(emptyMap(), emptyList())

    // -----------------------------------------------------------------------
    // Deletion
    // -----------------------------------------------------------------------

    suspend fun deleteAlbum(albumId: String) {
        cancelFlags[albumId] = true
        withContext(Dispatchers.IO) {
            // Let an in-flight job notice the flag and stop writing first: deleting the folder
            // underneath it would leave pages the manifest knows nothing about. Bounded, because
            // a stuck request must not make the delete button look dead.
            var waited = 0
            while (_downloading.value[albumId]?.phase == "downloading" && waited < 50) {
                delay(100)
                waited++
            }
            deleteAlbumFolder(albumId)
            forgetManifest(albumId)
            removeIndex(albumId)
        }
    }

    private fun deleteAlbumFolder(albumId: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf("${selectionPath(albumId)}%"),
                )
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                if (dir.exists()) runCatching { dir.deleteRecursively() }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId",
                )
                dir.listFiles()?.forEach { it.deleteRecursively() }
                dir.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun deleteChapterFolder(albumId: String, chapterId: String) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(chapterPath(albumId, chapterId)),
                )
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId/${safeSegment(chapterId)}",
                )
                if (dir.exists()) runCatching { dir.deleteRecursively() }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "JMReader/$albumId/${safeSegment(chapterId)}",
                )
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
        } catch (_: Exception) {
        }
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

    private fun loadChapterPages(): Map<String, Map<String, Int>> {
        val dir = File(context.filesDir, "downloads")
        if (!dir.isDirectory) return emptyMap()
        val out = mutableMapOf<String, Map<String, Int>>()
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") }?.forEach { file ->
            val manifest = runCatching { decodeManifest(file.readText()) }.getOrNull() ?: return@forEach
            val pages = manifest.chapters.associate { it.chapterId to it.pageCount }
            if (pages.isNotEmpty()) out[manifest.albumId] = pages
        }
        return out
    }

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

    /** Rewrites the album's index row from its manifest, so the library shows real counts. */
    private fun syncIndex(albumId: String, fallbackName: String) {
        val m = manifest(albumId) ?: return
        if (m.chapters.isEmpty()) return
        updateIndex(
            DownloadedAlbum(
                albumId = albumId,
                name = m.name.ifBlank { fallbackName },
                chapterCount = m.chapters.size,
                pageCount = m.pageCount,
                timestamp = System.currentTimeMillis(),
            ),
        )
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

        /** Chapter payloads fetched at once; the payload is small, the socket count is the cost. */
        const val READ_PARALLELISM = 4

        /** Attempts per page (1 try + 1 retry) before the chapter is declared failed. */
        const val PAGE_ATTEMPTS = 2

        const val KEY_LEGACY_MIGRATED = "legacyNamingMigrated"
    }
}

/** Extension of the JPEG stored on disk. */
internal const val PAGE_IMAGE_EXT = ".jpg"

/**
 * Marker appended after the image extension, e.g. `001.jpg.jm`.
 *
 * The media scanner classifies a file by its extension, so a name that no longer *ends* in an
 * image extension is not indexed as a picture - which is how these stay out of the gallery.
 */
internal const val GALLERY_HIDDEN_SUFFIX = ".jm"

/** MIME the pages are registered under, so MediaStore does not file them as pictures. */
internal const val PAGE_MIME = "application/octet-stream"

/** On-disk name of one page, e.g. `001` -> `001.jpg.jm`. */
internal fun downloadPageName(base: String): String = "$base$PAGE_IMAGE_EXT$GALLERY_HIDDEN_SUFFIX"

/**
 * True for a page this app wrote.
 *
 * Both the current hidden name and the plain `.jpg` written by earlier versions count, so albums
 * downloaded before the rename still open instead of reading as empty.
 */
internal fun isDownloadedPageFile(fileName: String): Boolean =
    fileName.endsWith(GALLERY_HIDDEN_SUFFIX) || fileName.endsWith(PAGE_IMAGE_EXT)

/**
 * A folder name that cannot escape its parent.
 *
 * Chapter ids are numeric strings today, but they arrive from the network and end up in a path,
 * so anything unexpected is folded to `_` rather than trusted.
 */
internal fun safeSegment(value: String): String {
    val cleaned = value.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }.joinToString("")
    return cleaned.ifBlank { "_" }
}

/** One page to fetch: its output name and everything [DownloadManager.savePage] needs. */
private data class PageTask(
    val name: String,
    val url: String,
    val aid: Long,
    val scrambleId: Long,
)
