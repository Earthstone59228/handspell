package dev.handspell.app.ui.alphabet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedLettersTest {
    @Test fun `page report parses to a set of letters`() {
        assertEquals(setOf("A", "C"), parseCompletedLetters("""["A","C","A"]"""))
        assertEquals(emptySet<String>(), parseCompletedLetters("[]"))
    }

    @Test fun `anything else is rejected`() {
        assertNull(parseCompletedLetters("""["A","hello"]"""))
        assertNull(parseCompletedLetters("""{"A":1}"""))
        assertNull(parseCompletedLetters("""["a"]"""))
    }
}
