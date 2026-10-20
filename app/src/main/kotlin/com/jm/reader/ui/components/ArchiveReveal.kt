package com.jm.reader.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jm.reader.ui.theme.ArchiveMotion
import kotlinx.coroutines.delay

/** Whether the system animator scale is non-zero ("remove animations" in developer options). */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
}

/**
 * Staggered entrance for the static part of a screen (masthead, hero, quick actions).
 *
 * Paint-only: alpha plus 8 dp of travel through [graphicsLayer], so nothing re-measures while it
 * animates. Items are driven by their own [index] and share the reference project's 45 ms band
 * spacing, capped at eight bands - the tenth card should not wait half a second for its turn.
 *
 * [generation] is the "this content was (re)loaded" key: change it on a refresh to replay the
 * entrance, pass a stable value to keep it still.
 */
@Composable
fun ArchiveReveal(
    index: Int,
    generation: Any?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val enabled = rememberMotionEnabled()
    var progress by remember(generation) { mutableFloatStateOf(if (enabled) 0f else 1f) }

    LaunchedEffect(generation, enabled) {
        if (!enabled) {
            progress = 1f
            return@LaunchedEffect
        }
        delay(ArchiveMotion.staggerDelayMs(index))
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(ArchiveMotion.EnterMs, easing = ArchiveMotion.Enter),
        ) { value, _ -> progress = value }
    }

    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * ArchiveMotion.RevealTravelDp.dp.toPx()
        },
    ) {
        content()
    }
}
