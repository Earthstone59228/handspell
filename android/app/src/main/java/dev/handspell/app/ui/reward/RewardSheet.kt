package dev.handspell.app.ui.reward

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import dev.handspell.app.R
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslSheet
import dev.handspell.app.ui.components.StreakMark
import dev.handspell.app.ui.theme.AslMotion
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.AslText
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import dev.handspell.app.ui.theme.Spacing

/**
 * A brief reward after a letter or word is completed (a camera match or "Mark complete"; never a skip): what was done,
 * today's count and the streak, then one Continue. One tap anywhere closes it, so it never holds up the next action.
 * No sound; with reduce motion on nothing moves.
 */
@Composable
fun RewardSheet(reward: Reward?, onDismiss: () -> Unit, proMention: (@Composable (Reward) -> Unit)? = null) {
    // Keep the last reward while the sheet animates out.
    var shown by remember { mutableStateOf(reward) }
    if (reward != null) shown = reward
    AslSheet(visible = reward != null, onDismiss = onDismiss) {
        shown?.let { RewardContent(it, onDismiss, proMention) }
    }
}

@Composable
private fun RewardContent(reward: Reward, onContinue: () -> Unit, proMention: (@Composable (Reward) -> Unit)?) {
    val colors = LocalAslColors.current
    val reduceMotion = LocalReduceMotion.current
    val scale = remember(reward.subject) { Animatable(if (reduceMotion) 1f else 0.92f) }
    LaunchedEffect(reward.subject) {
        if (!reduceMotion) scale.animateTo(1f, tween(AslMotion.standardMillis))
    }
    Surface(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.referenceGuide * 1.5f)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        shape = RoundedCornerShape(dev.handspell.app.ui.theme.LocalInsetCornerRadius.current ?: AslShapes.extraLarge),
        color = colors.card,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val letter = reward.subject.kind == RewardSubject.Kind.LETTER
            Text(reward.subject.display, style = if (letter) AslText.hero else MaterialTheme.typography.displaySmall,
                color = colors.accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(Spacing.xl))
        }
    }
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(if (reward.subject.kind == RewardSubject.Kind.QUEST) stringResource(R.string.quest_complete_title)
            else stringResource(R.string.reward_title, reward.subject.display), style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(pluralStringResource(R.plurals.reward_today, reward.completedToday, reward.completedToday),
            style = MaterialTheme.typography.bodyLarge, color = colors.labelSecondary, textAlign = TextAlign.Center)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            StreakMark(active = true, size = Spacing.xl)
            Text(pluralStringResource(R.plurals.menu_streak_days, reward.streakDays, reward.streakDays),
                style = MaterialTheme.typography.titleMedium, color = colors.label)
        }
        val next = reward.nextMilestone
        val milestoneLine = when {
            reward.milestoneReached -> stringResource(R.string.reward_milestone, reward.streakDays)
            next != null -> pluralStringResource(R.plurals.reward_next_milestone, next - reward.streakDays,
                next - reward.streakDays, next)
            else -> stringResource(R.string.reward_past_milestones)
        }
        Text(milestoneLine, style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary, textAlign = TextAlign.Center)
    }
    proMention?.invoke(reward)
    AslButton(stringResource(R.string.continue_letter), onContinue, Modifier.fillMaxWidth())
}
