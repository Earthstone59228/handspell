package dev.handspell.app.ui.drill

import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.handspell.app.content.PackItem
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.progress.ProgressStore
import android.os.SystemClock
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

/** The CameraX objects required to bind the detector. They are stable for this detector instance. */
data class DrillCameraBinding(
    val analyzer: ImageAnalysis.Analyzer,
    val analyzerExecutor: Executor,
)

/** One immutable state object for a letter drill (docs/ARCHITECTURE.md §5). */
data class DrillUiState(
    val isLoading: Boolean = true,
    val sessionKey: String? = null,
    val drill: PackItem.Drill? = null,
    val drills: List<PackItem.Drill> = emptyList(),
    val detectorStatus: DetectorStatus = DetectorStatus.Idle,
    val feedback: SignFeedbackState = SignFeedbackState.NoHand(null),
    val matched: Boolean = false,
    val overlay: HandOverlay? = null,
    val canonicalHandshape: CanonicalHandshape? = null,
    val classifierModelId: String? = null,
    val cameraBinding: DrillCameraBinding? = null,
    val cameraUnavailable: Boolean = false,
    val cameraSession: Int = 0,
    val lowLightNotice: Boolean = false,
)

class DrillViewModel(
    private val signDetector: SignDetector,
    private val loadCanonicalHandshape: suspend (Letter) -> CanonicalHandshape?,
    private val progressStore: ProgressStore,
    private val monotonicTime: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private var startedAtMs = 0L
    private var attemptRecorded = false
    private var activeSessionKey: String? = null
    private val mutableUiState = MutableStateFlow(
        DrillUiState(
            cameraBinding = DrillCameraBinding(signDetector.analyzer, signDetector.analyzerExecutor),
            classifierModelId = signDetector.classifierModelId,
        ),
    )
    val uiState: StateFlow<DrillUiState> = mutableUiState.asStateFlow()

    /** Per-frame camera thumbnail, kept out of [uiState] so a 30 fps stream does not recompose the whole screen. */
    val previewThumbnail: StateFlow<android.graphics.Bitmap?> = signDetector.previewThumbnail

    init {
        viewModelScope.launch {
            signDetector.status.collect { status ->
                mutableUiState.value = mutableUiState.value.copy(detectorStatus = status)
            }
        }
        viewModelScope.launch {
            signDetector.feedback.collect { feedback ->
                mutableUiState.value = mutableUiState.value.copy(feedback = feedback)
                if (feedback is SignFeedbackState.Match &&
                    feedback.target == mutableUiState.value.drill?.letter && !attemptRecorded
                ) {
                    attemptRecorded = true
                    mutableUiState.value = mutableUiState.value.copy(matched = true)
                    val elapsed = (monotonicTime() - startedAtMs).coerceAtLeast(0L)
                    viewModelScope.launch {
                        withContext(NonCancellable) { progressStore.recordAttempt(feedback.target, true, elapsed) }
                    }
                }
            }
        }
        viewModelScope.launch {
            signDetector.overlay.collect { overlay ->
                mutableUiState.value = mutableUiState.value.copy(overlay = overlay)
            }
        }
        viewModelScope.launch {
            signDetector.lowLightNotice.collect { visible ->
                mutableUiState.value = mutableUiState.value.copy(lowLightNotice = visible)
            }
        }
    }

    fun setDrill(drill: PackItem.Drill, drills: List<PackItem.Drill>, sessionKey: String = drill.id) {
        if (activeSessionKey == sessionKey) return
        activeSessionKey = sessionKey
        attemptRecorded = false
        startedAtMs = monotonicTime()
        signDetector.stop(this)
        signDetector.setTarget(drill.letter)
        mutableUiState.value = mutableUiState.value.copy(
            isLoading = false,
            sessionKey = sessionKey,
            drill = drill,
            drills = drills,
            feedback = SignFeedbackState.NoHand(drill.letter),
            matched = false,
            overlay = null,
            canonicalHandshape = null,
            cameraUnavailable = false,
            lowLightNotice = false,
        )
        viewModelScope.launch {
            val canonicalHandshape = loadCanonicalHandshape(drill.letter)
            if (mutableUiState.value.drill?.id == drill.id) {
                mutableUiState.value = mutableUiState.value.copy(canonicalHandshape = canonicalHandshape)
            }
        }
    }

    fun startDetector() {
        if (mutableUiState.value.drill != null) signDetector.start(this)
    }

    fun onCameraUnavailable() {
        mutableUiState.value = mutableUiState.value.copy(cameraUnavailable = true)
    }

    fun dismissLowLightNotice() = signDetector.dismissLowLightNotice()

    fun retryDetector() {
        val drill = mutableUiState.value.drill ?: return
        signDetector.stop(this)
        signDetector.setTarget(drill.letter)
        mutableUiState.value = mutableUiState.value.copy(
            cameraUnavailable = false,
            cameraSession = mutableUiState.value.cameraSession + 1,
        )
        signDetector.start(this)
    }

    suspend fun recordSkip() {
        val state = mutableUiState.value
        val letter = state.drill?.letter ?: return
        if (attemptRecorded || state.feedback is SignFeedbackState.NoHand) return
        attemptRecorded = true
        progressStore.recordAttempt(letter, false, null)
    }

    override fun onCleared() {
        signDetector.stop(this)
        super.onCleared()
    }

    companion object {
        fun factory(
            signDetector: SignDetector,
            canonicalHandshapeCatalog: CanonicalHandshapeCatalog,
            progressStore: ProgressStore,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DrillViewModel(signDetector, canonicalHandshapeCatalog::handshapeFor, progressStore) as T
            }
    }
}
