package dev.handspell.app.ui.settings

import dev.handspell.app.ui.components.selectionOutline

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.R
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.reminder.ReminderScheduler
import dev.handspell.app.reminder.ReminderSlot
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import kotlinx.coroutines.launch

internal fun notificationsAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** What happens when the reminder switch is turned on: ask first (with our own rationale) or just enable. */
internal fun needsNotificationRationale(sdk: Int, granted: Boolean): Boolean = sdk >= 33 && !granted

/** State and actions for the Reminders group; the rationale sheet is shown by [ReminderRationale]. */
class ReminderController internal constructor(
    val enabled: Boolean,
    val slot: ReminderSlot,
    val blocked: Boolean,
    val rationaleVisible: Boolean,
    val onToggle: (Boolean) -> Unit,
    val onSlot: (ReminderSlot) -> Unit,
    val onRationaleContinue: () -> Unit,
    val onRationaleDismiss: () -> Unit,
    val onOpenSystemSettings: () -> Unit,
)

@Composable
internal fun rememberReminderController(preferences: AppPreferencesStore): ReminderController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by preferences.reminderEnabled.collectAsStateWithLifecycle(initialValue = false)
    val slot by preferences.reminderSlot.collectAsStateWithLifecycle(initialValue = ReminderSlot.EVENING)
    var rationale by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }
    fun enable() = scope.launch {
        preferences.setReminderEnabled(true)
        ReminderScheduler.schedule(context, slot)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        blocked = !granted
        if (granted) enable()
    }
    return ReminderController(
        enabled = enabled, slot = slot, blocked = blocked && !enabled, rationaleVisible = rationale,
        onToggle = { on ->
            if (!on) {
                blocked = false
                scope.launch { preferences.setReminderEnabled(false); ReminderScheduler.cancel(context) }
            } else if (needsNotificationRationale(Build.VERSION.SDK_INT, notificationsAllowed(context))) rationale = true
            else enable()
        },
        onSlot = { chosen ->
            scope.launch {
                preferences.setReminderSlot(chosen)
                if (enabled) ReminderScheduler.schedule(context, chosen)
            }
        },
        onRationaleContinue = {
            rationale = false
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onRationaleDismiss = { rationale = false },
        onOpenSystemSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
    )
}

@Composable
internal fun ReminderGroup(controller: ReminderController) {
    val colors = LocalAslColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsGroupHeader(stringResource(R.string.settings_reminders))
        SettingsGroup {
            Column(Modifier.selectableGroup()) {
                SettingsToggleRow(stringResource(R.string.settings_reminder_title), stringResource(R.string.settings_reminder_body),
                    controller.enabled, controller.onToggle)
                if (controller.enabled) listOf(
                    ReminderSlot.MORNING to R.string.settings_reminder_morning,
                    ReminderSlot.AFTERNOON to R.string.settings_reminder_afternoon,
                    ReminderSlot.EVENING to R.string.settings_reminder_evening,
                ).forEach { (slot, label) ->
                    SettingsDivider()
                    Row(
                        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
                            .selectionOutline(controller.slot == slot)
                            .selectable(selected = controller.slot == slot, role = Role.RadioButton) { controller.onSlot(slot) }
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface,
                            modifier = Modifier.weight(1f))
                        if (controller.slot == slot) Text("✓", style = MaterialTheme.typography.titleLarge, color = colors.accent,
                            modifier = Modifier.clearAndSetSemantics {})
                    }
                }
                if (controller.blocked) {
                    SettingsDivider()
                    SettingsInfoRow(stringResource(R.string.settings_reminder_title), stringResource(R.string.settings_reminder_blocked))
                    SettingsActionRow(stringResource(R.string.open_settings), onClick = controller.onOpenSystemSettings)
                }
            }
        }
    }
}

/** Our rationale before Android's own prompt: what it is for, and that it can be turned off. */
@Composable
internal fun ReminderRationale(controller: ReminderController) {
    dev.handspell.app.ui.components.AslSheet(visible = controller.rationaleVisible, onDismiss = controller.onRationaleDismiss) {
        Text(stringResource(R.string.reminder_rationale_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.reminder_rationale_body), style = MaterialTheme.typography.bodyLarge,
            color = LocalAslColors.current.labelSecondary)
        AslButton(stringResource(R.string.reminder_rationale_continue), controller.onRationaleContinue, Modifier.fillMaxWidth())
        AslButton(stringResource(R.string.settings_cancel), controller.onRationaleDismiss, Modifier.fillMaxWidth(),
            style = AslButtonStyle.Secondary)
    }
}
