package dev.handspell.app.ui.streak

import dev.handspell.app.progress.ProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StreakModelTest {
    private val utc = TimeZone.getTimeZone("UTC")

    /** Epoch day of a UTC calendar date. */
    private fun day(year: Int, month: Int, dayOfMonth: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month, dayOfMonth) }.timeInMillis / 86_400_000L

    private fun snapshot(last: Long?, current: Int, longest: Int, days: Set<Long>) = ProgressSnapshot(
        ProgressSnapshot.SCHEMA_VERSION, emptyMap(), emptySet(), emptyList(), current, longest, true,
        lastPracticeDay = last, practiceDays = days,
    )

    @Test fun `September 2026 starts on a Tuesday and has 30 days`() {
        val today = day(2026, Calendar.SEPTEMBER, 30)
        val practised = setOf(day(2026, Calendar.SEPTEMBER, 1), day(2026, Calendar.SEPTEMBER, 29), today,
            day(2026, Calendar.AUGUST, 31))
        val month = monthCalendar(practised, today)
        assertEquals(2026, month.year)
        assertEquals(Calendar.SEPTEMBER, month.month)
        val first = month.weeks.first()
        assertNull(first[0].dayOfMonth) // Monday blank
        assertEquals(1, first[1].dayOfMonth)
        assertTrue(first[1].practised)
        val cells = month.weeks.flatten().filter { it.dayOfMonth != null }
        assertEquals(30, cells.size)
        assertTrue(month.weeks.all { it.size == 7 })
        assertEquals(3, month.practisedCount) // August 31 is another month
        assertTrue(cells.last().isToday)
        assertFalse(cells.any { it.isFuture })
    }

    @Test fun `future days are marked and today is found mid-month`() {
        val today = day(2026, Calendar.FEBRUARY, 10)
        val cells = monthCalendar(emptySet(), today).weeks.flatten().filter { it.dayOfMonth != null }
        assertEquals(28, cells.size)
        assertTrue(cells[9].isToday)
        assertTrue(cells[10].isFuture)
        assertFalse(cells[8].isFuture)
    }

    @Test fun `current streak lapses after a missed day but longest and milestones stay`() {
        val today = day(2026, Calendar.SEPTEMBER, 30)
        val noon = today * 86_400_000L + 43_200_000L
        val live = streakState(snapshot(today - 1, 5, 8, emptySet()), noon, utc)
        assertEquals(5, live.current)
        assertFalse(live.practisedToday)
        assertEquals(listOf(MilestoneStatus.REACHED, MilestoneStatus.REACHED, MilestoneStatus.NEXT, MilestoneStatus.AHEAD),
            live.milestones.map { it.status })
        assertEquals(9, live.milestones[2].daysToGo)
        val lapsed = streakState(snapshot(today - 2, 5, 8, emptySet()), noon, utc)
        assertEquals(0, lapsed.current)
        assertEquals(8, lapsed.longest)
        assertEquals(MilestoneStatus.NEXT, lapsed.milestones[2].status)
        assertEquals(14, lapsed.milestones[2].daysToGo)
        assertTrue(streakState(null, noon, utc).loading)
    }
}
