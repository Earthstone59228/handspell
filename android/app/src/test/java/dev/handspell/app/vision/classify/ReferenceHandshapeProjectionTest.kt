package dev.handspell.app.vision.classify

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ReferenceHandshapeProjectionTest {
    @Test
    fun `reference guides restore the original display orientation for every golden hand`() {
        val source = checkNotNull(javaClass.getResourceAsStream("/normalizer_golden.json"))
        val cases = Json.parseToJsonElement(source.use { it.readBytes().decodeToString() }).jsonArray
        var checked = 0
        for (element in cases) {
            val case = element.jsonObject
            val expected = case.getValue("expected")
            if (expected is JsonNull) continue
            val vector = expected.jsonArray.map { it.jsonPrimitive.double.toFloat() }
            val actual = ReferenceHandshapeProjection.landmarks(vector)
            val world = case.getValue("world").jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.double } }
            val palm = (0..2).map { world[9][it] - world[0][it] }
            val scale = sqrt(palm.sumOf { it * it })
            val left = case.getValue("handedness").jsonPrimitive.content == "LEFT"
            for (i in world.indices) {
                val xyz = listOf(actual[i].x, actual[i].y, actual[i].z)
                for (axis in 0..2) {
                    val sign = if (left && axis == 0) -1 else 1
                    assertEquals((world[i][axis] - world[0][axis]) * sign / scale, xyz[axis].toDouble(), 1e-5)
                }
            }
            checked++
        }
        assertTrue(checked > 5)
    }
}
