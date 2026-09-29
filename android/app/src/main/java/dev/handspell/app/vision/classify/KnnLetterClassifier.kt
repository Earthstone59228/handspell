package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.LetterScore
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.vision.LetterClassifier
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.sqrt

/**
 * Stage 1 of docs/CLASSIFIER.md §3: k-nearest-neighbour over the recorded reference exemplars in
 * `assets/classifier/references-v1.csv`.
 *
 * Distance is Euclidean in the 66-d normalised space with the orientation block's squared
 * differences weighted [ORIENTATION_WEIGHT] — that block is noisier and less discriminative than
 * the shape block but cannot be dropped, because K/P and G/Q differ in nothing else. The [K]
 * nearest exemplars each vote with weight `1 / (d + `[DISTANCE_EPSILON]`)`; votes are summed per
 * letter and normalised to 1, so the reported probabilities are a distance-weighted neighbour
 * share, not a calibrated likelihood. [Classification.nearestDistance] carries the raw distance to
 * the single closest exemplar, which is what `DefaultFeedbackEngine` gates on: a pose can be the
 * nearest thing to "A" in the set and still be nowhere near any real hand.
 *
 * A per-letter centroid would be smaller and faster and is the wrong shape for the data — G and O
 * have genuinely multi-modal exemplar clouds across signers, and averaging two valid poses produces
 * an invalid one.
 *
 * Instances are immutable and [classify] keeps its scratch arrays local to the call, so a single
 * instance can be shared across the analyser thread and the UI.
 */
