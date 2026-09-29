package dev.handspell.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.R
import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.ProLockLabel
import androidx.compose.runtime.remember
import dev.handspell.app.ui.settings.FrostedSettingsHero
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupFooter
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.settings.SettingsInfoRow
import dev.handspell.app.ui.settings.SettingsValueRow
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun ProgressRoute(
    progressStore: ProgressStore, onBack: () -> Unit, onPractice: () -> Unit, onSettings: () -> Unit,
    isPro: Boolean = false, onSeePro: () -> Unit = {},
) {
    val snapshot by progressStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
    ProgressScreen(snapshot, onBack, onPractice, onSettings, isPro, onSeePro)
}

/** Same frosted hero and grouped cards as Settings: a stat row first, then the detail groups. */
@Composable
private fun ProgressScreen(
    snapshot: ProgressSnapshot?, onBack: () -> Unit, onPractice: () -> Unit, onSettings: () -> Unit,
    isPro: Boolean, onSeePro: () -> Unit,
) {
    FrostedSettingsHero(
        onBack = onBack,
        title = stringResource(R.string.progress_title),
        body = stringResource(R.string.progress_hero_body),
    ) {
        when {
            snapshot == null -> SettingsGroup {
                SettingsValueRow(stringResource(R.string.progress_title), stringResource(R.string.settings_loading))
            }
            snapshot.letters.isEmpty() && snapshot.speedRuns.isEmpty() && snapshot.completedStoryStepIds.isEmpty() ->
                SettingsGroup {
                    Column {
                        SettingsInfoRow(stringResource(R.string.progress_empty), stringResource(R.string.progress_empty_body))
                        SettingsDivider()
                        SettingsActionRow(stringResource(R.string.progress_start), onClick = onPractice)
                    }
                }
            else -> RecordedProgress(snapshot, onSettings, isPro, onSeePro)
        }
    }
}

@Composable
private fun RecordedProgress(snapshot: ProgressSnapshot, onSettings: () -> Unit, isPro: Boolean, onSeePro: () -> Unit) {
    val colors = LocalAslColors.current
    val letters = snapshot.letters.values.sortedBy { it.letter.name }
    val attempts = letters.sumOf { it.attempts }
    val matches = letters.sumOf { it.matches }
    val practised = letters.count { it.attempts > 0 }

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        StatTile(snapshot.currentStreakDays.toString(), stringResource(R.string.progress_stat_streak), colors.accent,
            Modifier.weight(1f))
        StatTile("$practised/${Letter.staticLetters.size}", stringResource(R.string.progress_stat_letters), colors.label,
            Modifier.weight(1f))
        StatTile(matches.toString(), stringResource(R.string.progress_stat_matched), colors.label, Modifier.weight(1f))
    }

    InsightsGroup(remember(snapshot) { insightsFor(snapshot, System.currentTimeMillis()) }, isPro, onSeePro)

    Group(stringResource(R.string.progress_summary)) {
        SettingsValueRow(stringResource(R.string.progress_stat_best),
            pluralStringResource(R.plurals.settings_streak_days, snapshot.longestStreakDays, snapshot.longestStreakDays))
        SettingsDivider()
        SettingsValueRow(stringResource(R.string.settings_attempts),
            pluralStringResource(R.plurals.settings_attempts_count, attempts, attempts, matches))
        if (snapshot.completedStoryStepIds.isNotEmpty()) {
            val steps = snapshot.completedStoryStepIds.size
            SettingsDivider()
            SettingsValueRow(stringResource(R.string.home_pro_packs),
                pluralStringResource(R.plurals.progress_story_steps, steps, steps))
        }
    }

    if (letters.isNotEmpty()) Group(stringResource(R.string.progress_by_letter)) {
        letters.forEachIndexed { index, item ->
            if (index > 0) SettingsDivider()
            DetailRow(item.letter.display,
                pluralStringResource(R.plurals.progress_letter_count, item.attempts, item.matches, item.attempts))
        }
    }

    if (snapshot.speedRuns.isNotEmpty()) Group(stringResource(R.string.progress_speed_runs)) {
        snapshot.speedRuns.take(5).forEachIndexed { index, run ->
            if (index > 0) SettingsDivider()
            DetailRow((index + 1).toString(),
                pluralStringResource(R.plurals.progress_speed_result, run.durationSeconds, run.durationSeconds, run.correct))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroup { SettingsActionRow(stringResource(R.string.progress_manage_data), onClick = onSettings) }
        SettingsGroupFooter(stringResource(R.string.alphabet_progress_note))
    }
}

/**
 * Progress insights (Pro). Free users see the same rows as a locked preview: the questions, a neutral grey lock in
 * place of each answer, and one "See what Pro adds" row. Nothing opens by itself.
 */
@Composable
private fun InsightsGroup(insights: Insights, isPro: Boolean, onSeePro: () -> Unit) {
    val colors = LocalAslColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.insights_title))
        SettingsGroup {
            Column {
                val suggestion = when (insights.suggestion.size) {
                    0 -> stringResource(R.string.insights_suggestion_none)
                    1 -> stringResource(R.string.insights_suggestion_one, insights.suggestion[0].display)
                    else -> stringResource(R.string.insights_suggestion_two, insights.suggestion[0].display, insights.suggestion[1].display)
                }
                InsightRow(stringResource(R.string.insights_suggestion_title), suggestion, isPro)
                SettingsDivider()
                InsightRow(stringResource(R.string.insights_weakest),
                    if (insights.weakest.isEmpty()) stringResource(R.string.insights_weakest_none)
                    else insights.weakest.joinToString(" · ") { rate ->
                        "${rate.letter.display} ${(rate.rate * 100).toInt()}%"
                    }, isPro)
                SettingsDivider()
                InsightRow(stringResource(R.string.insights_this_week),
                    pluralStringResource(R.plurals.insights_letters, insights.practisedThisWeek, insights.practisedThisWeek), isPro)
                SettingsDivider()
                InsightRow(stringResource(R.string.insights_average),
                    insights.averageMatchMs?.let { stringResource(R.string.insights_seconds, it / 1000.0) }
                        ?: stringResource(R.string.insights_not_enough), isPro)
                SettingsDivider()
                InsightRow(stringResource(R.string.insights_best_speed),
                    insights.bestSpeedScore?.let { pluralStringResource(R.plurals.insights_matches, it, it) }
                        ?: stringResource(R.string.speed_no_best), isPro)
                if (!isPro) {
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onSeePro)
                }
            }
        }
        SettingsGroupFooter(stringResource(if (isPro) R.string.insights_footer else R.string.insights_footer_locked))
    }
}

@Composable
private fun InsightRow(title: String, value: String, unlocked: Boolean) {
    val colors = LocalAslColors.current
    Column(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget).semantics(mergeDescendants = true) {}
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary)
        if (unlocked) Text(value, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        else ProLockLabel(colors.onSurfaceSecondary)
    }
}

@Composable
private fun Group(title: String, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(title)
        SettingsGroup { Column { rows() } }
    }
}

@Composable
private fun StatTile(value: String, caption: String, valueColor: Color, modifier: Modifier) {
    AslCard(modifier.fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "$value $caption" }) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = LocalAslColors.current.onSurfaceSecondary)
        }
    }
}

/** A large leading label (a letter, a round number) with its detail beside it. */
@Composable
private fun DetailRow(lead: String, detail: String) {
    val description = stringResource(R.string.progress_letter_row, lead, detail)
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(lead, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.sizeIn(minWidth = Spacing.xxl))
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = LocalAslColors.current.onSurfaceSecondary)
    }
}
