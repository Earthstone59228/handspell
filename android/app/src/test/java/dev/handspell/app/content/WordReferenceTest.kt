package dev.handspell.app.content

import dev.handspell.app.ui.words.loopFrameIndex
import dev.handspell.app.ui.words.stillFrameIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WordReferenceTest {
    private val hand = List(42) { (it % 10) / 10.0 }.joinToString(",", "[", "]")

    private fun doc(words: String, version: Int = 1, fps: Int = 10) = """{"version":$version,"fps":$fps,"words":{$words}}"""

    @Test fun `a small sample parses into frames of hands`() {
        val refs = WordReferences.parse(doc(""""yes":{"body":[0.5,0.3,0.2,0.5,0.8,0.5],"head":[0.15,0.2],
            "frames":[[$hand,null],[$hand,null]]}"""))!!
        val yes = refs.getValue("yes")
        assertEquals(2, yes.frames.size)
        assertEquals(42, yes.frames[0][0]!!.size)
        assertNull(yes.frames[0][1])
        assertEquals(100L, yes.frameMillis)
    }

    @Test fun `bad words are left out and a bad document means no references`() {
        val short = "[0.1,0.2]"
        val refs = WordReferences.parse(doc(
            """"a":{"body":[0.5,0.3,0.2,0.5,0.8,0.5],"head":[0.1,0.1],"frames":[[$short]]},
            "b":{"body":[0.5,0.3],"head":[0.1,0.1],"frames":[[$hand]]},
            "c":{"body":[0.5,0.3,0.2,0.5,0.8,0.5],"head":[0.1,0.1],"frames":[]},
            "d":{"body":[0.5,0.3,0.2,0.5,0.8,0.5],"head":[0.1,0.1],"frames":[[null,null]]},
            "e":{"body":[0.5,0.3,0.2,0.5,0.8,0.5],"head":[0.1,0.1],"frames":[[$hand]]}""",
        ))!!
        assertEquals(setOf("e"), refs.keys)
        assertNull(WordReferences.parse(doc("", version = 2)))
        assertNull(WordReferences.parse(doc("", fps = 0)))
        assertNull(WordReferences.parse("{broken"))
    }

    @Test fun `every catalog word has a well-formed bundled reference`() {
        val catalog = WordCatalog.parse(File("src/main/assets/content/words.json").readText())!!
        val refs = WordReferences.parse(File("src/main/assets/content/word-refs.json").readText())
        assertNotNull(refs)
        catalog.forEach { word ->
            val ref = refs!![word.gloss]
            assertNotNull("no reference for ${word.gloss}", ref)
            assertTrue(ref!!.frames.isNotEmpty())
            ref.frames.flatten().filterNotNull().forEach { h ->
                assertEquals(42, h.size)
                assertTrue(h.all { it.isFinite() })
            }
        }
    }

    @Test fun `free words carry tips and Pro words do not`() {
        val catalog = WordCatalog.parse(File("src/main/assets/content/words.json").readText())!!
        assertTrue(catalog.filter { it.tier == Tier.FREE }.all { !it.tip.isNullOrBlank() })
        assertTrue(catalog.filter { it.tier == Tier.PRO }.all { it.tip == null })
        assertEquals("Open hand, thumb tapping your chin.", catalog.first { it.gloss == "mom" }.tip)
    }

    @Test fun `loop holds the first frame then steps at the frame rate`() {
        assertEquals(0, loopFrameIndex(0, 20, 100, holdMs = 600))
        assertEquals(0, loopFrameIndex(599, 20, 100, holdMs = 600))
        assertEquals(0, loopFrameIndex(600, 20, 100, holdMs = 600))
        assertEquals(1, loopFrameIndex(700, 20, 100, holdMs = 600))
        assertEquals(19, loopFrameIndex(2599, 20, 100, holdMs = 600))
        assertEquals(0, loopFrameIndex(2600, 20, 100, holdMs = 600)) // next loop starts with the hold
        assertEquals(0, loopFrameIndex(500, 1, 100))
        assertEquals(10, stillFrameIndex(20))
        assertEquals(0, stillFrameIndex(1))
        assertEquals(0, stillFrameIndex(0))
    }
}
