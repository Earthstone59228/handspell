package dev.handspell.app.vision.words

import android.content.Context
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.vision.DetectorSessionGuard
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.camera.FrameConverter
import dev.handspell.app.vision.landmarker.HandLandmarkerHelper
import dev.handspell.app.vision.landmarker.PoseLandmarkerHelper
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The surface the word drill screen uses. [CameraWordDetector] is the real one. */
interface WordDetector {
    val status: StateFlow<DetectorStatus>
    val progress: StateFlow<WordProgress>
    val overlay: StateFlow<HandOverlay?>
    val previewThumbnail: StateFlow<android.graphics.Bitmap?>
    val analyzer: ImageAnalysis.Analyzer
    val analyzerExecutor: Executor

    /** Choose the word to recognise (a gloss the model knows). Resets progress. */
    fun setTarget(gloss: String)

    fun start(owner: Any)
    fun stop(owner: Any)
}

/**
 * CameraX frame -> Hand Landmarker (two hands) + Pose Landmarker (nose and shoulders, every [POSE_EVERY_N_FRAMES]th
 * frame, since the body barely moves) -> [WordFrame] -> [WordRecognizer]. The lifecycle rules are the letter
 * detector's: one owner at a time, a helper reports only while it is current, and a stale screen cannot stop its
 * successor (see DetectorSessionGuard).
 */
class CameraWordDetector(
    private val context: Context,
    private val classifier: WordClassifier?,
    private val frameConverter: FrameConverter = FrameConverter(),
    /** Further models (the Pro words); a target is scored by whichever model knows its gloss. */
    extraClassifiers: List<WordClassifier> = emptyList(),
) : WordDetector {

    private val classifiers: List<WordClassifier> = listOfNotNull(classifier) + extraClassifiers

    private val statusState = MutableStateFlow<DetectorStatus>(
        if (classifiers.isEmpty()) DetectorStatus.Failed("error_words_unavailable", null) else DetectorStatus.Idle,
    )
    private val progressState = MutableStateFlow<WordProgress>(WordProgress.NoHand)
    private val overlayState = MutableStateFlow<HandOverlay?>(null)
    private val thumbnailState = MutableStateFlow<android.graphics.Bitmap?>(null)
    private val lock = Any()
    private val sessions = DetectorSessionGuard()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "handspell-words") }

    override val status: StateFlow<DetectorStatus> = statusState.asStateFlow()
    override val progress: StateFlow<WordProgress> = progressState.asStateFlow()
    override val overlay: StateFlow<HandOverlay?> = overlayState.asStateFlow()
    override val previewThumbnail: StateFlow<android.graphics.Bitmap?> = thumbnailState.asStateFlow()
    override val analyzerExecutor: Executor = executor

    private class Helpers(val hands: HandLandmarkerHelper, val pose: PoseLandmarkerHelper) {
        fun close() {
            hands.close()
            pose.close()
        }
    }

    @Volatile private var helpers: Helpers? = null
    @Volatile private var latestPose: FloatArray? = null
    @Volatile private var recognizer: WordRecognizer? = null
    private var target: String? = null
    private val slotTracker = HandSlotTracker()
    private var frameIndex = 0L

    override val analyzer: ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        try {
            val current = helpers
            if (current != null) {
                val frame = frameConverter.convert(imageProxy)
                synchronized(lock) { if (helpers === current) thumbnailState.value = frame.thumbnail }
                val now = SystemClock.uptimeMillis()
                try {
                    if (frameIndex++ % POSE_EVERY_N_FRAMES == 0L) current.pose.detect(frame.image, now)
                    current.hands.detect(frame.image, now, frame.width, frame.height)
                } catch (error: RuntimeException) {
                    if (helpers === current) throw error // a screen change may close helpers under us
                }
            }
        } finally {
            imageProxy.close()
        }
    }

    override fun setTarget(gloss: String) {
        synchronized(lock) {
            target = gloss
            val scorer = classifiers.firstOrNull { gloss in it.labels }
            recognizer = scorer?.let { WordRecognizer(it::probabilityOf, gloss) }
            progressState.value = WordProgress.NoHand
        }
    }

    override fun start(owner: Any) {
        val (token, previous) = synchronized(lock) {
            if (sessions.owns(owner) && statusState.value == DetectorStatus.Running) return
            if (classifiers.isEmpty()) return
            val old = helpers
            helpers = null
            val generation = sessions.claim(owner)
            latestPose = null
            frameIndex = 0
            slotTracker.reset()
            recognizer?.reset()
            progressState.value = WordProgress.NoHand
            overlayState.value = null
            thumbnailState.value = null
            statusState.value = DetectorStatus.Starting
            generation to old
        }
        try {
            previous?.close()
            val created = Helpers(
                hands = HandLandmarkerHelper(
                    context = context,
                    onResults = { hands -> onHands(token, hands) },
                    onError = { error -> onError(token, error) },
                ),
                pose = PoseLandmarkerHelper(
                    context = context,
                    onPose = { _, anchors -> synchronized(lock) { if (sessions.isCurrent(token)) latestPose = anchors } },
                    onError = { error -> onError(token, error) },
                ),
            )
            val accepted = synchronized(lock) {
                var current = false
                sessions.ifCurrent(token) {
                    helpers = created
                    statusState.value = DetectorStatus.Running
                    current = true
                }
                current
            }
            if (!accepted) created.close()
        } catch (error: Throwable) {
            synchronized(lock) {
                sessions.ifCurrent(token) { statusState.value = DetectorStatus.Failed("error_landmarker_failed", error) }
            }
        }
    }

    override fun stop(owner: Any) {
        val previous = synchronized(lock) {
            if (!sessions.release(owner)) return
            val old = helpers
            helpers = null
            recognizer?.reset()
            progressState.value = WordProgress.NoHand
            overlayState.value = null
            thumbnailState.value = null
            latestPose = null
            statusState.value = if (classifiers.isEmpty()) statusState.value else DetectorStatus.Idle
            old
        }
        previous?.close()
    }

    private fun onHands(token: Long, hands: List<HandLandmarks>) {
        synchronized(lock) {
            if (!sessions.isCurrent(token) || helpers == null) return
            val active = recognizer ?: return
            overlayState.value = hands.firstOrNull()?.let {
                HandOverlay(it.image, it.imageWidth, it.imageHeight, it.timestampMs)
            }
            val timestamp = hands.firstOrNull()?.timestampMs ?: SystemClock.uptimeMillis()
            val slots = slotTracker.assign(
                hands.take(2).map { hand ->
                    FloatArray(42) { k -> if (k % 2 == 0) hand.image[k / 2].x else hand.image[k / 2].y }
                },
                timestamp,
            )
            progressState.value = active.onFrame(WordFrame(slots, latestPose), timestamp)
        }
    }

    private fun onError(token: Long, error: RuntimeException) {
        synchronized(lock) {
            sessions.ifCurrent(token) {
                if (helpers != null) {
                    overlayState.value = null
                    statusState.value = DetectorStatus.Failed("error_landmarker_failed", error)
                }
            }
        }
    }

    companion object {
        const val POSE_EVERY_N_FRAMES = 3L
    }
}
