package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.NormalizedHand
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the stage-1 classifier against synthetic reference sets.
 *
 * The vectors here are not hands — they are deliberately simple points in the 66-d space, because
 * what needs pinning down is the arithmetic and the parser: the distance weighting, the neighbour
 * vote, and every way a generated CSV can be wrong. Real-hand behaviour is the evaluation
 * protocol's job (docs/CLASSIFIER.md §8), not a unit test's.
 */
class KnnLetterClassifierTest {

    private val letters = listOf(Letter.A, Letter.B, Letter.C, Letter.D)

    /** A vector that is 1.0 in one shape dimension per letter, 0 elsewhere, plus a small nudge. */
    private fun exemplar(letterIndex: Int, copy: Int): FloatArray {
        val vector = FloatArray(NormalizedHand.VECTOR_DIM)
        vector[letterIndex * 2] = 1f
        vector[letterIndex * 2 + 1] = 0.01f * copy
        return vector
    }

    private fun referenceCsv(
        rows: List<Pair<String, FloatArray>>,
        specVersion: Int = 1,
        header: String? = null,
    ): String = buildString {
        append("# spec_version=$specVersion\n")
        append(header ?: (listOf("letter") + (0 until NormalizedHand.VECTOR_DIM).map { "f$it" }).joinToString(","))
        append("\n")
        for ((letter, vector) in rows) {
            append(letter)
            for (value in vector) {
                append(',')
                append(value)
            }
            append("\n")
        }
    }

    private fun defaultRows(copies: Int = 3): List<Pair<String, FloatArray>> =
        letters.flatMapIndexed { index, letter ->
            (0 until copies).map { copy -> letter.name to exemplar(index, copy) }
        }

    private fun source(text: String): () -> InputStream =
        { ByteArrayInputStream(text.toByteArray()) }

    private fun load(text: String) = KnnLetterClassifier.load(source(text))

    @Test
    fun `a known exemplar classifies as its own letter with high probability`() {
        val classifier = load(referenceCsv(defaultRows()))

        val result = classifier.classify(NormalizedHand(exemplar(1, 0)), timestampMs = 7L)

        assertEquals(Letter.B, result.top?.letter)
        assertTrue("p was ${result.top?.probability}", (result.top?.probability ?: 0f) > 0.9f)
        assertEquals(0.0, result.nearestDistance.toDouble(), 1e-6)
        assertEquals(7L, result.timestampMs)
    }

    @Test
    fun `probabilities cover every supported letter, sum to one and descend`() {
        val classifier = load(referenceCsv(defaultRows()))

        val result = classifier.classify(NormalizedHand(exemplar(0, 1)), timestampMs = 0L)

        assertEquals(letters, result.ranked.map { it.letter })
        assertEquals(1f, result.ranked.map { it.probability }.sum(), 1e-5f)
        assertEquals(result.ranked, result.ranked.sortedByDescending { it.probability })
    }

    @Test
    fun `a far vector is rejected by distance, not by probability`() {
        val classifier = load(referenceCsv(defaultRows()))
        val nowhereNearAHand = NormalizedHand(FloatArray(NormalizedHand.VECTOR_DIM) { 3f })

        val result = classifier.classify(nowhereNearAHand, timestampMs = 0L)

        // The vote still ranks something first — it has to, the probabilities are a share of five
        // neighbours — which is exactly why the feedback engine gates on this distance.
        assertTrue(result.top!!.probability > 0f)
        assertTrue("nearest was ${result.nearestDistance}", result.nearestDistance > 0.45f)
    }

    @Test
    fun `the orientation block is weighted at one half`() {
        val zero = "A" to FloatArray(NormalizedHand.VECTOR_DIM)
        val classifier = load(referenceCsv(listOf(zero)))

        val shapeOffset = FloatArray(NormalizedHand.VECTOR_DIM).also { it[0] = 0.2f }
        val orientationOffset = FloatArray(NormalizedHand.VECTOR_DIM)
            .also { it[NormalizedHand.SHAPE_DIM] = 0.2f }

        val shapeDistance = classifier.classify(NormalizedHand(shapeOffset), 0L).nearestDistance
        val orientationDistance = classifier.classify(NormalizedHand(orientationOffset), 0L).nearestDistance

        assertEquals(0.2, shapeDistance.toDouble(), 1e-6)
        assertEquals(0.2 * sqrt(0.5), orientationDistance.toDouble(), 1e-6)
    }

