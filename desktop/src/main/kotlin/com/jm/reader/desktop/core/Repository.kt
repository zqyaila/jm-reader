package com.jm.reader.desktop.core

import org.json.JSONArray
import org.json.JSONObject

/** Unified result type used across the repository. */
sealed class RepoResult<out T> {
    data class Ok<T>(val data: T) : RepoResult<T>()
    data class Err(val message: String) : RepoResult<Nothing>()

    fun <R> map(fn: (T) -> R): RepoResult<R> = when (this) {
        is Ok -> Ok(fn(data))
        is Err -> this
    }
}

/**
 * One page of `search` results: the comics plus the API-reported total and search scope.
 *
 * `redirectAid` is set by the server when the query is an album id: the API answers with
 * `{"total":1,"redirect_aid":<id>,"content":[]}` instead of a result list, which is exactly what
 * makes a single adaptive search box possible.
 */
data class SearchPage(
    val query: String = "",
    val searchType: String = "site",
    val total: Int = 0,
    val redirectAid: String? = null,
    val items: List<ComicListItem> = emptyList(),
)

/**
 * Thin wrapper over [ApiClient] exposing typed methods for the desktop feature set.
 *
 * Scope note: this covers the reading path (browse → search → detail → read). The member
 * features (login, favourites, comments, downloads, daily check-in) are **not** ported yet — see
 * SCOPE_AND_ACCEPTANCE.md for the phase plan. Ads/coin flows are intentionally absent, as on
 * Android.
 */
