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
import dev.handspell.app.prefs.ThemeMode
import dev.handspell.app.billing.DemoTrialState
import dev.handspell.app.ui.pro.demoTrialTitle
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    onOpenDocuments: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val model: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(
        preferences, progressStore, clearAlphabetData, entitlementGate, classifierModelId, buildInfo,
    ))
    val state by model.uiState.collectAsStateWithLifecycle()
    val demoTrial by entitlementGate.demoTrial.collectAsStateWithLifecycle()
    val reminders = rememberReminderController(preferences)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SettingsScreen(
        state, onBack, model::askClearProgress, model::dismissDialog,
        model::confirmClearProgress, model::openPaywall, model::restorePurchases,
        model::retryEntitlement, model::setLeftHanded, onOpenCapture, onOpenPacks,
        onSetTheme = { model.setThemeMode(it) },
        demoTrial = demoTrial,
        onStartDemoTrial = { scope.launch { entitlementGate.startDemoTrial() } },
        reminders = reminders,
        onOpenDocuments = onOpenDocuments,
        onOpenAbout = onOpenAbout,
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
    onSetTheme: (ThemeMode) -> Unit = {},
    demoTrial: DemoTrialState = DemoTrialState.NotStarted,
    onStartDemoTrial: () -> Unit = {},
    reminders: ReminderController? = null,
    onOpenDocuments: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
) {
    val colors = LocalAslColors.current
    androidx.compose.foundation.layout.Box {
    // Only what a learner acts on: Pro, how it looks, the reminder, documents, deleting data. Technical details
    // (model, version, debug tools) sit behind the one quiet About row.
    FrostedSettingsHero(onBack) {
        SubscriptionGroup(state, onOpenPaywall, onRestore, onRetryEntitlement, onOpenPacks, demoTrial, onStartDemoTrial)
        AppearanceGroup(state.themeMode, onSetTheme, state.leftHanded, onSetLeftHanded)
        if (reminders != null) ReminderGroup(reminders)
        SettingsGroup { SettingsActionRow(stringResource(R.string.settings_documents), onClick = onOpenDocuments) }
        DeleteGroup(state, onAskClear)
        SettingsGroup { SettingsActionRow(stringResource(R.string.settings_about_row), onClick = onOpenAbout) }
    }
    if (reminders != null) ReminderRationale(reminders)
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

/** Theme: System, Light or Dark. The main menu's quick toggle writes the same setting. */
@Composable
private fun AppearanceGroup(mode: ThemeMode, onSetTheme: (ThemeMode) -> Unit, leftHanded: Boolean, onSetLeftHanded: (Boolean) -> Unit) {
    val colors = LocalAslColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_appearance))
        SettingsGroup {
            Column(Modifier.selectableGroup()) {
                listOf(
                    ThemeMode.SYSTEM to R.string.settings_theme_system,
                    ThemeMode.LIGHT to R.string.settings_theme_light,
                    ThemeMode.DARK to R.string.settings_theme_dark,
                ).forEachIndexed { index, (option, label) ->
                    if (index > 0) SettingsDivider()
                    Row(
                        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
                            .selectable(selected = mode == option, role = Role.RadioButton) { onSetTheme(option) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface,
                            modifier = Modifier.weight(1f))
                        if (mode == option) Text("✓", style = MaterialTheme.typography.titleLarge, color = colors.accent,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                }
                SettingsDivider()
                SettingsToggleRow(
                    stringResource(R.string.settings_left_handed_layout),
                    stringResource(R.string.settings_left_handed_layout_body),
                    leftHanded, onSetLeftHanded,
                )
            }
        }
    }
}

@Composable
private fun DeleteGroup(state: SettingsUiState, onAskClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroup {
            SettingsDestructiveRow(
                stringResource(R.string.settings_delete_data),
                enabled = state.deletion != DeletionUi.IN_PROGRESS,
                onClick = onAskClear,
            )
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
    demoTrial: DemoTrialState,
    onStartDemoTrial: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_pro))
        SettingsGroup {
            Column {
                val status = when (state.subscription) {
                    SubscriptionUi.CHECKING -> stringResource(R.string.settings_pro_checking)
                    SubscriptionUi.FREE -> stringResource(R.string.settings_pro_free)
                    SubscriptionUi.PRO -> demoTrialTitle(demoTrial) ?: stringResource(R.string.settings_pro_active)
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
                    SettingsInfoRow(stringResource(R.string.menu_pro_title), stringResource(R.string.menu_pro_body_free))
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.settings_see_pro), onClick = onPaywall)
                    if (demoTrial == DemoTrialState.NotStarted) {
                        SettingsDivider()
                        SettingsActionRow(stringResource(R.string.demo_trial_start), onClick = onStartDemoTrial)
                    }
                }
                if (state.subscription == SubscriptionUi.PRO) {
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.settings_open_packs), onClick = onOpenPacks)
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
        // One short line here; the demo, Test Store and cancellation details live on the Pro screen.
        when (demoTrial) {
            is DemoTrialState.Active -> SettingsGroupFooter(stringResource(R.string.demo_trial_active_body))
            is DemoTrialState.Ended -> SettingsGroupFooter(stringResource(R.string.demo_trial_ended_body))
            DemoTrialState.NotStarted -> if (state.billingConfigured) SettingsGroupFooter(stringResource(R.string.settings_test_store_footer))
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
internal fun PrivacyGroup(modelId: String?) {
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
internal fun AboutGroup(state: SettingsUiState) {
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
internal fun DebugGroup(onOpenCapture: () -> Unit) {
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
