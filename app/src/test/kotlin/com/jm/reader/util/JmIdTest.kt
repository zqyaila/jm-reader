package com.jm.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The adaptive search box has to decide, from the raw text alone, whether the reader typed an
 * album id or a keyword. These are the shapes the site and the `jmcomic` reference accept.
 */
class JmIdTest {

    @Test
    fun `plain digits are an id`() {
        assertEquals("441923", JmId.parse("441923"))
        assertEquals("1", JmId.parse("1"))
    }

    @Test
    fun `JM prefix is stripped, in any case and with optional space`() {
        assertEquals("441923", JmId.parse("JM441923"))
        assertEquals("441923", JmId.parse("jm441923"))
        assertEquals("441923", JmId.parse("Jm 441923"))
        assertEquals("441923", JmId.parse("  JM441923  "))
    }

    @Test
    fun `album and photo urls yield the id`() {
        assertEquals("441923", JmId.parse("https://18comic.vip/album/441923/"))
        assertEquals("441923", JmId.parse("https://18comic.vip/photo/441923"))
        assertEquals("441923", JmId.parse("https://example.com/album/?id=441923"))
        assertEquals("441923", JmId.parse("https://example.com/x?foo=1&id=441923&bar=2"))
    }

    @Test
    fun `keywords and junk are not ids`() {
        assertNull(JmId.parse("MANA"))
        assertNull(JmId.parse("神里绫华"))
        assertNull(JmId.parse(""))
        assertNull(JmId.parse(null))
        assertNull(JmId.parse("   "))
        assertNull(JmId.parse("JM"))
        assertNull(JmId.parse("441923a"))
        assertNull(JmId.parse("https://example.com/album/"))
    }

    @Test
    fun `looksLikeId mirrors parse`() {
        assertTrue(JmId.looksLikeId("441923"))
        assertTrue(JmId.looksLikeId("JM441923"))
        assertFalse(JmId.looksLikeId("MANA"))
    }
}
