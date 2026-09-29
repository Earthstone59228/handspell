package dev.handspell.app.vision.words

import kotlin.math.hypot

/**
 * Keeps each physical hand in the same [WordFrame] slot from frame to frame. MediaPipe lists hands in whatever order
 * it likes, and the word features need a hand to stay in its slot for the whole window (the training data has stable
 * identities). A hand keeps the slot whose last wrist position is nearest; a slot that has not been seen for
 * [FORGET_MS] is forgotten. Not thread-safe.
 */
class HandSlotTracker {
    private val lastWrist = arrayOfNulls<FloatArray>(2)
    private val lastSeenMs = LongArray(2)

    /** [hands] are 42-float x, y arrays (0 to 2 of them); returns the two slots, null where no hand is assigned. */
    fun assign(hands: List<FloatArray>, timestampMs: Long): List<FloatArray?> {
        for (s in 0..1) if (lastWrist[s] != null && timestampMs - lastSeenMs[s] > FORGET_MS) lastWrist[s] = null
        val slots = arrayOfNulls<FloatArray>(2)
        when (hands.size) {
            0 -> Unit
            1 -> slots[nearestSlot(hands[0])] = hands[0]
            else -> {
                val a = hands[0]
                val b = hands[1]
                val keep = distance(a, 0) + distance(b, 1)
                val swap = distance(a, 1) + distance(b, 0)
                if (swap < keep) { slots[1] = a; slots[0] = b } else { slots[0] = a; slots[1] = b }
            }
        }
        for (s in 0..1) slots[s]?.let { lastWrist[s] = floatArrayOf(it[0], it[1]); lastSeenMs[s] = timestampMs }
        return slots.toList()
    }

    fun reset() {
        lastWrist.fill(null)
    }

    private fun nearestSlot(hand: FloatArray): Int {
        val d0 = lastWrist[0]?.let { dist(hand, it) }
        val d1 = lastWrist[1]?.let { dist(hand, it) }
        return when {
            d0 == null && d1 == null -> 0
            d1 == null -> 0.takeIf { d0 != null && d0 < NEW_HAND_DISTANCE } ?: 1
            d0 == null -> 1.takeIf { d1 < NEW_HAND_DISTANCE } ?: 0
            else -> if (d0 <= d1) 0 else 1
        }
    }

    /** Distance from a hand to a slot's last wrist; a slot with no history is neutral (a fixed middling distance). */
    private fun distance(hand: FloatArray, slot: Int): Float = lastWrist[slot]?.let { dist(hand, it) } ?: NEW_HAND_DISTANCE

    private fun dist(hand: FloatArray, wrist: FloatArray): Float = hypot(hand[0] - wrist[0], hand[1] - wrist[1])

    companion object {
        const val FORGET_MS = 700L

        /** Normalised image distance beyond which a hand is treated as a different hand than a slot's last one. */
        const val NEW_HAND_DISTANCE = 0.25f
    }
}
