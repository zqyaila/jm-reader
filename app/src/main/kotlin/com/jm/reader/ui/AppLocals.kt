package com.jm.reader.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.jm.reader.data.download.DownloadManager
import com.jm.reader.data.history.HistoryManager
import com.jm.reader.data.history.SearchHistoryManager
import com.jm.reader.data.repo.AppRepository
import com.jm.reader.data.session.SessionManager
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.ui.strings.LanguageManager

val LocalRepository = staticCompositionLocalOf<AppRepository> {
    error("LocalRepository not provided")
}

val LocalSession = staticCompositionLocalOf<SessionManager> {
    error("LocalSession not provided")
}

val LocalAppStrings = staticCompositionLocalOf<AppStrings> {
    error("LocalAppStrings not provided")
}

val LocalLanguageManager = staticCompositionLocalOf<LanguageManager> {
    error("LocalLanguageManager not provided")
}

val LocalDownloadManager = staticCompositionLocalOf<DownloadManager> {
    error("LocalDownloadManager not provided")
}

val LocalHistoryManager = staticCompositionLocalOf<HistoryManager> {
    error("LocalHistoryManager not provided")
}

val LocalSearchHistoryManager = staticCompositionLocalOf<SearchHistoryManager> {
    error("LocalSearchHistoryManager not provided")
}
