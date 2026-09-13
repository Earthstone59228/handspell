package dev.handspell.app.vision.landmarker

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Handedness
import dev.handspell.app.core.model.Landmark3

/**
 * MediaPipe Hand Landmarker wrapper in LIVE_STREAM mode (docs/ARCHITECTURE.md §3).
 *
 * The analyser thread converts frames and calls [detect] with a monotonic timestamp; MediaPipe runs
 * inference on its own thread and delivers [HandLandmarks] back through [onResults]. The wrapper
 * only remembers the most recent frame size and timestamp (they do not change between adjacent
 * frames and are not present on the async result), then closes the landmarker in [close].
 *
 * CPU delegate is the verified default for the hand model at preview resolutions
 * (docs/research/mediapipe.md §1).
 */
class HandLandmarkerHelper(
    context: Context,
    modelAssetPath: String = MODEL_ASSET_PATH,
    numHands: Int = MAX_HANDS,
    private val onResults: (List<HandLandmarks>) -> Unit,
    private val onError: (RuntimeException) -> Unit,
) {

    private val landmarker: HandLandmarker

    @Volatile
    private var lastTimestampMs = 0L

    @Volatile
    private var lastImageWidth = 0

    @Volatile
    private var lastImageHeight = 0

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(modelAssetPath)
            .setDelegate(Delegate.CPU)
            .build()
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(numHands)
            .setMinHandDetectionConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> onResults(toHandLandmarks(result)) }
            .setErrorListener(onError)
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    fun detect(image: MPImage, timestampMs: Long, imageWidth: Int, imageHeight: Int) {
        lastTimestampMs = timestampMs
        lastImageWidth = imageWidth
        lastImageHeight = imageHeight
        landmarker.detectAsync(image, timestampMs)
    }

    fun close() = landmarker.close()

    /**
     * MediaPipe returns world landmarks, image landmarks and handedness as three parallel lists:
     * index `i` of each list describes the same hand. [image] and [handedness] are read by index on
     * the strength of that contract, which the landmarker itself guarantees.
     */
    private fun toHandLandmarks(result: HandLandmarkerResult): List<HandLandmarks> {
        val world = result.handWorldLandmarks()
        val image = result.handLandmarks()
        val handedness = result.handedness()

        return List(world.size) { i ->
            val worldPoints = world[i].map { Landmark3(it.x(), it.y(), it.z()) }
            val imagePoints = image[i].map { Landmark3(it.x(), it.y(), it.z()) }
            val category = handedness[i].firstOrNull()
            val hand = category?.categoryName()?.let { name ->
                Handedness.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            } ?: Handedness.RIGHT

            HandLandmarks(
                world = worldPoints,
                image = imagePoints,
                handedness = hand,
                handednessScore = category?.score() ?: 0f,
                timestampMs = lastTimestampMs,
                imageWidth = lastImageWidth,
                imageHeight = lastImageHeight,
            )
        }
    }

    companion object {
        const val MODEL_ASSET_PATH = "models/hand_landmarker.task"
        const val MAX_HANDS = 2
    }
}
