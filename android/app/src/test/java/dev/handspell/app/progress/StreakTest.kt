package dev.handspell.app.progress

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    @Test fun `first consecutive missed and same-day sessions`() {
        val first = advanceStreak(StoredProgress(), 100)
        assertEquals(1, first.currentStreakDays)
        assertEquals(1, first.longestStreakDays)
        val again = advanceStreak(first, 100)
        assertEquals(1, again.currentStreakDays)
        val next = advanceStreak(again, 101)
        assertEquals(2, next.currentStreakDays)
        assertEquals(2, next.longestStreakDays)
        val missed = advanceStreak(next, 103)
        assertEquals(1, missed.currentStreakDays)
        assertEquals(2, missed.longestStreakDays)
    }

    @Test fun `day boundary uses the device time zone`() {
        val bangkok = TimeZone.getTimeZone("Asia/Bangkok")
        val before = 86_400_000L - 7L * 3_600_000L - 1L
        assertEquals(0L, localPracticeDay(before, bangkok))
        assertEquals(1L, localPracticeDay(before + 1L, bangkok))
    }
}