class Repository(
    private val session: Session,
    private val api: ApiClient,
    private val hostManager: HostManager,
) {
    companion object {
        /** Scope used by the adaptive search box ("站内搜索"). */
        const val SITE_SEARCH = "site"

        /** Sort key used for latest/`search` ordering. */
        const val ORDER_LATEST = "mr"

        /** Fallback cover CDN used until `GET /setting` reports the real `img_host`. */
        const val DEFAULT_IMG_HOST = "https://cdn-msp3.jmdanjonproxy.vip"
    }

    // -----------------------------------------------------------------------
    // Bootstrap
    // -----------------------------------------------------------------------

    /**
     * Ensures a **working** API host is configured, then refreshes app settings (img_host).
     *
     * [HostManager.bootstrap] already probes every candidate with `GET /setting`; if even that
     * fails we retry once (the network may have been flaky) before giving up with a readable
     * message instead of a raw failure.
     */
    suspend fun bootstrap(): RepoResult<String> {
        var url = hostManager.bootstrap().getOrNull()
        if (url.isNullOrBlank()) {
            url = hostManager.bootstrap().getOrNull()
        }
        if (url.isNullOrBlank()) return RepoResult.Err(ApiError.noHost(session))
        refreshSettings()
        return RepoResult.Ok(url)
    }

    /** GET /setting - stores img_host (cover CDN) into the session. */
    suspend fun refreshSettings() {
        val r = api.get("setting", mapOf("app_img_shunt" to "1", "t" to System.currentTimeMillis() / 1000))
        if (r is ApiClient.Result.Success) {
            r.obj?.let { obj ->
                val host = obj.str("img_host")
                if (host.isNotBlank()) session.imgHost = host
                // `jm3_version` is the *mobile* app version (the plain `version` field is the
                // legacy 1.x one); adopt it only when it is newer, exactly like the reference
                // client, so the Tokenparam header never moves backwards.
                val announced = obj.str("jm3_version").ifBlank { obj.str("jm3_test_version") }
                if (announced.isNotBlank() && Versions.compare(announced, session.appVersion) > 0) {
                    session.appVersion = announced
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Home / comic lists
    // -----------------------------------------------------------------------

    /** GET /promote - returns a list of promote sections, flattened to one carousel. */
    suspend fun getPromote(): RepoResult<List<ComicListItem>> {
        val r = api.get("promote")
        return r.toRepoList { _, arr ->
            val ja = arr ?: JSONArray()
            (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { sec ->
                val content = sec.optJSONArray("content") ?: JSONArray()
                (0 until content.length())
                    .mapNotNull { content.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                    .distinctBy { it.id }
            } }.flatten().distinctBy { it.id }
        }
    }

    /** GET /latest?page= */
    suspend fun getLatest(page: Int): RepoResult<List<ComicListItem>> =
        api.get("latest", mapOf("page" to page)).toRepoList { _, arr ->
            val ja = arr ?: JSONArray()
            (0 until ja.length())
                .mapNotNull { ja.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                .distinctBy { it.id }
        }

    /** GET /random_recommend */
    suspend fun randomRecommend(): RepoResult<List<ComicListItem>> =
        api.get("random_recommend").toRepoList { o, arr -> listFromObjOrArr(o, arr) }

    // -----------------------------------------------------------------------
    // Search / tags / categories
    // -----------------------------------------------------------------------

    /** The adaptive search used by the search screen: one box, no mode chooser. */
    suspend fun adaptiveSearch(keyword: String, page: Int = 1): RepoResult<SearchPage> =
        searchPage(keyword, SITE_SEARCH, page)

    /**
     * GET /search - keeps the API-reported `total` (and echoed `search_type`) so the UI can show
     * result counts and know when paging is exhausted.
     */
    suspend fun searchPage(
        keyword: String,
        searchType: String? = null,
        page: Int = 1,
    ): RepoResult<SearchPage> {
        val params = mutableMapOf<String, Any>(
            "search_query" to keyword,
            "page" to page,
            "o" to ORDER_LATEST,
        )
        if (!searchType.isNullOrBlank()) params["search_type"] = searchType
        return api.get("search", params).toRepoList { o, arr ->
            val redirect = o?.int("redirect_aid", 0) ?: 0
            SearchPage(
                query = o?.str("search_query")?.ifBlank { keyword } ?: keyword,
                searchType = o?.str("search_type").orEmpty().ifBlank { searchType ?: SITE_SEARCH },
                total = o?.long("total")?.toInt() ?: 0,
                redirectAid = if (redirect > 0) redirect.toString() else null,
                items = parseComicList(o, arr),
            )
        }
    }

    /** GET /hot_tags */
    suspend fun hotTags(): RepoResult<List<TagItem>> =
        api.get("hot_tags").toRepoList { o, arr ->
            val list = (arr ?: o?.optJSONArray("data") ?: JSONArray())
            (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let { TagItem.fromJson(it) } }
        }

    /** GET /categories - response is {categories: [...], blocks: [...]}. */
    suspend fun categories(): RepoResult<List<Category>> =
        api.get("categories").toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("categories")).let { ja ->
                (0 until ja.length())
                    .mapNotNull { ja.optJSONObject(it)?.let { Category.fromJson(it) } }
                    .distinctBy { it.slug }
            }
        }

    /** GET /categories/filter?c=&o=&page= - c is the category slug, o the sort key. */
    suspend fun categoriesFilter(c: String, o: String = "", page: Int = 1): RepoResult<List<ComicListItem>> =
        api.get("categories/filter", mapOf("c" to c, "o" to o, "page" to page)).toRepoList { o2, arr ->
            listFromObjOrArr(o2, arr)
        }

    // -----------------------------------------------------------------------
    // Comic detail / reader
    // -----------------------------------------------------------------------

    /** GET /album?id= */
    suspend fun getAlbum(id: String): RepoResult<ComicDetail> =
        api.get("album", mapOf("id" to id)).toRepoList { o, _ ->
            ComicDetail.fromJson(o ?: JSONObject())
        }

    /** GET /comic_read?id= */
    suspend fun comicRead(id: String): RepoResult<ReadData> =
        api.get("comic_read", mapOf("id" to id)).toRepoList { o, _ ->
            ReadData.fromJson(o ?: JSONObject())
        }

    // -----------------------------------------------------------------------
    // Image URL helpers
    // -----------------------------------------------------------------------

    /** Cover URL for comic list/grid items. */
    fun comicCover(id: String, updateAt: Long): String {
        val host = session.imgHost.ifBlank { DEFAULT_IMG_HOST }
        return "$host/media/albums/${id}_3x4.jpg?v=$updateAt"
    }

    /** Prefixes a relative media path with the current image host. */
    fun imgUrl(path: String): String {
        if (path.startsWith("http")) return path
        val host = session.imgHost.ifBlank { DEFAULT_IMG_HOST }
        return host + "/" + path.trimStart('/')
    }

    // -----------------------------------------------------------------------
    // Helper adapters (ported from the Android AppRepository)
    // -----------------------------------------------------------------------

    private inline fun <T> ApiClient.Result.toRepoList(fn: (JSONObject?, JSONArray?) -> T): RepoResult<T> =
        when (this) {
            is ApiClient.Result.Success ->
                if (code == 200) RepoResult.Ok(fn(obj, arr))
                else RepoResult.Err(ApiError.of(session, code, serverMessage))
            is ApiClient.Result.ApiFailure ->
                RepoResult.Err(ApiError.of(session, code, serverMessage, httpStatus))
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, message))
        }

    /**
     * Retained for the phase-2 member endpoints (`/week`, `/daily`, `/forum`), which answer with
     * a bare object rather than a list. Kept next to [toRepoList] so the two adapters stay
     * visibly symmetric.
     */
    @Suppress("unused")
    private fun ApiClient.Result.toRepoObj(): RepoResult<JSONObject> =
        when (this) {
            is ApiClient.Result.Success ->
                if (code == 200 && obj != null) RepoResult.Ok(obj)
                else if (code != 200) RepoResult.Err(ApiError.of(session, code, serverMessage))
                else RepoResult.Err(AppStrings.forLanguage(session.language).noData)
            is ApiClient.Result.ApiFailure ->
                RepoResult.Err(ApiError.of(session, code, serverMessage, httpStatus))
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, message))
        }

    private fun listFromObjOrArr(o: JSONObject?, arr: JSONArray?): List<ComicListItem> {
        if (arr != null && arr.length() > 0) {
            return (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                .distinctBy { it.id }
        }
        val data = o?.obj("data")
        val candidates = listOf(
            data?.optJSONArray("content"),
            data?.optJSONArray("list"),
            o?.optJSONArray("content"),
            o?.optJSONArray("list"),
        )
        for (c in candidates) {
            if (c != null && c.length() > 0) {
                return (0 until c.length())
                    .mapNotNull { c.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }
        return emptyList()
    }

    /** Reads a comic list from either a bare JSON array or a `{ content | list }` wrapper. */
    private fun parseComicList(o: JSONObject?, arr: JSONArray?): List<ComicListItem> {
        val list = o?.obj("data") ?: o
        if (list != null) {
            val content = list.objList("content")
            if (content.isNotEmpty()) return content.map { ComicListItem.fromJson(it) }.distinctBy { it.id }
            val rows = list.objList("list")
            if (rows.isNotEmpty()) return rows.map { ComicListItem.fromJson(it) }.distinctBy { it.id }
            return listFromObjOrArr(o, arr)
        }
        return listFromObjOrArr(o, arr)
    }

    /** Extracts a JSON array of objects from the response for non-comic sections. */
    private fun objArrFromResponse(o: JSONObject?, arr: JSONArray?, keys: List<String>): JSONArray {
        if (arr != null) return arr
        o?.let {
            for (key in keys) {
                it.optJSONArray(key)?.let { ja -> if (ja.length() > 0) return ja }
                it.obj("data")?.optJSONArray(key)?.let { ja -> if (ja.length() > 0) return ja }
            }
        }
        return JSONArray()
    }
}
