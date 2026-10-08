package com.jm.reader.data.history

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Recent search terms, kept on the device.
 *
 * Lives beside [HistoryManager] and follows the same shape (SharedPreferences + a `StateFlow`) so
 * the search box can offer what the reader searched before without an account or a network call.
 * A term is stored once - repeating a search moves it back to the top instead of duplicating it -
 * and the list is capped so it cannot grow without bound.
 */
class SearchHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_search_history", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    /** Records a term, newest first. Blank input is ignored. */
    fun record(query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        _entries.update { current -> mergeEntry(current, term, MAX_ENTRIES) }
        persist()
    }

    fun remove(query: String) {
        _entries.update { current -> current.filterNot { it.equals(query, ignoreCase = true) } }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }

    private fun persist() {
        // Newline-joined: a search term can never contain one, so this needs no escaping.
        prefs.edit().putString(KEY, _entries.value.joinToString("\n")).apply()
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(MAX_ENTRIES)
            .orEmpty()

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 20
    }
}

/**
 * Puts [term] at the front of [current], dropping any earlier copy of it and capping the length.
 *
 * Split out from the manager so the behaviour can be tested without an Android `Context`:
 * searching the same words again must *move* the entry rather than adding a duplicate, and
 * matching is case-insensitive so "MANA" and "mana" are one entry.
 */
internal fun mergeEntry(current: List<String>, term: String, max: Int): List<String> =
    (listOf(term) + current.filterNot { it.equals(term, ignoreCase = true) }).take(max)
