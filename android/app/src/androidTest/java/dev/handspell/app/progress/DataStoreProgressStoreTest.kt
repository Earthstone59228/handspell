package dev.handspell.app.progress

import androidx.test.platform.app.InstrumentationRegistry
import dev.handspell.app.core.model.Letter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataStoreProgressStoreTest {
    @Test fun recordsAndDeletesPracticeData() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().context
        val store = DataStoreProgressStore(context)
        store.clearAll()
        try {
            store.recordAttempt(Letter.A, matched = true, timeToMatchMs = 1200)
            store.recordAttempt(Letter.A, matched = false, timeToMatchMs = null)
            val recorded = store.snapshot.first().letters[Letter.A]!!
            assertEquals(2, recorded.attempts)
            assertEquals(1, recorded.matches)
            assertEquals(1200L, recorded.bestTimeToMatchMs)
            store.clearAll()
            assertTrue(store.snapshot.first().letters.isEmpty())
        } finally {
            store.clearAll()
        }
    }
}
