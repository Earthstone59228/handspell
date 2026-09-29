package dev.handspell.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handspell.app.R
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Owns real preferences and local data; there is no telemetry toggle because there is no telemetry. */
@Composable
fun SettingsRoute(
    preferences: AppPreferencesStore,
    progressStore: ProgressStore,
    clearAlphabetData: suspend () -> Unit,
    entitlementGate: EntitlementGate,
    classifierModelId: String?,
    buildInfo: BuildInfo,
    onBack: () -> Unit,
    onOpenPacks: () -> Unit = {},
    onOpenCapture: (() -> Unit)? = null,
) {
    val model: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(
        preferences, progressStore, clearAlphabetData, entitlementGate, classifierModelId, buildInfo,
    ))
    val state by model.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state, onBack, model::askClearProgress, model::dismissDialog,
        model::confirmClearProgress, model::openPaywall, model::restorePurchases,
        model::retryEntitlement, model::setLeftHanded, onOpenCapture, onOpenPacks,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onAskClear: () -> Unit,
    onDismissClear: () -> Unit,
    onConfirmClear: () -> Unit,
    onOpenPaywall: () -> Unit,
    onRestore: () -> Unit,
    onRetryEntitlement: () -> Unit,
    onSetLeftHanded: (Boolean) -> Unit,
    onOpenCapture: (() -> Unit)? = null,
    onOpenPacks: () -> Unit = {},
) {
    val colors = LocalAslColors.current
    FrostedSettingsHero(onBack) {
        PracticeGroup(state, onAskClear, onSetLeftHanded)
        SubscriptionGroup(state, onOpenPaywall, onRestore, onRetryEntitlement, onOpenPacks)
        PrivacyGroup(state.classifierModelId)
        AboutGroup(state)
        if (onOpenCapture != null) DebugGroup(onOpenCapture)
    }
    if (state.confirmClearProgress) {
        Dialog(onDismissRequest = onDismissClear) {
            Surface(
                shape = RoundedCornerShape(AslShapes.extraLarge),
                color = colors.surface,
                contentColor = colors.onSurface,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Text(
                        stringResource(R.string.settings_delete_confirm_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.settings_delete_confirm_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceSecondary,
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(top = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        AslButton(stringResource(R.string.settings_cancel), onDismissClear, Modifier.fillMaxWidth())
                        AslButton(
                            stringResource(R.string.settings_delete_data), onConfirmClear,
                            Modifier.fillMaxWidth().border(
                                Spacing.hairline, colors.separator, RoundedCornerShape(AslShapes.button),
                            ),
                            style = AslButtonStyle.Secondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeGroup(state: SettingsUiState, onAskClear: () -> Unit, onSetLeftHanded: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_progress))
        SettingsGroup {
            Column {
                when (val progress = state.progress) {
                    ProgressSummary.Loading -> SettingsValueRow(stringResource(R.string.settings_progress), stringResource(R.string.settings_loading))
                    ProgressSummary.Empty -> SettingsInfoRow(stringResource(R.string.settings_no_progress), stringResource(R.string.settings_no_progress_body))
                    is ProgressSummary.Recorded -> {
                        SettingsValueRow(
                            stringResource(R.string.settings_streak),
                            if (progress.currentStreakDays == 0) stringResource(R.string.settings_no_streak)
                            else pluralStringResource(R.plurals.settings_streak_days, progress.currentStreakDays, progress.currentStreakDays),
                        )
                        SettingsDivider()
                        SettingsValueRow(stringResource(R.string.settings_letters_practised), stringResource(
                            R.string.settings_letters_count, progress.lettersPractised, progress.lettersTotal,
                        ))
                        SettingsDivider()
                        SettingsValueRow(stringResource(R.string.settings_attempts), pluralStringResource(
                            R.plurals.settings_attempts_count, progress.attempts, progress.attempts, progress.matches,
                        ))
                    }
                }
                SettingsDivider()
                SettingsToggleRow(
                    stringResource(R.string.settings_left_handed_layout),
                    stringResource(R.string.settings_left_handed_layout_body),
                    state.leftHanded, onSetLeftHanded,
                )
                SettingsDivider()
                SettingsDestructiveRow(
                    stringResource(R.string.settings_delete_data),
                    enabled = state.deletion != DeletionUi.IN_PROGRESS,
                    onClick = onAskClear,
                )
            }
        }
        SettingsGroupFooter(stringResource(R.string.settings_progress_footer))
        when (state.deletion) {
            DeletionUi.DONE -> StatusMessage(stringResource(R.string.settings_deleted), LiveRegionMode.Assertive)
            DeletionUi.FAILED -> StatusMessage(stringResource(R.string.settings_delete_failed), LiveRegionMode.Assertive)
            else -> Unit
        }
    }
}

@Composable
private fun SubscriptionGroup(
    state: SettingsUiState, onPaywall: () -> Unit, onRestore: () -> Unit, onRetry: () -> Unit, onOpenPacks: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_pro))
        SettingsGroup {
            Column {
                SettingsInfoRow(stringResource(R.string.home_pro_packs), stringResource(R.string.pro_features_detail))
                SettingsDivider()
                // Interim home of the story and speed packs since the tab bar went; the owner may move it.
                SettingsActionRow(stringResource(R.string.settings_open_packs), onClick = onOpenPacks)
                SettingsDivider()
                val status = when (state.subscription) {
                    SubscriptionUi.CHECKING -> stringResource(R.string.settings_pro_checking)
                    SubscriptionUi.FREE -> stringResource(R.string.settings_pro_free)
                    SubscriptionUi.PRO -> stringResource(R.string.settings_pro_active)
                    SubscriptionUi.UNAVAILABLE -> stringResource(
                        if (state.billingConfigured) R.string.settings_pro_unavailable else R.string.settings_pro_not_configured)
                }
                SettingsValueRow(stringResource(R.string.settings_pro_status), status)
                if (state.subscription == SubscriptionUi.UNAVAILABLE && state.billingConfigured) {
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.retry), onClick = onRetry)
                }
                if (state.subscription == SubscriptionUi.FREE) {
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onPaywall)
                }
                if (state.billingConfigured) {
                    SettingsDivider()
                    SettingsActionRow(
                        stringResource(R.string.settings_restore),
                        enabled = state.restore != RestoreUi.IN_PROGRESS,
                        onClick = onRestore,
                    )
                }
            }
        }
        if (state.billingConfigured) {
            SettingsGroupFooter(stringResource(R.string.settings_test_store_footer))
            SettingsGroupFooter(stringResource(R.string.settings_cancel_subscription))
        }
        val restoreResult = when (state.restore) {
            RestoreUi.IDLE -> null
            RestoreUi.IN_PROGRESS -> stringResource(R.string.settings_restoring)
            RestoreUi.RESTORED -> stringResource(R.string.settings_restored)
            RestoreUi.NOTHING_TO_RESTORE -> stringResource(R.string.settings_nothing_to_restore)
            RestoreUi.FAILED -> stringResource(R.string.settings_restore_failed)
        }
        if (restoreResult != null) StatusMessage(restoreResult, LiveRegionMode.Polite)
    }
}

