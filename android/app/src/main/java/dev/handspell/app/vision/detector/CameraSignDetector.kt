package dev.handspell.app.vision.detector

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.FeedbackEngine
import dev.handspell.app.vision.HandNormalizer
import dev.handspell.app.vision.LetterClassifier
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.camera.FrameConverter
import dev.handspell.app.vision.classify.ClassifierAssetException
import dev.handspell.app.vision.landmarker.HandLandmarkerHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The real [SignDetector]: CameraX frame -> MediaPipe Hand Landmarker -> normalise -> classify ->
 * feedback, wired into a single façade for the UI (docs/ARCHITECTURE.md §3).
 *
 * [classifier] is nullable only so [dev.handspell.app.di.AppContainer] can construct this class with
 * an [initialFailure] when the stage-1 reference set does not exist yet. In that state [status] is
 * already `Failed`, so the camera screen never binds the analyser and [onHandLandmarks] is never
 * called with a null classifier.
 *
 * All access to [feedbackEngine] is serialised behind [lock]: MediaPipe delivers results on its own
 * thread while `setTarget`/`stop` arrive on the UI thread, and `DefaultFeedbackEngine` is
 * deliberately single-threaded by contract.
 */
class CameraSignDetector(
    private val context: Context,
    private val normalizer: HandNormalizer,
    private val classifier: LetterClassifier?,
    private val feedbackEngine: FeedbackEngine,
    private val frameConverter: FrameConverter = FrameConverter(),
    initialFailure: ClassifierAssetException? = null,
) : SignDetector {

    private val statusState = MutableStateFlow<DetectorStatus>(
        initialFailure?.let { DetectorStatus.Failed(it.messageId, it) } ?: DetectorStatus.Idle,
    )
    private val feedbackState = MutableStateFlow<SignFeedbackState>(SignFeedbackState.NoHand(null))
    private val overlayState = MutableStateFlow<HandOverlay?>(null)
    private val lock = Any()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "handspell-analysis")
    }

    override val status: StateFlow<DetectorStatus> = statusState.asStateFlow()
    override val feedback: Flow<SignFeedbackState> = feedbackState.asStateFlow()
    override val overlay: StateFlow<HandOverlay?> = overlayState.asStateFlow()
    override val analyzerExecutor: Executor = analysisExecutor

    @Volatile
    private var landmarkerHelper: HandLandmarkerHelper? = null

    private var target: Letter? = null

    override val analyzer: ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        val helper = landmarkerHelper
        if (helper != null) {
            try {
                val frame = frameConverter.convert(imageProxy)
                helper.detect(frame.image, SystemClock.uptimeMillis(), frame.width, frame.height)
            } catch (_: Throwable) {
                // A single unreadable frame is dropped. Fatal landmarker failures arrive via the
                // error listener, not here.
            }
        }
        imageProxy.close()
    }

    override fun setTarget(target: Letter?) {
        synchronized(lock) {
            this.target = target
            feedbackEngine.setTarget(target)
            feedbackState.value = SignFeedbackState.NoHand(target)
        }
    }

    override fun start() {
        if (statusState.value == DetectorStatus.Running) return
        if (statusState.value is DetectorStatus.Failed) return
        statusState.value = DetectorStatus.Starting
        try {
            landmarkerHelper = HandLandmarkerHelper(
                context = context,
                onResults = ::onHandLandmarks,
                onError = ::onLandmarkerError,
            )
            statusState.value = DetectorStatus.Running
        } catch (error: Throwable) {
            statusState.value = DetectorStatus.Failed(ERROR_LANDMARKER_FAILED, error)
        }
    }

    override fun stop() {
        landmarkerHelper?.close()
        landmarkerHelper = null
        synchronized(lock) {
            feedbackEngine.reset()
            feedbackState.value = SignFeedbackState.NoHand(target)
            overlayState.value = null
        }
        statusState.value = DetectorStatus.Idle
    }

    private fun onHandLandmarks(hands: List<HandLandmarks>) {
        synchronized(lock) {
            if (hands.isEmpty()) {
                feedbackState.value = feedbackEngine.onNoHand(SystemClock.uptimeMillis())
                return
            }
            val classifier = classifier ?: return

            // Single-target practice uses the first hand MediaPipe reports.
            val hand = hands.first()
            overlayState.value = HandOverlay(
                imageLandmarks = hand.image,
                imageWidth = hand.imageWidth,
                imageHeight = hand.imageHeight,
                timestampMs = hand.timestampMs,
            )
            val normalized = normalizer.normalize(hand)
            if (normalized == null) {
                feedbackState.value = feedbackEngine.onNoHand(hand.timestampMs)
                return
            }
            val classification = classifier.classify(normalized, hand.timestampMs)
            feedbackState.value = feedbackEngine.onFrame(classification)
        }
    }

    private fun onLandmarkerError(error: RuntimeException) {
        overlayState.value = null
        statusState.value = DetectorStatus.Failed(ERROR_LANDMARKER_FAILED, error)
    }

    companion object {
        private const val ERROR_LANDMARKER_FAILED = "error_landmarker_failed"
    }
}
