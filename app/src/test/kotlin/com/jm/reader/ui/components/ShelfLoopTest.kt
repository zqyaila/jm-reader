package com.jm.reader.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bank is a loop, so a fling may leave `focus` far outside `0 until count`. Re-seating it has
 * to be visually identical: subtracting a whole lap must land on the same book.
 */
class ShelfLoopTest {

    @Test
    fun focusWrapsInBothDirections() {
        assertEquals(0f, shelfWrapFocus(10f, 10), 0.0001f)
        assertEquals(9f, shelfWrapFocus(-1f, 10), 0.0001f)
        assertEquals(2.3f, shelfWrapFocus(12.3f, 10), 0.0001f)
        assertEquals(7.3f, shelfWrapFocus(-2.7f, 10), 0.0001f)
        assertEquals(4f, shelfWrapFocus(4f, 10), 0.0001f)
    }

    @Test
    fun degeneratesSafelyWithoutItems() {
        assertEquals(3f, shelfWrapFocus(3f, 0), 0.0001f)
    }
}
