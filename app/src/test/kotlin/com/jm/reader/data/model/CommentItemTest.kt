package com.jm.reader.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The album-comment payload is loosely typed and easy to get subtly wrong, so the shapes the
 * reference client (JMComic-qt `ToolUtil.ParseBookComment`) relies on are pinned here.
 */
class CommentItemTest {

    @Test
    fun `parses the fields the comment list actually renders`() {
        val json = JSONObject(
            """
            {
              "CID": "9001",
              "UID": "42",
              "username": "reader",
              "photo": "abc123.jpg",
              "content": "great <br> chapter",
              "likes": "7",
              "addtime": "2026-01-02 03:04",
              "expinfo": { "level": 3, "level_name": "老司机" },
              "AID": "441923",
              "name": "Some Work"
            }
            """.trimIndent(),
        )

        val c = CommentItem.fromJson(json)
        assertEquals("9001", c.cid)
        assertEquals("42", c.uid)
        assertEquals("reader", c.username)
        assertEquals("abc123.jpg", c.avatar)
        assertEquals("great <br> chapter", c.content)
        assertEquals(7L, c.likes)
        assertEquals("2026-01-02 03:04", c.addtime)
        assertEquals("3", c.level)
        assertEquals("老司机", c.levelName)
        assertEquals("441923", c.linkAlbumId)
        assertEquals("Some Work", c.linkAlbumName)
    }

    @Test
    fun `replies nest recursively`() {
        val json = JSONObject(
            """
            {
              "CID": "1",
              "content": "parent",
              "replys": [
                { "CID": "2", "username": "kid", "content": "child" },
                { "CID": "3", "content": "other", "replys": [ { "CID": "4", "content": "grandchild" } ] }
              ]
            }
            """.trimIndent(),
        )

        val c = CommentItem.fromJson(json)
        assertEquals(2, c.replies.size)
        assertEquals("child", c.replies[0].content)
        assertEquals(1, c.replies[1].replies.size)
        assertEquals("grandchild", c.replies[1].replies[0].content)
    }

    @Test
    fun `placeholder avatars are reported as absent`() {
        assertNull(CommentItem.fromJson(JSONObject("""{"photo":"nopic-Male.gif"}""")).avatar)
        assertNull(CommentItem.fromJson(JSONObject("""{"photo":"NOPIC-Female.gif"}""")).avatar)
        assertNull(CommentItem.fromJson(JSONObject("""{"photo":""}""")).avatar)
        assertNull(CommentItem.fromJson(JSONObject("{}")).avatar)
    }

    @Test
    fun `nickname stands in when username is missing, and level falls back to the top level`() {
        val c = CommentItem.fromJson(
            JSONObject("""{"nickname":"anon","level":"5","title":"VIP"}"""),
        )
        assertEquals("anon", c.username)
        assertEquals("5", c.level)
        assertEquals("VIP", c.levelName)
    }

    @Test
    fun `missing optional fields do not throw and stay blank`() {
        val c = CommentItem.fromJson(JSONObject("{}"))
        assertEquals("", c.cid)
        assertEquals("", c.content)
        assertEquals(0L, c.likes)
        assertTrue(c.replies.isEmpty())
        assertNull(c.linkAlbumId)
    }

    @Test
    fun `page wrapper exposes total and list`() {
        val page = JSONObject(
            """{"total": 137, "list": [{"CID":"1"},{"CID":"2"}]}""",
        )
        assertEquals(137, page.int("total"))
        assertEquals(2, page.objList("list").size)
    }

    @Test
    fun `total parses from the string form the live API sends`() {
        // Verified against GET /forum: total is a JSON string ("23"), not a number.
        val page = JSONObject("""{"total": "23", "list": []}""")
        assertEquals(23, page.int("total"))
    }

    @Test
    fun `parent_CID is read and marks top-level comments`() {
        val top = CommentItem.fromJson(JSONObject("""{"CID":"1","parent_CID":"0"}"""))
        assertTrue(top.isTopLevel)
        val reply = CommentItem.fromJson(JSONObject("""{"CID":"2","parent_CID":"1"}"""))
        assertEquals("1", reply.parentCid)
        assertTrue(!reply.isTopLevel)
        // Absent parent -> top level, so a stripped field never orphans a comment.
        assertTrue(CommentItem.fromJson(JSONObject("""{"CID":"3"}""")).isTopLevel)
    }

    @Test
    fun `flat page is folded into threads by parent_CID`() {
        val page = listOf(
            CommentItem(cid = "1", content = "root"),
            CommentItem(cid = "2", content = "kid", parentCid = "1"),
            CommentItem(cid = "3", content = "grandkid", parentCid = "2"),
            CommentItem(cid = "4", content = "other root"),
        )

        val threads = buildCommentThreads(page)
        assertEquals(listOf("1", "4"), threads.map { it.cid })
        assertEquals(listOf("2"), threads[0].replies.map { it.cid })
        assertEquals(listOf("3"), threads[0].replies[0].replies.map { it.cid })
        assertTrue(threads[1].replies.isEmpty())
    }

    @Test
    fun `a reply whose parent is not on the page stays visible at the top level`() {
        val threads = buildCommentThreads(
            listOf(CommentItem(cid = "9", content = "orphan", parentCid = "404")),
        )
        assertEquals(listOf("9"), threads.map { it.cid })
    }

    @Test
    fun `a missing middle ancestor does not duplicate its descendant`() {
        // "1" is absent (deleted, or simply on a page that has not been fetched). "2" therefore
        // cannot reach the top, but "3" is still a *descendant* of "2" and must appear only once.
        val page = listOf(
            CommentItem(cid = "2", parentCid = "1"),
            CommentItem(cid = "3", parentCid = "2"),
        )

        val threads = buildCommentThreads(page)
        assertEquals(listOf("2"), threads.map { it.cid })
        assertEquals(listOf("3"), threads.single().replies.map { it.cid })
    }

    @Test
    fun `a parent cycle terminates and loses nothing`() {
        val threads = buildCommentThreads(
            listOf(
                // Both name each other as parent. Neither is a natural root, so they are promoted
                // rather than dropped - and the walk must still terminate.
                CommentItem(cid = "a", parentCid = "b"),
                CommentItem(cid = "b", parentCid = "a"),
            ),
        )
        assertEquals(setOf("a", "b"), threads.map { it.cid }.toSet())
    }

    @Test
    fun `no comment disappears from a chain of replies`() {
        val page = listOf(
            CommentItem(cid = "1"),
            CommentItem(cid = "2", parentCid = "1"),
            CommentItem(cid = "3", parentCid = "2"),
            CommentItem(cid = "4", parentCid = "3"),
            CommentItem(cid = "5", parentCid = "4"),
            CommentItem(cid = "6", parentCid = "404"),
        )

        val seen = mutableSetOf<String>()
        fun walk(list: List<CommentItem>) {
            list.forEach { seen += it.cid; walk(it.replies) }
        }
        walk(buildCommentThreads(page))
        assertEquals(setOf("1", "2", "3", "4", "5", "6"), seen)
    }

    @Test
    fun `nested replys and flat parent_CID rows are combined`() {
        val parent = CommentItem(
            cid = "1",
            replies = listOf(CommentItem(cid = "inlined")),
        )
        val threads = buildCommentThreads(
            listOf(parent, CommentItem(cid = "flat", parentCid = "1")),
        )
        assertEquals(listOf("inlined", "flat"), threads.single().replies.map { it.cid })
    }

    @Test
    fun `empty input yields no threads`() {
        assertTrue(buildCommentThreads(emptyList()).isEmpty())
    }
}
