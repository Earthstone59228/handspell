package dev.handspell.app.ui.menu

import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.LetterProgress
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.WordRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class MainMenuStateTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 20_000L
    private val noon = day * 86_400_000L + 43_200_000L

    private fun snapshot(lastDay: Long?, streak: Int) = ProgressSnapshot(
        schemaVersion = ProgressSnapshot.SCHEMA_VERSION,
        letters = mapOf(
            Letter.A to LetterProgress(Letter.A, 2, 1, 500, 1),
            Letter.B to LetterProgress(Letter.B, 3, 0, null, 1),
        ),
        completedStoryStepIds = emptySet(), speedRuns = emptyList(),
        currentStreakDays = streak, longestStreakDays = streak, onboardingCompleted = true,
        lastPracticeDay = lastDay,
        words = mapOf("hello" to WordRecord("hello", matches = 1), "bye" to WordRecord("bye", markedComplete = true),
            "yes" to WordRecord("yes", attempts = 2)),
        alphabetCompleted = setOf("A", "C", "J"),
    )

    @Test fun `loading until progress is read`() {
        assertTrue(mainMenuState(null, listOf("hello"), false, noon, utc).loading)
    }

    @Test fun `the page's list decides letters, camera matches only before its first report`() {
        val state = mainMenuState(snapshot(day, 3), listOf("hello", "bye", "yes"), false, noon, utc)
        assertEquals(3, state.lettersComplete) // A, C and J from the page
        assertEquals(1, mainMenuState(snapshot(day, 3).copy(alphabetCompleted = null), emptyList(), false, noon, utc)
            .lettersComplete) // only A has a camera match
        assertEquals(0, mainMenuState(snapshot(day, 3).copy(alphabetCompleted = emptySet()), emptyList(), false, noon, utc)
            .lettersComplete)
        assertEquals(Letter.entries.size, state.lettersTotal)
        assertEquals(2, state.wordsComplete)
        assertEquals(3, state.wordsTotal)
    }

    @Test fun `progress screen and menu share one letters-complete count`() {
        val s = snapshot(day, 3)
        assertEquals(3, lettersCompleteCount(s))
        assertEquals(mainMenuState(s, emptyList(), false, noon, utc).lettersComplete, lettersCompleteCount(s))
        assertEquals(1, lettersCompleteCount(s.copy(alphabetCompleted = null)))
    }

    @Test fun `streak is live today or yesterday and zero after a gap`() {
        mainMenuState(snapshot(day, 3), emptyList(), false, noon, utc).let {
            assertEquals(3, it.streakDays); assertTrue(it.practisedToday)
        }
        mainMenuState(snapshot(day - 1, 3), emptyList(), false, noon, utc).let {
            assertEquals(3, it.streakDays); assertFalse(it.practisedToday)
        }
        assertEquals(0, mainMenuState(snapshot(day - 2, 3), emptyList(), false, noon, utc).streakDays)
    }
}
