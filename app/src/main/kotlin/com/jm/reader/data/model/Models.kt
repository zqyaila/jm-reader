package com.jm.reader.data.model

import org.json.JSONArray
import org.json.JSONObject

// ---------------------------------------------------------------------------
// JSON helpers (org.json based; the API responses are dynamic / loosely typed)
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

fun JSONObject.objOrEmpty(key: String): JSONObject =
    obj(key) ?: JSONObject()

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

// ---------------------------------------------------------------------------
// Domain models
// ---------------------------------------------------------------------------

data class CategoryRef(
    val id: String? = null,
    val title: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject?): CategoryRef? {
            if (o == null) return null
            return CategoryRef(
                id = o.strOrNull("id"),
                title = o.strOrNull("title"),
            )
        }
    }
}

/** A comic row returned by list endpoints (latest / search / week / daily / categories / favorites). */
data class ComicListItem(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    val image: String = "",
    val category: CategoryRef? = null,
    val categorySub: CategoryRef? = null,
    val liked: Boolean = false,
    val isFavorite: Boolean = false,
    val updateAt: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): ComicListItem = ComicListItem(
            id = o.str("id"),
            name = o.str("name"),
            author = o.firstStr("author"),
            image = o.str("image"),
            category = CategoryRef.fromJson(o.obj("category")),
            categorySub = CategoryRef.fromJson(o.obj("category_sub")),
            liked = o.bool("liked"),
            isFavorite = o.bool("is_favorite"),
            updateAt = o.long("update_at"),
        )
    }
}

/** A chapter (episode) inside a comic's `series` field. */
data class SeriesItem(
    val id: String = "",
    val sort: Int = 0,
    val name: String = "",
    val totalPage: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): SeriesItem = SeriesItem(
            id = o.str("id"),
            sort = o.int("sort"),
            name = o.str("name"),
            totalPage = o.int("total_page"),
        )
    }
}

/** Full comic detail from the `album` endpoint. */
data class ComicDetail(
    val id: String = "",
    val name: String = "",
    val authors: List<String> = emptyList(),
    val description: String = "",
    val addtime: Long = 0L,
    val totalViews: Long = 0L,
    val totalPhotos: Long = 0L,
    val likes: Long = 0L,
    val commentTotal: Long = 0L,
    val tags: List<String> = emptyList(),
    val series: List<SeriesItem> = emptyList(),
    val seriesId: String? = null,
    val works: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val relatedList: List<ComicListItem> = emptyList(),
    val liked: Boolean = false,
    val isFavorite: Boolean = false,
    val price: String? = null,
    val purchased: Boolean = false,
) {
    /** Paid content is content that has a price but has not been unlocked (purchased is "" or absent). */
    val isPaid: Boolean get() = !purchased && !price.isNullOrBlank()

    companion object {
        fun fromJson(o: JSONObject): ComicDetail = ComicDetail(
            id = o.str("id"),
            name = o.str("name"),
            authors = o.strList("author"),
            description = o.str("description"),
            addtime = o.long("addtime"),
            totalViews = o.long("total_views"),
            totalPhotos = o.long("total_photos"),
            likes = o.long("likes"),
            commentTotal = o.long("comment_total"),
            tags = o.strList("tags"),
            series = o.objList("series").map { SeriesItem.fromJson(it) },
            seriesId = o.strOrNull("series_id"),
            works = o.strList("works"),
            actors = o.strList("actors"),
            relatedList = o.objList("related_list").map { ComicListItem.fromJson(it) },
            liked = o.bool("liked"),
            isFavorite = o.bool("is_favorite"),
            price = o.strOrNull("price"),
            // `purchased` is a string in the API: "" or absent means not purchased.
            purchased = !o.strOrNull("purchased").isNullOrBlank(),
        )
    }
}

/** One page in a chapter from the `comic_read` endpoint. */
data class ReadPage(
    val page: Int = 0,
    val image: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): ReadPage = ReadPage(
            page = o.int("page"),
            image = o.str("image"),
        )
    }
}

/** Chapter read data from the `comic_read` endpoint. */
data class ReadData(
    val id: String = "",
    val name: String = "",
    val scrambleId: Long = 0L,
    val totalPage: Int = 0,
    val images: List<ReadPage> = emptyList(),
    val seriesId: String? = null,
) {
    companion object {
        fun fromJson(o: JSONObject): ReadData = ReadData(
            id = o.str("id"),
            name = o.str("name"),
            scrambleId = o.long("scramble_id"),
            totalPage = o.int("total_page"),
            images = o.objList("images").map { ReadPage.fromJson(it) },
            seriesId = o.strOrNull("series_id"),
        )
    }
}

