package com.jm.reader.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release parsing is the one part of the updater that can be tested without a device. It pins the
 * two things that would silently ship a broken button:
 *
 *  - the APK asset is picked correctly for a debug vs release install,
 *  - the `sha256:` digest prefix is stripped, because the downloader compares raw hex.
 */
class UpdateParsingTest {

    private val json = """
        {
          "tag_name": "latest",
          "published_at": "2026-10-18T17:54:03Z",
          "body": "Rolling build",
          "assets": [
            {"name": "JMReader-windows-x64.msi", "size": 120982313,
             "browser_download_url": "https://example/msi", "digest": "sha256:aa"},
            {"name": "JMReader-android-debug.apk", "size": 19595232,
             "browser_download_url": "https://example/debug",
             "digest": "sha256:5772f07e92557b7c095d40c932adebf9cb669f6246355bd60633bc036f73f0c1"},
            {"name": "JMReader-android-release.apk", "size": 18000000,
             "browser_download_url": "https://example/release", "digest": "sha256:bb"}
          ]
        }
    """.trimIndent()

    @Test
    fun `debug install picks the debug apk`() {
        val info = parseRelease(json, preferDebugAsset = true)!!
        assertEquals("JMReader-android-debug.apk", info.assetName)
        assertEquals(19_595_232L, info.sizeBytes)
        assertEquals("5772f07e92557b7c095d40c932adebf9cb669f6246355bd60633bc036f73f0c1", info.sha256)
        assertEquals("https://example/debug", info.url)
    }

    @Test
    fun `release install picks the release apk`() {
        val info = parseRelease(json, preferDebugAsset = false)!!
        assertEquals("JMReader-android-release.apk", info.assetName)
        assertEquals("bb", info.sha256)
    }

    @Test
    fun `release install is not offered a debug-only apk`() {
        val debugOnly = """{"tag_name":"latest","assets":[
            {"name":"JMReader-android-debug.apk","browser_download_url":"https://example/debug"}]}"""
        assertNull(parseRelease(debugOnly, preferDebugAsset = false))
        // A debug install may still take it.
        assertEquals("JMReader-android-debug.apk", parseRelease(debugOnly, preferDebugAsset = true)?.assetName)
    }

    @Test
    fun `a release without apk assets yields null`() {
        val noApk = """{"tag_name":"latest","assets":[{"name":"x.msi"}]}"""
        assertNull(parseRelease(noApk, preferDebugAsset = true))
        assertNull(parseRelease("not json", preferDebugAsset = true))
    }

    @Test
    fun `iso timestamp is parsed as utc`() {
        val millis = parseIso8601("2026-10-18T17:54:03Z")
        assertTrue(millis > 0)
        // 2026-10-18T17:54:03Z == 2026-10-19T01:54:03+08:00
        assertEquals(1_792_346_043_000L, millis)
    }

    @Test
    fun `bad timestamp degrades to zero`() {
        assertEquals(0L, parseIso8601(""))
        assertEquals(0L, parseIso8601("yesterday"))
    }
}
