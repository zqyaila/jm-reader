package com.jm.reader.util

import android.os.Build
import android.text.Html

/**
 * Renders an API string that may contain HTML into plain text.
 *
 * Comment bodies, forum posts and blog articles all arrive as HTML fragments (`<br>`, `<a>`,
 * entities, occasionally emoji images). Showing the raw markup would litter the UI with tags, so
 * every one of those surfaces funnels through here.
 *
 * Falls back to the input untouched if the platform parser rejects it, because a comment that
 * reads oddly beats a comment that disappears.
 */
fun stripHtml(html: String): String = runCatching {
    val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    } else {
        @Suppress("DEPRECATION") Html.fromHtml(html)
    }
    spanned.toString().trim()
}.getOrDefault(html)
