package com.jm.reader.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

/**
 * GitHub transports for the desktop updater: direct plus the reverse proxies listed on
 * github.akams.cn.
 *
 * The desktop client does not download the APK/MSI itself (it hands the release page to the
 * browser), but it can still be blocked from `github.com`. Measuring the transports lets the reader
 * pick one that works, and the same choice is reused when opening the release page.
 */
class UpdateSources(baseHttp: OkHttpClient) {

    data class Source(val id: String, val label: String, val prefix: String)

    companion object {
        const val AUTO_ID = "auto"
        const val RELEASE_PAGE = "https://github.com/zqyaila/jm-reader/releases/latest"
        private const val API = "https://api.github.com/repos/zqyaila/jm-reader/releases/latest"
        private const val KEY = "preferredSource"
        private const val NODE = "com/jm/reader/update"
    }

    /** Direct first; the proxies are the fallbacks. */
    val sources: List<Source> = listOf(
        Source("direct", "GitHub", ""),
        Source("ghproxy", "gh-proxy.com", "https://gh-proxy.com/"),
        Source("ghfast", "ghfast.top", "https://ghfast.top/"),
        Source("ghproxynet", "ghproxy.net", "https://ghproxy.net/"),
    )

    private val node: Preferences? = runCatching { Preferences.userRoot().node(NODE) }.getOrNull()
    private var memory: String? = null

    private val probeHttp: OkHttpClient = baseHttp.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    var preferredId: String
        get() = node?.let { runCatching { it.get(KEY, AUTO_ID) }.getOrNull() } ?: memory ?: AUTO_ID
        set(value) {
            val clean = value.ifBlank { AUTO_ID }
            memory = clean
            node?.let { runCatching { it.put(KEY, clean); it.flush() } }
        }

    /** The release page to open, through the chosen transport when one is pinned. */
    val releasePage: String
        get() {
            val picked = sources.firstOrNull { it.id == preferredId } ?: return RELEASE_PAGE
            return picked.prefix + RELEASE_PAGE
        }

    /** Latency per transport; `null` means it did not answer within the budget. */
    suspend fun measure(): List<Pair<Source, Long?>> = coroutineScope {
        sources.map { source ->
            async(Dispatchers.IO) { source to probe(source) }
        }.map { it.await() }
    }

    private suspend fun probe(source: Source): Long? = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val ok = try {
            probeHttp.newCall(
                Request.Builder()
                    .url(source.prefix + API)
                    .header("User-Agent", "JMReader-Desktop")
                    .build(),
            ).execute().use { it.code in 200..499 }
        } catch (_: Exception) {
            false
        }
        if (ok) (System.nanoTime() - started) / 1_000_000 else null
    }
}
