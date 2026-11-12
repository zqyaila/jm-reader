package com.jm.reader.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading position is what every chapter row and the "continue reading" button are built from, so
 * two properties have to hold: the file survives chapter names with punctuation, and a chapter
 * that was once finished never silently turns back into "unread".
 */
class ReadingProgressTest {

    private val entry = ChapterProgress(
        albumId = "123456",
        chapterId = "900",
        name = "第 3 話 · 前篇",
        page = 12,
        furthest = 30,
        total = 40,
        done = false,
        at = 1_700_000_000_000L,
    )

    @Test
    fun `round trip keeps every field`() {
        assertEquals(listOf(entry), decodeProgress(encodeProgress(entry)))
    }

    @Test
    fun `a tab or newline in the chapter name cannot split the row`() {
        val messy = entry.copy(name = "第\t3 話\n上")
        val text = encodeProgress(messy)

        assertEquals("row must stay on one line", 1, text.split("\n").size)
        assertEquals("row must still have eight fields", 8, text.split("\t").size)
        assertEquals("第 3 話 上", decodeProgress(text).single().name)
    }

    @Test
    fun `malformed rows are dropped, the rest of the file loads`() {
        val text = listOf(
            "123\t900\t12\t30\t40\t0\t1700000000000\tok",
            "123\t901\tnope\t3\t10\t0\t1700000000000\tbad page",
            "123\t902\t3",                                  // too few fields
            "",
            "123\t903\t5\t5\t9\t0\t1700000000000\tlast",
        ).joinToString("\n")

        assertEquals(listOf("ok", "last"), decodeProgress(text).map { it.name })
    }

    @Test
    fun `blank ids are ignored instead of creating an unusable row`() {
        assertEquals(emptyList<ChapterProgress>(), decodeProgress("\t900\t1\t1\t2\t0\t0\tx"))
        assertEquals(emptyList<ChapterProgress>(), decodeProgress(null))
    }

    @Test
    fun `an untouched chapter is unread`() {
        assertEquals(ChapterReadState.UNREAD, chapterReadState(null))
        assertEquals(
            ChapterReadState.UNREAD,
            chapterReadState(entry.copy(page = 0, furthest = 0)),
        )
    }

    @Test
    fun `a chapter in the middle is reading`() {
        assertEquals(ChapterReadState.READING, chapterReadState(entry))
        assertEquals(ChapterReadState.READING, chapterReadState(entry, expectedTotal = 40))
    }

    @Test
    fun `reaching the last page marks the chapter read`() {
        assertEquals(ChapterReadState.READ, chapterReadState(entry.copy(furthest = 40)))
        assertEquals(ChapterReadState.READ, chapterReadState(entry.copy(done = true)))
    }

    @Test
    fun `the album's page count decides when the reader never learned it`() {
        val unknown = entry.copy(total = 0, furthest = 18)
        assertEquals(ChapterReadState.READING, chapterReadState(unknown, expectedTotal = 40))
        assertEquals(ChapterReadState.READ, chapterReadState(unknown, expectedTotal = 18))
    }

    @Test
    fun `flipping back does not un-read a chapter`() {
        // page = where you are now, furthest = how far you got: only the latter decides.
        val reRead = entry.copy(page = 2, furthest = 40)
        assertEquals(ChapterReadState.READ, chapterReadState(reRead))
    }

    @Test
    fun `missing optional fields fall back to sane values`() {
        val decoded = decodeProgress("123\t900\t12\t30\t40\t1\t1700000000000")
        val single = decoded.single()
        assertEquals("", single.name)
        assertEquals(ChapterReadState.READ, single.state)
        assertNull(decodeProgress("")?.firstOrNull())
    }
}
