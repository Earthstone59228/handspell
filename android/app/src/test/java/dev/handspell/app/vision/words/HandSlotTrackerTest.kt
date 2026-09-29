package dev.handspell.app.vision.words

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HandSlotTrackerTest {

    private fun hand(x: Float, y: Float) = FloatArray(42).also { it[0] = x; it[1] = y }

    @Test
    fun `two hands keep their slots when the reported order swaps`() {
        val tracker = HandSlotTracker()
        val left = hand(0.2f, 0.6f)
        val right = hand(0.8f, 0.6f)
        val first = tracker.assign(listOf(left, right), 0)
        assertSame(left, first[0]); assertSame(right, first[1])
        val nextLeft = hand(0.22f, 0.6f)
        val nextRight = hand(0.79f, 0.61f)
        val second = tracker.assign(listOf(nextRight, nextLeft), 33) // reported in the opposite order
        assertSame(nextLeft, second[0]); assertSame(nextRight, second[1])
    }

    @Test
    fun `a single hand stays in its slot and a second slot stays empty`() {
        val tracker = HandSlotTracker()
        tracker.assign(listOf(hand(0.8f, 0.5f), hand(0.2f, 0.5f)), 0) // slot 0 right side, slot 1 left side
        val only = hand(0.19f, 0.5f)
        val slots = tracker.assign(listOf(only), 33)
        assertNull(slots[0]); assertSame(only, slots[1])
    }

    @Test
    fun `no hands gives two empty slots`() {
        val slots = HandSlotTracker().assign(emptyList(), 0)
        assertNull(slots[0]); assertNull(slots[1])
    }

    @Test
    fun `a slot is forgotten after the forget interval`() {
        val tracker = HandSlotTracker()
        tracker.assign(listOf(hand(0.2f, 0.5f), hand(0.8f, 0.5f)), 0)
        val fresh = hand(0.79f, 0.5f)
        val slots = tracker.assign(listOf(fresh), HandSlotTracker.FORGET_MS + 100)
        assertSame(fresh, slots[0]) // history cleared, a lone new hand takes slot 0
    }

    @Test
    fun `a lone hand far from the remembered one takes the free slot`() {
        val tracker = HandSlotTracker()
        val a = hand(0.2f, 0.5f)
        tracker.assign(listOf(a), 0)
        val far = hand(0.9f, 0.5f)
        val slots = tracker.assign(listOf(far), 33)
        assertSame(far, slots[1]) // too far from slot 0's wrist to be the same hand
        assertNull(slots[0])
    }
}
