package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.NormalizedHand

/** Restores display orientation from a spec-v1 reference, retaining the K/P and G/Q distinction. */
internal object ReferenceHandshapeProjection {
    fun landmarks(vector: List<Float>): List<Landmark3> {
        require(vector.size == NormalizedHand.VECTOR_DIM && vector.all(Float::isFinite))
        val e1 = vector.subList(60, 63)
        val e3 = vector.subList(63, 66)
        // e2 = e3 cross e1; inverse of the normalizer's orthonormal row matrix.
        val e2 = listOf(
            e3[1] * e1[2] - e3[2] * e1[1],
            e3[2] * e1[0] - e3[0] * e1[2],
            e3[0] * e1[1] - e3[1] * e1[0],
        )
        return listOf(Landmark3(0f, 0f, 0f)) + (0 until 20).map { point ->
            val x = vector[point * 3]
            val y = vector[point * 3 + 1]
            val z = vector[point * 3 + 2]
            Landmark3(
                e1[0] * x + e2[0] * y + e3[0] * z,
                e1[1] * x + e2[1] * y + e3[1] * z,
                e1[2] * x + e2[2] * y + e3[2] * z,
            )
        }
    }
}
