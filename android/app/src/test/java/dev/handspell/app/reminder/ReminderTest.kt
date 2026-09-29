package dev.handspell.app.reminder

import dev.handspell.app.prefs.readReminderEnabled
import dev.handspell.app.prefs.readReminderSlot
import dev.handspell.app.prefs.reminderEnabledKey
import dev.handspell.app.prefs.reminderSlotKey
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.ui.settings.needsNotificationRationale
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderTest {
    private val bangkok = TimeZone.getTimeZone("Asia/Bangkok")
    private fun at(zone: TimeZone, y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        Calendar.getInstance(zone).apply { clear(); set(y, m, d, h, min) }.timeInMillis

    @Test fun `next reminder is today if the slot is ahead, otherwise tomorrow`() {
        val morning = at(bangkok, 2026, Calendar.SEPTEMBER, 30, 8, 0)
        assertEquals(at(bangkok, 2026, Calendar.SEPTEMBER, 30, 19), nextReminderAt(morning, ReminderSlot.EVENING, bangkok))
        assertEquals(at(bangkok, 2026, Calendar.SEPTEMBER, 30, 9), nextReminderAt(morning, ReminderSlot.MORNING, bangkok))
        val nine = at(bangkok, 2026, Calendar.SEPTEMBER, 30, 9, 0)
        assertEquals(at(bangkok, 2026, Calendar.OCTOBER, 1, 9), nextReminderAt(nine, ReminderSlot.MORNING, bangkok))
        val night = at(bangkok, 2026, Calendar.SEPTEMBER, 30, 23, 0)
        assertEquals(at(bangkok, 2026, Calendar.OCTOBER, 1, 14), nextReminderAt(night, ReminderSlot.AFTERNOON, bangkok))
    }

    @Test fun `reminder keeps its local hour across a daylight saving change`() {
        val ny = TimeZone.getTimeZone("America/New_York")
        val before = at(ny, 2026, Calendar.MARCH, 7, 20)
        assertEquals(at(ny, 2026, Calendar.MARCH, 8, 19), nextReminderAt(before, ReminderSlot.EVENING, ny))
    }

    private fun snapshot(last: Long?, streak: Int) = ProgressSnapshot(
        ProgressSnapshot.SCHEMA_VERSION, emptyMap(), emptySet(), emptyList(), streak, streak, true, lastPracticeDay = last,
    )

    @Test fun `no reminder once today has practice, streak copy while one is alive`() {
        val now = at(bangkok, 2026, Calendar.SEPTEMBER, 30, 19)
        val today = Math.floorDiv(now + bangkok.getOffset(now), 86_400_000L)
        assertEquals(ReminderDecision.Skip, reminderDecision(snapshot(today, 4), now, bangkok))
        assertEquals(ReminderDecision.KeepStreak(4), reminderDecision(snapshot(today - 1, 4), now, bangkok))
        assertEquals(ReminderDecision.Start, reminderDecision(snapshot(today - 2, 4), now, bangkok))
        assertEquals(ReminderDecision.Start, reminderDecision(null, now, bangkok))
    }

    @Test fun `reminder is off by default, evening by default, and asks only on Android 13+ without permission`() {
        assertFalse(readReminderEnabled(emptyPreferences()))
        assertTrue(readReminderEnabled(mutablePreferencesOf(reminderEnabledKey to true)))
        assertEquals(ReminderSlot.EVENING, readReminderSlot(emptyPreferences()))
        assertEquals(ReminderSlot.MORNING, readReminderSlot(mutablePreferencesOf(reminderSlotKey to "MORNING")))
        assertTrue(needsNotificationRationale(33, granted = false))
        assertFalse(needsNotificationRationale(33, granted = true))
        assertFalse(needsNotificationRationale(32, granted = false))
    }
}
