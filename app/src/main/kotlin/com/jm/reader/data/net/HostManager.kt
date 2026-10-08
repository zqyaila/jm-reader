package com.jm.reader.data.net

import com.jm.reader.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bootstraps and *validates* the API host.
 *
 * Mirrors the web app's FETCH_HOST flow, with the robustness fixes the old version was missing:
 *  1. fetch the encrypted host config from the CDN txt files
 *  2. decrypt with key = ASCII(md5("diosfjckwpqpdfjkvnqQjsik")) (AES-256-ECB); the files are
 *     served with a UTF-8 BOM, so the base64 step has to tolerate it (see [Crypto.base64Decode])
 *  3. collect candidate hosts from `Setting` / `Server` / `jm3_Server`
 *  4. **probe each candidate with a real API call** (`GET /setting`) and cache the first one that
 *     answers with a `code: 200` envelope
 *  5. fall back to the embedded payload / previously cached host only if nothing else works
 *
 * Step 4 matters: the old code picked a random entry of `Server` and trusted it. When the CDN
 * fetch failed it silently selected an *image* CDN from the stale embedded payload, so every API
 * call (login, register, daily check-in, watch history) returned an HTML 404 page and the UI
 * surfaced meaningless errors.
 */
class HostManager(
    private val session: SessionManager,
    private val http: OkHttpClient,
) {
    private val hostUrls = listOf(
        "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
    )

    /**
     * Encrypted host config baked into the web bundle (REACT_APP_HOST_BACKUP_CODE).
     * Refreshed from the live CDN payload; it lists the mobile API hosts, not the image CDNs.
     */
    private val fallbackHostCode =
        "X+bnzYIcwF6C7Rd3T7njPDNH08zsH9zyqCrrjCr7qcnHb1LsmIZGIHtrNVR/GiraHE6OuhvrxEzwciVvhdU0I9OYcmWTxF1K7fLfcwkn7kMQg2DZ2qpE7dKGkqKCmQijaSUOswxL1/p9pSVe/vRYEzbB5pfcAB6Yz/zVVIendBJK629QiqQndRXM9bijtZuYJtKw3YBAA26a+fy06dNszfw9v/4R8akVaSTWLOJc0nJy+9vm2t2W997vcqFL91iklKuKVEZHTtdpaLTgWExXaLjtIz2zlVZfy3jYrzKZ7x+LL7o02c6WB4HV69s1VqCJYl+3l3RNwDjJ0iRNnG9p/caZL/y6sT8i78Wc38WZhOAxkDsOFiGNpvS3eojKA0wGmNGDvSXAwp6T0MeV1YopFg=="

    /** Known mobile API hosts, used only after every decrypted candidate has failed. */
    private val knownApiHosts = listOf(
        "www.cdnhjk.net",
        "www.cdngwc.cc",
        "www.cdngwc.net",
        "www.cdngwc.club",
    )

    private companion object {
        /** Upper bound on hosts we are willing to probe during startup. */
        const val MAX_CANDIDATES = 6
    }

    /** Short-timeout client so probing several hosts cannot stall the splash screen. */
    private val probeClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** Returns a **verified** API base URL (`https://<host>/`), caching it in the session. */
    suspend fun bootstrap(): Result<String> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()

        // Prefer whatever worked last: it is already known to be reachable.
        session.apiUrl?.let { normalize(it)?.let(candidates::add) }

        val config = fetchConfig()
        if (config != null) candidates.addAll(candidateHosts(config))
        candidates.addAll(knownApiHosts)

        val hosts = candidates.take(MAX_CANDIDATES).toList()
        if (hosts.isEmpty()) return@withContext Result.failure(IllegalStateException("無法取得 API 主機"))

        // Fast path: the most likely host first. Only when it fails do we fan the rest out
        // concurrently, so a dead candidate list costs ~one timeout rather than one per host.
        var winner = if (probe(hosts.first())) hosts.first() else null
        if (winner == null && hosts.size > 1) {
            winner = coroutineScope {
                hosts.drop(1)
                    .map { host -> async(Dispatchers.IO) { if (probe(host)) host else null } }
                    .firstNotNullOfOrNull { it.await() }
            }
        }

        if (winner != null) {
            val apiUrl = "https://$winner/"
            session.apiUrl = apiUrl
            return@withContext Result.success(apiUrl)
        }

        // Nothing answered. Keep a cached host if we have one so offline-ish flows still try.
        val cached = session.apiUrl
        if (!cached.isNullOrBlank()) return@withContext Result.success(cached)
        Result.failure(IllegalStateException("無法取得 API 主機"))
    }

    /** All hosts named by the decrypted config, most trustworthy key first. */
    private fun candidateHosts(config: JSONObject): List<String> {
        val out = LinkedHashSet<String>()
        // `Setting` is the API list in current payloads; `Server` is kept for older payloads.
        for (key in listOf("Setting", "Server")) {
            val arr = config.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                normalize(arr.optString(i))?.let(out::add)
            }
        }
        // `jm3_Server` is a list of [host, label] pairs.
        config.optJSONArray("jm3_Server")?.let { pairs ->
            for (i in 0 until pairs.length()) {
                val pair: JSONArray = pairs.optJSONArray(i) ?: continue
                normalize(pair.optString(0))?.let(out::add)
            }
        }
        return out.toList()
    }

    private fun normalize(value: String?): String? {
        val host = value.orEmpty().trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')
        return host.takeIf { it.isNotBlank() && it.contains('.') }
    }

    /** `GET /setting` must answer with a JSON envelope carrying `code: 200`. */
    private fun probe(host: String): Boolean {
        val time = System.currentTimeMillis() / 1000L
        val request = Request.Builder()
            .url("https://$host/setting?app_img_shunt=1&lang=${session.apiLang}&t=$time")
            .headers(
                Headers.Builder()
                    .add("Tokenparam", "$time,${session.appVersion}")
                    .add("Token", Crypto.md5Hex("$time${ApiClient.TOKEN_SECRET}"))
                    .add("version", ApiClient.CLIENT_VERSION)
                    .build(),
            )
            .build()
        return try {
            probeClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val body = resp.body?.string().orEmpty()
                val code = runCatching { JSONObject(body).optInt("code", -1) }.getOrDefault(-1)
                code == 200
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchConfig(): JSONObject? = withContext(Dispatchers.IO) {
        for (u in hostUrls) {
            val text = try {
                http.newCall(Request.Builder().url(u).build()).execute().use { it.body?.string() }
            } catch (_: Exception) {
                null
            }
            if (text.isNullOrBlank()) continue
            val plain = Crypto.decryptHostText(text) ?: continue
            val json = runCatching { JSONObject(plain) }.getOrNull() ?: continue
            if (json.optJSONArray("Server") != null || json.optJSONArray("Setting") != null) {
                return@withContext json
            }
        }
        val plain = Crypto.decryptHostText(fallbackHostCode) ?: return@withContext null
        runCatching { JSONObject(plain) }.getOrNull()
    }
}
