package dev.handspell.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RootGateTest {
    @Test fun `waits for saved progress before choosing`() {
        assertEquals(RootGate.Loading, resolveGate(RootGate.Loading, null))
    }

    @Test fun `first run shows the introduction and a returning user sees the menu`() {
        assertEquals(RootGate.Onboarding, resolveGate(RootGate.Loading, false))
        assertEquals(RootGate.Menu, resolveGate(RootGate.Loading, true))
    }

    @Test fun `decision holds for the rest of the launch`() {
        // Deleting practice data clears the onboarding flag; that must not pull the user out of the menu.
        assertEquals(RootGate.Menu, resolveGate(RootGate.Menu, false))
        // Get started moves to the menu before the store write lands.
        assertEquals(RootGate.Menu, resolveGate(RootGate.Menu, null))
    }
}
