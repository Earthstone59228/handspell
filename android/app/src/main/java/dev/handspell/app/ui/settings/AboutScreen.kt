package dev.handspell.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R

/**
 * The technical details moved out of Settings: how the camera and data are handled, which recognition model is
 * loaded, the version, and (debug builds only) the calibration capture tool.
 */
@Composable
fun AboutScreen(classifierModelId: String?, buildInfo: BuildInfo, onBack: () -> Unit, onOpenCapture: (() -> Unit)? = null) {
    FrostedSettingsHero(onBack = onBack, title = stringResource(R.string.settings_about_title), body = null) {
        PrivacyGroup(classifierModelId)
        AboutGroup(SettingsUiState(versionName = buildInfo.versionName, versionCode = buildInfo.versionCode))
        if (onOpenCapture != null) DebugGroup(onOpenCapture)
    }
}
