package com.jm.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The diagonal cut has to be *exactly* full-width at 1.0 and empty at 0, or the page either never
 * finishes arriving or leaves a sliver of the previous screen visible for the rest of the session.
 */
class PlaneRevealTest {

    private val width = 1080f
    private val slantMax = 540f

    @Test
    fun landedEdgeCoversTheWholePage() {
        val slant = planeSlant(width, slantMax)
        // The trailing corner (edge - slant) is what actually reaches the right edge.
        assertEquals(width, planeEdge(1f, width, slant) - slant, 0.001f)
    }

    @Test
    fun nothingIsPaintedBeforeTheSheetStarts() {
        assertEquals(0f, planeEdge(0f, width, planeSlant(width, slantMax)), 0.001f)
    }

    @Test
    fun edgeAdvancesMonotonically() {
        val slant = planeSlant(width, slantMax)
        var previous = -1f
        listOf(0f, 0.15f, 0.4f, 0.75f, 1f).forEach { p ->
            val edge = planeEdge(p, width, slant)
            assertTrue("edge must not move backwards", edge >= previous)
            previous = edge
        }
    }

    @Test
    fun slantIsCappedOnWideCanvases() {
        assertEquals(540f, planeSlant(4000f, slantMax), 0.001f)
        assertEquals(230f, planeSlant(500f, slantMax), 0.001f)
    }
}
