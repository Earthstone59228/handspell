package dev.handspell.app.vision.feedback

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.LetterScore
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.vision.FeedbackThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feedback engine decides when the app tells someone they signed a letter correctly, so these
 * tests are mostly about the ways it must refuse to: before the hold window, below the margin, too
 * far from any real exemplar, and after the prompt changed.
 *
 * Time is a parameter, not a clock — every frame carries its own timestamp — so a 400 ms hold window
 * is exercised in microseconds and there is nothing to flake.
 */
class DefaultFeedbackEngineTest {

    private val thresholds = FeedbackThresholds()

    /** A frame where [target] scores [probability] and [competitor] takes the rest. */
    private fun frame(
        target: Letter,
        probability: Float,
        timestampMs: Long,
        competitor: Letter = Letter.S,
        distance: Float = 0.1f,
        shapeDistance: Float = distance,
    ): Classification {
        val scores = listOf(
            LetterScore(target, probability),
            LetterScore(competitor, 1f - probability),
        ).sortedByDescending { it.probability }
        return Classification(
            ranked = scores,
            nearestDistance = distance,
            nearestShapeDistance = shapeDistance,
            timestampMs = timestampMs,
        )
    }

    private fun engine(thresholds: FeedbackThresholds = this.thresholds, target: Letter? = Letter.A) =
        DefaultFeedbackEngine(thresholds).apply { setTarget(target) }

    @Test
    fun `no match before the hold window has elapsed`() {
        val engine = engine()
        var timestamp = 0L
        while (timestamp < thresholds.holdToConfirmMs) {
            val state = engine.onFrame(frame(Letter.A, 0.97f, timestamp))
            assertTrue("matched at ${timestamp}ms, before the hold window", state is SignFeedbackState.Adjust)
            timestamp += FRAME_INTERVAL_MS
        }
        val confirmed = engine.onFrame(frame(Letter.A, 0.97f, thresholds.holdToConfirmMs))
        assertTrue(confirmed is SignFeedbackState.Match)
        assertEquals(thresholds.holdToConfirmMs, (confirmed as SignFeedbackState.Match).heldMs)
    }

    @Test
    fun `a single perfect frame never matches`() {
        val engine = engine()
        val state = engine.onFrame(frame(Letter.A, 1f, 0L))
        assertTrue(state is SignFeedbackState.Adjust)
        assertEquals(0f, (state as SignFeedbackState.Adjust).holdProgress, 0f)
    }

    @Test
    fun `hold progress rises monotonically to one and never exceeds it`() {
        val engine = engine()
        var previous = -1f
        var timestamp = 0L
        while (timestamp < thresholds.holdToConfirmMs) {
            val state = engine.onFrame(frame(Letter.A, 0.97f, timestamp)) as SignFeedbackState.Adjust
            assertTrue("progress went backwards at ${timestamp}ms", state.holdProgress > previous)
            assertTrue(state.holdProgress in 0f..1f)
            previous = state.holdProgress
            timestamp += FRAME_INTERVAL_MS
        }
        assertTrue(previous > 0.9f)
    }

    @Test
    fun `latch keeps the match visible through a score dip`() {
        val engine = engine()
        confirmMatch(engine)

        // The score collapses the frame after confirmation; the badge must not flicker.
        val dipped = engine.onFrame(frame(Letter.A, 0.05f, thresholds.holdToConfirmMs + FRAME_INTERVAL_MS))
        assertTrue(dipped is SignFeedbackState.Match)

        val stillLatched = engine.onFrame(
            frame(Letter.A, 0.05f, thresholds.holdToConfirmMs + thresholds.matchLatchMs - 1),
        )
        assertTrue(stillLatched is SignFeedbackState.Match)

        val afterLatch = engine.onFrame(
            frame(Letter.A, 0.05f, thresholds.holdToConfirmMs + thresholds.matchLatchMs),
        )
        assertFalse(afterLatch is SignFeedbackState.Match)
    }

    @Test
    fun `held ms freezes while the latch covers a dip`() {
        val engine = engine()
        val match = confirmMatch(engine)
        val dipped = engine.onFrame(
            frame(Letter.A, 0.05f, thresholds.holdToConfirmMs + 200),
        ) as SignFeedbackState.Match
        assertEquals(match.heldMs, dipped.heldMs)
    }