    @Test
    fun `supported letters are the letters present, in alphabetical order`() {
        val rows = listOf("S" to exemplar(0, 0), "C" to exemplar(1, 0), "S" to exemplar(0, 1))
        val classifier = load(referenceCsv(rows))

        assertEquals(listOf(Letter.C, Letter.S), classifier.supportedLetters)
        assertEquals(3, classifier.exemplarCount)
        assertEquals(2, classifier.exemplarCountFor(Letter.S))
        assertEquals(0, classifier.exemplarCountFor(Letter.A))
    }

    @Test
    fun `fewer exemplars than k still votes`() {
        val classifier = load(referenceCsv(listOf("A" to exemplar(0, 0), "B" to exemplar(1, 0))))

        val result = classifier.classify(NormalizedHand(exemplar(0, 0)), 0L)

        assertEquals(Letter.A, result.top?.letter)
        assertEquals(1f, result.ranked.map { it.probability }.sum(), 1e-5f)
    }

    @Test
    fun `identifies itself for the settings screen`() {
        val classifier = load(referenceCsv(defaultRows()))
        assertEquals("knn-v1", classifier.modelId)
        assertEquals(NormalizedHand.SPEC_VERSION, classifier.specVersion)
    }

    @Test
    fun `personal exemplars can be swapped in and cleared without mutating bundled references`() {
        val bundled = load(referenceCsv(defaultRows()))
        val classifier = ReloadableKnnLetterClassifier(bundled)
        val aPose = NormalizedHand(exemplar(0, 0))

        assertEquals(Letter.A, classifier.classify(aPose, 0L).top?.letter)
        classifier.replacePersonalExemplars(mapOf(Letter.B to List(8) { aPose }))
        assertTrue(classifier.hasPersonalExemplars)
        assertEquals("knn-v1+personal-v1", classifier.modelId)
        assertEquals(Letter.B, classifier.classify(aPose, 0L).top?.letter)

        classifier.replacePersonalExemplars(emptyMap())
        assertFalse(classifier.hasPersonalExemplars)
        assertEquals("knn-v1", classifier.modelId)
        assertEquals(Letter.A, classifier.classify(aPose, 0L).top?.letter)
    }

    @Test
    fun `a reference set from another spec version is refused`() {
        val error = assertThrows(ClassifierAssetException.SpecVersionMismatch::class.java) {
            load(referenceCsv(defaultRows(), specVersion = 2))
        }
        assertEquals(2, error.found)
        assertEquals(NormalizedHand.SPEC_VERSION, error.expected)
        assertEquals("error_classifier_spec_mismatch", error.messageId)
    }

    @Test
    fun `a reference set with no spec version comment is refused`() {
        val body = referenceCsv(defaultRows()).lines().drop(1).joinToString("\n")
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) { load(body) }
        assertTrue(error.message!!.contains("spec_version"))
    }

    @Test
    fun `a wrong header is refused`() {
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            load(referenceCsv(defaultRows(), header = "letter,x0,x1"))
        }
        assertTrue(error.message!!.contains("header"))
    }

    @Test
    fun `a short row is refused`() {
        val text = referenceCsv(defaultRows()) + "A,1.0,2.0\n"
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) { load(text) }
        assertTrue(error.message!!.contains("fields"))
    }

    @Test
    fun `a non-numeric cell is refused`() {
        val text = referenceCsv(defaultRows()).replace(",1.0,", ",banana,")
        assertThrows(ClassifierAssetException.Malformed::class.java) { load(text) }
    }

    @Test
    fun `an unknown letter is refused`() {
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            load(referenceCsv(listOf("AA" to exemplar(0, 0))))
        }
        assertTrue(error.message!!.contains("unknown letter"))
    }

    @Test
    fun `a motion letter is refused`() {
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            load(referenceCsv(listOf("J" to exemplar(0, 0))))
        }
        assertTrue(error.message!!.contains("motion letter"))
    }

    @Test
    fun `an empty reference set is refused`() {
        assertThrows(ClassifierAssetException.Malformed::class.java) { load(referenceCsv(emptyList())) }
    }

    @Test
    fun `an absent asset is reported as missing, not as corrupt`() {
        val error = assertThrows(ClassifierAssetException.Missing::class.java) {
            KnnLetterClassifier.load({ throw FileNotFoundException("classifier/references-v1.csv") })
        }
        assertEquals("error_classifier_asset_missing", error.messageId)
    }

    @Test
    fun `an unreadable asset is distinguished from a missing one`() {
        val error = assertThrows(ClassifierAssetException.Unreadable::class.java) {
            KnnLetterClassifier.load({ throw IOException("device is on fire") })
        }
        assertEquals("error_classifier_asset_unreadable", error.messageId)
    }
}
