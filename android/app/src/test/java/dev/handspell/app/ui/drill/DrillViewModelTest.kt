package dev.handspell.app.ui.drill

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.lifecycle.ViewModelStore
import dev.handspell.app.content.PackItem
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.SpeedRunResult
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.DetectorSessionGuard
import dev.handspell.app.vision.SignDetector
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DrillViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun matchStaysCompletedAfterNoHandAndResetsForNextDrill() = runTest(dispatcher) {
        val detector = ControlledDetector()
        val progress = RecordingProgressStore()
        val model = DrillViewModel(detector, { null }, progress, monotonicTime = { 1000L })
        val a = PackItem.Drill("a", Letter.A, "A", "A", null)
        val b = PackItem.Drill("b", Letter.B, "B", "B", null)
        runCurrent()

        model.setDrill(a, listOf(a, b))
        runCurrent()
        assertFalse(model.uiState.value.matched)

        detector.feedbackState.value = SignFeedbackState.Match(Letter.A, 0.95f, 400L)
        runCurrent()
        assertTrue(model.uiState.value.matched)
        assertEquals(1, progress.matches)
        assertEquals(Letter.A, model.completion.value)
        model.consumeCompletion()
        assertEquals(null, model.completion.value)

        detector.feedbackState.value = SignFeedbackState.NoHand(Letter.A)
        runCurrent()
        assertTrue(model.uiState.value.matched)

        model.setDrill(b, listOf(a, b))
        runCurrent()
        assertFalse(model.uiState.value.matched)
        assertEquals(1, progress.matches)
        // A skip never produces a reward.
        detector.feedbackState.value = SignFeedbackState.NotRecognized(Letter.B, 0.2f)
        runCurrent()
        model.recordSkip()
        assertEquals(null, model.completion.value)
    }

    @Test fun oldViewModelClearCannotStopNewOwnersDetectorOrThumbnail() = runTest(dispatcher) {
        val detector = ControlledDetector()
        val old = DrillViewModel(detector, { null }, RecordingProgressStore(), monotonicTime = { 1000L })
        val next = DrillViewModel(detector, { null }, RecordingProgressStore(), monotonicTime = { 1000L })
        val oldStore = ViewModelStore().apply { put("old", old) }
        val a = PackItem.Drill("a", Letter.A, "A", "A", null)
        val b = PackItem.Drill("b", Letter.B, "B", "B", null)
        runCurrent()
        old.setDrill(a, listOf(a, b))
        old.startDetector()
        next.setDrill(b, listOf(a, b))
        next.startDetector()
        val thumbnail = uninitializedBitmap()
        detector.previewThumbnail.value = thumbnail
        assertEquals(DetectorStatus.Running, detector.status.value)
        oldStore.clear() // Navigation's outgoing entry clears after the new entry started.
        assertEquals(DetectorStatus.Running, detector.status.value)
        assertEquals(Letter.B, detector.currentTarget)
        assertSame(thumbnail, detector.previewThumbnail.value)
        detector.stop(next)
    }

    private fun uninitializedBitmap(): Bitmap {
        // Identity sentinel only; no Bitmap method is called in this JVM test.
        val unsafe = Class.forName("sun.misc.Unsafe")
        val field = unsafe.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return unsafe.getMethod("allocateInstance", Class::class.java)
            .invoke(field.get(null), Bitmap::class.java) as Bitmap
    }

    private class ControlledDetector : SignDetector {
        private val sessions = DetectorSessionGuard()
        var currentTarget: Letter? = null
        override val status = MutableStateFlow<DetectorStatus>(DetectorStatus.Idle)
        override val classifierModelId: String = "test"
        val feedbackState = MutableStateFlow<SignFeedbackState>(SignFeedbackState.NoHand(null))
        override val feedback = feedbackState
        override val overlay = MutableStateFlow<HandOverlay?>(null)
        override val previewThumbnail = MutableStateFlow<Bitmap?>(null)
        override val lowLightNotice = MutableStateFlow(false)
        override fun dismissLowLightNotice() = Unit
        override val analyzer = ImageAnalysis.Analyzer { image -> image.close() }
        override val analyzerExecutor = Executor { command -> command.run() }
        override fun setTarget(target: Letter?) { currentTarget = target; feedbackState.value = SignFeedbackState.NoHand(target) }
        override fun start(owner: Any) { sessions.claim(owner); status.value = DetectorStatus.Running }
        override fun stop(owner: Any) {
            if (!sessions.release(owner)) return
            status.value = DetectorStatus.Idle
            previewThumbnail.value = null
        }
    }

    private class RecordingProgressStore : ProgressStore {
        override val snapshot = MutableStateFlow(ProgressSnapshot(1, emptyMap(), emptySet(), emptyList(), 0, 0, false))
        var matches = 0
        override suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?) {
            if (matched) matches++
        }
        override suspend fun recordStoryStep(stepId: String) = Unit
        override suspend fun recordSpeedRun(result: SpeedRunResult) = Unit
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun clearAll() = Unit
    }
}