    @Test
    fun `changing the target resets the hold`() {
        val engine = engine()
        var timestamp = 0L
        while (timestamp < thresholds.holdToConfirmMs - FRAME_INTERVAL_MS) {
            engine.onFrame(frame(Letter.A, 0.97f, timestamp))
            timestamp += FRAME_INTERVAL_MS
        }

        engine.setTarget(Letter.B)
        engine.setTarget(Letter.A)

        // A pose held across the prompt change carries no credit: the window starts again here.
        val resumed = engine.onFrame(frame(Letter.A, 0.97f, timestamp))
        assertTrue(resumed is SignFeedbackState.Adjust)
        assertEquals(0f, (resumed as SignFeedbackState.Adjust).holdProgress, 0f)
        assertTrue(
            engine.onFrame(frame(Letter.A, 0.97f, timestamp + thresholds.holdToConfirmMs - 1))
                is SignFeedbackState.Adjust,
        )
        assertTrue(
            engine.onFrame(frame(Letter.A, 0.97f, timestamp + thresholds.holdToConfirmMs))
                is SignFeedbackState.Match,
        )
    }

    @Test
    fun `reset drops the smoothing state without dropping the target`() {
        val engine = engine()
        repeat(20) { engine.onFrame(frame(Letter.A, 0.97f, it * FRAME_INTERVAL_MS)) }
        engine.reset()

        val state = engine.onFrame(frame(Letter.A, 0.97f, 10_000L))
        assertTrue(state is SignFeedbackState.Adjust)
        assertEquals(Letter.A, state.target)
        assertEquals(0f, (state as SignFeedbackState.Adjust).holdProgress, 0f)
    }

    @Test
    fun `a below-margin frame never matches however long it is held`() {
        // The default thresholds cannot isolate the margin rule: a normalised vector with
        // p >= 0.85 always beats its runner-up by >= 0.70. Lowering matchProbability puts the
        // margin on its own, which is what this test is for.
        val marginOnly = FeedbackThresholds(matchProbability = 0.40f, adjustProbability = 0.20f)
        val engine = engine(marginOnly)

        var timestamp = 0L
        repeat(60) {
            val state = engine.onFrame(frame(Letter.A, 0.52f, timestamp, competitor = Letter.S))
            assertFalse("matched with a 0.04 margin at ${timestamp}ms", state is SignFeedbackState.Match)
            timestamp += FRAME_INTERVAL_MS
        }
    }

    @Test
    fun `stage one distance beyond the reject radius names no letter`() {
        val engine = engine()
        val state = engine.onFrame(
            frame(Letter.A, 0.99f, 0L, distance = thresholds.rejectDistance + 0.01f),
        )
        assertTrue(state is SignFeedbackState.NotRecognized)
        assertEquals(Letter.A, state.target)
    }

    @Test
    fun `stage one distance beyond the match radius adjusts but never matches`() {
        val engine = engine()
        val distance = (thresholds.matchDistance + thresholds.rejectDistance) / 2f
        var timestamp = 0L
        repeat(60) {
            val state = engine.onFrame(frame(Letter.A, 0.99f, timestamp, distance = distance))
            assertTrue(state is SignFeedbackState.Adjust)
            timestamp += FRAME_INTERVAL_MS
        }
    }

    @Test
    fun `direction agnostic letters use shape distance for acceptance`() {
        val engine = engine(target = Letter.A)
        val weightedDistance = thresholds.rejectDistance + 0.1f
        val shapeDistance = thresholds.matchDistance - 0.1f

        var timestamp = 0L
        while (timestamp < thresholds.holdToConfirmMs) {
            assertTrue(
                engine.onFrame(
                    frame(Letter.A, 0.99f, timestamp, distance = weightedDistance, shapeDistance = shapeDistance),
                ) is SignFeedbackState.Adjust,
            )
            timestamp += FRAME_INTERVAL_MS
        }
        assertTrue(
            engine.onFrame(
                frame(Letter.A, 0.99f, timestamp, distance = weightedDistance, shapeDistance = shapeDistance),
            ) is SignFeedbackState.Match,
        )
    }

    @Test
    fun `direction sensitive letters retain weighted distance acceptance`() {
        val engine = engine(target = Letter.K)
        val state = engine.onFrame(
            frame(
                Letter.K,
                0.99f,
                0L,
                distance = thresholds.rejectDistance + 0.1f,
                shapeDistance = thresholds.matchDistance - 0.1f,
            ),
        )

        assertTrue(state is SignFeedbackState.NotRecognized)
    }

    @Test
    fun `the mlp's NaN distance does not gate the match`() {
        val engine = engine()
        var timestamp = 0L
        repeat(20) {
            engine.onFrame(frame(Letter.A, 0.97f, timestamp, distance = Float.NaN))
            timestamp += FRAME_INTERVAL_MS
        }
        assertTrue(
            engine.onFrame(frame(Letter.A, 0.97f, timestamp, distance = Float.NaN))
                is SignFeedbackState.Match,
        )
    }

    @Test
    fun `no hand is reported only after three consecutive hand-free frames`() {
        val engine = engine()
        val lastSeen = engine.onFrame(frame(Letter.A, 0.60f, 0L))
        assertTrue(lastSeen is SignFeedbackState.Adjust)

        assertEquals(lastSeen, engine.onNoHand(33L))
        assertEquals(lastSeen, engine.onNoHand(66L))
        assertEquals(SignFeedbackState.NoHand(Letter.A), engine.onNoHand(99L))
        assertEquals(SignFeedbackState.NoHand(Letter.A), engine.onNoHand(132L))
    }

