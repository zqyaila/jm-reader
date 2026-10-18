package com.jm.reader.data.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jm.reader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * In-app update from the rolling GitHub release.
 *
 * The release workflow republishes the `latest` tag on every green build, so a new APK never gets
 * a new `versionName`. The updater therefore compares the release's `published_at` with
 * [BuildConfig.BUILD_TIME] (the moment this APK was compiled), with a tolerance for the gap between
 * "Gradle finished" and "the release was published".
 *
 * Two transport details matter for readers in mainland China:
 *
 *  - **Direct first, proxy after.** `api.github.com` / `github.com` are tried directly, then via
 *    the reverse proxies listed on github.akams.cn (`gh-proxy.com`, `ghfast.top`, `ghproxy.net`).
 *    Once a source works it is reused for the rest of the download.
 *  - **Resume, not restart.** Downloads land in `cache/updates/<asset>.part` and continue with an
 *    HTTP `Range` request, so a dropped connection costs only the bytes still missing. True binary
 *    delta patching would need a bsdiff engine and server-side patches, which this project does not
 *    have; resuming is the incremental part that is actually possible here.
 */
class UpdateManager(
    private val context: Context,
    baseHttp: OkHttpClient,
) {
    data class UpdateInfo(
        val tag: String,
        val publishedAt: Long,
        val notes: String,
        val assetName: String,
        val url: String,
        val sizeBytes: Long,
        val sha256: String?,
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        data object Latest : CheckResult()
        data class Failed(val message: String) : CheckResult()
    }

    sealed class DownloadResult {
        data class Done(val file: File) : DownloadResult()
        data class Failed(val message: String) : DownloadResult()
    }

    private val http: OkHttpClient = baseHttp.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val downloadHttp: OkHttpClient = baseHttp.newBuilder()
        // Short connect budget: a blocked direct GitHub connection must fall through to a proxy
        // quickly instead of making the reader wait out a 10 s handshake per source.
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // A whole APK can legitimately take minutes; only the per-read timeout should fire.
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** Checks the rolling release; the first source that answers wins. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        var lastError = ""
        for (url in apiUrls()) {
            try {
                val body = http.newCall(
                    Request.Builder().url(url).header("User-Agent", USER_AGENT).build(),
                ).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        lastError = "HTTP ${resp.code}"
                        null
                    } else {
                        resp.body?.string()
                    }
                } ?: continue

                val info = parseRelease(body, preferDebugAsset = BuildConfig.DEBUG)
                if (info == null) {
                    lastError = "No Android asset in release"
                    continue
                }
                val newer = info.publishedAt > BuildConfig.BUILD_TIME + PUBLISH_TOLERANCE_MS
                return@withContext if (newer) CheckResult.Available(info) else CheckResult.Latest
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        CheckResult.Failed(lastError.ifBlank { "GitHub unreachable" })
    }

    /**
     * Downloads [info] into the cache, resuming a previous partial file when the server honours
     * `Range`. [onProgress] reports 0f..1f (or -1f when the total is unknown).
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates")
        if (!dir.exists() && !dir.mkdirs()) return@withContext DownloadResult.Failed("Cannot create cache dir")

        val part = File(dir, "${info.assetName}.part")
        val target = File(dir, info.assetName)
        var lastError: String? = null

        for (prefix in ASSET_PREFIXES) {
            try {
                val ok = fetch(prefix + info.url, part, info.sizeBytes, onProgress)
                if (!ok) continue

                val expected = info.sha256
                if (expected != null) {
                    val actual = sha256(part)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        part.delete()
                        lastError = "Checksum mismatch"
                        continue
                    }
                }
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                return@withContext DownloadResult.Done(target)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        DownloadResult.Failed(lastError ?: "Download failed")
    }

    /** Hands the APK to the system installer. Returns false when the user must grant permission. */
    fun install(file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        // Some installers only honour the grant when it is also carried in the clip data.
        intent.clipData = ClipData.newRawUri("apk", uri)
        context.startActivity(intent)
        return true
    }

    // -----------------------------------------------------------------------
    // Transport
    // -----------------------------------------------------------------------

    private fun apiUrls(): List<String> {
        val api = "https://api.github.com/repos/$OWNER/$REPO/releases/tags/latest"
        return listOf(api) + API_PREFIXES.map { it + api }
    }

    /** Streams into [part], appending when the server answers `206 Partial Content`. */
    private fun fetch(url: String, part: File, expectedSize: Long, onProgress: (Float) -> Unit): Boolean {
        val existing = if (part.exists()) part.length() else 0L
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { if (existing > 0) header("Range", "bytes=$existing-") }
            .build()

        downloadHttp.newCall(request).execute().use { resp ->
            // 416 means our partial file is already the whole thing (or stale): start over.
            if (resp.code == 416) {
                part.delete()
                if (existing > 0) return fetch(url, part, expectedSize, onProgress)
                return false
            }
            if (!resp.isSuccessful) return false
            val body = resp.body ?: return false

            val append = resp.code == 206 && existing > 0
            val total = when {
                resp.code == 206 -> parseContentRangeTotal(resp.header("Content-Range"))
                else -> body.contentLength()
            }.let { if (it > 0) it else expectedSize }

            var written = if (append) existing else 0L
            if (append) onProgress(if (total > 0) written.toFloat() / total else -1f)

            FileOutputStream(part, append).use { out ->
                val buffer = ByteArray(64 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
                out.flush()
            }
            // A server that ignored `Range` rewrote the file from byte 0; that is still complete.
            return total <= 0 || part.length() >= total - 1
        }
    }

    private fun parseContentRangeTotal(header: String?): Long {
        // bytes 1024-2047/15728640
        val slash = header?.substringAfterLast('/') ?: return 0L
        return slash.trim().toLongOrNull() ?: 0L
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val OWNER = "zqyaila"
        const val REPO = "jm-reader"
        const val USER_AGENT = "JMReader-Android"

        /** Release publishes a few minutes after Gradle finishes; this is the slack allowed. */
        const val PUBLISH_TOLERANCE_MS = 15L * 60 * 1000

        /** Reverse proxies listed by github.akams.cn; tried only when direct GitHub fails. */
        val API_PREFIXES = listOf(
            "https://gh-proxy.com/",
            "https://ghfast.top/",
        )

        /** Empty prefix = direct. Proxies are prefixed onto the full github.com asset URL. */
        val ASSET_PREFIXES = listOf(
            "",
            "https://gh-proxy.com/",
            "https://ghfast.top/",
            "https://ghproxy.net/",
        )
    }
}

