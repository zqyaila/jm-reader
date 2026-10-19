package com.jm.reader.desktop.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The desktop "guess you like" uses the same weighted tag pick as the phone, so it gets the same
 * guarantees: heavier tags are more likely, every positive tag is reachable, and an empty profile
 * yields no pick instead of a crash.
 */
class GuessTest {

    @Test
    fun `empty weights produce no tag`() {
        assertNull(Guess.pickTag(emptyMap()))
        assertNull(Guess.pickTag(mapOf("a" to 0)))
    }

    @Test
    fun `picks follow the weights`() {
        val random = Random(42)
        val picks = (0 until 20_000).map { Guess.pickTag(mapOf("heavy" to 80, "light" to 20), random) }
        val heavy = picks.count { it == "heavy" } / picks.size.toFloat()
        assertTrue(heavy in 0.74f..0.86f, "heavy share was $heavy")
    }

    @Test
    fun `every positive tag is reachable`() {
        val random = Random(7)
        val seen = mutableSetOf<String>()
        repeat(2000) { Guess.pickTag(mapOf("a" to 1, "b" to 1, "c" to 1), random)?.let(seen::add) }
        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun `ranking and share are stable`() {
        assertEquals(listOf("c", "a", "b"), Guess.rankedTags(mapOf("b" to 1, "a" to 1, "c" to 5), 3))
        assertEquals(0f, Guess.share(3, 0))
        assertEquals(0.5f, Guess.share(5, 10))
    }
}
