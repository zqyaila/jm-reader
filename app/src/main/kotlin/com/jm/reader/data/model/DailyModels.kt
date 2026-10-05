package com.jm.reader.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One cell of the 每日签到 calendar.
 *
 * `signed` is deliberately three-valued in the API: `true`, `false`, or `null` for days that are
 * outside the running activity window. [pending] captures that third state.
 */
data class DayCell(
    val label: String,
    val signed: Boolean,
    val bonus: Boolean,
    val pending: Boolean,
)

/**
 * Parsed `/daily` payload.
 *
 * Live shape (verified against the API):
 * ```json
 * {
 *   "daily_id": 73, "event_name": "10月-「来都来了」", "currentProgress": "14.3%",
 *   "three_days_coin": "150", "seven_days_coin": "350",
 *   "record": [[{"date": "01", "signed": false, "bonus": true}, …], …]
 * }
 * ```
 */
data class DailyRecord(
    val dailyId: String = "",
    val eventName: String = "",
    val progress: String = "",
    val threeDaysCoin: String = "",
    val sevenDaysCoin: String = "",
    val signedCount: Int = 0,
    val signedToday: Boolean = false,
    val weeks: List<List<DayCell>> = emptyList(),
)

/** `"05"` style label for a day-of-month number. */
fun dayLabel(dayOfMonth: Int): String = dayOfMonth.toString().padStart(2, '0')

/** Today's day-of-month label, matching the `date` field the API sends. */
fun todayLabel(calendar: Calendar = Calendar.getInstance()): String =
    dayLabel(calendar.get(Calendar.DAY_OF_MONTH))

/**
 * Defensive parse of the `/daily` payload: every field is optional, every type mismatch is
 * tolerated, and `record` may be absent entirely. Never throws.
 */
fun parseDailyRecord(data: JSONObject, today: String = todayLabel()): DailyRecord {
    var signedCount = 0
    var signedToday = false
    val weeks = mutableListOf<List<DayCell>>()

    val record: JSONArray? = data.optJSONArray("record")
    if (record != null) {
        for (i in 0 until record.length()) {
            val week = record.optJSONArray(i) ?: continue
            val cells = mutableListOf<DayCell>()
            for (j in 0 until week.length()) {
                val day: JSONObject = week.optJSONObject(j) ?: continue
                val signed = day.bool("signed")
                if (signed) signedCount++
                val label = day.str("date").padStart(2, '0')
                if (label == today && signed) signedToday = true
                cells += DayCell(
                    label = label,
                    signed = signed,
                    bonus = day.bool("bonus"),
                    pending = day.has("signed") && day.isNull("signed"),
                )
            }
            if (cells.isNotEmpty()) weeks += cells
        }
    }

    return DailyRecord(
        dailyId = data.str("daily_id"),
        eventName = data.str("event_name"),
        progress = data.str("currentProgress"),
        threeDaysCoin = data.str("three_days_coin"),
        sevenDaysCoin = data.str("seven_days_coin"),
        signedCount = signedCount,
        signedToday = signedToday,
        weeks = weeks,
    )
}
