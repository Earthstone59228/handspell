package dev.handspell.app.core.model

/**
 * What the learner is told about the frame they are currently producing.
 *
 * Deliberately four states, not two. A false "correct" teaches a wrong sign, which is a harm to the
 * learner and a disrespect to ASL; silence is always the safer failure. [Match] is only ever emitted
 * after the hold-to-confirm window in docs/CLASSIFIER.md §6 has elapsed.
 */
sealed interface SignFeedbackState {
    /** The letter the learner was asked to produce; null on the dev capture screen. */
    val target: Letter?

    /** No hand in frame (debounced). */
    data class NoHand(override val target: Letter?) : SignFeedbackState

    /** A hand is visible but does not resemble any letter we know well enough to name. */
    data class NotRecognized(
        override val target: Letter?,
        val confidence: Float,
    ) : SignFeedbackState

    /** Close to [target] but not yet confirmable. [hint] is an authored nudge, never generated text. */
    data class Adjust(
        override val target: Letter,
        val confidence: Float,
        val hint: FeedbackHint?,
        /** 0..1, how far through the hold-to-confirm window this attempt is. */
        val holdProgress: Float,
    ) : SignFeedbackState

    /** Confirmed: held above threshold for the full confirm window. */
    data class Match(
        override val target: Letter,
        val confidence: Float,
        val heldMs: Long,
    ) : SignFeedbackState
}

/**
 * A short, human-authored correction. [id] keys into a string resource so hints are translatable and
 * never assembled from model output.
 */
data class FeedbackHint(val id: String, val confusedWith: Letter?)
