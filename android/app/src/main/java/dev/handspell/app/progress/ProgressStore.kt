package dev.handspell.app.progress

import dev.handspell.app.core.model.Letter
import kotlinx.coroutines.flow.Flow

/** Per-letter practice history. Counts only; no frames, no images, nothing identifying. */
data class LetterProgress(
    val letter: Letter,
    val attempts: Int,
    val matches: Int,
    /** Fastest confirmed match, milliseconds from prompt shown to match. */
    val bestTimeToMatchMs: Long?,
    /** Epoch millis of the most recent attempt, or null if never practised. */
    val lastPractisedAt: Long?,
)

data class SpeedRunResult(
    val roundId: String,
    val completedAt: Long,
    val correct: Int,
    val durationSeconds: Int,
)

/** Everything the app knows about the user. All of it is local and all of it is deletable. */
data class ProgressSnapshot(
    val schemaVersion: Int,
    val letters: Map<Letter, LetterProgress>,
    val completedStoryStepIds: Set<String>,
    /** Most recent runs first, capped so the record cannot grow without bound. */
    val speedRuns: List<SpeedRunResult>,
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val onboardingCompleted: Boolean,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_SPEED_RUNS = 50
    }
}

/**
 * Local-only persistence. Backed by DataStore; all writes are suspending and off the main thread.
 *
 * [clearAll] is a real feature, not a debug hook: it is the app's data-deletion path, reachable from
 * Settings, and it must leave no file behind.
 */
interface ProgressStore {
    val snapshot: Flow<ProgressSnapshot>

    suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?)

    suspend fun recordStoryStep(stepId: String)

    suspend fun recordSpeedRun(result: SpeedRunResult)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun clearAll()
}
