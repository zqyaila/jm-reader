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
 * Deliberately **on-device only**: no account is needed, nothing is uploaded, and clearing app data
 * clears it. Two measurements are kept:
 *
 *  - **works**: every album opened in the detail screen, with the tags it carried. Stored per album
 *    rather than as a running tag counter, so re-opening the same work cannot inflate its tags.
 *  - **time**: milliseconds actually spent on the reader screen (the reader reports resumed time,
 *    so a backgrounded app does not count).
 *
 * Tag shares are derived from the album map: a tag's weight is "how many distinct works carried
 * it". That is what the weighted random picker consumes.
 */
class ReadingStatsManager(context: Context) {

    /**
     * Immutable view for the UI.
     *
     * [tagCounts] maps tag -> number of distinct works carrying it; [albumCount] is the number of
     * distinct works opened.
     */
    data class Snapshot(
        val albumCount: Int = 0,
        val totalMillis: Long = 0L,
        val tagCounts: Map<String, Int> = emptyMap(),
    ) {
        /** Total tag weight, i.e. the denominator of every share. */
        val tagTotal: Int get() = tagCounts.values.sum()

        val hasData: Boolean get() = albumCount > 0 || totalMillis > 0

        /** Tags sorted by weight, heaviest first. */
        fun rankedTags(): List<Map.Entry<String, Int>> =
            tagCounts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    }

    private data class Album(val tags: List<String>, val at: Long)

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_reading_stats", Context.MODE_PRIVATE)

    private val albums = LinkedHashMap<String, Album>()
    private var totalMillis: Long = 0L

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    init {
        load()
        publish()
    }

    /**
     * Records that [albumId] was opened, with its [tags].
     *
     * Idempotent for the same album: the stored tag set is replaced (merged) rather than added to a
     * counter, so opening a work fifty times does not make it fifty times as important.
     */
    @Synchronized
    fun recordAlbum(albumId: String, tags: List<String>) {
        if (albumId.isBlank()) return
        val clean = tags.map { it.trim().trimStart('#') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TAGS_PER_ALBUM)
        val existing = albums[albumId]
        val merged = ((existing?.tags ?: emptyList()) + clean).distinct().take(MAX_TAGS_PER_ALBUM)
        albums.remove(albumId)
        albums[albumId] = Album(merged, System.currentTimeMillis())
        trim()
        persist()
        publish()
    }

    /** Adds time actually spent reading. Ignored for non-positive or absurd values. */
    @Synchronized
    fun addReadingTime(millis: Long) {
        if (millis <= 0L) return
        // A single session longer than 12 h is almost certainly a clock/suspend artefact.
        totalMillis += millis.coerceAtMost(MAX_SESSION_MILLIS)
        persist()
        publish()
    }

    @Synchronized
    fun clear() {
        albums.clear()
        totalMillis = 0L
        prefs.edit().remove(KEY).apply()
        publish()
    }

    // -----------------------------------------------------------------------
    // Derived state + persistence
    // -----------------------------------------------------------------------

    private fun publish() {
        val counts = LinkedHashMap<String, Int>()
        albums.values.forEach { album ->
            album.tags.forEach { tag -> counts[tag] = (counts[tag] ?: 0) + 1 }
        }
        _snapshot.value = Snapshot(
            albumCount = albums.size,
            totalMillis = totalMillis,
            tagCounts = counts.entries
                .sortedByDescending { it.value }
                .associate { it.key to it.value },
        )
    }

    private fun trim() {
        if (albums.size <= MAX_ALBUMS) return
        // LinkedHashMap keeps insertion order, and recordAlbum re-inserts on every open, so the
        // oldest entries are at the front.
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
            arr.put(
                id,
                JSONObject()
                    .put("tags", JSONArray(album.tags))
                    .put("at", album.at),
            )
        }
        root.put("albums", arr)
        root.put("millis", totalMillis)
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        totalMillis = root.optLong("millis", 0L).coerceAtLeast(0L)
        val map = root.optJSONObject("albums") ?: return
        map.keys().forEach { id ->
            val o = map.optJSONObject(id) ?: return@forEach
            val tagsArr = o.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArr.length())
                .mapNotNull { tagsArr.optString(it).takeIf { t -> t.isNotBlank() } }
            albums[id] = Album(tags.distinct(), o.optLong("at", 0L))
        }
    }

    private companion object {
        const val KEY = "stats_v1"
        const val MAX_ALBUMS = 500
        const val MAX_TAGS_PER_ALBUM = 12
        const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000
    }
}
