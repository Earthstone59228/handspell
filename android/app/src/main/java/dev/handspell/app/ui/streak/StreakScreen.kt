package dev.handspell.app.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.R
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.StreakMark
import dev.handspell.app.ui.settings.FrostedSettingsHero
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupFooter
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun StreakRoute(progressStore: ProgressStore, onBack: () -> Unit, belowMilestones: @Composable () -> Unit = {}) {
    val snapshot by progressStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val state = remember(snapshot) { streakState(snapshot, System.currentTimeMillis()) }
    StreakScreen(state, onBack, belowMilestones)
}

/** Current and longest streak, this month's practised days and the milestone markers. Nothing here moves. */
@Composable
fun StreakScreen(state: StreakState, onBack: () -> Unit, belowMilestones: @Composable () -> Unit = {}) {
    val body = when {
        state.loading -> null
        state.current == 0 -> stringResource(R.string.streak_body_none)
        state.practisedToday -> stringResource(R.string.streak_body_today)
        else -> stringResource(R.string.streak_body_pending)
    }
    FrostedSettingsHero(onBack = onBack, title = stringResource(R.string.streak_title), body = body) {
        if (state.loading) {
            Text(stringResource(R.string.settings_loading), style = MaterialTheme.typography.bodyLarge)
            return@FrostedSettingsHero
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StreakTile(state.current, stringResource(R.string.streak_current), active = state.current > 0 && state.practisedToday,
                showMark = true, modifier = Modifier.weight(1f))
            StreakTile(state.longest, stringResource(R.string.streak_longest), active = false, showMark = false,
                modifier = Modifier.weight(1f))
        }
        state.calendar?.let { MonthGroup(it) }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SettingsGroupHeader(stringResource(R.string.streak_milestones))
            SettingsGroup {
                Column {
                    state.milestones.forEachIndexed { index, row ->
                        if (index > 0) SettingsDivider()
                        MilestoneRowView(row)
                    }
                }
            }
            SettingsGroupFooter(stringResource(R.string.streak_footer))
        }
        belowMilestones()
    }
}

@Composable
private fun StreakTile(value: Int, caption: String, active: Boolean, showMark: Boolean, modifier: Modifier) {
    val colors = LocalAslColors.current
    val days = pluralStringResource(R.plurals.settings_streak_days, value, value)
    AslCard(modifier.fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "$caption: $days" }) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (showMark) StreakMark(active = active)
                Text(value.toString(), style = MaterialTheme.typography.headlineMedium,
                    color = if (showMark) colors.accent else colors.label, maxLines = 1)
            }
            Text(caption, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceSecondary)
        }
    }
}

@Composable
private fun MonthGroup(calendar: MonthCalendar) {
    val colors = LocalAslColors.current
    val locale: Locale = LocalConfiguration.current.locales[0]
    val title = remember(calendar.year, calendar.month, locale) {
        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(calendar.year, calendar.month, 1) }
        SimpleDateFormat("LLLL yyyy", locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date.time)
    }
    val weekdays = remember(locale) {
        val names = DateFormatSymbols(locale).shortWeekdays
        // Monday-first: MONDAY(2)..SATURDAY(7), then SUNDAY(1).
        (listOf(2, 3, 4, 5, 6, 7, 1)).map { names[it].take(1).uppercase(locale) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(title)
        SettingsGroup {
            Column(Modifier.padding(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
                    weekdays.forEach { day ->
                        Text(day, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceSecondary,
                            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                calendar.weeks.forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { cell -> DayCell(cell, title, Modifier.weight(1f)) }
                    }
                }
            }
        }
        SettingsGroupFooter(pluralStringResource(R.plurals.streak_month_count, calendar.practisedCount, calendar.practisedCount))
    }
}

@Composable
private fun DayCell(cell: CalendarCell, monthTitle: String, modifier: Modifier) {
    val colors = LocalAslColors.current
    val day = cell.dayOfMonth
    Box(modifier.aspectRatio(1f).padding(Spacing.xxs), contentAlignment = Alignment.Center) {
        if (day == null) return@Box
        val description = stringResource(
            if (cell.practised) R.string.streak_day_practised else R.string.streak_day_open, day, monthTitle,
        )
        val shape = Modifier.fillMaxWidth().aspectRatio(1f).sizeIn(maxWidth = Spacing.touchTarget)
            .semantics { contentDescription = description }
        Box(
            when {
                cell.practised -> shape.background(colors.accent, CircleShape)
                cell.isToday -> shape.border(Spacing.stroke, colors.accent, CircleShape)
                else -> shape
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.toString(), style = MaterialTheme.typography.labelMedium,
                color = when {
                    cell.practised -> colors.onAccent
                    cell.isFuture -> colors.labelTertiary
                    else -> colors.onSurface
                },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

@Composable
private fun MilestoneRowView(row: MilestoneRow) {
    val colors = LocalAslColors.current
    val copy = stringResource(when (row.days) {
        3 -> R.string.streak_milestone_3
        7 -> R.string.streak_milestone_7
        14 -> R.string.streak_milestone_14
        else -> R.string.streak_milestone_30
    })
    val status = when (row.status) {
        MilestoneStatus.REACHED -> stringResource(R.string.streak_milestone_reached)
        MilestoneStatus.NEXT -> pluralStringResource(R.plurals.streak_days_to_go, row.daysToGo, row.daysToGo)
        MilestoneStatus.AHEAD -> null
    }
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget).semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        StreakMark(active = row.status == MilestoneStatus.REACHED, size = Spacing.xl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(pluralStringResource(R.plurals.streak_milestone_days, row.days, row.days),
                style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(copy, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary)
        }
        if (status != null) Text(status, style = MaterialTheme.typography.labelMedium,
            color = if (row.status == MilestoneStatus.REACHED) colors.accent else colors.onSurfaceSecondary)
    }
}
