package com.jm.reader.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.URLEncoder

/**
 * Low-level API client that mirrors `jmcomic.JmCryptoTool` + JMComic-qt's `ServerReq`.
 * Ported one-for-one from the Android module's `data/net/ApiClient.kt`.
 *
 *  - GET requests append query params + `lang`; POST requests send a urlencoded FormBody
 *  - Every request carries `Tokenparam` = "<unixSecs>,<appVersion>" and
 *    `Token` = md5("<unixSecs>185Hcomic3PAPP7R")
 *  - Logged-in requests add `Authorization: Bearer <jwttoken>` and `Cookie: AVS=<s>`
 *  - Responses come back as `{ "code": 200, "data": "<base64 AES-256-ECB ciphertext>" }` and are
 *    decrypted with key = ASCII(md5("<unixSecs><secret>")) using the *same* timestamp that was
 *    sent in this request.
 */
class ApiClient(
    private val session: Session,
    private val http: OkHttpClient,
) {
    companion object {
        /** Value advertised by the reference client (`GlobalConfig.HeaderVer`). */
        const val APP_VERSION = "2.1.7"

        /** Client build string sent in the `version` header (`config.UpdateVersion`). */
        const val CLIENT_VERSION = "v1.3.6"

        /** Token secret (`JmMagicConstants.APP_TOKEN_SECRET`). */
        const val TOKEN_SECRET = "185Hcomic3PAPP7R"

        /** Response secret (`JmMagicConstants.APP_DATA_SECRET`). */
        const val DATA_SECRET = "185Hcomic3PAPP7R"

        /** Only `/chapter_view_template` uses the second secret (`APP_TOKEN_SECRET_2`). */
        const val CONTENT_TOKEN_SECRET = "18comicAPPContent"

        /** Paths served with the second secret + a plain md5(secret) response key. */
        private val CONTENT_PATHS = listOf("chapter_view_template")
    }

    sealed class Result {
        /** The server answered with a JSON envelope whose `code` is 200. */
        data class Success(
            val code: Int,
            val obj: JSONObject? = null,
            val arr: JSONArray? = null,
            /** `errorMsg` / `message` from the envelope, when the server sent one. */
            val serverMessage: String? = null,
        ) : Result()

        /**
         * The server rejected the request. [serverMessage] is the server's own explanation and
         * should be shown verbatim when present.
         */
        data class ApiFailure(
            val code: Int,
            val serverMessage: String? = null,
            val httpStatus: Int = 0,
        ) : Result()

        data class NetworkFailure(val message: String) : Result()
    }

    suspend fun get(path: String, params: Map<String, Any?> = emptyMap()): Result =
        request("GET", path, params)

    suspend fun post(path: String, params: Map<String, Any?> = emptyMap()): Result =
        request("POST", path, params)

    private suspend fun request(method: String, path: String, params: Map<String, Any?>): Result =
        withContext(Dispatchers.IO) {
            val base = session.apiUrl ?: return@withContext Result.NetworkFailure("API 主機尚未設定")
            val time = System.currentTimeMillis() / 1000L
            val url = if (method == "GET") buildGetUrl(base, path, params) else joinUrl(base, path)
            val request = buildRequest(method, url, params, time)
            // A 401 only means "your session died" for authenticated endpoints. A rejected
            // *login* / failed *registration* must never log the current user out.
            val isAuthEndpoint = path.trimStart('/').startsWith("login") ||
                path.trimStart('/').startsWith("register") ||
                path.trimStart('/').startsWith("logout")
            val authenticated = !isAuthEndpoint &&
                (!session.jwtToken.isNullOrBlank() || !session.avsSession.isNullOrBlank())

            var attempt = 0
            while (attempt < 3) {
                attempt++
                try {
                    http.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        val result = parseResponse(body, url, time, resp.code)
                        if (result is Result.ApiFailure) {
                            if (result.httpStatus == 401 && authenticated) {
                                // Token expired / invalid - drop the stale session so the UI
                                // shows logged-out. Never do this for a failed *login*.
                                session.clearAuth()
                            }
                            val retryable = result.httpStatus == 429 || result.httpStatus >= 500
                            if (retryable && attempt < 3) {
                                delay(400L * attempt)
                                // Continue the retry loop rather than returning this failure.
                            } else {
                                return@withContext result
                            }
                        } else {
                            return@withContext result
                        }
                    }
                } catch (e: IOException) {
                    if (attempt < 3) {
                        delay(600L * attempt)
                        continue
                    }
                    return@withContext Result.NetworkFailure(e.message ?: "網路錯誤")
                }
            }
            Result.NetworkFailure("請求失敗")
        }

    private fun joinUrl(base: String, path: String): String {
        val b = base.trimEnd('/')
        val p = path.trimStart('/')
        return "$b/$p"
    }

    private fun buildGetUrl(base: String, path: String, params: Map<String, Any?>): String {
        val url = joinUrl(base, path)
        val pairs = params.filter { (_, v) -> v != null && v != "" && v != Unit }
            .map { (k, v) -> "${enc(k)}=${enc(v.toString())}" }
        val query = (pairs + "lang=${enc(session.apiLang)}").joinToString("&")
        return if (query.isNotEmpty()) "$url?$query" else url
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun buildRequest(method: String, url: String, params: Map<String, Any?>, time: Long): Request {
        val isContent = CONTENT_PATHS.any { url.contains(it) }
        val secret = if (isContent) CONTENT_TOKEN_SECRET else TOKEN_SECRET
        val tokenParam = "$time,${session.appVersion}"
        val token = Crypto.md5Hex("$time$secret")
        val headers = Headers.Builder()
            .add("Tokenparam", tokenParam)
            .add("Token", token)
            .add("version", CLIENT_VERSION)
        // Only send credentials we actually hold.
        session.jwtToken?.takeIf { it.isNotBlank() }?.let { headers.add("Authorization", "Bearer $it") }
        session.avsSession?.takeIf { it.isNotBlank() }?.let { headers.add("Cookie", "AVS=$it") }
        val requestHeaders = headers.build()

        val builder = Request.Builder().url(url).headers(requestHeaders)
        if (method == "POST") {
            val fb = FormBody.Builder()
            params.filter { (_, v) -> v != null }.forEach { (k, v) -> fb.add(k, v.toString()) }
            // The server localises `errorMsg` from this parameter, so send it on POSTs too.
            if (params.keys.none { it.equals("lang", ignoreCase = true) }) {
                fb.add("lang", session.apiLang)
            }
            builder.post(fb.build())
        }
        return builder.build()
    }

    private fun parseResponse(body: String, url: String, time: Long, httpStatus: Int): Result {
        val envelope = body.toJsonObjectOrNull()
            ?: run {
                if (httpStatus >= 400) {
                    return Result.ApiFailure(httpStatus, null, httpStatus)
                }
                return Result.NetworkFailure(
                    if (body.isBlank()) "伺服器沒有回應" else "響應格式錯誤",
                )
            }

        val code = envelope.int("code", -1)
        val serverMessage = readServerMessage(envelope)
        val dataRaw = envelope.opt("data")

        // An API-level failure: surface the server's own text instead of a numeric code.
        if (code != 200) {
            return Result.ApiFailure(code, serverMessage, httpStatus)
        }

        // `data` that is not a string was not encrypted (empty arrays for an unauthenticated
        // `/daily_chk`, or plain JSON objects for `/register`).
        if (dataRaw !is String) {
            return Result.Success(code, dataRaw as? JSONObject, dataRaw as? JSONArray, serverMessage)
        }

        val isContent = CONTENT_PATHS.any { url.contains(it) }
        val secrets = if (isContent) listOf(CONTENT_TOKEN_SECRET) else listOf(DATA_SECRET, CONTENT_TOKEN_SECRET)
        for (secret in secrets) {
            val keyHex = if (isContent) Crypto.md5Hex(secret) else Crypto.md5Hex("$time$secret")
            val plain = Crypto.aesEcbDecrypt(dataRaw, keyHex) ?: continue
            val value = runCatching { JSONTokener(plain).nextValue() }.getOrNull()
            when (value) {
                is JSONObject -> return Result.Success(code, value, null, serverMessage)
                is JSONArray -> return Result.Success(code, null, value, serverMessage)
                else -> return Result.ApiFailure(code, serverMessage, httpStatus)
            }
        }
        return Result.ApiFailure(code, serverMessage, httpStatus)
    }

    /** `errorMsg` is used by API errors, `message` by some endpoints; either may be a JSON array. */
    private fun readServerMessage(envelope: JSONObject): String? {
        for (key in listOf("errorMsg", "message", "msg")) {
            if (!envelope.has(key) || envelope.isNull(key)) continue
            val raw = envelope.opt(key)
            val text = when (raw) {
                is JSONArray -> (0 until raw.length()).joinToString("\n") { raw.optString(it) }
                null -> ""
                else -> raw.toString()
            }.trim()
            if (text.isNotEmpty()) return text
        }
        return null
    }
}
