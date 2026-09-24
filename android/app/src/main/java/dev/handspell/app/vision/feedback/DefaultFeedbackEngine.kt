package dev.handspell.app.vision.feedback

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.vision.FeedbackEngine
import dev.handspell.app.vision.FeedbackThresholds

/**
 * The hold-to-confirm state machine in docs/CLASSIFIER.md §6.
 *
 * Everything here exists to make a false `Match` hard to produce, because a false match teaches a
 * wrong sign (docs/DESIGN.md §3). Four independent gates must agree before the drill advances:
 * the smoothed probability of the target, its margin over the runner-up, the stage-1 distance to
 * the nearest real exemplar, and time — the conditions have to hold *continuously* for
 * [FeedbackThresholds.holdToConfirmMs], so no single lucky frame can confirm anything. The one gate
 * that works the other way is the latch: once confirmed, the match survives a dip for
 * [FeedbackThresholds.matchLatchMs] so the badge does not flicker while the learner is still
 * holding the pose.
 *
 * Pure Kotlin by contract — no Android types, no coroutines, no clock. Every time comes from
 * [Classification.timestampMs] or the [onNoHand] argument, which is what makes a 400 ms hold window
 * testable in a few microseconds.
 *
 * Not thread-safe: the detector calls it from one analyser thread.
 */
