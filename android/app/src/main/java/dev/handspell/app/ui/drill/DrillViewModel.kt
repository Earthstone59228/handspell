package dev.handspell.app.ui.drill

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.handspell.app.content.PackItem
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

/** The CameraX objects required to bind the detector. They are stable for this detector instance. */
data class DrillCameraBinding(
    val analyzer: ImageAnalysis.Analyzer,
    val analyzerExecutor: Executor,
)

/** One immutable state object for a letter drill (docs/ARCHITECTURE.md §5). */
data class DrillUiState(
    val isLoading: Boolean = true,
    val drill: PackItem.Drill? = null,
    val drills: List<PackItem.Drill> = emptyList(),
    val detectorStatus: DetectorStatus = DetectorStatus.Idle,
    val feedback: SignFeedbackState = SignFeedbackState.NoHand(null),
    val overlay: HandOverlay? = null,
    val canonicalHandshape: CanonicalHandshape? = null,
    val classifierModelId: String? = null,
    val cameraBinding: DrillCameraBinding? = null,
    val cameraUnavailable: Boolean = false,
    val cameraSession: Int = 0,
)

class DrillViewModel(
    private val signDetector: SignDetector,
    private val canonicalHandshapeCatalog: CanonicalHandshapeCatalog,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        DrillUiState(
            cameraBinding = DrillCameraBinding(signDetector.analyzer, signDetector.analyzerExecutor),
            classifierModelId = signDetector.classifierModelId,
        ),
    )
    val uiState: StateFlow<DrillUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            signDetector.status.collect { status ->
                mutableUiState.value = mutableUiState.value.copy(detectorStatus = status)
            }
        }
        viewModelScope.launch {
            signDetector.feedback.collect { feedback ->
                mutableUiState.value = mutableUiState.value.copy(feedback = feedback)
            }
        }
        viewModelScope.launch {
            signDetector.overlay.collect { overlay ->
                mutableUiState.value = mutableUiState.value.copy(overlay = overlay)
            }
        }
    }

    fun setDrill(drill: PackItem.Drill, drills: List<PackItem.Drill>) {
        if (mutableUiState.value.drill?.id == drill.id) return
        signDetector.stop()
        signDetector.setTarget(drill.letter)
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = false,
            drill = drill,
            drills = drills,
            feedback = SignFeedbackState.NoHand(drill.letter),
            overlay = null,
            canonicalHandshape = null,
            cameraUnavailable = false,
        )
        viewModelScope.launch {
            val canonicalHandshape = canonicalHandshapeCatalog.handshapeFor(drill.letter)
            if (mutableUiState.value.drill?.id == drill.id) {
                mutableUiState.value = mutableUiState.value.copy(canonicalHandshape = canonicalHandshape)
            }
        }
    }

    fun startDetector() {
        if (mutableUiState.value.drill != null) signDetector.start()
    }

    fun onCameraUnavailable() {
        mutableUiState.value = mutableUiState.value.copy(cameraUnavailable = true)
    }

    fun retryDetector() {
        val drill = mutableUiState.value.drill ?: return
        signDetector.stop()
        signDetector.setTarget(drill.letter)
        mutableUiState.value = mutableUiState.value.copy(
            cameraUnavailable = false,
            cameraSession = mutableUiState.value.cameraSession + 1,
        )
        signDetector.start()
    }

    override fun onCleared() {
        signDetector.stop()
        super.onCleared()
    }

    companion object {
        fun factory(
            signDetector: SignDetector,
            canonicalHandshapeCatalog: CanonicalHandshapeCatalog,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DrillViewModel(signDetector, canonicalHandshapeCatalog) as T
            }
    }
}
