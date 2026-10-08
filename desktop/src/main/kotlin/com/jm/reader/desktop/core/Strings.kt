package com.jm.reader.desktop.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UI languages. The API server only accepts `lang` = "TW" | "CN" (the web app never sends "EN"),
 * so English maps to "CN" for server-side data while all UI chrome is localised locally.
 */
enum class UiLanguage(val apiLang: String, val code: String, val displayName: String) {
    ZH_CN("CN", "zh-CN", "简体中文"),
    ZH_TW("TW", "zh-TW", "繁體中文"),
    EN("CN", "en", "English"),
}

/** Persists and broadcasts the current UI language so the whole tree recomposes on change. */
class LanguageManager(private val session: Session) {

    private val _language = MutableStateFlow(session.language)
    val language: StateFlow<UiLanguage> = _language.asStateFlow()

    init {
        _language.value = session.language
    }

    fun set(lang: UiLanguage) {
        if (session.language != lang) session.language = lang
        if (_language.value != lang) _language.value = lang
    }
}

/**
 * Desktop UI strings.
 *
 * This is a **subset** of the Android module's `AppStrings` (822 lines x 3 languages): only the
 * keys the desktop screens actually render. Keeping the field names identical to the Android table
 * means the two can be diffed, and the follow-up refactor that lifts the whole table into a shared
 * module is mechanical (see SCOPE_AND_ACCEPTANCE.md).
 */
data class AppStrings(
    // App & navigation
    val appName: String,
    val home: String,
    val search: String,
    val settings: String,
    val back: String,
    val retry: String,
    val refresh: String,
    val loading: String,
    val close: String,
    val open: String,
    val cancel: String,
    val noMore: String,
    val noResult: String,
    val noData: String,
    val clear: String,
    // Home
    val forYou: String,
    val latest: String,
    val searchHint: String,
    val searchTotalFmt: String, // "共 %1$d 部作品"
    val hotTagsTitle: String,
    val searchLooksLikeId: String,
    // Detail
    val comicDetail: String,
    val startReading: String,
    val authorLabel: String,
    val tagLabel: String,
    val views: String,
    val pages: String,
    val likes: String,
    val chapters: String,
    val singleFileWork: String,
    val relatedWorks: String,
    val noDescription: String,
    val descriptionLabel: String,
    val loadFail: String,
    val workIdLabel: String,
    val workIdCopied: String,
    // Reader
    val chapterList: String,
    val chooseChapter: String,
    val prevChapter: String,
    val nextChapter: String,
    val imageLoadFail: String,
    val current: String,
    val singleFile: String,
    val chapterFmt: String, // "第 %1$d 話"
    // Settings
    val language: String,
    val darkTheme: String,
    val apiHost: String,
    val appVersion: String,
    val about: String,
    val disclaimer: String,
    val resetSession: String,
    val clearImageCache: String,
    val imageCacheCleared: String,
    val sessionCleared: String,
    // Errors
    val errUnauthorized: String,
    val errForbidden: String,
    val errNotFound: String,
    val errRateLimited: String,
    val errServer: String,
    val errNetwork: String,
    val errTimeout: String,
    val errNoHost: String,
    val errRequestFailedFmt: String, // "請求失敗（%1$d）"
) {
    companion object {
        fun forLanguage(language: UiLanguage): AppStrings = when (language) {
            UiLanguage.ZH_CN -> ZH_CN
            UiLanguage.ZH_TW -> ZH_TW
            UiLanguage.EN -> EN
        }
    }
}

private val ZH_CN = AppStrings(
    appName = "JM Reader",
    home = "首页",
    search = "搜索",
    settings = "设置",
    back = "返回",
    retry = "重试",
    refresh = "刷新",
    loading = "加载中…",
    close = "关闭",
    open = "打开",
    cancel = "取消",
    noMore = "没有更多了",
    noResult = "没有找到结果",
    noData = "服务器没有返回数据",
    clear = "清空",
    forYou = "推荐",
    latest = "最新",
    searchHint = "搜索作品 / 作者，或直接输入 JM 号",
    searchTotalFmt = "共 %1\$d 部作品",
    hotTagsTitle = "热门标签",
    searchLooksLikeId = "看起来是作品 ID，回车直接打开",
    comicDetail = "作品详情",
    startReading = "开始阅读",
    authorLabel = "作者",
    tagLabel = "标签",
    views = "浏览",
    pages = "页数",
    likes = "点赞",
    chapters = "章节",
    singleFileWork = "单话作品",
    relatedWorks = "相关作品",
    noDescription = "暂无简介",
    descriptionLabel = "简介",
    loadFail = "加载失败",
    workIdLabel = "作品 ID",
    workIdCopied = "已复制作品 ID",
    chapterList = "章节列表",
    chooseChapter = "选择章节",
    prevChapter = "上一话",
    nextChapter = "下一话",
    imageLoadFail = "图片加载失败",
    current = "当前",
    singleFile = "单话",
    chapterFmt = "第 %1\$d 话",
    language = "语言",
    darkTheme = "深色主题",
    apiHost = "API 主机",
    appVersion = "版本",
    about = "关于",
    disclaimer = "非官方第三方客户端，仅供个人使用与技术学习。内容版权归原作者所有，本应用不托管任何内容。",
    resetSession = "清除登录状态",
    clearImageCache = "清除图片缓存",
    imageCacheCleared = "图片缓存已清除",
    sessionCleared = "登录状态已清除",
    errUnauthorized = "登录状态已失效，请重新登录",
    errForbidden = "没有权限访问该内容",
    errNotFound = "内容不存在或已被删除",
    errRateLimited = "请求过于频繁，请稍后再试",
    errServer = "服务器暂时不可用，请稍后重试",
    errNetwork = "网络连接失败，请检查网络",
    errTimeout = "请求超时，请稍后重试",
    errNoHost = "无法连接到服务器，请检查网络后重试",
    errRequestFailedFmt = "请求失败（%1\$d）",
)

