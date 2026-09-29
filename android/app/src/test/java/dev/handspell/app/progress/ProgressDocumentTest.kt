package dev.handspell.app.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressDocumentTest {
    @Test fun `missing document starts empty but malformed document is unreadable`() {
        assertNotNull(decodeProgressDocument(null))
        assertNull(decodeProgressDocument("{broken"))
        assertNull(decodeProgressDocument("[]"))
    }

    @Test fun `unsupported schema is preserved as unreadable`() {
        assertNull(decodeProgressDocument("""{"schemaVersion":2,"letters":[]}"""))
        assertEquals(ProgressSnapshot.SCHEMA_VERSION,
            decodeProgressDocument("""{"schemaVersion":1,"letters":[]}""")?.schemaVersion)
    }

    @Test fun `malformed document is backed up and transformed from empty progress`() {
        val raw = "{broken"
        val decision = planProgressWrite(raw, null) { it.copy(onboardingCompleted = true) }
        assertEquals(raw, decision.backup)
        assertTrue(decodeProgressDocument(decision.serialized)?.onboardingCompleted == true)
    }

    @Test fun `existing backup is never overwritten by another malformed document`() {
        val decision = planProgressWrite("{new damage", "{original damage") { it }
        assertNull(decision.backup)
        assertNotNull(decodeProgressDocument(decision.serialized))
    }

    @Test fun `valid document writes normally without backup`() {
        val decision = planProgressWrite("""{"schemaVersion":1,"letters":[]}""", null) {
            it.copy(currentStreakDays = 2)
        }
        assertNull(decision.backup)
        assertEquals(2, decodeProgressDocument(decision.serialized)?.currentStreakDays)
    }

    @Test fun `backup keeps snapshot unreadable flag set after reset`() {
        val valid = """{"schemaVersion":1,"letters":[]}"""
        assertTrue(progressUnreadableFlag(valid, decodeProgressDocument(valid), "{original damage"))
        assertTrue(progressUnreadableFlag("{broken", null, null))
        assertEquals(false, progressUnreadableFlag(valid, decodeProgressDocument(valid), null))
    }
}
