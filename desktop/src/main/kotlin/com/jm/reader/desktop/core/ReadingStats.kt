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
 *  - Pages are stored per chapter as "furthest page reached", so scrolling back or re-opening a
 *    chapter never inflates the count; those pages divided by the work's size give a **completion
 *    rate**, which (together with time and the explicit verdict) is what drives "guess you like".
 *  - A work's weight is `completion x time x verdict`, not raw pages: finishing a 60-page one-shot
 *    should outweigh a 2 % sample of a 900-page series.
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
        val completion: Float,
        val verdict: Verdict? = null,
    )

    data class TagStat(
        val weight: Int,
        val works: Int,
        val millis: Long,
        val completionSum: Float,
    ) {
        val completion: Float get() = if (works == 0) 0f else completionSum / works
    }

    data class Snapshot(
        val albumCount: Int = 0,
        val totalMillis: Long = 0L,
        val averageCompletion: Float = 0f,
        val tagCounts: Map<String, TagStat> = emptyMap(),
        val topWorks: List<WorkRead> = emptyList(),
        val verdicts: Map<String, Verdict> = emptyMap(),
    ) {
        val tagTotal: Int get() = tagCounts.values.sumOf { it.weight }

        val hasData: Boolean get() = albumCount > 0 || totalMillis > 0

        fun rankedTags(): List<Map.Entry<String, TagStat>> =
            tagCounts.entries.sortedWith(
                compareByDescending<Map.Entry<String, TagStat>> { it.value.weight }.thenBy { it.key },
            )

        val dislikedIds: Set<String>
            get() = verdicts.filterValues { it == Verdict.DISLIKED }.keys
    }

    private data class Album(
        var name: String = "",
        var tags: List<String> = emptyList(),
        val chapters: MutableMap<String, Int> = LinkedHashMap(),
        var millis: Long = 0L,
        var totalPages: Int = 0,
        var totalChapters: Int = 0,
        var verdict: Verdict? = null,
    ) {
        val pages: Int get() = chapters.values.sum()
        val completion: Float
            get() = Taste.completion(pages, totalPages, chapters.size, totalChapters)
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

    /** How big the work is, from its chapter list. Only ever grows. */
    @Synchronized
    fun setScope(albumId: String, totalChapters: Int, totalPages: Int) {
        if (albumId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (totalChapters > album.totalChapters) album.totalChapters = totalChapters
        if (totalPages > album.totalPages) album.totalPages = totalPages
        commit()
    }

    @Synchronized
    fun recordChapter(albumId: String, chapterId: String) {
        if (albumId.isBlank() || chapterId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (album.chapters[chapterId] == null) album.chapters[chapterId] = 1
        if (album.chapters.size > album.totalChapters) album.totalChapters = album.chapters.size
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

    /** Explicit verdict; passing the current one again clears it (a toggle). */
    @Synchronized
    fun setVerdict(albumId: String, verdict: Verdict?) {
        if (albumId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        album.verdict = if (album.verdict == verdict) null else verdict
        commit()
    }

    @Synchronized
    fun clear() {
        albums.clear()
        memoryBlob = null
        node?.let { runCatching { it.remove(KEY); it.remove(LEGACY_KEY); it.flush() } }
        publish()
    }

    // -----------------------------------------------------------------------

    private fun commit() {
        trim()
        persist()
        publish()
    }

    private fun publish() {
        val tags = LinkedHashMap<String, TagStat>()
        var albumCount = 0
        var totalMillis = 0L
        var completionSum = 0f
        val works = ArrayList<WorkRead>()
        val verdicts = LinkedHashMap<String, Verdict>()

        albums.forEach { (id, album) ->
            album.verdict?.let { verdicts[id] = it }
            if (album.chapters.isEmpty()) return@forEach
            val completion = album.completion
            val weight = Taste.weightPoints(completion, album.millis, album.verdict)
            albumCount++
            totalMillis += album.millis
            completionSum += completion
            album.tags.forEach { tag ->
                val prev = tags[tag]
                tags[tag] = TagStat(
                    weight = (prev?.weight ?: 0) + weight,
                    works = (prev?.works ?: 0) + 1,
                    millis = (prev?.millis ?: 0L) + album.millis,
                    completionSum = (prev?.completionSum ?: 0f) + completion,
                )
            }
            works += WorkRead(
                albumId = id,
                name = album.name,
                pages = album.pages,
                millis = album.millis,
                completion = completion,
                verdict = album.verdict,
            )
        }

        _snapshot.value = Snapshot(
            albumCount = albumCount,
            totalMillis = totalMillis,
            averageCompletion = if (albumCount == 0) 0f else completionSum / albumCount,
            tagCounts = tags.entries.sortedByDescending { it.value.weight }.associate { it.key to it.value },
            topWorks = works.sortedByDescending {
                Taste.weightPoints(it.completion, it.millis, it.verdict)
            }.take(MAX_TOP_WORKS),
            verdicts = verdicts,
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
                    .put("millis", album.millis)
                    .put("totalPages", album.totalPages)
                    .put("totalChapters", album.totalChapters)
                    .put("verdict", album.verdict?.stored ?: Int.MIN_VALUE),
            )
        }
        root.put("albums", arr)
        val blob = root.toString()
        memoryBlob = blob
        node?.let { runCatching { it.put(KEY, blob); it.flush() } }
    }

    private fun load() {
        val raw = node?.let {
            runCatching { it.get(KEY, null) }.getOrNull()
                ?: runCatching { it.get(LEGACY_KEY, null) }.getOrNull()
        } ?: memoryBlob ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val map = root.optJSONObject("albums") ?: return
        map.keys().forEach { id ->
            val o = map.optJSONObject(id) ?: return@forEach
            val album = Album(
                name = o.optString("name"),
                millis = o.optLong("millis", 0L).coerceAtLeast(0L),
                totalPages = o.optInt("totalPages", 0).coerceAtLeast(0),
                totalChapters = o.optInt("totalChapters", 0).coerceAtLeast(0),
                verdict = Verdict.of(o.optInt("verdict", Int.MIN_VALUE)),
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
        const val KEY = "stats_v3"
        const val LEGACY_KEY = "stats_v2"
        const val MAX_ALBUMS = 500
        const val MAX_TAGS_PER_ALBUM = 12
        const val MAX_TOP_WORKS = 8
        const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000
    }
}

/** The reader's explicit verdict on a work, from the bar at the bottom of the detail page. */
enum class Verdict(val stored: Int) {
    LIKED(1),
    MEH(0),
    DISLIKED(-1),
    ;

    companion object {
        fun of(stored: Int): Verdict? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * Completion x time scoring - identical rules to the Android module's `Taste`, so both clients
 * report the same numbers for the same reading.
 */
object Taste {
    fun completion(readPages: Int, totalPages: Int, chaptersRead: Int, totalChapters: Int): Float {
        if (totalPages > 0) return (readPages.toFloat() / totalPages).coerceIn(0f, 1f)
        if (totalChapters > 0) return (chaptersRead.toFloat() / totalChapters).coerceIn(0f, 1f)
        return if (readPages > 0) 0.5f else 0f
    }

    fun timeFactor(millis: Long): Float {
        val minutes = (millis / 60_000f).coerceAtLeast(0f)
        return 1f + (kotlin.math.ln(1f + minutes) / kotlin.math.ln(61f)).coerceIn(0f, 1.5f)
    }

    fun verdictMultiplier(verdict: Verdict?): Float = when (verdict) {
        Verdict.LIKED -> 1.6f
        Verdict.DISLIKED -> 0.2f
        else -> 1f
    }

    fun weightPoints(completion: Float, millis: Long, verdict: Verdict?): Int {
        if (completion <= 0f) return 0
        val points = completion * timeFactor(millis) * verdictMultiplier(verdict) * 100f
        return kotlin.math.round(points).toInt().coerceAtLeast(1)
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
