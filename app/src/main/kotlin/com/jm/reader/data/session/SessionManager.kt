package com.jm.reader.data.session

import android.content.Context
import android.content.SharedPreferences
import com.jm.reader.ui.strings.UiLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Stores login/session/host state. Mirrors what the web app keeps in localStorage.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_session", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_LANG = "TW"

        /** Fallback app version used in the `Tokenparam` header until `/setting` reports one. */
        const val DEFAULT_APP_VERSION = "2.1.7"
        private const val KEY_JWT = "jwttoken"
        private const val KEY_MEMBER = "memberInfo"
        private const val KEY_LANG = "lang"
        private const val KEY_API_URL = "apiUrl"
        private const val KEY_AUTH_EXPIRY = "authExpiry"
        private const val KEY_IMG_HOST = "imgHost"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_LOGGED_IN = "loggedIn"
    }

    // --- Auth ---

    init {
        // Migrate installs written before the explicit login flag existed: a stored member payload
        // is what the old `isLoggedIn` used, so honour it once and persist the flag.
        if (!prefs.contains(KEY_LOGGED_IN) && !prefs.getString(KEY_MEMBER, null).isNullOrBlank()) {
            prefs.edit().putBoolean(KEY_LOGGED_IN, true).apply()
        }
    }

    var jwtToken: String?
        get() = prefs.getString(KEY_JWT, null)
        set(value) = prefs.edit().putString(KEY_JWT, value).apply()

    var memberJson: String?
        get() = prefs.getString(KEY_MEMBER, null)
        set(value) = prefs.edit().putString(KEY_MEMBER, value).apply()

    var language: UiLanguage
        get() = runCatching {
            UiLanguage.valueOf(prefs.getString(KEY_LANG, UiLanguage.ZH_TW.name) ?: UiLanguage.ZH_TW.name)
        }.getOrDefault(UiLanguage.ZH_TW)
        set(value) = prefs.edit().putString(KEY_LANG, value.name).apply()

    /** Server-side language param sent on GET requests (TW/CN only). */
    val apiLang: String
        get() = language.apiLang

    /** The `s` field of memberInfo used as the AVS cookie value. */
    val avsSession: String?
        get() = memberJson?.let { runCatching { JSONObject(it).optString("s") }.getOrNull() }

    /**
     * A session counts as logged in when a login actually succeeded and its member payload is
     * still stored. The flag is written by [saveAuth] and cleared by [clearAuth].
     *
     * Whether requests then authenticate with `jwttoken` (as `Authorization: Bearer`) or with the
     * payload's `s` field (as the `AVS` cookie) is decided per request - the mobile API returns
     * `jwttoken`, the web client relies on `s`, and we send whichever we hold.
     */
    val isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_LOGGED_IN, false) && memberJson?.isNotBlank() == true

    private val _loggedIn = MutableStateFlow(isLoggedIn)

    /** Emits on every login / logout so screens refresh without being recreated. */
    val loggedInFlow: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun saveAuth(token: String, memberData: JSONObject) {
        prefs.edit()
            .putString(KEY_JWT, token)
            .putString(KEY_MEMBER, memberData.toString())
            // The previous implementation invented a one-hour client-side expiry here; the JWT
            // lifetime is decided by the server (a 401 clears the session), so there is nothing
            // to expire locally.
            .putBoolean(KEY_LOGGED_IN, true)
            .apply()
        _loggedIn.value = isLoggedIn
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_JWT)
            .remove(KEY_MEMBER)
            .remove(KEY_AUTH_EXPIRY)
            .putBoolean(KEY_LOGGED_IN, false)
            .apply()
        _loggedIn.value = false
    }

    // --- Host ---

    var apiUrl: String?
        get() = prefs.getString(KEY_API_URL, null)
        set(value) = prefs.edit().putString(KEY_API_URL, value).apply()

    var imgHost: String
        get() = prefs.getString(KEY_IMG_HOST, "") ?: ""
        set(value) = prefs.edit().putString(KEY_IMG_HOST, value).apply()

    /**
     * App version sent in `Tokenparam` (`"<ts>,<version>"`). `/setting` advertises the current
     * one, so we follow it instead of hard-coding a stale value.
     */
    var appVersion: String
        get() = prefs.getString(KEY_APP_VERSION, DEFAULT_APP_VERSION)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_APP_VERSION
        set(value) = prefs.edit().putString(KEY_APP_VERSION, value).apply()
}
