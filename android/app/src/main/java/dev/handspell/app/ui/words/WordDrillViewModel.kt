package dev.handspell.app.ui.words

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.handspell.app.content.WordEntry
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.drill.DrillCameraBinding
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.words.WordDetector
import dev.handspell.app.vision.words.WordProgress
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One immutable state object for a word drill; mirrors [dev.handspell.app.ui.drill.DrillUiState]. */
data class WordDrillUiState(
    val sessionKey: String? = null,
    val word: WordEntry? = null,
    val words: List<WordEntry> = emptyList(),
    val detectorStatus: DetectorStatus = DetectorStatus.Idle,
    val progress: WordProgress = WordProgress.NoHand,
    val matched: Boolean = false,
    val overlay: HandOverlay? = null,
    val cameraBinding: DrillCameraBinding? = null,
    val cameraUnavailable: Boolean = false,
    val cameraSession: Int = 0,
) {
    /** The detector could not load its model: word signs are not in this build, so there is nothing to retry. */
    val wordsUnavailable: Boolean
        get() = (detectorStatus as? DetectorStatus.Failed)?.messageId == WORDS_UNAVAILABLE
}

internal const val WORDS_UNAVAILABLE = "error_words_unavailable"

/**
 * Drives [WordDetector] for one word at a time. Same rules as the letter drill: the detector is leased to this
 * ViewModel while the screen shows, a match is recorded once per session, and a skip only counts as an attempt
 * when the learner actually signed something.
 */
class WordDrillViewModel(
    private val detector: WordDetector,
    private val progressStore: ProgressStore,
    /** Starting the detector loads the hand model, which is slow, so it runs off the main thread. */
    private val startDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    private val monotonicTime: () -> Long = SystemClock::elapsedRealtime,
) : ViewModel() {
    private var startedAtMs = 0L
    private var attemptRecorded = false
    private var signedSomething = false
    private var activeSessionKey: String? = null
    private val mutableUiState = MutableStateFlow(
        WordDrillUiState(cameraBinding = DrillCameraBinding(detector.analyzer, detector.analyzerExecutor)),
    )
    val uiState: StateFlow<WordDrillUiState> = mutableUiState.asStateFlow()
    val previewThumbnail: StateFlow<android.graphics.Bitmap?> = detector.previewThumbnail

    /** Emits the gloss each time a word is matched, so the screen can show the reward once. */
    private val mutableMatches = MutableStateFlow<String?>(null)
    val lastMatch: StateFlow<String?> = mutableMatches.asStateFlow()

    /** The screen has shown the reward for [lastMatch]. */
    fun consumeMatch() { mutableMatches.value = null }

    init {
        viewModelScope.launch {
            detector.status.collect { status -> mutableUiState.update { it.copy(detectorStatus = status) } }
        }
        viewModelScope.launch {
            detector.overlay.collect { overlay -> mutableUiState.update { it.copy(overlay = overlay) } }
        }
        viewModelScope.launch {
            detector.progress.collect { progress -> onProgress(progress) }
        }
    }

    private fun onProgress(progress: WordProgress) {
        val word = mutableUiState.value.word ?: return
        if (mutableUiState.value.matched) return
        mutableUiState.update { it.copy(progress = progress) }
        if (progress is WordProgress.Trying) signedSomething = true
        if (progress == WordProgress.Matched && !attemptRecorded) {
            attemptRecorded = true
            mutableUiState.update { it.copy(matched = true) }
            val elapsed = (monotonicTime() - startedAtMs).coerceAtLeast(0L)
            mutableMatches.value = word.gloss
            viewModelScope.launch {
                withContext(NonCancellable) { progressStore.recordWordAttempt(word.gloss, true, elapsed) }
            }
        }
    }

    fun setWord(word: WordEntry, words: List<WordEntry>, sessionKey: String = word.gloss) {
        if (activeSessionKey == sessionKey) return
        activeSessionKey = sessionKey
        attemptRecorded = false
        signedSomething = false
        startedAtMs = monotonicTime()
        detector.stop(this)
        detector.setTarget(word.gloss)
        mutableMatches.value = null
        mutableUiState.update {
            it.copy(
                sessionKey = sessionKey, word = word, words = words, progress = WordProgress.NoHand,
                matched = false, overlay = null, cameraUnavailable = false,
            )
        }
    }

    fun startDetector() {
        if (mutableUiState.value.word != null) viewModelScope.launch(startDispatcher) { detector.start(this@WordDrillViewModel) }
    }

    fun onCameraUnavailable() = mutableUiState.update { it.copy(cameraUnavailable = true) }

    fun retryDetector() {
        val word = mutableUiState.value.word ?: return
        detector.stop(this)
        detector.setTarget(word.gloss)
        mutableUiState.update { it.copy(cameraUnavailable = false, cameraSession = it.cameraSession + 1) }
        viewModelScope.launch(startDispatcher) { detector.start(this@WordDrillViewModel) }
    }

    /** A skip after real signing is an unmatched attempt; a skip with no hand shown records nothing. */
    suspend fun recordSkip() {
        val word = mutableUiState.value.word ?: return
        if (attemptRecorded || !signedSomething) return
        attemptRecorded = true
        progressStore.recordWordAttempt(word.gloss, false, null)
    }

    override fun onCleared() {
        detector.stop(this)
        super.onCleared()
    }

    companion object {
        fun factory(detector: WordDetector, progressStore: ProgressStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    WordDrillViewModel(detector, progressStore) as T
            }
    }
}

/** The word after [word] in [words], wrapping; null when there is nothing else to practise. */
internal fun nextWord(words: List<WordEntry>, word: WordEntry): WordEntry? {
    if (words.size < 2) return null
    val index = words.indexOfFirst { it.gloss == word.gloss }
    return words[(index + 1).mod(words.size)]
}
