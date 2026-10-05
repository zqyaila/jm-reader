package com.jm.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version guard for the `Tokenparam` header.
 *
 * `GET /setting` returns both `version` (the legacy 1.x app) and `jm3_version` (the mobile app).
 * Only the latter is relevant, and only when it is *newer* - the app must never walk its own
 * version backwards because the server also advertises an unrelated 1.8.2.
 */
class VersionsTest {

    @Test
    fun `newer versions are detected`() {
        assertEquals(1, Versions.compare("2.1.9", "2.1.7"))
        assertEquals(1, Versions.compare("3.0", "2.9.9"))
        assertEquals(1, Versions.compare("2.2", "2.1.9"))
    }

    @Test
    fun `older and equal versions are not adopted`() {
        assertEquals(-1, Versions.compare("1.8.2", "2.1.7"))
        assertEquals(0, Versions.compare("2.1.7", "2.1.7"))
        assertEquals(0, Versions.compare("2.1", "2.1.0"))
        assertEquals(0, Versions.compare("", ""))
    }

    @Test
    fun `the live setting payload does not downgrade the header`() {
        // Values captured from GET /setting on 2026-10-05.
        val appVersion = "2.1.7"
        val legacy = "1.8.2"
        val mobile = "2.1.9"
        assertTrue("legacy version must not win", Versions.compare(legacy, appVersion) < 0)
        assertTrue("mobile version must win", Versions.compare(mobile, appVersion) > 0)
    }

    @Test
    fun `malformed parts are treated as zero`() {
        assertEquals(0, Versions.parse("x.y").sum())
        assertEquals(listOf(2, 1, 0), Versions.parse("2.1.x"))
        assertEquals(listOf(0), Versions.parse(null))
    }
}
