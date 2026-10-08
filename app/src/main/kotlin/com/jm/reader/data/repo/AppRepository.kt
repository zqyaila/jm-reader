package com.jm.reader.data.repo

import com.jm.reader.data.model.BlogItem
import com.jm.reader.data.model.Category
import com.jm.reader.data.model.CategoryRef
import com.jm.reader.data.model.ComicDetail
import com.jm.reader.data.model.ComicListItem
import com.jm.reader.data.model.CommentItem
import com.jm.reader.data.model.CommentPage
import com.jm.reader.data.model.ForumItem
import com.jm.reader.data.model.GameItem
import com.jm.reader.data.model.Member
import com.jm.reader.data.model.MovieItem
import com.jm.reader.data.model.NovelItem
import com.jm.reader.data.model.ReadData
import com.jm.reader.data.model.TagItem
import com.jm.reader.data.model.bool
import com.jm.reader.data.model.int
import com.jm.reader.data.model.long
import com.jm.reader.data.model.obj
import com.jm.reader.data.model.objList
import com.jm.reader.data.model.parseDailyRecord
import com.jm.reader.data.model.str
import com.jm.reader.data.model.strList
import com.jm.reader.data.model.strOrNull
import com.jm.reader.data.net.ApiClient
import com.jm.reader.data.net.ApiError
import com.jm.reader.data.net.HostManager
import com.jm.reader.data.session.SessionManager
import com.jm.reader.ui.strings.AppStrings
import com.jm.reader.util.Versions
import org.json.JSONArray
import org.json.JSONObject

/** Unified result type used across repositories. */
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

/** Outcome of `POST /daily_chk`. */
data class DailyCheckResult(
    val alreadyCheckedIn: Boolean,
    val message: String?,
)

/** One page of a paged comic list that also reports how many rows exist in total. */
data class HistoryPage(
    val total: Int = 0,
    val items: List<ComicListItem> = emptyList(),
)

/**
 * Thin wrapper over [ApiClient] exposing typed methods for every feature used by the app.
 * Ads, coin purchases and recharge flows are intentionally NOT exposed here.
 */
