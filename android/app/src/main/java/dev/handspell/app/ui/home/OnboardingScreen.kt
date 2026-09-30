package dev.handspell.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.R
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.settings.FrostedSettingsHero
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.settings.SettingsInfoRow
import dev.handspell.app.ui.settings.SettingsToggleRow
import dev.handspell.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The two writes the first-run screen makes; kept out of the composable so they can be tested. */
class OnboardingActions(private val progressStore: ProgressStore, private val preferences: AppPreferencesStore) {
    suspend fun setLeftHanded(enabled: Boolean) = preferences.setLeftHanded(enabled)
    suspend fun finish() = progressStore.setOnboardingCompleted(true)
}

@Composable
fun OnboardingRoute(progressStore: ProgressStore, preferences: AppPreferencesStore, onFinished: () -> Unit) {
    val actions = remember(progressStore, preferences) { OnboardingActions(progressStore, preferences) }
    val scope = rememberCoroutineScope()
    val leftHanded by preferences.leftHanded.collectAsStateWithLifecycle(initialValue = false)
    OnboardingScreen(
        leftHanded = leftHanded,
        onLeftHanded = { scope.launch { actions.setLeftHanded(it) } },
        onContinue = {
            onFinished()
            scope.launch { actions.finish() }
        },
    )
}

/** First run, on the same frosted hero and grouped cards as Settings and Progress. */
@Composable
fun OnboardingScreen(leftHanded: Boolean, onLeftHanded: (Boolean) -> Unit, onContinue: () -> Unit) {
    FrostedSettingsHero(
        onBack = null,
        title = stringResource(R.string.onboarding_title),
        body = stringResource(R.string.onboarding_hero_body),
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SettingsGroupHeader(stringResource(R.string.onboarding_before_you_start))
            SettingsGroup {
                Column {
                    SettingsInfoRow(stringResource(R.string.onboarding_practice_title), stringResource(R.string.onboarding_practice_body))
                    SettingsDivider()
                    SettingsInfoRow(stringResource(R.string.onboarding_hold_title), stringResource(R.string.onboarding_hold_body))
                    SettingsDivider()
                    SettingsInfoRow(stringResource(R.string.onboarding_camera_title), stringResource(R.string.camera_permission_body))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SettingsGroupHeader(stringResource(R.string.onboarding_layout))
            SettingsGroup {
                SettingsToggleRow(
                    stringResource(R.string.onboarding_left_hand),
                    stringResource(R.string.settings_left_handed_layout_body),
                    leftHanded, onLeftHanded,
                )
            }
        }
        AslButton(stringResource(R.string.onboarding_continue), onContinue, Modifier.fillMaxWidth())
    }
}
