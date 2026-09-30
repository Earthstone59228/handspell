package dev.handspell.app.ui.pro

import dev.handspell.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProPromotionTest {
    @Test fun `mention only for free users at the trigger and never after not now`() {
        assertTrue(shouldMentionPro(triggered = true, isPro = false, dismissedThisSession = false))
        assertFalse(shouldMentionPro(triggered = false, isPro = false, dismissedThisSession = false))
        assertFalse(shouldMentionPro(triggered = true, isPro = true, dismissedThisSession = false))
        assertFalse(shouldMentionPro(triggered = true, isPro = false, dismissedThisSession = true))
    }

    @Test fun `comparison keeps letters and the first words free`() {
        val free = PRO_COMPARISON.associate { it.feature to it.free }
        assertEquals(R.string.compare_included, free[R.string.compare_letters])
        assertEquals(R.string.compare_included, free[R.string.compare_words])
        assertTrue(PRO_COMPARISON.all { it.pro != R.string.compare_not_included })
    }

    @Test fun `reward Pro line at most once a day, free users only, gone after not now`() {
        assertTrue(shouldShowRewardProLine(isPro = false, dismissedThisSession = false, lastShownDay = null, today = 10))
        assertTrue(shouldShowRewardProLine(false, false, lastShownDay = 9, today = 10))
        assertFalse(shouldShowRewardProLine(false, false, lastShownDay = 10, today = 10))
        assertFalse(shouldShowRewardProLine(isPro = true, dismissedThisSession = false, lastShownDay = null, today = 10))
        assertFalse(shouldShowRewardProLine(false, dismissedThisSession = true, lastShownDay = null, today = 10))
    }
}
