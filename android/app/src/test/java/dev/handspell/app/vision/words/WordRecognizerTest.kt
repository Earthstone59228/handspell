package dev.handspell.app.vision.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordRecognizerTest {

    private fun hand(x: Float): FloatArray = FloatArray(42) { i -> if (i % 2 == 0) x + (i / 2) * 0.01f else 0.5f - (i / 2) * 0.01f }
    private val pose = floatArrayOf(0.5f, 0.3f, 0.6f, 0.5f, 0.4f, 0.5f)

    private fun frame(withHand: Boolean, x: Float = 0.5f) = WordFrame(listOf(if (withHand) hand(x) else null, null), pose)

    /** Feeds 30 fps frames from [startMs]; returns the last progress. */
    private fun feed(r: WordRecognizer, startMs: Long, seconds: Double, withHand: Boolean = true): WordProgress {
        var last: WordProgress = WordProgress.NoHand
        var t = startMs
        val end = startMs + (seconds * 1000).toLong()
        var i = 0
        while (t < end) {
            last = r.onFrame(frame(withHand, 0.3f + 0.005f * (i % 40)), t)
            t += 33; i++
        }
        return last
    }

    @Test
    fun `no hand means NoHand and never a match`() {
        val r = WordRecognizer({ _, _ -> 0.99 }, "hello")
        assertEquals(WordProgress.NoHand, feed(r, 0, 3.0, withHand = false))
    }

    @Test
    fun `a confident score matches only after consecutive evaluations`() {
        val r = WordRecognizer({ _, _ -> 0.9 }, "hello")
        val progress = mutableListOf<WordProgress>()
        var t = 0L
        var i = 0
        while (t < 2000) {
            progress += r.onFrame(frame(true, 0.3f + 0.005f * (i++ % 40)), t)
            t += 33
        }
        val firstMatch = progress.indexOfFirst { it == WordProgress.Matched }
        assertTrue("should match within 2 s", firstMatch >= 0)
        val firstTrying = progress.indexOfFirst { it is WordProgress.Trying }
        assertTrue("a Trying evaluation comes before the match", firstTrying in 0 until firstMatch)
        assertTrue("stays matched", progress.drop(firstMatch).all { it == WordProgress.Matched })
    }

    @Test
    fun `a low score never matches`() {
        val r = WordRecognizer({ _, _ -> 0.3 }, "hello")
        val last = feed(r, 0, 4.0)
        assertTrue(last is WordProgress.Trying)
        assertEquals(0.3, (last as WordProgress.Trying).probability, 1e-9)
    }

    @Test
    fun `one passing evaluation between failures is not enough`() {
        var call = 0
        // The scorer runs once per window (3 per evaluation), so vary the score per evaluation, not per call.
        val r = WordRecognizer({ _, _ -> if ((call++ / WordGate.WINDOWS_MS.size) % 3 == 1) 0.9 else 0.1 }, "hello")
        assertTrue(feed(r, 0, 4.0) !is WordProgress.Matched)
    }

    @Test
    fun `too little hand presence in the window is NoHand`() {
        val r = WordRecognizer({ _, _ -> 0.99 }, "hello")
        // A hand in 1 of every 5 frames = 20% presence, below the 40% floor.
        var last: WordProgress = WordProgress.NoHand
        var t = 0L
        var i = 0
        while (t < 3000) {
            last = r.onFrame(frame(i % 5 == 0), t)
            t += 33; i++
        }
        assertEquals(WordProgress.NoHand, last)
    }

    @Test
    fun `reset clears a match`() {
        val r = WordRecognizer({ _, _ -> 0.9 }, "hello")
        feed(r, 0, 3.0)
        r.reset()
        assertEquals(WordProgress.NoHand, r.onFrame(frame(false), 10_000))
    }
}
