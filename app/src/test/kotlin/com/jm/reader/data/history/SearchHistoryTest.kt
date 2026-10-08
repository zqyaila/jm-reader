package com.jm.reader.data.history

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The "recent searches" list has to behave predictably: repeating a search moves it to the top
 * instead of adding a duplicate, casing does not create a second entry, and the list is capped.
 */
class SearchHistoryTest {

    @Test
    fun `a new term goes to the front`() {
        assertEquals(listOf("b", "a"), mergeEntry(listOf("a"), "b", max = 20))
    }

    @Test
    fun `repeating a term moves it instead of duplicating it`() {
        // "a" jumps to the front; the rest keep their relative order.
        assertEquals(listOf("a", "b", "c"), mergeEntry(listOf("b", "a", "c"), "a", max = 20))
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf("mana", "other"), mergeEntry(listOf("MANA", "other"), "mana", max = 20))
        assertEquals(listOf("MANA", "other"), mergeEntry(listOf("mana", "other"), "MANA", max = 20))
    }

    @Test
    fun `the list is capped, dropping the oldest`() {
        val current = listOf("c", "b", "a")
        assertEquals(listOf("d", "c", "b"), mergeEntry(current, "d", max = 3))
    }

    @Test
    fun `recording into an empty list works`() {
        assertEquals(listOf("first"), mergeEntry(emptyList(), "first", max = 20))
    }
}
