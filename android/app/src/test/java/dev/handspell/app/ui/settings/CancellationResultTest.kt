package dev.handspell.app.ui.settings

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CancellationResultTest {
    @Test fun `cancellation escapes failure mapping`() {
        val cancellation = CancellationException("closed")
        assertEquals(cancellation, assertThrows(CancellationException::class.java) {
            runCatchingUnlessCancelled<Unit> { throw cancellation }.getOrDefault(Unit)
        })
    }

    @Test fun `ordinary failures remain available for UI mapping`() {
        val result = runCatchingUnlessCancelled<String> { throw IllegalStateException("offline") }
        assertEquals("fallback", result.getOrDefault("fallback"))
    }
}
