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
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.settings.SettingsInfoRow
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/**
 * The main menu's quiet Pro entry: a grey card like the Settings rows (never the accent fill), saying plainly what Pro
 * adds. A tap on the row opens the paywall; nothing opens by itself. With Pro active it becomes the way to the packs.
 */
@Composable
fun ProMenuCard(isPro: Boolean, trialLabel: String?, onSeePro: () -> Unit, onOpenPacks: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_pro))
        SettingsGroup {
            Column {
                SettingsInfoRow(
                    if (isPro) trialLabel ?: stringResource(R.string.menu_pro_title_active) else stringResource(R.string.menu_pro_title),
                    stringResource(if (isPro) R.string.menu_pro_body_active else R.string.menu_pro_body_free),
                )
                SettingsDivider()
                if (isPro) SettingsActionRow(stringResource(R.string.settings_open_packs), onClick = onOpenPacks)
                else SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onSeePro)
            }
        }
    }
}

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
