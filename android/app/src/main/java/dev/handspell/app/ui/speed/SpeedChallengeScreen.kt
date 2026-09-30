package dev.handspell.app.ui.speed

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handspell.app.R
import dev.handspell.app.content.PackItem
import dev.handspell.app.content.WordEntry
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.localPracticeDay
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.components.AslSheet
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.home.SpeedSessionViewModel
import dev.handspell.app.ui.pro.ProMention
import dev.handspell.app.ui.pro.shouldMentionPro
import dev.handspell.app.ui.settings.FrostedSettingsHero
import dev.handspell.app.ui.settings.SettingsActionRow
import dev.handspell.app.ui.settings.SettingsDivider
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.settings.SettingsGroupFooter
import dev.handspell.app.ui.settings.SettingsGroupHeader
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import dev.handspell.app.vision.words.WordDetector
import dev.handspell.app.ui.words.WordDrillRoute
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

enum class SpeedMode(val roundId: String) { LETTERS("daily-letters"), WORDS("daily-words") }

const val SPEED_CHALLENGE_SECONDS = 60

/**
 * The timed challenge's prompts: every available letter (or word) once in a shuffled order, then the same order
 * again if the learner gets through all of them. [seed] is fixed per round so a rotation keeps the order.
 */
fun challengePrompts(ids: List<String>, seed: Long): List<String> = ids.shuffled(Random(seed))

/** Best saved score for a mode, or null before its first finished round. */
fun bestScore(snapshot: ProgressSnapshot?, mode: SpeedMode): Int? =
    snapshot?.speedRuns?.filter { it.roundId == mode.roundId }?.maxOfOrNull { it.correct }

@Composable
fun SpeedChallengeRoute(
    drills: List<PackItem.Drill>,
    words: List<WordEntry>,
    wordsAvailable: Boolean,
    signDetector: SignDetector,
    catalog: CanonicalHandshapeCatalog,
    wordDetector: () -> WordDetector,
    progressStore: ProgressStore,
    snapshot: ProgressSnapshot?,
    references: Map<String, dev.handspell.app.content.WordReference> = emptyMap(),
    isPro: Boolean,
    proMentionDismissed: Boolean,
    onDismissProMention: () -> Unit,
    onSeePro: () -> Unit,
    onBack: () -> Unit,
) {
    val model: SpeedSessionViewModel = viewModel(key = "speed-daily", factory = SpeedSessionViewModel.factory(progressStore))
    val state by model.state.collectAsStateWithLifecycle()
    var modeName by rememberSaveable { mutableStateOf(SpeedMode.LETTERS.name) }
    var seed by rememberSaveable { mutableLongStateOf(0L) }
    val mode = SpeedMode.valueOf(modeName)
    val prompts = remember(mode, seed, drills, words) {
        challengePrompts(if (mode == SpeedMode.LETTERS) drills.map { it.id } else words.map { it.gloss }, seed)
    }
    val round = remember(mode, prompts) {
        PackItem.SpeedRound(mode.roundId, "", SPEED_CHALLENGE_SECONDS, emptyList(), targetCorrect = 0)
    }
    LaunchedEffect(round) { model.initialize(listOf(round)) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) model.pause() }
        lifecycle.addObserver(observer)
        onDispose { model.pause(); lifecycle.removeObserver(observer) }
    }
    val now = System.currentTimeMillis()
    val access = speedAccess(isPro, snapshot?.freeSpeedChallengeDay, now)
    fun beginRound() {
        if (!speedAccess(isPro, snapshot?.freeSpeedChallengeDay, System.currentTimeMillis()).canStart) return
        seed = System.nanoTime()
        model.choose(PackItem.SpeedRound(mode.roundId, "", SPEED_CHALLENGE_SECONDS, emptyList(), 0))
        model.start()
        if (!isPro) {
            val day = localPracticeDay(System.currentTimeMillis())
            // The free round counts as used once it starts, even if the screen closes during the write.
            scope.launch { withContext(NonCancellable) { progressStore.useFreeSpeedChallenge(day) } }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (state.started && state.paused) model.resume() else if (!state.started) beginRound() }
    }
    fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val running = state.started && !state.finished && state.roundId == mode.roundId
    var exitSheet by remember { mutableStateOf(false) }
    BackHandler(running && !exitSheet) { model.pause(); exitSheet = true }

    Box(Modifier.fillMaxSize()) {
        when {
            running && !state.paused -> RunningChallenge(
                mode, prompts, state.promptIndex, state.remainingSeconds, state.score, state.generation, drills, words,
                signDetector, catalog, wordDetector, progressStore, references,
                onResult = { matched -> model.letterResult(state.promptIndex, matched) },
                onPause = model::pause,
                onBack = { model.pause(); exitSheet = true },
            )
            running && state.paused -> PausedChallenge(state.remainingSeconds, onResume = {
                if (cameraGranted()) model.resume() else permission.launch(Manifest.permission.CAMERA)
            }, onBack = { exitSheet = true })
            state.finished && state.roundId == mode.roundId -> FinishedChallenge(
                mode, state.score, bestScore(snapshot, mode), access, now, isPro,
                mention = shouldMentionPro(access is SpeedAccess.UsedToday, isPro, proMentionDismissed),
                onAgain = { if (cameraGranted()) beginRound() else permission.launch(Manifest.permission.CAMERA) },
                onChoose = { model.choose(null) },
                onSeePro = onSeePro, onDismissMention = onDismissProMention, onBack = onBack,
            )
            else -> ChallengeIntro(
                mode, onMode = { modeName = it.name }, wordsAvailable = wordsAvailable && words.isNotEmpty(),
                bestLetters = bestScore(snapshot, SpeedMode.LETTERS), bestWords = bestScore(snapshot, SpeedMode.WORDS),
                access = access, now = now, loading = snapshot == null || drills.isEmpty(),
                onStart = { if (cameraGranted()) beginRound() else permission.launch(Manifest.permission.CAMERA) },
                onSeePro = onSeePro, onBack = onBack,
            )
        }
        AslSheet(visible = exitSheet, onDismiss = { exitSheet = false }) {
            Text(stringResource(R.string.speed_exit_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.speed_exit_body), style = MaterialTheme.typography.bodyLarge,
                color = LocalAslColors.current.labelSecondary)
            AslButton(stringResource(R.string.speed_keep_playing), { exitSheet = false; model.resume() }, Modifier.fillMaxWidth())
            AslButton(stringResource(R.string.speed_end_round), { exitSheet = false; model.choose(null); onBack() },
                Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
        }
    }
}

