package com.jm.reader.data.stats

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln
import kotlin.math.roundToInt

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
 * How a work's contribution to the taste profile is scored.
 *
 * The weight is **completion × time**, not pages. Pages alone reward long works: reading 20 pages of
 * a 900-page series is a 2 % sample but used to outweigh finishing a 60-page one-shot. Completion
 * says how much of *this* work was actually consumed, and the time factor stops a 5-second skim that
 * happens to end on the last page from counting as a real read.
 *
 * Pure functions on purpose: the scoring is unit-tested without Android.
 */
object Taste {

    /** 0..1. Falls back to chapters, then to a neutral 0.5 when the work's size is unknown. */
    fun completion(readPages: Int, totalPages: Int, chaptersRead: Int, totalChapters: Int): Float {
        if (totalPages > 0) return (readPages.toFloat() / totalPages).coerceIn(0f, 1f)
        if (totalChapters > 0) return (chaptersRead.toFloat() / totalChapters).coerceIn(0f, 1f)
        return if (readPages > 0) 0.5f else 0f
    }

    /**
     * 1.0 with no time, ~2.0 at an hour, capped at 2.5.
     *
     * Logarithmic because the difference between 1 and 10 minutes matters far more than between 100
     * and 110. The cap keeps a work left open for hours from dominating every tag on its own.
     */
    fun timeFactor(millis: Long): Float {
        val minutes = (millis / 60_000f).coerceAtLeast(0f)
        return 1f + (ln(1f + minutes) / ln(61f)).coerceIn(0f, 1.5f)
    }

    fun verdictMultiplier(verdict: Verdict?): Float = when (verdict) {
        Verdict.LIKED -> 1.6f
        Verdict.DISLIKED -> 0.2f
        else -> 1f
    }

    /** Points a single work adds to each of its tags; 0 when nothing was read. */
    fun weightPoints(completion: Float, millis: Long, verdict: Verdict?): Int {
        if (completion <= 0f) return 0
        val points = completion * timeFactor(millis) * verdictMultiplier(verdict) * 100f
        return points.roundToInt().coerceAtLeast(1)
    }
}

/**
 * Local reading statistics - the data behind the stats screen and the "guess you like" dice.
 *
 * **Only actual reading counts.** Opening a detail page (tapping a cover) is not a read: nothing is
 * recorded until the reader screen successfully loads a chapter.
 *
 * Per work it keeps:
 *  - `chapters`: chapter id -> furthest page reached, so re-reading a page adds nothing;
 *  - totals from the album's chapter list, which turn "pages read" into a **completion rate**;
 *  - `millis`: reading time, reported only while the reader is resumed;
 *  - `tags`: the work's tags;
 *  - `verdict`: the explicit 起立 / 平平无奇 / 萎了 rating.
 *
 * Everything is on-device; there is no account and no upload.
 */
class ReadingStatsManager(context: Context) {

    data class WorkRead(
        val albumId: String,
        val name: String,
        val pages: Int,
        val millis: Long,
        /** 0..1 share of the work that was actually read. */
        val completion: Float,
        val verdict: Verdict? = null,
    )

    /** Per-tag aggregate: how much it weighs, and what the reading behind it looked like. */
    data class TagStat(
        val weight: Int,
        val works: Int,
        val millis: Long,
        /** Sum of per-work completions; divide by [works] for the average. */
        val completionSum: Float,
    ) {
        val completion: Float get() = if (works == 0) 0f else completionSum / works
    }

    data class Snapshot(
        val albumCount: Int = 0,
        val totalMillis: Long = 0L,
        /** Mean completion across works that were actually read, 0..1. */
        val averageCompletion: Float = 0f,
        val tagCounts: Map<String, TagStat> = emptyMap(),
        val topWorks: List<WorkRead> = emptyList(),
        val verdicts: Map<String, Verdict> = emptyMap(),
    ) {
        val tagTotal: Int get() = tagCounts.values.sumOf { it.weight }

        val hasData: Boolean get() = albumCount > 0 || totalMillis > 0

        /** Tags sorted by weight, heaviest first. */
        fun rankedTags(): List<Map.Entry<String, TagStat>> =
            tagCounts.entries.sortedWith(
                compareByDescending<Map.Entry<String, TagStat>> { it.value.weight }.thenBy { it.key },
            )

        /** Works the reader explicitly rejected, so the guesser can avoid handing them back. */
        val dislikedIds: Set<String>
            get() = verdicts.filterValues { it == Verdict.DISLIKED }.keys
    }