/** Member / user data (returned by login and used as memberInfo). */
data class Member(
    val uid: String = "",
    val s: String = "",
    val username: String = "",
    val email: String = "",
    val photo: String = "",
    val nickName: String = "",
    val gender: String = "",
    val coin: Long = 0L,
    val level: String = "",
    val levelName: String = "",
    val adFree: Boolean = false,
    val charge: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): Member = Member(
            uid = o.str("uid"),
            s = o.str("s"),
            username = o.str("username"),
            email = o.str("email"),
            photo = o.str("photo"),
            nickName = o.str("nick_name"),
            gender = o.str("gender"),
            coin = o.long("coin"),
            level = o.str("level"),
            levelName = o.str("level_name"),
            adFree = o.bool("ad_free"),
            charge = o.str("charge"),
        )
    }
}

/** A tag (category tag / hot tag / tags_favorite item). */
data class TagItem(
    val id: String = "",
    val title: String = "",
    val count: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): TagItem = TagItem(
            id = o.str("id").ifBlank { o.str("tag") },
            title = o.str("title").ifBlank { o.str("tag") },
            count = o.long("count"),
        )
    }
}

/** A category (from the `categories` endpoint). `slug` is used as the `c` filter param. */
data class Category(
    val slug: String = "",
    val title: String = "",
    val subCategories: List<Category> = emptyList(),
) {
    companion object {
        fun fromJson(o: JSONObject): Category = Category(
            slug = o.str("slug").ifBlank { o.str("id") },
            title = o.str("name").ifBlank { o.str("title") },
            subCategories = o.objList("sub_categories").map { fromJson(it) },
        )
    }
}

/** A novel list item (from `novels` endpoint). */
data class NovelItem(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    val image: String = "",
    val updateAt: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): NovelItem = NovelItem(
            id = o.str("id"),
            name = o.str("name"),
            author = o.firstStr("author"),
            image = o.str("image"),
            updateAt = o.long("update_at"),
        )
    }
}

/** A movie / video list item (from `videos` endpoint). */
data class MovieItem(
    val id: String = "",
    val title: String = "",
    val photo: String = "",
    val tags: List<String> = emptyList(),
) {
    val image: String get() = photo

    companion object {
        fun fromJson(o: JSONObject): MovieItem = MovieItem(
            id = o.str("id"),
            title = o.str("title"),
            photo = o.str("photo"),
            tags = o.strList("tags"),
        )
    }
}

/** A game list item (from `allgames` endpoint). */
data class GameItem(
    val id: String = "",
    val name: String = "",
    val image: String = "",
    val link: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject): GameItem = GameItem(
            id = o.str("gid").ifBlank { o.str("id") },
            name = o.str("title").ifBlank { o.str("name") },
            image = o.str("photo").ifBlank { o.str("image") },
            link = o.str("link"),
        )
    }
}

/** A blog list item (from `blogs` endpoint). */
data class BlogItem(
    val id: String = "",
    val title: String = "",
    val image: String = "",
    val updateAt: Long = 0L,
) {
    companion object {
        fun fromJson(o: JSONObject): BlogItem = BlogItem(
            id = o.str("id"),
            title = o.str("title"),
            image = o.str("image").ifBlank { o.str("photo") },
            updateAt = o.long("update_at"),
        )
    }
}

/** A forum post (from `forum` endpoint). */
data class ForumItem(
    val cid: String = "",
    val nickname: String = "",
    val content: String = "",
    val addtime: String = "",
    val replyCount: Int = 0,
) {
    companion object {
        fun fromJson(o: JSONObject): ForumItem = ForumItem(
            cid = o.str("CID").ifBlank { o.str("id") },
            nickname = o.str("nickname"),
            content = o.str("content"),
            addtime = o.str("addtime"),
            replyCount = o.objList("replys").size,
        )
    }
}

/**
 * One album comment, from `GET /forum?mode=manhua&aid=<album>&page=<n>`.
 *
 * The live payload is `{"total": "23", "list": [<comment>, …]}` with a **flat** list — replies are
 * not nested, they are linked back to their parent through [parentCid] (`"0"` for a top-level
 * comment). [replies] is still parsed because the same endpoint serves the desktop client a nested
 * `replys` array for some scopes, and threading the flat form is the caller's job
 * (`CommentsController`, which folds both representations into one tree).
 *
 * Field names mirror the API (`CID`, `UID`, `expinfo.level_name`, …), with the same
 * lower/upper-case tolerance the other models have.
 *
 * Avatar files are relative: the client resolves them against the image host under `/media/users/`
 * (see [com.jm.reader.data.repo.AppRepository.commentAvatar]). `nopic-*.gif` is the API's
 * "no avatar" placeholder and is reported as null so the UI can draw its own.
 */
