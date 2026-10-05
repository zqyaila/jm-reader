package com.jm.reader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guard for the liquid-glass layer.
 *
 * The design follows the **skill-liquid-glass** spec, whose fog is only 15 %. This app scrolls
 * full-bleed white cover art underneath its glass and Compose has no `vibrancy`, so the fog is
 * raised - and these tests prove the raised values keep WCAG AA contrast over the *worst possible*
 * backdrop (pure white and pure black), not just over the app's own canvas.
 *
 * They also pin the spec tokens and catch the two regressions this feature already had:
 * a see-through page canvas, and a fog thin enough to lose the text on it.
 */
class GlassContrastTest {

    // --- WCAG helpers -------------------------------------------------------

    private fun channel(v: Float): Double {
        val s = v.toDouble()
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Standard source-over compositing of a translucent [src] on top of [dst]. */
    private fun over(src: Color, dst: Color): Color {
        val a = src.alpha
        return Color(
            red = src.red * a + dst.red * (1f - a),
            green = src.green * a + dst.green * (1f - a),
            blue = src.blue * a + dst.blue * (1f - a),
            alpha = 1f,
        )
    }

    private fun darkFog(forBar: Boolean): Color =
        GlassFogDark.copy(alpha = if (forBar) GlassBarFogAlphaDark else GlassPanelFogAlphaDark)

    private fun lightFog(forBar: Boolean): Color =
        GlassFogLight.copy(alpha = if (forBar) GlassBarFogAlphaLight else GlassPanelFogAlphaLight)

    /** Backdrops a pane can sit on, worst cases first: a white page, a black page, mid grey. */
    private val worstBackdrops = listOf(
        Color.White,
        Color.Black,
        Color(0xFF808080),
        Color(0xFF1B1220), // the app's own backdrop gradient
    )

    // --- spec tokens --------------------------------------------------------

    @Test
    fun `glass tokens match the skill spec`() {
        assertEquals(2f, GlassBlurDp)
        assertEquals(12f, GlassLensRefractionDp)
        assertEquals(24f, GlassLensDistortionDp)
        assertEquals(0.15f, GlassHighlightAlpha)
        assertEquals(0.08f, GlassShadowAlpha)
        assertEquals(4f, GlassInnerShadowDp)
        assertEquals(0.10f, GlassInnerShadowAlpha)
    }

    @Test
    fun `fog stays thin enough to read the backdrop through`() {
        // Upper bound: a nearly opaque pane would stop being glass.
        assertTrue(GlassBarFogAlphaDark < 0.85f)
        assertTrue(GlassPanelFogAlphaDark < 0.85f)
        assertTrue(GlassBarFogAlphaLight < 0.85f)
        assertTrue(GlassPanelFogAlphaLight < 0.85f)
        // Lower bound: below this the text loses AA over a white backdrop (asserted next).
        assertTrue(GlassBarFogAlphaDark >= 0.75f)
        assertTrue(GlassPanelFogAlphaDark >= 0.70f)
    }

    // --- the actual guarantee ----------------------------------------------

    @Test
    fun `dark glass text keeps AA over any backdrop`() {
        for (bg in worstBackdrops) {
            for (forBar in listOf(true, false)) {
                val pane = over(darkFog(forBar), bg)
                assertTrue(
                    "body text, bar=$forBar, backdrop=$bg: ${ratio(DarkColors.onSurface, pane)}",
                    ratio(DarkColors.onSurface, pane) >= 4.5,
                )
                assertTrue(
                    "hint text, bar=$forBar, backdrop=$bg: ${ratio(DarkColors.onSurfaceVariant, pane)}",
                    ratio(DarkColors.onSurfaceVariant, pane) >= 4.5,
                )
            }
        }
    }

    @Test
    fun `light glass text keeps AA over any backdrop`() {
        for (bg in worstBackdrops) {
            for (forBar in listOf(true, false)) {
                val pane = over(lightFog(forBar), bg)
                assertTrue(
                    "body text, bar=$forBar, backdrop=$bg: ${ratio(LightColors.onSurface, pane)}",
                    ratio(LightColors.onSurface, pane) >= 4.5,
                )
                assertTrue(
                    "hint text, bar=$forBar, backdrop=$bg: ${ratio(LightColors.onSurfaceVariant, pane)}",
                    ratio(LightColors.onSurfaceVariant, pane) >= 4.5,
                )
            }
        }
    }

    @Test
    fun `navigation accents keep AA on their own glass`() {
        // The selected item is a *solid* accent pill, so its label contrast does not depend on what
        // the glass sampled. These are the exact pairs MainScreen uses.
        assertTrue(
            "dark selected nav (on #121212): ${ratio(BrandOrangeSoft, DarkBg)}",
            ratio(BrandOrangeSoft, DarkBg) >= 4.5,
        )
        assertTrue(
            "light selected nav (on white): ${ratio(BrandOrangeDeep, Color.White)}",
            ratio(BrandOrangeDeep, Color.White) >= 4.5,
        )
        // Unselected labels sit on the glass itself.
        for (bg in worstBackdrops) {
            val darkBar = over(darkFog(true), bg)
            assertTrue(
                "dark unselected nav label on $bg: ${ratio(DarkColors.onSurfaceVariant, darkBar)}",
                ratio(DarkColors.onSurfaceVariant, darkBar) >= 4.5,
            )
            val lightBar = over(lightFog(true), bg)
            assertTrue(
                "light unselected nav label on $bg: ${ratio(LightColors.onSurfaceVariant, lightBar)}",
                ratio(LightColors.onSurfaceVariant, lightBar) >= 4.5,
            )
        }
    }

    @Test
    fun `page canvas keeps AA with the backdrop behind it`() {
        val backdrops = listOf(Color(0xFF1B1220), Color(0xFF0A1418), Color(0xFF7C4DFF))
        for (bg in backdrops) {
            val darkCanvas = over(DarkColors.background, bg)
            assertTrue(
                "dark canvas body text on $bg: ${ratio(DarkColors.onSurface, darkCanvas)}",
                ratio(DarkColors.onSurface, darkCanvas) >= 4.5,
            )
            val lightCanvas = over(LightColors.background, bg)
            assertTrue(
                "light canvas body text on $bg: ${ratio(LightColors.onSurface, lightCanvas)}",
                ratio(LightColors.onSurface, lightCanvas) >= 4.5,
            )
        }
    }

    @Test
    fun `page canvas is translucent enough for glass to have something to refract`() {
        assertTrue("dark canvas too opaque", DarkColors.background.alpha <= 0.88f)
        assertTrue("light canvas too opaque", LightColors.background.alpha <= 0.92f)
        assertTrue("canvas must still carry text", DarkColors.background.alpha >= 0.70f)
        assertTrue("backdrop blobs must stay visible", BackdropBlobAlpha in 0.15f..0.45f)
    }
}
