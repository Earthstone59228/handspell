package dev.handspell.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.PackItem
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable state for the practice catalogue; every content outcome is visible to the screen. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val drills: List<PackItem.Drill> = emptyList(),
    val packs: List<ContentPack> = emptyList(),
    val error: Boolean = false,
    val attemptCounts: Map<Letter, Int> = emptyMap(),
    val isPro: Boolean = false,
)

class HomeViewModel(
    private val contentRepository: ContentRepository,
    progressStore: ProgressStore,
    entitlementGate: EntitlementGate,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState(isPro = entitlementGate.isPro.value))
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            contentRepository.packs.collect { packs ->
                mutableUiState.update { it.copy(packs = packs) }
            }
        }
        viewModelScope.launch {
            progressStore.snapshot.collect { snapshot ->
                mutableUiState.update { current ->
                    current.copy(attemptCounts = snapshot.letters.mapValues { it.value.attempts })
                }
            }
        }
        viewModelScope.launch {
            entitlementGate.isPro.collect { isPro ->
                mutableUiState.update { it.copy(isPro = isPro) }
            }
        }
    }

    fun reload() {
        mutableUiState.update { it.copy(isLoading = true, error = false) }
        viewModelScope.launch {
            val drills = runCatching { contentRepository.availableDrills() }.getOrElse { emptyList() }
            mutableUiState.value = if (drills.isEmpty()) {
                mutableUiState.value.copy(isLoading = false, error = true)
            } else {
                mutableUiState.value.copy(isLoading = false, drills = drills)
            }
        }
    }

    companion object {
        fun factory(contentRepository: ContentRepository, progressStore: ProgressStore,
                    entitlementGate: EntitlementGate): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(contentRepository, progressStore, entitlementGate) as T
            }
    }
}
