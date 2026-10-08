package com.jm.reader.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Page files are deliberately *not* named `…jpg`, so the system media scanner does not file them
 * as pictures and they stay out of the gallery. Two things must keep working:
 *
 *  - new downloads are written with the marker, and
 *  - albums downloaded by earlier versions (plain `.jpg`) are still listed by [isDownloadedPageFile].
 */
class DownloadNamingTest {

    @Test
    fun `pages are written with the gallery-hiding marker`() {
        assertEquals("01_001.jpg.jm", downloadPageName("01_001"))
        assertEquals("12_034.jpg.jm", downloadPageName("12_034"))
    }

    @Test
    fun `the stored name no longer ends in an image extension`() {
        // This is the property that actually keeps it out of the gallery.
        val name = downloadPageName("01_001")
        assertFalse(
            "a name ending in .jpg would be indexed as a picture",
            name.endsWith(".jpg"),
        )
    }

    @Test
    fun `current and legacy page names are both recognised`() {
        assertTrue(isDownloadedPageFile("01_001.jpg.jm"))   // current
        assertTrue(isDownloadedPageFile("01_001.jpg"))      // downloaded by an older build
    }

    @Test
    fun `unrelated files in the album folder are ignored`() {
        assertFalse(isDownloadedPageFile(".nomedia"))
        assertFalse(isDownloadedPageFile("notes.txt"))
        assertFalse(isDownloadedPageFile("cover.png"))
        assertFalse(isDownloadedPageFile(""))
    }

    @Test
    fun `the registered mime is not an image type`() {
        // MediaStore files anything with an image MIME as a picture, regardless of the name.
        assertFalse(PAGE_MIME.startsWith("image/"))
    }
}
