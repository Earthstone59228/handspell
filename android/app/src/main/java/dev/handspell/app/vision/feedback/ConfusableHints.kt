package dev.handspell.app.vision.feedback

import dev.handspell.app.core.model.FeedbackHint
import dev.handspell.app.core.model.Letter

/**
 * Maps "you are aiming at X but the classifier keeps seeing Y" onto one authored sentence.
 *
 * Every id below is a key in `res/values/strings_content.xml` and every sentence was written by a
 * person: docs/CLASSIFIER.md §5 is explicit that the Adjust hint names its group-mate's
 * distinguishing cue in authored copy, never generated text. The pairs are exactly the confusable
 * groups in that section, and the cue chosen for a pair is the *target's* own cue — the thing the
 * learner should change to move away from the letter they are currently producing.
 *
 * Pairs with no honest authored cue fall back to [GENERIC_HINT_ID] rather than borrowing a sentence
 * that nearly fits. H vs U is the clearest case: they differ in where the hand points (sideways vs
 * up) and no authored string says that, so saying nothing specific beats saying something wrong.
 */
object ConfusableHints {

    /** Used when the pair is not a documented confusion, or when there is no competitor at all. */
    const val GENERIC_HINT_ID: String = "hint_generic"

    private val HINT_BY_PAIR: Map<Pair<Letter, Letter>, String> = mapOf(
        // A / S / T — where the thumb sits against the fist (docs/CLASSIFIER.md §5).
        (Letter.A to Letter.S) to "hint_thumb_to_side",
        (Letter.A to Letter.T) to "hint_thumb_to_side",
        (Letter.S to Letter.A) to "hint_thumb_across_front",
        (Letter.S to Letter.T) to "hint_thumb_across_front",
        (Letter.T to Letter.A) to "hint_thumb_between_index_middle",
        (Letter.T to Letter.S) to "hint_thumb_between_index_middle",
        (Letter.T to Letter.M) to "hint_thumb_between_index_middle",
        (Letter.T to Letter.N) to "hint_thumb_between_index_middle",
        // M / N / T — how many fingers the thumb tucks under.
        (Letter.M to Letter.N) to "hint_thumb_under_three",
        (Letter.M to Letter.T) to "hint_thumb_under_three",
        (Letter.N to Letter.M) to "hint_thumb_under_two",
        (Letter.N to Letter.T) to "hint_thumb_under_two",
        // K / P and G / Q — same handshape, opposite wrist direction (sign of e1.y).
        (Letter.K to Letter.P) to "hint_point_forward",
        (Letter.P to Letter.K) to "hint_point_down",
        (Letter.G to Letter.Q) to "hint_point_forward",
        (Letter.Q to Letter.G) to "hint_point_down",
        // H / U / V — finger spread. H vs U differs only in direction, so it is not listed.
        (Letter.V to Letter.U) to "hint_spread_fingers",
        (Letter.V to Letter.H) to "hint_spread_fingers",
        (Letter.U to Letter.V) to "hint_fingers_together",
        (Letter.H to Letter.V) to "hint_fingers_together",
        // R / U — index crossed over middle vs. parallel. Only the "cross them" direction has copy.
        (Letter.R to Letter.U) to "hint_cross_fingers",
        (Letter.R to Letter.V) to "hint_cross_fingers",
        // D / F and D / X — index straight, pinched, or hooked.
        (Letter.D to Letter.F) to "hint_index_up_curled",
        (Letter.D to Letter.X) to "hint_index_up_curled",
        (Letter.F to Letter.D) to "hint_three_straight",
        (Letter.X to Letter.D) to "hint_hook_index",
    )

    /**
     * The hint to show while the learner is aiming at [target] and the classifier's runner-up is
     * [competitor]. Never null: the Adjust state always says something, even if only the generic
     * "check the shape and try again".
     *
     * [FeedbackHint.confusedWith] is set only when the returned copy is actually about that letter,
     * so the UI can say "looks like S" without ever attaching a letter to generic copy.
     */
    fun hintFor(target: Letter, competitor: Letter?): FeedbackHint {
        if (competitor == null || competitor == target) {
            return FeedbackHint(GENERIC_HINT_ID, confusedWith = null)
        }
        val hintId = HINT_BY_PAIR[target to competitor]
            ?: return FeedbackHint(GENERIC_HINT_ID, confusedWith = null)
        return FeedbackHint(hintId, confusedWith = competitor)
    }

    /** Every hint id this object can return; the test asserts each one exists in strings_content.xml. */
    fun hintIds(): Set<String> = HINT_BY_PAIR.values.toSet() + GENERIC_HINT_ID
}
