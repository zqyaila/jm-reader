package com.jm.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bank is a projection, so its numbers have to be the reference's exactly. These are the
 * values Wenku8_Flutter's `ArchiveShelfGeometry` produces for a 360 dp phone viewport; if any of
 * them drift the row stops reading as a physical shelf and starts looking like a crooked carousel.
 */
class ShelfGeometryTest {

    private val width = 360f
    private val spine = shelfSpine(width)
    private val cover = shelfCover(width)
    private val body = shelfBodyHeight(cover)

    @Test
    fun spinePitchAndCoverFollowTheReferenceFormula() {
        assertEquals(15.48f, spine, 0.001f)
        assertEquals(26.48f, shelfPitch(width), 0.001f)
        assertEquals(115.2f, cover, 0.001f)
        assertEquals(187.93f, body, 0.01f)
    }

    @Test
    fun narrowAndWideViewportsClampTheBookProportions() {
        assertEquals(14f, shelfSpine(200f), 0.001f)
        assertEquals(18f, shelfSpine(900f), 0.001f)
        assertEquals(100f, shelfCover(200f), 0.001f)
        assertEquals(128f, shelfCover(900f), 0.001f)
    }

    @Test
    fun weightIsOneAtTheCentreAndZeroAtTheRadius() {
        assertEquals(1f, shelfWeight(0f), 0.0001f)
        assertEquals(0.5f, shelfWeight(0.74f), 0.0005f)
        assertEquals(0f, shelfWeight(1.48f), 0.0001f)
        assertEquals(0f, shelfWeight(2.5f), 0.0001f)
    }

    @Test
    fun railSlopesWithTheScrollDirection() {
        assertEquals(0f, railY(0f), 0.0001f)
        // d·2.5 + d²·0.45: the right-hand neighbour sits lower, the left-hand one slightly higher.
        // That asymmetry is the "wave" - a symmetric lift would read as a carousel again.
        assertEquals(2.95f, railY(1f), 0.001f)
        assertEquals(-2.05f, railY(-1f), 0.001f)
        // The rail clamps at seven books, so the virtual loop never launches a volume into orbit.
        assertEquals(railY(7f), railY(30f), 0.0001f)
    }

    @Test
    fun theCentreFileIsPulledOutAndLifted() {
        val resting = origin(weight = 0f)
        assertEquals(0f, resting.x, 0.001f)
        assertEquals(159.32f, resting.y, 0.01f)

        val extracted = origin(weight = 1f)
        // pull = 0.84 · 115.2 = 96.77, projected back by 0.66 on x and -0.30 on y, minus the
        // 0.60·height lift.
        assertEquals(-63.87f, extracted.x, 0.02f)
        assertEquals(75.59f, extracted.y, 0.02f)
    }

    @Test
    fun rowHeightHoldsTheFloorShadowAndTheExtractedCover() {
        assertEquals(407.25f, shelfRowHeight(cover, body), 0.05f)
        // The extracted cover must still end above the floor line.
        val extracted = origin(weight = 1f)
        assert(extracted.y + body < shelfBaseY(cover, body) + body)
    }

    private fun railY(distance: Float): Float = shelfRailY(distance)

    private fun origin(weight: Float) = shelfOrigin(cover, body, weight)
}
