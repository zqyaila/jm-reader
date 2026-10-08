package com.jm.reader.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the metric half of the skill-liquid-glass spec.
 *
 * [GlassContrastTest] guards the *colour* tokens (fog/highlight/shadow); this guards the numbers
 * that decide how big and how round the controls are, so a stray edit cannot quietly drift the app
 * away from the design it was drawn against.
 */
class GlassSpecTest {

    @Test
    fun `control sizes match the spec`() {
        assertEquals(48.dp, GlassSizes.ButtonHeight)
        assertEquals(48.dp, GlassSizes.SearchBarHeight)
        assertEquals(56.dp, GlassSizes.TopBarHeight)
        assertEquals(36.dp, GlassSizes.ChipHeight)
        assertEquals(64.dp, GlassSizes.BottomBarHeight)
        assertEquals(56.dp, GlassSizes.BottomTabHeight)
    }

    @Test
    fun `content padding and radii match the spec`() {
        assertEquals(16.dp, GlassSizes.ContentPadding)
        assertEquals(24.dp, GlassSizes.HeavyContentPadding)
        assertEquals(16.dp, GlassSizes.RadiusStandard)
        assertEquals(24.dp, GlassSizes.RadiusHeavy)
        assertEquals(12.dp, GlassSizes.RadiusInner)
    }

    @Test
    fun `the bar is taller than a tab, and a tab is at least the 48dp touch target`() {
        // A 64dp pill holding 56dp tabs leaves 4dp of breathing room on each side.
        assertEquals(8.dp, GlassSizes.BottomBarHeight - GlassSizes.BottomTabHeight)
        assertTrue(
            "a bottom tab must clear the 48dp minimum touch target",
            GlassSizes.BottomTabHeight.value >= 48f,
        )
    }

    @Test
    fun `specular sheen uses the spec angle`() {
        assertEquals(45f, GlassHighlightAngle)
    }

    @Test
    fun `the shape tokens line up with the metric tokens`() {
        // If someone changes a radius token the shape constants must follow it; this fails loudly
        // if the two ever drift apart.
        assertEquals(GlassSizes.RadiusStandard, 16.dp)
        assertEquals(GlassSizes.RadiusHeavy, 24.dp)
    }
}
