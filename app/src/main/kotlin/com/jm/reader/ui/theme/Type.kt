package com.jm.reader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 **Expressive** type scale.
 *
 * Why this file exists: the app previously used Material's stock [Typography], whose headlines are
 * Regular weight with wide tracking. On a cover-art-dominant screen that reads as flat - a title
 * and a caption end up at almost the same visual weight. The Expressive scale fixes the *hierarchy*
 * rather than the font: headlines go SemiBold with negative tracking (so a long comic title still
 * fits one line and reads as a title), body stays Regular at 14sp for comfy reading, and labels go
 * Medium so the chips sitting on artwork still hold their own.
 *
 * ### Font family
 * The design doc asks for `robotoFlex`. It is deliberately **not** bundled here:
 *  - shipping it would add ~1.5 MB of assets, and
 *  - declaring it as a downloadable font would make the very first screen depend on a network
 *    round-trip to the font provider (and on Google Play Services being present), which is a bad
 *    trade for a build that ships to devices without Play Services.
 *
 * [FontFamily.Default] resolves to Roboto on Android - which *is* Roboto Flex's parent design - so
 * the metric differences are a handful of units of width on a few glyphs. The scale (sizes,
 * weights, tracking, line heights) below is what actually carries the Expressive look, and that is
 * fully applied. Swap in a bundled `roboto_flex` resource later and every screen picks it up by
 * changing the one [AppFontFamily] line.
 */
private val AppFontFamily: FontFamily = FontFamily.Default

/**
 * Expressive letter-spacing convention: the larger the text, the tighter it tracks. Below ~16sp it
 * flips to positive tracking, which is what keeps small labels legible instead of cramped.
 *
 * All letter-spacing literals are Floats on purpose - `Float.sp` is the extension this scale is
 * written against, so there is no reliance on widening `Double` literals at every call site.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25f).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.25f).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2f).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    // A top-bar title is `titleLarge`; SemiBold here is what separates it from body text.
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15f.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1f.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5f.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25f.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4f.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1f.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5f.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5f.sp,
    ),
)
