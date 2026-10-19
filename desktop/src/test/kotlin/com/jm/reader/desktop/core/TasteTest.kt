package com.jm.reader.desktop.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Same guarantees as the Android `Taste` tests: the desktop client must score reading the same way,
 * otherwise the two "guess you like" buttons would drift apart.
 */
class TasteTest {

    @Test
    fun `completion is pages over size and never above one`() {
        assertEquals(0.5f, Taste.completion(50, 100, 1, 2), 0.001f)
        assertEquals(1f, Taste.completion(120, 100, 1, 1), 0.001f)
    }

    @Test
    fun `completion falls back to chapters then to neutral`() {
        assertEquals(3f / 48f, Taste.completion(20, 0, 3, 48), 0.001f)
        assertEquals(0.5f, Taste.completion(7, 0, 1, 0), 0.001f)
    }

    @Test
    fun `time factor is bounded and monotonic`() {
        assertEquals(1f, Taste.timeFactor(0), 0.001f)
        assertEquals(2f, Taste.timeFactor(60 * 60_000L), 0.02f)
        assertTrue(Taste.timeFactor(10 * 60 * 60_000L) <= 2.5f)
    }

    @Test
    fun `a finished short work outweighs a sample of a long one`() {
        val finished = Taste.weightPoints(Taste.completion(60, 60, 1, 1), 30 * 60_000L, null)
        val sampled = Taste.weightPoints(Taste.completion(20, 900, 1, 48), 30 * 60_000L, null)
        assertTrue(finished > sampled * 10)
    }

    @Test
    fun `verdict scales the weight`() {
        val base = Taste.weightPoints(0.8f, 20 * 60_000L, null)
        assertTrue(Taste.weightPoints(0.8f, 20 * 60_000L, Verdict.LIKED) > base)
        assertTrue(Taste.weightPoints(0.8f, 20 * 60_000L, Verdict.DISLIKED) < base / 2)
    }
}
