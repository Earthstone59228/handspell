package dev.handspell.app.vision

import androidx.camera.core.ImageAnalysis
import dev.handspell.app.core.model.FeedbackHint
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.SignFeedbackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A [SignDetector] that needs no camera, no model files and no permission.
 *
 * It walks a fixed script — no hand, unrecognised, adjusting, match — on a timer, so screens can be
 * built and screenshot-tested against the real contract while the vision pipeline is still being
 * written. Swapping in the real detector is a one-line change in the app container.
 */
class FakeSignDetector(
    private val scope: CoroutineScope,
    private val stepMillis: Long = 900L,
) : SignDetector {

    private val statusState = MutableStateFlow<DetectorStatus>(DetectorStatus.Idle)
    private val feedbackState = MutableStateFlow<SignFeedbackState>(SignFeedbackState.NoHand(null))
    private var target: Letter? = null
    private var job: Job? = null

    override val status: StateFlow<DetectorStatus> = statusState.asStateFlow()

    override val feedback: Flow<SignFeedbackState> = feedbackState.asStateFlow()

    /** Drops every frame; the fake never looks at pixels. */
    override val analyzer: ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { image -> image.close() }

    override fun setTarget(target: Letter?) {
        this.target = target
        feedbackState.value = SignFeedbackState.NoHand(target)
    }

    override fun start() {
        if (job != null) return
        statusState.value = DetectorStatus.Starting
        job = scope.launch {
            statusState.value = DetectorStatus.Running
            while (isActive) {
                for (step in 0 until STEPS) {
                    feedbackState.value = stateFor(step, target)
                    delay(stepMillis)
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        statusState.value = DetectorStatus.Idle
        feedbackState.value = SignFeedbackState.NoHand(target)
    }

    private fun stateFor(step: Int, letter: Letter?): SignFeedbackState = when {
        letter == null || step == 0 -> SignFeedbackState.NoHand(letter)
        step == 1 -> SignFeedbackState.NotRecognized(letter, confidence = 0.21f)
        step == 2 -> SignFeedbackState.Adjust(letter, 0.52f, FeedbackHint("hint_generic", null), 0.3f)
        step == 3 -> SignFeedbackState.Adjust(letter, 0.78f, FeedbackHint("hint_generic", null), 0.8f)
        else -> SignFeedbackState.Match(letter, confidence = 0.93f, heldMs = 400L)
    }

    private companion object {
        const val STEPS = 5
    }
}
