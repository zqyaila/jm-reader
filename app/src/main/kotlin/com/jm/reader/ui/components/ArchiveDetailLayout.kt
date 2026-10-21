package com.jm.reader.ui.components

import androidx.compose.ui.unit.dp

/**
 * The dossier's fixed measurements.
 *
 * The loading template and the loaded page both read these, so the skeleton cannot reflow into a
 * slightly different layout when the data lands - the grey bars are replaced in place.
 */
object ArchiveDetailLayout {
    val Gutter = 24.dp
    val BarHeight = 52.dp
    val CoverWidth = 112.dp
    val CoverHeight = 149.dp

    /** Space between the top bar and the cover plate. */
    val HeaderTop = 4.dp
}
