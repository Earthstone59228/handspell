package dev.handspell.app.vision.motion

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Letter
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What a motion letter (J or Z) recognizer reports for the frames seen so far. */
sealed interface MotionProgress {
    /** No hand in the recent frames. */
    data object NoHand : MotionProgress

    /** A hand is visible but not in the starting handshape (an I for J, a pointing index finger for Z). */
    data object WrongShape : MotionProgress

    /** Right handshape. [progress] is 0 when still and rises (never past 0.9) while the fingertip is tracing. */
    data class Tracing(val progress: Float) : MotionProgress

    /** The fingertip drew the letter. */
    data class Matched(val distance: Float) : MotionProgress
}

/** Every threshold of J/Z recognition in one place. There is no real J/Z data to tune them on; see docs/CLASSIFIER.md. */
object MotionGate {
    /** A finger counts as extended when its tip is this many times farther from the wrist than its knuckle. */
    const val EXTENDED_RATIO = 1.5f

    /** ...and as curled when it is nearer than this. Between the two counts as neither. */
    const val CURLED_RATIO = 1.4f

    const val BUFFER_MS = 3600L
    val WINDOWS_MS = longArrayOf(700L, 1000L, 1400L, 1900L, 2500L, 3200L)
    const val MIN_SAMPLES = 8

    /** Share of a window's frames that must show the right handshape. */
    const val MIN_SHAPE_SHARE = 0.7f

    /** The drawn path must span at least this many hand sizes, or it is jitter. */
    const val MIN_EXTENT_HAND_SIZES = 1.2f

    /** Mean distance, in bounding-box units, between the drawn path and the letter's template. */
    const val MAX_DISTANCE = 0.10f

    const val RESAMPLE = 24

    /**
     * The path is judged only once the fingertip has come to rest: it moved less than this many hand sizes over the
     * last [REST_MS]. "Draw it, then hold a moment" also stops half-finished strokes from being scored again and again.
     */
    const val REST_MS = 200L
    const val REST_MOVEMENT_HAND_SIZES = 0.2f
}

/**
 * Recognises J and Z, the two fingerspelled letters that are drawn in the air. The handshape must be right (J: pinky
 * out, other fingers curled; Z: index finger out, others curled) for most of the window; then the path of the
 * fingertip (pinky for J, index for Z), normalised for position and size and resampled by arc length, must match the
 * letter's template. Left- and right-handed drawing both match (the template is tried mirrored), and speed does not
 * matter (arc-length resampling). Not thread-safe.
 */
class MotionLetterRecognizer(val letter: Letter) {

    init {
        require(letter == Letter.J || letter == Letter.Z) { "motion letters are J and Z, not $letter" }
    }

    private class Sample(val timestampMs: Long, val x: Float, val y: Float, val handSize: Float, val shapeOk: Boolean)

    private val buffer = ArrayDeque<Sample>()
    private var lastHandMs = Long.MIN_VALUE
    private var matched: MotionProgress.Matched? = null
    private var latest: MotionProgress = MotionProgress.NoHand
    private val templates = templates(letter).flatMap { listOf(it, mirrorX(it)) }

    fun reset() {
        buffer.clear()
        matched = null
        latest = MotionProgress.NoHand
        lastHandMs = Long.MIN_VALUE
    }

    /** Feed one analysed frame; [hand] is null when no hand was found. */
    fun onFrame(hand: HandLandmarks?, timestampMs: Long): MotionProgress {
        matched?.let { return it }
        if (hand == null) {
            if (lastHandMs == Long.MIN_VALUE || timestampMs - lastHandMs > NO_HAND_MS) {
                buffer.clear()
                latest = MotionProgress.NoHand
            }
            return latest
        }
        lastHandMs = timestampMs
        val sample = sampleOf(hand, timestampMs) ?: return latest
        buffer.addLast(sample)
        while (buffer.isNotEmpty() && timestampMs - buffer.first().timestampMs > MotionGate.BUFFER_MS) buffer.removeFirst()
        if (!sample.shapeOk) {
            latest = MotionProgress.WrongShape
            return latest
        }
        var best = Float.MAX_VALUE
        var bestExtent = 0f
        if (!atRest(timestampMs)) {
            latest = MotionProgress.Tracing(min(0.9f, extentOf(timestampMs, MotionGate.WINDOWS_MS.last()) / TRACE_EXTENT_FOR_FULL))
            return latest
        }
        for (window in MotionGate.WINDOWS_MS) {
            val result = score(timestampMs, window) ?: continue
            if (result.first < best) best = result.first
            bestExtent = max(bestExtent, result.second)
        }
        if (best <= MotionGate.MAX_DISTANCE) {
            matched = MotionProgress.Matched(best).also { latest = it }
            return latest
        }
        latest = MotionProgress.Tracing(min(0.9f, bestExtent / TRACE_EXTENT_FOR_FULL))
        return latest
    }

