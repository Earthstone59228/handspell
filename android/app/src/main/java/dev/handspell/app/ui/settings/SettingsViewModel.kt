package dev.handspell.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.billing.EntitlementStatus
import dev.handspell.app.billing.NoopEntitlementGate
import dev.handspell.app.billing.PaywallSource
import dev.handspell.app.billing.RestoreResult
import dev.handspell.app.core.model.Letter
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.prefs.ThemeMode
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BuildInfo(val versionName: String, val versionCode: Int)

sealed interface ProgressSummary {
    data object Loading : ProgressSummary
    data object Empty : ProgressSummary
    data class Recorded(
        val currentStreakDays: Int,
        val lettersPractised: Int,
        val lettersTotal: Int,
        val attempts: Int,
        val matches: Int,
    ) : ProgressSummary
}

enum class SubscriptionUi { CHECKING, FREE, PRO, UNAVAILABLE }
enum class RestoreUi { IDLE, IN_PROGRESS, RESTORED, NOTHING_TO_RESTORE, FAILED }
enum class DeletionUi { IDLE, IN_PROGRESS, DONE, FAILED }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val leftHanded: Boolean = false,
    val progress: ProgressSummary = ProgressSummary.Loading,
    val subscription: SubscriptionUi = SubscriptionUi.CHECKING,
    val billingConfigured: Boolean = false,
    val restore: RestoreUi = RestoreUi.IDLE,
    val confirmClearProgress: Boolean = false,
    val deletion: DeletionUi = DeletionUi.IDLE,
    val classifierModelId: String? = null,
    val versionName: String = "",
    val versionCode: Int = 0,
)

private data class LocalUi(
    val restore: RestoreUi = RestoreUi.IDLE,
    val confirmClearProgress: Boolean = false,
    val deletion: DeletionUi = DeletionUi.IDLE,
)

class SettingsViewModel(
    private val preferences: AppPreferencesStore,
    private val progressStore: ProgressStore,
    private val clearAlphabetData: suspend () -> Unit,
    private val entitlementGate: EntitlementGate,
    classifierModelId: String?,
    buildInfo: BuildInfo,
) : ViewModel() {
    private val localUi = MutableStateFlow(LocalUi())
    private val initial = SettingsUiState(
        billingConfigured = entitlementGate !is NoopEntitlementGate,
        classifierModelId = classifierModelId,
        versionName = buildInfo.versionName,
        versionCode = buildInfo.versionCode,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.themeMode,
        preferences.leftHanded,
        progressStore.snapshot,
        entitlementGate.status,
        localUi,
    ) { theme, leftHanded, progress, entitlement, local ->
        initial.copy(
            themeMode = theme,
            leftHanded = leftHanded,
            progress = progress.toSummary(),
            subscription = when (entitlement) {
                EntitlementStatus.Unknown, EntitlementStatus.Loading -> SubscriptionUi.CHECKING
                is EntitlementStatus.Resolved -> if (entitlement.isPro) SubscriptionUi.PRO else SubscriptionUi.FREE
                is EntitlementStatus.Unavailable -> SubscriptionUi.UNAVAILABLE
            },
            restore = local.restore,
            confirmClearProgress = local.confirmClearProgress,
            deletion = local.deletion,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }
    fun setLeftHanded(enabled: Boolean) = viewModelScope.launch { preferences.setLeftHanded(enabled) }

    fun askClearProgress() {
        localUi.update { it.copy(confirmClearProgress = true, deletion = DeletionUi.IDLE) }
    }

    fun dismissDialog() {
        localUi.update { it.copy(confirmClearProgress = false) }
    }

    fun confirmClearProgress() = viewModelScope.launch {
        localUi.update { it.copy(confirmClearProgress = false, deletion = DeletionUi.IN_PROGRESS) }
        val result = runCatchingUnlessCancelled {
            clearAlphabetData()
            progressStore.clearAll()
        }
        localUi.update { it.copy(deletion = if (result.isSuccess) DeletionUi.DONE else DeletionUi.FAILED) }
    }

    fun openPaywall() = entitlementGate.requestPaywall(PaywallSource.SETTINGS)

    fun restorePurchases() = viewModelScope.launch {
        localUi.update { it.copy(restore = RestoreUi.IN_PROGRESS) }
        val result = runCatchingUnlessCancelled { entitlementGate.restorePurchases() }.getOrDefault(RestoreResult.FAILED)
        localUi.update { it.copy(restore = when (result) {
            RestoreResult.RESTORED -> RestoreUi.RESTORED
            RestoreResult.NOTHING_TO_RESTORE -> RestoreUi.NOTHING_TO_RESTORE
            RestoreResult.FAILED -> RestoreUi.FAILED
        }) }
    }

    fun retryEntitlement() = viewModelScope.launch { runCatchingUnlessCancelled { entitlementGate.refresh() } }

    companion object {
        fun factory(
            preferences: AppPreferencesStore,
            progressStore: ProgressStore,
            clearAlphabetData: suspend () -> Unit,
            entitlementGate: EntitlementGate,
            classifierModelId: String?,
            buildInfo: BuildInfo,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(preferences, progressStore, clearAlphabetData, entitlementGate, classifierModelId, buildInfo) as T
        }
    }
}

internal inline fun <T> runCatchingUnlessCancelled(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }

internal fun ProgressSnapshot.toSummary(): ProgressSummary {
    if (letters.isEmpty() && completedStoryStepIds.isEmpty() && speedRuns.isEmpty()) return ProgressSummary.Empty
    return ProgressSummary.Recorded(
        currentStreakDays = currentStreakDays,
        lettersPractised = letters.values.count { it.attempts > 0 },
        lettersTotal = Letter.staticLetters.size,
        attempts = letters.values.sumOf { it.attempts },
        matches = letters.values.sumOf { it.matches },
    )
}
