package dev.handspell.app.prefs

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeftHandedPreferenceTest {
    @Test fun `left-handed layout defaults off and reads saved value`() {
        assertFalse(readLeftHanded(emptyPreferences()))
        assertTrue(readLeftHanded(mutablePreferencesOf(leftHandedKey to true)))
    }

    @Test fun `theme defaults to system and reads saved light or dark`() {
        org.junit.Assert.assertEquals(ThemeMode.SYSTEM, readThemeMode(emptyPreferences()))
        org.junit.Assert.assertEquals(ThemeMode.LIGHT, readThemeMode(mutablePreferencesOf(themeKey to "LIGHT")))
        org.junit.Assert.assertEquals(ThemeMode.DARK, readThemeMode(mutablePreferencesOf(themeKey to "DARK")))
        org.junit.Assert.assertEquals(ThemeMode.SYSTEM, readThemeMode(mutablePreferencesOf(themeKey to "SEPIA")))
    }
}
