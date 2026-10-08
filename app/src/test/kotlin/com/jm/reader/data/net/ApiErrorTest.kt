package com.jm.reader.data.net

import com.jm.reader.ui.strings.En
import com.jm.reader.ui.strings.ZhCN
import com.jm.reader.ui.strings.ZhTW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reader must never be shown a bare HTTP/API code. These cases pin down the mapping, using the
 * exact `errorMsg` values the live endpoints return (captured while verifying the fixes):
 *
 *  - `POST /login` with a wrong password -> HTTP 401, `{"code":401,"data":[],"errorMsg":"无效的用户名和/或密码！"}`
 *  - `GET /watch_list` unauthenticated    -> HTTP 401, `{"code":401,"errorMsg":"Authentication fail."}`
 *  - `GET /favorite`  unauthenticated     -> HTTP 401, `{"code":401,"errorMsg":"請先登入會員"}`
 */
class ApiErrorTest {

    @Test
    fun `server message wins over any local phrasing`() {
        val msg = ApiError.of(ZhCN, 401, "无效的用户名和/或密码！", 401)
        assertEquals("无效的用户名和/或密码！", msg)
    }

    @Test
    fun `401 without a server message becomes a login hint, never a code`() {
        for (strings in listOf(ZhCN, ZhTW, En)) {
            val msg = ApiError.of(strings, 401, null, 401)
            assertEquals(strings.errUnauthorized, msg)
            assertFalse("must not surface the raw code: $msg", msg.contains("401"))
        }
    }

    @Test
    fun `other statuses map to localised phrases`() {
        assertEquals(ZhCN.errForbidden, ApiError.of(ZhCN, 403, null, 403))
        assertEquals(ZhCN.errNotFound, ApiError.of(ZhCN, 404, null, 404))
        assertEquals(ZhCN.errRateLimited, ApiError.of(ZhCN, 429, null, 429))
        // A 5xx is recognised from the HTTP status, not from the JSON code.
        assertEquals(ZhCN.errServer, ApiError.of(ZhCN, 500, null, 500))
        assertEquals(ZhCN.errServer, ApiError.of(ZhCN, 200, null, 520))
    }

    @Test
    fun `unknown api codes are wrapped in a sentence, not printed alone`() {
        val msg = ApiError.of(ZhCN, 1001, null)
        assertTrue(msg.contains("1001"))
        assertTrue("a bare number must not be the whole message", msg.length > "1001".length)
    }

    @Test
    fun `transport failures are classified`() {
        assertEquals(ZhCN.errTimeout, ApiError.network(ZhCN, "connect timed out"))
        assertEquals(ZhCN.errNetwork, ApiError.network(ZhCN, "Unable to resolve host \"x\""))
        assertEquals(ZhCN.errNoHost, ApiError.network(ZhCN, "API 主機尚未設定"))
        assertEquals(ZhCN.errNetwork, ApiError.network(ZhCN, null))
    }
}
