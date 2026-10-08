package com.jm.reader.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion + metric tokens of the **skill-liquid-glass** spec
 * (https://github.com/JUEMING-006/skill-liquid-glass, "统一参数规范（v2）").
 *
 * The colour/blur half of the spec lives in [Color.kt] and [Glass.kt]; this file holds the
 * dimensions and springs, so a single place answers "what does the spec say a button/bar/radius
 * is?" instead of those numbers being scattered through the screens.
 *
 * The spec is explicit that motion is **springs only** - it declares no tween durations at all.
 * [GlassPress] is the press/release spring from the spec's `GlassAnimationSpecs`; [GlassSettle] is
 * its scale spring.
 */
object GlassMotion {
    /** Press and release: `spring(0.5f, 300f, 0.001f)`. */
    val press: SpringSpec<Float> =
        spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)

    /** Scale/selection settle: `spring(0.6f, 250f, 0.001f)`. */
    val settle: SpringSpec<Float> =
        spring(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)

    /** Drag follow: `spring(1f, 1000f, 0.001f)` - critically damped, no overshoot. */
    val drag: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1000f,
        visibilityThreshold = 0.001f,
    )
}

/**
 * Spec dimensions. The spec gives a canonical size per control; using them keeps the app's touch
 * targets at the sizes the design was drawn for (and comfortably above the 48dp minimum for the
 * controls that act as buttons).
 */
object GlassSizes {
    /** `LiquidButton` height. */
    val ButtonHeight: Dp = 48.dp

    /** `LiquidSearchBar` height. */
    val SearchBarHeight: Dp = 48.dp

    /**
     * `LiquidTopBar` height in the spec.
     *
     * Deliberately **not** applied to this app's top bar: `TopAppBar` measures its own status-bar
     * inset, and forcing an outer height clips the title under the system bar. The app therefore
     * keeps Material's default height and the token stays here as the spec reference.
     */
    val TopBarHeight: Dp = 56.dp

    /** `LiquidFilterChip` height. */
    val ChipHeight: Dp = 36.dp

    /** `LiquidBottomTabs` container height. */
    val BottomBarHeight: Dp = 64.dp

    /** `LiquidBottomTabs` individual tab height. */
    val BottomTabHeight: Dp = 56.dp

    /** Standard content padding; the spec's de-facto default. */
    val ContentPadding: Dp = 16.dp

    /** Padding for the larger "heavy" cards. */
    val HeavyContentPadding: Dp = 24.dp

    /** Corner radius of a standard card. */
    val RadiusStandard: Dp = 16.dp

    /** Corner radius of a large/heavy card. */
    val RadiusHeavy: Dp = 24.dp

    /** Corner radius for elements nested inside a card (chips, thumbnails). */
    val RadiusInner: Dp = 12.dp
}

/**
 * Material 3 **Expressive** motion tokens.
 *
 * Two families, split by *what is moving* - this is the whole point of the Expressive motion
 * system, and it is what stops the app feeling either sluggish or twitchy:
 *
 *  - **spatial** (position, size, scale) may overshoot a little. A moving object with a bit of
 *    bounce reads as physical; that is what the app uses for the bottom-nav indicator and for
 *    expanding a card.
 *  - **effects** (colour, alpha) must *never* overshoot. A colour that overshoots passes through
 *    its clamp and flashes, so these are fast and critically damped.
 *
 * The values are deliberately the *same* springs the liquid-glass spec declares in [GlassMotion]
 * (press `spring(0.5, 300)`, settle `spring(0.6, 250)`), mapped onto the Expressive categories.
 * Having one motion language matters more here than matching Material's exact stock numbers: a
 * glass pane that dips on `spring(0.5, 300)` while the nav pill glides on a *different* spring
 * would look like two apps stitched together.
 */
object AppMotion {
    /**
     * Small spatial moves - nav indicator, chip selection, icon swap. Mirrors [GlassMotion.settle],
     * which is already the app's "glide and settle" spring.
     */
    val spatialFast: SpringSpec<Float> = GlassMotion.settle

    /**
     * Standard spatial moves - card expansion, screen content entering. Slightly looser than
     * [spatialFast] so a larger distance is covered without the tail becoming visible.
     */
    val spatialDefault: SpringSpec<Float> =
        spring(dampingRatio = 0.7f, stiffness = 220f, visibilityThreshold = 0.001f)

    /**
     * Large surface moves - bottom sheets, full-screen transitions. Critically damped: a sheet that
     * overshoots past the screen edge looks like a bug, not like physics.
     */
    val spatialSlow: SpringSpec<Float> = GlassMotion.drag

    /**
     * Colour / alpha. Fast and non-bouncy - an effects spring that overshoots would make a fade
     * flash past full opacity before settling.
     */
    val effectsFast: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f, visibilityThreshold = 0.001f)

    /**
     * Slower fades - scrims, backdrops, large surfaces cross-fading. Still critically damped, just
     * less stiff than [effectsFast]: a long fade that finishes in 60 ms reads as a flicker.
     */
    val effectsDefault: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f, visibilityThreshold = 0.001f)
}

/**
 * Material 3 **Expressive** spacing tokens - a 4dp-based ladder.
 *
 * Same rationale as [GlassSizes]: one place answers "how far apart do things sit?" instead of every
 * screen inventing its own `12.dp`. [GlassSizes.ContentPadding] (16dp) is [ScreenEdge]; the larger
 * [GlassSizes.HeavyContentPadding] (24dp) is [Section].
 */
object AppSpacing {
    /** Inline gap between an icon and its label. */
    val Inline: Dp = 4.dp

    /** Between tightly-related peers (chips in a row, lines in a caption). */
    val Tight: Dp = 8.dp

    /** Inside a card, between its own children. */
    val CardInner: Dp = 12.dp

    /** Screen edge padding - equals [GlassSizes.ContentPadding]. */
    val ScreenEdge: Dp = 16.dp

    /** Between two distinct sections of a screen - equals [GlassSizes.HeavyContentPadding]. */
    val Section: Dp = 24.dp

    /** Above a section header / below the top bar. */
    val SectionHeaderTop: Dp = 32.dp
}
