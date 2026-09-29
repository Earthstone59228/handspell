package dev.handspell.app.ui.theme

import dev.handspell.app.prefs.ThemeMode
import dev.handspell.app.ui.alphabet.themeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {
    @Test fun `system follows the phone and light or dark are fixed`() {
        assertTrue(resolveDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(resolveDark(ThemeMode.SYSTEM, systemDark = false))
        assertFalse(resolveDark(ThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveDark(ThemeMode.DARK, systemDark = false))
    }

    @Test fun `quick toggle flips what is on screen and stores an explicit choice`() {
        assertEquals(ThemeMode.LIGHT, toggledTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(ThemeMode.DARK, toggledTheme(ThemeMode.SYSTEM, systemDark = false))
        assertEquals(ThemeMode.DARK, toggledTheme(ThemeMode.LIGHT, systemDark = false))
        assertEquals(ThemeMode.LIGHT, toggledTheme(ThemeMode.DARK, systemDark = false))
        // Two taps come back to what was on screen.
        val once = toggledTheme(ThemeMode.SYSTEM, systemDark = true)
        assertTrue(resolveDark(toggledTheme(once, systemDark = true), systemDark = true))
    }

    @Test fun `the web page gets the resolved appearance`() {
        assertEquals("dark", themeLabel(true))
        assertEquals("light", themeLabel(false))
    }
}
