package dev.handspell.app.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.ui.components.StreakMark
import dev.handspell.app.ui.settings.FrostedSettingsHero
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Callbacks of the main menu, grouped so the screen signature stays readable. */
data class MainMenuActions(
    val onLetters: () -> Unit,
    val onWords: () -> Unit,
    val onProgress: () -> Unit,
    val onSettings: () -> Unit,
)

/**
 * The app's first screen after the introduction: the alphabet menu's header (large title, icons on the right) over
 * two large entries in the alphabet's off-white card style, then the streak and the quieter rows.
 */
@Composable
fun MainMenuScreen(
    state: MainMenuState,
    actions: MainMenuActions,
    headerActions: @Composable () -> Unit = {},
    belowEntries: @Composable () -> Unit = {},
) {
    val colors = LocalAslColors.current
    FrostedSettingsHero(
        onBack = null,
        title = stringResource(R.string.app_name),
        body = stringResource(R.string.menu_hero_body),
        topSpace = Spacing.xs,
        actions = {
            headerActions()
            HeaderIcon(R.drawable.ic_settings, stringResource(R.string.menu_open_settings), actions.onSettings)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MenuEntryCard(
                title = stringResource(R.string.menu_letters),
                meta = if (state.loading) null
                else stringResource(R.string.menu_letters_meta, state.lettersComplete, state.lettersTotal),
                onClick = actions.onLetters,
            )
            MenuEntryCard(
                title = stringResource(R.string.menu_words),
                meta = if (state.loading || state.wordsTotal == 0) null
                else stringResource(R.string.menu_words_meta, state.wordsComplete, state.wordsTotal),
                onClick = actions.onWords,
            )
        }
        ProgressEntry(state, actions.onProgress)
        belowEntries()
    }
}

/** 48dp header icon, drawn in the label colour like the alphabet menu's gear and paper. */
@Composable
internal fun HeaderIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Spacing.touchTarget).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LocalAslColors.current.label,
            modifier = Modifier.size(Spacing.xl + Spacing.xxs))
    }
}

/** A large entry in the alphabet's letter-card style: off-white card, dark title, small meta line, blue chevron. */
@Composable
private fun MenuEntryCard(title: String, meta: String?, onClick: () -> Unit) {
    val colors = LocalAslColors.current
    Surface(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.referenceGuide + Spacing.sm)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(AslShapes.large),
        color = colors.card,
        contentColor = colors.onCard,
    ) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.onCard)
                if (meta != null) Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onCardSecondary)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = colors.accent,
                modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

@Composable
private fun ProgressEntry(state: MainMenuState, onClick: () -> Unit) {
    // The one way into Progress, carrying the live streak as its subtitle.
    val streak = if (state.streakDays > 0) pluralStringResource(R.plurals.menu_streak_days, state.streakDays, state.streakDays)
    else stringResource(R.string.menu_streak_none)
    val body = when {
        state.loading -> stringResource(R.string.settings_loading)
        state.streakDays == 0 -> stringResource(R.string.menu_progress_start, streak)
        state.practisedToday -> stringResource(R.string.menu_progress_today_done, streak)
        else -> stringResource(R.string.menu_progress_today_pending, streak)
    }
    MenuRowCard(stringResource(R.string.progress_title), body, onClick) {
        StreakMark(active = state.streakDays > 0 && state.practisedToday)
    }
}

/** A grey row card (the Settings group style) with a leading mark, a title, one line of detail and a blue chevron. */
@Composable
fun MenuRowCard(title: String, body: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val colors = LocalAslColors.current
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget).clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = colors.accent, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}

/** Quick light/dark switch on the header: a circle, half filled. Writes the same Theme setting as Settings. */
@Composable
fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
    val colors = LocalAslColors.current
    val description = stringResource(if (dark) R.string.menu_theme_to_light else R.string.menu_theme_to_dark)
    Box(
        Modifier.size(Spacing.touchTarget).clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(Spacing.xl)) {
            val stroke = size.width * 0.075f
            val radius = size.width / 2 - stroke
            drawCircle(colors.label, radius, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            drawArc(colors.label, startAngle = 90f, sweepAngle = 180f, useCenter = true,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
        }
    }
}
