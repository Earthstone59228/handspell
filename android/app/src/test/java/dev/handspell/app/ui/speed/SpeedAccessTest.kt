package dev.handspell.app.ui.speed

import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.SpeedRunResult
import dev.handspell.app.progress.StoredProgress
import dev.handspell.app.progress.StoredSpeedRun
import dev.handspell.app.progress.recordSpeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SpeedAccessTest {
    private val bangkok = TimeZone.getTimeZone("Asia/Bangkok")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    private fun at(zone: TimeZone, y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        Calendar.getInstance(zone).apply { clear(); set(y, m, d, h, min) }.timeInMillis

    @Test fun `free gets one round a day and it resets at local midnight`() {
        val evening = at(bangkok, 2026, Calendar.SEPTEMBER, 30, 22, 30)
        val today = Math.floorDiv(evening + bangkok.getOffset(evening), 86_400_000L)
        assertEquals(SpeedAccess.FreeAvailable, speedAccess(false, null, evening, bangkok))
        assertEquals(SpeedAccess.FreeAvailable, speedAccess(false, today - 1, evening, bangkok))
        val used = speedAccess(false, today, evening, bangkok)
        assertTrue(used is SpeedAccess.UsedToday)
        assertFalse(used.canStart)
        assertEquals(at(bangkok, 2026, Calendar.OCTOBER, 1, 0), (used as SpeedAccess.UsedToday).resetsAtMs)
        assertEquals(1 to 30, timeUntil(used.resetsAtMs, evening))
        // One minute after midnight the next day it is available again.
        assertEquals(SpeedAccess.FreeAvailable, speedAccess(false, today, used.resetsAtMs + 60_000, bangkok))
    }

    @Test fun `pro is unlimited even after a free round was used`() {
        val now = at(bangkok, 2026, Calendar.SEPTEMBER, 30, 9)
        val today = Math.floorDiv(now + bangkok.getOffset(now), 86_400_000L)
        assertEquals(SpeedAccess.Unlimited, speedAccess(true, today, now, bangkok))
    }

    @Test fun `midnight is found across a daylight saving change`() {
        // New York springs forward on 8 March 2026; the day is 23 hours long.
        val before = at(newYork, 2026, Calendar.MARCH, 7, 23, 0)
        assertEquals(at(newYork, 2026, Calendar.MARCH, 8, 0), nextLocalMidnight(before, newYork))
        val during = at(newYork, 2026, Calendar.MARCH, 8, 12, 0)
        assertEquals(at(newYork, 2026, Calendar.MARCH, 9, 0), nextLocalMidnight(during, newYork))
        assertEquals(0 to 1, timeUntil(1_000, 1_000 - 30_000))
    }

    @Test fun `prompts are a fixed shuffle per round`() {
        val ids = ('A'..'Y').map { it.toString() }
        assertEquals(challengePrompts(ids, 42), challengePrompts(ids, 42))
        assertEquals(ids.toSet(), challengePrompts(ids, 7).toSet())
        assertEquals(ids.size, challengePrompts(ids, 7).size)
    }

    @Test fun `finished rounds count once per result and the best score is per mode`() {
        var state = StoredProgress()
        state = recordSpeed(state, StoredSpeedRun("daily-letters", 100, 7, 60), day = 5)
        state = recordSpeed(state, StoredSpeedRun("daily-letters", 100, 7, 60), day = 5) // retried save
        state = recordSpeed(state, StoredSpeedRun("daily-words", 200, 3, 60), day = 5)
        assertEquals(2, state.today?.speedRounds)
        assertEquals(2, state.speedRuns.size)
        val snapshot = ProgressSnapshot(ProgressSnapshot.SCHEMA_VERSION, emptyMap(), emptySet(),
            listOf(SpeedRunResult("daily-letters", 1, 7, 60), SpeedRunResult("daily-letters", 2, 9, 60),
                SpeedRunResult("daily-words", 3, 3, 60)), 0, 0, true)
        assertEquals(9, bestScore(snapshot, SpeedMode.LETTERS))
        assertEquals(3, bestScore(snapshot, SpeedMode.WORDS))
        assertNull(bestScore(null, SpeedMode.LETTERS))
    }
}
