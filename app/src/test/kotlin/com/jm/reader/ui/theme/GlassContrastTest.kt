package com.jm.reader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contrast guard for the frosted-glass layer.
 *
 * The first version of the glass UI made icons and text unreadable: the page canvas was only 45%
 * opaque, several screens used a fully transparent `Scaffold`, the panel/bar fills were
 * translucent *white washes* (which brighten a dark theme) and the backdrop blobs were drawn at
 * 45% alpha. This test locks in the fix by asserting WCAG contrast ratios for the real colour
 * pairs the UI uses, including the worst-case backdrop underneath a translucent scrim.
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

    /** Darkest / brightest backdrop samples a scrim may sit on. */
    private val darkBackdropWorst = Color(0xFF1A1412)
    private val lightBackdropWorst = Color(0xFFFFF3E8)

    /** Backdrop with the decorative orange blob at full strength (the worst case for contrast). */
    private val darkBackdropWithBlob: Color =
        over(BrandOrange.copy(alpha = BackdropBlobAlpha), darkBackdropWorst)

    // --- assertions ---------------------------------------------------------

    @Test
    fun `scrims are opaque enough to carry text`() {
        // These thresholds are the actual fix: a wash thin enough to see the art is a wash thin
        // enough to lose the text.
        assertTrue("panel scrim too transparent", GlassScrimDark.alpha >= 0.85f)
        assertTrue("panel scrim too transparent", GlassScrimLight.alpha >= 0.85f)
        assertTrue("bar scrim too transparent", GlassBarScrimDark.alpha >= 0.75f)
        assertTrue("bar scrim too transparent", GlassBarScrimLight.alpha >= 0.75f)
    }

    @Test
    fun `page canvas stays near opaque`() {
        assertTrue("dark canvas must not be see-through", DarkColors.background.alpha >= 0.85f)
        assertTrue("light canvas must not be see-through", LightColors.background.alpha >= 0.85f)
        assertTrue("dark surface must not be see-through", DarkColors.surface.alpha >= 0.85f)
        assertTrue("light surface must not be see-through", LightColors.surface.alpha >= 0.85f)
    }

    @Test
    fun `dark theme text on glass panels keeps AA contrast`() {
        val panel = over(GlassScrimDark, darkBackdropWithBlob)
        assertTrue(
            "primary text on a dark glass panel: ${ratio(DarkColors.onSurface, panel)}",
            ratio(DarkColors.onSurface, panel) >= 4.5,
        )
        assertTrue(
            "secondary text on a dark glass panel: ${ratio(DarkColors.onSurfaceVariant, panel)}",
            ratio(DarkColors.onSurfaceVariant, panel) >= 4.5,
        )
    }

    @Test
    fun `dark theme text on glass bars keeps AA contrast`() {
        val bar = over(GlassBarScrimDark, darkBackdropWithBlob)
        assertTrue(
            "primary text on the nav bar: ${ratio(DarkColors.onSurface, bar)}",
            ratio(DarkColors.onSurface, bar) >= 4.5,
        )
        assertTrue(
            "unselected nav labels: ${ratio(DarkColors.onSurfaceVariant, bar)}",
            ratio(DarkColors.onSurfaceVariant, bar) >= 4.5,
        )
        // Icons only need 3:1 (WCAG non-text contrast).
        assertTrue(
            "selected nav icon: ${ratio(DarkColors.primary, bar)}",
            ratio(DarkColors.primary, bar) >= 3.0,
        )
    }

    @Test
    fun `light theme text on glass panels keeps AA contrast`() {
        val panel = over(GlassScrimLight, lightBackdropWorst)
        assertTrue(
            "primary text on a light glass panel: ${ratio(LightColors.onSurface, panel)}",
            ratio(LightColors.onSurface, panel) >= 4.5,
        )
        assertTrue(
            "secondary text on a light glass panel: ${ratio(LightColors.onSurfaceVariant, panel)}",
            ratio(LightColors.onSurfaceVariant, panel) >= 4.5,
        )
    }

    @Test
    fun `light theme text on glass bars keeps AA contrast`() {
        val bar = over(GlassBarScrimLight, lightBackdropWorst)
        assertTrue(
            "primary text on the light nav bar: ${ratio(LightColors.onSurface, bar)}",
            ratio(LightColors.onSurface, bar) >= 4.5,
        )
        assertTrue(
            "unselected nav labels: ${ratio(LightColors.onSurfaceVariant, bar)}",
            ratio(LightColors.onSurfaceVariant, bar) >= 4.5,
        )
    }

    @Test
    fun `text on the page canvas keeps AA contrast`() {
        assertTrue(
            "body text on the dark canvas: ${ratio(DarkColors.onSurface, DarkColors.background)}",
            ratio(DarkColors.onSurface, DarkColors.background) >= 4.5,
        )
        assertTrue(
            "secondary text on the dark canvas: ${ratio(DarkColors.onSurfaceVariant, DarkColors.background)}",
            ratio(DarkColors.onSurfaceVariant, DarkColors.background) >= 4.5,
        )
        assertTrue(
            "body text on the light canvas: ${ratio(LightColors.onSurface, LightColors.background)}",
            ratio(LightColors.onSurface, LightColors.background) >= 4.5,
        )
    }

    @Test
    fun `the decorative blob stays dim`() {
        assertTrue("backdrop blobs must stay decoration, not light source", BackdropBlobAlpha <= 0.25f)
    }
}
