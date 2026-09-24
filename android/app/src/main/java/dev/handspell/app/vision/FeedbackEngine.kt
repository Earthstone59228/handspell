package dev.handspell.app.vision

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState

/**
 * Temporal smoothing and hold-to-confirm. Pure logic: no Android types, no coroutines, fully unit
 * testable by feeding a synthetic sequence of [Classification]s.
 *
 * Call [onFrame] for every frame with a hand and [onNoHand] for every frame without one; the engine
 * owns all state, including the EMA buffer and the confirm timer.
 */
interface FeedbackEngine {
    /** Changing the target resets all smoothing state so a held pose cannot carry over. */
    fun setTarget(target: Letter?)

    fun onFrame(classification: Classification): SignFeedbackState

    fun onNoHand(timestampMs: Long): SignFeedbackState

    fun reset()
}

/**
 * Tuning constants for [FeedbackEngine]. Defaults are the calibrated values from
 * docs/CLASSIFIER.md §6; they live here rather than as literals in the engine so the evaluation
 * harness can sweep them.
 */
data class FeedbackThresholds(
    /** Smoothed probability of the target required before a match can begin. */
    val matchProbability: Float = 0.85f,
    /** Smoothed probability of the target required to show "keep adjusting". */
    val adjustProbability: Float = 0.45f,
    /** Required gap between the target and the runner-up letter. */
    val matchMargin: Float = 0.20f,
    /** Stage-1 only: nearest-exemplar distance above which nothing is named. */
    val rejectDistance: Float = 0.45f,
    /** Stage-1 only: nearest-exemplar distance a match must be within. */
    val matchDistance: Float = 0.32f,
    /** Exponential moving average weight applied to each new probability vector. */
    val emaAlpha: Float = 0.35f,
    /** Continuous time above [matchProbability] before a match is shown. */
    val holdToConfirmMs: Long = 400L,
    /** Match stays latched this long after conditions lapse, to stop flicker. */
    val matchLatchMs: Long = 800L,
    /** Consecutive hand-free frames before reporting NoHand. */
    val noHandFrames: Int = 3,
    // --- Hysteresis (Schmitt-trigger style): it takes more to start a match than to keep one. ---
    // Each band is how much looser the "keep going" condition is than the matching "start" condition, so
    // sweeping the start thresholds keeps the band intact.
    /** An established hold only needs `matchProbability - probabilityBand`. */
    val probabilityBand: Float = 0.13f,
    /** An established hold only needs `matchMargin - marginBand` over the runner-up. */
    val marginBand: Float = 0.10f,
    /** Stage-1 only: an established hold may drift out to `matchDistance + distanceBand`. */
    val distanceBand: Float = 0.06f,
    /** A hold survives up to this long without meeting even the exit conditions (one noisy frame), frozen. */
    val holdGraceMs: Long = 150L,
    /** Once "close, keep adjusting" is showing it stays at least this long before dropping to "not recognised". */
    val adjustDropDelayMs: Long = 300L,
)
