package com.jm.reader.desktop.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.prefs.Preferences

/**
 * Stores login / session / host state, mirroring what the Android module keeps in
 * `SharedPreferences` and what the web app keeps in `localStorage`.
 *
 * Backend: `java.util.prefs` (the JDK's built-in preference store). On Windows that lands in
 * `HKCU\Software\JavaSoft\Prefs\com\jm\reader`, so it survives reinstalls of the app — which is
 * what you want for a login token. No third-party dependency, no file-format to maintain.
 *
 * Every access is defensive: a preference store can be unavailable (locked registry hive, a
 * sandbox that denies the backing store, a headless CI runner). Rather than crashing the whole
 * app on a settings read, the value falls back to an in-memory map that lasts for the session.
 */
class Session {

    companion object {
        const val DEFAULT_LANG = "TW"

        /** Fallback app version used in the `Tokenparam` header until `/setting` reports one. */
        const val DEFAULT_APP_VERSION = "2.1.7"

        private const val NODE = "com/jm/reader"
        private const val KEY_JWT = "jwttoken"
        private const val KEY_MEMBER = "memberInfo"
        private const val KEY_LANG = "lang"
        private const val KEY_API_URL = "apiUrl"
        private const val KEY_AUTH_EXPIRY = "authExpiry"
        private const val KEY_IMG_HOST = "imgHost"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_LOGGED_IN = "loggedIn"
    }

    private val node: Preferences? = runCatching { Preferences.userRoot().node(NODE) }.getOrNull()

    /** Last-resort store used only when [node] is null or throws. */
    private val memory = HashMap<String, String>()
    private val memoryFlags = HashMap<String, Boolean>()

    private fun readString(key: String): String? =
        node?.let { runCatching { it.get(key, null) }.getOrNull() } ?: memory[key]

    private fun writeString(key: String, value: String?) {
        if (value == null) memory.remove(key) else memory[key] = value
        node?.let { n ->
            runCatching {
                if (value == null) n.remove(key) else n.put(key, value)
                n.flush()
            }
        }
    }

    private fun readFlag(key: String, def: Boolean): Boolean =
        node?.let { runCatching { it.getBoolean(key, def) }.getOrNull() } ?: memoryFlags[key] ?: def

    private fun writeFlag(key: String, value: Boolean) {
        memoryFlags[key] = value
        node?.let { n -> runCatching { n.putBoolean(key, value); n.flush() } }
    }

    init {
        // Migrate installs written before the explicit login flag existed: a stored member payload
        // is what the old `isLoggedIn` used, so honour it once and persist the flag.
        if (readString(KEY_LOGGED_IN) == null && !readString(KEY_MEMBER).isNullOrBlank()) {
            writeFlag(KEY_LOGGED_IN, true)
        }
    }

    // --- Auth ---

    var jwtToken: String?
        get() = readString(KEY_JWT)
        set(value) = writeString(KEY_JWT, value)

    var memberJson: String?
        get() = readString(KEY_MEMBER)
        set(value) = writeString(KEY_MEMBER, value)

    var language: UiLanguage
        get() = runCatching {
            UiLanguage.valueOf(readString(KEY_LANG) ?: UiLanguage.ZH_TW.name)
        }.getOrDefault(UiLanguage.ZH_TW)
        set(value) = writeString(KEY_LANG, value.name)

    /** Server-side language param sent on GET requests (TW/CN only). */
    val apiLang: String
        get() = language.apiLang

    /** The `s` field of memberInfo used as the AVS cookie value. */
    val avsSession: String?
        get() = memberJson?.let { runCatching { JSONObject(it).optString("s") }.getOrNull() }

    /**
     * A session counts as logged in when a login actually succeeded and its member payload is
     * still stored. The flag is written by [saveAuth] and cleared by [clearAuth].
     */
    val isLoggedIn: Boolean
        get() = readFlag(KEY_LOGGED_IN, false) && memberJson?.isNotBlank() == true

    private val _loggedIn = MutableStateFlow(isLoggedIn)

    /** Emits on every login / logout so screens refresh without being recreated. */
    val loggedInFlow: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun saveAuth(token: String, memberData: JSONObject) {
        writeString(KEY_JWT, token)
        writeString(KEY_MEMBER, memberData.toString())
        writeFlag(KEY_LOGGED_IN, true)
        _loggedIn.value = isLoggedIn
    }

    fun clearAuth() {
        writeString(KEY_JWT, null)
        writeString(KEY_MEMBER, null)
        writeString(KEY_AUTH_EXPIRY, null)
        writeFlag(KEY_LOGGED_IN, false)
        _loggedIn.value = false
    }

    // --- Host ---

    var apiUrl: String?
        get() = readString(KEY_API_URL)
        set(value) = writeString(KEY_API_URL, value)

    var imgHost: String
        get() = readString(KEY_IMG_HOST).orEmpty()
        set(value) = writeString(KEY_IMG_HOST, value)

    /**
     * App version sent in `Tokenparam` (`"<ts>,<version>"`). `/setting` advertises the current
     * one, so we follow it instead of hard-coding a stale value.
     */
    var appVersion: String
        get() = readString(KEY_APP_VERSION)?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_VERSION
        set(value) = writeString(KEY_APP_VERSION, value)
}