    @Test
    fun `a fresh engine reports no hand immediately`() {
        assertEquals(SignFeedbackState.NoHand(Letter.A), engine().onNoHand(0L))
    }

    @Test
    fun `losing the hand clears the hold`() {
        val engine = engine()
        var timestamp = 0L
        while (timestamp < thresholds.holdToConfirmMs - FRAME_INTERVAL_MS) {
            engine.onFrame(frame(Letter.A, 0.97f, timestamp))
            timestamp += FRAME_INTERVAL_MS
        }
        repeat(3) { engine.onNoHand(timestamp + it * FRAME_INTERVAL_MS) }

        val resumed = engine.onFrame(frame(Letter.A, 0.97f, timestamp + 200))
        assertEquals(0f, (resumed as SignFeedbackState.Adjust).holdProgress, 0f)
    }

    @Test
    fun `a stale latched match is not replayed while debouncing`() {
        val engine = engine()
        confirmMatch(engine)
        val state = engine.onNoHand(thresholds.holdToConfirmMs + thresholds.matchLatchMs)
        assertEquals(SignFeedbackState.NoHand(Letter.A), state)
    }

    @Test
    fun `adjust names the competitor's authored cue`() {
        val engine = engine()
        val state = engine.onFrame(frame(Letter.A, 0.55f, 0L, competitor = Letter.S)) as SignFeedbackState.Adjust
        assertEquals("hint_thumb_to_side", state.hint?.id)
        assertEquals(Letter.S, state.hint?.confusedWith)
    }

    @Test
    fun `adjust falls back to the generic hint for an undocumented confusion`() {
        val engine = engine()
        val state = engine.onFrame(frame(Letter.A, 0.55f, 0L, competitor = Letter.L)) as SignFeedbackState.Adjust
        assertEquals(ConfusableHints.GENERIC_HINT_ID, state.hint?.id)
        assertNull(state.hint?.confusedWith)
    }

    @Test
    fun `a target in the top three is named even below the adjust probability`() {
        val engine = engine()
        val ranked = listOf(
            LetterScore(Letter.S, 0.50f),
            LetterScore(Letter.T, 0.30f),
            LetterScore(Letter.A, 0.20f),
            LetterScore(Letter.M, 0.00f),
        )
        val state = engine.onFrame(Classification(ranked, nearestDistance = 0.1f, timestampMs = 0L))
        assertTrue(state is SignFeedbackState.Adjust)
        assertEquals(0.20f, (state as SignFeedbackState.Adjust).confidence, 1e-6f)
    }

    @Test
    fun `a target outside the top three and below the adjust probability is not named`() {
        val engine = engine()
        val ranked = listOf(
            LetterScore(Letter.S, 0.40f),
            LetterScore(Letter.T, 0.30f),
            LetterScore(Letter.M, 0.20f),
            LetterScore(Letter.A, 0.10f),
        )
        val state = engine.onFrame(Classification(ranked, nearestDistance = 0.1f, timestampMs = 0L))
        assertTrue(state is SignFeedbackState.NotRecognized)
    }

    @Test
    fun `a letter the classifier does not know is never named`() {
        val engine = DefaultFeedbackEngine(thresholds).apply { setTarget(Letter.W) }
        val ranked = listOf(LetterScore(Letter.S, 0.6f), LetterScore(Letter.T, 0.4f))
        val state = engine.onFrame(Classification(ranked, nearestDistance = 0.1f, timestampMs = 0L))
        assertTrue(state is SignFeedbackState.NotRecognized)
        assertEquals(0f, (state as SignFeedbackState.NotRecognized).confidence, 0f)
    }

    @Test
    fun `with no target the engine reports the hand without naming a letter`() {
        val engine = engine(target = null)
        val state = engine.onFrame(frame(Letter.A, 0.99f, 0L))
        assertTrue(state is SignFeedbackState.NotRecognized)
        assertNull(state.target)
        assertEquals(0.99f, (state as SignFeedbackState.NotRecognized).confidence, 1e-6f)
    }

    @Test
    fun `smoothing delays a match when the score is still climbing`() {
        val engine = engine()
        // 0.35 EMA needs several frames at 0.90 before the smoothed value clears 0.85, so a pose
        // that only just arrived cannot confirm at the instant the hold window would allow.
        var timestamp = 0L
        engine.onFrame(frame(Letter.A, 0.10f, timestamp))
        timestamp += FRAME_INTERVAL_MS
        repeat(12) {
            engine.onFrame(frame(Letter.A, 0.90f, timestamp))
            timestamp += FRAME_INTERVAL_MS
        }
        assertTrue(timestamp > thresholds.holdToConfirmMs)
        val state = engine.onFrame(frame(Letter.A, 0.90f, timestamp))
        assertTrue(state is SignFeedbackState.Adjust)
    }

