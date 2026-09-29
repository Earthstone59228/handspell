package dev.handspell.app.vision.words

/** Anything that can score a feature vector against a word. [WordClassifier] is the real one. */
fun interface WordScorer {
    fun probabilityOf(gloss: String, features: FloatArray): Double
}

/** Every threshold of word recognition in one place. Don't retune them without held-out data (docs/research). */
object WordGate {
    /** p(target) needed on an evaluation for it to count towards a match. */
    const val ACCEPT_PROBABILITY = 0.5

    /** Consecutive evaluations that must pass before the word counts as signed. */
    const val CONSECUTIVE = 2

    /** The dominant hand must be in at least this share of the window's frames. */
    const val MIN_HAND_PRESENCE = 0.4

    const val EVALUATE_EVERY_MS = 200L
    const val BUFFER_MS = 2500L

    /** Window lengths tried on every evaluation; the best probability wins, so signing speed doesn't matter. */
    val WINDOWS_MS = longArrayOf(1000L, 1500L, 2000L)
}

sealed interface WordProgress {
    /** No hand, or too little of one, in the recent window. */
    data object NoHand : WordProgress

    /** A hand is signing; [probability] is the best window's p(target) at the last evaluation. */
    data class Trying(val probability: Double) : WordProgress

    data object Matched : WordProgress
}

/**
 * Turns a stream of [WordFrame]s into "did they sign [target]". Keeps the last [WordGate.BUFFER_MS] of frames,
 * and every [WordGate.EVALUATE_EVERY_MS] scores the most recent windows. Not thread-safe: call from one thread.
 */
class WordRecognizer(private val scorer: WordScorer, val target: String) {

    private class Stamped(val timestampMs: Long, val frame: WordFrame)

    private val buffer = ArrayDeque<Stamped>()
    private var lastEvaluationMs: Long? = null
    private var passes = 0
    private var latest: WordProgress = WordProgress.NoHand

    fun onFrame(frame: WordFrame, timestampMs: Long): WordProgress {
        buffer.addLast(Stamped(timestampMs, frame))
        while (buffer.isNotEmpty() && timestampMs - buffer.first().timestampMs > WordGate.BUFFER_MS) buffer.removeFirst()
        if (latest == WordProgress.Matched) return latest
        lastEvaluationMs?.let { if (timestampMs - it < WordGate.EVALUATE_EVERY_MS) return latest }
        lastEvaluationMs = timestampMs
        latest = evaluate(timestampMs)
        return latest
    }

    fun reset() {
        buffer.clear()
        lastEvaluationMs = null
        passes = 0
        latest = WordProgress.NoHand
    }

    private fun evaluate(nowMs: Long): WordProgress {
        var best = -1.0
        for (window in WordGate.WINDOWS_MS) {
            val frames = buffer.filter { nowMs - it.timestampMs <= window }.map { it.frame }
            if (frames.size < WordFeatures.MIN_FRAMES || !handPresent(frames)) continue
            val features = WordFeatures.compute(frames) ?: continue
            best = maxOf(best, scorer.probabilityOf(target, features))
        }
        if (best < 0.0) {
            passes = 0
            return WordProgress.NoHand
        }
        passes = if (best >= WordGate.ACCEPT_PROBABILITY) passes + 1 else 0
        return if (passes >= WordGate.CONSECUTIVE) WordProgress.Matched else WordProgress.Trying(best)
    }

    /** True when one hand slot is present in at least [WordGate.MIN_HAND_PRESENCE] of the frames. */
    private fun handPresent(frames: List<WordFrame>): Boolean {
        val threshold = frames.size * WordGate.MIN_HAND_PRESENCE
        return (0..1).any { slot -> frames.count { it.hands[slot] != null } >= threshold }
    }
}
