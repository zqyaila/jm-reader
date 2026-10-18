package com.jm.reader.data.net

import com.jm.reader.data.session.SessionManager
import com.jm.reader.util.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bootstraps and *validates* the API host.
 *
 * The order is deliberate, because it is what the splash screen waits on:
 *
 *  1. build the candidate list **locally first** - last good host, the encrypted payload embedded
 *     in the APK, then the known hosts. No network call is made to obtain candidates.
 *  2. race the top candidates (`GET /setting`) and take the **first** one that answers `code: 200`.
 *     A dead host therefore costs one budget window, not one timeout each.
 *  3. only if every candidate fails, fetch the CDN host list (the old first step) and race that.
 *
 * The previous implementation fetched the CDN config *before* anything else, one URL after the
 * other, with the API client's 15 s/20 s timeouts - so a slow or blocked CDN could hold the splash
 * for ~40 s. The embedded payload already lists the same mobile API hosts, so that call belongs on
 * the failure path, not on the critical path.
 *
 * `GET /setting` also returns `img_host` and `jm3_version`, so the probe applies them directly and
 * the extra settings round-trip the repository used to make is skipped.
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

        /** How many candidates are raced at once. */
        const val PROBE_RACE = 4

        /** Total budget for one probe race, in milliseconds. */
        const val PROBE_BUDGET_MS = 3_500L
    }

    /** Short-timeout client so probing several hosts cannot stall the splash screen. */
    private val probeClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .callTimeout(3500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** Even shorter budget for the off-critical-path CDN fetch. */
    private val configClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(2500, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .callTimeout(4000, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    /** Returns a **verified** API base URL (`https://<host>/`), caching it in the session. */
    suspend fun bootstrap(): Result<String> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()
        // Prefer whatever worked last: it is already known to be reachable.
        session.apiUrl?.let { normalize(it)?.let(candidates::add) }
        candidates.addAll(embeddedCandidates())
        candidates.addAll(knownApiHosts)

        val hosts = candidates.take(MAX_CANDIDATES).toList()
        if (hosts.isEmpty()) return@withContext Result.failure(IllegalStateException("無法取得 API 主機"))

        var winner = raceProbe(hosts)

        // Everything embedded/known failed - only now pay for the CDN list and race its entries.
        if (winner == null) {
            val fresh = fetchConfig()?.let(::candidateHosts).orEmpty()
                .filterNot { it in candidates }
            if (fresh.isNotEmpty()) winner = raceProbe(fresh.take(MAX_CANDIDATES))
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

    /** Candidates from the payload shipped inside the APK - no network, a few milliseconds. */
    private fun embeddedCandidates(): List<String> =
        Crypto.decryptHostText(fallbackHostCode)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let(::candidateHosts)
            .orEmpty()

    /**
     * Probes up to [PROBE_RACE] hosts concurrently and returns the **first** that answers.
     *
     * `firstNotNullOfOrNull { it.await() }` would wait for candidates in list order, so a dead
     * first entry still cost a full timeout; this takes whichever completes first and cancels the
     * rest.
     */
    private suspend fun raceProbe(hosts: List<String>): String? = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope null
        val winners = Channel<String>(Channel.BUFFERED)
        val jobs = hosts.take(PROBE_RACE).map { host ->
            launch(Dispatchers.IO) {
                if (probe(host) != null) winners.send(host)
            }
        }
        val winner = withTimeoutOrNull(PROBE_BUDGET_MS) { winners.receive() }
        jobs.forEach { it.cancel() }
        winner
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

    /**
     * `GET /setting` must answer with a JSON envelope carrying `code: 200`.
     *
     * Returns the payload on success, and applies `img_host` / `jm3_version` from it - this saves
     * the separate settings request the splash used to wait for.
     */
    private fun probe(host: String): JSONObject? {
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
                if (!resp.isSuccessful) return null
                val body = resp.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
                if (json.optInt("code", -1) != 200) return null
                applySettings(json)
                json
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Mirrors `AppRepository.refreshSettings` so the probe payload is not thrown away. */
    private fun applySettings(obj: JSONObject) {
        obj.optString("img_host").takeIf { it.isNotBlank() }?.let { session.imgHost = it }
        val announced = obj.optString("jm3_version").ifBlank { obj.optString("jm3_test_version") }
        if (announced.isNotBlank() && Versions.compare(announced, session.appVersion) > 0) {
            session.appVersion = announced
        }
    }

    /** Fetches both CDN copies concurrently and returns the first usable payload. */
    private suspend fun fetchConfig(): JSONObject? = coroutineScope {
        val results = hostUrls.map { url -> async(Dispatchers.IO) { fetchOne(url) } }
        results.firstNotNullOfOrNull { it.await() }
            ?: Crypto.decryptHostText(fallbackHostCode)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun fetchOne(url: String): JSONObject? = try {
        configClient.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val plain = Crypto.decryptHostText(text) ?: return null
            val json = runCatching { JSONObject(plain) }.getOrNull() ?: return null
            json.takeIf { it.optJSONArray("Server") != null || it.optJSONArray("Setting") != null }
        }
    } catch (_: Exception) {
        null
    }
}
