package com.jm.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * Motion vocabulary, ported from Wenku8_Flutter's `NovelMotion`.
 *
 * The numbers are the reference project's, because the feel comes from them being *short and
 * slightly overshooting* rather than from any particular animation API:
 *
 *  - 120 ms contact - a press must be acknowledged before the finger lifts;
 *  - 420 ms enter / 320 ms exit - a route lands quickly and leaves faster than it arrived;
 *  - `settle` is a cubic bezier with a small overshoot (1.08 on the control point), which is what
 *    makes a tap feel like jelly rather than like a linear scale.
 *
 * Reveals are paint-only: alpha + a few dp of travel, never a relayout, so a staggered entrance
 * cannot cost a frame on a long list.
 */
object ArchiveMotion {

    const val ContactMs = 120
    const val EnterMs = 420
    const val ExitMs = 320

    /** How long each staggered item waits behind the previous one. */
    const val StaggerStepMs = 45L

    /** Delay bands are capped, so a long batch never waits seconds for its last row. */
    const val StaggerBands = 8

    /** How far a revealed item travels, in dp. The reference uses (-8, -6). */
    const val RevealTravelDp = 8f

    val Enter: Easing = FastOutSlowInEasing
    val Exit: Easing = FastOutLinearInEasing

    /** `Cubic(.2, 1.08, .34, 1)` - the overshoot is deliberate, it is the "jelly" in the tap. */
    val Settle = CubicBezierEasing(0.2f, 1.08f, 0.34f, 1f)

    val PressDown = tween<Float>(ContactMs, easing = LinearOutSlowInEasing)
    val PressUp = tween<Float>(EnterMs, easing = Settle)

    val enterSpec = tween<Float>(EnterMs, easing = Enter)
    val exitSpec = tween<Float>(ExitMs, easing = Exit)

    fun staggerDelayMs(index: Int): Long =
        index.coerceIn(0, StaggerBands) * StaggerStepMs
}
