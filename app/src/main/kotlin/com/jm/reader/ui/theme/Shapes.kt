package com.jm.reader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 **Expressive** shape scale - "emphasized rounded", per the design doc.
 *
 * The scale is anchored to the three radii the liquid-glass spec already declared, so there is one
 * answer to "how round is a card?" rather than two competing scales:
 *
 *   small  (12dp) == [GlassSizes.RadiusInner]    - chips, thumbnails, inputs nested in a card
 *   medium (16dp) == [GlassSizes.RadiusStandard] - the everyday card
 *   large  (24dp) == [GlassSizes.RadiusHeavy]    - heroes, sheets, detail headers
 *
 * [extraLarge] (32dp) is only used by full-bleed expressive containers (bottom sheets); buttons and
 * bars are *capsules* rather than rounded rectangles - see [GlassCapsule] - which is why the small
 * end of this scale is not used for them.
 *
 * [GlassSpecTest] asserts the overlap with the size tokens, so the two scales cannot drift apart.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(10.dp),
)
