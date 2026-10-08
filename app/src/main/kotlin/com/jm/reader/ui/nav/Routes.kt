package com.jm.reader.ui.nav

import android.net.Uri

object Routes {
    const val SPLASH = "splash"

    // Bottom-nav shell
    const val MAIN = "main"
    const val TAB_HOME = "tab_home"
    const val TAB_CATEGORIES = "tab_categories"
    const val TAB_LIBRARY = "tab_library"
    const val TAB_MEMBER = "tab_member"

    /**
     * Search screen. Both query args are optional, so `search` works for a plain search while
     * [search] builds deep links for author / keyword / id searches.
     *   mode -> "work" (default) | "author" | "id"
     *   q    -> pre-filled query (keyword, author name or comic id)
     */
    const val SEARCH = "search?mode={mode}&q={q}"
    const val CATEGORIES = "categories"
    const val WEEK = "week"
    const val DAILY = "daily"
    const val HOT_TAGS = "hotTags"
    const val NOVELS = "novels"
    const val MOVIES = "movies"
    const val GAMES = "games"
    const val BLOGS = "blogs"
    const val FORUM = "forum"

    const val COMIC_DETAIL = "comic/{id}"
    const val READER = "reader/{id}?readId={readId}"
    const val COMIC_LIST = "comicList/{type}?title={title}&id={id}"
    const val OFFLINE_READER = "offline/{id}"

    const val NOVEL_DETAIL = "novel/{id}"
    const val MOVIE_DETAIL = "movie/{id}"
    const val GAME_DETAIL = "game/{id}"
    const val BLOG_DETAIL = "blog/{id}"

    /** Login screen with an optional pre-filled account name (e.g. right after registering). */
    const val LOGIN = "login?username={username}"
    const val REGISTER = "register"

    fun comicDetail(id: String) = "comic/$id"
    fun login(username: String? = null): String =
        if (username.isNullOrBlank()) "login" else "login?username=" + Uri.encode(username)
    fun search(mode: String? = null, q: String? = null): String {
        val params = mutableListOf<String>()
        if (!mode.isNullOrBlank()) params += "mode=" + Uri.encode(mode)
        if (!q.isNullOrBlank()) params += "q=" + Uri.encode(q)
        return if (params.isEmpty()) "search" else "search?" + params.joinToString("&")
    }
    fun reader(id: String, readId: String? = null) =
        "reader/$id" + (readId?.let { "?readId=$it" } ?: "")
    fun comicList(type: String, title: String, id: String? = null) =
        "comicList/$type?title=$title" + (id?.let { "&id=$it" } ?: "")
    fun novelDetail(id: String) = "novel/$id"
    fun movieDetail(id: String) = "movie/$id"
    fun gameDetail(id: String) = "game/$id"
    fun blogDetail(id: String) = "blog/$id"
    fun offlineReader(id: String) = "offline/$id"
}
