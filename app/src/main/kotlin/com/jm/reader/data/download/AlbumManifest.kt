package com.jm.reader.data.download

import com.jm.reader.data.model.SeriesItem

/** One chapter that is on disk. `pageCount` is the number of pages written for it. */
data class DownloadedChapter(
    val chapterId: String,
    val name: String,
    val sort: Int,
    val pageCount: Int,
)

/**
 * What has been downloaded for one album.
 *
 * Downloads used to be an album-wide folder of `01_001.jpg.jm` files: the chapter boundary only
 * existed as a number in the file name, so a 400-chapter serial could not be downloaded chapter
 * by chapter, and the offline reader could only show it as one endless column. Each chapter now
 * gets its own sub-folder and this manifest records which chapters are complete, in reading
 * order, with their names.
 *
 * The manifest lives in the app's private files directory rather than next to the pages: on
 * API 29+ `Downloads/JMReader/<id>/` is MediaStore's, not ours to write a file into.
 */
data class AlbumManifest(
    val albumId: String,
    val name: String,
    val chapters: List<DownloadedChapter>,
) {
    fun chapter(chapterId: String): DownloadedChapter? =
        chapters.firstOrNull { it.chapterId == chapterId }

    val pageCount: Int get() = chapters.sumOf { it.pageCount }

    /** Adds or replaces one chapter, keeping the series order. */
    fun withChapter(chapter: DownloadedChapter): AlbumManifest = copy(
        chapters = (chapters.filterNot { it.chapterId == chapter.chapterId } + chapter)
            .sortedBy { it.sort },
    )

    fun withoutChapter(chapterId: String): AlbumManifest =
        copy(chapters = chapters.filterNot { it.chapterId == chapterId })
}

/**
 * Manifest wire format, one record per line:
 *
 * ```
 * v1
 * <albumId>\t<album name>
 * <chapterId>\t<sort>\t<pageCount>\t<chapter name>
 * ```
 *
 * Tab-separated with the free-text field last for the same reason the bookmark file is: chapter
 * names come from the site and may contain anything, but must not be able to split a record.
 */
internal fun encodeManifest(manifest: AlbumManifest): String = buildString {
    append(MANIFEST_VERSION).append('\n')
    append(manifest.albumId.replace('\t', ' ').replace('\n', ' ')).append('\t')
    append(manifest.name.replace('\t', ' ').replace('\n', ' ')).append('\n')
    manifest.chapters.forEach { c ->
        append(c.chapterId.replace('\t', ' ').replace('\n', ' ')).append('\t')
        append(c.sort).append('\t')
        append(c.pageCount).append('\t')
        append(c.name.replace('\t', ' ').replace('\n', ' ')).append('\n')
    }
}

internal fun decodeManifest(text: String?): AlbumManifest? {
    val lines = text?.split('\n')?.filter { it.isNotBlank() } ?: return null
    if (lines.size < 2 || lines[0].trim() != MANIFEST_VERSION) return null
    val head = lines[1].split('\t')
    if (head.size < 2 || head[0].isBlank()) return null
    val albumId = head[0]
    val chapters = lines.drop(2).mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size < 3) return@mapNotNull null
        val id = parts[0]
        val sort = parts[1].toIntOrNull() ?: return@mapNotNull null
        val pages = parts[2].toIntOrNull() ?: return@mapNotNull null
        if (id.isBlank() || pages <= 0) return@mapNotNull null
        DownloadedChapter(chapterId = id, name = parts.getOrElse(3) { "" }, sort = sort, pageCount = pages)
    }
    return AlbumManifest(albumId = albumId, name = head[1], chapters = chapters.sortedBy { it.sort })
}

internal const val MANIFEST_VERSION = "v1"

/** Sort key used to keep a series in reading order regardless of what the API sends. */
internal fun seriesInOrder(series: List<SeriesItem>): List<SeriesItem> =
    series.filter { it.id.isNotBlank() }.sortedBy { it.sort }

/** On-disk name of one page inside a chapter folder, e.g. page 7 -> `007`. */
internal fun chapterPageName(index: Int): String = "%03d".format(index.coerceAtLeast(1))
