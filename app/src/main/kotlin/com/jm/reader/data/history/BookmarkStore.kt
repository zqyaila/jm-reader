package com.jm.reader.data.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A single page the reader marked, for one album. `page` is 1-based. */
data class ReadingBookmark(
    val albumId: String,
    val chapterId: String,
    val chapterName: String,
    val page: Int,
    val createdAt: Long = 0L,
)

/**
 * Per-album page bookmarks.
 *
 * Bookmarks are local only: the site has no bookmark endpoint, and syncing them through favourites
 * would need a per-page API that does not exist. The store follows [SearchHistoryManager]'s shape
 * (SharedPreferences + `StateFlow`) so the reader can show the list without a network round trip.
 *
 * Rows are written one per line, fields tab-separated, the chapter name last so it can contain
 * anything but a newline. One bookmark per (chapter, page); adding it again removes it.
 */
class BookmarkStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_bookmarks", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ReadingBookmark>> = _items.asStateFlow()

    /** Adds the bookmark, or removes it when the same page is already marked. Returns `true` if added. */
    fun toggle(bookmark: ReadingBookmark): Boolean {
        val exists = _items.value.any { it.chapterId == bookmark.chapterId && it.page == bookmark.page }
        _items.update { current ->
            if (exists) {
                current.filterNot { it.chapterId == bookmark.chapterId && it.page == bookmark.page }
            } else {
                (listOf(bookmark.copy(createdAt = System.currentTimeMillis())) + current).take(MAX_ITEMS)
            }
        }
        persist()
        return !exists
    }

    fun remove(bookmark: ReadingBookmark) {
        _items.update { current ->
            current.filterNot { it.chapterId == bookmark.chapterId && it.page == bookmark.page }
        }
        persist()
    }

    fun forAlbum(albumId: String): List<ReadingBookmark> =
        _items.value.filter { it.albumId == albumId }.sortedWith(
            compareBy({ it.chapterName }, { it.page }),
        )

    private fun persist() {
        prefs.edit().putString(KEY, _items.value.joinToString("\n", transform = ::encodeBookmark)).apply()
    }

    private fun load(): List<ReadingBookmark> = decodeBookmarks(prefs.getString(KEY, null))

    private companion object {
        const val KEY = "items"

        /** Enough for a long serial; the oldest marks fall off after that. */
        const val MAX_ITEMS = 300
    }
}

/**
 * One bookmark as one line. Fields are tab-separated with the chapter name last, so the name may
 * contain anything except a newline or a tab (both are folded to spaces here).
 *
 * Split out from the store so the wire format can be tested without an Android `Context`.
 */
internal fun encodeBookmark(item: ReadingBookmark): String = listOf(
    item.albumId,
    item.chapterId,
    item.page.toString(),
    item.createdAt.toString(),
    item.chapterName.replace('\t', ' ').replace('\n', ' '),
).joinToString("\t")

/** Inverse of [encodeBookmark]; malformed lines are dropped rather than crashing the reader. */
internal fun decodeBookmarks(text: String?): List<ReadingBookmark> =
    text?.split("\n")
        ?.mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size < 4) return@mapNotNull null
            val page = parts[2].toIntOrNull() ?: return@mapNotNull null
            ReadingBookmark(
                albumId = parts[0],
                chapterId = parts[1],
                chapterName = parts.getOrElse(4) { "" },
                page = page,
                createdAt = parts[3].toLongOrNull() ?: 0L,
            )
        }
        .orEmpty()
