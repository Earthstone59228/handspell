package dev.handspell.app.core.model

/** One letter and the classifier's probability for it, in [0,1]. */
data class LetterScore(val letter: Letter, val probability: Float)

/**
 * A single frame's classification result.
 *
 * [ranked] is sorted by descending probability and contains every letter the classifier knows about.
 * [nearestDistance] is stage-1 only (Euclidean distance in normalised-vector space to the closest
 * reference exemplar); the stage-2 MLP reports [Float.NaN].
 */
data class Classification(
    val ranked: List<LetterScore>,
    val nearestDistance: Float,
    val timestampMs: Long,
) {
    val top: LetterScore? get() = ranked.firstOrNull()

    fun probabilityOf(letter: Letter): Float =
        ranked.firstOrNull { it.letter == letter }?.probability ?: 0f
}
