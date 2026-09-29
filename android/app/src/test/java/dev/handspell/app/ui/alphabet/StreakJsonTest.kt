package dev.handspell.app.ui.alphabet

import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakJsonTest {
    private val now = 86_400_000L * 10
    private val day = localPracticeDay(now)
    private fun snapshot(last: Long?) = ProgressSnapshot(
        1, emptyMap(), emptySet(), emptyList(), 4, 7, true, lastPracticeDay = last,
    )

    @Test fun `bridge encodes active and pending streaks`() {
        assertEquals("{\"current\":4,\"longest\":7,\"today\":true}", streakJson(snapshot(day), now))
        assertEquals("{\"current\":4,\"longest\":7,\"today\":false}", streakJson(snapshot(day - 1), now))
        assertEquals("{\"current\":0,\"longest\":7,\"today\":false}", streakJson(snapshot(day - 2), now))
        assertEquals("{\"current\":0,\"longest\":0,\"today\":false}", streakJson(null, now))
    }
}
