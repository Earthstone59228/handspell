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
    /** Sum and count of timed matches, for the average time to match (Progress insights). */
    val totalMatchMs: Long = 0,
    val timedMatches: Int = 0,
)

/**
 * Per-word practice history, keyed by gloss. [markedComplete] is the learner's own "Mark complete"; a camera match
 * also counts as complete. Either way the word stays practicable.
 */
data class WordRecord(
    val gloss: String,
    val attempts: Int = 0,
    val matches: Int = 0,
    val bestTimeToMatchMs: Long? = null,
    val lastPractisedAt: Long? = null,
    val markedComplete: Boolean = false,
) {
    val complete: Boolean get() = markedComplete || matches > 0
}

/** What was completed on one local day; reset on the first record of a new day. Feeds rewards and the daily quest. */
data class DayActivity(
    val day: Long,
    val letters: Set<String> = emptySet(),
    val words: Set<String> = emptySet(),
    val speedRounds: Int = 0,
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
    val unreadable: Boolean = false,
    val lastPracticeDay: Long? = null,
    val words: Map<String, WordRecord> = emptyMap(),
    /**
     * Letters the alphabet menu shows as complete (its own "Mark complete" plus camera matches it has merged), as the
     * page last reported them, or null before its first report. The page keeps its own copy; this one lets native
     * screens count them.
     */
    val alphabetCompleted: Set<String>? = null,
    /** Today's completions, or an older day's record that no longer applies (check [DayActivity.day]). */
    val today: DayActivity? = null,
    /** Local days (epoch days, see localPracticeDay) with any practice, newest [MAX_PRACTICE_DAYS] kept. */
    val practiceDays: Set<Long> = emptySet(),
    /** The local day on which the free daily speed challenge was last started, or null if never. */
    val freeSpeedChallengeDay: Long? = null,
    /** The day whose daily quest reward has been shown, so it shows once. */
    val questCelebratedDay: Long? = null,
) {
    companion object {
        /** v2 added [words]. Older documents are migrated on read (see decodeProgressDocument). */
        const val SCHEMA_VERSION = 2
        const val MAX_SPEED_RUNS = 50
        const val MAX_PRACTICE_DAYS = 400
    }
}

/**
 * Local-only persistence. Backed by DataStore; all writes are suspending and off the main thread.
 *
 * [clearAll] is a real feature, not a debug hook: it is the app's data-deletion path, reachable from
 * Settings, and it must leave no stored practice data behind.
 */
interface ProgressStore {
    val snapshot: Flow<ProgressSnapshot>

    suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?)

    /** A camera attempt at a word. A match counts towards the streak, like a letter attempt. */
    suspend fun recordWordAttempt(gloss: String, matched: Boolean, timeToMatchMs: Long?) {}

    /** The learner's own "Mark complete" (or its undo) for a word. Counts as practice for the streak when set. */
    suspend fun setWordMarkedComplete(gloss: String, complete: Boolean) {}

    /** The alphabet page's own "Mark complete" for [letter]: counts as practice today, like marking a word. */
    suspend fun recordLetterMarked(letter: String) {}

    /** A free user started today's speed challenge; the free round is used for [day] (a local epoch day). */
    suspend fun useFreeSpeedChallenge(day: Long) {}

    suspend fun setQuestCelebrated(day: Long) {}

    /** Mirror of the alphabet page's completed letters; see [ProgressSnapshot.alphabetCompleted]. */
    suspend fun setAlphabetCompleted(letters: Set<String>) {}

    suspend fun recordStoryStep(stepId: String)

    suspend fun recordSpeedRun(result: SpeedRunResult)

    suspend fun setOnboardingCompleted(completed: Boolean)

    suspend fun clearAll()
}
