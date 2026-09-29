package dev.handspell.app.vision.words

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shipped `classifier/words-v3.bin` gives the numpy reference's probabilities on real feature vectors. */
class WordNetAssetTest {

    private fun resource(name: String) = checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing $name" }

    @Test
    fun `the shipped model matches the numpy reference`() {
        val classifier = WordClassifier.load({ resource("words-v3.bin") })
        val document = Json.parseToJsonElement(resource("words_v3_parity.json").use { it.readBytes().decodeToString() }).jsonObject
        assertEquals(document.getValue("labels").jsonArray.map { it.jsonPrimitive.content }, classifier.labels)
        assertTrue(WordClassifier.OTHER in classifier.labels)
        val cases = document.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty())
        for ((index, case) in cases.withIndex()) {
            val features = case.jsonObject.getValue("features").jsonArray.map { it.jsonPrimitive.double.toFloat() }.toFloatArray()
            val expected = case.jsonObject.getValue("expected").jsonArray.map { it.jsonPrimitive.double }
            val actual = classifier.probabilities(features)
            for (i in expected.indices) assertEquals("case $index[$i]", expected[i], actual[i], 1e-4)
        }
    }
}
