package dev.handspell.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.AslSectionLabel
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Lighter-grey card holding one group of rows, white text. */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) = AslCard(content = content)

@Composable
internal fun SettingsGroupHeader(title: String) = AslSectionLabel(title)

@Composable
internal fun SettingsGroupFooter(body: String) {
    Text(
        body,
        style = MaterialTheme.typography.labelMedium,
        color = LocalAslColors.current.labelSecondary,
        modifier = Modifier.padding(horizontal = Spacing.xs),
    )
}

@Composable
internal fun SettingsDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.md)
            .background(LocalAslColors.current.separator).sizeIn(minHeight = Spacing.hairline),
    )
}

@Composable
internal fun SettingsInfoRow(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.onSurfaceSecondary)
    }
}

@Composable
internal fun SettingsValueRow(title: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
            .semantics(mergeDescendants = true) {}.padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.onSurfaceSecondary)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** White label with a blue chevron. */
@Composable
internal fun SettingsActionRow(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalAslColors.current
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface.copy(alpha = alpha),
            modifier = Modifier.weight(1f),
        )
        Text("›", style = MaterialTheme.typography.titleLarge, color = colors.accent.copy(alpha = alpha),
            modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * Deletion is a plain, explicit row (no red: the app has three colours); the confirmation dialog is the guard.
 */
@Composable
internal fun SettingsDestructiveRow(title: String, enabled: Boolean, onClick: () -> Unit) =
    SettingsActionRow(title, enabled, onClick)