class AppRepository(
    private val session: SessionManager,
    private val api: ApiClient,
    private val hostManager: HostManager,
) {
    companion object {
        /**
         * Returned by [login] when the server rejects the username / password pair *and* sent no
         * explanation of its own. Screens map it to a localised message.
         */
        const val ERR_BAD_CREDENTIALS = "jm.err.bad_credentials"

        /** Scope used by the adaptive search box ("站内搜索"). */
        const val SITE_SEARCH = "site"

        /** Gender values the mobile `/register` endpoint expects (see JMComic-qt `RegisterReq`). */
        const val GENDER_MALE = "Male"
        const val GENDER_FEMALE = "Female"

        /**
         * `mode` that scopes `/forum` to a single album's comments (reference client:
         * `CommentWidget.readMode[1]`). The other scopes are "all" and "chat".
         */
        const val COMMENT_MODE_ALBUM = "manhua"

        /** `folder_id` that means "every folder" (`GetFavoritesReq2` in the reference client). */
        const val FAVORITE_FOLDER_ALL = "0"
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

    /** GET /promote - returns a list of promote sections. */
    suspend fun getPromote(): RepoResult<List<List<ComicListItem>>> {
        val r = api.get("promote")
        return r.toRepoList { o, arr ->
            (arr ?: JSONArray()).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { sec ->
                    (sec.optJSONArray("content") ?: JSONArray()).let { content ->
                        (0 until content.length()).mapNotNull { content.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                            .distinctBy { it.id }
                    }
                } }
            }
        }
    }

    /** GET /latest?page= */
    suspend fun getLatest(page: Int): RepoResult<List<ComicListItem>> =
        api.get("latest", mapOf("page" to page)).toRepoList { _, arr ->
            (arr ?: JSONArray()).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }

    /** GET /promote_list?id=&page= */
    suspend fun getPromoteList(id: String, page: Int): RepoResult<List<ComicListItem>> =
        api.get("promote_list", mapOf("id" to id, "page" to page)).toRepoList { o, arr ->
            listFromObjOrArr(o, arr)
        }

    /** GET /serialization?type=&date=&page= */
    suspend fun getSerializationMore(type: String?, date: Long?, page: Int): RepoResult<List<ComicListItem>> =
        api.get("serialization", mapOf("type" to type, "date" to date, "page" to page)).toRepoList { o, arr ->
            listFromObjOrArr(o, arr)
        }

    /** GET /week - returns the raw config (categories/type tabs). */
    suspend fun getWeek(): RepoResult<JSONObject> =
        api.get("week").toRepoObj()

    /** GET /week/filter?id=&type= */
    suspend fun getWeekFilter(id: String, type: String): RepoResult<List<ComicListItem>> =
        api.get("week/filter", mapOf("id" to id, "type" to type)).toRepoList { o, arr ->
            listFromObjOrArr(o, arr)
        }

    /** GET /daily?user_id= */
    suspend fun getDaily(userId: String): RepoResult<JSONObject> =
        api.get("daily", mapOf("user_id" to userId)).toRepoObj()

    /** GET /daily_list?user_id= */
    suspend fun getDailyList(userId: String): RepoResult<JSONObject> =
        api.get("daily_list", mapOf("user_id" to userId)).toRepoObj()

    /** POST /daily_chk {user_id, daily_id} */
    suspend fun dailyCheck(userId: String, dailyId: String): RepoResult<DailyCheckResult> {
        val r = api.post("daily_chk", mapOf("user_id" to userId, "daily_id" to dailyId))
        return when (r) {
            is ApiClient.Result.Success -> {
                val obj = r.obj
                if (obj == null) {
                    // The API answers `{"code":200,"data":[]}` when the caller is not
                    // authenticated (verified against the live endpoint): say so plainly.
                    RepoResult.Err(AppStrings.forLanguage(session.language).errUnauthorized)
                } else {
                    val msg = obj.str("msg").ifBlank { r.serverMessage.orEmpty() }
                    val already = listOf("今天已經簽到過了", "已簽到", "已签到", "簽到過", "已完成")
                        .any { msg.contains(it) }
                    RepoResult.Ok(DailyCheckResult(alreadyCheckedIn = already, message = msg.ifBlank { null }))
                }
            }
            is ApiClient.Result.ApiFailure ->
                RepoResult.Err(ApiError.of(session, r.code, r.serverMessage, r.httpStatus))
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, r.message))
        }
    }

    /** POST /daily_list/filter {data} */
    suspend fun dailyListFilter(data: String): RepoResult<JSONObject> =
        api.post("daily_list/filter", mapOf("data" to data)).toRepoObj()

    /**
     * Signs in for today, unless the account has already done so.
     *
     * Used at launch so the reader never loses a day. It is **idempotent without any local state**:
     * `GET /daily` reports whether today is already signed (`signedToday`), so a repeat launch
     * simply finds nothing to do instead of relying on a stored "last check-in" date that could
     * drift out of sync with the server.
     *
     * Returns the server's message when a check-in was actually submitted, or null when there was
     * nothing to do (logged out, already signed, or the daily config could not be read).
     */
    suspend fun autoDailyCheck(): String? {
        val id = uid ?: return null
        val daily = getDaily(id)
        if (daily !is RepoResult.Ok) return null
        val record = parseDailyRecord(daily.data)
        if (record.signedToday || record.dailyId.isBlank()) return null
        return when (val r = dailyCheck(id, record.dailyId)) {
            is RepoResult.Ok -> r.data.message
            is RepoResult.Err -> null
        }
    }

    /** The signed-in member's uid, or null when nobody is logged in. */
    val uid: String?
        get() = session.memberJson
            ?.let { json -> runCatching { JSONObject(json).str("uid") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    // -----------------------------------------------------------------------
    // Search / tags / categories
    // -----------------------------------------------------------------------

    /**
     * GET /search - keyword search.
     *
     * @param searchType scope of the search: "site" (default, everything), "work" (titles only),
     *        "author" (author names only), "tag" or "character". The API echoes the scope it used.
     * @param filter optional JSON string for the advanced filter.
     */
    suspend fun search(
        keyword: String,
        filter: String? = null,
        page: Int = 1,
        searchType: String? = null,
    ): RepoResult<List<ComicListItem>> = searchPage(keyword, searchType, page, filter).map { it.items }

    /**
     * Adaptive search used by the search screen: one box, no mode chooser.
     *
     * `search_type=site` is the scope the site itself uses for "站内搜索" and, verified against the
     * live endpoint, it already matches titles *and* authors *and* tags. When the query is an album
     * id the server answers with `redirect_aid` instead of a list, which [SearchPage] surfaces so
     * the UI can jump straight to the album.
     */
    suspend fun adaptiveSearch(
        keyword: String,
        page: Int = 1,
        filter: String? = null,
    ): RepoResult<SearchPage> = searchPage(keyword, SITE_SEARCH, page, filter)

    /**
     * GET /search - like [search] but keeps the API-reported `total` (and echoed `search_type`)
     * so the UI can show result counts and know when paging is exhausted.
     */
    suspend fun searchPage(
        keyword: String,
        searchType: String? = null,
        page: Int = 1,
        filter: String? = null,
    ): RepoResult<SearchPage> {
        val params = mutableMapOf<String, Any>("search_query" to keyword, "page" to page, "o" to "mr")
        if (!filter.isNullOrBlank()) params["filter"] = filter
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

    /** GET /random_recommend */
    suspend fun randomRecommend(): RepoResult<List<ComicListItem>> =
        api.get("random_recommend").toRepoList { o, arr -> listFromObjOrArr(o, arr) }

    /** GET /categories - response is {categories: [...], blocks: [...]}. */
    suspend fun categories(): RepoResult<List<Category>> =
        api.get("categories").toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("categories")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { Category.fromJson(it) } }
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

    /** GET /album_download_2/{id} */
    suspend fun albumDownload(id: String): RepoResult<JSONObject> =
        api.get("album_download_2/$id").toRepoObj()

    /** GET /hot_tags */
    suspend fun hotTagsV2(): RepoResult<List<TagItem>> = hotTags()

    // -----------------------------------------------------------------------
    // Auth / member
    // -----------------------------------------------------------------------

    /**
     * POST /login {username, password}
     *
     * A rejected pair comes back as HTTP 401 *and* an envelope with `code: 401`, `data: []` and
     * `errorMsg` such as "无效的用户名和/或密码" (verified against the live endpoint). That message is
     * what we hand back so the screen can show it verbatim; [ERR_BAD_CREDENTIALS] is only the
     * fallback when the server said nothing.
     *
     * A success payload carries `jwttoken` (used as `Authorization: Bearer`), plus `uid`, `s`,
     * `username`, `coin`, `level`… `s` doubles as the web client's `AVS` cookie.
     */
    suspend fun login(username: String, password: String): RepoResult<Member> {
        val r = api.post("login", mapOf("username" to username, "password" to password))
        return when (r) {
            is ApiClient.Result.Success -> {
                val data = r.obj
                    ?: return RepoResult.Err(
                        r.serverMessage?.takeIf { it.isNotBlank() } ?: ERR_BAD_CREDENTIALS,
                    )
                session.saveAuth(data.str("jwttoken"), data)
                RepoResult.Ok(Member.fromJson(data))
            }
            is ApiClient.Result.ApiFailure -> {
                val msg = r.serverMessage?.trim().orEmpty()
                when {
                    msg.isNotEmpty() -> RepoResult.Err(msg)
                    r.code == 401 || r.httpStatus == 401 -> RepoResult.Err(ERR_BAD_CREDENTIALS)
                    else -> RepoResult.Err(ApiError.of(session, r.code, null, r.httpStatus))
                }
            }
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, r.message))
        }
    }

    /**
     * POST /register {username,password,password_confirm,email,gender,verification}
     *
     * This is the **mobile** endpoint (not the web `/signup`, which is Cloudflare-gated — verified
     * live: it answers 403). It replies HTTP 200 with an inner status:
     *   `{"code":200,"data":{"status":"ok","msg":"您已注册。检查您的电子邮箱中的确认链接！"}}`
     *   `{"code":200,"data":{"status":"fail","errors":["密码长度小于 8"],"msg":"..."}}`
     *
     * On success the server's own `msg` is returned so the screen can tell the reader that the
     * account still needs e-mail confirmation before the first login.
     */
    suspend fun register(
        username: String,
        password: String,
        passwordConfirm: String,
        email: String,
        gender: String,
    ): RepoResult<String> {
        val r = api.post(
            "register",
            mapOf(
                "username" to username,
                "password" to password,
                "password_confirm" to passwordConfirm,
                "email" to email,
                "gender" to gender,
                // The mobile endpoint accepts an empty captcha; only the web flow needs one.
                "verification" to "",
            ),
        )
        return when (r) {
            is ApiClient.Result.Success -> {
                val failure = registrationFailure(r.obj, r.serverMessage)
                if (failure != null) {
                    RepoResult.Err(failure)
                } else {
                    RepoResult.Ok(
                        r.obj?.str("msg").orEmpty().ifBlank {
                            AppStrings.forLanguage(session.language).registerSuccess
                        },
                    )
                }
            }
            is ApiClient.Result.ApiFailure ->
                RepoResult.Err(ApiError.of(session, r.code, r.serverMessage, r.httpStatus))
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, r.message))
        }
    }

    /**
     * `/register` answers HTTP 200 even when it refuses the submission, reporting
     * `{ "status": "fail", "msg": "...", "errors": ["..."] }` (or `status: "ok"` on success).
     * Returns the reported problem, or null when the registration went through.
     */
    private fun registrationFailure(obj: JSONObject?, envelopeMessage: String?): String? {
        val o = obj ?: return envelopeMessage?.takeIf { it.isNotBlank() }
        val errors = o.opt("errors")
        val errorList = when (errors) {
            is JSONArray -> (0 until errors.length())
                .mapNotNull { errors.optString(it)?.takeIf { m -> m.isNotBlank() } }
            is String -> listOfNotNull(errors.takeIf { it.isNotBlank() })
            else -> emptyList()
        }
        val status = o.str("status").lowercase()
        val failed = errorList.isNotEmpty() ||
            status == "fail" || status == "error" || status == "false"
        if (!failed) return null
        return errorList.joinToString("\n")
            .ifBlank { o.str("msg") }
            .ifBlank { envelopeMessage.orEmpty() }
            .ifBlank { AppStrings.forLanguage(session.language).registerFailed }
    }

    /** POST /forgot {email} */
    suspend fun forgot(email: String): RepoResult<JSONObject> =
        api.post("forgot", mapOf("email" to email)).toRepoObj()

    /** POST /logout */
    suspend fun logout(): RepoResult<JSONObject> =
        api.post("logout").toRepoObj()

    /** GET /useredit/{uid} */
    suspend fun getUserInfo(uid: String): RepoResult<JSONObject> =
        api.get("useredit/$uid").toRepoObj()

    /** POST /useredit/{uid} */
    suspend fun editUser(uid: String, form: Map<String, Any?>): RepoResult<JSONObject> =
        api.post("useredit/$uid", form).toRepoObj()

    // -----------------------------------------------------------------------
    // Library / favorites / history / likes
    // -----------------------------------------------------------------------

    /**
     * GET /favorite?page=&folder_id=&o= (o: "mr" favorite time / "mp" update time).
     *
     * [folderId] must not be blank. `folder_id` is how the server picks which collection to list,
     * and the API client drops empty query params - so an empty default sent **no** `folder_id` at
     * all, which is why the library's favorites tab could come back empty even with favorites
     * saved. The reference client (`GetFavoritesReq2`) explicitly sends `"0"` for "everything".
     */
    suspend fun favorites(page: Int, folderId: String = FAVORITE_FOLDER_ALL, o: String = "mr"): RepoResult<List<ComicListItem>> =
        api.get("favorite", mapOf("page" to page, "folder_id" to folderId, "o" to o)).toRepoList { o2, arr ->
            listFromObjOrArr(o2, arr)
        }

    /**
     * POST /favorite {aid} - toggles the album in the account's favorites.
     *
     * Reported as an acknowledgement rather than a payload: the endpoint answers
     * `{"code":200,"data":[]}` on success, and the old `toRepoObj()` mapping treated a missing
     * object as `noData` - so a favourite that *did* save looked like a failure. Callers ignored
     * the result and re-fetched the album instead, which is why the heart only turned red after a
     * manual refresh.
     */
    suspend fun addFavorite(aid: String): RepoResult<Unit> =
        api.post("favorite", mapOf("aid" to aid)).toRepoAck()

    /** POST /favorite_folder - create/edit/delete favorite folders. */
    suspend fun editFavoriteFolder(type: String, folderId: String? = null, folderName: String? = null, aid: String? = null): RepoResult<JSONObject> {
        val p = mutableMapOf<String, Any>("type" to type)
        if (!folderId.isNullOrBlank()) p["folder_id"] = folderId
        if (!folderName.isNullOrBlank()) p["folder_name"] = folderName
        if (!aid.isNullOrBlank()) p["aid"] = aid
        return api.post("favorite_folder", p).toRepoObj()
    }

    /** POST /like {id, like_type} */
    suspend fun addLike(id: String, likeType: String = "album"): RepoResult<JSONObject> =
        api.post("like", mapOf("id" to id, "like_type" to likeType)).toRepoObj()

    /** GET /tags_favorite - response items are {tag, updated_at}. */
    suspend fun tagsFavorite(): RepoResult<List<TagItem>> =
        api.get("tags_favorite").toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("list")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { TagItem.fromJson(it) } }
            }
        }

    /** POST /tags_favorite_update {type, tags} */
    suspend fun updateTagsFavorite(type: String, tags: String): RepoResult<JSONObject> =
        api.post("tags_favorite_update", mapOf("type" to type, "tags" to tags)).toRepoObj()

    // -----------------------------------------------------------------------
    // History (watch list)
    // -----------------------------------------------------------------------

    /** GET /watch_list?page= - raw response, kept for callers that want the envelope. */
    suspend fun watchList(page: Int): RepoResult<JSONObject> =
        api.get("watch_list", mapOf("page" to page)).toRepoObj()

    /**
     * GET /watch_list?page= - the account's viewing history, newest first.
     *
     * This is the *cloud* half of the history tab: `addWatch` writes to it whenever a logged-in
     * reader opens a chapter, but nothing used to read it back, which is why cloud history looked
     * empty. The payload is `{"list": [<album>, …], "total": <int>}` with the same album shape as
     * `/latest` (reference client: `GetHistoryReq2` → `ToolUtil.ParseHistoryReq2`), so
     * [ComicListItem] parses it directly.
     *
     * Requires a logged-in session - an anonymous call answers
     * `HTTP 401 / code 401 / errorMsg "Authentication fail."`.
     */
    suspend fun cloudHistory(page: Int = 1): RepoResult<HistoryPage> =
        api.get("watch_list", mapOf("page" to page)).toRepoList { o, arr ->
            val items = listFromObjOrArr(o, arr)
            HistoryPage(
                total = o?.int("total", 0)?.takeIf { it > 0 } ?: items.size,
                items = items,
            )
        }

    /** POST /watch_list {id} - marks an album as read. */
    suspend fun addWatch(id: String): RepoResult<JSONObject> =
        api.post("watch_list", mapOf("id" to id)).toRepoObj()

    /** POST /album_sertracking {id} - track a comic for updates. */
    suspend fun trackAlbum(id: String): RepoResult<JSONObject> =
        api.post("album_sertracking", mapOf("id" to id)).toRepoObj()

    /** POST /album_tracking {page} - tracked comics list. */
    suspend fun trackedAlbums(page: Int): RepoResult<JSONObject> =
        api.post("album_tracking", mapOf("page" to page)).toRepoObj()

    // -----------------------------------------------------------------------
    // Notifications
    // -----------------------------------------------------------------------

    /** GET /notifications?type=&page= */
    suspend fun notifications(page: Int): RepoResult<JSONObject> =
        api.get("notifications", mapOf("page" to page)).toRepoObj()

    /** GET /notifications/unreadCount */
    suspend fun notificationsUnread(): RepoResult<JSONObject> =
        api.get("notifications/unreadCount").toRepoObj()

    // -----------------------------------------------------------------------
    // Novels
    // -----------------------------------------------------------------------

    /** GET /novels - params {o, t} (o: "" latest / mv / mp / tf; t: "a" content type). */
    suspend fun novels(o: String = "", t: String = "a"): RepoResult<List<NovelItem>> =
        api.get("novels", mapOf("o" to o, "t" to t)).toRepoList { o2, arr ->
            objArrFromResponse(o2, arr, listOf("list")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { NovelItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }

    /** GET /novel - params {nid}. */
    suspend fun novelDetail(nid: String): RepoResult<JSONObject> =
        api.get("novel", mapOf("nid" to nid)).toRepoObj()

    /** GET /novelchapters - params {ncid}. */
    suspend fun novelChapters(ncid: String): RepoResult<JSONObject> =
        api.get("novelchapters", mapOf("ncid" to ncid)).toRepoObj()

    /** GET /search_novels?search_query= */
    suspend fun searchNovels(keyword: String): RepoResult<List<NovelItem>> =
        api.get("search_novels", mapOf("search_query" to keyword)).toRepoList { o2, arr ->
            objArrFromResponse(o2, arr, listOf("list")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { NovelItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }

    /** GET /novel_favorites - params {page, folder_id, o}. */
    suspend fun novelFavorites(page: Int = 1, folderId: String = "", o: String = "mr"): RepoResult<JSONObject> =
        api.get("novel_favorites", mapOf("page" to page, "folder_id" to folderId, "o" to o)).toRepoObj()

    // -----------------------------------------------------------------------
    // Movies
    // -----------------------------------------------------------------------

    /** GET /videos - params {page, video_type?}. */
    suspend fun movies(page: Int = 1, videoType: String = "movie"): RepoResult<List<MovieItem>> =
        api.get("videos", mapOf("page" to page, "video_type" to videoType)).toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("list")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { MovieItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }

    /** GET /latest_hanime */
    suspend fun latestHanime(): RepoResult<List<MovieItem>> =
        api.get("latest_hanime").toRepoList { o, arr ->
            val ja = arr ?: JSONArray()
            (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { MovieItem.fromJson(it) } }
                .distinctBy { it.id }
        }

    /** GET /video - params {id, video_type}. */
    suspend fun movieInfo(id: String, videoType: String = "movie"): RepoResult<JSONObject> =
        api.get("video", mapOf("id" to id, "video_type" to videoType)).toRepoObj()

    // -----------------------------------------------------------------------
    // Games
    // -----------------------------------------------------------------------

    /** GET /allgames - params {page, search?, category?, game_type?}. Response `games` array. */
    suspend fun games(page: Int = 1): RepoResult<List<GameItem>> =
        api.get("allgames", mapOf("page" to page)).toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("games", "hot_games")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { GameItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }

    /** GET /game/{id} */
    suspend fun gameInfo(id: String): RepoResult<JSONObject> =
        api.get("game/$id").toRepoObj()

    // -----------------------------------------------------------------------
    // Blogs / forum
    // -----------------------------------------------------------------------

    /** GET /blogs - params {page, blog_type}. */
    suspend fun blogs(page: Int = 1, blogType: String = "dinner"): RepoResult<List<BlogItem>> =
        api.get("blogs", mapOf("page" to page, "blog_type" to blogType)).toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("list")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { BlogItem.fromJson(it) } }
                    .distinctBy { it.id }
            }
        }

    /** GET /blog?id= */
    suspend fun blogInfo(id: String): RepoResult<JSONObject> =
        api.get("blog", mapOf("id" to id)).toRepoObj()

    /** GET /forum - params {mode, page}. */
    suspend fun forum(mode: String = "all", page: Int = 1): RepoResult<List<ForumItem>> =
        api.get("forum", mapOf("mode" to mode, "page" to page)).toRepoList { o, arr ->
            objArrFromResponse(o, arr, listOf("list")).let { ja ->
                (0 until ja.length()).mapNotNull { ja.optJSONObject(it)?.let { ForumItem.fromJson(it) } }
                    .distinctBy { it.cid }
            }
        }

    /** POST /comment - send a comment/topic. */
    suspend fun sendComment(params: Map<String, Any?>): RepoResult<JSONObject> =
        api.post("comment", params).toRepoObj()

    /** POST /comment_vote */
    suspend fun voteComment(id: String): RepoResult<JSONObject> =
        api.post("comment_vote", mapOf("id" to id)).toRepoObj()

    // -----------------------------------------------------------------------
    // Album comments
    // -----------------------------------------------------------------------

    /**
     * GET /forum?mode=&aid=&page= - the comment list of one album.
     *
     * `mode="manhua"` is the scope the reference client (JMComic-qt `GetCommentReq2`) uses for an
     * album's own comments; the same endpoint also serves the site-wide forum with other modes.
     * The decrypted payload is `{"total": <int>, "list": [<comment>, …]}`, and each entry may carry
     * nested `replys`.
     */
    suspend fun albumComments(aid: String, page: Int = 1, mode: String = COMMENT_MODE_ALBUM): RepoResult<CommentPage> =
        api.get("forum", mapOf("mode" to mode, "aid" to aid, "page" to page)).toRepoList { o, arr ->
            when {
                arr != null -> CommentPage(
                    total = arr.length(),
                    items = (0 until arr.length())
                        .mapNotNull { i -> arr.optJSONObject(i)?.let { CommentItem.fromJson(it) } },
                )
                else -> {
                    val list = o?.objList("list") ?: emptyList()
                    CommentPage(
                        total = o?.int("total", 0)?.takeIf { it > 0 } ?: list.size,
                        items = list.map { CommentItem.fromJson(it) },
                    )
                }
            }
        }

    /**
     * POST /comment {comment, aid[, comment_id]} - publish a comment on an album, or reply to an
     * existing comment when [replyToCid] is given (JMComic-qt `SendCommentReq2`).
     *
     * Requires a logged-in session. Verified against the live endpoint: an anonymous POST answers
     * `HTTP 401 / code 401 / errorMsg "請先登入會員"`, i.e. this mobile route really does accept
     * posts (the `jmcomic` python library claims it does not, and posts to the web route instead).
     * That message is surfaced verbatim by [ApiError.of], so the reader is told to log in rather
     * than shown a bare 401.
     */
    suspend fun postComment(aid: String, content: String, replyToCid: String? = null): RepoResult<JSONObject> {
        val params = mutableMapOf<String, Any?>("comment" to content, "aid" to aid)
        if (!replyToCid.isNullOrBlank()) params["comment_id"] = replyToCid
        // An empty `data` on a 200 means "posted, nothing to say about it" - not a failure.
        return api.post("comment", params).toRepoObjAck()
    }

    /**
     * POST /comment_vote {id} - like a comment.
     *
     * Only called for logged-in readers: the live endpoint answers an anonymous vote with an empty
     * `HTTP 200` body (no envelope at all), which would surface as "server did not respond" -
     * a misleading thing to show someone who merely is not logged in.
     */
    suspend fun likeComment(cid: String): RepoResult<Unit> =
        api.post("comment_vote", mapOf("id" to cid)).toRepoAck()

    /** Absolute URL for a comment avatar, or null when the comment has no picture. */
    fun commentAvatar(photo: String?): String? {
        val file = photo?.trim().orEmpty()
        if (file.isEmpty()) return null
        if (file.startsWith("http")) return file
        val host = session.imgHost.ifBlank { "https://cdn-msp3.jmdanjonproxy.vip" }
        return "$host/media/users/${file.trimStart('/')}"
    }

    // -----------------------------------------------------------------------
    // Tasks / achievements (kept - not ad related)
    // -----------------------------------------------------------------------

    /** GET /tasks?type=&filter= */
    suspend fun tasks(type: String, filter: String? = null): RepoResult<JSONObject> =
        api.get("tasks", mapOf("type" to type, "filter" to filter)).toRepoObj()

    // -----------------------------------------------------------------------
    // Creator
    // -----------------------------------------------------------------------

    /** GET /creator_author */
    suspend fun creators(): RepoResult<JSONObject> =
        api.get("creator_author").toRepoObj()

    /** GET /creator_work?uid= */
    suspend fun creatorWorks(uid: String): RepoResult<JSONObject> =
        api.get("creator_work", mapOf("uid" to uid)).toRepoObj()

    // -----------------------------------------------------------------------
    // Helper adapters
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
     * For write endpoints whose success is the *code*, not the payload.
     *
     * `/favorite` and `/comment_vote` answer `{"code":200,"data":[]}` when they worked - there is
     * no object to return, and treating that as `noData` made every successful write look like a
     * failure to the caller.
     */
    private fun ApiClient.Result.toRepoAck(): RepoResult<Unit> =
        when (this) {
            is ApiClient.Result.Success ->
                if (code == 200) RepoResult.Ok(Unit)
                else RepoResult.Err(ApiError.of(session, code, serverMessage))
            is ApiClient.Result.ApiFailure ->
                RepoResult.Err(ApiError.of(session, code, serverMessage, httpStatus))
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, message))
        }

    /** Like [toRepoObj], but a 200 with an empty `data` is a success carrying an empty object. */
    private fun ApiClient.Result.toRepoObjAck(): RepoResult<JSONObject> =
        when (this) {
            is ApiClient.Result.Success ->
                if (code == 200) RepoResult.Ok(obj ?: JSONObject())
                else RepoResult.Err(ApiError.of(session, code, serverMessage))
            is ApiClient.Result.ApiFailure ->
                RepoResult.Err(ApiError.of(session, code, serverMessage, httpStatus))
            is ApiClient.Result.NetworkFailure -> RepoResult.Err(ApiError.network(session, message))
        }

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
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
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
                return (0 until c.length()).mapNotNull { c.optJSONObject(it)?.let { ComicListItem.fromJson(it) } }
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
            return emptyList()
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

    // -----------------------------------------------------------------------
    // Image URL helpers
    // -----------------------------------------------------------------------

    /** Cover URL for comic list/grid items. */
    fun comicCover(id: String, updateAt: Long): String {
        val host = session.imgHost.ifBlank { "https://cdn-msp3.jmdanjonproxy.vip" }
        return "$host/media/albums/${id}_3x4.jpg?v=$updateAt"
    }

    /** Cover URL for detail (uses addtime). */
    fun comicCoverDetail(id: String, addtime: Long): String = comicCover(id, addtime)

    /** Prefixes a relative media path with the current image host (e.g. /media/novels/x.jpg). */
    fun imgUrl(path: String): String {
        if (path.startsWith("http")) return path
        val host = session.imgHost.ifBlank { "https://cdn-msp3.jmdanjonproxy.vip" }
        return host + "/" + path.trimStart('/')
    }

    /** Current member info. */
    val member: Member?
        get() = session.memberJson?.let { json ->
            runCatching { Member.fromJson(JSONObject(json)) }.getOrNull()
        }
}
