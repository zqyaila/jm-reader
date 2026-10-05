package com.jm.reader.util

/**
 * Dotted-version comparison, mirroring `JmcomicText.compare_versions`.
 *
 * Used to decide whether the `jm3_version` advertised by `GET /setting` is newer than the app
 * version we send in the `Tokenparam` header, so we never accidentally *downgrade* it (the same
 * endpoint also carries the unrelated legacy `version` field).
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
