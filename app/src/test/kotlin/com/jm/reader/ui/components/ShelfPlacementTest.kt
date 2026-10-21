package com.jm.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The row's geometry is dp; `place()` and the canvas are pixels.
 *
 * A build that forgot the final multiply laid every book on top of the last one, which looked like
 * a single white stack with one cover in front. These assertions keep the conversion honest.
 */
class ShelfPlacementTest {

    private val density = 3f
    private val startX = 122.6f
    private val pitch = 26.48f

    @Test
    fun neighboursAreOnePitchApartInPixels() {
        val a = shelfSlotPx(slot = 5, focus = 5f, startX = startX, pitch = pitch, originX = 0f, density = density)
        val b = shelfSlotPx(slot = 6, focus = 5f, startX = startX, pitch = pitch, originX = 0f, density = density)
        assertEquals(pitch * density, b - a, 0.001f)
    }

    @Test
    fun theCentredSlotSitsAtTheStartOfTheRow() {
        val centred = shelfSlotPx(slot = 5, focus = 5f, startX = startX, pitch = pitch, originX = 0f, density = density)
        assertEquals(startX * density, centred, 0.001f)
    }

    @Test
    fun densityScalesTheWholeRow() {
        val at1x = shelfSlotPx(12, 5f, startX, pitch, 4f, 1f)
        val at3x = shelfSlotPx(12, 5f, startX, pitch, 4f, 3f)
        assertEquals(at1x * 3f, at3x, 0.01f)
    }
}