/**
 * Parses a GitHub "release by tag" payload into [UpdateManager.UpdateInfo].
 *
 * Pure on purpose: the asset pick ([preferDebugAsset]) and the digest format are easy to get wrong,
 * and this way both are covered by a JVM test instead of only by an install attempt.
 */
internal fun parseRelease(json: String, preferDebugAsset: Boolean): UpdateManager.UpdateInfo? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val assets = root.optJSONArray("assets") ?: return null
    val apks = (0 until assets.length())
        .mapNotNull { assets.optJSONObject(it) }
        .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null

    val wanted = if (preferDebugAsset) "debug" else "release"
    val match = apks.firstOrNull { it.optString("name").contains(wanted, ignoreCase = true) }
    // A release-signed install must never be offered the debug APK: the signatures differ, so the
    // installer would reject it after a full download. Debug installs may fall back to whatever
    // APK exists (local development builds).
    val asset = match ?: if (preferDebugAsset) apks.first() else return null

    val digest = asset.optString("digest").removePrefix("sha256:").takeIf { it.isNotBlank() }
    return UpdateManager.UpdateInfo(
        tag = root.optString("tag_name"),
        publishedAt = parseIso8601(root.optString("published_at")),
        notes = root.optString("body").trim().take(800),
        assetName = asset.optString("name"),
        url = asset.optString("browser_download_url"),
        sizeBytes = asset.optLong("size", 0L),
        sha256 = digest,
    )
}

/**
 * `2026-10-18T17:54:03Z` -> epoch millis; 0 when unparsable.
 *
 * `java.time` would need API 26 (or core-library desugaring), and this app supports API 24, so the
 * one fixed format GitHub emits is parsed with [java.text.SimpleDateFormat] in UTC.
 */
internal fun parseIso8601(value: String): Long {
    if (value.isBlank()) return 0L
    return runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(value)
            ?.time
    }.getOrNull() ?: 0L
}