    private data class Album(
        var name: String = "",
        var tags: List<String> = emptyList(),
        val chapters: MutableMap<String, Int> = LinkedHashMap(),
        var millis: Long = 0L,
        var at: Long = 0L,
        var totalPages: Int = 0,
        var totalChapters: Int = 0,
        var verdict: Verdict? = null,
    ) {
        val pages: Int get() = chapters.values.sum()

        val completion: Float
            get() = Taste.completion(pages, totalPages, chapters.size, totalChapters)
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

    /**
     * How big the work is, from its chapter list. Only ever grows: a partial series response must
     * not shrink a denominator that a full one already established.
     */
    @Synchronized
    fun setScope(albumId: String, totalChapters: Int, totalPages: Int) {
        if (albumId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (totalChapters > album.totalChapters) album.totalChapters = totalChapters
        if (totalPages > album.totalPages) album.totalPages = totalPages
        album.at = System.currentTimeMillis()
        commit()
    }

    /** A chapter was opened: it counts as at least one page read. */
    @Synchronized
    fun recordChapter(albumId: String, chapterId: String) {
        if (albumId.isBlank() || chapterId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        if (album.chapters[chapterId] == null) album.chapters[chapterId] = 1
        if (album.chapters.size > album.totalChapters) album.totalChapters = album.chapters.size
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

    /** Explicit verdict from the detail page; passing the current one again clears it (a toggle). */
    @Synchronized
    fun setVerdict(albumId: String, verdict: Verdict?) {
        if (albumId.isBlank()) return
        val album = albums.getOrPut(albumId) { Album() }
        album.verdict = if (album.verdict == verdict) null else verdict
        album.at = System.currentTimeMillis()
        commit()
    }

    @Synchronized
    fun clear() {
        albums.clear()
        prefs.edit().remove(KEY).remove(LEGACY_KEY).remove(OLDER_KEY).apply()
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
        val tags = LinkedHashMap<String, TagStat>()
        var albumCount = 0
        var totalMillis = 0L
        var completionSum = 0f
        val works = ArrayList<WorkRead>()
        val verdicts = LinkedHashMap<String, Verdict>()

        albums.forEach { (id, album) ->
            album.verdict?.let { verdicts[id] = it }
            // A work counts as "read" only once a page was turned in the reader.
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
                    .put("at", album.at)
                    .put("totalPages", album.totalPages)
                    .put("totalChapters", album.totalChapters)
                    .put("verdict", album.verdict?.stored ?: Int.MIN_VALUE),
            )
        }
        root.put("albums", arr)
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    private fun load() {
        // v3 adds completion denominators and the verdict. v2 rows stay readable: they simply carry
        // no totals, so completion falls back to a neutral estimate until the work is opened again
        // (the reader calls setScope on every load, which corrects it).
        val raw = prefs.getString(KEY, null)
            ?: prefs.getString(LEGACY_KEY, null)
            ?: return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val map = root.optJSONObject("albums") ?: return
        map.keys().forEach { id ->
            val o = map.optJSONObject(id) ?: return@forEach
            val album = Album(
                name = o.optString("name"),
                millis = o.optLong("millis", 0L).coerceAtLeast(0L),
                at = o.optLong("at", 0L),
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
        const val KEY = "stats_v3"
        const val LEGACY_KEY = "stats_v2"
        const val OLDER_KEY = "stats_v1"
        const val MAX_ALBUMS = 500
        const val MAX_TAGS_PER_ALBUM = 12
        const val MAX_TOP_WORKS = 8
        const val MAX_SESSION_MILLIS = 12L * 60 * 60 * 1000
    }
}
