package com.jm.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Frosted-glass ("毛玻璃") helpers.
 *
 * Design contract - **legibility first**:
 *  - **Page canvas** ([AppBackdrop] + the theme's `background`) is a near-opaque surface, so lists,
 *    grids and body text always sit on a predictable contrast.
 *  - Only **bars** (top bar / bottom navigation) and **panels** are glass, and they are built from
 *    a *scrim*: a near-opaque black veil in the dark theme, a near-opaque white veil in the light
 *    theme. A scrim darkens/lightens what is behind it instead of washing it out, so
 *    `onSurface` / `onSurfaceVariant` keep their contrast.
 *  - A hairline border and a faint sheen supply the "pane of glass" read without adding brightness
 *    behind the text.
 *  - `Modifier.blur` only has an effect on Android 12+ (API 31); below that the scrims alone still
 *    look right.
 *
 * `GlassContrastTest` asserts the resulting contrast ratios, so the earlier regression (a
 * translucent white wash over a bright gradient, which made icons and text unreadable) fails the
 * build instead of shipping.
 */
val GlassShape: Shape = RoundedCornerShape(20.dp)
val GlassShapeLarge: Shape = RoundedCornerShape(28.dp)
val GlassShapeSmall: Shape = RoundedCornerShape(14.dp)

/** True when the platform can actually blur. */
val supportsBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun Modifier.platformBlur(radius: Dp): Modifier =
    if (supportsBlur && radius > 0.dp) {
        this.blur(radius, BlurredEdgeTreatment.Unbounded)
    } else {
        this
    }

/** Root gradient for the current theme. */
@Composable
fun backdropBrush(): Brush = if (isSystemInDarkTheme()) BackdropBrush else BackdropBrushLight

/** Panel veil: darkens in the dark theme, lightens in the light theme. */
@Composable
fun glassScrimColor(): Color = if (isSystemInDarkTheme()) GlassScrimDark else GlassScrimLight

/** Bar veil: same idea, slightly more see-through than a panel. */
@Composable
fun glassBarScrimColor(): Color = if (isSystemInDarkTheme()) GlassBarScrimDark else GlassBarScrimLight

@Composable
fun glassBorderColor(): Color = if (isSystemInDarkTheme()) GlassBorderDark else GlassBorderLight

/** Translucent panel with a hairline highlight - the core glass recipe. */
@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassShape,
    tint: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    val fill = tint ?: glassScrimColor()
    val stroke = borderColor ?: glassBorderColor()
    return this
        .clip(shape)
        .background(fill)
        .border(borderWidth, stroke, shape)
}

/**
 * A glass panel: contrast-safe scrim + hairline border + a faint sheen.
 *
 * [blurRadius] softens the sheen (and is a no-op below API 31). Content is drawn **after** the
 * sheen, so nothing bright ever sits behind text.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape,
    tint: Color? = null,
    blurRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(tint ?: glassScrimColor())
            .border(1.dp, glassBorderColor(), shape),
    ) {
        // Decorative sheen only - kept behind the content and very low alpha so it can never
        // reduce text contrast.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(GlassSheen, Color.Transparent),
                    ),
                )
                .platformBlur(blurRadius),
        )
        content()
    }
}

/**
 * Full-screen app background: a calm gradient plus three soft, dim colour blobs.
 *
 * This layer is what the translucent bars, panels and tiles refract - without visible colour
 * variation behind them, "glass" just looks like a flat strip.
 */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(backdropBrush())) {
        Box(
            Modifier
                .size(340.dp)
                .offset(x = (-80).dp, y = (-50).dp)
                .platformBlur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(BrandOrange.copy(alpha = BackdropBlobAlpha), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(320.dp)
                .offset(x = 60.dp, y = 90.dp)
                .platformBlur(80.dp)
                .background(
                    Brush.radialGradient(
                        listOf(GlassAccentBlue.copy(alpha = BackdropBlobAlpha), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .size(260.dp)
                .offset(x = 120.dp, y = (-40).dp)
                .platformBlur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(GlassAccentViolet.copy(alpha = BackdropBlobAlpha * 0.8f), Color.Transparent),
                    ),
                ),
        )
    }
}

/**
 * Strip used behind the top bar and the navigation bar.
 *
 * **Never put a blur on this node.** `Modifier.blur` is a graphics layer on the *layout node*, so it
 * blurs everything the node draws - children included. Applying it here smeared the navigation
 * icons/labels and the library title/tabs into an unreadable haze (the reason those two bars looked
 * "invisible" while every colour ratio computed fine). The frosted read comes instead from:
 *  - a translucent scrim: page content stays faintly visible as it scrolls underneath, and
 *  - a sheen plus a hairline border: the "pane of glass" highlight.
 *
 * The gradient and the sheen are drawn as *sibling* layers, so they can be blurred or shaped
 * without ever touching [content].
 *
 * @param shape use a rounded shape (with outer padding) to get the floating "liquid glass" bar;
 *        the default keeps the bar full-bleed.
 */
@Composable
fun GlassBar(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    tint: Color? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.clip(shape)) {
        // Layer 1: the app gradient, so the bar is not a flat rectangle.
        Box(
            Modifier
                .matchParentSize()
                .background(backdropBrush()),
        )
        // Layer 2: the translucent scrim that keeps text and icons legible.
        Box(
            Modifier
                .matchParentSize()
                .background(tint ?: glassBarScrimColor()),
        )
        // Layer 3: sheen - the glass highlight. Low alpha, still behind the content.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(listOf(GlassSheen, Color.Transparent)),
                ),
        )
        // Layer 4: content (NavigationBar / TopAppBar). Drawn after every background layer and
        // outside any blur, so it always stays sharp.
        content()
        // Layer 5: hairline edge on top of the content, so the bar reads as a distinct surface.
        Box(
            Modifier
                .matchParentSize()
                .border(1.dp, glassBorderColor(), shape)
                .clip(shape),
        )
    }
}
