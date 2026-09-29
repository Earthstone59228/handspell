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
}
