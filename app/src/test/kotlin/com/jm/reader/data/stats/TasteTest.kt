package com.jm.reader.data.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The taste weight is what decides which tags "guess you like" draws from, so these tests pin the
 * two properties the old page-based scoring got wrong:
 *
 *  - a finished short work must outweigh a 2 % sample of a long one;
 *  - reading time must matter, but not without bound.
 */
class TasteTest {

    @Test
    fun `completion is pages read over the work's size`() {
        assertEquals(0.5f, Taste.completion(50, 100, 1, 2), 0.001f)
        // Never above 1, even if a series response reported fewer pages than were read.
        assertEquals(1f, Taste.completion(120, 100, 1, 1), 0.001f)
    }

    @Test
    fun `completion falls back to chapters when pages are unknown`() {
        assertEquals(3f / 48f, Taste.completion(20, 0, 3, 48), 0.001f)
    }

    @Test
    fun `completion is neutral when the work's size is unknown`() {
        assertEquals(0.5f, Taste.completion(7, 0, 1, 0), 0.001f)
        assertEquals(0f, Taste.completion(0, 0, 0, 0), 0.001f)
    }

    @Test
    fun `time factor grows and is capped`() {
        assertEquals(1f, Taste.timeFactor(0), 0.001f)
        // An hour is the reference point: the factor is ~2 there.
        assertEquals(2f, Taste.timeFactor(60 * 60_000L), 0.02f)
        assertTrue(Taste.timeFactor(10 * 60 * 60_000L) <= 2.5f)
        assertTrue(Taste.timeFactor(30 * 60_000L) > Taste.timeFactor(5 * 60_000L))
    }

    @Test
    fun `finishing a short work beats sampling a long one`() {
        val finishedShort = Taste.weightPoints(
            completion = Taste.completion(60, 60, 1, 1),
            millis = 30 * 60_000L,
            verdict = null,
        )
        val sampledLong = Taste.weightPoints(
            completion = Taste.completion(20, 900, 1, 48),
            millis = 30 * 60_000L,
            verdict = null,
        )
        assertTrue("finished=$finishedShort sampled=$sampledLong", finishedShort > sampledLong * 10)
    }

    @Test
    fun `verdict scales the weight`() {
        val base = Taste.weightPoints(0.8f, 20 * 60_000L, null)
        val liked = Taste.weightPoints(0.8f, 20 * 60_000L, Verdict.LIKED)
        val disliked = Taste.weightPoints(0.8f, 20 * 60_000L, Verdict.DISLIKED)
        assertTrue(liked > base)
        assertTrue(disliked < base / 2)
        // A disliked work still contributes something: it is one tap, not an eraser.
        assertTrue(disliked >= 1)
    }

    @Test
    fun `nothing read scores nothing`() {
        assertEquals(0, Taste.weightPoints(0f, 60 * 60_000L, Verdict.LIKED))
    }
}
