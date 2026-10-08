package com.jm.reader.desktop.core

import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// JSON helpers (org.json based; the API responses are dynamic / loosely typed).
// Ported verbatim from the Android module's `data/model/Models.kt`, where these
// live alongside the domain models.
// ---------------------------------------------------------------------------

fun JSONObject.str(key: String, def: String = ""): String =
    if (has(key) && !isNull(key)) optString(key, def) else def

fun JSONObject.strOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key, null) else null

fun JSONObject.int(key: String, def: Int = 0): Int =
    if (has(key) && !isNull(key)) optInt(key, def) else def

fun JSONObject.long(key: String, def: Long = 0L): Long =
    if (has(key) && !isNull(key)) optLong(key, def) else def

fun JSONObject.bool(key: String, def: Boolean = false): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key, def) else def

fun JSONObject.obj(key: String): JSONObject? =
    if (has(key) && !isNull(key)) optJSONObject(key) else null

/** Reads a field that may be a string, a JSON array of strings, or absent. */
fun JSONObject.strList(key: String): List<String> {
    if (!has(key) || isNull(key)) return emptyList()
    return when (val v = opt(key)) {
        is JSONArray -> (0 until v.length()).map { v.optString(it) }.filter { it.isNotBlank() }
        is JSONObject -> emptyList()
        else -> {
            val s = v?.toString() ?: ""
            if (s.isNotBlank()) listOf(s) else emptyList()
        }
    }
}

/**
 * First non-blank value of a field that may be a bare string **or** an array of strings, else null.
 *
 * The API is inconsistent about this: `/latest` and `/search` send `author` as a single string,
 * while `/album` sends it as an array. An empty array must yield null - falling back to
 * `optString` would put the literal `"[]"` on a card as if it were an author name.
 */
fun JSONObject.firstStr(key: String): String? =
    strList(key).firstOrNull() ?: (opt(key) as? String)?.takeIf { it.isNotBlank() }

/** Reads a field that may be a JSONObject, a JSON array, or absent (returns the list or empty). */
fun JSONObject.objList(key: String): List<JSONObject> {
    if (!has(key) || isNull(key)) return emptyList()
    val v = opt(key)
    if (v is JSONArray) {
        return (0 until v.length()).mapNotNull { v.optJSONObject(it) }
    }
    return emptyList()
}

/** Parses a raw body string to a JSONObject, or null if it is not a JSON object. */
fun String.toJsonObjectOrNull(): JSONObject? =
    runCatching { JSONObject(this) }.getOrNull()
