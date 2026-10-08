package com.jm.reader.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The album row shape is shared by every list endpoint the app reads - `/latest`, `/search`,
 * `/favorite`, `/watch_list`, `/categories/filter`. Cloud history in particular is parsed from the
 * `{"list": [...], "total": N}` wrapper (reference client: `ToolUtil.ParseHistoryReq2`), so the
 * fields it relies on are pinned here.
 */
class ComicListItemTest {

    @Test
    fun `parses the fields a list endpoint returns`() {
        val json = JSONObject(
            """
            {
              "id": "1159383",
              "name": "Some Work",
              "author": ["A", "B"],
              "image": "cover.jpg",
              "category": { "id": "1", "title": "本子" },
              "category_sub": { "id": "2", "title": "短篇" },
              "liked": true,
              "is_favorite": true,
              "update_at": 1659491075
            }
            """.trimIndent(),
        )

        val item = ComicListItem.fromJson(json)
        assertEquals("1159383", item.id)
        assertEquals("Some Work", item.name)
        assertEquals("A", item.author)
        assertEquals("cover.jpg", item.image)
        assertEquals("本子", item.category?.title)
        assertEquals("短篇", item.categorySub?.title)
        assertTrue(item.liked)
        assertTrue(item.isFavorite)
        assertEquals(1659491075L, item.updateAt)
    }

    @Test
    fun `author accepts a bare string as well as an array`() {
        // Verified live: /latest and /search send a bare string, /album sends an array.
        assertEquals("Solo", ComicListItem.fromJson(JSONObject("""{"author":"Solo"}""")).author)
        assertEquals("First", ComicListItem.fromJson(JSONObject("""{"author":["First","Second"]}""")).author)
        // An empty array must not become the literal "[]" on a card.
        assertNull(ComicListItem.fromJson(JSONObject("""{"author":[]}""")).author)
        assertNull(ComicListItem.fromJson(JSONObject("""{"author":""}""")).author)
        assertNull(ComicListItem.fromJson(JSONObject("{}")).author)
    }

    @Test
    fun `liked and is_favorite come back as real booleans`() {
        // Verified live against /latest and /search: both are JSON booleans, not "1"/"0".
        val json = JSONObject("""{"liked": false, "is_favorite": false}""")
        val item = ComicListItem.fromJson(json)
        assertEquals(false, item.liked)
        assertEquals(false, item.isFavorite)
    }

    @Test
    fun `a missing id stays blank rather than throwing`() {
        val item = ComicListItem.fromJson(JSONObject("{}"))
        assertEquals("", item.id)
        assertEquals("", item.name)
        assertNull(item.category)
        assertEquals(0L, item.updateAt)
    }

    @Test
    fun `the watch_list wrapper exposes list and total`() {
        // GET /watch_list -> {"list": [<album>, ...], "total": <int>}
        val page = JSONObject(
            """{"total": 7, "list": [{"id":"1","name":"a"},{"id":"2","name":"b"}]}""",
        )
        assertEquals(7, page.int("total"))
        val items = page.objList("list").map { ComicListItem.fromJson(it) }
        assertEquals(listOf("1", "2"), items.map { it.id })
    }
}
