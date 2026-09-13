package dev.handspell.app.vision

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.NormalizedHand

/**
 * Scores a normalised hand against the letters this build ships.
 *
 * Two implementations land behind this interface: the stage-1 k-NN over self-recorded reference
 * exemplars, and the stage-2 MLP whose weights are read from a flat float array in assets. Swapping
 * them is a one-line change in the app container.
 */
interface LetterClassifier {
    /** Identifies the weights/exemplars in use, e.g. "knn-v1" or "mlp-v2"; shown on the settings screen. */
    val modelId: String

    /** Normalisation spec the model was built against; the app refuses to load a mismatch. */
    val specVersion: Int

    /**
     * Letters this model is allowed to report. Letters that failed the evaluation gate in
     * docs/CLASSIFIER.md §8 are absent, and the drill list is built from this set.
     */
    val supportedLetters: List<Letter>

    fun classify(hand: NormalizedHand, timestampMs: Long): Classification
}
