package com.jm.reader.ui.strings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the三语 tables: `AppStrings` is a data class, so a missing value only surfaces at build
 * time - but a *blank* one would ship. Also checks the format placeholders, because a mismatch
 * there would throw `IllegalFormatException` at render time (exactly the class of crash the
 * 每日签到 screen used to be able to hit).
 */
class AppStringsTest {

    private val tables = mapOf(
        "ZhCN" to ZhCN,
        "ZhTW" to ZhTW,
        "En" to En,
    )

    @Test
    fun `forLanguage returns the matching table`() {
        assertEquals(ZhCN, AppStrings.forLanguage(UiLanguage.ZH_CN))
        assertEquals(ZhTW, AppStrings.forLanguage(UiLanguage.ZH_TW))
        assertEquals(En, AppStrings.forLanguage(UiLanguage.EN))
    }

    @Test
    fun `error strings are present and non blank in every language`() {
        tables.forEach { (name, s) ->
            listOf(
                "errNetwork" to s.errNetwork,
                "errTimeout" to s.errTimeout,
                "errServer" to s.errServer,
                "errNotFound" to s.errNotFound,
                "errForbidden" to s.errForbidden,
                "errUnauthorized" to s.errUnauthorized,
                "errRateLimited" to s.errRateLimited,
                "errNoHost" to s.errNoHost,
                "errRequestFailedFmt" to s.errRequestFailedFmt,
                "loginFailed" to s.loginFailed,
                "registerFailed" to s.registerFailed,
                "registerNeedConfirm" to s.registerNeedConfirm,
                "sessionExpired" to s.sessionExpired,
                "searchAdaptiveHint" to s.searchAdaptiveHint,
                "libraryHistoryHint" to s.libraryHistoryHint,
            ).forEach { (field, value) ->
                assertTrue("$name.$field is blank", value.isNotBlank())
            }
        }
    }

    @Test
    fun `login failure text is not a numeric code`() {
        tables.forEach { (name, s) ->
            assertFalse("$name.loginFailed looks like an API code", s.loginFailed.trim().all { it.isDigit() })
        }
    }

    @Test
    fun `format strings accept the arguments the screens pass`() {
        tables.forEach { (name, s) ->
            // Render once with the real argument types; a bad placeholder throws here.
            s.signedDaysFmt.format(0)
            s.searchTotalFmt.format(1)
            s.historyCountFmt.format(2)
            s.chapterFmt.format(3)
            s.dailyProgressFmt.format("10%")
            s.historyResumeFmt.format("第1话 · P3")
            s.searchJumpIdFmt.format("441923")
            s.errRequestFailedFmt.format(500)
            s.deleteConfirmFmt.format("x")
            s.downloadProgressFmt.format(1, 2)
            s.commentsCountFmt.format(1)
            s.commentReplyToFmt.format("reader")
            s.commentRepliesFmt.format(2)
            assertTrue("$name signedDaysFmt lost its placeholder", s.signedDaysFmt.contains("%"))
        }
    }
}