class KnnLetterClassifier private constructor(
    private val assetName: String,
    /** Exemplar vectors, flattened: exemplar `e` occupies `[e * VECTOR_DIM, (e + 1) * VECTOR_DIM)`. */
    private val exemplars: FloatArray,
    /** Per exemplar, an index into [supportedLetters]. */
    private val exemplarLetters: IntArray,
    override val supportedLetters: List<Letter>,
    private val k: Int,
    private val classifierModelId: String = MODEL_ID,
) : LetterClassifier {

    override val modelId: String = classifierModelId

    override val specVersion: Int = NormalizedHand.SPEC_VERSION

    /** How many exemplars back this classifier; shown on the settings screen next to [modelId]. */
    val exemplarCount: Int get() = exemplarLetters.size

    /** How many exemplars the reference set holds for [letter]; 0 for an unsupported letter. */
    fun exemplarCountFor(letter: Letter): Int {
        val letterIndex = supportedLetters.indexOf(letter)
        if (letterIndex < 0) return 0
        return exemplarLetters.count { it == letterIndex }
    }

    /**
     * Returns a separate immutable classifier with the user's local examples appended. The bundled
     * reference arrays are never mutated, so an analyser can finish a frame against the old model
     * while a calibration screen prepares a replacement. Personal examples are intentionally not
     * written into an asset or shared outside this device.
     */
    fun withPersonalExemplars(personal: Map<Letter, List<NormalizedHand>>): KnnLetterClassifier {
        val accepted = personal
            .filterKeys { it in supportedLetters && !it.requiresMotion }
            .toSortedMap(compareBy { it.ordinal })
        val additionalCount = accepted.values.sumOf(List<NormalizedHand>::size)
        if (additionalCount == 0) return this

        require(accepted.values.all { hands -> hands.size in 8..24 }) {
            "personal calibration needs 8..24 exemplars per letter"
        }
        require(accepted.values.flatten().all { hand ->
            hand.vector.size == NormalizedHand.VECTOR_DIM && hand.vector.all(Float::isFinite)
        }) { "personal exemplars must be finite ${NormalizedHand.VECTOR_DIM}-float vectors" }

        val combinedVectors = FloatArray(exemplars.size + additionalCount * NormalizedHand.VECTOR_DIM)
        exemplars.copyInto(combinedVectors)
        val combinedLetters = IntArray(exemplarLetters.size + additionalCount)
        exemplarLetters.copyInto(combinedLetters)
        var exemplar = exemplarLetters.size
        for ((letter, hands) in accepted) {
            val index = supportedLetters.indexOf(letter)
            for (hand in hands) {
                hand.vector.copyInto(combinedVectors, exemplar * NormalizedHand.VECTOR_DIM)
                combinedLetters[exemplar] = index
                exemplar++
            }
        }
        return KnnLetterClassifier(
            assetName = assetName,
            exemplars = combinedVectors,
            exemplarLetters = combinedLetters,
            supportedLetters = supportedLetters,
            k = k,
            classifierModelId = "$MODEL_ID+personal-v1",
        )
    }

    override fun classify(hand: NormalizedHand, timestampMs: Long): Classification {
        val neighbourDistances = DoubleArray(k) { Double.MAX_VALUE }
        val neighbourLetters = IntArray(k) { -1 }
        var nearestShapeDistance = Double.MAX_VALUE
        var filled = 0
        var worstSquaredUpperBound = Double.POSITIVE_INFINITY

        val vector = hand.vector
        exemplars@ for (exemplar in exemplarLetters.indices) {
            val base = exemplar * NormalizedHand.VECTOR_DIM
            var shapeSum = 0.0
            // Fixed ranges retain the original accumulation order and allow loop optimization.
            for (i in 0 until NormalizedHand.SHAPE_DIM / 2) {
                val delta = vector[i].toDouble() - exemplars[base + i]
                shapeSum += delta * delta
            }
            // Remaining squared differences are nonnegative. This reference cannot enter
            // the neighbour list once even its partial sum exceeds the conservative bound.
            if (shapeSum > worstSquaredUpperBound) continue@exemplars
            for (i in NormalizedHand.SHAPE_DIM / 2 until NormalizedHand.SHAPE_DIM) {
                val delta = vector[i].toDouble() - exemplars[base + i]
                shapeSum += delta * delta
            }
            if (shapeSum > worstSquaredUpperBound) continue
            var orientationSum = 0.0
            for (i in NormalizedHand.SHAPE_DIM until NormalizedHand.VECTOR_DIM) {
                val delta = vector[i].toDouble() - exemplars[base + i]
                orientationSum += delta * delta
            }
            val squaredDistance = shapeSum + ORIENTATION_WEIGHT * orientationSum
            if (squaredDistance > worstSquaredUpperBound) continue
            val distance = sqrt(squaredDistance)
            // Insertion into a k-long ascending list: k is 5, so this beats sorting every exemplar.
            if (filled < k || distance < neighbourDistances[k - 1]) {
                var slot = if (filled < k) filled else k - 1
                while (slot > 0 && neighbourDistances[slot - 1] > distance) {
                    neighbourDistances[slot] = neighbourDistances[slot - 1]
                    neighbourLetters[slot] = neighbourLetters[slot - 1]
                    slot--
                }
                neighbourDistances[slot] = distance
                if (slot == 0) nearestShapeDistance = sqrt(shapeSum)
                neighbourLetters[slot] = exemplarLetters[exemplar]
                if (filled < k) filled++
                if (filled == k) {
                    // A square-root can round distinct sums to the same distance. Round upward
                    // before and after squaring so pruning never changes the original tie rule.
                    val upperDistance = Math.nextUp(neighbourDistances[k - 1])
                    worstSquaredUpperBound = Math.nextUp(upperDistance * upperDistance)
                }
            }
        }

        val weightByLetter = DoubleArray(supportedLetters.size)
        var totalWeight = 0.0
        for (neighbour in 0 until filled) {
            val weight = 1.0 / (neighbourDistances[neighbour] + DISTANCE_EPSILON)
            weightByLetter[neighbourLetters[neighbour]] += weight
            totalWeight += weight
        }

        val ranked = supportedLetters
            .mapIndexed { index, letter ->
                LetterScore(letter, (weightByLetter[index] / totalWeight).toFloat())
            }
            .sortedWith(compareByDescending<LetterScore> { it.probability }.thenBy { it.letter.ordinal })

        return Classification(
            ranked = ranked,
            nearestDistance = neighbourDistances[0].toFloat(),
            nearestShapeDistance = nearestShapeDistance.toFloat(),
            timestampMs = timestampMs,
        )
    }

    companion object {
        const val MODEL_ID: String = "knn-v1"

        /** Path inside `assets/`; built by `training/scripts/build_references.py`, never by hand. */
        const val ASSET_NAME: String = "classifier/references-v1.csv"

        /** Neighbours polled per frame (docs/CLASSIFIER.md §6). */
        const val K: Int = 5

        /** Weight applied to the orientation block's squared differences. */
        const val ORIENTATION_WEIGHT: Double = 0.5

        /** Keeps an exact-match exemplar's vote finite instead of infinite. */
        const val DISTANCE_EPSILON: Double = 0.01

        private const val SPEC_VERSION_KEY = "spec_version"
        private const val COMMENT_PREFIX = "#"
        private const val LETTER_COLUMN = "letter"

        /**
         * Parses the reference CSV from [source] and closes the stream.
         *
         * [source] is a supplier rather than a stream so the caller decides where the bytes come
         * from — `AssetManager.open(ASSET_NAME)` in the app, a string in tests — and so a failure
         * to open is caught and typed here rather than at every call site.
         *
         * @throws ClassifierAssetException for a missing, unreadable, mis-versioned or malformed
         *   reference set. Callers surface it as `DetectorStatus.Failed(messageId, cause)`; see
         *   `assets/classifier/README.md` for the required behaviour when the file is absent.
         */
        fun load(
            source: () -> InputStream,
            assetName: String = ASSET_NAME,
            k: Int = K,
        ): KnnLetterClassifier {
            require(k >= 1) { "k must be at least 1, got $k" }
            val lines = readLines(source, assetName)
            return parse(lines, assetName, k)
        }

        private fun readLines(source: () -> InputStream, assetName: String): List<String> =
            try {
                source().use { stream ->
                    stream.bufferedReader().use(BufferedReader::readLines)
                }
            } catch (e: FileNotFoundException) {
                throw ClassifierAssetException.Missing(assetName, e)
            } catch (e: IOException) {
                throw ClassifierAssetException.Unreadable(assetName, e)
            }

        private fun parse(lines: List<String>, assetName: String, k: Int): KnnLetterClassifier {
            var declaredSpecVersion: Int? = null
            var headerSeen = false
            val letterOfRow = ArrayList<Letter>()
            val vectors = ArrayList<FloatArray>()

            for ((index, raw) in lines.withIndex()) {
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith(COMMENT_PREFIX)) {
                    val version = specVersionOrNull(line)
                    if (version != null) declaredSpecVersion = version
                    continue
                }
                if (!headerSeen) {
                    requireSpecVersion(declaredSpecVersion, assetName)
                    checkHeader(line, assetName, index)
                    headerSeen = true
                    continue
                }
                val (letter, vector) = parseRow(line, assetName, index)
                letterOfRow.add(letter)
                vectors.add(vector)
            }

            if (!headerSeen) {
                requireSpecVersion(declaredSpecVersion, assetName)
                throw ClassifierAssetException.Malformed(assetName, "no header row")
            }
            if (vectors.isEmpty()) {
                throw ClassifierAssetException.Malformed(assetName, "no exemplar rows")
            }

            // Supported letters are exactly the letters present, in enum order so every screen
            // built from them is in alphabetical order without sorting again.
            val supportedLetters = letterOfRow.distinct().sortedBy { it.ordinal }
            val letterIndex = supportedLetters.withIndex().associate { (i, letter) -> letter to i }
            val flattened = FloatArray(vectors.size * NormalizedHand.VECTOR_DIM)
            for ((row, vector) in vectors.withIndex()) {
                vector.copyInto(flattened, row * NormalizedHand.VECTOR_DIM)
            }

            return KnnLetterClassifier(
                assetName = assetName,
                exemplars = flattened,
                exemplarLetters = IntArray(letterOfRow.size) { letterIndex.getValue(letterOfRow[it]) },
                supportedLetters = supportedLetters,
                k = k,
            )
        }

        private fun specVersionOrNull(commentLine: String): Int? {
            val body = commentLine.removePrefix(COMMENT_PREFIX).trim()
            if (!body.startsWith(SPEC_VERSION_KEY)) return null
            val value = body.substringAfter('=', missingDelimiterValue = "").trim()
            return value.toIntOrNull() ?: -1
        }

        private fun requireSpecVersion(declared: Int?, assetName: String) {
            if (declared == null) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "no '# $SPEC_VERSION_KEY=' comment before the header row",
                )
            }
            if (declared != NormalizedHand.SPEC_VERSION) {
                throw ClassifierAssetException.SpecVersionMismatch(
                    assetName,
                    expected = NormalizedHand.SPEC_VERSION,
                    found = declared,
                )
            }
        }

        private fun checkHeader(line: String, assetName: String, lineIndex: Int) {
            val columns = line.split(',')
            val expectedSize = NormalizedHand.VECTOR_DIM + 1
            if (columns.size != expectedSize) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "header on line ${lineIndex + 1} has ${columns.size} columns, expected $expectedSize",
                )
            }
            if (columns[0].trim() != LETTER_COLUMN) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "header column 0 is '${columns[0].trim()}', expected '$LETTER_COLUMN'",
                )
            }
            for (i in 0 until NormalizedHand.VECTOR_DIM) {
                val expected = "f$i"
                if (columns[i + 1].trim() != expected) {
                    throw ClassifierAssetException.Malformed(
                        assetName,
                        "header column ${i + 1} is '${columns[i + 1].trim()}', expected '$expected'",
                    )
                }
            }
        }

        private fun parseRow(line: String, assetName: String, lineIndex: Int): Pair<Letter, FloatArray> {
            val fields = line.split(',')
            val expectedSize = NormalizedHand.VECTOR_DIM + 1
            if (fields.size != expectedSize) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "line ${lineIndex + 1} has ${fields.size} fields, expected $expectedSize",
                )
            }
            val name = fields[0].trim()
            val letter = Letter.fromNameOrNull(name)
                ?: throw ClassifierAssetException.Malformed(
                    assetName,
                    "line ${lineIndex + 1} names unknown letter '$name'",
                )
            if (letter.requiresMotion) {
                // A single normalised frame cannot represent J or Z; such a row means the capture
                // screen or the build script produced something this classifier must not score.
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "line ${lineIndex + 1} holds motion letter '$name', which has no static exemplar",
                )
            }
            val vector = FloatArray(NormalizedHand.VECTOR_DIM)
            for (i in 0 until NormalizedHand.VECTOR_DIM) {
                val text = fields[i + 1].trim()
                val value = text.toFloatOrNull()
                if (value == null || !value.isFinite()) {
                    throw ClassifierAssetException.Malformed(
                        assetName,
                        "line ${lineIndex + 1} column ${i + 1} is '$text', expected a finite float",
                    )
                }
                vector[i] = value
            }
            return letter to vector
        }
    }

    override fun toString(): String = "KnnLetterClassifier($assetName, $exemplarCount exemplars, k=$k)"
}
