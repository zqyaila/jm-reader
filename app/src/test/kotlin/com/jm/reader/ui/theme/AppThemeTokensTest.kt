package com.jm.reader.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Material 3 **Expressive** half of the theme.
 *
 * [GlassSpecTest] guards the liquid-glass numbers and [GlassContrastTest] the glass colours; this
 * one guards the refactor that lifted the app off Material's stock type/shape scale. Those are the
 * tokens a reader *feels* rather than sees (a headline that stopped being bold, a chip that went
 * back to square), so they are exactly the kind of thing that regresses silently in a code review.
 */
class AppThemeTokensTest {

    // -----------------------------------------------------------------------
    // Type scale
    // -----------------------------------------------------------------------

    /** The intended order, biggest to smallest. Used by the monotonicity tests below. */
    private val orderedSizes = listOf(
        "displayLarge" to AppTypography.displayLarge,
        "displayMedium" to AppTypography.displayMedium,
        "displaySmall" to AppTypography.displaySmall,
        "headlineLarge" to AppTypography.headlineLarge,
        "headlineMedium" to AppTypography.headlineMedium,
        "headlineSmall" to AppTypography.headlineSmall,
        "titleLarge" to AppTypography.titleLarge,
        "titleMedium" to AppTypography.titleMedium,
        "titleSmall" to AppTypography.titleSmall,
        "bodyLarge" to AppTypography.bodyLarge,
        "bodyMedium" to AppTypography.bodyMedium,
        "bodySmall" to AppTypography.bodySmall,
        "labelLarge" to AppTypography.labelLarge,
        "labelMedium" to AppTypography.labelMedium,
        "labelSmall" to AppTypography.labelSmall,
    )

    @Test
    fun `the type scale descends monotonically`() {
        // A stray edit that makes bodyMedium bigger than titleSmall would invert the hierarchy and
        // make section titles look like captions.
        // `zipWithNext`'s two-argument transform lets each Pair be destructured in place.
        orderedSizes.zipWithNext { (biggerName, bigger), (smallerName, smaller) ->
            assertTrue(
                "expected $biggerName (${bigger.fontSize}) to be larger than " +
                    "$smallerName (${smaller.fontSize})",
                bigger.fontSize.value > smaller.fontSize.value,
            )
        }
    }

    @Test
    fun `every style declares a line height that cannot clip its own glyphs`() {
        orderedSizes.forEach { (name, style) ->
            val lineHeight = style.lineHeight
            assertTrue(
                "$name must declare an explicit lineHeight",
                lineHeight != androidx.compose.ui.unit.TextUnit.Unspecified,
            )
            assertTrue(
                "$name lineHeight ($lineHeight) must be >= fontSize (${style.fontSize})",
                lineHeight.value >= style.fontSize.value,
            )
        }
    }

    @Test
    fun `headlines and titles are SemiBold, body is Regular`() {
        // This is the actual hierarchy fix vs. Material's stock scale (all-Regular headlines).
        assertEquals(FontWeight.SemiBold, AppTypography.headlineLarge.fontWeight)
        assertEquals(FontWeight.SemiBold, AppTypography.headlineMedium.fontWeight)
        assertEquals(FontWeight.SemiBold, AppTypography.headlineSmall.fontWeight)
        assertEquals(FontWeight.SemiBold, AppTypography.titleLarge.fontWeight)
        assertEquals(FontWeight.SemiBold, AppTypography.titleMedium.fontWeight)
        assertEquals(FontWeight.Normal, AppTypography.bodyLarge.fontWeight)
        assertEquals(FontWeight.Normal, AppTypography.bodyMedium.fontWeight)
    }

    @Test
    fun `labels are Medium so chips stay legible over artwork`() {
        // ComicCard's category and JM-number badges are `labelSmall` on a 60% black plate over a
        // cover; Regular weight gets lost there.
        assertEquals(FontWeight.Medium, AppTypography.labelLarge.fontWeight)
        assertEquals(FontWeight.Medium, AppTypography.labelMedium.fontWeight)
        assertEquals(FontWeight.Medium, AppTypography.labelSmall.fontWeight)
    }

    @Test
    fun `large text tracks negative and small text tracks positive`() {
        // The Expressive tracking rule. If this inverts, a 32sp headline looks gappy and an 11sp
        // label looks crowded.
        assertTrue(
            "headlineLarge should tighten",
            AppTypography.headlineLarge.letterSpacing.value < 0f,
        )
        assertTrue(
            "displayLarge should tighten",
            AppTypography.displayLarge.letterSpacing.value < 0f,
        )
        assertTrue(
            "bodyMedium should not tighten",
            AppTypography.bodyMedium.letterSpacing.value >= 0f,
        )
        assertTrue(
            "labelSmall should loosen",
            AppTypography.labelSmall.letterSpacing.value > 0f,
        )
    }

