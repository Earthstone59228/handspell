package dev.handspell.app.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface AppPreferencesStore {
    val themeMode: Flow<ThemeMode>
    val leftHanded: Flow<Boolean>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setLeftHanded(enabled: Boolean)

    /** When the demo trial was started (epoch ms), or null if never. Kept by "Delete practice data". */
    val demoTrialStartedAt: kotlinx.coroutines.flow.Flow<Long?> get() = kotlinx.coroutines.flow.flowOf(null)
    suspend fun setDemoTrialStartedAt(timeMs: Long) {}

    /** Daily streak reminder: off by default; evening unless changed. */
    val reminderEnabled: kotlinx.coroutines.flow.Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    val reminderSlot: kotlinx.coroutines.flow.Flow<dev.handspell.app.reminder.ReminderSlot>
        get() = kotlinx.coroutines.flow.flowOf(dev.handspell.app.reminder.ReminderSlot.EVENING)
    suspend fun setReminderEnabled(enabled: Boolean) {}
    suspend fun setReminderSlot(slot: dev.handspell.app.reminder.ReminderSlot) {}
}

internal val leftHandedKey = booleanPreferencesKey("left_handed")
internal val themeKey = stringPreferencesKey("theme_mode")
internal val reminderEnabledKey = booleanPreferencesKey("reminder_enabled")
internal val reminderSlotKey = stringPreferencesKey("reminder_slot")

internal fun readReminderEnabled(preferences: Preferences): Boolean = preferences[reminderEnabledKey] ?: false
internal fun readReminderSlot(preferences: Preferences): dev.handspell.app.reminder.ReminderSlot =
    dev.handspell.app.reminder.ReminderSlot.entries.firstOrNull { it.name == preferences[reminderSlotKey] }
        ?: dev.handspell.app.reminder.ReminderSlot.EVENING

internal val demoTrialKey = androidx.datastore.preferences.core.longPreferencesKey("demo_trial_started_at")

/** Saved Theme; anything unknown (or nothing) is System. */
internal fun readThemeMode(preferences: Preferences): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemeMode.SYSTEM
internal fun readLeftHanded(preferences: Preferences): Boolean = preferences[leftHandedKey] ?: false

private val Context.appPreferences by preferencesDataStore(name = "app_preferences")

class DataStoreAppPreferencesStore(context: Context) : AppPreferencesStore {
    private val store = context.applicationContext.appPreferences
    override val themeMode: Flow<ThemeMode> = store.data.map(::readThemeMode)
    override val leftHanded: Flow<Boolean> = store.data.map(::readLeftHanded)

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }

    override val demoTrialStartedAt: Flow<Long?> = store.data.map { it[demoTrialKey] }

    override suspend fun setDemoTrialStartedAt(timeMs: Long) {
        store.edit { if (it[demoTrialKey] == null) it[demoTrialKey] = timeMs }
    }

    override val reminderEnabled: Flow<Boolean> = store.data.map(::readReminderEnabled)
    override val reminderSlot: Flow<dev.handspell.app.reminder.ReminderSlot> = store.data.map(::readReminderSlot)

    override suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[reminderEnabledKey] = enabled }
    }

    override suspend fun setReminderSlot(slot: dev.handspell.app.reminder.ReminderSlot) {
        store.edit { it[reminderSlotKey] = slot.name }
    }

    override suspend fun setLeftHanded(enabled: Boolean) {
        store.edit { it[leftHandedKey] = enabled }
    }
}
