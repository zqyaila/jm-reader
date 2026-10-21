package com.jm.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plaques and filled cells pair a fill with a foreground, and the pair has to survive both the
 * light and the dark scheme. Two rounds of "the text is invisible" were caused by taking those two
 * colours from unrelated roles (`background` for one, a hard-coded white for the other) and having
 * them coincide. This pins the actual pairs the UI uses.
 */
class ArchivePaletteTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(scheme: ColorScheme, name: String) {
        // ArchiveCode's inverted plaque.
        assertTrue(
            "$name: onSurface on surface is ${
                "%.2f".format(contrast(scheme.onSurface, scheme.surface))
            }:1",
            contrast(scheme.onSurface, scheme.surface) >= 7.0,
        )
        // Filled primary cells (check-in day, comic rank badge) and anything drawn on them.
        assertTrue(
            "$name: primary / onPrimary contrast is too low",
            contrast(scheme.primary, scheme.onPrimary) >= 4.5,
        )
        // Body text.
        assertTrue(
            "$name: body text contrast is too low",
            contrast(scheme.onBackground, scheme.background) >= 7.0,
        )
    }

    @Test
    fun archiveLightIsLegible() = check(ArchiveLightColors, "archive/light")

    @Test
    fun archiveDarkIsLegible() = check(ArchiveDarkColors, "archive/dark")

    @Test
    fun emberLightIsLegible() = check(EmberLightColors, "ember/light")

    @Test
    fun emberDarkIsLegible() = check(EmberDarkColors, "ember/dark")
}
