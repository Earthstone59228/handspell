package dev.handspell.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/**
 * A plain-language privacy screen. It intentionally contains no non-functional preferences:
 * there are no account, telemetry, or camera-storage controls for this app to configure.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCapture: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAslColors.current
    Column(modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget),
            ) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.settings_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.labelSecondary,
            )
            SettingsGroup {
                Column {
                    SettingsRow(
                        title = stringResource(R.string.settings_camera_title),
                        body = stringResource(R.string.settings_camera_body),
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_data_title),
                        body = stringResource(R.string.settings_data_body),
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_network_title),
                        body = stringResource(R.string.settings_network_body),
                    )
                }
            }
            if (onOpenCapture != null) {
                SettingsGroup {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            stringResource(R.string.debug_capture_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.debug_capture_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.labelSecondary,
                        )
                        Button(onClick = onOpenCapture) {
                            Text(stringResource(R.string.debug_capture_open))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) = Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(AslShapes.large),
    color = LocalAslColors.current.surface,
    border = androidx.compose.foundation.BorderStroke(Spacing.hairline, LocalAslColors.current.separator),
    content = content,
)

@Composable
private fun SettingsRow(title: String, body: String) = Column(
    modifier = Modifier.padding(Spacing.md),
    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.labelSecondary)
}

@Composable
private fun SettingsDivider() = androidx.compose.foundation.layout.Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(start = Spacing.md)
        .background(LocalAslColors.current.separator)
        .sizeIn(minHeight = Spacing.hairline),
)
