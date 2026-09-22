package dev.handspell.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.content.PackItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Immutable state for the practice catalogue; every content outcome is visible to the screen. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val drills: List<PackItem.Drill> = emptyList(),
    val error: Boolean = false,
)

class HomeViewModel(private val contentRepository: ContentRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        mutableUiState.value = HomeUiState(isLoading = true)
        viewModelScope.launch {
            val drills = runCatching { contentRepository.availableDrills() }.getOrElse { emptyList() }
            mutableUiState.value = if (drills.isEmpty()) {
                HomeUiState(isLoading = false, error = true)
            } else {
                HomeUiState(isLoading = false, drills = drills)
            }
        }
    }

    companion object {
        fun factory(contentRepository: ContentRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(contentRepository) as T
            }
    }
}
