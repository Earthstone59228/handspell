package dev.handspell.app.ui.quest

import dev.handspell.app.progress.DayActivity
import dev.handspell.app.progress.ProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyQuestTest {
    private fun snapshot(today: DayActivity?) = ProgressSnapshot(
        ProgressSnapshot.SCHEMA_VERSION, emptyMap(), emptySet(), emptyList(), 0, 0, true, today = today,
    )

    @Test fun `the quest is fixed for a day and changes the next day`() {
        assertEquals(questFor(20_000), questFor(20_000))
        (20_000L until 20_010L).forEach { assertNotEquals(questFor(it), questFor(it + 1)) }
        assertEquals(Quest.entries.toSet(), (0L until 3L).map(::questFor).toSet())
        assertEquals(questFor(-1), questFor(2)) // floorMod, no crash before 1970
    }

    @Test fun `progress counts today's completions and caps at the target`() {
        val day = (0L..2L).first { questFor(it) == Quest.LETTERS }
        assertEquals(0, questProgress(null, day).done)
        val two = questProgress(snapshot(DayActivity(day, letters = setOf("A", "B"))), day)
        assertEquals(2, two.done); assertFalse(two.complete); assertEquals(2f / 3f, two.fraction, 0.001f)
        val five = questProgress(snapshot(DayActivity(day, letters = setOf("A", "B", "C", "D", "E"))), day)
        assertEquals(3, five.done); assertTrue(five.complete); assertEquals(1f, five.fraction, 0f)
        // Yesterday's record counts for nothing today.
        assertEquals(0, questProgress(snapshot(DayActivity(day - 1, letters = setOf("A", "B", "C"))), day).done)
    }

    @Test fun `words and speed quests read their own counts`() {
        val wordsDay = (0L..2L).first { questFor(it) == Quest.WORDS }
        assertTrue(questProgress(snapshot(DayActivity(wordsDay, words = setOf("yes", "no"))), wordsDay).complete)
        val speedDay = (0L..2L).first { questFor(it) == Quest.SPEED_ROUND }
        assertFalse(questProgress(snapshot(DayActivity(speedDay, letters = setOf("A", "B", "C"))), speedDay).complete)
        assertTrue(questProgress(snapshot(DayActivity(speedDay, speedRounds = 1)), speedDay).complete)
    }

    @Test fun `the reward shows once per day`() {
        val done = QuestProgress(Quest.SPEED_ROUND, 1, true)
        assertTrue(shouldCelebrateQuest(done, celebratedDay = null, day = 9))
        assertTrue(shouldCelebrateQuest(done, celebratedDay = 8, day = 9))
        assertFalse(shouldCelebrateQuest(done, celebratedDay = 9, day = 9))
        assertFalse(shouldCelebrateQuest(QuestProgress(Quest.LETTERS, 2, false), null, 9))
    }
}
