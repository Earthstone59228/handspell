package dev.handspell.app.ui.reward

import dev.handspell.app.progress.DayActivity
import dev.handspell.app.progress.ProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RewardTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 20_000L
    private val now = day * 86_400_000L + 3_600_000L
    private val a = RewardSubject(RewardSubject.Kind.LETTER, "A", "A")
    private val hello = RewardSubject(RewardSubject.Kind.WORD, "hello", "hello")

    private fun snapshot(lastDay: Long?, streak: Int, today: DayActivity? = null) = ProgressSnapshot(
        ProgressSnapshot.SCHEMA_VERSION, emptyMap(), emptySet(), emptyList(), streak, streak, true,
        lastPracticeDay = lastDay, today = today,
    )

    @Test fun `first ever completion starts a one-day streak`() {
        val reward = rewardFor(a, null, now, utc)
        assertEquals(1, reward.completedToday)
        assertEquals(1, reward.streakDays)
        assertEquals(3, reward.nextMilestone)
        assertFalse(reward.milestoneReached)
    }

    @Test fun `same numbers before and after the completion is saved`() {
        val before = rewardFor(a, snapshot(day - 1, 2, DayActivity(day - 1, setOf("B"))), now, utc)
        val after = rewardFor(a, snapshot(day, 3, DayActivity(day, setOf("A"))), now, utc)
        assertEquals(before.copy(), after)
        assertEquals(3, after.streakDays)
        assertTrue(after.milestoneReached)
        assertEquals(7, after.nextMilestone)
    }

    @Test fun `today's count merges letters and words and ignores other days`() {
        val today = DayActivity(day, letters = setOf("A", "B"), words = setOf("yes"))
        assertEquals(4, rewardFor(hello, snapshot(day, 5, today), now, utc).completedToday)
        assertEquals(3, rewardFor(a, snapshot(day, 5, today), now, utc).completedToday)
        assertEquals(1, rewardFor(a, snapshot(day, 5, DayActivity(day - 1, setOf("C", "D"))), now, utc).completedToday)
    }

    @Test fun `a gap resets the streak and milestones stop at thirty`() {
        assertEquals(1, rewardFor(a, snapshot(day - 3, 9, null), now, utc).streakDays)
        assertNull(nextMilestone(30))
        assertEquals(14, nextMilestone(7))
    }
}
