package com.jm.reader.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The two looks this app ships.
 *
 * [ARCHIVE] is the default: paper and ink, near-square corners, monospace file codes.
 * [EMBER] is the previous look, kept because some readers prefer the warmer, rounder version.
 *
 * The choice is a *theme*, not a skin: it swaps the colour scheme, the shape scale and the
 * backdrop together. Mixing (say) archive colours with 24 dp corners is what makes a half-applied
 * theme look broken.
 */
enum class AppTheme {
    ARCHIVE,
    EMBER,
    ;

    companion object {
        fun of(value: String?): AppTheme =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ARCHIVE
    }
}

/** Which theme the current composition renders; used by the themed backdrop and glass fog. */
val LocalAppTheme = staticCompositionLocalOf { AppTheme.ARCHIVE }

/**
 * Persists the reader's theme choice and exposes it as a flow.
 *
 * A tiny separate preferences file rather than adding another field to [SessionManager]: the theme
 * is a display preference, and it must still apply when somebody is logged out.
 */
class ThemeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jm_theme", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(AppTheme.of(prefs.getString(KEY, null)))

    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    val current: AppTheme get() = _theme.value

    fun set(theme: AppTheme) {
        if (_theme.value == theme) return
        _theme.value = theme
        prefs.edit().putString(KEY, theme.name).apply()
    }

    private companion object {
        const val KEY = "theme"
    }
}