    private fun sampleOf(hand: HandLandmarks, timestampMs: Long): Sample? {
        val w = hand.imageWidth.toFloat()
        val h = hand.imageHeight.toFloat()
        if (w <= 0f || h <= 0f) return null
        fun px(i: Int) = hand.image[i].x * w
        fun py(i: Int) = hand.image[i].y * h
        fun dist(a: Int, b: Int) = hypot(px(a) - px(b), py(a) - py(b))
        val handSize = dist(HandLandmarks.WRIST, HandLandmarks.MIDDLE_MCP)
        if (handSize < MIN_HAND_PIXELS) return null
        // Ratio of tip-to-wrist over knuckle-to-wrist: large when a finger is out, near 1 when folded on the palm.
        fun ratio(mcp: Int, tip: Int): Float = dist(tip, HandLandmarks.WRIST) / max(dist(mcp, HandLandmarks.WRIST), 1e-3f)
        val index = ratio(5, 8)
        val middle = ratio(9, 12)
        val ring = ratio(13, 16)
        val pinky = ratio(17, 20)
        val ok = when (letter) {
            Letter.J -> pinky > MotionGate.EXTENDED_RATIO && index < MotionGate.CURLED_RATIO &&
                middle < MotionGate.CURLED_RATIO && ring < MotionGate.CURLED_RATIO
            else -> index > MotionGate.EXTENDED_RATIO && middle < MotionGate.CURLED_RATIO &&
                ring < MotionGate.CURLED_RATIO && pinky < MotionGate.CURLED_RATIO
        }
        val tip = if (letter == Letter.J) 20 else 8
        return Sample(timestampMs, px(tip), py(tip), handSize, ok)
    }

    /** Distance to the template and the path's extent (in hand sizes) for the window ending at [nowMs]; null if unusable. */
    private fun score(nowMs: Long, windowMs: Long): Pair<Float, Float>? {
        val window = buffer.filter { nowMs - it.timestampMs <= windowMs }
        if (window.size < MotionGate.MIN_SAMPLES) return null
        if (window.count { it.shapeOk }.toFloat() / window.size < MotionGate.MIN_SHAPE_SHARE) return null
        val tips = window.filter { it.shapeOk }
        val handSize = tips.map { it.handSize }.sorted()[tips.size / 2]
        val smoothed = smooth(tips.map { floatArrayOf(it.x, it.y) })
        val minX = smoothed.minOf { it[0] }
        val maxX = smoothed.maxOf { it[0] }
        val minY = smoothed.minOf { it[1] }
        val maxY = smoothed.maxOf { it[1] }
        val span = max(maxX - minX, maxY - minY)
        val extent = span / handSize
        if (extent < MotionGate.MIN_EXTENT_HAND_SIZES) return Float.MAX_VALUE to extent
        val path = resampleByArcLength(smoothed, MotionGate.RESAMPLE)
        val shape = normalise(path)
        val distance = templates.minOf { meanDistance(shape, it) }
        return distance to extent
    }

    /** True when the fingertip moved less than the rest threshold over the last [MotionGate.REST_MS]. */
    private fun atRest(nowMs: Long): Boolean {
        val recent = buffer.filter { nowMs - it.timestampMs <= MotionGate.REST_MS }
        if (recent.size < 2) return false
        val handSize = recent.map { it.handSize }.sorted()[recent.size / 2]
        val spread = max(recent.maxOf { it.x } - recent.minOf { it.x }, recent.maxOf { it.y } - recent.minOf { it.y })
        return spread <= MotionGate.REST_MOVEMENT_HAND_SIZES * handSize
    }

