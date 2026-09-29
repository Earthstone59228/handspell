package dev.handspell.app.ui.home

import androidx.lifecycle.SavedStateHandle
import dev.handspell.app.content.PackItem
import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.SpeedRunResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProSessionViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val story = listOf(
        PackItem.StoryStep("narration", "Read this beat", null, emptyList()),
        PackItem.StoryStep("word", null, "AA", listOf(Letter.A, Letter.A)),
    )
    private val round = PackItem.SpeedRound("round", "Practice", 1, listOf(Letter.A), 1)

    @Test fun skippedWordIsNotCompletedAndStaleCallbackCannotAdvanceNextLetter() = runTest(dispatcher) {
        val store = FakeProgressStore()
        val model = StorySessionViewModel(store, SavedStateHandle())
        model.initialize(story)
        model.continueNarration(0)
        runCurrent()
        model.letterResult(1, 0, false)
        model.letterResult(1, 0, true) // Old prompt after skip; ignored.
        assertEquals(1, model.state.value.letterIndex)
        model.letterResult(1, 1, true)
        runCurrent()
        assertEquals(setOf("narration"), store.completed)
        assertEquals(1, model.state.value.skippedWords)
        assertEquals(2, model.state.value.stepIndex)
    }

    @Test fun resumeFindsFirstIncompleteBeatAndRestoresPartiallySpelledWord() = runTest(dispatcher) {
        val store = FakeProgressStore(setOf("narration"))
        val saved = SavedStateHandle()
        val original = StorySessionViewModel(store, saved)
        original.initialize(story)
        assertEquals(1, original.state.value.stepIndex)
        original.letterResult(1, 0, true)
        val restored = StorySessionViewModel(store, saved)
        restored.initialize(story)
        assertEquals(1, restored.state.value.letterIndex)
        restored.letterResult(1, 1, true)
        runCurrent()
        assertEquals(setOf("narration", "word"), store.completed)
        restored.replay()
        assertEquals(0, restored.state.value.stepIndex)
        assertEquals(1, restored.state.value.generation)
    }

    @Test fun storyWriteFailureKeepsPendingProgressForRetry() = runTest(dispatcher) {
        val store = FakeProgressStore().apply { failWrites = true }
        val model = StorySessionViewModel(store, SavedStateHandle())
        model.initialize(story)
        model.continueNarration(0)
        runCurrent()
        assertEquals(SessionSave.FAILED, model.state.value.save)
        store.failWrites = false
        model.retrySave()
        runCurrent()
        assertEquals(setOf("narration"), store.completed)
        assertEquals(SessionSave.SAVED, model.state.value.save)
    }

    @Test fun speedIgnoresLateAndDuplicateCallbacksAndSavesZeroOrPositiveScoreOnce() = runTest(dispatcher) {
        val store = FakeProgressStore()
        val model = SpeedSessionViewModel(store, SavedStateHandle(), { testScheduler.currentTime }, { 123L })
        model.choose(round)
        model.start()
        runCurrent()
        model.letterResult(0, true)
        model.letterResult(0, true)
        assertEquals(1, model.state.value.score)
        advanceTimeBy(1000)
        model.letterResult(1, true) // At deadline before next timer callback.
        runCurrent()
        assertTrue(model.state.value.finished)
        assertEquals(1, model.state.value.score)
        assertEquals(listOf(SpeedRunResult("round", 123, 1, 1)), store.runs)
        model.retrySave()
        runCurrent()
        assertEquals(1, store.runs.size)
    }

    @Test fun backgroundPausePreservesRemainingTimeAndZeroScoreCompletes() = runTest(dispatcher) {
        val store = FakeProgressStore()
        val saved = SavedStateHandle()
        val original = SpeedSessionViewModel(store, saved, { testScheduler.currentTime }, { 456L })
        original.choose(round)
        original.start()
        runCurrent()
        advanceTimeBy(400)
        original.pause()
        runCurrent()
        advanceTimeBy(5000)
        assertEquals(600L, original.state.value.remainingMs)
        assertTrue(original.state.value.paused)
        val restored = SpeedSessionViewModel(store, saved, { testScheduler.currentTime }, { 456L })
        restored.initialize(listOf(round))
        assertEquals(600L, restored.state.value.remainingMs)
        assertTrue(restored.state.value.paused)
        restored.resume()
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        assertTrue(restored.state.value.finished)
        assertEquals(0, store.runs.single().correct)
    }

    @Test fun failedSpeedSaveRetriesSameResultIdentity() = runTest(dispatcher) {
        val store = FakeProgressStore().apply { failWrites = true }
        var wall = 999L
        val model = SpeedSessionViewModel(store, SavedStateHandle(), { testScheduler.currentTime }, { wall })
        model.choose(round)
        model.start()
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(SessionSave.FAILED, model.state.value.save)
        store.failWrites = false
        wall = 1500L
        model.retrySave()
        runCurrent()
        assertEquals(999L, store.runs.single().completedAt)
        assertEquals(SessionSave.SAVED, model.state.value.save)
    }

    private class FakeProgressStore(completedIds: Set<String> = emptySet()) : ProgressStore {
        override val snapshot = MutableStateFlow(ProgressSnapshot(1, emptyMap(), completedIds, emptyList(), 0, 0, false))
        val completed = completedIds.toMutableSet()
        val runs = mutableListOf<SpeedRunResult>()
        var failWrites = false
        override suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?) = Unit
        override suspend fun recordStoryStep(stepId: String) {
            check(!failWrites)
            completed += stepId
            snapshot.value = snapshot.value.copy(completedStoryStepIds = completed)
        }
        override suspend fun recordSpeedRun(result: SpeedRunResult) { check(!failWrites); runs += result }
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun clearAll() = Unit
    }
}
