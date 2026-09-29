package dev.handspell.app.ui.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun questTitle(quest: Quest): String = when (quest) {
    Quest.LETTERS -> pluralStringResource(R.plurals.quest_letters, quest.target, quest.target)
    Quest.WORDS -> pluralStringResource(R.plurals.quest_words, quest.target, quest.target)
    Quest.SPEED_ROUND -> stringResource(R.string.quest_speed)
}

/** The main menu's quest card: the goal, "1 of 3", a plain bar, and a row that takes you to where it is done. */
@Composable
fun QuestCard(progress: QuestProgress, onGo: () -> Unit) {
    val colors = LocalAslColors.current
    val title = questTitle(progress.quest)
    val status = if (progress.complete) stringResource(R.string.quest_done)
    else stringResource(R.string.quest_progress, progress.done, progress.quest.target)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.quest_header))
        SettingsGroup {
            Column {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.md)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$title. $status"
                            progressBarRangeInfo = ProgressBarRangeInfo(progress.fraction, 0f..1f)
                        },
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                        Text(status, style = MaterialTheme.typography.labelMedium,
                            color = if (progress.complete) colors.accent else colors.onSurfaceSecondary)
                    }
                    Box(Modifier.fillMaxWidth().height(Spacing.xs).background(colors.separator, RoundedCornerShape(Spacing.xxs))) {
                        Box(Modifier.fillMaxWidth(progress.fraction).height(Spacing.xs)
                            .background(colors.accent, RoundedCornerShape(Spacing.xxs)))
                    }
                }
                if (!progress.complete) {
                    SettingsDivider()
                    SettingsActionRow(stringResource(when (progress.quest) {
                        Quest.LETTERS -> R.string.quest_go_letters
                        Quest.WORDS -> R.string.quest_go_words
                        Quest.SPEED_ROUND -> R.string.quest_go_speed
                    }), onClick = onGo)
                }
            }
        }
    }
}
