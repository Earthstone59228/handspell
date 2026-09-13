package dev.handspell.app.core.model

/**
 * Output of the shared Kotlin/Python normalisation spec (docs/CLASSIFIER.md §2).
 *
 * [vector] is laid out as 60 shape floats (landmarks 1..20, xyz, rotation-aligned and scale-
 * normalised) followed by 6 orientation floats (the hand's long axis then its palm normal, both
 * unit vectors in the upright camera frame after mirroring). Every hand is expressed as a right
 * hand; left hands are mirrored on the x axis.
 */
@JvmInline
value class NormalizedHand(val vector: FloatArray) {
    init {
        require(vector.size == VECTOR_DIM) { "expected $VECTOR_DIM floats, got ${vector.size}" }
    }

    companion object {
        const val SPEC_VERSION = 1
        const val SHAPE_DIM = 60
        const val ORIENTATION_DIM = 6
        const val VECTOR_DIM = SHAPE_DIM + ORIENTATION_DIM
    }
}
