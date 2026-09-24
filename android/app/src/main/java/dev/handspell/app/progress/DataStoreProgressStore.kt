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
private data class StoredProgress(
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
private data class StoredLetter(
    val letter: String,
    val attempts: Int,
    val matches: Int,
    val bestTimeToMatchMs: Long?,
    val lastPractisedAt: Long?,
)

@Serializable
private data class StoredSpeedRun(
    val roundId: String,
    val completedAt: Long,
    val correct: Int,
    val durationSeconds: Int,
)

class DataStoreProgressStore(context: Context) : ProgressStore {
    private val store = context.applicationContext.progressPreferences
    private val documentKey = stringPreferencesKey("progress_document")
    private val json = Json { ignoreUnknownKeys = true }

    override val snapshot: Flow<ProgressSnapshot> = store.data.map { preferences ->
        decode(preferences[documentKey]).toSnapshot()
    }

    override suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?) {
        val now = System.currentTimeMillis()
        val day = localDay(now)
        update { old ->
            val previous = old.letters.firstOrNull { it.letter == letter.name }
            val nextLetter = StoredLetter(
                letter = letter.name,
                attempts = (previous?.attempts ?: 0) + 1,
                matches = (previous?.matches ?: 0) + if (matched) 1 else 0,
                bestTimeToMatchMs = listOfNotNull(previous?.bestTimeToMatchMs, timeToMatchMs.takeIf { matched }).minOrNull(),
                lastPractisedAt = now,
            )
            val streak = when (old.lastPracticeDay) {
                day -> old.currentStreakDays
                day - 1 -> old.currentStreakDays + 1
                else -> 1
            }
            old.copy(
                letters = old.letters.filterNot { it.letter == letter.name } + nextLetter,
                currentStreakDays = streak,
                longestStreakDays = maxOf(old.longestStreakDays, streak),
                lastPracticeDay = day,
            )
        }
    }

    override suspend fun recordStoryStep(stepId: String) = update {
        it.copy(completedStoryStepIds = it.completedStoryStepIds + stepId)
    }

    override suspend fun recordSpeedRun(result: SpeedRunResult) = update {
        it.copy(speedRuns = (listOf(result.toStored()) + it.speedRuns).take(ProgressSnapshot.MAX_SPEED_RUNS))
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) = update {
        it.copy(onboardingCompleted = completed)
    }

    override suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private suspend fun update(transform: (StoredProgress) -> StoredProgress) {
        store.edit { preferences ->
            preferences[documentKey] = json.encodeToString(transform(decode(preferences[documentKey])))
        }
    }

    private fun decode(document: String?): StoredProgress {
        val decoded = document?.let { runCatching { json.decodeFromString<StoredProgress>(it) }.getOrNull() }
        return decoded?.takeIf { it.schemaVersion == ProgressSnapshot.SCHEMA_VERSION } ?: StoredProgress()
    }

    private fun StoredProgress.toSnapshot(): ProgressSnapshot = ProgressSnapshot(
        schemaVersion = schemaVersion,
        letters = letters.mapNotNull { saved ->
            Letter.fromNameOrNull(saved.letter)?.let { letter ->
                letter to LetterProgress(letter, saved.attempts, saved.matches, saved.bestTimeToMatchMs, saved.lastPractisedAt)
            }
        }.toMap(),
        completedStoryStepIds = completedStoryStepIds,
        speedRuns = speedRuns.map { SpeedRunResult(it.roundId, it.completedAt, it.correct, it.durationSeconds) },
        currentStreakDays = currentStreakDays.takeIf {
            val today = localDay(System.currentTimeMillis())
            lastPracticeDay == today || lastPracticeDay == today - 1
        } ?: 0,
        longestStreakDays = longestStreakDays,
        onboardingCompleted = onboardingCompleted,
    )

    private fun SpeedRunResult.toStored() = StoredSpeedRun(roundId, completedAt, correct, durationSeconds)

    private fun localDay(timeMs: Long): Long =
        Math.floorDiv(timeMs + TimeZone.getDefault().getOffset(timeMs), MILLIS_PER_DAY)

    private companion object { const val MILLIS_PER_DAY = 86_400_000L }
}
