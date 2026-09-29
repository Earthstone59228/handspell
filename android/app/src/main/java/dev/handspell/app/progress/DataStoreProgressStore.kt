package dev.handspell.app.progress

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import dev.handspell.app.core.model.Letter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.TimeZone

private val Context.progressPreferences by preferencesDataStore(
    name = "progress",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Serializable
internal data class StoredProgress(
    val schemaVersion: Int = ProgressSnapshot.SCHEMA_VERSION,
    val letters: List<StoredLetter> = emptyList(),
    val completedStoryStepIds: Set<String> = emptySet(),
    val speedRuns: List<StoredSpeedRun> = emptyList(),
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val onboardingCompleted: Boolean = false,
    val lastPracticeDay: Long? = null,
    val words: List<StoredWord> = emptyList(),
    val alphabetCompleted: Set<String>? = null,
    val today: StoredDay? = null,
    /** Null in documents written before practice days were kept; filled in by [migrateProgress]. */
    val practiceDays: List<Long>? = null,
    val freeSpeedChallengeDay: Long? = null,
    val questCelebratedDay: Long? = null,
)

@Serializable
internal data class StoredDay(
    val day: Long,
    val letters: Set<String> = emptySet(),
    val words: Set<String> = emptySet(),
    val speedRounds: Int = 0,
)

@Serializable
internal data class StoredWord(
    val gloss: String,
    val attempts: Int = 0,
    val matches: Int = 0,
    val bestTimeToMatchMs: Long? = null,
    val lastPractisedAt: Long? = null,
    val markedComplete: Boolean = false,
)

@Serializable
internal data class StoredLetter(
    val letter: String,
    val attempts: Int,
    val matches: Int,
    val bestTimeToMatchMs: Long?,
    val lastPractisedAt: Long?,
    val totalMatchMs: Long = 0,
    val timedMatches: Int = 0,
)

@Serializable
internal data class StoredSpeedRun(
    val roundId: String,
    val completedAt: Long,
    val correct: Int,
    val durationSeconds: Int,
)

private val progressJson = Json { ignoreUnknownKeys = true }

class DataStoreProgressStore(context: Context) : ProgressStore {
    private val store = context.applicationContext.progressPreferences
    private val documentKey = stringPreferencesKey("progress_document")
    private val unreadableBackupKey = stringPreferencesKey("unreadable_progress_document_backup")

    override val snapshot: Flow<ProgressSnapshot> = store.data.map { preferences ->
        val raw = preferences[documentKey]
        val decoded = decode(raw)
        (decoded ?: StoredProgress()).toSnapshot(
            unreadable = progressUnreadableFlag(raw, decoded, preferences[unreadableBackupKey]),
        )
    }

    override suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?) {
        val now = System.currentTimeMillis()
        update { recordLetter(it, letter, matched, timeToMatchMs, now, localPracticeDay(now)) }
    }

    override suspend fun recordWordAttempt(gloss: String, matched: Boolean, timeToMatchMs: Long?) {
        val now = System.currentTimeMillis()
        update { recordWord(it, gloss, matched, timeToMatchMs, now, localPracticeDay(now)) }
    }

    override suspend fun setWordMarkedComplete(gloss: String, complete: Boolean) {
        val now = System.currentTimeMillis()
        update { markWord(it, gloss, complete, localPracticeDay(now)) }
    }

    override suspend fun recordLetterMarked(letter: String) {
        val now = System.currentTimeMillis()
        update { markLetter(it, letter, localPracticeDay(now)) }
    }

    override suspend fun setAlphabetCompleted(letters: Set<String>) = update {
        it.copy(alphabetCompleted = letters)
    }

    override suspend fun recordStoryStep(stepId: String) = update {
        it.copy(completedStoryStepIds = it.completedStoryStepIds + stepId)
    }

    override suspend fun recordSpeedRun(result: SpeedRunResult) = update {
        recordSpeed(it, result.toStored(), localPracticeDay(result.completedAt))
    }

    override suspend fun useFreeSpeedChallenge(day: Long) = update { it.copy(freeSpeedChallengeDay = day) }

    override suspend fun setQuestCelebrated(day: Long) = update { it.copy(questCelebratedDay = day) }

    override suspend fun setOnboardingCompleted(completed: Boolean) = update {
        it.copy(onboardingCompleted = completed)
    }

    override suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private suspend fun update(transform: (StoredProgress) -> StoredProgress) {
        store.edit { preferences ->
            val decision = planProgressWrite(
                preferences[documentKey], preferences[unreadableBackupKey], transform,
            )
            decision.backup?.let { preferences[unreadableBackupKey] = it }
            preferences[documentKey] = decision.serialized
        }
    }

    private fun decode(document: String?): StoredProgress? = decodeProgressDocument(document)

    private fun StoredProgress.toSnapshot(unreadable: Boolean): ProgressSnapshot = ProgressSnapshot(
        schemaVersion = schemaVersion,
        letters = letters.mapNotNull { saved ->
            Letter.fromNameOrNull(saved.letter)?.let { letter ->
                letter to LetterProgress(letter, saved.attempts, saved.matches, saved.bestTimeToMatchMs, saved.lastPractisedAt,
                    saved.totalMatchMs, saved.timedMatches)
            }
        }.toMap(),
        completedStoryStepIds = completedStoryStepIds,
        speedRuns = speedRuns.map { SpeedRunResult(it.roundId, it.completedAt, it.correct, it.durationSeconds) },
        currentStreakDays = currentStreakDays.takeIf {
            val today = localPracticeDay(System.currentTimeMillis())
            lastPracticeDay == today || lastPracticeDay == today - 1
        } ?: 0,
        longestStreakDays = longestStreakDays,
        onboardingCompleted = onboardingCompleted,
        unreadable = unreadable,
        lastPracticeDay = lastPracticeDay,
        words = words.associate { it.gloss to WordRecord(
            it.gloss, it.attempts, it.matches, it.bestTimeToMatchMs, it.lastPractisedAt, it.markedComplete,
        ) },
        alphabetCompleted = alphabetCompleted,
        today = today?.let { DayActivity(it.day, it.letters, it.words, it.speedRounds) },
        practiceDays = practiceDays.orEmpty().toSet(),
        freeSpeedChallengeDay = freeSpeedChallengeDay,
        questCelebratedDay = questCelebratedDay,
    )

    private fun SpeedRunResult.toStored() = StoredSpeedRun(roundId, completedAt, correct, durationSeconds)

}

