package dev.handspell.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Entry point for the actual legal files bundled with the app. */
@Composable
fun PaperScreen(
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onLicense: () -> Unit,
    onNotices: () -> Unit,
    onModelLicense: () -> Unit,
    onCapacitorCoreLicense: () -> Unit,
    onCapacitorSplashLicense: () -> Unit,
) {
    val colors = LocalAslColors.current
    Column(Modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        ScreenHeader(stringResource(R.string.paper_title), onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                stringResource(R.string.paper_intro), style = MaterialTheme.typography.bodyLarge,
                color = colors.labelSecondary, modifier = Modifier.padding(horizontal = Spacing.xs),
            )
            SettingsGroup {
                Column {
                    SettingsActionRow(stringResource(R.string.settings_privacy_notice), onClick = onPrivacy)
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.paper_mit_license), onClick = onLicense)
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.paper_notices), onClick = onNotices)
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.paper_model_license), onClick = onModelLicense)
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.paper_capacitor_core_license), onClick = onCapacitorCoreLicense)
                    SettingsDivider()
                    SettingsActionRow(stringResource(R.string.paper_capacitor_splash_license), onClick = onCapacitorSplashLicense)
                }
            }
        }
    }
}
