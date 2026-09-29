package dev.handspell.app.reminder

import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import java.util.Calendar
import java.util.TimeZone

/** The fixed times a reminder can come. Few on purpose: no clock picker to fiddle with. */
enum class ReminderSlot(val hour: Int) { MORNING(9), AFTERNOON(14), EVENING(19) }

/** Next time the slot comes round: today if it is still ahead, otherwise tomorrow (local time, DST-safe). */
fun nextReminderAt(nowMs: Long, slot: ReminderSlot, zone: TimeZone = TimeZone.getDefault()): Long {
    val calendar = Calendar.getInstance(zone).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, slot.hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (calendar.timeInMillis <= nowMs) calendar.add(Calendar.DAY_OF_YEAR, 1)
    return calendar.timeInMillis
}

/** What the reminder does when it fires. */
sealed interface ReminderDecision {
    /** Today already has practice: stay quiet. */
    data object Skip : ReminderDecision

    /** A streak is alive from yesterday: "Keep your N-day streak". */
    data class KeepStreak(val days: Int) : ReminderDecision

    /** No streak running: a plain nudge to practise. */
    data object Start : ReminderDecision
}

fun reminderDecision(snapshot: ProgressSnapshot?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): ReminderDecision {
    val today = localPracticeDay(nowMs, zone)
    val last = snapshot?.lastPracticeDay
    return when {
        last == today -> ReminderDecision.Skip
        last == today - 1 && (snapshot?.currentStreakDays ?: 0) > 0 -> ReminderDecision.KeepStreak(snapshot!!.currentStreakDays)
        else -> ReminderDecision.Start
    }
}
