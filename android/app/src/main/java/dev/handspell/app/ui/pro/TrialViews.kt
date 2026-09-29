package dev.handspell.app.ui.pro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R
import dev.handspell.app.billing.DemoTrialState
import dev.handspell.app.billing.FreeTrialTerms
import dev.handspell.app.billing.TrialUnit
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.settings.SettingsInfoRow
import dev.handspell.app.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

/** "7 days", "1 month": the length of a store trial in words. */
@Composable
fun trialLength(terms: FreeTrialTerms): String = when (terms.unit) {
    TrialUnit.DAY -> pluralStringResource(R.plurals.trial_days, terms.length, terms.length)
    TrialUnit.WEEK -> pluralStringResource(R.plurals.trial_weeks, terms.length, terms.length)
    TrialUnit.MONTH -> pluralStringResource(R.plurals.trial_months, terms.length, terms.length)
    TrialUnit.YEAR -> pluralStringResource(R.plurals.trial_years, terms.length, terms.length)
}

@Composable
fun formatDateTime(timeMs: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(timeMs, locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(timeMs)) }
}

/** The title that labels an active demo trial wherever Pro status shows (menu card, Settings). */
@Composable
fun demoTrialTitle(trial: DemoTrialState): String? =
    (trial as? DemoTrialState.Active)?.let { stringResource(R.string.demo_trial_active_title, formatDateTime(it.endsAtMs)) }

/** The paywall and Settings group for the demo trial: offer (tap to start), active until, or ended. */
@Composable
fun DemoTrialGroup(trial: DemoTrialState, isRealPro: Boolean, onStart: () -> Unit) {
    if (isRealPro && trial !is DemoTrialState.Active) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.demo_trial_header))
        SettingsGroup {
            Column {
                when (trial) {
                    DemoTrialState.NotStarted -> {
                        SettingsInfoRow(stringResource(R.string.demo_trial_offer_title), stringResource(R.string.demo_trial_offer_body))
                        SettingsDivider()
                        SettingsActionRow(stringResource(R.string.demo_trial_start), onClick = onStart)
                    }
                    is DemoTrialState.Active -> SettingsInfoRow(demoTrialTitle(trial).orEmpty(), stringResource(R.string.demo_trial_active_body))
                    is DemoTrialState.Ended -> SettingsInfoRow(stringResource(R.string.demo_trial_ended_title), stringResource(R.string.demo_trial_ended_body))
                }
            }
        }
    }
}
