package dev.handspell.app.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

interface AppPreferencesStore {
    val themeMode: Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}

private val Context.appPreferences by preferencesDataStore(name = "app_preferences")

class DataStoreAppPreferencesStore(context: Context) : AppPreferencesStore {
    private val store = context.applicationContext.appPreferences
    private val themeKey = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = store.data.map { preferences ->
        ThemeMode.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }
}
