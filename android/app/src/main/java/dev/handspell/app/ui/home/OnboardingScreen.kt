package dev.handspell.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped)
            .verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
        OnboardingPoint(R.string.onboarding_practice_title, R.string.onboarding_practice_body)
        OnboardingPoint(R.string.onboarding_letters_title, R.string.onboarding_letters_body)
        OnboardingPoint(R.string.onboarding_camera_title, R.string.camera_permission_body)
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun OnboardingPoint(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge,
            color = LocalAslColors.current.labelSecondary)
    }
}
