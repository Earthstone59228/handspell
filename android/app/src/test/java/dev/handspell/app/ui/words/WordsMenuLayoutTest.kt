package dev.handspell.app.ui.words

import dev.handspell.app.content.Tier
import dev.handspell.app.content.WordEntry
import dev.handspell.app.progress.WordRecord
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WordsMenuLayoutTest {
    private fun w(gloss: String, display: String = gloss, tier: Tier = Tier.FREE) = WordEntry(gloss, display, tier)

    @Test fun `progress line counts completed over every listed word`() {
        val state = WordsMenuState(
            loading = false,
            words = listOf(w("hello"), w("bye"), w("cat", tier = Tier.PRO)),
            records = mapOf("hello" to WordRecord("hello", matches = 1), "cat" to WordRecord("cat", markedComplete = true)),
        )
        assertEquals("2 / 3", wordsProgressLine(state.completeOfAll, state.words.size))
        assertEquals(1, state.completeCount) // the locked Pro word is not practicable
    }

    @Test fun `thumbnail frame is about nine twentieths in and hand bounds span its points`() {
        assertEquals(9, thumbnailFrameIndex(20))
        assertEquals(0, thumbnailFrameIndex(1))
        assertEquals(0, thumbnailFrameIndex(0))
        val hand = FloatArray(42) { if (it % 2 == 0) 0.2f + it / 100f else 0.5f - it / 200f }
        assertArrayEquals(floatArrayOf(0.2f, 0.295f, 0.6f, 0.495f), handBounds(hand), 0.0001f)
    }
}