    /** How far the shape-correct fingertip travelled in the last [windowMs], in hand sizes. */
    private fun extentOf(nowMs: Long, windowMs: Long): Float {
        val tips = buffer.filter { nowMs - it.timestampMs <= windowMs && it.shapeOk }
        if (tips.size < 2) return 0f
        val handSize = tips.map { it.handSize }.sorted()[tips.size / 2]
        return max(tips.maxOf { it.x } - tips.minOf { it.x }, tips.maxOf { it.y } - tips.minOf { it.y }) / handSize
    }

    private fun smooth(points: List<FloatArray>): List<FloatArray> = points.mapIndexed { i, p ->
        val a = points[max(0, i - 1)]
        val c = points[min(points.lastIndex, i + 1)]
        floatArrayOf((a[0] + p[0] + c[0]) / 3f, (a[1] + p[1] + c[1]) / 3f)
    }

    companion object {
        private const val NO_HAND_MS = 400L
        private const val MIN_HAND_PIXELS = 12f
        private const val TRACE_EXTENT_FOR_FULL = 3f

        /**
         * The letter's ideal paths, y down, already normalised (see [normalise]). J is a straight stroke down and a hook
         * back up; people draw the hook at different widths, so three widths (hook width over stroke length 0.3, 0.42
         * and 0.55) are accepted. Z is across, back down the diagonal, across.
         */
        fun templates(letter: Letter): List<List<FloatArray>> = when (letter) {
            Letter.J -> listOf(0.3f, 0.42f, 0.55f).map { ratio ->
                val radius = ratio / 2f
                val raw = buildList {
                    add(floatArrayOf(0f, 0f)); add(floatArrayOf(0f, 1f))
                    for (k in 1..8) {
                        val a = Math.PI * k / 8
                        add(floatArrayOf((-radius + radius * Math.cos(a)).toFloat(), (1.0 + radius * Math.sin(a)).toFloat()))
                    }
                }
                normalise(resampleByArcLength(raw, MotionGate.RESAMPLE))
            }
            else -> listOf(
                normalise(resampleByArcLength(
                    listOf(floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(0f, 0.9f), floatArrayOf(1f, 0.9f)),
                    MotionGate.RESAMPLE,
                )),
            )
        }

        fun mirrorX(path: List<FloatArray>): List<FloatArray> = path.map { floatArrayOf(-it[0], it[1]) }.let { normalise(it) }

        /** Translate the start to the origin and scale so the larger side of the bounding box is 1. */
        fun normalise(path: List<FloatArray>): List<FloatArray> {
            val minX = path.minOf { it[0] }
            val maxX = path.maxOf { it[0] }
            val minY = path.minOf { it[1] }
            val maxY = path.maxOf { it[1] }
            val span = max(max(maxX - minX, maxY - minY), 1e-6f)
            val x0 = path.first()[0]
            val y0 = path.first()[1]
            return path.map { floatArrayOf((it[0] - x0) / span, (it[1] - y0) / span) }
        }

        fun resampleByArcLength(points: List<FloatArray>, count: Int): List<FloatArray> {
            val cumulative = FloatArray(points.size)
            for (i in 1 until points.size) {
                cumulative[i] = cumulative[i - 1] + hypot(points[i][0] - points[i - 1][0], points[i][1] - points[i - 1][1])
            }
            val total = cumulative.last()
            if (total <= 1e-6f) return List(count) { points.first().copyOf() }
            var segment = 0
            return List(count) { k ->
                val target = total * k / (count - 1)
                while (segment < points.size - 2 && cumulative[segment + 1] < target) segment++
                val length = cumulative[segment + 1] - cumulative[segment]
                val t = if (length <= 1e-9f) 0f else ((target - cumulative[segment]) / length).coerceIn(0f, 1f)
                floatArrayOf(
                    points[segment][0] + (points[segment + 1][0] - points[segment][0]) * t,
                    points[segment][1] + (points[segment + 1][1] - points[segment][1]) * t,
                )
            }
        }

        fun meanDistance(a: List<FloatArray>, b: List<FloatArray>): Float {
            var total = 0f
            for (i in a.indices) total += hypot(a[i][0] - b[i][0], a[i][1] - b[i][1])
            return total / a.size
        }
    }
}

/** Convenience for tests and callers: is [progress] the matched state. */
val MotionProgress.isMatched: Boolean get() = this is MotionProgress.Matched
