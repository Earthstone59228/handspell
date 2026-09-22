package dev.handspell.app.calibration

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Handedness
import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.vision.HandNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalCalibrationTest {

    @Test
    fun `debug signer ids are opaque numbered labels only`() {
        assertEquals("s42", DebugSignerId.parse("s42")?.value)
        assertEquals("s999999", DebugSignerId.parse("s999999")?.value)
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), DebugSignerId.quickChoices.map { it.value })
        listOf("s0", "s", "s0001", "Alex", "s 2", " s2", "s1000000").forEach { value ->
            assertNull("$value must not become a signer id", DebugSignerId.parse(value))
        }
    }

    @Test
    fun `a session saves eight distinct finite normalized examples and resets`() = runTest {
        var index = 0
        val normalizer = normalizer { NormalizedHand(FloatArray(NormalizedHand.VECTOR_DIM).also { it[0] = index++ * .1f }) }
        val store = InMemoryPersonalCalibrationStore()
        val session = PersonalCalibrationSession(normalizer, store)

        session.begin(Letter.B)
        repeat(CalibrationSessionState.MINIMUM_SAMPLES) { session.accept(landmarks()) }

        assertTrue(session.state.value.canSave)
        assertTrue(session.save())
        assertEquals(0, session.state.value.accepted.size)
        assertEquals(CalibrationSessionState.MINIMUM_SAMPLES, store.snapshot.first().exemplarsByLetter.getValue(Letter.B).size)
    }

    @Test
    fun `a session refuses duplicate samples and motion letters`() {
        val store = InMemoryPersonalCalibrationStore()
        val session = PersonalCalibrationSession(normalizer { NormalizedHand(FloatArray(NormalizedHand.VECTOR_DIM)) }, store)

        session.begin(Letter.C)
        session.accept(landmarks())
        session.accept(landmarks())

        assertEquals(1, session.state.value.accepted.size)
        assertEquals(1, session.state.value.duplicateCount)
        assertFalse(session.state.value.canSave)
        val error = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { session.begin(Letter.J) }
        assertTrue(error.message!!.contains("motion"))
    }

    private fun normalizer(normalize: () -> NormalizedHand?): HandNormalizer = object : HandNormalizer {
        override val specVersion: Int = NormalizedHand.SPEC_VERSION
        override fun normalize(landmarks: HandLandmarks): NormalizedHand? = normalize()
    }

    private fun landmarks(): HandLandmarks {
        val points = List(HandLandmarks.LANDMARK_COUNT) { Landmark3(0f, 0f, 0f) }
        return HandLandmarks(
            world = points,
            image = points,
            handedness = Handedness.RIGHT,
            handednessScore = 1f,
            timestampMs = 0L,
            imageWidth = 1,
            imageHeight = 1,
        )
    }
}
