package dev.handspell.app.vision.words

import java.io.InputStream

/**
 * The word-sign model as the recognizer sees it: features in, one probability per label out. One label is [OTHER],
 * which absorbs unrelated signs so a word is only accepted when it beats "something else". The model was trained on
 * mirrored copies, so left- and right-handed signing score alike and no flip is needed here.
 */
class WordClassifier(val net: WordNet) : WordScorer {

    val labels: List<String> = net.labels

    init {
        require(net.steps == WordFeatures.STEPS && net.stepDim == WordFeatures.SLOT_DIM * 2) {
            "word model expects ${WordFeatures.STEPS} steps of ${WordFeatures.SLOT_DIM * 2} floats, " +
                "file has ${net.steps} of ${net.stepDim}"
        }
    }

    fun probabilities(features: FloatArray): DoubleArray = net.probabilities(features)

    /** Probability of [gloss] for [features]. */
    override fun probabilityOf(gloss: String, features: FloatArray): Double {
        val index = labels.indexOf(gloss)
        require(index >= 0) { "unknown word $gloss" }
        return net.probabilities(features)[index]
    }

    companion object {
        const val OTHER = "other"
        const val ASSET_NAME = WordNet.ASSET_NAME

        /** The Pro-tier words have their own model, so the free words keep their accuracy. */
        const val PRO_ASSET_NAME = "classifier/words-pro-v3.bin"

        fun load(source: () -> InputStream, assetName: String = ASSET_NAME): WordClassifier =
            WordClassifier(WordNet.parse(source, assetName))
    }
}
