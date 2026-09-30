package dev.handspell.app.vision.motion

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Handedness
import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.Letter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionLetterRecognizerTest {

    private enum class Shape { I, INDEX, OPEN, FIST }

    /** A 1000x1000 frame; hand size 120 px. [x], [y] place the wrist; y grows downward. */
    private fun hand(shape: Shape, x: Float, y: Float, t: Long): HandLandmarks {
        val hs = 120f
        val pts = Array(21) { floatArrayOf(0f, 0f) }
        fun set(i: Int, dx: Float, dy: Float) { pts[i] = floatArrayOf(x + dx * hs, y + dy * hs) }
        set(0, 0f, 0f)
        set(1, -0.5f, -0.3f); set(2, -0.8f, -0.5f); set(3, -1.0f, -0.7f); set(4, -1.2f, -0.9f)
        val mcpX = floatArrayOf(-0.4f, 0f, 0.4f, 0.75f) // index, middle, ring, pinky
        val mcpY = floatArrayOf(-0.95f, -1.0f, -0.95f, -0.85f)
        val extended = when (shape) {
            Shape.I -> booleanArrayOf(false, false, false, true)
            Shape.INDEX -> booleanArrayOf(true, false, false, false)
            Shape.OPEN -> booleanArrayOf(true, true, true, true)
            Shape.FIST -> booleanArrayOf(false, false, false, false)
        }
        for (f in 0..3) {
            val base = 5 + f * 4
            set(base, mcpX[f], mcpY[f])
            if (extended[f]) {
                set(base + 1, mcpX[f], mcpY[f] - 0.45f); set(base + 2, mcpX[f], mcpY[f] - 0.7f); set(base + 3, mcpX[f], mcpY[f] - 0.95f)
            } else {
                set(base + 1, mcpX[f], mcpY[f] - 0.3f); set(base + 2, mcpX[f], mcpY[f] - 0.1f); set(base + 3, mcpX[f], mcpY[f] + 0.05f)
            }
        }
        val image = pts.map { Landmark3(it[0], it[1], 0f) }.map { Landmark3(it.x / 1000f, it.y / 1000f, 0f) }
        return HandLandmarks(
            world = List(21) { Landmark3(0f, 0f, 0f) }, image = image, handedness = Handedness.RIGHT, handednessScore = 1f,
            timestampMs = t, imageWidth = 1000, imageHeight = 1000,
        )
    }

    /** Wrist positions in pixels are 0..1000; a path is in hand sizes (0.12 * 1000 = 120 px). */
    private fun run(letter: Letter, shape: Shape, path: List<Pair<Float, Float>>, fps: Int = 30, restFrames: Int = 6): MotionProgress {
        val recognizer = MotionLetterRecognizer(letter)
        var t = 0L
        var last: MotionProgress = MotionProgress.NoHand
        val startX = 500f
        val startY = 400f
        repeat(restFrames) { last = recognizer.onFrame(hand(shape, startX, startY, t), t); t += 1000L / fps }
        for ((dx, dy) in path) { last = recognizer.onFrame(hand(shape, startX + dx * 120f, startY + dy * 120f, t), t); t += 1000L / fps }
        repeat(maxOf(restFrames, 8)) {
            last = recognizer.onFrame(hand(shape, startX + path.last().first * 120f, startY + path.last().second * 120f, t), t); t += 1000L / fps
        }
        return last
    }

    private fun interpolate(corners: List<Pair<Float, Float>>, stepsPerSegment: Int): List<Pair<Float, Float>> = buildList {
        for (i in 0 until corners.size - 1) for (k in 0 until stepsPerSegment) {
            val t = k / stepsPerSegment.toFloat()
            add(corners[i].first + (corners[i + 1].first - corners[i].first) * t to corners[i].second + (corners[i + 1].second - corners[i].second) * t)
        }
        add(corners.last())
    }

    /** A pinky-tip J: down 2 hand sizes, then a hook. [sign] -1 hooks left, +1 right. */
    private fun jPath(size: Float = 1f, sign: Float = -1f): List<Pair<Float, Float>> {
        val down = interpolate(listOf(0f to 0f, 0f to 2.2f * size), 14)
        val hook = (1..14).map { k ->
            val a = PI * k / 14
            (sign * 0.9f * size * (1f - cos(a)).toFloat() * 0.5f * 2f * 0.5f) to (2.2f * size + 0.9f * size * sin(a).toFloat())
        }
        return down + hook
    }

    private fun zPath(size: Float = 1f, sign: Float = 1f): List<Pair<Float, Float>> =
        interpolate(listOf(0f to 0f, sign * 2.2f * size to 0f, 0f to 2.0f * size, sign * 2.2f * size to 2.0f * size), 12)

    @Test fun `a J drawn with the pinky out matches, at any size, either hook direction and speed`() {
        for (size in listOf(0.8f, 1.2f, 1.8f)) for (sign in listOf(-1f, 1f)) for (fps in listOf(15, 30)) {
            val result = run(Letter.J, Shape.I, jPath(size, sign), fps)
            assertTrue("J size $size sign $sign fps $fps -> $result", result is MotionProgress.Matched)
        }
    }

    @Test fun `a Z drawn with the index finger matches, at any size, either direction and speed`() {
        for (size in listOf(0.8f, 1.2f, 1.8f)) for (sign in listOf(-1f, 1f)) for (fps in listOf(15, 30)) {
            val result = run(Letter.Z, Shape.INDEX, zPath(size, sign), fps)
            assertTrue("Z size $size sign $sign fps $fps -> $result", result is MotionProgress.Matched)
        }
    }

    @Test fun `noisy drawing still matches`() {
        val rng = Random(11)
        var matched = 0
        repeat(20) {
            val noisy = zPath(1.2f).map { (x, y) -> (x + rng.nextFloat() * 0.16f - 0.08f) to (y + rng.nextFloat() * 0.16f - 0.08f) }
            if (run(Letter.Z, Shape.INDEX, noisy) is MotionProgress.Matched) matched++
        }
        assertTrue("noisy Z matched $matched of 20", matched >= 18)
    }

    @Test fun `the wrong letter's path or the wrong handshape does not match`() {
        assertFalse(run(Letter.J, Shape.I, zPath()) is MotionProgress.Matched)
        assertFalse(run(Letter.Z, Shape.INDEX, jPath()) is MotionProgress.Matched)
        assertEquals(MotionProgress.WrongShape, run(Letter.J, Shape.OPEN, jPath()))
        assertEquals(MotionProgress.WrongShape, run(Letter.Z, Shape.FIST, zPath()))
        assertFalse(run(Letter.J, Shape.INDEX, jPath()) is MotionProgress.Matched)
    }

    @Test fun `holding still, a straight line, a circle and jitter do not match`() {
        val still = List(40) { 0f to 0f }
        val line = interpolate(listOf(0f to 0f, 0f to 3f), 30)
        val circle = (0..40).map { k -> (1.2f * cos(2 * PI * k / 40)).toFloat() to (1.2f * sin(2 * PI * k / 40)).toFloat() }
        val jitter = List(40) { k -> (if (k % 2 == 0) 0.03f else -0.03f) to 0f }
        for (letter in listOf(Letter.J, Letter.Z)) {
            val shape = if (letter == Letter.J) Shape.I else Shape.INDEX
            for ((name, path) in listOf("still" to still, "line" to line, "circle" to circle, "jitter" to jitter)) {
                assertFalse("$letter $name", run(letter, shape, path) is MotionProgress.Matched)
            }
        }
    }

    @Test fun `random wandering with the right handshape almost never matches`() {
        val rng = Random(2026)
        var falseMatches = 0
        val trials = 300
        repeat(trials) { n ->
            val letter = if (n % 2 == 0) Letter.J else Letter.Z
            var x = 0f; var y = 0f
            val path = List(50) {
                x += (rng.nextFloat() - 0.5f) * 0.5f; y += (rng.nextFloat() - 0.5f) * 0.5f; x to y
            }
            if (run(letter, if (letter == Letter.J) Shape.I else Shape.INDEX, path) is MotionProgress.Matched) falseMatches++
        }
        assertTrue("false matches: $falseMatches of $trials", falseMatches <= trials / 100)
    }

    @Test fun `no hand is reported and a match stays matched until reset`() {
        val recognizer = MotionLetterRecognizer(Letter.Z)
        assertEquals(MotionProgress.NoHand, recognizer.onFrame(null, 0))
        var t = 0L
        var last: MotionProgress = MotionProgress.NoHand
        for ((dx, dy) in zPath()) { last = recognizer.onFrame(hand(Shape.INDEX, 500f + dx * 120f, 400f + dy * 120f, t), t); t += 33 }
        assertFalse("not judged until the fingertip rests", last is MotionProgress.Matched)
        repeat(8) { last = recognizer.onFrame(hand(Shape.INDEX, 500f + 2.2f * 120f, 400f + 2f * 120f, t), t); t += 33 }
        assertTrue(last is MotionProgress.Matched)
        assertTrue(recognizer.onFrame(null, t + 5000) is MotionProgress.Matched)
        recognizer.reset()
        assertEquals(MotionProgress.NoHand, recognizer.onFrame(null, t + 6000))
    }

    @Test fun `tiny detections expire a trace like missing hands`() {
        val recognizer = MotionLetterRecognizer(Letter.Z)
        val visible = hand(Shape.INDEX, 500f, 400f, 0)
        assertTrue(recognizer.onFrame(visible, 0) is MotionProgress.Tracing)
        val tiny = visible.copy(imageWidth = 1, imageHeight = 1)
        assertTrue(recognizer.onFrame(tiny, 100) is MotionProgress.Tracing)
        assertEquals(MotionProgress.NoHand, recognizer.onFrame(tiny, 450))
    }

    @Test fun `a detection gap cannot join separate parts of a stroke`() {
        val recognizer = MotionLetterRecognizer(Letter.Z)
        val path = zPath()
        var t = 0L
        for ((dx, dy) in path.take(path.size - 1)) {
            recognizer.onFrame(hand(Shape.INDEX, 500f + dx * 120f, 400f + dy * 120f, t), t)
            t += 33
        }
        t += 500
        val end = path.last()
        repeat(10) {
            assertFalse(recognizer.onFrame(hand(Shape.INDEX, 500f + end.first * 120f, 400f + end.second * 120f, t), t) is MotionProgress.Matched)
            t += 33
        }
    }

    @Test fun `a short endpoint pause does not count as a full rest`() {
        // Missing callbacks leave only two endpoints inside the rest window.
        // The unobserved interval must not be treated as a sustained hold.
        val recognizer = MotionLetterRecognizer(Letter.Z)
        var t = 0L
        for ((dx, dy) in zPath()) {
            recognizer.onFrame(hand(Shape.INDEX, 500f + dx * 120f, 400f + dy * 120f, t), t)
            t += 33
        }
        t += 250
        val end = hand(Shape.INDEX, 500f + 2.2f * 120f, 400f + 2f * 120f, t)
        assertFalse(recognizer.onFrame(end, t) is MotionProgress.Matched)
        assertFalse(recognizer.onFrame(end, t + 33) is MotionProgress.Matched)
    }

    @Test fun `changing hands cannot complete another hands stroke`() {
        val recognizer = MotionLetterRecognizer(Letter.Z)
        val path = zPath()
        var t = 0L
        for ((dx, dy) in path.take(path.size - 1)) {
            recognizer.onFrame(hand(Shape.INDEX, 500f + dx * 120f, 400f + dy * 120f, t), t)
            t += 33
        }
        val end = path.last()
        repeat(10) {
            val other = hand(Shape.INDEX, 500f + end.first * 120f, 400f + end.second * 120f, t)
                .copy(handedness = Handedness.LEFT)
            assertFalse(recognizer.onFrame(other, t) is MotionProgress.Matched)
            t += 33
        }
    }

    @Test fun `nonfinite image landmarks are rejected without poisoning the trace`() {
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val recognizer = MotionLetterRecognizer(Letter.Z)
            val visible = hand(Shape.INDEX, 500f, 400f, 0)
            val invalid = visible.copy(image = visible.image.mapIndexed { i, p -> if (i == 8) p.copy(x = bad) else p })
            assertEquals(MotionProgress.NoHand, recognizer.onFrame(invalid, 0))
            assertTrue(recognizer.onFrame(visible, 33) is MotionProgress.Tracing)
        }
    }

    @Test fun `a local wrong stroke is not hidden by low average deviation`() {
        val template = MotionLetterRecognizer.templates(Letter.J).first()
        val detour = template.mapIndexed { i, p ->
            if (i == 12) floatArrayOf(p[0] + 0.25f, p[1]) else p.copyOf()
        }
        assertTrue(MotionLetterRecognizer.meanDistance(detour, template) < MotionGate.MAX_DISTANCE)
        assertEquals(Float.MAX_VALUE, MotionLetterRecognizer.templateDistance(detour, template))
        assertEquals(0f, MotionLetterRecognizer.templateDistance(template, template))
    }

    @Test fun `only J and Z are motion letters`() {
        assertTrue(runCatching { MotionLetterRecognizer(Letter.A) }.isFailure)
    }
}
