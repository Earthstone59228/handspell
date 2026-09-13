package dev.handspell.app.vision.normalize

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Handedness
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.vision.HandNormalizer
import kotlin.math.sqrt

/**
 * The one Kotlin implementation of the normalisation spec (docs/CLASSIFIER.md §2).
 *
 * This is a line-for-line port of `training/handspell/normalize.py`, which is the normative
 * reference. Both implementations are held identical by the golden vectors in
 * `training/testdata/normalizer_golden.json`; every arithmetic operation happens in [Double] and
 * narrows to [Float] only when the output array is written, which is what keeps the two languages
 * within the agreed 1e-6 absolute tolerance.
 */
class DefaultHandNormalizer : HandNormalizer {

    override val specVersion: Int = NormalizedHand.SPEC_VERSION

    override fun normalize(landmarks: HandLandmarks): NormalizedHand? {
        require(landmarks.world.size == HandLandmarks.LANDMARK_COUNT) {
            "expected ${HandLandmarks.LANDMARK_COUNT} world landmarks, got ${landmarks.world.size}"
        }

        val p = Array(HandLandmarks.LANDMARK_COUNT) { index ->
            val lm = landmarks.world[index]
            doubleArrayOf(lm.x.toDouble(), lm.y.toDouble(), lm.z.toDouble())
        }

        // 1. Mirror LEFT hands into the canonical right-hand space.
        if (landmarks.handedness == Handedness.LEFT) {
            for (i in p.indices) p[i][0] = -p[i][0]
        }

        // 2. Translate to the wrist (landmark 0), the only finger-articulation-stable origin.
        val wrist = p[HandLandmarks.WRIST].copyOf()
        for (i in p.indices) {
            p[i][0] -= wrist[0]
            p[i][1] -= wrist[1]
            p[i][2] -= wrist[2]
        }

        // 3. Palm frame: u = p_9, v = p_5 - p_17; e1 = u/|u|, e2 = orthonormalised v, e3 = e1 x e2.
        val u = p[HandLandmarks.MIDDLE_MCP]
        val normU = norm(u)
        if (normU < DEGENERATE_EPS) return null
        val e1 = scale(u, 1.0 / normU)

        val v = sub(p[HandLandmarks.INDEX_MCP], p[HandLandmarks.PINKY_MCP])
        val w = sub(v, scale(e1, dot(v, e1)))
        val normW = norm(w)
        if (normW < DEGENERATE_EPS) return null
        val e2 = scale(w, 1.0 / normW)

        val e3 = cross(e1, e2)

        // q_i = R · p_i, with R = [e1; e2; e3] as rows.
        val q = Array(HandLandmarks.LANDMARK_COUNT) { i ->
            val pi = p[i]
            doubleArrayOf(dot(e1, pi), dot(e2, pi), dot(e3, pi))
        }

        // 4. Scale by the wrist-to-middle-MCP span (rotation preserves length, so |u| is exact).
        for (i in q.indices) {
            q[i][0] /= normU
            q[i][1] /= normU
            q[i][2] /= normU
        }

        // 5 + 6. Shape block (landmarks 1..20, xyz) then orientation block (e1, e3).
        val vector = FloatArray(NormalizedHand.VECTOR_DIM)
        var out = 0
        for (i in 1 until HandLandmarks.LANDMARK_COUNT) {
            vector[out++] = q[i][0].toFloat()
            vector[out++] = q[i][1].toFloat()
            vector[out++] = q[i][2].toFloat()
        }
        for (axis in 0 until 3) vector[out++] = e1[axis].toFloat()
        for (axis in 0 until 3) vector[out++] = e3[axis].toFloat()

        return NormalizedHand(vector)
    }

    private companion object {
        const val DEGENERATE_EPS = 1e-6

        fun norm(v: DoubleArray): Double = sqrt(dot(v, v))

        fun dot(a: DoubleArray, b: DoubleArray): Double =
            a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

        fun cross(a: DoubleArray, b: DoubleArray): DoubleArray = doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )

        fun sub(a: DoubleArray, b: DoubleArray): DoubleArray =
            doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])

        fun scale(v: DoubleArray, s: Double): DoubleArray =
            doubleArrayOf(v[0] * s, v[1] * s, v[2] * s)
    }
}