internal fun advanceStreak(old: StoredProgress, day: Long): StoredProgress {
    val streak = when (old.lastPracticeDay) {
        day -> old.currentStreakDays
        day - 1 -> old.currentStreakDays + 1
        else -> 1
    }
    val days = (old.practiceDays.orEmpty().filterNot { it == day } + day).sorted()
        .takeLast(ProgressSnapshot.MAX_PRACTICE_DAYS)
    return old.copy(
        currentStreakDays = streak,
        longestStreakDays = maxOf(old.longestStreakDays, streak),
        lastPracticeDay = day,
        practiceDays = days,
    )
}

/**
 * One camera attempt at a letter. A letter that is already complete can be practised again: each match adds to its
 * counts, and the streak moves at most once per day however often it is completed.
 */
internal fun recordLetter(
    old: StoredProgress, letter: Letter, matched: Boolean, timeToMatchMs: Long?, now: Long, day: Long,
): StoredProgress {
    val previous = old.letters.firstOrNull { it.letter == letter.name }
    val nextLetter = StoredLetter(
        letter = letter.name,
        attempts = (previous?.attempts ?: 0) + 1,
        matches = (previous?.matches ?: 0) + if (matched) 1 else 0,
        bestTimeToMatchMs = listOfNotNull(previous?.bestTimeToMatchMs, timeToMatchMs.takeIf { matched }).minOrNull(),
        lastPractisedAt = now,
        totalMatchMs = (previous?.totalMatchMs ?: 0) + if (matched && timeToMatchMs != null) timeToMatchMs else 0,
        timedMatches = (previous?.timedMatches ?: 0) + if (matched && timeToMatchMs != null) 1 else 0,
    )
    val base = advanceStreak(old, day).copy(letters = old.letters.filterNot { it.letter == letter.name } + nextLetter)
    return if (matched) base.copy(today = dayOf(old, day).let { it.copy(letters = it.letters + letter.name) }) else base
}

/**
 * A finished speed round. Saving the same result twice (a retried save) replaces it, so today's round count only
 * grows for a new result.
 */
internal fun recordSpeed(old: StoredProgress, run: StoredSpeedRun, day: Long): StoredProgress {
    val duplicate = old.speedRuns.any { it.roundId == run.roundId && it.completedAt == run.completedAt }
    val runs = (listOf(run) + old.speedRuns.filterNot { it.roundId == run.roundId && it.completedAt == run.completedAt })
        .take(ProgressSnapshot.MAX_SPEED_RUNS)
    if (duplicate) return old.copy(speedRuns = runs)
    return old.copy(speedRuns = runs, today = dayOf(old, day).let { it.copy(speedRounds = it.speedRounds + 1) })
}

