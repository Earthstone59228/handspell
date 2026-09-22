package dev.handspell.app.core.model

/** One letter and the classifier's probability for it, in [0,1]. */
data class LetterScore(val letter: Letter, val probability: Float)

/**
 * A single frame's classification result.
 *
 * [ranked] is sorted by descending probability and contains every letter the classifier knows about.
 * [nearestDistance] is the fully weighted stage-1 distance to the closest reference exemplar;
 * [nearestShapeDistance] is its orientation-invariant shape component. The feedback engine uses
 * the latter only for direction-agnostic static letters. The stage-2 MLP reports [Float.NaN].
 */
data class Classification(
    val ranked: List<LetterScore>,
    val nearestDistance: Float,
    val nearestShapeDistance: Float = nearestDistance,
    val timestampMs: Long,
) {
    val top: LetterScore? get() = ranked.firstOrNull()

    fun probabilityOf(letter: Letter): Float =
        ranked.firstOrNull { it.letter == letter }?.probability ?: 0f
}
