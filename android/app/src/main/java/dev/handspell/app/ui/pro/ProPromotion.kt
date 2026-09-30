package dev.handspell.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.handspell.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.settings.SettingsInfoRow
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/**
 * The main menu's Pro card. Free users see what Pro adds in plain words, a "See what's in Pro" button (opens the
 * paywall on tap only), the 3-day demo trial while it is unused, and "Not now", which shrinks the card to one quiet
 * row for the rest of the session. With Pro active it just says so.
 */
@Composable
fun ProMenuCard(
    isPro: Boolean,
    trialLabel: String?,
    proWordCount: Int,
    demoAvailable: Boolean,
    dismissed: Boolean,
    onSeePro: () -> Unit,
    onStartDemo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAslColors.current
    if (isPro || dismissed) Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_pro))
        SettingsGroup {
            Column {
                if (isPro) {
                    SettingsInfoRow(trialLabel ?: stringResource(R.string.menu_pro_title_active), stringResource(R.string.menu_pro_body_active))
                } else SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onSeePro)
            }
        }
        return
    }
    AslCard(contentInset = Spacing.lg) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.settings_pro).uppercase(), style = MaterialTheme.typography.labelSmall,
                color = colors.accent)
            Text(stringResource(R.string.pro_card_title), style = MaterialTheme.typography.headlineSmall, color = colors.onSurface,
                modifier = Modifier.semantics { heading() })
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (proWordCount > 0) ProPoint(pluralStringResource(R.plurals.pro_point_words, proWordCount, proWordCount))
                ProPoint(stringResource(R.string.pro_point_speed))
                ProPoint(stringResource(R.string.pro_point_packs))
                ProPoint(stringResource(R.string.pro_point_insights))
            }
            AslButton(stringResource(R.string.pro_card_see), onSeePro, Modifier.fillMaxWidth())
            if (demoAvailable) {
                AslButton(stringResource(R.string.pro_card_demo), onStartDemo, Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
                Text(stringResource(R.string.pro_card_demo_note), style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceSecondary)
            }
            AslButton(stringResource(R.string.pro_mention_not_now), onDismiss,
                Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
        }
    }
}

@Composable
private fun ProPoint(text: String) {
    val colors = LocalAslColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
        Text("✓", style = MaterialTheme.typography.bodyMedium, color = colors.accent, modifier = Modifier.clearAndSetSemantics {})
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

/** Bottom of the Words menu for free users: how many words Pro adds, a few locked examples, one tap to the paywall. */
@Composable
fun WordsProStrip(proWordCount: Int, examples: List<String>, onSeePro: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalAslColors.current
    SettingsGroup {
        Column {
            Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(pluralStringResource(R.plurals.words_pro_strip_title, proWordCount, proWordCount),
                    style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    examples.forEach { word ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                            dev.handspell.app.ui.components.ProLockIcon(colors.onSurfaceSecondary)
                            Text(word, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary, maxLines = 1)
                        }
                    }
                }
            }
            SettingsDivider()
            SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onSeePro)
            SettingsDivider()
            SettingsActionRow(stringResource(R.string.pro_mention_not_now), onClick = onDismiss)
        }
    }
}

/** The reward sheet's Pro line: free users only, at most once a day, never again this session after "Not now". */
fun shouldShowRewardProLine(isPro: Boolean, dismissedThisSession: Boolean, lastShownDay: Long?, today: Long): Boolean =
    !isPro && !dismissedThisSession && lastShownDay != today

/** One row of the free-versus-Pro comparison. Values are plain words; nothing is shown in red or crossed out. */
data class ComparisonRow(val feature: Int, val free: Int, val pro: Int)

val PRO_COMPARISON = listOf(
    ComparisonRow(R.string.compare_letters, R.string.compare_included, R.string.compare_included),
    ComparisonRow(R.string.compare_words, R.string.compare_included, R.string.compare_included),
    ComparisonRow(R.string.compare_speed, R.string.compare_speed_free, R.string.compare_speed_pro),
    ComparisonRow(R.string.compare_stories, R.string.compare_not_included, R.string.compare_included),
    ComparisonRow(R.string.compare_speed_packs, R.string.compare_not_included, R.string.compare_included),
    ComparisonRow(R.string.compare_insights, R.string.compare_preview, R.string.compare_included),
    ComparisonRow(R.string.compare_more_words, R.string.compare_not_included, R.string.compare_included),
)

/** "What you get": free and Pro side by side, the free column first so nobody has to look for it. */
@Composable
fun ProComparison(rows: List<ComparisonRow> = PRO_COMPARISON) {
    val colors = LocalAslColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.compare_title))
        SettingsGroup {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                    Text("", Modifier.weight(1f))
                    Text(stringResource(R.string.compare_free), style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(COLUMN))
                    Text(stringResource(R.string.home_pro_label), style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(COLUMN))
                }
                rows.forEach { row ->
                    SettingsDivider()
                    val feature = stringResource(row.feature)
                    val free = stringResource(row.free)
                    val pro = stringResource(row.pro)
                    val description = stringResource(R.string.compare_row_description, feature,
                        stringResource(spoken(row.free)), stringResource(spoken(row.pro)))
                    Row(
                        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
                            .semantics(mergeDescendants = true) { contentDescription = description }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(feature, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                        Text(free, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceSecondary,
                            textAlign = TextAlign.Center, modifier = Modifier.width(COLUMN))
                        Text(pro, style = MaterialTheme.typography.bodySmall, color = colors.onSurface,
                            textAlign = TextAlign.Center, modifier = Modifier.width(COLUMN))
                    }
                }
            }
        }
    }
}

private val COLUMN = Spacing.huge + Spacing.xxl

private fun spoken(res: Int): Int = if (res == R.string.compare_not_included) R.string.compare_not_included_spoken else res

/**
 * A soft, one-line mention of Pro after a streak milestone or once the free daily speed round is used. It opens the
 * paywall only when tapped, and "Not now" hides every mention for the rest of this session.
 */
@Composable
fun ProMention(text: String, onSeePro: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAslColors.current
    SettingsGroup {
        Column(modifier) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary,
                modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md))
            SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onSeePro)
            SettingsDivider()
            SettingsActionRow(stringResource(R.string.pro_mention_not_now), onClick = onDismiss)
        }
    }
}

/** A mention shows only for a free user, only at its trigger, and never again this session after "Not now". */
fun shouldMentionPro(triggered: Boolean, isPro: Boolean, dismissedThisSession: Boolean): Boolean =
    triggered && !isPro && !dismissedThisSession
