package dev.handspell.app.ui.streak

import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import dev.handspell.app.ui.reward.STREAK_MILESTONES
import java.util.Calendar
import java.util.TimeZone

/** One cell of the month grid. [dayOfMonth] is null for the blanks before the 1st and after the last day. */
data class CalendarCell(
    val dayOfMonth: Int?,
    val epochDay: Long?,
    val practised: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

/** The current month, Monday-first, as rows of seven cells. [month] is 0-based (Calendar.MONTH). */
data class MonthCalendar(val year: Int, val month: Int, val weeks: List<List<CalendarCell>>, val practisedCount: Int)

enum class MilestoneStatus { REACHED, NEXT, AHEAD }

data class MilestoneRow(val days: Int, val status: MilestoneStatus, val daysToGo: Int)

data class StreakState(
    val loading: Boolean = true,
    val current: Int = 0,
    val longest: Int = 0,
    val practisedToday: Boolean = false,
    val calendar: MonthCalendar? = null,
    val milestones: List<MilestoneRow> = emptyList(),
)

/**
 * The streak screen from saved progress. The current streak is live only if the last practice day is today or
 * yesterday (the rule used everywhere). A milestone is reached once the longest streak has touched it, so a lapse
 * never takes an earned marker away; "days to go" counts from the current streak.
 */
fun streakState(snapshot: ProgressSnapshot?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): StreakState {
    if (snapshot == null) return StreakState(loading = true)
    val today = localPracticeDay(nowMs, zone)
    val live = snapshot.lastPracticeDay == today || snapshot.lastPracticeDay == today - 1
    val current = if (live) snapshot.currentStreakDays else 0
    val longest = maxOf(snapshot.longestStreakDays, current)
    val next = STREAK_MILESTONES.firstOrNull { it > longest }
    return StreakState(
        loading = false,
        current = current,
        longest = longest,
        practisedToday = snapshot.lastPracticeDay == today,
        calendar = monthCalendar(snapshot.practiceDays, today),
        milestones = STREAK_MILESTONES.map { days ->
            MilestoneRow(
                days = days,
                status = when {
                    longest >= days -> MilestoneStatus.REACHED
                    days == next -> MilestoneStatus.NEXT
                    else -> MilestoneStatus.AHEAD
                },
                daysToGo = (days - current).coerceAtLeast(0),
            )
        },
    )
}

/** The month containing [today] (an epoch day in local time), Monday-first. */
fun monthCalendar(practiceDays: Set<Long>, today: Long): MonthCalendar {
    // Epoch days are already local, so the calendar is read in UTC to avoid shifting them a second time.
    val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = today * DAY_MS }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDay = today - (dayOfMonth - 1)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    // Calendar.MONDAY == 2 ... SUNDAY == 1; leading blanks for a Monday-first week.
    val leading = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cells = List(leading) { CalendarCell(null, null, false, false, false) } +
        (0 until daysInMonth).map { offset ->
            val epochDay = firstDay + offset
            CalendarCell(offset + 1, epochDay, epochDay in practiceDays, epochDay == today, epochDay > today)
        }
    val padded = cells + List((7 - cells.size % 7) % 7) { CalendarCell(null, null, false, false, false) }
    return MonthCalendar(
        year = year,
        month = month,
        weeks = padded.chunked(7),
        practisedCount = (firstDay until firstDay + daysInMonth).count { it in practiceDays },
    )
}

private const val DAY_MS = 86_400_000L
