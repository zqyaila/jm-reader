package com.jm.reader.data.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device browsing history: every album opened in the detail screen or the reader, newest first.
 *
 * History lives in SharedPreferences so it works without an account and offline - the Library tab
 * shows it even when nobody is logged in. Logged-in readers still report their reads to the
 * server's `watch_list` as before; this is the local copy.
 *
 * The reference project keeps the equivalent data in a `history` sqlite table keyed by `bookId`
 * with a `tick` sort key (JMComic-qt `history_view.py`); here one JSON entry per album plays the
 * same role, plus the chapter/page the reader stopped at.
 */
class HistoryManager(context: Context) {

    data class Entry(
        val albumId: String,
        val name: String,
        val author: String?,
        val updateAt: Long,
        val viewedAt: Long,
        /** Last chapter title opened in the reader, when known. */
        val episodeName: String? = null,
        /** 1-based page the reader last showed. */
        val pageIndex: Int = 0,
    ) {
        /** How many pages the reader had reached, when known. */
        val hasProgress: Boolean get() = pageIndex > 0
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_history", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    /** Records an album (or moves it back to the top). Safe to call from the main thread. */
    fun record(albumId: String, name: String, author: String?, updateAt: Long) {
        upsert(albumId) { existing ->
            Entry(
                albumId = albumId,
                name = name.ifBlank { existing?.name.orEmpty() },
                author = author?.takeIf { it.isNotBlank() } ?: existing?.author,
                updateAt = if (updateAt > 0) updateAt else existing?.updateAt ?: 0L,
                viewedAt = System.currentTimeMillis(),
                episodeName = existing?.episodeName,
                pageIndex = existing?.pageIndex ?: 0,
            )
        }
    }

    /**
     * Records reading progress from the reader. Called often, so it only bumps what changed and
     * never clears the album's other fields.
     */
    fun recordProgress(albumId: String, episodeName: String?, pageIndex: Int) {
        if (albumId.isBlank()) return
        upsert(albumId) { existing ->
            val base = existing ?: Entry(
                albumId = albumId,
                name = "",
                author = null,
                updateAt = 0L,
                viewedAt = System.currentTimeMillis(),
            )
            base.copy(
                episodeName = episodeName?.takeIf { it.isNotBlank() } ?: base.episodeName,
                pageIndex = if (pageIndex > 0) pageIndex else base.pageIndex,
                viewedAt = System.currentTimeMillis(),
            )
        }
    }

    private inline fun upsert(albumId: String, build: (Entry?) -> Entry) {
        if (albumId.isBlank()) return
        _entries.update { current ->
            val existing = current.firstOrNull { it.albumId == albumId }
            (listOf(build(existing)) + current.filterNot { it.albumId == albumId }).take(MAX_ENTRIES)
        }
        persist()
    }

    fun remove(albumId: String) {
        _entries.update { current -> current.filterNot { it.albumId == albumId } }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist() {
        val arr = JSONArray()
        _entries.value.forEach { e ->
            arr.put(
                JSONObject()
                    .put("albumId", e.albumId)
                    .put("name", e.name)
                    .put("author", e.author ?: "")
                    .put("updateAt", e.updateAt)
                    .put("viewedAt", e.viewedAt)
                    .put("episodeName", e.episodeName ?: "")
                    .put("pageIndex", e.pageIndex)
            )
        }
        // apply() is asynchronous, so recording never blocks the UI thread.
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("albumId")
                if (id.isBlank()) return@mapNotNull null
                Entry(
                    albumId = id,
                    name = o.optString("name"),
                    author = o.optString("author").takeIf { it.isNotBlank() },
                    updateAt = o.optLong("updateAt"),
                    viewedAt = o.optLong("viewedAt"),
                    episodeName = o.optString("episodeName").takeIf { it.isNotBlank() },
                    pageIndex = o.optInt("pageIndex", 0),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 200
    }
}
