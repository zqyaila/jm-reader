package com.jm.reader.desktop.core

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-logic regression tests for the ported desktop core.
 *
 * These are the same behaviours the Android suite covers (`app/src/test`), which is the point:
 * the desktop port is a copy of that logic with the platform bits swapped, and copying an
 * algorithm is exactly when a silent transcription error creeps in. Everything here runs offline.
 */
class CoreLogicTest {

    // ------------------------------------------------------------------ JM ids

    @Test
    fun `parses every id shape the adaptive search box accepts`() {
        assertEquals("441923", JmId.parse("441923"))
        assertEquals("441923", JmId.parse("JM441923"))
        assertEquals("441923", JmId.parse("jm441923"))
        assertEquals("441923", JmId.parse("JM 441923"))
        assertEquals("441923", JmId.parse(" https://18comic.vip/album/441923/ "))
        assertEquals("441923", JmId.parse("https://18comic.vip/photo/441923"))
        assertEquals("441923", JmId.parse("https://example.com/album/?id=441923"))
    }

    @Test
    fun `rejects text that is not an id, so the caller falls back to a keyword search`() {
        assertNull(JmId.parse("海贼王"))
        assertNull(JmId.parse(""))
        assertNull(JmId.parse(null))
        // 11 digits is not a JM id; treating it as one would hijack a real keyword search.
        assertNull(JmId.parse("12345678901"))
    }

    @Test
    fun `display form is canonical and never double-prefixes`() {
        assertEquals("JM441923", JmId.display("441923"))
        assertEquals("JM441923", JmId.display("JM441923"))
        assertEquals("JM441923", JmId.display("https://18comic.vip/album/441923/"))
    }

    @Test
    fun `looksLikeId agrees with parse`() {
        assertTrue(JmId.looksLikeId("jm123"))
        assertFalse(JmId.looksLikeId("hello"))
    }

    // ------------------------------------------------------------ version compare

    @Test
    fun `version comparison treats missing components as zero`() {
        assertEquals(0, Versions.compare("2.1", "2.1.0"))
        assertEquals(1, Versions.compare("2.1.7", "2.1.6"))
        assertEquals(-1, Versions.compare("2.0.9", "2.1"))
        assertEquals(1, Versions.compare("3.0.0", "2.9.9"))
    }

    @Test
    fun `version comparison ignores non-numeric suffixes`() {
        assertEquals(0, Versions.compare("2.1.7-beta", "2.1.7"))
        assertEquals(1, Versions.compare("2.1.8-rc1", "2.1.7"))
    }

    // ---------------------------------------------------------------- html strip

    @Test
    fun `strips markup but keeps the text`() {
        val out = HtmlText.strip("<p>Hello <b>world</b></p>")
        assertEquals("Hello world", out)
    }

    @Test
    fun `turns br and block ends into line breaks`() {
        val out = HtmlText.strip("line1<br/>line2<br>line3")
        assertEquals("line1\nline2\nline3", out)
    }

    @Test
    fun `decodes named and numeric entities`() {
        assertEquals("a & b < c > d", HtmlText.strip("a &amp; b &lt; c &gt; d"))
        assertEquals("A", HtmlText.strip("&#65;"))
        assertEquals("A", HtmlText.strip("&#x41;"))
        assertEquals("…", HtmlText.strip("&hellip;"))
    }

    @Test
    fun `blank input passes through untouched`() {
        assertEquals("", HtmlText.strip(""))
    }

    // ----------------------------------------------------------- descramble geometry

    @Test
    fun `slice count is deterministic and within the documented ladder`() {
        val allowed = setOf(2, 4, 6, 8, 10, 12, 14, 16, 18, 20)
        for (aid in listOf(1L, 268_850L, 300_000L, 421_925L, 421_926L, 900_000L)) {
            val n = ImageDescrambler.sliceCount(aid, "00001")
            assertTrue(n in allowed, "slice count $n for aid=$aid is off the ladder")
            assertEquals(n, ImageDescrambler.sliceCount(aid, "00001"), "not deterministic")
        }
    }

    @Test
    fun `only albums at or above the scramble id are descrambled, and never gifs`() {
        assertTrue(ImageDescrambler.needsDescramble(500_000L, 400_000L, "https://x/1.webp"))
        assertFalse(ImageDescrambler.needsDescramble(300_000L, 400_000L, "https://x/1.webp"))
        assertFalse(ImageDescrambler.needsDescramble(500_000L, 400_000L, "https://x/1.gif"))
    }

