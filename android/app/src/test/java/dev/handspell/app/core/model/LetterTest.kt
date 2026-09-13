package dev.handspell.app.core.model

import dev.handspell.app.vision.FeedbackThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LetterTest {

    @Test
    fun `staticLetters has 24 entries and excludes J and Z`() {
        assertEquals(24, Letter.staticLetters.size)
        assertFalse(Letter.staticLetters.contains(Letter.J))
        assertFalse(Letter.staticLetters.contains(Letter.Z))
    }

    @Test
    fun `FeedbackThresholds defaults match docs CLASSIFIER md section 6`() {
        val defaults = FeedbackThresholds()

        assertEquals(0.35f, defaults.emaAlpha, 0f)
        assertEquals(0.85f, defaults.matchProbability, 0f)
        assertEquals(0.20f, defaults.matchMargin, 0f)
        assertEquals(0.32f, defaults.matchDistance, 0f)
        assertEquals(0.45f, defaults.adjustProbability, 0f)
        assertEquals(0.45f, defaults.rejectDistance, 0f)
        assertEquals(400L, defaults.holdToConfirmMs)
        assertEquals(800L, defaults.matchLatchMs)
        assertEquals(3, defaults.noHandFrames)
    }
}