@Composable
private fun ChallengeIntro(
    mode: SpeedMode,
    onMode: (SpeedMode) -> Unit,
    wordsAvailable: Boolean,
    bestLetters: Int?,
    bestWords: Int?,
    access: SpeedAccess,
    now: Long,
    loading: Boolean,
    onStart: () -> Unit,
    onSeePro: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAslColors.current
    FrostedSettingsHero(onBack = onBack, title = stringResource(R.string.speed_challenge_title),
        body = stringResource(R.string.speed_challenge_body, SPEED_CHALLENGE_SECONDS)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SettingsGroupHeader(stringResource(R.string.speed_challenge_mode))
            SettingsGroup {
                Column(Modifier.selectableGroup()) {
                    ModeRow(stringResource(R.string.menu_letters), best = bestLetters, selected = mode == SpeedMode.LETTERS,
                        enabled = true, note = null) { onMode(SpeedMode.LETTERS) }
                    SettingsDivider()
                    ModeRow(stringResource(R.string.menu_words), best = bestWords, selected = mode == SpeedMode.WORDS,
                        enabled = wordsAvailable,
                        note = if (wordsAvailable) null else stringResource(R.string.words_unavailable_title)) { onMode(SpeedMode.WORDS) }
                }
            }
        }
        if (access is SpeedAccess.UsedToday) {
            // The locked state says plainly what happened, when it resets, and what Pro would change.
            val (hours, minutes) = timeUntil(access.resetsAtMs, now)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.speed_used_title), style = MaterialTheme.typography.headlineSmall, color = colors.label)
                Text(stringResource(R.string.speed_used_reset, hours, minutes), style = MaterialTheme.typography.bodyLarge,
                    color = colors.labelSecondary)
            }
            dev.handspell.app.ui.components.AslCard {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.speed_used_pro_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    Text(stringResource(R.string.speed_access_pro_note), style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceSecondary)
                    AslButton(stringResource(R.string.settings_see_pro), onSeePro, Modifier.fillMaxWidth())
                }
            }
        } else {
            Text(stringResource(if (access == SpeedAccess.Unlimited) R.string.speed_access_pro else R.string.speed_access_free),
                style = MaterialTheme.typography.bodyLarge, color = colors.label,
                modifier = Modifier.padding(horizontal = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite })
            val modeReady = mode == SpeedMode.LETTERS || wordsAvailable
            AslButton(stringResource(R.string.speed_start), onStart, Modifier.fillMaxWidth(), enabled = modeReady && !loading)
        }
        SettingsGroupFooter(stringResource(R.string.speed_challenge_footer))
    }
}

