package com.jm.reader.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Bootstraps and *validates* the API host. Ported from the Android module's
 * `data/net/HostManager.kt`.
 *
 * 1. fetch the encrypted host config from the CDN txt files
 * 2. decrypt with key = ASCII(md5("diosfjckwpqpdfjkvnqQjsik")) (AES-256-ECB)
 * 3. collect candidate hosts from `Setting` / `Server` / `jm3_Server`
 * 4. **probe each candidate with a real API call** (`GET /setting`) and cache the first one that
 *    answers with a `code: 200` envelope
 * 5. fall back to the embedded payload / previously cached host only if nothing else works
 */
class HostManager(
    private val session: Session,
    private val http: OkHttpClient,
) {
    private val hostUrls = listOf(
        "https://rup4a04-c02.tos-cn-hongkong.bytepluses.com/newsvr-2025.txt",
        "https://rup4a04-c01.tos-ap-southeast-1.bytepluses.com/newsvr-2025.txt",
    )

    /** Encrypted host config baked into the web bundle (REACT_APP_HOST_BACKUP_CODE). */
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

        /** The lines screen lists at most this many hosts. */
        const val MAX_LINES = 8
    }

    /** One row on the lines screen: a host and how long it took to answer, if it did. */
    data class Line(val host: String, val ms: Long?)

    /** Every host this client is willing to talk to, best guess first. */
    fun candidateHosts(): List<String> = linkedSetOf<String>().apply {
        normalize(session.preferredApiHost)?.let(::add)
        session.apiUrl?.let { normalize(it)?.let(::add) }
        addAll(embeddedCandidates())
        addAll(knownApiHosts)
    }.take(MAX_LINES).toList()

    /** Explicit pick from the lines screen; "" means automatic. */
    var preferredHost: String
        get() = session.preferredApiHost
        set(value) {
            session.preferredApiHost = normalize(value).orEmpty()
        }

    /** Applies a pick immediately, so the next request uses it without a restart. */
    fun use(host: String?) {
        val clean = normalize(host)
        preferredHost = clean.orEmpty()
        if (clean != null) session.apiUrl = "https://$clean/"
    }

    /** Probes every candidate concurrently, for the lines screen. */
    suspend fun measureAll(): List<Line> = coroutineScope {
        candidateHosts().map { host ->
            async(Dispatchers.IO) {
                val started = System.nanoTime()
                val ok = probe(host)
                Line(host, if (ok) (System.nanoTime() - started) / 1_000_000 else null)
            }
        }.map { it.await() }
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

    /**
     * Returns a **verified** API base URL (`https://<host>/`), caching it in the session.
     *
     * Order matters because this is what the splash waits on:
     *  1. build the candidate list locally - explicit pick, last good host, the encrypted payload
     *     shipped in the jar, then the known hosts. No network call to obtain candidates.
     *  2. race the top candidates with `GET /setting` and take the first `code: 200`.
     *  3. only if all of them fail, fetch the CDN config (the old first step) and race that.
     *
     * The old version fetched the CDN list before anything else, one URL after the other, so a slow
     * or blocked CDN held the splash for tens of seconds before probing even started.
     */
    suspend fun bootstrap(): Result<String> = withContext(Dispatchers.IO) {
        val candidates = linkedSetOf<String>()
        normalize(session.preferredApiHost)?.let(candidates::add)
        session.apiUrl?.let { normalize(it)?.let(candidates::add) }
        candidates.addAll(embeddedCandidates())
        candidates.addAll(knownApiHosts)

        val hosts = candidates.take(MAX_CANDIDATES).toList()
        if (hosts.isEmpty()) return@withContext Result.failure(IllegalStateException("無法取得 API 主機"))

        var winner = raceProbe(hosts)

        if (winner == null) {
            val fresh = fetchConfig()?.let(::candidateHosts).orEmpty().filterNot { it in candidates }
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

    /** Candidates from the payload shipped with the app - no network, a few milliseconds. */
    private fun embeddedCandidates(): List<String> =
        Crypto.decryptHostText(fallbackHostCode)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.let(::candidateHosts)
            .orEmpty()

    /**
     * Probes up to [PROBE_RACE] hosts concurrently and returns whichever answers first.
     *
     * `firstNotNullOfOrNull { it.await() }` waits in list order, so a dead first entry still cost a
     * full timeout; a channel returns the first *completion* instead.
     */
    private suspend fun raceProbe(hosts: List<String>): String? = coroutineScope {
        if (hosts.isEmpty()) return@coroutineScope null
        val winners = Channel<String>(Channel.BUFFERED)
        val jobs = hosts.take(PROBE_RACE).map { host ->
            launch(Dispatchers.IO) { if (probe(host)) winners.send(host) }
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

    /** Fetches both CDN copies concurrently and returns the first usable payload. */
    private suspend fun fetchConfig(): JSONObject? = coroutineScope {
        val results = hostUrls.map { url -> async(Dispatchers.IO) { fetchOne(url) } }
        results.firstNotNullOfOrNull { it.await() }
            ?: Crypto.decryptHostText(fallbackHostCode)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun fetchOne(url: String): JSONObject? = try {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val plain = Crypto.decryptHostText(text) ?: return null
            val json = runCatching { JSONObject(plain) }.getOrNull() ?: return null
            json.takeIf { it.optJSONArray("Server") != null || it.optJSONArray("Setting") != null }
        }
    } catch (_: Exception) {
        null
    }
}
