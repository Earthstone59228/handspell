package dev.handspell.app.core.model

/**
 * What the camera screen needs to draw the live landmark overlay: the 21 image-space landmarks
 * only, not the world landmarks the classifier uses. Same frame convention as [HandLandmarks] —
 * selfie-mirrored, display-upright — so the overlay is drawn directly onto the mirrored
 * `PreviewView` with no additional coordinate flip (docs/CLASSIFIER.md §1).
 *
 * Added to the [dev.handspell.app.vision.SignDetector] contract (docs/CONTRACTS.md §2) alongside
 * the vision/camera workstream so the camera screen can render an overlay without depending on
 * `HandLandmarks` directly.
 */
data class HandOverlay(
    val imageLandmarks: List<Landmark3>,
    val imageWidth: Int,
    val imageHeight: Int,
    val timestampMs: Long,
) {
    init {
        require(imageLandmarks.size == HandLandmarks.LANDMARK_COUNT) {
            "imageLandmarks must have ${HandLandmarks.LANDMARK_COUNT} points, got ${imageLandmarks.size}"
        }
    }
}
