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
 * full-bleed white cover art underneath its *bottom bar* and Compose has no `vibrancy`, so the fog
 * is raised - and these tests prove each surface kind keeps WCAG AA over the worst backdrop it can
 * actually meet:
 *
 *  - **bottom bar** → pure white / black cover art scrolling underneath,
 *  - **top bar / panels** → the app canvas with the brightest backdrop blob behind it,
 *  - **canvas text** → that same brightest blob.
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

    private fun over(src: Color, dst: Color): Color {
        val a = src.alpha
        return Color(
            red = src.red * a + dst.red * (1f - a),
            green = src.green * a + dst.green * (1f - a),
            blue = src.blue * a + dst.blue * (1f - a),
            alpha = 1f,
        )
    }

    private fun fog(kind: GlassKind, dark: Boolean): Color {
        val base = if (dark) GlassFogDark else GlassFogLight
        val alpha = when (kind) {
            GlassKind.Panel -> if (dark) GlassPanelFogAlphaDark else GlassPanelFogAlphaLight
            GlassKind.TopBar -> if (dark) GlassTopBarFogAlphaDark else GlassTopBarFogAlphaLight
            GlassKind.BottomBar ->
                if (dark) GlassBottomBarFogAlphaDark else GlassBottomBarFogAlphaLight
        }
        return base.copy(alpha = alpha)
    }

    /** Cover art a bottom bar can meet while the list scrolls. */
    private val artBackdrops = listOf(Color.White, Color.Black, Color(0xFF808080))

    /** Brightest realistic backdrop, i.e. the orange blob at full strength over the gradient. */
    private val darkBrightest = over(BrandOrange.copy(alpha = BackdropBlobAlpha), Color(0xFF1B1220))
    private val lightBrightest = over(BrandOrange.copy(alpha = BackdropBlobAlpha), Color(0xFFFFF0E2))

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
    fun `fog is graded by what a surface can meet`() {
        // The bottom bar needs the densest fog; panes over the canvas are the thinnest.
        assertTrue(GlassBottomBarFogAlphaDark > GlassTopBarFogAlphaDark)
        assertTrue(GlassTopBarFogAlphaDark > GlassPanelFogAlphaDark)
        // Bars must clear the white-art case (asserted below); panels must still look like glass.
        assertTrue(GlassBottomBarFogAlphaDark >= 0.75f)
        assertTrue(GlassBottomBarFogAlphaLight >= 0.75f)
        assertTrue(GlassPanelFogAlphaDark <= 0.60f)
        assertTrue(GlassPanelFogAlphaLight <= 0.65f)
    }

    // --- the guarantees -----------------------------------------------------

    @Test
    fun `bottom bar text keeps AA over any cover art`() {
        for (bg in artBackdrops) {
            val darkBar = over(fog(GlassKind.BottomBar, dark = true), bg)
            assertTrue(
                "dark body text on $bg: ${ratio(DarkColors.onSurface, darkBar)}",
                ratio(DarkColors.onSurface, darkBar) >= 4.5,
            )
            assertTrue(
                "dark hint text on $bg: ${ratio(DarkColors.onSurfaceVariant, darkBar)}",
                ratio(DarkColors.onSurfaceVariant, darkBar) >= 4.5,
            )
            val lightBar = over(fog(GlassKind.BottomBar, dark = false), bg)
            assertTrue(
                "light body text on $bg: ${ratio(LightColors.onSurface, lightBar)}",
                ratio(LightColors.onSurface, lightBar) >= 4.5,
            )
            assertTrue(
                "light hint text on $bg: ${ratio(LightColors.onSurfaceVariant, lightBar)}",
                ratio(LightColors.onSurfaceVariant, lightBar) >= 4.5,
            )
        }
    }

    @Test
    fun `top bar and panel text keeps AA over the canvas`() {
        val darkCanvas = over(DarkColors.background, darkBrightest)
        val lightCanvas = over(LightColors.background, lightBrightest)
        for (kind in listOf(GlassKind.TopBar, GlassKind.Panel)) {
            val darkPane = over(fog(kind, true), darkCanvas)
            assertTrue(
                "$kind dark body text: ${ratio(DarkColors.onSurface, darkPane)}",
                ratio(DarkColors.onSurface, darkPane) >= 4.5,
            )
            assertTrue(
                "$kind dark hint text: ${ratio(DarkColors.onSurfaceVariant, darkPane)}",
                ratio(DarkColors.onSurfaceVariant, darkPane) >= 4.5,
            )
            val lightPane = over(fog(kind, false), lightCanvas)
            assertTrue(
                "$kind light body text: ${ratio(LightColors.onSurface, lightPane)}",
                ratio(LightColors.onSurface, lightPane) >= 4.5,
            )
            assertTrue(
                "$kind light hint text: ${ratio(LightColors.onSurfaceVariant, lightPane)}",
                ratio(LightColors.onSurfaceVariant, lightPane) >= 4.5,
            )
        }
    }

    @Test
    fun `canvas text keeps AA over the brightest backdrop blob`() {
        val darkCanvas = over(DarkColors.background, darkBrightest)
        assertTrue(
            "dark canvas body text: ${ratio(DarkColors.onSurface, darkCanvas)}",
            ratio(DarkColors.onSurface, darkCanvas) >= 4.5,
        )
        assertTrue(
            "dark canvas hint text: ${ratio(DarkColors.onSurfaceVariant, darkCanvas)}",
            ratio(DarkColors.onSurfaceVariant, darkCanvas) >= 4.5,
        )
        val lightCanvas = over(LightColors.background, lightBrightest)
        assertTrue(
            "light canvas body text: ${ratio(LightColors.onSurface, lightCanvas)}",
            ratio(LightColors.onSurface, lightCanvas) >= 4.5,
        )
        assertTrue(
            "light canvas hint text: ${ratio(LightColors.onSurfaceVariant, lightCanvas)}",
            ratio(LightColors.onSurfaceVariant, lightCanvas) >= 4.5,
        )
    }

    @Test
    fun `navigation accents keep AA on their solid pill`() {
        assertTrue(
            "dark selected nav (on #121212): ${ratio(BrandOrangeSoft, DarkBg)}",
            ratio(BrandOrangeSoft, DarkBg) >= 4.5,
        )
        assertTrue(
            "light selected nav (on white): ${ratio(BrandOrangeDeep, Color.White)}",
            ratio(BrandOrangeDeep, Color.White) >= 4.5,
        )
    }

    @Test
    fun `canvas stays translucent enough for the glass to have something to refract`() {
        assertTrue("dark canvas too opaque", DarkColors.background.alpha <= 0.75f)
        assertTrue("light canvas too opaque", LightColors.background.alpha <= 0.85f)
        assertTrue("canvas must still carry text", DarkColors.background.alpha >= 0.50f)
        assertTrue("backdrop blobs must stay visible", BackdropBlobAlpha in 0.15f..0.45f)
    }
}
