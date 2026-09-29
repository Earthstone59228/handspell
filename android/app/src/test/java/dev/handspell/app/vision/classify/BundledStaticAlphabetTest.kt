package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.vision.feedback.DefaultFeedbackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the real bundled data through parsing, inference and feedback, not synthetic test hands. */
class BundledStaticAlphabetTest {
    @Test
    fun `all 24 static reference guides can be recognized and held to confirmation`() {
        val csv = checkNotNull(javaClass.getResourceAsStream("/references-v1.csv"))
            .use { it.readBytes().decodeToString() }
        val classifier = KnnLetterClassifier.load({ csv.byteInputStream() })
        assertEquals(Letter.staticLetters, classifier.supportedLetters)
        assertEquals(1536, classifier.exemplarCount)
        val guides = csv.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1).map { it.split(',') }.distinctBy { it.first() }.toList()
        for (guide in guides) {
            val target = checkNotNull(Letter.fromNameOrNull(guide.first()))
            val hand = NormalizedHand(guide.drop(1).map(String::toFloat).toFloatArray())
            val engine = DefaultFeedbackEngine().also { it.setTarget(target) }
            val first = classifier.classify(hand, 0L)
            assertEquals("$target reference ranking", target, first.top?.letter)
            engine.onFrame(first)
            val confirmed = engine.onFrame(classifier.classify(hand, 450L))
            assertTrue("$target did not confirm its bundled guide: $confirmed", confirmed is SignFeedbackState.Match)
        }
    }
}
