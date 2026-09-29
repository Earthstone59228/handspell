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
        assertNull(decodeProgressDocument("""{"schemaVersion":99,"letters":[]}"""))
        assertNull(decodeProgressDocument("""{"schemaVersion":0,"letters":[]}"""))
        assertEquals(ProgressSnapshot.SCHEMA_VERSION,
            decodeProgressDocument("""{"schemaVersion":1,"letters":[]}""")?.schemaVersion)
    }

    @Test fun `version 1 document migrates to the current schema keeping everything`() {
        val v1 = """{"schemaVersion":1,"letters":[{"letter":"A","attempts":3,"matches":2,"bestTimeToMatchMs":900,
            "lastPractisedAt":5}],"completedStoryStepIds":["s1"],"currentStreakDays":4,"longestStreakDays":6,
            "onboardingCompleted":true,"lastPracticeDay":20000}"""
        val migrated = decodeProgressDocument(v1)!!
        assertEquals(ProgressSnapshot.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(2, migrated.letters.single().matches)
        assertEquals(setOf("s1"), migrated.completedStoryStepIds)
        assertEquals(4, migrated.currentStreakDays)
        assertEquals(20000L, migrated.lastPracticeDay)
        assertTrue(migrated.words.isEmpty())
        assertNull(migrated.alphabetCompleted)
        // A write after migration keeps the old data and stamps the new version without a backup.
        val decision = planProgressWrite(v1, null) { recordWord(it, "hello", true, 1200, 7, 20000) }
        assertNull(decision.backup)
        val written = decodeProgressDocument(decision.serialized)!!
        assertEquals(2, written.letters.single().matches)
        assertEquals(1, written.words.single().matches)
    }

    @Test fun `word attempts count matches and keep the best time`() {
        var state = StoredProgress()
        state = recordWord(state, "hello", false, null, now = 1, day = 10)
        state = recordWord(state, "hello", true, 1500, now = 2, day = 10)
        state = recordWord(state, "hello", true, 900, now = 3, day = 11)
        val hello = state.words.single()
        assertEquals(3, hello.attempts)
        assertEquals(2, hello.matches)
        assertEquals(900L, hello.bestTimeToMatchMs)
        assertEquals(2, state.currentStreakDays)
    }

    @Test fun `marking a word complete is idempotent and undo keeps the streak`() {
        var state = markWord(StoredProgress(), "yes", true, day = 30)
        state = markWord(state, "yes", true, day = 30)
        assertEquals(1, state.words.size)
        assertTrue(state.words.single().markedComplete)
        assertEquals(1, state.currentStreakDays)
        state = markWord(state, "yes", false, day = 31)
        assertEquals(false, state.words.single().markedComplete)
        assertEquals(1, state.currentStreakDays)
        assertEquals(30L, state.lastPracticeDay)
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

    @Test fun `older documents get their practice days from the streak they record`() {
        val v1 = """{"schemaVersion":1,"currentStreakDays":3,"longestStreakDays":5,"lastPracticeDay":100}"""
        assertEquals(listOf(98L, 99L, 100L), decodeProgressDocument(v1)!!.practiceDays)
        assertEquals(emptyList<Long>(), decodeProgressDocument("""{"schemaVersion":1}""")!!.practiceDays)
        // A v2 document written before practice days existed is seeded the same way; a kept list is left alone.
        assertEquals(listOf(100L), decodeProgressDocument("""{"schemaVersion":2,"currentStreakDays":1,"lastPracticeDay":100}""")!!.practiceDays)
        assertEquals(listOf(50L, 100L), decodeProgressDocument("""{"schemaVersion":2,"lastPracticeDay":100,"practiceDays":[50,100]}""")!!.practiceDays)
    }

    @Test fun `each practice day is kept once and the list is capped`() {
        var state = StoredProgress(practiceDays = emptyList())
        state = advanceStreak(state, 10)
        state = advanceStreak(state, 10)
        state = advanceStreak(state, 12)
        assertEquals(listOf(10L, 12L), state.practiceDays)
        val full = StoredProgress(practiceDays = (1L..ProgressSnapshot.MAX_PRACTICE_DAYS.toLong()).toList())
        val next = advanceStreak(full, 1000)
        assertEquals(ProgressSnapshot.MAX_PRACTICE_DAYS, next.practiceDays!!.size)
        assertEquals(1000L, next.practiceDays!!.last())
        assertEquals(2L, next.practiceDays!!.first())
    }
}