data class CommentItem(
    val cid: String = "",
    val uid: String = "",
    val username: String = "",
    /** Raw avatar filename; null when the comment has no real picture. */
    val avatar: String? = null,
    val content: String = "",
    val likes: Long = 0L,
    val addtime: String = "",
    val level: String = "",
    val levelName: String = "",
    /** `parent_CID`: the comment this one replies to, or "0"/blank for top-level. */
    val parentCid: String = "",
    /** Album this comment is attached to (`AID`), for the "on <work>" backlink. */
    val linkAlbumId: String? = null,
    val linkAlbumName: String? = null,
    val replies: List<CommentItem> = emptyList(),
) {
    /** True when the API did not nest this comment under another one. */
    val isTopLevel: Boolean get() = parentCid.isBlank() || parentCid == "0"

    companion object {
        fun fromJson(o: JSONObject): CommentItem {
            val exp = o.obj("expinfo")
            val photo = o.str("photo").trim()
            return CommentItem(
                cid = o.str("CID").ifBlank { o.str("cid") },
                uid = o.str("UID").ifBlank { o.str("uid") },
                username = o.str("username").ifBlank { o.str("nickname") },
                avatar = photo.takeIf { it.isNotBlank() && !it.startsWith("nopic", ignoreCase = true) },
                content = o.str("content"),
                likes = o.long("likes"),
                addtime = o.str("addtime"),
                level = exp?.str("level").orEmpty().ifBlank { o.str("level") },
                levelName = exp?.str("level_name").orEmpty().ifBlank { o.str("title") },
                parentCid = o.str("parent_CID").ifBlank { o.str("parent_cid") }.trim(),
                linkAlbumId = o.strOrNull("AID")?.takeIf { it.isNotBlank() },
                linkAlbumName = o.strOrNull("name")?.takeIf { it.isNotBlank() },
                replies = o.objList("replys").map { fromJson(it) },
            )
        }
    }
}

/** One page of album comments: `{ "total": <int|string>, "list": [...] }`. */
data class CommentPage(
    val total: Int = 0,
    val items: List<CommentItem> = emptyList(),
)

/**
 * Folds a flat page into threads: every comment whose [CommentItem.parentCid] names another comment
 * on the same page is attached under it (recursively, depth-capped), everything else stays at the
 * top level. Comments whose parent is missing from the page are kept at the top level rather than
 * dropped, so a filtered/deleted parent never hides its replies.
 */
fun buildCommentThreads(page: List<CommentItem>): List<CommentItem> {
    if (page.isEmpty()) return emptyList()
    val byCid = page.associateBy { it.cid }

    // Resolve each comment to the highest ancestor reachable *within this page*.
    //
    // Doing it this way (rather than asking "is this a root?") is what keeps a comment from being
    // emitted twice. When a chain's middle link is missing - common, because a deleted parent or a
    // page that has not been fetched yet leaves a hole - the child's own parent *does* exist, so the
    // child is a descendant, not a root, even though the walk cannot reach the very top. Both ends
    // resolve to the same root and the child is placed exactly once.
    //
    // A cycle (two comments naming each other) makes the walk revisit a comment; that comment then
    // becomes its own root, so the thread is still rendered rather than vanishing.
    fun threadRootOf(c: CommentItem): String {
        val seen = HashSet<String>()
        var cur = c
        while (true) {
            if (!seen.add(cur.cid)) return c.cid
            if (cur.isTopLevel) return cur.cid
            cur = byCid[cur.parentCid] ?: return cur.cid
        }
    }

    val roots = LinkedHashMap<String, CommentItem>()
    val childrenByParent = HashMap<String, MutableList<CommentItem>>()
    for (c in page) {
        if (threadRootOf(c) == c.cid) roots[c.cid] = c
        else childrenByParent.getOrPut(c.parentCid) { mutableListOf() }.add(c)
    }

    // `visited` bounds the recursion depth and stops a malformed shared node from attaching twice.
    val visited = HashSet<String>()
    fun attach(node: CommentItem, depth: Int): CommentItem {
        if (!visited.add(node.cid)) return node
        if (depth >= MAX_REPLY_DEPTH) return node
        val kids = childrenByParent[node.cid].orEmpty().map { attach(it, depth + 1) }
        return if (kids.isEmpty()) node else node.copy(replies = node.replies + kids)
    }

    return roots.values.map { attach(it, 0) }
}

/** Threads deeper than this are flattened; real threads are 1-3 levels. */
private const val MAX_REPLY_DEPTH = 4
