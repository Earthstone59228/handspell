package dev.handspell.app.ui.words

import androidx.camera.core.ImageAnalysis
import dev.handspell.app.content.Tier
import dev.handspell.app.content.WordEntry
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.SpeedRunResult
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.words.WordDetector
import dev.handspell.app.vision.words.WordProgress
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordDrillViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val hello = WordEntry("hello", "hello", Tier.FREE)
    private val bye = WordEntry("bye", "bye", Tier.FREE)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `a match is recorded once with its time and marks the session matched`() = runTest(dispatcher) {
        val detector = FakeWordDetector()
        val store = RecordingStore()
        var clock = 1_000L
        val model = WordDrillViewModel(detector, store) { clock }
        runCurrent()
        model.setWord(hello, listOf(hello, bye))
        assertEquals("hello", detector.currentTarget)
        model.startDetector()
        assertEquals(model, detector.owner)
        clock = 3_500L
        detector.progressState.value = WordProgress.Trying(0.4)
        runCurrent()
        detector.progressState.value = WordProgress.Matched
        runCurrent()
        detector.progressState.value = WordProgress.NoHand
        detector.progressState.value = WordProgress.Matched
        runCurrent()
        assertTrue(model.uiState.value.matched)
        assertEquals(listOf(Triple("hello", true, 2_500L)), store.attempts)
        assertEquals("hello", model.lastMatch.value)
        model.recordSkip()
        assertEquals(1, store.attempts.size)
    }

    @Test fun `a skip counts only after real signing`() = runTest(dispatcher) {
        val detector = FakeWordDetector()
        val store = RecordingStore()
        val model = WordDrillViewModel(detector, store) { 0L }
        runCurrent()
        model.setWord(hello, listOf(hello, bye))
        model.recordSkip()
        assertTrue(store.attempts.isEmpty())
        model.setWord(bye, listOf(hello, bye))
        detector.progressState.value = WordProgress.Trying(0.2)
        runCurrent()
        model.recordSkip()
        model.recordSkip()
        assertEquals(listOf(Triple("bye", false, null as Long?)), store.attempts)
        assertNull(model.lastMatch.value)
    }

    @Test fun `missing model is the unavailable state, not a crash`() = runTest(dispatcher) {
        val detector = FakeWordDetector(DetectorStatus.Failed(WORDS_UNAVAILABLE, null))
        val model = WordDrillViewModel(detector, RecordingStore()) { 0L }
        runCurrent()
        assertTrue(model.uiState.value.wordsUnavailable)
        assertFalse(WordDrillUiState(detectorStatus = DetectorStatus.Failed("error_landmarker_failed", null)).wordsUnavailable)
    }

    @Test fun `next word wraps and a single word has no next`() {
        assertEquals(bye, nextWord(listOf(hello, bye), hello))
        assertEquals(hello, nextWord(listOf(hello, bye), bye))
        assertNull(nextWord(listOf(hello), hello))
    }

    private class FakeWordDetector(initial: DetectorStatus = DetectorStatus.Idle) : WordDetector {
        override val status = MutableStateFlow(initial)
        val progressState = MutableStateFlow<WordProgress>(WordProgress.NoHand)
        override val progress = progressState
        override val overlay = MutableStateFlow<HandOverlay?>(null)
        override val previewThumbnail = MutableStateFlow<android.graphics.Bitmap?>(null)
        override val analyzer = ImageAnalysis.Analyzer { it.close() }
        override val analyzerExecutor = Executor { it.run() }
        var currentTarget: String? = null
        var owner: Any? = null
        override fun setTarget(gloss: String) { currentTarget = gloss; progressState.value = WordProgress.NoHand }
        override fun start(owner: Any) { this.owner = owner }
        override fun stop(owner: Any) { if (this.owner === owner) this.owner = null }
    }

    private class RecordingStore : ProgressStore {
        val attempts = mutableListOf<Triple<String, Boolean, Long?>>()
        override val snapshot = emptyFlow<ProgressSnapshot>()
        override suspend fun recordWordAttempt(gloss: String, matched: Boolean, timeToMatchMs: Long?) {
            attempts += Triple(gloss, matched, timeToMatchMs)
        }
        override suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?) = Unit
        override suspend fun recordStoryStep(stepId: String) = Unit
        override suspend fun recordSpeedRun(result: SpeedRunResult) = Unit
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun clearAll() = Unit
    }
}
