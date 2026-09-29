package dev.handspell.app.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class WordCatalogTest {
    @Test fun `bundled list has the twelve free words in order, then Pro words`() {
        val text = File("src/main/assets/content/words.json").readText()
        val words = WordCatalog.parse(text)!!
        assertEquals(
            listOf("hello", "bye", "thankyou", "yes", "happy", "sad", "hungry", "drink", "water", "mom", "dad", "home"),
            words.filter { it.tier == Tier.FREE }.map { it.gloss },
        )
        assertEquals(words.take(12), words.filter { it.tier == Tier.FREE })
        assertEquals("thank you", words.first { it.gloss == "thankyou" }.display)
        // Pro words (added by the word-model work) are all Pro-tier after the free twelve.
        assertEquals(setOf(Tier.PRO), words.drop(12).map { it.tier }.toSet().ifEmpty { setOf(Tier.PRO) })
    }

    @Test fun `pro tier, blanks, duplicates and unknown versions`() {
        val words = WordCatalog.parse(
            """{"version":2,"words":[{"gloss":"cat","display":"cat","tier":"pro"},{"gloss":"","display":"x"},
            {"gloss":"cat","display":"again"},{"gloss":"dog","display":"dog"}]}""",
        )!!
        assertEquals(listOf(WordEntry("cat", "cat", Tier.PRO), WordEntry("dog", "dog", Tier.FREE)), words)
        assertNull(WordCatalog.parse("""{"version":3,"words":[]}"""))
        assertNull(WordCatalog.parse("{broken"))
    }
}