class DefaultFeedbackEngine(
    private val thresholds: FeedbackThresholds = FeedbackThresholds(),
) : FeedbackEngine {

    init {
        require(thresholds.emaAlpha > 0f && thresholds.emaAlpha <= 1f) {
            "emaAlpha must be in (0, 1], got ${thresholds.emaAlpha}"
        }
        require(thresholds.holdToConfirmMs > 0L) {
            "holdToConfirmMs must be positive, got ${thresholds.holdToConfirmMs}"
        }
        require(thresholds.noHandFrames >= 1) {
            "noHandFrames must be at least 1, got ${thresholds.noHandFrames}"
        }
        require(
            thresholds.probabilityBand >= 0f && thresholds.marginBand >= 0f && thresholds.distanceBand >= 0f &&
                thresholds.holdGraceMs >= 0L && thresholds.adjustDropDelayMs >= 0L,
        ) { "hysteresis bands and delays must not be negative" }
    }

    /** Smoothed probability per [Letter.ordinal]; letters the classifier never reports stay at 0. */
    private val smoothed = FloatArray(Letter.entries.size)

    /** Letters the last frame's classifier actually scored, so an unknown target cannot rank. */
    private val known = BooleanArray(Letter.entries.size)

    private var target: Letter? = null
    private var seenFrame = false
    private var handFreeFrames = 0

    /** Timestamp of the first frame of the current unbroken run of match conditions. */
    private var holdStartMs: Long? = null

    /** Timestamp the current match was confirmed at; the latch runs from here. */
    private var matchedAtMs: Long? = null

    /** How long the pose was actually held when the conditions last held; frozen during a dip. */
    private var heldMs = 0L

    /** Last frame on which the hold conditions (enter or exit band, as applicable) were met. */
    private var lastMetMs: Long? = null

    /** Last time a genuine (not held-over) Adjust was produced; delays dropping to NotRecognized. */
    private var lastAdjustMs: Long? = null

    private var lastState: SignFeedbackState? = null

    override fun setTarget(target: Letter?) {
        // A pose held across a prompt change must not carry credit forward, so this drops the EMA,
        // the hold timer and the latch, not just the letter.
        reset()
        this.target = target
    }

    override fun reset() {
        smoothed.fill(0f)
        known.fill(false)
        seenFrame = false
        handFreeFrames = 0
        holdStartMs = null
        matchedAtMs = null
        heldMs = 0L
        lastMetMs = null
        lastAdjustMs = null
        lastState = null
    }

    override fun onFrame(classification: Classification): SignFeedbackState {
        handFreeFrames = 0
        updateSmoothed(classification)

        val target = this.target
            // No target means the dev capture screen, which reads the classification itself; there
            // is nothing to confirm, so the engine only reports that a hand is present.
            ?: return emit(SignFeedbackState.NotRecognized(null, topProbability()))

        val now = classification.timestampMs
        val targetProbability = smoothed[target.ordinal]
        val runnerUp = runnerUpTo(target)
        val runnerUpProbability = runnerUp?.let { smoothed[it.ordinal] } ?: 0f
        val distance = classification.nearestDistance
        val isStageOne = !distance.isNaN()
        val acceptanceDistance = if (target in DIRECTION_AGNOSTIC_LETTERS) {
            classification.nearestShapeDistance
        } else {
            distance
        }

        // Hysteresis: starting a hold needs the strict "enter" band; an established hold (or latched match) only has
        // to stay inside the looser "exit" band, so a score hovering around the threshold cannot make it flicker.
        val engaged = holdStartMs != null || matchedAtMs != null
        val probabilityNeeded = thresholds.matchProbability - if (engaged) thresholds.probabilityBand else 0f
        val marginNeeded = thresholds.matchMargin - if (engaged) thresholds.marginBand else 0f
        val distanceAllowed = thresholds.matchDistance + if (engaged) thresholds.distanceBand else 0f
        val matchConditionsMet = known[target.ordinal] &&
            targetProbability >= probabilityNeeded &&
            targetProbability - runnerUpProbability >= marginNeeded &&
            (!isStageOne || acceptanceDistance <= distanceAllowed)

        matchedAtMs?.let { matchedAt ->
            if (now - matchedAt < thresholds.matchLatchMs) {
                if (matchConditionsMet) {
                    lastMetMs = now
                    holdStartMs?.let { heldMs = now - it }
                }
                return emit(SignFeedbackState.Match(target, targetProbability, heldMs))
            }
            // Latch expired: fall through and judge this frame on its own merits, which may
            // immediately re-confirm if the learner is still holding the pose.
            matchedAtMs = null
        }

        if (matchConditionsMet) {
            lastMetMs = now
            val holdStart = holdStartMs ?: now.also { holdStartMs = it }
            val elapsed = now - holdStart
            if (elapsed >= thresholds.holdToConfirmMs) {
                matchedAtMs = now
                heldMs = elapsed
                return emit(SignFeedbackState.Match(target, targetProbability, elapsed))
            }
            lastAdjustMs = now
            return emit(
                SignFeedbackState.Adjust(
                    target = target,
                    confidence = targetProbability,
                    hint = ConfusableHints.hintFor(target, runnerUp),
                    holdProgress = holdProgress(elapsed),
                ),
            )
        }

        // One noisy frame must not throw away a hold that was going well: freeze its progress for a moment.
        val holdStart = holdStartMs
        val lastMet = lastMetMs
        if (holdStart != null && lastMet != null && now - lastMet <= thresholds.holdGraceMs) {
            lastAdjustMs = now
            return emit(
                SignFeedbackState.Adjust(
                    target = target,
                    confidence = targetProbability,
                    hint = ConfusableHints.hintFor(target, runnerUp),
                    holdProgress = holdProgress(lastMet - holdStart),
                ),
            )
        }

        // Any failed condition (beyond the grace above) breaks continuity; the next qualifying frame starts fresh.
        holdStartMs = null

        if (isStageOne && acceptanceDistance > thresholds.rejectDistance) {
            // Nothing in the reference set is close to this hand, so no letter gets named no matter
            // what the weighted neighbour vote says.
            return emit(notRecognized(target, targetProbability, runnerUp, now))
        }

        val worthNaming = known[target.ordinal] &&
            (targetProbability >= thresholds.adjustProbability || isInTopThree(target))
        return if (worthNaming) {
            lastAdjustMs = now
            emit(
                SignFeedbackState.Adjust(
                    target = target,
                    confidence = targetProbability,
                    hint = ConfusableHints.hintFor(target, runnerUp),
                    holdProgress = 0f,
                ),
            )
        } else {
            emit(notRecognized(target, targetProbability, runnerUp, now))
        }
    }

    /**
     * "Not recognised" only replaces "keep adjusting" once the latter has been on screen for
     * [FeedbackThresholds.adjustDropDelayMs]; until then the badge stays on Adjust (progress at zero), so a
     * hand wobbling around the boundary does not strobe between the two messages.
     */
    private fun notRecognized(target: Letter, probability: Float, runnerUp: Letter?, now: Long): SignFeedbackState {
        val lastAdjust = lastAdjustMs
        if (lastState is SignFeedbackState.Adjust && lastAdjust != null && now - lastAdjust < thresholds.adjustDropDelayMs) {
            return SignFeedbackState.Adjust(
                target = target,
                confidence = probability,
                hint = ConfusableHints.hintFor(target, runnerUp),
                holdProgress = 0f,
            )
        }
        return SignFeedbackState.NotRecognized(target, probability)
    }

    override fun onNoHand(timestampMs: Long): SignFeedbackState {
        if (handFreeFrames < thresholds.noHandFrames) handFreeFrames++

        val previous = lastState
        val latchHolds = matchedAtMs?.let { timestampMs - it < thresholds.matchLatchMs } ?: false
        val staleMatch = previous is SignFeedbackState.Match && !latchHolds
        if (handFreeFrames < thresholds.noHandFrames && previous != null && !staleMatch) {
            // Debounce: one or two frames without landmarks is usually the landmarker blinking, not
            // the learner lowering their hand, so keep showing what the last real frame decided.
            return previous
        }

        val target = this.target
        reset()
        this.target = target
        handFreeFrames = thresholds.noHandFrames
        return emit(SignFeedbackState.NoHand(target))
    }

    private fun updateSmoothed(classification: Classification) {
        known.fill(false)
        val incoming = FloatArray(Letter.entries.size)
        for (score in classification.ranked) {
            incoming[score.letter.ordinal] = score.probability
            known[score.letter.ordinal] = true
        }
        if (!seenFrame) {
            // Nothing to average against yet; seeding with the raw frame avoids an artificial ramp
            // that would delay the first honest Match by several frames.
            incoming.copyInto(smoothed)
            seenFrame = true
            return
        }
        val alpha = thresholds.emaAlpha
        for (i in smoothed.indices) {
            smoothed[i] = alpha * incoming[i] + (1f - alpha) * smoothed[i]
        }
    }

    private fun runnerUpTo(target: Letter): Letter? {
        var best: Letter? = null
        var bestProbability = -1f
        for (letter in Letter.entries) {
            if (letter == target || !known[letter.ordinal]) continue
            val probability = smoothed[letter.ordinal]
            if (probability > bestProbability) {
                best = letter
                bestProbability = probability
            }
        }
        return best
    }

    private fun isInTopThree(target: Letter): Boolean {
        val targetProbability = smoothed[target.ordinal]
        var better = 0
        for (letter in Letter.entries) {
            if (letter == target || !known[letter.ordinal]) continue
            val probability = smoothed[letter.ordinal]
            // Ties are broken by enum order so the answer does not depend on iteration luck.
            if (probability > targetProbability ||
                (probability == targetProbability && letter.ordinal < target.ordinal)
            ) {
                better++
                if (better >= TOP_N) return false
            }
        }
        return true
    }

    private fun topProbability(): Float {
        var top = 0f
        for (letter in Letter.entries) {
            if (known[letter.ordinal] && smoothed[letter.ordinal] > top) top = smoothed[letter.ordinal]
        }
        return top
    }

    private fun holdProgress(elapsedMs: Long): Float =
        (elapsedMs.toFloat() / thresholds.holdToConfirmMs.toFloat()).coerceIn(0f, 1f)

    private fun emit(state: SignFeedbackState): SignFeedbackState {
        lastState = state
        return state
    }

    private companion object {
        /** "Target is in the top 3" from docs/CLASSIFIER.md §6. */
        const val TOP_N = 3
        val DIRECTION_AGNOSTIC_LETTERS = setOf(Letter.A, Letter.B, Letter.C, Letter.D)
    }
}
