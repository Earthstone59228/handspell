package dev.handspell.app.vision.words

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [WordFeatures] to `training/handspell/words_features.py` through `training/testdata/words_features_golden.json`
 * (regenerate with `python -m handspell.words_golden`). The test source set already exposes that directory.
 */
class WordFeaturesGoldenTest {

    private val tolerance = 1e-4

    private val document = Json.parseToJsonElement(
        checkNotNull(javaClass.classLoader.getResourceAsStream("words_features_golden.json")).bufferedReader().readText(),
    ).jsonObject

    @Test
    fun `feature dimension matches the fixture`() {
        assertEquals(document.getValue("featureDim").jsonPrimitive.int, WordFeatures.FEATURE_DIM)
    }

    @Test
    fun `the Kotlin features reproduce the Python golden vectors`() {
        val cases = document.getValue("cases").jsonArray
        assertTrue(cases.isNotEmpty())
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj.getValue("name").jsonPrimitive.content
            val result = WordFeatures.compute(obj.getValue("frames").jsonArray.map { frame(it) })
            val expected = obj.getValue("expected")
            if (expected is JsonNull) {
                assertNull("$name: expected null", result)
                continue
            }
            assertNotNull("$name: expected a vector", result)
            check(result != null)
            val want = expected.jsonArray
            assertEquals("$name: dimension", want.size, result.size)
            for (i in 0 until want.size) assertEquals("$name[$i]", want[i].jsonPrimitive.double, result[i].toDouble(), tolerance)

            val mirrored = WordFeatures.mirror(result)
            val wantMirrored = obj.getValue("expectedMirrored").jsonArray
            for (i in 0 until wantMirrored.size) {
                assertEquals("$name mirrored[$i]", wantMirrored[i].jsonPrimitive.double, mirrored[i].toDouble(), tolerance)
            }
        }
    }

    private fun floats(array: JsonArray): FloatArray =
        FloatArray(array.size) { i ->
            val e = array[i]
            if (e is JsonNull) Float.NaN else (e as JsonPrimitive).double.toFloat()
        }

    private fun frame(element: kotlinx.serialization.json.JsonElement): WordFrame {
        val obj = element.jsonObject
        val hands = obj.getValue("hands").jsonArray.map { if (it is JsonNull) null else floats(it.jsonArray) }
        val pose = obj.getValue("pose").let { if (it is JsonNull) null else floats(it.jsonArray) }
        return WordFrame(hands, pose)
    }
}
