package dev.handspell.app.vision

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.NormalizedHand

/**
 * Turns raw landmarks into the 66-float vector both the Kotlin app and the Python trainer consume.
 *
 * There is exactly one normalisation spec (docs/CLASSIFIER.md §2) and two implementations of it —
 * this one and `training/handspell/normalize.py`. They are held identical by the golden vectors in
 * `training/testdata/normalizer_golden.json`, which both test suites read.
 *
 * Implementations must do their arithmetic in [Double] and narrow to [Float] only when writing the
 * output array, so the two languages agree to 1e-6 absolute.
 */
interface HandNormalizer {
    /** Spec version baked into reference sets and model files; must match [NormalizedHand.SPEC_VERSION]. */
    val specVersion: Int

    /** Returns null for degenerate geometry (zero-length palm axis), which callers treat as no hand. */
    fun normalize(landmarks: HandLandmarks): NormalizedHand?
}
