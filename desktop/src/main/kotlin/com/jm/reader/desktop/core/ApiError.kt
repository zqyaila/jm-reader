package com.jm.reader.desktop.core

/**
 * Turns an API failure into something a reader can act on.
 *
 * The 18comic/JM API answers with a JSON envelope where `code != 200` and the human explanation
 * lives in `errorMsg` (or `message`). Passing that text straight to the UI is what replaces bare
 * strings like "API 錯誤 (401)" with e.g. "无效的用户名和/或密码".
 *
 * Only when the server said nothing useful do we fall back to a localised, code-agnostic phrase -
 * a numeric code is never shown on its own.
 */
object ApiError {

    /** Pure mapping, so it can be unit tested without any UI. */
    fun of(
        strings: AppStrings,
        code: Int,
        serverMessage: String? = null,
        httpStatus: Int = 0,
    ): String {
        serverMessage?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return when {
            code == 401 || httpStatus == 401 -> strings.errUnauthorized
            code == 403 || httpStatus == 403 -> strings.errForbidden
            code == 404 || httpStatus == 404 -> strings.errNotFound
            code == 429 || httpStatus == 429 -> strings.errRateLimited
            // Only the *HTTP* status means "server broke": the API has its own numeric `code`
            // space (the reference client's enum starts at 1001), so a large JSON code must not
            // be mistaken for a 5xx.
            httpStatus >= 500 -> strings.errServer
            code <= 0 && httpStatus <= 0 -> strings.errNetwork
            code > 0 -> strings.errRequestFailedFmt.format(code)
            else -> strings.errRequestFailedFmt.format(httpStatus)
        }
    }

    fun of(
        session: Session,
        code: Int,
        serverMessage: String? = null,
        httpStatus: Int = 0,
    ): String = of(AppStrings.forLanguage(session.language), code, serverMessage, httpStatus)

    /** Transport-level failures: keep the useful hints, drop raw socket noise. */
    fun network(strings: AppStrings, raw: String?): String {
        val text = raw?.trim().orEmpty()
        return when {
            text.contains("timeout", ignoreCase = true) ||
                text.contains("timed out", ignoreCase = true) -> strings.errTimeout
            text.contains("Unable to resolve host", ignoreCase = true) -> strings.errNetwork
            text.contains("UnknownHost", ignoreCase = true) -> strings.errNetwork
            text.isBlank() -> strings.errNetwork
            text.contains("API 主機尚未設定") -> strings.errNoHost
            else -> strings.errNetwork
        }
    }

    fun network(session: Session, raw: String?): String =
        network(AppStrings.forLanguage(session.language), raw)

    /** No API host could be reached at all. */
    fun noHost(session: Session): String =
        AppStrings.forLanguage(session.language).errNoHost
}
