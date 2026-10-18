package com.jm.reader.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The "guess you like" dice must be *weighted*, not merely random - otherwise the local stats would
 * be decorative. These tests pin the two properties that matter:
 *
 *  - a heavier tag is picked more often, in roughly the ratio of its weight;
 *  - an empty profile yields no pick, so the UI can fall back instead of crashing.
 */
class GuessEngineTest {

    @Test
    fun `empty weights produce no tag`() {
        assertNull(GuessEngine.pickTag(emptyMap()))
        assertNull(GuessEngine.pickTag(mapOf("a" to 0, "b" to 0)))
    }

    @Test
    fun `picks follow the weights`() {
        val counts = mapOf("heavy" to 80, "light" to 20)
        val random = Random(42)
        val picks = (0 until 20_000).map { GuessEngine.pickTag(counts, random) }
        val heavy = picks.count { it == "heavy" } / picks.size.toFloat()
        // 80/20 -> allow a generous band; a broken implementation lands near 0.5 or 1.0.
        assertTrue("heavy share was $heavy", heavy in 0.74f..0.86f)
    }

    @Test
    fun `every positive tag is reachable`() {
        val counts = mapOf("a" to 1, "b" to 1, "c" to 1)
        val seen = mutableSetOf<String>()
        val random = Random(7)
        repeat(2000) { GuessEngine.pickTag(counts, random)?.let(seen::add) }
        assertEquals(setOf("a", "b", "c"), seen)
    }

    @Test
    fun `ranking is by weight then name`() {
        val ranked = GuessEngine.rankedTags(mapOf("b" to 1, "a" to 1, "c" to 5), limit = 3)
        assertEquals(listOf("c", "a", "b"), ranked)
    }

    @Test
    fun `share is a fraction of the total and never divides by zero`() {
        assertEquals(0f, GuessEngine.share(3, 0))
        assertEquals(0.5f, GuessEngine.share(5, 10))
        assertEquals(1f, GuessEngine.share(10, 5))
    }
}
