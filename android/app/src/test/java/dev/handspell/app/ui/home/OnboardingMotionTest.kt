package dev.handspell.app.ui.home

import androidx.compose.ui.geometry.Offset
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.Letter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingMotionTest {
    private fun shape(scale: Float, shiftX: Float = 0f) = CanonicalHandshape(
        Letter.A, List(21) { Landmark3(shiftX + it * scale, 2f * it * scale, 0f) },
    )

    @Test fun poseIsHeldBeforeItMorphs() {
        assertEquals(0f, morphProgress(0f), 0f)
        assertEquals(0f, morphProgress(HOLD_FRACTION), 0f)
        assertEquals(1f, morphProgress(1f), 1e-6f)
        assertTrue(morphProgress(0.8f) in 0.01f..0.99f)
    }

    @Test fun morphNeverGoesBackwards() {
        var last = 0f
        for (i in 0..100) { val v = morphProgress(i / 100f); assertTrue(v >= last); last = v }
    }

    @Test fun fittedHandFillsTheUnitSquareWithoutDistortion() {
        val points = fitToUnitSquare(shape(scale = 3f, shiftX = 40f))
        assertTrue(points.all { it.x in 0f..1f && it.y in 0f..1f })
        // The hand is taller than wide (y spans twice x), so height fills the square and width is half of it, centred.
        assertEquals(0f, points.minOf { it.y }, 1e-5f)
        assertEquals(1f, points.maxOf { it.y }, 1e-5f)
        assertEquals(0.5f, points.maxOf { it.x } - points.minOf { it.x }, 1e-5f)
        assertEquals(0.25f, points.minOf { it.x }, 1e-5f)
    }

    @Test fun blendReachesBothPoses() {
        val a = List(21) { Offset(0f, 0f) }
        val b = List(21) { Offset(1f, 2f) }
        assertEquals(a, blend(a, b, 0f))
        assertEquals(b, blend(a, b, 1f))
        assertEquals(Offset(0.5f, 1f), blend(a, b, 0.5f)[7])
    }
}
