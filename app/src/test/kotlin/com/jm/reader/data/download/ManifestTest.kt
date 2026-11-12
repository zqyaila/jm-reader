package com.jm.reader.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest is what turns a folder of pages into a chapter list, so its wire format has to
 * survive the two things that actually happen on device: chapter names from the site (free text,
 * sometimes with punctuation or a stray tab) and files written by a build that predates it.
 */
class ManifestTest {

    private val manifest = AlbumManifest(
        albumId = "123456",
        name = "長篇 / 作品",
        chapters = listOf(
            DownloadedChapter(chapterId = "900", name = "第 3 話", sort = 3, pageCount = 24),
            DownloadedChapter(chapterId = "898", name = "第 1 話", sort = 1, pageCount = 40),
        ),
    )

    @Test
    fun `round trip keeps the album and every chapter`() {
        val decoded = decodeManifest(encodeManifest(manifest))
        assertNotNull(decoded)
        assertEquals("123456", decoded!!.albumId)
        assertEquals("長篇 / 作品", decoded.name)
        assertEquals(listOf("898", "900"), decoded.chapters.map { it.chapterId })
        assertEquals(64, decoded.pageCount)
    }

    @Test
    fun `chapters are stored in reading order, not insertion order`() {
        val decoded = decodeManifest(encodeManifest(manifest))!!
        assertEquals(listOf(1, 3), decoded.chapters.map { it.sort })
    }

    @Test
    fun `a tab in a chapter name cannot split the record`() {
        val messy = manifest.copy(chapters = listOf(manifest.chapters[0].copy(name = "第\t3 話")))
        val text = encodeManifest(messy)

        assertEquals("one header line, one album line, one chapter line", 3, text.trim().split("\n").size)
        val decoded = decodeManifest(text)!!
        assertEquals(1, decoded.chapters.size)
        assertEquals("第 3 話", decoded.chapters[0].name)
    }

    @Test
    fun `an unknown version is rejected instead of being misread`() {
        assertNull(decodeManifest("v9\n123\tname\n"))
        assertNull(decodeManifest(null))
        assertNull(decodeManifest(""))
    }

    @Test
    fun `a damaged chapter line is dropped, the rest of the album still opens`() {
        val text = "v1\n123\tAlbum\n" +
            "900\t3\t24\tOk\n" +
            "901\tnot-a-number\t10\tBroken sort\n" +
            "902\t4\tnope\tBroken pages\n" +
            "903\t5\t0\tEmpty\n" +
            "904\t6\t12\tLast\n"
        val decoded = decodeManifest(text)!!
        assertEquals(listOf("900", "904"), decoded.chapters.map { it.chapterId })
    }

    @Test
    fun `re-downloading a chapter replaces its row instead of duplicating it`() {
        val updated = manifest.withChapter(
            DownloadedChapter(chapterId = "900", name = "第 3 話", sort = 3, pageCount = 30),
        )
        assertEquals(2, updated.chapters.size)
        assertEquals(30, updated.chapter("900")!!.pageCount)
        assertEquals(70, updated.pageCount)
    }

    @Test
    fun `removing a chapter keeps the others in order`() {
        val updated = manifest.withoutChapter("898")
        assertEquals(listOf("900"), updated.chapters.map { it.chapterId })
    }

    @Test
    fun `page names sort by page number`() {
        assertEquals("001", chapterPageName(1))
        assertEquals("010", chapterPageName(10))
        assertEquals("001", chapterPageName(0))
    }

    @Test
    fun `folder names cannot escape the album folder`() {
        assertEquals("900", safeSegment("900"))
        val escaped = safeSegment("../../etc")
        assertTrue("separators must not survive", !escaped.contains('/'))
        assertTrue("no parent hop", !escaped.contains(".."))
        assertEquals("_", safeSegment(""))
    }
}
