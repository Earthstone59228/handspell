package dev.handspell.app.vision.feedback

import dev.handspell.app.core.model.Letter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hints are authored copy, so the thing worth testing is that the engine can only ever point at
 * copy that exists: a hint id with no string resource behind it would render as a blank nudge on the
 * one screen where the learner most needs a sentence.
 */
class ConfusableHintsTest {

    @Test
    fun `every hint id the engine can emit exists in strings_content`() {
        val defined = definedStringKeys()
        for (hintId in ConfusableHints.hintIds()) {
            assertTrue("$hintId is not defined in strings_content.xml", hintId in defined)
        }
    }

    @Test
    fun `the two halves of a confusable pair get opposite cues`() {
        val kOverP = ConfusableHints.hintFor(Letter.K, Letter.P)
        val pOverK = ConfusableHints.hintFor(Letter.P, Letter.K)
        assertEquals("hint_point_forward", kOverP.id)
        assertEquals("hint_point_down", pOverK.id)
        assertNotEquals(kOverP.id, pOverK.id)
    }

    @Test
    fun `an undocumented confusion falls back to generic copy with no letter attached`() {
        val hint = ConfusableHints.hintFor(Letter.A, Letter.W)
        assertEquals(ConfusableHints.GENERIC_HINT_ID, hint.id)
        assertNull(hint.confusedWith)
    }

    @Test
    fun `no competitor means no letter is named`() {
        assertEquals(ConfusableHints.GENERIC_HINT_ID, ConfusableHints.hintFor(Letter.A, null).id)
        assertEquals(ConfusableHints.GENERIC_HINT_ID, ConfusableHints.hintFor(Letter.A, Letter.A).id)
    }

    @Test
    fun `a documented confusion names the competitor`() {
        val hint = ConfusableHints.hintFor(Letter.M, Letter.N)
        assertEquals("hint_thumb_under_three", hint.id)
        assertEquals(Letter.N, hint.confusedWith)
    }

    private fun definedStringKeys(): Set<String> {
        val candidates = listOf(
            File("src/main/res/values/strings_content.xml"),
            File("app/src/main/res/values/strings_content.xml"),
            File("android/app/src/main/res/values/strings_content.xml"),
        )
        val file = candidates.firstOrNull(File::exists)
            ?: error("strings_content.xml not found from ${File(".").absolutePath}")
        return Regex("<string name=\"([^\"]+)\"").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }
}
