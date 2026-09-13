package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.LetterScore
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.vision.LetterClassifier
import java.io.InputStream
import kotlin.math.exp

/**
 * Stage 2 of docs/CLASSIFIER.md §4: the exported MLP, run as a plain matrix-vector forward pass.
 *
 * ~18k parameters is far too small to be worth a delegate, a GPU or an inference runtime — three
 * dense layers over a 66-float input take microseconds on the analyser thread — so this is the whole
 * of stage-2 inference. Arithmetic accumulates in [Double] and narrows once, which is what keeps the
 * Kotlin output inside 1e-5 of the numpy `forward()` the golden fixture was generated with.
 *
 * [Classification.nearestDistance] is [Float.NaN]: there are no exemplars here, and reporting a
 * fabricated distance would let the feedback engine's stage-1 distance gate silently pass.
 */
class MlpLetterClassifier(
    val weights: MlpWeights,
    override val modelId: String = MODEL_ID,
    assetName: String = MlpWeights.ASSET_NAME,
) : LetterClassifier {

    override val specVersion: Int = weights.specVersion

    /** Class index order is label order, which is the order the trainer used. */
    override val supportedLetters: List<Letter> = weights.labels.map { label ->
        val letter = Letter.fromNameOrNull(label.trim())
            ?: throw ClassifierAssetException.Malformed(assetName, "label '$label' is not a letter")
        if (letter.requiresMotion) {
            throw ClassifierAssetException.Malformed(
                assetName,
                "label '$label' is a motion letter, which a single-frame classifier must not report",
            )
        }
        letter
    }

    init {
        if (supportedLetters.distinct().size != supportedLetters.size) {
            throw ClassifierAssetException.Malformed(assetName, "labels contain a duplicate letter")
        }
        if (weights.inputDim != NormalizedHand.VECTOR_DIM) {
            throw ClassifierAssetException.Malformed(
                assetName,
                "input dim ${weights.inputDim} does not match ${NormalizedHand.VECTOR_DIM}",
            )
        }
    }

    override fun classify(hand: NormalizedHand, timestampMs: Long): Classification {
        val probabilities = probabilities(hand.vector)
        val ranked = supportedLetters
            .mapIndexed { index, letter -> LetterScore(letter, probabilities[index].toFloat()) }
            .sortedWith(compareByDescending<LetterScore> { it.probability }.thenBy { it.letter.ordinal })
        return Classification(ranked = ranked, nearestDistance = Float.NaN, timestampMs = timestampMs)
    }

    /** Forward pass plus softmax, in class-index order. Exposed so the golden test can assert on it. */
    fun probabilities(vector: FloatArray): DoubleArray {
        require(vector.size == weights.inputDim) {
            "expected ${weights.inputDim} inputs, got ${vector.size}"
        }
        var activations = DoubleArray(vector.size) { vector[it].toDouble() }
        for (layer in weights.layers) {
            val next = DoubleArray(layer.outDim)
            for (out in 0 until layer.outDim) {
                var sum = layer.bias[out].toDouble()
                val rowStart = out * layer.inDim
                for (i in 0 until layer.inDim) {
                    sum += layer.weights[rowStart + i].toDouble() * activations[i]
                }
                next[out] = when (layer.activation) {
                    MlpActivation.RELU -> if (sum > 0.0) sum else 0.0
                    MlpActivation.IDENTITY -> sum
                }
            }
            activations = next
        }
        return softmax(activations)
    }

    private fun softmax(logits: DoubleArray): DoubleArray {
        // Subtract the max first: the exported net is label-smoothed and never emits huge logits,
        // but a corrupt-but-CRC-valid file could, and exp() overflowing to Infinity would turn into
        // a NaN probability the feedback engine would compare against silently.
        val max = logits.max()
        var total = 0.0
        val out = DoubleArray(logits.size)
        for (i in logits.indices) {
            val value = exp(logits[i] - max)
            out[i] = value
            total += value
        }
        for (i in out.indices) out[i] /= total
        return out
    }

    companion object {
        const val MODEL_ID: String = "mlp-v1"

        /**
         * Parses [source] as HSML and wraps it.
         *
         * @throws ClassifierAssetException if the file is missing, unreadable, mis-versioned,
         *   malformed, CRC-failing, or labelled with something that is not a static letter.
         */
        fun load(source: () -> InputStream, assetName: String = MlpWeights.ASSET_NAME): MlpLetterClassifier =
            MlpLetterClassifier(MlpWeights.parse(source, assetName), assetName = assetName)
    }
}
