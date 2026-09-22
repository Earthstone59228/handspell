package dev.handspell.app.core.model

/**
 * A classifier reference pose reconstructed in canonical hand space for the compact teaching
 * guide. It contains no camera frame or biometric capture metadata and is not used for scoring.
 */
data class CanonicalHandshape(
    val letter: Letter,
    val landmarks: List<Landmark3>,
) {
    init {
        require(landmarks.size == HandLandmarks.LANDMARK_COUNT) {
            "Canonical handshape needs ${HandLandmarks.LANDMARK_COUNT} landmarks"
        }
    }
}
