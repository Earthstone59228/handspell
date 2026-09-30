package dev.handspell.app.vision.words

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One analysed camera frame for word recognition. [hands] always has two slots (any order, null = absent), each
 * 42 floats: x, y for the 21 hand landmarks in normalised image space. [pose] is nose, left shoulder, right
 * shoulder as x, y pairs (6 floats, NaN where a point was not found), or null when no body was found.
 */
class WordFrame(val hands: List<FloatArray?>, val pose: FloatArray?) {
    init {
        require(hands.size == 2) { "two hand slots" }
        hands.forEach { require(it == null || it.size == 42) { "a hand is 42 floats" } }
        require(pose == null || pose.size == 6) { "pose is 6 floats" }
    }
}

/**
 * Word-sign sequence features, spec words-v2. A line-for-line port of `training/handspell/words_features.py`, which
 * is the normative definition (its docstring explains every step). Pinned to it by WordFeaturesGoldenTest.
 */
object WordFeatures {
    const val STEPS = 16
    const val SLOT_DIM = 55
    const val FEATURE_DIM = STEPS * 2 * SLOT_DIM + (STEPS - 1) * 2
    const val MIN_FRAMES = 5
    private const val MIN_SCALE = 0.05
    private const val MIN_HAND = 1e-3

    /** Returns null when the attempt has too few frames, no hand, or no body to anchor to. */
    fun compute(all: List<WordFrame>): FloatArray? {
        // Crop to the active span: first to last frame that has a hand (rest frames around a sign are dropped).
        val first = all.indexOfFirst { f -> f.hands.any { it != null } }
        if (first < 0) return null
        val last = all.indexOfLast { f -> f.hands.any { it != null } }
        val frames = all.subList(first, last + 1)
        val n = frames.size
        if (n < MIN_FRAMES) return null
        val present = Array(n) { f -> BooleanArray(2) { h -> frames[f].hands[h] != null } }

        val count = IntArray(2)
        val travel = DoubleArray(2)
        for (h in 0..1) {
            var last: FloatArray? = null
            for (f in 0 until n) {
                if (!present[f][h]) continue
                count[h]++
                val wrist = frames[f].hands[h]!!
                last?.let { travel[h] += hypot(wrist[0] - it[0], wrist[1] - it[1]) }
                last = wrist
            }
        }
        val a = if (count[1] > count[0] || (count[1] == count[0] && travel[1] > travel[0])) 1 else 0
        val slots = intArrayOf(a, 1 - a)

        val noseX = median(frames.map { it.pose?.get(0)?.toDouble() ?: Double.NaN })
        val noseY = median(frames.map { it.pose?.get(1)?.toDouble() ?: Double.NaN })
        if (noseX.isNaN() || noseY.isNaN()) return null
        val widths = frames.map {
            val p = it.pose
            if (p == null) Double.NaN else hypot(p[2] - p[4], p[3] - p[5])
        }
        val scale = max(median(widths).let { if (it.isNaN()) 0.0 else it }, MIN_SCALE)

        val perFrame = Array(n) { Array(2) { FloatArray(SLOT_DIM) } }
        val wristA = Array(n) { doubleArrayOf(Double.NaN, Double.NaN) }
        for (f in 0 until n) {
            for (s in 0..1) {
                if (s == 1) continue // dominant hand only: the training data never has a second hand
                val h = slots[s]
                val pts = frames[f].hands[h] ?: continue
                val wx = pts[0].toDouble()
                val wy = pts[1].toDouble()
                val size = max(hypot(pts[18] - pts[0], pts[19] - pts[1]), MIN_HAND)
                val out = perFrame[f][s]
                for (p in 0 until 21) {
                    out[2 * p] = ((pts[2 * p] - wx) / size).toFloat()
                    out[2 * p + 1] = ((pts[2 * p + 1] - wy) / size).toFloat()
                }
                out[42] = ((wx - noseX) / scale).toFloat()
                out[43] = ((wy - noseY) / scale).toFloat()
                out[44] = ((pts[16] - noseX) / scale).toFloat()
                out[45] = ((pts[17] - noseY) / scale).toFloat()
                for ((k, tip) in intArrayOf(4, 12, 16, 20).withIndex()) {
                    out[46 + 2 * k] = ((pts[2 * tip] - noseX) / scale).toFloat()
                    out[47 + 2 * k] = ((pts[2 * tip + 1] - noseY) / scale).toFloat()
                }
                out[54] = 1f
                if (s == 0) {
                    wristA[f][0] = (wx - noseX) / scale
                    wristA[f][1] = (wy - noseY) / scale
                }
            }
        }

        // np.linspace(0, n - 1, STEPS) then np.rint: step = (n-1)/15, last element pinned to n-1.
        val step = (n - 1) / (STEPS - 1).toDouble()
        val idx = IntArray(STEPS) { i ->
            if (i == STEPS - 1) n - 1 else Math.rint(i * step).toInt()
        }
        val track = Array(STEPS) { wristA[idx[it]].clone() }
        val seen = (0 until STEPS).filter { !track[it][0].isNaN() }
        if (seen.isEmpty()) return null
        for (i in 0 until STEPS) {
            if (!track[i][0].isNaN()) continue
            val j = seen.minByOrNull { abs(it - i) }!!
            track[i] = track[j].clone()
        }

        val out = FloatArray(FEATURE_DIM)
        var o = 0
        for (i in 0 until STEPS) for (s in 0..1) for (d in 0 until SLOT_DIM) out[o++] = perFrame[idx[i]][s][d]
        for (i in 1 until STEPS) {
            out[o++] = (track[i][0] - track[i - 1][0]).toFloat()
            out[o++] = (track[i][1] - track[i - 1][1]).toFloat()
        }
        return out
    }

    /** The features of the left-right mirrored sign (every x feature and x velocity negated). */
    fun mirror(features: FloatArray): FloatArray {
        val out = features.clone()
        for (i in xColumns) out[i] = -out[i]
        return out
    }

    private val xColumns: IntArray = buildList {
        for (step in 0 until STEPS) for (slot in 0..1) {
            val base = (step * 2 + slot) * SLOT_DIM
            for (p in 0 until 21) add(base + 2 * p)
            add(base + 42)
            add(base + 44)
            for (k in 0 until 4) add(base + 46 + 2 * k)
        }
        val base = STEPS * 2 * SLOT_DIM
        for (i in 0 until STEPS - 1) add(base + 2 * i)
    }.toIntArray()

    private fun hypot(dx: Float, dy: Float): Double = hypot(dx.toDouble(), dy.toDouble())
    private fun hypot(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)

    /** np.median of the non-NaN values; NaN when there are none. */
    private fun median(values: List<Double>): Double {
        val v = values.filter { !it.isNaN() }.sorted()
        if (v.isEmpty()) return Double.NaN
        val m = v.size / 2
        return if (v.size % 2 == 1) v[m] else (v[m - 1] + v[m]) / 2.0
    }
}
