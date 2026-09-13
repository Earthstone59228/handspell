package dev.handspell.app.vision

import androidx.camera.core.ImageAnalysis
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle of the vision pipeline, surfaced so the UI can show a real state instead of a spinner. */
sealed interface DetectorStatus {
    data object Idle : DetectorStatus
    data object Starting : DetectorStatus
    data object Running : DetectorStatus

    /** Landmarker or model assets failed to load. [messageId] keys a string resource. */
    data class Failed(val messageId: String, val cause: Throwable?) : DetectorStatus
}

/**
 * The single surface the UI uses to practise a letter. Everything upstream — frame delivery,
 * landmarking, normalisation, classification, smoothing — is hidden behind it.
 *
 * The camera screen binds [analyzer] to a CameraX `ImageAnalysis` use case and collects [feedback].
 * [FakeSignDetector] satisfies the same contract with no camera at all, so UI work does not block on
 * the vision pipeline.
 */
interface SignDetector {
    val status: StateFlow<DetectorStatus>

    /** Emits at most one state per analysed frame; conflate on the UI side. */
    val feedback: Flow<SignFeedbackState>

    /** CameraX analyzer. Implementations must close every `ImageProxy` they receive. */
    val analyzer: ImageAnalysis.Analyzer

    /** Null means "classify freely and report the top letter", used by the dev capture screen. */
    fun setTarget(target: Letter?)

    fun start()

    fun stop()
}
