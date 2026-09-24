package dev.handspell.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handspell.app.R
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.progress.ProgressStore
import androidx.compose.ui.text.font.FontWeight
import dev.handspell.app.ui.components.ScreenHeader
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
    onOpenCapture: (() -> Unit)? = null,
) {
    val model: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(
        preferences, progressStore, clearAlphabetData, entitlementGate, classifierModelId, buildInfo,
    ))
    val state by model.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state, onBack, model::askClearProgress, model::dismissDialog,
        model::confirmClearProgress, model::openPaywall, model::restorePurchases,
        model::retryEntitlement, onOpenCapture,
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
    onOpenCapture: (() -> Unit)? = null,
) {
    val colors = LocalAslColors.current
    Column(Modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        ScreenHeader(stringResource(R.string.settings), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            PracticeGroup(state, onAskClear)
            SubscriptionGroup(state, onOpenPaywall, onRestore, onRetryEntitlement)
            PrivacyGroup(state.classifierModelId)
            AboutGroup(state)
            if (onOpenCapture != null) DebugGroup(onOpenCapture)
        }
    }
    if (state.confirmClearProgress) {
        AlertDialog(
            onDismissRequest = onDismissClear,
            shape = RoundedCornerShape(AslShapes.extraLarge),
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurface,
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = onConfirmClear) {
                    Text(stringResource(R.string.settings_delete_confirm), color = colors.onSurface, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClear) { Text(stringResource(R.string.settings_cancel), color = colors.onSurface) }
            },
        )
    }
}

@Composable
private fun PracticeGroup(state: SettingsUiState, onAskClear: () -> Unit) {
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
                            else stringResource(R.string.settings_streak_days, progress.currentStreakDays),
                        )
                        SettingsDivider()
                        SettingsValueRow(stringResource(R.string.settings_letters_practised), stringResource(
                            R.string.settings_letters_count, progress.lettersPractised, progress.lettersTotal,
                        ))
                        SettingsDivider()
                        SettingsValueRow(stringResource(R.string.settings_attempts), stringResource(
                            R.string.settings_attempts_count, progress.attempts, progress.matches,
                        ))
                    }
                }
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
private fun SubscriptionGroup(state: SettingsUiState, onPaywall: () -> Unit, onRestore: () -> Unit, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_pro))
        SettingsGroup {
            Column {
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
