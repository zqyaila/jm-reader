package com.jm.reader.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bookmark file is a hand-rolled line format (no JSON dependency in this module), so its
 * escaping is the part worth pinning down: a chapter name is free text and must not be able to
 * split one bookmark into two rows - which would silently turn the reader's jump list into
 * garbage after a restart.
 */
class BookmarkCodecTest {

    private val bookmark = ReadingBookmark(
        albumId = "123456",
        chapterId = "789",
        chapterName = "第 1 话 · 序",
        page = 12,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun roundTripKeepsEveryField() {
        val decoded = decodeBookmarks(encodeBookmark(bookmark))
        assertEquals(listOf(bookmark), decoded)
    }

    @Test
    fun tabsAndNewlinesInAChapterNameCannotSplitTheRow() {
        val messy = bookmark.copy(chapterName = "前篇\t上\n下")
        val text = encodeBookmark(messy)

        assertTrue("encoded row must stay on one line", !text.contains('\n'))
        assertEquals("row must still have exactly five fields", 5, text.split("\t").size)

        val decoded = decodeBookmarks(text)
        assertEquals(1, decoded.size)
        assertEquals("前篇 上 下", decoded.single().chapterName)
        assertEquals(bookmark.page, decoded.single().page)
    }

    @Test
    fun malformedRowsAreDroppedInsteadOfCrashing() {
        val text = listOf(
            "album\tchapter\t3\t0\tok",
            "album\tchapter\tnot-a-number\t0\tbad page",
            "album\tchapter\t3",                       // too few fields
            "",
            "album\tchapter\t9\t0\tlast",
        ).joinToString("\n")

        val decoded = decodeBookmarks(text)
        assertEquals(listOf("ok", "last"), decoded.map { it.chapterName })
    }

    @Test
    fun emptyInputYieldsNoBookmarks() {
        assertEquals(emptyList<ReadingBookmark>(), decodeBookmarks(null))
        assertEquals(emptyList<ReadingBookmark>(), decodeBookmarks(""))
    }
}