/** The alphabet page's "Mark complete": practice today, and a completion for the day's count. */
internal fun markLetter(old: StoredProgress, letter: String, day: Long): StoredProgress =
    advanceStreak(old, day).copy(today = dayOf(old, day).let { it.copy(letters = it.letters + letter) })

/** Today's record, fresh when the stored one is from another day. */
internal fun dayOf(old: StoredProgress, day: Long): StoredDay =
    old.today?.takeIf { it.day == day } ?: StoredDay(day)

/** One camera attempt at a word. A match advances the streak; a skip is recorded but is not practice. */
internal fun recordWord(
    old: StoredProgress, gloss: String, matched: Boolean, timeToMatchMs: Long?, now: Long, day: Long,
): StoredProgress {
    val previous = old.words.firstOrNull { it.gloss == gloss } ?: StoredWord(gloss)
    val next = previous.copy(
        attempts = previous.attempts + 1,
        matches = previous.matches + if (matched) 1 else 0,
        bestTimeToMatchMs = listOfNotNull(previous.bestTimeToMatchMs, timeToMatchMs.takeIf { matched }).minOrNull(),
        lastPractisedAt = now,
    )
    val base = advanceStreak(old, day).copy(words = old.words.filterNot { it.gloss == gloss } + next)
    return if (matched) base.copy(today = dayOf(old, day).let { it.copy(words = it.words + gloss) }) else base
}

/** "Mark complete" (idempotent) or its undo. Marking counts as practice today; undoing never touches the streak. */
internal fun markWord(old: StoredProgress, gloss: String, complete: Boolean, day: Long): StoredProgress {
    val previous = old.words.firstOrNull { it.gloss == gloss } ?: StoredWord(gloss)
    val next = previous.copy(markedComplete = complete)
    if (!complete) return old.copy(words = old.words.filterNot { it.gloss == gloss } + next)
    return advanceStreak(old, day).copy(
        words = old.words.filterNot { it.gloss == gloss } + next,
        today = dayOf(old, day).let { it.copy(words = it.words + gloss) },
    )
}

internal fun localPracticeDay(timeMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Math.floorDiv(timeMs + zone.getOffset(timeMs), 86_400_000L)

internal fun decodeProgressDocument(document: String?): StoredProgress? {
    if (document == null) return StoredProgress()
    val decoded = runCatching {
        progressJson.decodeFromString<StoredProgress>(document)
    }.getOrNull() ?: return null
    return migrateProgress(decoded)
}

/**
 * Older documents are upgraded in place: every field added since v1 has a default, so migrating is only a version
 * bump. A document from a newer app version (or a nonsense version) stays unreadable and is backed up, never
 * overwritten.
 */
internal fun migrateProgress(decoded: StoredProgress): StoredProgress? = when (decoded.schemaVersion) {
    in 1..ProgressSnapshot.SCHEMA_VERSION ->
        decoded.copy(schemaVersion = ProgressSnapshot.SCHEMA_VERSION, practiceDays = decoded.practiceDays ?: seedPracticeDays(decoded))
    else -> null
}

/**
 * Documents from before practice days were kept only know the last practice day and the streak that ended on it. A
 * streak of N ending on day D means D-N+1..D were all practised, so exactly those days are filled in; nothing is
 * invented for the time before the streak.
 */
internal fun seedPracticeDays(old: StoredProgress): List<Long> {
    val last = old.lastPracticeDay ?: return emptyList()
    val length = old.currentStreakDays.coerceIn(1, ProgressSnapshot.MAX_PRACTICE_DAYS)
    return ((last - length + 1)..last).toList()
}

internal fun progressUnreadableFlag(
    raw: String?, decoded: StoredProgress?, backup: String?,
): Boolean = (raw != null && decoded == null) || backup != null

internal data class ProgressWriteDecision(val serialized: String, val backup: String?)

internal fun planProgressWrite(
    raw: String?,
    existingBackup: String?,
    transform: (StoredProgress) -> StoredProgress,
): ProgressWriteDecision {
    val decoded = decodeProgressDocument(raw)
    val backup = if (decoded == null && existingBackup == null) raw else null
    return ProgressWriteDecision(
        serialized = progressJson.encodeToString(transform(decoded ?: StoredProgress())),
        backup = backup,
    )
}
