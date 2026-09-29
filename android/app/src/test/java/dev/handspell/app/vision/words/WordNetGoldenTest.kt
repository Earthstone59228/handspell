package dev.handspell.app.vision.words

import dev.handspell.app.vision.classify.ClassifierAssetException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [WordNet] to `training/handspell/words_net.py` through `words_net_golden.{bin,json}` (regenerate with
 * `python -m handspell.words_net_golden`). The tiny fixture model uses the real input geometry.
 */
class WordNetGoldenTest {

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing $name" }.use { it.readBytes() }

    private val bytes = resource("words_net_golden.bin")
    private val document = Json.parseToJsonElement(resource("words_net_golden.json").decodeToString()).jsonObject

    @Test
    fun `probabilities match the numpy reference`() {
        val net = WordNet.parse(bytes)
        val cases = document.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty())
        for ((index, case) in cases.withIndex()) {
            val features = case.jsonObject.getValue("features").jsonArray.map { it.jsonPrimitive.double.toFloat() }.toFloatArray()
            val expected = case.jsonObject.getValue("expected").jsonArray.map { it.jsonPrimitive.double }
            val actual = net.probabilities(features)
            assertEquals("case $index size", expected.size, actual.size)
            for (i in expected.indices) assertEquals("case $index[$i]", expected[i], actual[i], 1e-6)
            assertEquals(1.0, actual.sum(), 1e-9)
        }
    }

    @Test
    fun `labels and geometry come from the file`() {
        val net = WordNet.parse(bytes)
        assertEquals(document.getValue("labels").jsonArray.map { it.jsonPrimitive.content }, net.labels)
        assertEquals(WordFeatures.STEPS, net.steps)
        assertEquals(WordFeatures.SLOT_DIM * 2, net.stepDim)
    }

    @Test
    fun `a flipped byte fails the checksum`() {
        val corrupt = bytes.clone()
        corrupt[200] = (corrupt[200].toInt() xor 0x55).toByte()
        assertThrows(ClassifierAssetException.ChecksumMismatch::class.java) { WordNet.parse(corrupt) }
    }

    @Test
    fun `a truncated file is malformed and bad magic is refused`() {
        assertThrows(ClassifierAssetException::class.java) { WordNet.parse(bytes.copyOf(bytes.size / 2)) }
        val badMagic = bytes.clone().also { it[0] = 'X'.code.toByte() }
        assertThrows(ClassifierAssetException.Malformed::class.java) { WordNet.parse(badMagic) }
    }
}