    @Test
    fun `thresholds are validated so a sweep cannot configure a nonsense engine`() {
        val bad = listOf(
            FeedbackThresholds(emaAlpha = 0f),
            FeedbackThresholds(emaAlpha = 1.5f),
            FeedbackThresholds(holdToConfirmMs = 0L),
            FeedbackThresholds(noHandFrames = 0),
            FeedbackThresholds(probabilityBand = -0.1f),
            FeedbackThresholds(holdGraceMs = -1L),
        )
        for (thresholds in bad) {
            try {
                DefaultFeedbackEngine(thresholds)
                throw AssertionError("accepted $thresholds")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotEmpty())
            }
        }
    }

    // --- Hysteresis -----------------------------------------------------------------------------------

    /** Feeds [engine] frames of [probability] for [durationMs], returning the last state. */
    private fun feed(
        engine: DefaultFeedbackEngine, probability: Float, from: Long, durationMs: Long, distance: Float = 0.1f,
    ): SignFeedbackState {
        var state: SignFeedbackState = SignFeedbackState.NoHand(Letter.A)
        var t = from
        while (t < from + durationMs) {
            state = engine.onFrame(frame(Letter.A, probability, t, distance = distance))
            t += FRAME_INTERVAL_MS
        }
        return state
    }

    @Test
    fun `a probability between the exit and enter thresholds cannot start a hold`() {
        val engine = engine()
        // 0.78 is above the exit floor (0.85 - 0.13 = 0.72) but below the enter threshold: never matches from cold.
        val state = feed(engine, 0.78f, 0L, 2_000L)
        assertFalse(state is SignFeedbackState.Match)
    }

    @Test
    fun `an established hold survives a dip into the hysteresis band`() {
        val engine = engine()
        val start = 0L
        // Start strongly, then hover at 0.78 (between exit and enter). The hold must keep counting and confirm.
        feed(engine, 0.97f, start, 200L)
        val state = feed(engine, 0.78f, start + 200L, 600L)
        assertTrue("hold was dropped in the band: $state", state is SignFeedbackState.Match)
    }

    @Test
    fun `one bad frame within the grace period freezes the hold instead of resetting it`() {
        val engine = engine()
        val before = feed(engine, 0.97f, 0L, 200L) as SignFeedbackState.Adjust
        val glitch = engine.onFrame(frame(Letter.A, 0.05f, 200L, competitor = Letter.S))
        assertTrue(glitch is SignFeedbackState.Adjust)
        assertEquals(before.holdProgress, (glitch as SignFeedbackState.Adjust).holdProgress, 0.06f)
        val after = engine.onFrame(frame(Letter.A, 0.97f, 233L))
        assertTrue((after as SignFeedbackState.Adjust).holdProgress >= before.holdProgress)
    }

    @Test
    fun `a sustained loss beyond the grace period resets the hold`() {
        val engine = engine()
        feed(engine, 0.97f, 0L, 200L)
        val lost = feed(engine, 0.05f, 200L, 1_500L)
        assertFalse(lost is SignFeedbackState.Match)
        val restart = engine.onFrame(frame(Letter.A, 0.97f, 1_700L)) as SignFeedbackState.Adjust
        assertEquals(0f, restart.holdProgress, 0.01f)
    }

    @Test
    fun `adjust does not strobe to not-recognised on a single weak frame`() {
        val engine = engine()
        feed(engine, 0.6f, 0L, 200L)
        val weak = engine.onFrame(frame(Letter.A, 0.1f, 200L, competitor = Letter.S, distance = 0.5f))
        assertTrue("dropped to $weak", weak is SignFeedbackState.Adjust)
        val settled = feed(engine, 0.1f, 233L, 700L, distance = 0.5f)
        assertTrue("never dropped: $settled", settled is SignFeedbackState.NotRecognized)
    }

    /** Drives [engine] to a confirmed match at exactly `holdToConfirmMs`, so latch maths is exact. */
    private fun confirmMatch(engine: DefaultFeedbackEngine): SignFeedbackState.Match {
        var timestamp = 0L
        while (timestamp < thresholds.holdToConfirmMs) {
            engine.onFrame(frame(Letter.A, 0.97f, timestamp))
            timestamp += FRAME_INTERVAL_MS
        }
        return engine.onFrame(frame(Letter.A, 0.97f, thresholds.holdToConfirmMs)) as SignFeedbackState.Match
    }

    private companion object {
        /** ~30 fps, the rate the analyser delivers frames at. */
        const val FRAME_INTERVAL_MS = 33L
    }
}
