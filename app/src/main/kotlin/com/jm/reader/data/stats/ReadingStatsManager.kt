package com.jm.reader.data.stats

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local reading statistics - the data behind the stats screen and the "guess you like" dice.
 *
 * **Only actual reading counts.** Opening a detail page (tapping a cover) is not a read: nothing is
 * recorded until the reader screen successfully loads a chapter. That distinction is what makes the
 * numbers mean "what I read", not "what I clicked".
 *
 * Per work it keeps:
 *  - `chapters`: chapter id -> furthest page reached. Pages read of a work is the sum of those
 *    furthest pages, so re-reading a page does not inflate anything.
 *  - `millis`: reading time, reported by the reader only while it is resumed.
 *  - `tags`: the work's tags, captured when the reader loads its metadata.
 *
 * The tag weights that feed [GuessEngine] are **pages read**, not "works opened": a 120-page work
 * the reader actually finished should count far more than a 6-page sample they bounced off. The
 * weight is linear - a 200-page work contributes 200 to each of its tags - so what the stats screen
 * shows ("N 页") is exactly what the recommendation uses.
 *
 * Everything is on-device; there is no account and no upload.
 */
class ReadingStatsManager(context: Context) {

    data class WorkRead(
        val albumId: String,
        val name: String,
        val pages: Int,
        val millis: Long,
    )

    data class Snapshot(
        val albumCount: Int = 0,
        val totalMillis: Long = 0L,
        val totalPages: Int = 0,
        /** tag -> pages read in works carrying it (linear, no per-work cap). */
        val tagCounts: Map<String, Int> = emptyMap(),
        val topWorks: List<WorkRead> = emptyList(),
    ) {
        val tagTotal: Int get() = tagCounts.values.sum()

        val hasData: Boolean get() = albumCount > 0 || totalMillis > 0 || totalPages > 0

        /** Tags sorted by weight, heaviest first. */
        fun rankedTags(): List<Map.Entry<String, Int>> =
            tagCounts.entries.sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key },
            )
    }

    private data class Album(
        var name: String = "",
        var tags: List<String> = emptyList(),
        val chapters: MutableMap<String, Int> = LinkedHashMap(),
        var millis: Long = 0L,
        var at: Long = 0L,
    ) {
        val pages: Int get() = chapters.values.sum()
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_reading_stats", Context.MODE_PRIVATE)

    private val albums = LinkedHashMap<String, Album>()

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    init {
        load()
        publish()
    }

    /**
     * Records a work's title/tags. Called from the reader once the album metadata arrives, so a
     * work that was never opened in the reader contributes nothing.
     */
    @Synchronized
    fun setMeta(albumId: String, name: String, tags: List<String>) {
        if (albumId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (name.isNotBlank()) album.name = name
        val clean = tags.map { it.trim().trimStart('#') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TAGS_PER_ALBUM)
        if (clean.isNotEmpty()) album.tags = (album.tags + clean).distinct().take(MAX_TAGS_PER_ALBUM)
        album.at = System.currentTimeMillis()
        commit()
    }

    /** A chapter was opened: it counts as at least one page read. */
    @Synchronized
    fun recordChapter(albumId: String, chapterId: String) {
        if (albumId.isBlank() || chapterId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (album.chapters[chapterId] == null) album.chapters[chapterId] = 1
        album.at = System.currentTimeMillis()
        commit()
    }

    /** Furthest page reached in a chapter; only increases, so scrolling back costs nothing. */
    @Synchronized
    fun recordPage(albumId: String, chapterId: String, page: Int) {
        if (albumId.isBlank() || chapterId.isBlank() || page <= 0) return
        val album = albums.getOrPut(albumId) { Album() }
        val current = album.chapters[chapterId] ?: 0
        if (page <= current) return
        album.chapters[chapterId] = page
        album.at = System.currentTimeMillis()
        commit()
    }

    /** Reading time for one work; the reader reports resumed time only. */
    @Synchronized
    fun addReadingTime(albumId: String, millis: Long) {
        if (albumId.isBlank() || millis <= 0L) return
        val album = albums.getOrPut(albumId) { Album() }
        album.millis += millis.coerceAtMost(MAX_SESSION_MILLIS)
        album.at = System.currentTimeMillis()
        commit()
    }

    @Synchronized
    fun clear() {
        albums.clear()
        prefs.edit().remove(KEY).remove(LEGACY_KEY).apply()
        publish()
    }

    // -----------------------------------------------------------------------
    // Derived state + persistence
    // -----------------------------------------------------------------------

    private fun commit() {
        trim()
        persist()
        publish()
    }

    private fun publish() {
        val tags = LinkedHashMap<String, Int>()
        var albumCount = 0
        var totalPages = 0
        var totalMillis = 0L
        val works = ArrayList<WorkRead>()

        albums.forEach { (id, album) ->
            val pages = album.pages
            // A work counts as "read" only once a page was turned in the reader.
            if (pages > 0) {
                albumCount++
                totalPages += pages
                totalMillis += album.millis
                // Linear on purpose: the UI reports "N pages", so the weight behind the guess must
                // be that same N. (An earlier build capped this at 100, which silently disagreed
                // with the page count shown next to the tag.)
                val weight = pages.coerceAtLeast(1)
                album.tags.forEach { tag -> tags[tag] = (tags[tag] ?: 0) + weight }
                works += WorkRead(albumId = id, name = album.name, pages = pages, millis = album.millis)
            }
        }

        _snapshot.value = Snapshot(
            albumCount = albumCount,
            totalMillis = totalMillis,
            totalPages = totalPages,
            tagCounts = tags.entries.sortedByDescending { it.value }.associate { it.key to it.value },
            topWorks = works.sortedByDescending { it.pages }.take(MAX_TOP_WORKS),
        )
    }

    private fun trim() {
        if (albums.size <= MAX_ALBUMS) return
        val overflow = albums.size - MAX_ALBUMS
        val iterator = albums.keys.iterator()
        repeat(overflow) {
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun persist() {
        val root = JSONObject()
        val arr = JSONObject()
        albums.forEach { (id, album) ->
            val chapters = JSONObject()
            album.chapters.forEach { (cid, page) -> chapters.put(cid, page) }
            arr.put(
                id,
                JSONObject()
                    .put("name", album.name)
                    .put("tags", JSONArray(album.tags))
                    .put("chapters", chapters)
                    .put("millis", album.millis)
                    .put("at", album.at),
            )
        }
        root.put("albums", arr)
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val map = root.optJSONObject("albums") ?: return
        map.keys().forEach { id ->
            val o = map.optJSONObject(id) ?: return@forEach
            val album = Album(
                name = o.optString("name"),
                millis = o.optLong("millis", 0L).coerceAtLeast(0L),
                at = o.optLong("at", 0L),
            )
            val tagsArr = o.optJSONArray("tags") ?: JSONArray()
            album.tags = (0 until tagsArr.length())
                .mapNotNull { tagsArr.optString(it).takeIf { t -> t.isNotBlank() } }
                .distinct()
            val chapters = o.optJSONObject("chapters")
            chapters?.keys()?.forEach { cid ->
                val page = chapters.optInt(cid, 0)
                if (page > 0) album.chapters[cid] = page
            }
            albums[id] = album
        }
    }

    private companion object {
        const val KEY = "stats_v2"
        const val LEGACY_KEY = "stats_v1"
        const val MAX_ALBUMS = 500
        const val MAX_TAGS_PER_ALBUM = 12
        const val MAX_TOP_WORKS = 8
        const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000
    }
}
