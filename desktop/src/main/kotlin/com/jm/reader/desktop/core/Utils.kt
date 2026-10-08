package com.jm.reader.desktop.core

// ---------------------------------------------------------------------------
// Small pure-logic utilities, ported from the Android module's `util/` package.
// None of them touch Android APIs, so they port byte-for-byte.
// ---------------------------------------------------------------------------

/**
 * Parses an album ("JM") id out of whatever the reader typed.
 *
 * ```
 * 441923
 * JM441923 / jm441923 / JM 441923
 * https://18comic.vip/album/441923/
 * https://18comic.vip/photo/441923
 * https://example.com/album/?id=441923
 * ```
 *
 * Returns `null` when the text is not an id at all, so the caller can fall back to a keyword search.
 */
object JmId {

    private val jmPrefixed = Regex("^[Jj][Mm]\\s*(\\d{1,10})$")
    private val plain = Regex("^\\d{1,10}$")
    private val inUrl = Regex("/(?:album|photo)/?(?:\\?id=)?(\\d{1,10})")
    private val queryId = Regex("[?&]id=(\\d{1,10})")

    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        jmPrefixed.find(text)?.let { return it.groupValues[1] }
        plain.find(text)?.let { return it.value }
        inUrl.find(text)?.let { return it.groupValues[1] }
        queryId.find(text)?.let { return it.groupValues[1] }
        return null
    }

    /** True when the text looks like an id, so the UI can offer a direct "open" affordance. */
    fun looksLikeId(raw: String?): Boolean = parse(raw) != null

    /** Canonical on-screen form of an album id: `441923` -> `JM441923`. */
    fun display(id: String?): String {
        val digits = parse(id) ?: return id?.trim().orEmpty()
        return "JM$digits"
    }
}

/**
 * Dotted-version comparison, mirroring `JmcomicText.compare_versions`.
 *
 * Used to decide whether the `jm3_version` advertised by `GET /setting` is newer than the app
 * version we send in the `Tokenparam` header, so we never accidentally *downgrade* it.
 *
 * Missing components count as zero: `"2.1" == "2.1.0"`, and non-numeric parts are ignored.
 */
object Versions {

    fun parse(version: String?): List<Int> =
        version.orEmpty()
            .trim()
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** 1 when [a] is newer, -1 when [b] is newer, 0 when equal. */
    fun compare(a: String?, b: String?): Int {
        val left = parse(a)
        val right = parse(b)
        val length = maxOf(left.size, right.size)
        for (i in 0 until length) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        return 0
    }
}

/**
 * Renders an API string that may contain HTML into plain text.
 *
 * The Android module delegates to `android.text.Html`; there is no equivalent on the JVM, so this
 * is a small, dependency-free tag stripper plus entity decoder. It only has to be good enough for
 * the payloads the API actually sends (comment bodies, descriptions, forum posts): `<br>`, `<p>`,
 * links, and the usual entities. A comment that reads a little plainly beats the raw markup.
 */
object HtmlText {

    private val BR = Regex("(?i)<br\\s*/?>")
    private val BLOCK_END = Regex("(?i)</(p|div|li|tr|h[1-6])>")
    private val TAG = Regex("<[^>]*>")
    private val NUMERIC = Regex("&#(x[0-9a-fA-F]+|\\d+);")

    fun strip(html: String): String {
        if (html.isBlank()) return html
        var s = html.replace(BR, "\n").replace(BLOCK_END, "\n")
        s = TAG.replace(s, "")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&hellip;", "…")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
        s = NUMERIC.replace(s) { m ->
            val raw = m.groupValues[1]
            val code = if (raw.startsWith("x") || raw.startsWith("X")) {
                raw.drop(1).toIntOrNull(16)
            } else {
                raw.toIntOrNull()
            }
            code?.takeIf { it in 1..0x10FFFF }?.let { String(java.lang.Character.toChars(it)) } ?: m.value
        }
        // Collapse the runs of blank lines the tag stripping leaves behind.
        return s.split('\n').joinToString("\n") { it.trim() }.trim()
    }
}

/**
 * Reconstructs JMComic's server-side "scrambled" page images.
 *
 * The CDN stores some album pages with their horizontal strips in reverse vertical order. The web
 * app de-scrambles them in the browser (`scramble_image` / `get_num` / `onImageLoaded`); this is
 * the same *geometry*, kept pure so it can be unit tested. The pixel work itself lives in
 * [ImageLoader], which paints the strips onto a Skia surface.
 *
 * Slice count depends only on (album id, page file name):
 *   key = md5("<aid><pageName>").lastChar.code
 *   mod 10 for aid in [268850, 421925], mod 8 for aid >= 421926, else unchanged (-> 10)
 */
object ImageDescrambler {

    /** pageName is the image file base name, e.g. "00001" for 00001.webp. */
    fun sliceCount(aid: Long, pageName: String): Int {
        val keyHex = Crypto.md5Hex("$aid$pageName")
        var key = keyHex.last().code
        if (aid in 268_850L..421_925L) key %= 10
        else if (aid >= 421_926L) key %= 8
        return when (key) {
            0 -> 2
            1 -> 4
            2 -> 6
            3 -> 8
            4 -> 10
            5 -> 12
            6 -> 14
            7 -> 16
            8 -> 18
            9 -> 20
            else -> 10
        }
    }

    /** GIFs and albums below scramble_id are never scrambled. */
    fun needsDescramble(aid: Long, scrambleId: Long, imageUrl: String): Boolean {
        if (imageUrl.contains(".gif")) return false
        return aid >= scrambleId
    }

    /**
     * The vertical layout of the strips: for strip `i` (0-based, top of the *output*), the source
     * Y offset and the height to copy. Returned as a list so both the renderer and the tests use
     * exactly one implementation of the arrangement.
     *
     * The first strip absorbs the remainder so the pieces always tile the full height exactly.
     */
    fun stripPlan(height: Int, num: Int): List<Strip> {
        if (num <= 1 || height < num) return listOf(Strip(0, height, 0))
        val baseH = height / num
        val rem = height % num
        return (0 until num).map { i ->
            val copyH = if (i == 0) baseH + rem else baseH
            Strip(
                srcY = height - baseH * (i + 1) - rem,
                height = copyH,
                dstY = if (i == 0) 0 else baseH * i + rem,
            )
        }
    }

    /** One strip of the reconstruction: copy [height] rows from [srcY] to [dstY]. */
    data class Strip(val srcY: Int, val height: Int, val dstY: Int)

    /** File base name of an image URL, without extension or query (e.g. "00001"). */
    fun pageName(url: String): String {
        val path = url.substringBefore('?')
        val seg = path.substringAfterLast('/')
        return seg.substringBeforeLast('.').ifBlank { seg }
    }
}
