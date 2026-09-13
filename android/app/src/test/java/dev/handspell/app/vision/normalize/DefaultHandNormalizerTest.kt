package dev.handspell.app.vision.normalize

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Handedness
import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.NormalizedHand
import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Kotlin normaliser to the Python reference via `training/testdata/normalizer_golden.json`,
 * the same fixture `training/tests/test_normalize.py` asserts from the other side. The Gradle test
 * source set points its resources directory at `training/testdata` (app/build.gradle.kts), so the
 * file is available as a classpath root resource.
 */
class DefaultHandNormalizerTest {

    private val tolerance = 1e-6

    @Test
    fun `the Kotlin normalizer reproduces the Python golden vectors`() {
        val normalizer = DefaultHandNormalizer()
        val cases = cases()
        assertTrue(cases.isNotEmpty())

        for (case in cases) {
            val result = normalizer.normalize(toHandLandmarks(case))
            if (case.expected == null) {
                assertNull("${case.name}: expected degenerate -> null", result)
            } else {
                val vector = requireNotNull(result) { "${case.name}: expected a vector" }.vector
                assertEquals("${case.name}: vector dimension", NormalizedHand.VECTOR_DIM, vector.size)
                assertEquals("${case.name}: fixture dimension", case.expected.size, vector.size)
                for (i in case.expected.indices) {
                    assertEquals(
                        "${case.name}:[$i]",
                        case.expected[i],
                        vector[i].toDouble(),
                        tolerance,
                    )
                }
            }
        }
    }

    @Test
    fun `spec version matches the shared constant`() {
        assertEquals(NormalizedHand.SPEC_VERSION, DefaultHandNormalizer().specVersion)
    }

    private fun resource(name: String): InputStream =
        checkNotNull(javaClass.getResourceAsStream("/$name")) {
            "$name missing from test resources"
        }

    private fun cases(): List<Case> {
        val text = resource("normalizer_golden.json").use { it.readBytes().decodeToString() }
        return Json.parseToJsonElement(text).jsonArray.map { element ->
            val obj = element.jsonObject
            val expectedElement = obj["expected"]
            Case(
                name = obj.getValue("name").jsonPrimitive.content,
                handedness = obj.getValue("handedness").jsonPrimitive.content,
                world = obj.getValue("world").jsonArray.map { row ->
                    row.jsonArray.map { it.jsonPrimitive.double }
                },
                expected = if (expectedElement == null || expectedElement is JsonNull) {
                    null
                } else {
                    expectedElement.jsonArray.map { it.jsonPrimitive.double }
                },
            )
        }
    }

    private fun toHandLandmarks(case: Case): HandLandmarks = HandLandmarks(
        world = case.world.map { Landmark3(it[0].toFloat(), it[1].toFloat(), it[2].toFloat()) },
        image = List(HandLandmarks.LANDMARK_COUNT) { Landmark3(0f, 0f, 0f) },
        handedness = Handedness.valueOf(case.handedness.uppercase()),
        handednessScore = 1f,
        timestampMs = 0L,
        imageWidth = 640,
        imageHeight = 480,
    )

    private data class Case(
        val name: String,
        val handedness: String,
        val world: List<List<Double>>,
        val expected: List<Double>?,
    )
}
