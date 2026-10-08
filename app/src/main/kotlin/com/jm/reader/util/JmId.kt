package com.jm.reader.util

/**
 * Parses an album ("JM") id out of whatever the reader typed.
 *
 * The adaptive search box accepts plain ids in any of the shapes the site and the reference client
 * understand (`JmcomicText.parse_to_jm_id` in the `jmcomic` library does the same job server-side):
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

    /**
     * Canonical on-screen form of an album id: `441923` -> `JM441923`.
     *
     * Ids are shown with the `JM` prefix everywhere (cards, detail page) so a reader can copy them
     * straight into this app's search box or the site, both of which accept the prefixed form.
     * Already-prefixed input is normalised rather than double-prefixed.
     */
    fun display(id: String?): String {
        val digits = parse(id) ?: return id?.trim().orEmpty()
        return "JM$digits"
    }
}
