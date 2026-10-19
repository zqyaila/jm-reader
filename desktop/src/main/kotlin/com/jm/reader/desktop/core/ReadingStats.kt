package com.jm.reader.desktop.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.prefs.Preferences

/**
 * On-device reading statistics for the desktop client.
 *
 * Mirrors the Android module's `ReadingStatsManager` rule for rule, because the two clients should
 * report the same numbers for the same reading:
 *
 *  - **Only actual reading counts.** The reader records a chapter when it loads; opening a detail
 *    page records nothing.
 *  - Pages are stored per chapter as "furthest page reached"; a work's pages is the sum of those,
 *    so scrolling back or re-opening a chapter never inflates the count.
 *  - The tag weights that drive "guess you like" are those page counts, linear (200 pages -> 200).
 *
 * Backend: `java.util.prefs`, the same store [Session] uses. It survives app restarts and needs no
 * extra dependency or file-format handling.
 */
class ReadingStats {

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
        val tagCounts: Map<String, Int> = emptyMap(),
        val topWorks: List<WorkRead> = emptyList(),
    ) {
        val tagTotal: Int get() = tagCounts.values.sum()

        val hasData: Boolean get() = albumCount > 0 || totalMillis > 0 || totalPages > 0

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
    ) {
        val pages: Int get() = chapters.values.sum()
    }

    private val node: Preferences? = runCatching { Preferences.userRoot().node(NODE) }.getOrNull()

    /** Used when the preference store is unavailable (locked registry, sandboxed runner). */
    private var memoryBlob: String? = null

    private val albums = LinkedHashMap<String, Album>()

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    init {
        load()
        publish()
    }

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
        commit()
    }

    @Synchronized
    fun recordChapter(albumId: String, chapterId: String) {
        if (albumId.isBlank() || chapterId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (album.chapters[chapterId] == null) album.chapters[chapterId] = 1
        commit()
    }

    @Synchronized
    fun recordPage(albumId: String, chapterId: String, page: Int) {
        if (albumId.isBlank() || chapterId.isBlank() || page <= 0) return
        val album = albums.getOrPut(albumId) { Album() }
        if (page <= (album.chapters[chapterId] ?: 0)) return
        album.chapters[chapterId] = page
        commit()
    }

    @Synchronized
    fun addReadingTime(albumId: String, millis: Long) {
        if (albumId.isBlank() || millis <= 0L) return
        val album = albums.getOrPut(albumId) { Album() }
        album.millis += millis.coerceAtMost(MAX_SESSION_MILLIS)
        commit()
    }

    @Synchronized
    fun clear() {
        albums.clear()
        memoryBlob = null
        node?.let { runCatching { it.remove(KEY); it.flush() } }
        publish()
    }

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
            if (pages <= 0) return@forEach
            albumCount++
            totalPages += pages
            totalMillis += album.millis
            album.tags.forEach { tag -> tags[tag] = (tags[tag] ?: 0) + pages }
            works += WorkRead(albumId = id, name = album.name, pages = pages, millis = album.millis)
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
        val iterator = albums.keys.iterator()
        repeat(albums.size - MAX_ALBUMS) {
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
                    .put("millis", album.millis),
            )
        }
        root.put("albums", arr)
        val blob = root.toString()
        memoryBlob = blob
        node?.let { runCatching { it.put(KEY, blob); it.flush() } }
    }

    private fun load() {
        val raw = node?.let { runCatching { it.get(KEY, null) }.getOrNull() } ?: memoryBlob ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val map = root.optJSONObject("albums") ?: return
        map.keys().forEach { id ->
            val o = map.optJSONObject(id) ?: return@forEach
            val album = Album(
                name = o.optString("name"),
                millis = o.optLong("millis", 0L).coerceAtLeast(0L),
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
        const val NODE = "com/jm/reader/stats"
        const val KEY = "stats_v2"
        const val MAX_ALBUMS = 500
        const val MAX_TAGS_PER_ALBUM = 12
        const val MAX_TOP_WORKS = 8
        const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000
    }
}

/**
 * Weighted tag pick for "guess you like" - the desktop twin of the Android module's `GuessEngine`.
 * Pure so the distribution can be unit tested without a UI.
 */
object Guess {
    fun pickTag(counts: Map<String, Int>, random: kotlin.random.Random = kotlin.random.Random.Default): String? {
        val positive = counts.filterValues { it > 0 }
        val total = positive.values.sum()
        if (total <= 0) return null
        var offset = random.nextInt(total)
        for ((tag, weight) in positive) {
            if (offset < weight) return tag
            offset -= weight
        }
        return positive.keys.lastOrNull()
    }

    fun rankedTags(counts: Map<String, Int>, limit: Int = Int.MAX_VALUE): List<String> =
        counts.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }

    fun share(count: Int, total: Int): Float =
        if (total <= 0) 0f else (count.toFloat() / total).coerceIn(0f, 1f)
}