    @Test
    fun `strip plan tiles the image exactly, with no gap or overlap`() {
        val height = 1000
        val num = 8
        val plan = ImageDescrambler.stripPlan(height, num)
        assertEquals(num, plan.size)

        // Every output row must be written exactly once.
        val covered = IntArray(height)
        var copied = 0
        for (s in plan) {
            assertTrue(s.srcY >= 0 && s.srcY + s.height <= height, "source strip out of bounds: $s")
            assertTrue(s.dstY >= 0 && s.dstY + s.height <= height, "destination strip out of bounds: $s")
            for (y in s.dstY until s.dstY + s.height) covered[y]++
            copied += s.height
        }
        assertEquals(height, copied, "strips must cover the full height")
        assertTrue(covered.all { it == 1 }, "some rows are written twice or never")
    }

    @Test
    fun `strip plan with a non-divisible height still tiles exactly`() {
        val height = 1003
        val num = 6
        val plan = ImageDescrambler.stripPlan(height, num)
        assertEquals(num, plan.size)
        assertEquals(height, plan.sumOf { it.height })
        // The first strip absorbs the remainder, so the rest are all equal.
        val rest = plan.drop(1).map { it.height }.distinct()
        assertEquals(1, rest.size, "only the first strip may differ in height: $rest")
    }

    @Test
    fun `strip plan degenerates safely when the image is shorter than the slice count`() {
        val plan = ImageDescrambler.stripPlan(4, 20)
        assertEquals(1, plan.size)
        assertEquals(0, plan[0].srcY)
        assertEquals(4, plan[0].height)
        assertEquals(0, plan[0].dstY)
    }

    @Test
    fun `page name is the file base name without query or extension`() {
        assertEquals("00001", ImageDescrambler.pageName("https://cdn/album/x/00001.webp?v=12"))
        assertEquals("cover", ImageDescrambler.pageName("https://cdn/a/cover.jpg"))
        assertEquals("noext", ImageDescrambler.pageName("https://cdn/a/noext"))
    }

    // ---------------------------------------------------------------------- crypto

    @Test
    fun `md5 hex matches the known digest`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", Crypto.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", Crypto.md5Hex("abc"))
    }

    @Test
    fun `aes-256-ecb round trips through the api decoder`() {
        val secret = "185Hcomic3PAPP7R"
        val timestamp = "1700000000"
        val plaintext = """{"code":200,"data":{"name":"作品 名"}}"""

        val keyHex = Crypto.md5Hex("$timestamp$secret")
        val key = SecretKeySpec(keyHex.toByteArray(Charsets.UTF_8), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val base64 = java.util.Base64.getEncoder().encodeToString(ciphertext)

        assertEquals(plaintext, Crypto.aesEcbDecrypt(base64, keyHex))
    }

    @Test
    fun `base64 decoding tolerates a BOM, whitespace and missing padding`() {
        // "hello" -> aGVsbG8= ; strip the padding and add line breaks the way a CDN text file does.
        val noisy = "\uFEFFaGVs\nbG8"
        val decoded = Crypto.base64Decode(noisy)
        assertNotNull(decoded)
        assertEquals("hello", String(decoded, Charsets.UTF_8))
    }

    @Test
    fun `base64 decoding rejects unusable input instead of throwing`() {
        assertNull(Crypto.base64Decode(""))
        assertNull(Crypto.base64Decode("!!!"))
        // A single leftover symbol can never be a base64 block.
        assertNull(Crypto.base64Decode("A"))
    }

    // --------------------------------------------------------------------- strings

    @Test
    fun `every language table is fully populated`() {
        for (lang in UiLanguage.entries) {
            val s = AppStrings.forLanguage(lang)
            for ((name, value) in fields(s)) {
                assertTrue(value.isNotBlank(), "$lang.$name is blank")
            }
        }
    }

    @Test
    fun `format strings keep their placeholders in every language`() {
        for (lang in UiLanguage.entries) {
            val s = AppStrings.forLanguage(lang)
            assertTrue(s.chapterFmt.contains("%1\$d"), "$lang chapterFmt lost its placeholder")
            assertTrue(
                s.errRequestFailedFmt.contains("%1\$d"),
                "$lang errRequestFailedFmt lost its placeholder",
            )
            assertEquals("5", s.chapterFmt.format(5).filter { it.isDigit() })
        }
    }

    @Test
    fun `the server language codes stay within what the api accepts`() {
        // The API only understands TW/CN; English is a UI-only language that sends CN.
        for (lang in UiLanguage.entries) {
            assertTrue(lang.apiLang in setOf("TW", "CN"), "$lang sends ${lang.apiLang}")
        }
    }

    /**
     * Reflects over [AppStrings] rather than listing the fields by hand: a hand-written list is
     * exactly the thing that silently stops covering the table when a field is added.
     */
    private fun fields(s: AppStrings): List<Pair<String, String>> =
        AppStrings::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { f ->
                f.isAccessible = true
                (f.get(s) as? String)?.let { f.name to it }
            }
}
