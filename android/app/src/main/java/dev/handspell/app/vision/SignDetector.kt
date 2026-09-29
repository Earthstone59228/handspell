package dev.handspell.app.vision

import androidx.camera.core.ImageAnalysis
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState
import java.util.concurrent.Executor
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

    /** Identifier of the active classifier, so the UI can disclose the stage-1 fallback. */
    val classifierModelId: String?

    /** Emits at most one state per analysed frame; conflate on the UI side. */
    val feedback: Flow<SignFeedbackState>

    /** Image-space landmarks for the live camera overlay; null while no hand is in frame. */
    val overlay: StateFlow<HandOverlay?>

    /**
     * A small copy of the latest analysed camera frame (upright, selfie-mirrored, i.e. the same picture the preview
     * shows), updated every frame. Used only to draw a live blurred backdrop; null when there is no camera.
     */
    val previewThumbnail: StateFlow<android.graphics.Bitmap?>

    /** True once a dark preview has shown no hand for the low-light interval. */
    val lowLightNotice: StateFlow<Boolean>

    /** Hide the notice for the remainder of this camera session. */
    fun dismissLowLightNotice()

    /** CameraX analyzer. Implementations must close every `ImageProxy` they receive. */
    val analyzer: ImageAnalysis.Analyzer

    /** The serial executor CameraX must use for [analyzer]. */
    val analyzerExecutor: Executor

    /** Null means "classify freely and report the top letter", used by the dev capture screen. */
    fun setTarget(target: Letter?)

    /** Claim the shared detector for one drill screen. A later owner replaces the earlier session. */
    fun start(owner: Any)

    /** Release only the caller's session; an exiting screen cannot stop its successor. */
    fun stop(owner: Any)
}

/** Identity-based lease for a shared detector and its asynchronous helper callbacks. */
internal class DetectorSessionGuard {
    private var owner: Any? = null
    private var generation = 0L

    fun owns(candidate: Any): Boolean = owner === candidate

    fun claim(candidate: Any): Long {
        owner = candidate
        return ++generation
    }

    fun release(candidate: Any): Boolean {
        if (owner !== candidate) return false
        owner = null
        generation++
        return true
    }

    fun isCurrent(token: Long): Boolean = owner != null && generation == token

    fun ifCurrent(token: Long, action: () -> Unit) {
        if (isCurrent(token)) action()
    }
}
