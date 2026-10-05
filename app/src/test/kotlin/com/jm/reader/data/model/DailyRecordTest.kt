package com.jm.reader.data.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parses a **real captured** `/daily` payload (`src/test/resources/daily_sample.json`, fetched from
 * the live API). This is the record the 每日签到 screen renders, so the parser is exercised against
 * the exact shape the server sends - including the tri-state `signed` field (`true` / `false` /
 * `null`) that the old screen ignored.
 */
class DailyRecordTest {

    private fun sample(): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("daily_sample.json")
        assertNotNull("daily_sample.json must be on the test classpath", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun `parses the real payload`() {
        val record = parseDailyRecord(sample(), today = "nothing-matches")

        assertEquals("73", record.dailyId)
        assertEquals("10月-「来都来了」", record.eventName)
        assertEquals("0%", record.progress)
        assertEquals("150", record.threeDaysCoin)
        assertEquals("350", record.sevenDaysCoin)
        assertEquals(5, record.weeks.size)
        // Full weeks are 7 days; the trailing week is partial (29/30/31), exactly as sent.
        assertTrue(record.weeks.take(4).all { it.size == 7 })
        assertEquals(3, record.weeks.last().size)
        assertEquals(31, record.weeks.sumOf { it.size })
        assertEquals("01", record.weeks[0][0].label)
        assertEquals("31", record.weeks.last().last().label)
        // Nothing is signed in the captured sample.
        assertEquals(0, record.signedCount)
        assertFalse(record.signedToday)
    }

    @Test
    fun `null signed means pending, not done`() {
        val record = parseDailyRecord(sample(), today = "nothing-matches")
        val cells = record.weeks.flatten()
        // The captured payload uses `"signed": null` for days outside the activity window.
        assertTrue("expected at least one pending day", cells.any { it.pending })
        assertTrue(cells.filter { it.pending }.none { it.signed })
    }

    @Test
    fun `signedToday is detected from the matching day label`() {
        val payload = JSONObject(
            """
            {"daily_id": 1, "record": [[
              {"date": "04", "signed": false, "bonus": false},
              {"date": "05", "signed": true,  "bonus": false}
            ]]}
            """.trimIndent(),
        )
        val record = parseDailyRecord(payload, today = "05")
        assertTrue(record.signedToday)
        assertEquals(1, record.signedCount)
        assertEquals(2, record.weeks[0].size)
    }

    @Test
    fun `missing and malformed fields do not throw`() {
        val empty = parseDailyRecord(JSONObject("{}"))
        assertEquals("", empty.dailyId)
        assertEquals(0, empty.signedCount)
        assertTrue(empty.weeks.isEmpty())

        val junk = parseDailyRecord(
            JSONObject("""{"daily_id":{"a":1},"record":[{"not":"a week"},[],null]}"""),
        )
        assertEquals(0, junk.signedCount)
        assertTrue(junk.weeks.isEmpty())
    }

    @Test
    fun `day labels are zero padded`() {
        assertEquals("01", dayLabel(1))
        assertEquals("10", dayLabel(10))
    }
}
