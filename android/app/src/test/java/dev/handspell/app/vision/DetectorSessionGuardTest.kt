package dev.handspell.app.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorSessionGuardTest {
    @Test fun staleHelpersCannotApplyErrorsToNewSession() {
        val sessions = DetectorSessionGuard()
        val oldOwner = Any()
        val newOwner = Any()
        val staleHelper = sessions.claim(oldOwner)
        val currentHelper = sessions.claim(newOwner)
        var status: DetectorStatus = DetectorStatus.Running

        sessions.ifCurrent(staleHelper) { status = DetectorStatus.Failed("late_error", null) }
        assertEquals(DetectorStatus.Running, status)
        assertFalse(sessions.release(oldOwner))
        assertTrue(sessions.owns(newOwner))
        sessions.ifCurrent(currentHelper) { status = DetectorStatus.Failed("current_error", null) }
        assertEquals(DetectorStatus.Failed("current_error", null), status)
    }
}
