package com.jm.reader.data.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** How far a chapter has been read, as the chapter list needs to show it. */
enum class ChapterReadState {
    UNREAD,
    READING,
    READ,
}

/**
 * Where the reader stopped inside one chapter.
 *
 * `page` is the page last *shown* (1-based) and is what "continue reading" resumes at;
 * `furthest` is the deepest page ever reached and is what decides [state]. Keeping them apart
 * matters: flipping back to look at page 3 must not un-read a chapter, and must not drag the
 * resume point forward either.
 */
data class ChapterProgress(
    val albumId: String,
    val chapterId: String,
    val name: String = "",
    val page: Int = 0,
    val furthest: Int = 0,
    val total: Int = 0,
    val done: Boolean = false,
    val at: Long = 0L,
) {
    val state: ChapterReadState get() = chapterReadState(this, total)
}

/**
 * Derives the badge a chapter row shows.
 *
 * [expectedTotal] covers the case where the reader never got a page count from `comic_read`
 * (offline chapters, or a record written before the count was known) while the album's `series`
 * list does report `total_page`.
 */
internal fun chapterReadState(progress: ChapterProgress?, expectedTotal: Int = 0): ChapterReadState {
    if (progress == null) return ChapterReadState.UNREAD
    val total = if (progress.total > 0) progress.total else expectedTotal
    return when {
        progress.furthest <= 0 && progress.page <= 0 -> ChapterReadState.UNREAD
        progress.done -> ChapterReadState.READ
        total > 0 && progress.furthest >= total -> ChapterReadState.READ
        else -> ChapterReadState.READING
    }
}

/**
 * Per-chapter reading positions, kept on the device.
 *
 * The old model stored one `pageIndex` per album, which cannot answer "which chapters have I
 * read" or "resume this chapter at page 27" for a 400-chapter serial. This store keeps one row
 * per (album, chapter); the album-level [HistoryManager] entry stays as the library's "last
 * opened" list and gains the chapter id so both agree.
 *
 * Rows are one per line, fields tab-separated, the free-text chapter name last - the same shape
 * [BookmarkStore] uses, so a chapter name containing punctuation cannot corrupt the file.
 */
class ReadingProgressStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_reading_progress", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(groupByAlbum(decodeProgress(prefs.getString(KEY, null))))
    val state: StateFlow<Map<String, Map<String, ChapterProgress>>> = _state.asStateFlow()

    fun album(albumId: String): Map<String, ChapterProgress> = _state.value[albumId].orEmpty()

    fun chapter(albumId: String, chapterId: String): ChapterProgress? =
        _state.value[albumId]?.get(chapterId)

    /** The chapter the reader touched most recently, i.e. what "continue reading" should open. */
    fun lastChapter(albumId: String): ChapterProgress? =
        _state.value[albumId]?.values?.maxByOrNull { it.at }

    /**
     * Records the page the reader is on.
     *
     * Called on every page turn (debounced) and when the reader is left. A record never lowers
     * [ChapterProgress.furthest] and never clears [ChapterProgress.done], so re-reading a chapter
     * cannot make it look unread again.
     */
    fun record(
        albumId: String,
        chapterId: String,
        name: String?,
        page: Int,
        total: Int,
        done: Boolean = false,
    ) {
        if (albumId.isBlank() || chapterId.isBlank()) return
        val shown = page.coerceAtLeast(0)
        val known = total.coerceAtLeast(0)
        if (shown <= 0 && known <= 0) return
        _state.update { current ->
            val prev = current[albumId]?.get(chapterId)
            val furthest = maxOf(shown, prev?.furthest ?: 0)
            val totalNow = if (known > 0) known else prev?.total ?: 0
            val entry = ChapterProgress(
                albumId = albumId,
                chapterId = chapterId,
                name = name?.takeIf { it.isNotBlank() } ?: prev?.name.orEmpty(),
                page = if (shown > 0) shown else prev?.page ?: 0,
                furthest = furthest,
                total = totalNow,
                done = done || prev?.done == true || (totalNow > 0 && furthest >= totalNow),
                at = System.currentTimeMillis(),
            )
            current + (albumId to (current[albumId].orEmpty() + (chapterId to entry)))
        }
        persist()
    }

    fun clearAlbum(albumId: String) {
        _state.update { it - albumId }
        persist()
    }

    fun clear() {
        _state.value = emptyMap()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist() {
        val rows = _state.value.values.flatMap { it.values }
            // Newest first, so trimming an over-long history drops the coldest works.
            .sortedByDescending { it.at }
            .take(MAX_ROWS)
        prefs.edit()
            .putString(KEY, rows.joinToString("\n", transform = ::encodeProgress))
            .apply()
    }

    private fun groupByAlbum(rows: List<ChapterProgress>): Map<String, Map<String, ChapterProgress>> =
        rows.groupBy { it.albumId }.mapValues { (_, list) -> list.associateBy { it.chapterId } }

    private companion object {
        const val KEY = "chapters"

        /**
         * Row cap. A long serial is ~500 chapters and one reader might follow a few dozen works;
         * 12k rows keeps that comfortably and bounds the file at a few hundred KB.
         */
        const val MAX_ROWS = 12_000
    }
}

/**
 * One progress row as one line: album, chapter, shown page, furthest page, total, done, time,
 * and the chapter name last so it may contain anything but a tab or a newline.
 */
internal fun encodeProgress(item: ChapterProgress): String = listOf(
    item.albumId,
    item.chapterId,
    item.page.toString(),
    item.furthest.toString(),
    item.total.toString(),
    if (item.done) "1" else "0",
    item.at.toString(),
    item.name.replace('\t', ' ').replace('\n', ' '),
).joinToString("\t")

/** Inverse of [encodeProgress]; malformed rows are dropped rather than crashing the app. */
internal fun decodeProgress(text: String?): List<ChapterProgress> =
    text?.split("\n")
        ?.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 7) return@mapNotNull null
            val albumId = parts[0]
            val chapterId = parts[1]
            if (albumId.isBlank() || chapterId.isBlank()) return@mapNotNull null
            val page = parts[2].toIntOrNull() ?: return@mapNotNull null
            ChapterProgress(
                albumId = albumId,
                chapterId = chapterId,
                name = parts.getOrElse(7) { "" },
                page = page,
                furthest = parts[3].toIntOrNull() ?: page,
                total = parts[4].toIntOrNull() ?: 0,
                done = parts[5] == "1",
                at = parts[6].toLongOrNull() ?: 0L,
            )
        }
        .orEmpty()
