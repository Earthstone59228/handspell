package dev.handspell.app.core.model

/**
 * Which physical hand the landmarker reports. The pipeline feeds MediaPipe a selfie-mirrored,
 * display-upright image (docs/CLASSIFIER.md §1), which is the orientation its handedness head
 * assumes, so this label is the user's actual hand and needs no correction.
 */
enum class Handedness { LEFT, RIGHT }

/**
 * One landmark in MediaPipe's hand world-landmark frame: metres, origin at the hand's approximate
 * geometric centre, axes aligned with the selfie-mirrored, display-upright image —
 * +x toward the right of the screen, +y down the screen, +z toward the camera.
 */
data class Landmark3(val x: Float, val y: Float, val z: Float)

/**
 * One hand as delivered by MediaPipe Hand Landmarker for a single frame.
 *
 * [world] is the input to [dev.handspell.app.vision.HandNormalizer]. [image] is only used to draw
 * the overlay: normalised image coordinates in [0,1] with z relative to the wrist.
 */
data class HandLandmarks(
    /** 21 points, MediaPipe canonical order (0 = wrist, 4 = thumb tip, 8 = index tip, ... 20 = pinky tip). */
    val world: List<Landmark3>,
    /** 21 points in normalised image space, same ordering as [world]. */
    val image: List<Landmark3>,
    val handedness: Handedness,
    /** Landmarker's confidence in [handedness], 0..1. */
    val handednessScore: Float,
    /** Frame presentation time in milliseconds, monotonic (SystemClock.uptimeMillis). */
    val timestampMs: Long,
    /** Size of the analysed frame after rotation correction, in pixels. */
    val imageWidth: Int,
    val imageHeight: Int,
) {
    init {
        require(world.size == LANDMARK_COUNT) { "world must have $LANDMARK_COUNT points, got ${world.size}" }
        require(image.size == LANDMARK_COUNT) { "image must have $LANDMARK_COUNT points, got ${image.size}" }
    }

    companion object {
        const val LANDMARK_COUNT = 21

        // Indices used by the normalisation spec; see docs/CLASSIFIER.md.
        const val WRIST = 0
        const val INDEX_MCP = 5
        const val MIDDLE_MCP = 9
        const val PINKY_MCP = 17
    }
}
