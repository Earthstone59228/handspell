package dev.handspell.app.progress

import dev.handspell.app.core.model.Letter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item 2: a complete letter or word stays practicable, and completing it again never breaks streak or stats. */
class CompleteAgainTest {
    @Test fun `matching a complete letter again adds to its counts and moves the streak once per day`() {
        var state = StoredProgress()
        state = recordLetter(state, Letter.A, true, 1500, now = 1, day = 100)
        state = recordLetter(state, Letter.A, true, 900, now = 2, day = 100)
        state = recordLetter(state, Letter.A, true, 1200, now = 3, day = 100)
        val a = state.letters.single()
        assertEquals(3, a.attempts)
        assertEquals(3, a.matches)
        assertEquals(900L, a.bestTimeToMatchMs)
        assertEquals(1, state.currentStreakDays)
        state = recordLetter(state, Letter.A, true, 800, now = 4, day = 101)
        assertEquals(2, state.currentStreakDays)
        assertEquals(2, state.longestStreakDays)
        assertEquals(1, state.letters.size)
    }

    @Test fun `a marked word can be matched afterwards and stays complete`() {
        var state = markWord(StoredProgress(), "home", true, day = 50)
        state = recordWord(state, "home", true, 2000, now = 5, day = 50)
        state = markWord(state, "home", true, day = 50)
        val home = state.words.single()
        assertTrue(home.markedComplete)
        assertEquals(1, home.matches)
        assertEquals(1, state.currentStreakDays)
    }

    @Test fun `today's completions reset on a new day and skips never count`() {
        var state = recordLetter(StoredProgress(), Letter.A, true, 1000, now = 1, day = 7)
        state = recordLetter(state, Letter.B, false, null, now = 2, day = 7)
        state = markLetter(state, "C", day = 7)
        state = recordWord(state, "yes", true, 900, now = 3, day = 7)
        assertEquals(setOf("A", "C"), state.today?.letters)
        assertEquals(setOf("yes"), state.today?.words)
        state = markLetter(state, "D", day = 8)
        assertEquals(8L, state.today?.day)
        assertEquals(setOf("D"), state.today?.letters)
        assertTrue(state.today?.words.orEmpty().isEmpty())
        assertEquals(2, state.currentStreakDays)
    }
}
