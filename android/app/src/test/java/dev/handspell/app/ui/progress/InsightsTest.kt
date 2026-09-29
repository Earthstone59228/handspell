package dev.handspell.app.ui.progress

import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.LetterProgress
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.SpeedRunResult
import dev.handspell.app.progress.StoredProgress
import dev.handspell.app.progress.recordLetter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class InsightsTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val dayMs = 86_400_000L
    private val today = 20_000L
    private val now = today * dayMs + 12 * 3_600_000L

    private fun letter(l: Letter, attempts: Int, matches: Int, daysAgo: Long, total: Long = 0, timed: Int = 0) =
        l to LetterProgress(l, attempts, matches, null, (today - daysAgo) * dayMs + 1000, total, timed)

    private fun snapshot(vararg letters: Pair<Letter, LetterProgress>, runs: List<SpeedRunResult> = emptyList()) =
        ProgressSnapshot(ProgressSnapshot.SCHEMA_VERSION, letters.toMap(), emptySet(), runs, 0, 0, true)

    @Test fun `weakest letters by match rate, suggestion from the two weakest`() {
        val s = snapshot(
            letter(Letter.R, 5, 1, 0), letter(Letter.U, 4, 2, 1), letter(Letter.A, 6, 6, 2),
            letter(Letter.T, 1, 0, 0), letter(Letter.B, 4, 3, 10),
        )
        val insights = insightsFor(s, now, utc)
        assertEquals(listOf(Letter.R, Letter.U, Letter.B), insights.weakest.map { it.letter })
        assertEquals(listOf(Letter.R, Letter.U), insights.suggestion)
        assertEquals(0.2f, insights.weakest.first().rate, 0.001f)
    }

    @Test fun `this week counts the last seven local days`() {
        val s = snapshot(letter(Letter.A, 1, 1, 0), letter(Letter.B, 1, 1, 6), letter(Letter.C, 1, 1, 7))
        assertEquals(2, insightsFor(s, now, utc).practisedThisWeek)
    }

    @Test fun `average time uses only timed matches and best speed is the highest score`() {
        val s = snapshot(letter(Letter.A, 3, 3, 0, total = 3000, timed = 2), letter(Letter.B, 2, 2, 0, total = 3000, timed = 1),
            runs = listOf(SpeedRunResult("daily-letters", 1, 4, 60), SpeedRunResult("speed-1", 2, 9, 60)))
        val insights = insightsFor(s, now, utc)
        assertEquals(2000L, insights.averageMatchMs)
        assertEquals(9, insights.bestSpeedScore)
    }

    @Test fun `a new learner is pointed at untried letters and has no numbers yet`() {
        val insights = insightsFor(null, now, utc)
        assertTrue(insights.weakest.isEmpty())
        assertEquals(listOf(Letter.A, Letter.B), insights.suggestion)
        assertNull(insights.averageMatchMs)
        assertNull(insights.bestSpeedScore)
    }

    @Test fun `stored letters keep the total and count of timed matches`() {
        var state = recordLetter(StoredProgress(), Letter.A, true, 1500, now = 1, day = 1)
        state = recordLetter(state, Letter.A, true, null, now = 2, day = 1)
        state = recordLetter(state, Letter.A, false, null, now = 3, day = 1)
        state = recordLetter(state, Letter.A, true, 500, now = 4, day = 1)
        assertEquals(2000L, state.letters.single().totalMatchMs)
        assertEquals(2, state.letters.single().timedMatches)
    }
}