@Composable
private fun ModeRow(title: String, best: Int?, selected: Boolean, enabled: Boolean, note: String?, onSelect: () -> Unit) {
    val colors = LocalAslColors.current
    val alpha = if (enabled) 1f else 0.4f
    Row(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface.copy(alpha = alpha))
            Text(note ?: if (best == null) stringResource(R.string.speed_no_best) else stringResource(R.string.speed_challenge_best, best),
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary.copy(alpha = alpha))
        }
        if (selected && enabled) Text("✓", style = MaterialTheme.typography.titleLarge, color = colors.accent)
    }
}

@Composable
private fun RunningChallenge(
    mode: SpeedMode,
    prompts: List<String>,
    promptIndex: Int,
    remainingSeconds: Int,
    score: Int,
    generation: Int,
    drills: List<PackItem.Drill>,
    words: List<WordEntry>,
    signDetector: SignDetector,
    catalog: CanonicalHandshapeCatalog,
    wordDetector: () -> WordDetector,
    progressStore: ProgressStore,
    references: Map<String, dev.handspell.app.content.WordReference>,
    onResult: (Boolean) -> Unit,
    onPause: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAslColors.current
    Column(Modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        ScreenHeader(stringResource(R.string.speed_challenge_title), onBack, compact = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.speed_live, remainingSeconds, score), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
            AslButton(stringResource(R.string.speed_pause), onPause, style = AslButtonStyle.Secondary)
        }
        if (prompts.isEmpty()) return@Column
        val id = prompts[promptIndex % prompts.size]
        val key = "challenge-${mode.name}-$generation-$promptIndex"
        when (mode) {
            SpeedMode.LETTERS -> {
                val drill = drills.firstOrNull { it.id == id } ?: return@Column
                LetterDrillRoute(
                    drill, drills, signDetector, catalog, progressStore, onBack,
                    onSkip = { onResult(false) }, onMatch = { onResult(true) },
                    modifier = Modifier.weight(1f), sessionKey = key, showHeader = false,
                )
            }
            SpeedMode.WORDS -> {
                val word = words.firstOrNull { it.gloss == id } ?: return@Column
                WordDrillRoute(
                    word, words, wordDetector(), progressStore, onBack,
                    onNext = { onResult(false) }, onMatched = { onResult(true) },
                    showHeader = false, sessionKey = key, modifier = Modifier.weight(1f),
                    reference = references[word.gloss],
                )
            }
        }
    }
}

@Composable
private fun PausedChallenge(remainingSeconds: Int, onResume: () -> Unit, onBack: () -> Unit) {
    FrostedSettingsHero(onBack = onBack, title = stringResource(R.string.speed_challenge_title), body = null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Text(stringResource(R.string.speed_paused_body, remainingSeconds), style = MaterialTheme.typography.bodyLarge)
            AslButton(stringResource(R.string.speed_resume), onResume, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FinishedChallenge(
    mode: SpeedMode,
    score: Int,
    best: Int?,
    access: SpeedAccess,
    now: Long,
    isPro: Boolean,
    mention: Boolean,
    onAgain: () -> Unit,
    onChoose: () -> Unit,
    onSeePro: () -> Unit,
    onDismissMention: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAslColors.current
    FrostedSettingsHero(onBack = onBack, title = stringResource(R.string.speed_challenge_title), body = null) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }) {
            Text(pluralStringResource(if (mode == SpeedMode.LETTERS) R.plurals.speed_challenge_score_letters
                else R.plurals.speed_challenge_score_words, score, score), style = MaterialTheme.typography.headlineMedium)
            if (best != null) Text(stringResource(R.string.speed_challenge_best, maxOf(best, score)),
                style = MaterialTheme.typography.bodyLarge, color = colors.labelSecondary)
        }
        if (access.canStart) AslButton(stringResource(R.string.speed_again), onAgain, Modifier.fillMaxWidth())
        else {
            val (hours, minutes) = timeUntil((access as SpeedAccess.UsedToday).resetsAtMs, now)
            Text(stringResource(R.string.speed_access_used, hours, minutes), style = MaterialTheme.typography.bodyLarge)
        }
        if (mention && !isPro) ProMention(stringResource(R.string.speed_access_pro_note), onSeePro, onDismissMention)
        AslButton(stringResource(R.string.speed_choose_mode), onChoose, Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
    }
}
