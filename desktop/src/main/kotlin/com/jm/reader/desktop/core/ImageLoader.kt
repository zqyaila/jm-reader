package com.jm.reader.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.Surface as SkiaSurface
import java.util.concurrent.TimeUnit

/**
 * Loads and decodes images for the desktop UI.
 *
 * Two deliberate choices:
 *
 *  1. **No third-party image library.** Decoding goes through Skia, which Compose Desktop already
 *     ships (Skiko). That means JPEG *and* WebP *and* GIF work out of the box with no extra
 *     dependency to version-match against Kotlin — and it gives us the raw handle we need for
 *     de-scrambling, which a normal async-image component would hide from us.
 *  2. **One in-memory LRU** keyed by URL, shared by covers and reader pages, so scrolling back
 *     through a chapter is instant and the heap stays bounded.
 *
 * All work happens on [Dispatchers.IO]; callers just `await` a finished bitmap.
 */
object ImageLoader {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val MAX_CACHE_ENTRIES = 160

    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private val cacheLock = Mutex()

    /** Cache key: URL plus the de-scramble parameters, so scrambled and raw never collide. */
    private fun key(url: String, aid: Long, scrambleId: Long) = "$url|$aid|$scrambleId"

    private suspend fun cached(k: String): ImageBitmap? = cacheLock.withLock { cache[k] }

    private suspend fun put(k: String, bitmap: ImageBitmap) {
        cacheLock.withLock { cache[k] = bitmap }
    }

    /** Drops every cached bitmap (used by the "clear image cache" action). */
    suspend fun clear() = cacheLock.withLock { cache.clear() }

    /** Loads a plain image (covers, avatars). */
    suspend fun load(url: String): ImageBitmap? = load(url, aid = 0L, scrambleId = 0L)

    /**
     * Loads an image and applies the de-scramble transform when the album requires it.
     *
     * @param aid        the album id (`ReadData.id`)
     * @param scrambleId the album's scramble threshold (`ReadData.scramble_id`)
     */
    suspend fun load(url: String, aid: Long, scrambleId: Long): ImageBitmap? {
        if (url.isBlank()) return null
        val k = key(url, aid, scrambleId)
        cached(k)?.let { return it }

        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { it.body?.bytes() }
            }.getOrNull()
        } ?: return null

        val bitmap = withContext(Dispatchers.IO) { decode(bytes, url, aid, scrambleId) } ?: return null
        put(k, bitmap)
        return bitmap
    }

    private fun decode(bytes: ByteArray, url: String, aid: Long, scrambleId: Long): ImageBitmap? {
        val needs = aid > 0 && ImageDescrambler.needsDescramble(aid, scrambleId, url)
        return if (needs) {
            val num = ImageDescrambler.sliceCount(aid, ImageDescrambler.pageName(url))
            descramble(bytes, num) ?: decodePlain(bytes)
        } else {
            decodePlain(bytes)
        }
    }

    private fun decodePlain(bytes: ByteArray): ImageBitmap? =
        runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()

    /**
     * Paints the strips back in reading order onto a raster surface.
     *
     * Falls back to the un-scrambled image (rather than a broken one) if Skia refuses the payload:
     * a page that is merely in the wrong order is far better than a blank slot, and the failure
     * will be visible in the next screenshot instead of hiding as an empty box.
     */
    private fun descramble(bytes: ByteArray, num: Int): ImageBitmap? = runCatching {
        val image = SkiaImage.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        if (w <= 0 || h <= 0) return@runCatching null
        if (num <= 1 || h < num) return@runCatching image.toComposeImageBitmap()

        val surface = SkiaSurface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        for (strip in ImageDescrambler.stripPlan(h, num)) {
            if (strip.srcY < 0 || strip.srcY + strip.height > h) continue
            canvas.drawImageRect(
                image,
                SkiaRect.makeXYWH(0f, strip.srcY.toFloat(), w.toFloat(), strip.height.toFloat()),
                SkiaRect.makeXYWH(0f, strip.dstY.toFloat(), w.toFloat(), strip.height.toFloat()),
            )
        }
        surface.makeImageSnapshot().toComposeImageBitmap()
    }.getOrNull()
}
