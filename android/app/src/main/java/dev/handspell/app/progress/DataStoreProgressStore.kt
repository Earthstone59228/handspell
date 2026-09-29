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
)

@Serializable
internal data class StoredLetter(
    val letter: String,
    val attempts: Int,
    val matches: Int,
    val bestTimeToMatchMs: Long?,
    val lastPractisedAt: Long?,
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
        val day = localPracticeDay(now)
        update { old ->
            val previous = old.letters.firstOrNull { it.letter == letter.name }
            val nextLetter = StoredLetter(
                letter = letter.name,
                attempts = (previous?.attempts ?: 0) + 1,
                matches = (previous?.matches ?: 0) + if (matched) 1 else 0,
                bestTimeToMatchMs = listOfNotNull(previous?.bestTimeToMatchMs, timeToMatchMs.takeIf { matched }).minOrNull(),
                lastPractisedAt = now,
            )
            advanceStreak(old, day).copy(
                letters = old.letters.filterNot { it.letter == letter.name } + nextLetter,
            )
        }
    }

    override suspend fun recordStoryStep(stepId: String) = update {
        it.copy(completedStoryStepIds = it.completedStoryStepIds + stepId)
    }

    override suspend fun recordSpeedRun(result: SpeedRunResult) = update {
        it.copy(speedRuns = (listOf(result.toStored()) + it.speedRuns.filterNot { saved ->
            saved.roundId == result.roundId && saved.completedAt == result.completedAt
        }).take(ProgressSnapshot.MAX_SPEED_RUNS))
    }

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
                letter to LetterProgress(letter, saved.attempts, saved.matches, saved.bestTimeToMatchMs, saved.lastPractisedAt)
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
    )

    private fun SpeedRunResult.toStored() = StoredSpeedRun(roundId, completedAt, correct, durationSeconds)

}

internal fun advanceStreak(old: StoredProgress, day: Long): StoredProgress {
    val streak = when (old.lastPracticeDay) {
        day -> old.currentStreakDays
        day - 1 -> old.currentStreakDays + 1
        else -> 1
    }
    return old.copy(
        currentStreakDays = streak,
        longestStreakDays = maxOf(old.longestStreakDays, streak),
        lastPracticeDay = day,
    )
}

internal fun localPracticeDay(timeMs: Long, zone: TimeZone = TimeZone.getDefault()): Long =
    Math.floorDiv(timeMs + zone.getOffset(timeMs), 86_400_000L)

internal fun decodeProgressDocument(document: String?): StoredProgress? {
    if (document == null) return StoredProgress()
    val decoded = runCatching {
        progressJson.decodeFromString<StoredProgress>(document)
    }.getOrNull()
    return decoded?.takeIf { it.schemaVersion == ProgressSnapshot.SCHEMA_VERSION }
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
