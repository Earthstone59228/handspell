package dev.handspell.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.handspell.app.content.PackItem
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.SpeedRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class SessionSave { IDLE, SAVING, SAVED, FAILED }

internal data class StorySessionState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val stepIndex: Int = 0,
    val letterIndex: Int = 0,
    val skippedLetter: Boolean = false,
    val skippedWords: Int = 0,
    val generation: Int = 0,
    val save: SessionSave = SessionSave.IDLE,
)

/** Keeps a partially spelled word through rotation and never credits a skipped word. */
internal class StorySessionViewModel(private val store: ProgressStore, private val saved: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(StorySessionState(
        stepIndex = saved["step"] ?: 0, letterIndex = saved["letter"] ?: 0,
        skippedLetter = saved["skipped"] ?: false, skippedWords = saved["skippedWords"] ?: 0,
        generation = saved["generation"] ?: 0,
    ))
    val state = mutableState.asStateFlow()
    private var steps = emptyList<PackItem.StoryStep>()
    private var saveJob: Job? = null
    private val pending = (saved.get<ArrayList<String>>("pending") ?: arrayListOf()).toMutableSet()

    suspend fun initialize(items: List<PackItem.StoryStep>) {
        steps = items
        try {
            if (saved.get<Boolean>("initialized") != true) {
                val completed = store.snapshot.first().completedStoryStepIds
                val firstUnread = items.indexOfFirst { it.id !in completed }.let { if (it < 0) items.size else it }
                update(mutableState.value.copy(stepIndex = firstUnread))
                saved["initialized"] = true
            }
            update(mutableState.value.copy(loading = false, loadFailed = false))
            persistPending()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { update(mutableState.value.copy(loading = false, loadFailed = true)) }
    }

    fun continueNarration(expectedStep: Int) {
        val current = mutableState.value
        if (current.stepIndex != expectedStep || steps.getOrNull(expectedStep)?.letters?.isEmpty() != true) return
        advance(true)
    }

    fun letterResult(expectedStep: Int, expectedLetter: Int, matched: Boolean) {
        val current = mutableState.value
        val step = steps.getOrNull(current.stepIndex) ?: return
        if (current.loading || expectedStep != current.stepIndex || expectedLetter != current.letterIndex) return
        if (current.letterIndex + 1 == step.letters.size) advance(matched && !current.skippedLetter)
        else update(current.copy(letterIndex = current.letterIndex + 1, skippedLetter = current.skippedLetter || !matched))
    }

    private fun advance(completed: Boolean) {
        val current = mutableState.value
        val step = steps.getOrNull(current.stepIndex) ?: return
        if (completed) {
            pending += step.id
            saved["pending"] = ArrayList(pending)
        }
        update(current.copy(stepIndex = current.stepIndex + 1, letterIndex = 0, skippedLetter = false,
            skippedWords = current.skippedWords + if (!completed && step.letters.isNotEmpty()) 1 else 0))
        if (completed) persistPending()
    }

    fun replay() = update(mutableState.value.copy(stepIndex = 0, letterIndex = 0, skippedLetter = false,
        skippedWords = 0, generation = mutableState.value.generation + 1))

    fun retrySave() = persistPending()

    private fun persistPending() {
        if (pending.isEmpty() || saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            update(mutableState.value.copy(save = SessionSave.SAVING))
            try {
                while (pending.isNotEmpty()) {
                    val id = pending.first()
                    store.recordStoryStep(id)
                    pending.remove(id)
                    saved["pending"] = ArrayList(pending)
                }
                update(mutableState.value.copy(save = SessionSave.SAVED))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { update(mutableState.value.copy(save = SessionSave.FAILED)) }
        }
    }

    private fun update(next: StorySessionState) {
        saved["step"] = next.stepIndex; saved["letter"] = next.letterIndex; saved["skipped"] = next.skippedLetter
        saved["skippedWords"] = next.skippedWords; saved["generation"] = next.generation
        mutableState.value = next
    }

    companion object {
        fun factory(store: ProgressStore) = sessionFactory { StorySessionViewModel(store, it) }
    }
}

internal data class SpeedSessionState(
    val roundId: String? = null,
    val remainingMs: Long = 0,
    val score: Int = 0,
    val promptIndex: Int = 0,
    val generation: Int = 0,
    val started: Boolean = false,
    val paused: Boolean = false,
    val finished: Boolean = false,
    val save: SessionSave = SessionSave.IDLE,
) {
    val remainingSeconds: Int get() = ((remainingMs + 999) / 1000).toInt()
}

/** A monotonic clock prevents wall-clock changes, late camera callbacks and rotations from adding time. */
internal class SpeedSessionViewModel(
    private val store: ProgressStore,
    private val saved: SavedStateHandle,
    private val monotonicTime: () -> Long,
    private val wallTime: () -> Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SpeedSessionState(
        roundId = saved["round"], remainingMs = saved["remaining"] ?: 0L,
        score = saved["score"] ?: 0, promptIndex = saved["prompt"] ?: 0, generation = saved["generation"] ?: 0,
        started = saved["started"] ?: false, paused = saved["started"] ?: false, finished = saved["finished"] ?: false,
    ))
    val state = mutableState.asStateFlow()
    private var round: PackItem.SpeedRound? = null
    private var deadline = 0L
    private var ticker: Job? = null
    private var saveJob: Job? = null

    fun initialize(rounds: List<PackItem.SpeedRound>) {
        round = rounds.firstOrNull { it.id == mutableState.value.roundId }
        if (round == null && mutableState.value.roundId != null) choose(null)
        if (mutableState.value.finished) retrySave()
    }

    fun choose(option: PackItem.SpeedRound?) {
        ticker?.cancel()
        round = option
        update(SpeedSessionState(roundId = option?.id, remainingMs = (option?.durationSeconds ?: 0) * 1000L,
            generation = mutableState.value.generation + 1))
        saved["completedAt"] = null
    }

    fun start() {
        val option = round ?: return
        if (mutableState.value.started) return
        saved["completedAt"] = null
        update(SpeedSessionState(option.id, option.durationSeconds * 1000L,
            generation = mutableState.value.generation + 1, started = true))
        resume()
    }

    fun pause() {
        val current = mutableState.value
        if (!current.started || current.finished || current.paused) return
        if (!tick()) return
        ticker?.cancel()
        update(mutableState.value.copy(paused = true))
    }

    fun resume() {
        val current = mutableState.value
        if (!current.started || current.finished || ticker?.isActive == true) return
        deadline = monotonicTime() + current.remainingMs
        update(current.copy(paused = false))
        ticker = viewModelScope.launch {
            while (tick()) delay(TICK_MILLIS)
        }
    }

    fun letterResult(expectedPrompt: Int, matched: Boolean) {
        val current = mutableState.value
        if (!current.started || current.paused || current.finished || current.promptIndex != expectedPrompt || !tick()) return
        update(mutableState.value.copy(score = current.score + if (matched) 1 else 0, promptIndex = current.promptIndex + 1))
    }

    private fun tick(): Boolean {
        val remaining = (deadline - monotonicTime()).coerceAtLeast(0)
        if (remaining == 0L) {
            update(mutableState.value.copy(remainingMs = 0, finished = true, paused = false))
            if (saved.get<Long>("completedAt") == null) saved["completedAt"] = wallTime()
            retrySave()
            return false
        }
        update(mutableState.value.copy(remainingMs = remaining))
        return true
    }

    fun retrySave() {
        val current = mutableState.value
        val option = round ?: return
        if (!current.finished || current.save == SessionSave.SAVED || saveJob?.isActive == true) return
        // Saved once per result, including after configuration or process recreation.
        val result = SpeedRunResult(option.id, saved["completedAt"] ?: wallTime(), current.score, option.durationSeconds)
        saveJob = viewModelScope.launch {
            update(mutableState.value.copy(save = SessionSave.SAVING))
            try {
                store.recordSpeedRun(result)
                update(mutableState.value.copy(save = SessionSave.SAVED))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { update(mutableState.value.copy(save = SessionSave.FAILED)) }
        }
    }

    private fun update(next: SpeedSessionState) {
        saved["round"] = next.roundId; saved["remaining"] = next.remainingMs; saved["score"] = next.score
        saved["prompt"] = next.promptIndex; saved["generation"] = next.generation; saved["started"] = next.started
        saved["finished"] = next.finished
        mutableState.value = next
    }

    companion object {
        private const val TICK_MILLIS = 100L
        fun factory(store: ProgressStore) = sessionFactory {
            SpeedSessionViewModel(store, it, android.os.SystemClock::elapsedRealtime, System::currentTimeMillis)
        }
    }
}

private fun <T : ViewModel> sessionFactory(create: (SavedStateHandle) -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V =
        create(extras.createSavedStateHandle()) as V
}