private val ZH_TW = AppStrings(
    appName = "JM Reader",
    home = "首頁",
    search = "搜尋",
    settings = "設定",
    back = "返回",
    retry = "重試",
    refresh = "重新整理",
    loading = "載入中…",
    close = "關閉",
    open = "開啟",
    cancel = "取消",
    noMore = "沒有更多了",
    noResult = "沒有找到結果",
    noData = "伺服器沒有回傳資料",
    clear = "清空",
    forYou = "推薦",
    latest = "最新",
    searchHint = "搜尋作品 / 作者，或直接輸入 JM 號",
    searchTotalFmt = "共 %1\$d 部作品",
    hotTagsTitle = "熱門標籤",
    searchLooksLikeId = "看起來是作品 ID，按 Enter 直接開啟",
    comicDetail = "作品詳情",
    startReading = "開始閱讀",
    authorLabel = "作者",
    tagLabel = "標籤",
    views = "瀏覽",
    pages = "頁數",
    likes = "讚",
    chapters = "章節",
    singleFileWork = "單話作品",
    relatedWorks = "相關作品",
    noDescription = "暫無簡介",
    descriptionLabel = "簡介",
    loadFail = "載入失敗",
    workIdLabel = "作品 ID",
    workIdCopied = "已複製作品 ID",
    chapterList = "章節列表",
    chooseChapter = "選擇章節",
    prevChapter = "上一話",
    nextChapter = "下一話",
    imageLoadFail = "圖片載入失敗",
    current = "目前",
    singleFile = "單話",
    chapterFmt = "第 %1\$d 話",
    language = "語言",
    darkTheme = "深色主題",
    apiHost = "API 主機",
    appVersion = "版本",
    about = "關於",
    disclaimer = "非官方第三方客戶端，僅供個人使用與技術學習。內容版權歸原作者所有，本應用不託管任何內容。",
    resetSession = "清除登入狀態",
    clearImageCache = "清除圖片快取",
    imageCacheCleared = "圖片快取已清除",
    sessionCleared = "登入狀態已清除",
    errUnauthorized = "登入狀態已失效，請重新登入",
    errForbidden = "沒有權限存取該內容",
    errNotFound = "內容不存在或已被刪除",
    errRateLimited = "請求過於頻繁，請稍後再試",
    errServer = "伺服器暫時無法使用，請稍後重試",
    errNetwork = "網路連線失敗，請檢查網路",
    errTimeout = "請求逾時，請稍後重試",
    errNoHost = "無法連線到伺服器，請檢查網路後重試",
    errRequestFailedFmt = "請求失敗（%1\$d）",
)

private val EN = AppStrings(
    appName = "JM Reader",
    home = "Home",
    search = "Search",
    settings = "Settings",
    back = "Back",
    retry = "Retry",
    refresh = "Refresh",
    loading = "Loading…",
    close = "Close",
    open = "Open",
    cancel = "Cancel",
    noMore = "No more results",
    noResult = "No results found",
    noData = "The server returned no data",
    clear = "Clear",
    forYou = "Featured",
    latest = "Latest",
    searchHint = "Search titles / authors, or type a JM id",
    searchTotalFmt = "%1\$d works found",
    hotTagsTitle = "Hot tags",
    searchLooksLikeId = "That looks like a work ID — press Enter to open it",
    comicDetail = "Comic detail",
    startReading = "Start reading",
    authorLabel = "Author",
    tagLabel = "Tags",
    views = "Views",
    pages = "Pages",
    likes = "Likes",
    chapters = "Chapters",
    singleFileWork = "Single chapter",
    relatedWorks = "Related",
    noDescription = "No description",
    descriptionLabel = "Description",
    loadFail = "Failed to load",
    workIdLabel = "Work ID",
    workIdCopied = "Work ID copied",
    chapterList = "Chapters",
    chooseChapter = "Choose a chapter",
    prevChapter = "Previous",
    nextChapter = "Next",
    imageLoadFail = "Image failed to load",
    current = "Current",
    singleFile = "Single",
    chapterFmt = "Chapter %1\$d",
    language = "Language",
    darkTheme = "Dark theme",
    apiHost = "API host",
    appVersion = "Version",
    about = "About",
    disclaimer = "Unofficial third-party client, for personal use and technical study only. All content is copyright its original authors; this app hosts nothing.",
    resetSession = "Sign out / clear session",
    clearImageCache = "Clear image cache",
    imageCacheCleared = "Image cache cleared",
    sessionCleared = "Session cleared",
    errUnauthorized = "Your session expired — please sign in again",
    errForbidden = "You do not have access to this content",
    errNotFound = "Content not found or removed",
    errRateLimited = "Too many requests — please try again later",
    errServer = "The server is temporarily unavailable, please try again",
    errNetwork = "Network connection failed — check your connection",
    errTimeout = "Request timed out, please try again",
    errNoHost = "Could not reach any server — check your connection and retry",
    errRequestFailedFmt = "Request failed (%1\$d)",
)
