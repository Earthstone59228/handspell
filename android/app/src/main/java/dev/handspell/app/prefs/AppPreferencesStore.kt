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
}

internal val leftHandedKey = booleanPreferencesKey("left_handed")
internal fun readLeftHanded(preferences: Preferences): Boolean = preferences[leftHandedKey] ?: false

private val Context.appPreferences by preferencesDataStore(name = "app_preferences")

class DataStoreAppPreferencesStore(context: Context) : AppPreferencesStore {
    private val store = context.applicationContext.appPreferences
    private val themeKey = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = store.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemeMode.SYSTEM
    }
    override val leftHanded: Flow<Boolean> = store.data.map(::readLeftHanded)

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }

    override suspend fun setLeftHanded(enabled: Boolean) {
        store.edit { it[leftHandedKey] = enabled }
    }
}
