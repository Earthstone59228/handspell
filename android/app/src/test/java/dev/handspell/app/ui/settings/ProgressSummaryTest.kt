package dev.handspell.app.ui.settings

import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.LetterProgress
import dev.handspell.app.progress.ProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSummaryTest {
    private fun snapshot(letters: Map<Letter, LetterProgress> = emptyMap()) = ProgressSnapshot(
        schemaVersion = ProgressSnapshot.SCHEMA_VERSION,
        letters = letters,
        completedStoryStepIds = emptySet(),
        speedRuns = emptyList(),
        currentStreakDays = 2,
        longestStreakDays = 3,
        onboardingCompleted = false,
    )

    @Test fun emptyHistoryHasEmptySummary() {
        assertTrue(snapshot().toSummary() is ProgressSummary.Empty)
    }

    @Test fun recordedSummaryCountsAttemptsAndMatchesSeparately() {
        val progress = LetterProgress(Letter.A, attempts = 3, matches = 1,
            bestTimeToMatchMs = 1200, lastPractisedAt = 1)
        assertEquals(ProgressSummary.Recorded(2, 1, Letter.staticLetters.size, 3, 1),
            snapshot(mapOf(Letter.A to progress)).toSummary())
    }
}