    @Test
    fun `the top bar title style is the one the app actually renders`() {
        // AppTopBar hardcodes `titleLarge`; pin the numbers so a change there shows up here.
        assertEquals(22.sp, AppTypography.titleLarge.fontSize)
        assertEquals(28.sp, AppTypography.titleLarge.lineHeight)
    }

    @Test
    fun `every style resolves to a single font family`() {
        // Guards the "AppFontFamily" switch point: if a style were left at the default with a
        // different family, swapping in Roboto Flex later would miss it.
        val families = orderedSizes.map { it.second.fontFamily }.toSet()
        assertEquals("all styles must share one font family", 1, families.size)
    }

    // -----------------------------------------------------------------------
    // Shapes
    // -----------------------------------------------------------------------

    @Test
    fun `the shape scale is anchored to the liquid-glass radii`() {
        // If these drift, a card nested in a card stops lining up with its own inner chip.
        assertEquals(CornerSize(GlassSizes.RadiusInner), AppShapes.small.topStart)
        assertEquals(CornerSize(GlassSizes.RadiusStandard), AppShapes.medium.topStart)
        assertEquals(CornerSize(GlassSizes.RadiusHeavy), AppShapes.large.topStart)
    }

    @Test
    fun `the shape scale is monotonically rounder`() {
        // Values pinned explicitly, which is stronger than mere monotonicity: the Expressive ladder
        // for a rounded app is 8 / 12 / 16 / 24 / 32.
        assertEquals(CornerSize(8.dp), AppShapes.extraSmall.topStart)
        assertEquals(CornerSize(12.dp), AppShapes.small.topStart)
        assertEquals(CornerSize(16.dp), AppShapes.medium.topStart)
        assertEquals(CornerSize(24.dp), AppShapes.large.topStart)
        assertEquals(CornerSize(32.dp), AppShapes.extraLarge.topStart)
    }

    @Test
    fun `every corner of a shape gets the same radius`() {
        // RoundedCornerShape can technically be built with four different corners; an accidental
        // asymmetric shape would show up as a lopsided card.
        listOf(
            "extraSmall" to AppShapes.extraSmall,
            "small" to AppShapes.small,
            "medium" to AppShapes.medium,
            "large" to AppShapes.large,
            "extraLarge" to AppShapes.extraLarge,
        ).forEach { (name, s) ->
            assertEquals("$name topEnd", s.topStart, s.topEnd)
            assertEquals("$name bottomStart", s.topStart, s.bottomStart)
            assertEquals("$name bottomEnd", s.topStart, s.bottomEnd)
        }
    }

    // -----------------------------------------------------------------------
    // Spacing
    // -----------------------------------------------------------------------

    @Test
    fun `the spacing ladder is monotonically increasing`() {
        val ladder = listOf(
            "Inline" to AppSpacing.Inline,
            "Tight" to AppSpacing.Tight,
            "CardInner" to AppSpacing.CardInner,
            "ScreenEdge" to AppSpacing.ScreenEdge,
            "Section" to AppSpacing.Section,
            "SectionHeaderTop" to AppSpacing.SectionHeaderTop,
        )
        ladder.zipWithNext { (smallerName, smaller), (biggerName, bigger) ->
            assertTrue(
                "expected $biggerName ($bigger) to exceed $smallerName ($smaller)",
                bigger.value > smaller.value,
            )
        }
    }

    @Test
    fun `spacing shares its anchors with the liquid-glass size tokens`() {
        // Two scales, one truth: a screen that mixes `AppSpacing.ScreenEdge` with
        // `GlassSizes.ContentPadding` must not get two different margins.
        assertEquals(GlassSizes.ContentPadding, AppSpacing.ScreenEdge)
        assertEquals(GlassSizes.HeavyContentPadding, AppSpacing.Section)
    }

    // -----------------------------------------------------------------------
    // Motion
    // -----------------------------------------------------------------------

    @Test
    fun `the nav indicator reuses the glass settle spring instead of inventing a second one`() {
        // Reference equality on purpose: `AppMotion.spatialFast` must *be* `GlassMotion.settle`,
        // not merely resemble it. Two lookalike springs drift apart on the next tuning pass.
        assertSame(GlassMotion.settle, AppMotion.spatialFast)
        assertSame(GlassMotion.drag, AppMotion.spatialSlow)
    }

    @Test
    fun `effects springs never overshoot`() {
        // An alpha/colour spring with dampingRatio < 1 overshoots past full opacity and flashes.
        assertEquals(1f, AppMotion.effectsFast.dampingRatio)
        assertEquals(1f, AppMotion.effectsDefault.dampingRatio)
        assertEquals(1f, AppMotion.spatialSlow.dampingRatio)
    }

    @Test
    fun `spatial springs may overshoot but stay controlled`() {
        val ratio = AppMotion.spatialDefault.dampingRatio
        assertTrue("spatialDefault should have some bounce", ratio < 1f)
        assertTrue("spatialDefault must not be wildly underdamped", ratio >= 0.5f)
        assertEquals(0.5f, AppMotion.spatialFast.dampingRatio)
    }
}
