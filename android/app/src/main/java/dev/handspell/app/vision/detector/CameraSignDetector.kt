package dev.handspell.app.vision.detector

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
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

    private val initialFailureStatus = initialFailure?.let { DetectorStatus.Failed(it.messageId, it) }
    private val statusState = MutableStateFlow<DetectorStatus>(initialFailureStatus ?: DetectorStatus.Idle)
    private val feedbackState = MutableStateFlow<SignFeedbackState>(SignFeedbackState.NoHand(null))
    private val overlayState = MutableStateFlow<HandOverlay?>(null)
    private val lowLightState = MutableStateFlow(false)
    private val thumbnailState = MutableStateFlow<android.graphics.Bitmap?>(null)
    private val lock = Any()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "handspell-analysis")
    }

    override val status: StateFlow<DetectorStatus> = statusState.asStateFlow()
    override val classifierModelId: String? = classifier?.modelId
    override val feedback: Flow<SignFeedbackState> = feedbackState.asStateFlow()
    override val overlay: StateFlow<HandOverlay?> = overlayState.asStateFlow()
    override val lowLightNotice: StateFlow<Boolean> = lowLightState.asStateFlow()
    override val previewThumbnail: StateFlow<android.graphics.Bitmap?> = thumbnailState.asStateFlow()
    override val analyzerExecutor: Executor = analysisExecutor

    @Volatile
    private var landmarkerHelper: HandLandmarkerHelper? = null

    private var target: Letter? = null
    @Volatile private var lastHandSeenMs = 0L
    @Volatile private var lowLightDismissed = false

    override val analyzer: ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        try {
            val helper = landmarkerHelper
            if (helper != null) {
                val now = SystemClock.uptimeMillis()
                if (!lowLightDismissed && now - lastHandSeenMs >= LOW_LIGHT_WAIT_MS &&
                    isLowLight(imageProxy)) lowLightState.value = true
                val frame = frameConverter.convert(imageProxy)
                thumbnailState.value = frame.thumbnail
                helper.detect(frame.image, SystemClock.uptimeMillis(), frame.width, frame.height)
            }
        } finally {
            // CameraX owns the image buffer; it must be released even if conversion or inference
            // submission fails so the keep-latest pipeline never stalls.
            imageProxy.close()
        }
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
        lastHandSeenMs = SystemClock.uptimeMillis()
        lowLightState.value = false
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
            thumbnailState.value = null
        }
        statusState.value = initialFailureStatus ?: DetectorStatus.Idle
        lowLightState.value = false
    }

    override fun dismissLowLightNotice() {
        lowLightDismissed = true
        lowLightState.value = false
    }

    private fun onHandLandmarks(hands: List<HandLandmarks>) {
        synchronized(lock) {
            if (hands.isEmpty()) {
                overlayState.value = null
                feedbackState.value = feedbackEngine.onNoHand(SystemClock.uptimeMillis())
                return
            }
            val classifier = classifier ?: return
            lastHandSeenMs = SystemClock.uptimeMillis()
            lowLightState.value = false

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
        private const val LOW_LIGHT_WAIT_MS = 5_000L
        private const val LOW_LIGHT_MEAN_RGB = 45
    }

    private fun isLowLight(image: ImageProxy): Boolean {
        val plane = image.planes.firstOrNull() ?: return false
        val buffer = plane.buffer
        var total = 0L
        var samples = 0
        for (row in 0 until 8) for (column in 0 until 8) {
            val x = (column * image.width / 8).coerceAtMost(image.width - 1)
            val y = (row * image.height / 8).coerceAtMost(image.height - 1)
            val index = y * plane.rowStride + x * plane.pixelStride
            if (index + 2 >= buffer.limit()) continue
            total += (buffer.get(index).toInt() and 0xff) +
                (buffer.get(index + 1).toInt() and 0xff) +
                (buffer.get(index + 2).toInt() and 0xff)
            samples++
        }
        return samples > 0 && total < LOW_LIGHT_MEAN_RGB * 3L * samples
    }
}