@Composable
private fun PrivacyGroup(modelId: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_privacy))
        SettingsGroup {
            Column {
                SettingsInfoRow(stringResource(R.string.settings_camera_title), stringResource(R.string.settings_camera_body))
                SettingsDivider()
                SettingsInfoRow(stringResource(R.string.settings_data_title), stringResource(R.string.settings_data_body))
                SettingsDivider()
                SettingsInfoRow(stringResource(R.string.settings_network_title), stringResource(R.string.settings_network_body))
                SettingsDivider()
                SettingsInfoRow(stringResource(R.string.settings_model_title), stringResource(
                    R.string.settings_model_body, modelId ?: stringResource(R.string.settings_model_not_loaded),
                ))
            }
        }
    }
}

@Composable
private fun AboutGroup(state: SettingsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_about))
        SettingsGroup {
            Column {
                SettingsValueRow(stringResource(R.string.settings_version), stringResource(
                    R.string.settings_version_value, state.versionName, state.versionCode,
                ))
            }
        }
    }
}

@Composable
private fun DebugGroup(onOpenCapture: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_debug_tools))
        SettingsGroup {
            Column {
                SettingsInfoRow(stringResource(R.string.debug_capture_title), stringResource(R.string.debug_capture_body))
                SettingsDivider()
                SettingsActionRow(stringResource(R.string.debug_capture_open), onClick = onOpenCapture)
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String, mode: LiveRegionMode) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalAslColors.current.label,
        modifier = Modifier.padding(horizontal = Spacing.xs).semantics { liveRegion = mode },
    )
}
