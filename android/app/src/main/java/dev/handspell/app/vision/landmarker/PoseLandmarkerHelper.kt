package dev.handspell.app.vision.landmarker

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * MediaPipe Pose Landmarker (lite) in LIVE_STREAM mode. Word recognition only needs three body points to anchor
 * hand positions: nose, left shoulder and right shoulder. [onPose] receives them as six floats (x, y for each, in
 * normalised image space) or null when no body was found in that frame.
 */
class PoseLandmarkerHelper(
    context: Context,
    modelAssetPath: String = MODEL_ASSET_PATH,
    private val onPose: (timestampMs: Long, anchors: FloatArray?) -> Unit,
    private val onError: (RuntimeException) -> Unit,
) {
    private val landmarker: PoseLandmarker

    init {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setModelAssetPath(modelAssetPath).setDelegate(Delegate.CPU).build(),
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setResultListener { result, _ -> onPose(result.timestampMs(), anchors(result)) }
            .setErrorListener(onError)
            .build()
        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(image: MPImage, timestampMs: Long) {
        landmarker.detectAsync(image, timestampMs)
    }

    fun close() = landmarker.close()

    private fun anchors(result: PoseLandmarkerResult): FloatArray? {
        val body = result.landmarks().firstOrNull() ?: return null
        if (body.size <= RIGHT_SHOULDER) return null
        val nose = body[NOSE]
        val left = body[LEFT_SHOULDER]
        val right = body[RIGHT_SHOULDER]
        return floatArrayOf(nose.x(), nose.y(), left.x(), left.y(), right.x(), right.y())
    }

    companion object {
        const val MODEL_ASSET_PATH = "models/pose_landmarker_lite.task"
        private const val NOSE = 0
        private const val LEFT_SHOULDER = 11
        private const val RIGHT_SHOULDER = 12
    }
}
