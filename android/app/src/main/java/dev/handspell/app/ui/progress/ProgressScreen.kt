package dev.handspell.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.R
import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun ProgressRoute(progressStore: ProgressStore, onPractice: () -> Unit, onSettings: () -> Unit) {
    val snapshot by progressStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
    ProgressScreen(snapshot, onPractice, onSettings)
}

@Composable
private fun ProgressScreen(snapshot: ProgressSnapshot?, onPractice: () -> Unit, onSettings: () -> Unit) {
    val colors = LocalAslColors.current
    Column(
        Modifier.fillMaxSize().background(colors.backgroundGrouped)
            .verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.alphabet_progress_note), style = MaterialTheme.typography.bodyLarge)
        when {
            snapshot == null -> Text(stringResource(R.string.settings_loading), style = MaterialTheme.typography.bodyLarge)
            snapshot.letters.isEmpty() -> {
                Text(stringResource(R.string.progress_empty), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.progress_empty_body), style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onPractice, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                    Text(stringResource(R.string.progress_start))
                }
            }
            else -> {
                val practised = snapshot.letters.values.count { it.attempts > 0 }
                Text(stringResource(R.string.practice_letters_progress, practised, Letter.staticLetters.size), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.settings_attempts_count,
                    snapshot.letters.values.sumOf { it.attempts }, snapshot.letters.values.sumOf { it.matches }),
                    style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.settings_streak_days, snapshot.currentStreakDays), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.progress_by_letter), style = MaterialTheme.typography.titleMedium)
                snapshot.letters.values.sortedBy { it.letter.name }.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.letter.display, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.progress_letter_count, item.matches, item.attempts),
                            style = MaterialTheme.typography.bodyLarge, color = colors.labelSecondary)
                    }
                }
                if (snapshot.completedStoryStepIds.isNotEmpty()) {
                    Text(stringResource(R.string.progress_story_steps, snapshot.completedStoryStepIds.size),
                        style = MaterialTheme.typography.bodyLarge)
                }
                if (snapshot.speedRuns.isNotEmpty()) {
                    Text(stringResource(R.string.progress_speed_runs), style = MaterialTheme.typography.titleMedium)
                    snapshot.speedRuns.take(5).forEach { run ->
                        Text(stringResource(R.string.progress_speed_result, run.correct, run.durationSeconds),
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        if (snapshot != null && (snapshot.letters.isNotEmpty() || snapshot.speedRuns.isNotEmpty() ||
                    snapshot.completedStoryStepIds.isNotEmpty())) {
            TextButton(onClick = onSettings, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                Text(stringResource(R.string.progress_manage_data))
            }
        }
    }
}
