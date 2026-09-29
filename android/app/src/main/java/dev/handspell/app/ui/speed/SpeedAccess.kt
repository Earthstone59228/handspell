package dev.handspell.app.ui.speed

import dev.handspell.app.progress.localPracticeDay
import java.util.Calendar
import java.util.TimeZone

/** Whether a speed challenge can start now. Free: one a day, reset at local midnight. Pro: no limit. */
sealed interface SpeedAccess {
    data object Unlimited : SpeedAccess
    data object FreeAvailable : SpeedAccess
    data class UsedToday(val resetsAtMs: Long) : SpeedAccess

    val canStart: Boolean get() = this !is UsedToday
}

/** Pure function of the entitlement, the day the free round was last used, and the clock. */
fun speedAccess(isPro: Boolean, freeUsedDay: Long?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): SpeedAccess = when {
    isPro -> SpeedAccess.Unlimited
    freeUsedDay == localPracticeDay(nowMs, zone) -> SpeedAccess.UsedToday(nextLocalMidnight(nowMs, zone))
    else -> SpeedAccess.FreeAvailable
}

/** The first instant of the next local day (daylight-saving changes included). */
fun nextLocalMidnight(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Calendar.getInstance(zone).apply {
        timeInMillis = nowMs
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** Hours and minutes until [resetsAtMs], rounded up to the next minute so it never reads "0 min" early. */
fun timeUntil(resetsAtMs: Long, nowMs: Long): Pair<Int, Int> {
    val minutes = ((resetsAtMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000
    return (minutes / 60).toInt() to (minutes % 60).toInt()
}
