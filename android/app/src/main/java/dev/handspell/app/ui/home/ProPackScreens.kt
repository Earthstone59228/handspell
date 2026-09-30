package dev.handspell.app.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import dev.handspell.app.billing.EntitlementStatus
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.content.PackItem
import dev.handspell.app.content.PackKind
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import kotlinx.coroutines.CancellationException

@Composable
fun ProPackRoute(
    packId: String,
    repository: ContentRepository,
    drills: List<PackItem.Drill>,
    detector: SignDetector,
    catalog: CanonicalHandshapeCatalog,
    progress: ProgressStore,
    onBack: () -> Unit,
    contentLoading: Boolean = false,
    onRetryContent: () -> Unit = {},
) {
    var pack by remember(packId) { mutableStateOf<ContentPack?>(null) }
    var failed by remember(packId) { mutableStateOf(false) }
    var attempt by remember(packId) { mutableStateOf(0) }
    LaunchedEffect(packId, attempt) {
        failed = false
        pack = null
        try {
            pack = repository.pack(packId)
            failed = pack == null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val retry = { onRetryContent(); attempt++; Unit }
    val loadedPack = pack
    when {
        failed -> PackMessage(stringResource(R.string.pro_pack_unavailable), onBack, retry)
        loadedPack == null || contentLoading -> PackMessage(stringResource(R.string.content_loading), onBack, loading = true)
        loadedPack.items.isEmpty() -> PackMessage(stringResource(R.string.pro_empty_pack), onBack)
        drills.isEmpty() -> PackMessage(stringResource(R.string.content_unavailable_body), onBack, retry)
        loadedPack.kind == PackKind.STORY -> {
            val required = loadedPack.items.filterIsInstance<PackItem.StoryStep>().flatMap { it.letters }
            if (required.any { letter -> drills.none { it.letter == letter } })
                PackMessage(stringResource(R.string.pro_missing_letters), onBack)
            else StoryPack(loadedPack, drills, detector, catalog, progress, onBack)
        }
        loadedPack.kind == PackKind.SPEED -> SpeedPack(loadedPack, drills, detector, catalog, progress, onBack)
        else -> PackMessage(stringResource(R.string.pro_pack_unavailable), onBack)
    }
}

/** Unknown/offline access is visible and retryable; it never silently dismisses a pack. */
@Composable
fun ProPackAccess(
    status: EntitlementStatus,
    billingConfigured: Boolean,
    onBack: () -> Unit,
    onPaywall: () -> Unit,
    onRetry: () -> Unit,
) {
    val loading = status == EntitlementStatus.Loading || status == EntitlementStatus.Unknown
    Column(Modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        ScreenHeader(stringResource(R.string.settings_pro), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            Text(stringResource(R.string.pro_features_detail), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.paywall_free), style = MaterialTheme.typography.bodyMedium,
                color = LocalAslColors.current.labelSecondary)
            if (billingConfigured) Text(stringResource(if (dev.handspell.app.BuildConfig.REVENUECAT_API_KEY.startsWith("test_")) R.string.paywall_test_store else R.string.paywall_store_billing), style = MaterialTheme.typography.bodyLarge)
            when {
                !billingConfigured -> Text(stringResource(R.string.settings_pro_not_configured), style = MaterialTheme.typography.bodyLarge)
                loading -> {
                    CircularProgressIndicator(color = LocalAslColors.current.label)
                    Text(stringResource(R.string.pro_access_loading), style = MaterialTheme.typography.bodyLarge)
                }
                status is EntitlementStatus.Unavailable -> {
                    Text(stringResource(R.string.settings_pro_unavailable), style = MaterialTheme.typography.bodyLarge)
                    AslButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
                }
                else -> {
                    Text(stringResource(R.string.pro_access_required), style = MaterialTheme.typography.bodyLarge)
                    AslButton(stringResource(R.string.settings_see_pro), onPaywall, Modifier.fillMaxWidth())
                }
            }
        }
        AslButton(stringResource(R.string.pro_return_packs), onBack, Modifier.fillMaxWidth().padding(Spacing.md),
            style = AslButtonStyle.Secondary)
    }
}

@Composable
private fun PackMessage(message: String, onBack: () -> Unit, onRetry: (() -> Unit)? = null, loading: Boolean = false) {
    dev.handspell.app.ui.settings.FrostedSettingsHero(onBack = onBack, title = stringResource(R.string.home_pro_packs), body = null) {
        if (loading) CircularProgressIndicator(color = LocalAslColors.current.label)
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (onRetry != null) AslButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StoryPack(pack: ContentPack, drills: List<PackItem.Drill>, detector: SignDetector,
                      catalog: CanonicalHandshapeCatalog, progress: ProgressStore, onBack: () -> Unit) {
    val steps = remember(pack) { pack.items.filterIsInstance<PackItem.StoryStep>() }
    val model: StorySessionViewModel = viewModel(key = "story-${pack.packId}", factory = StorySessionViewModel.factory(progress))
    val state by model.state.collectAsStateWithLifecycle()
    var loadAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(pack.packId, loadAttempt) { model.initialize(steps) }
    when {
        state.loading -> { PackMessage(stringResource(R.string.content_loading), onBack, loading = true); return }
        state.loadFailed -> { PackMessage(stringResource(R.string.pro_progress_load_failed), onBack, { loadAttempt++ }); return }
    }
    val step = steps.getOrNull(state.stepIndex)
    Column(Modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        ScreenHeader(pack.title, onBack, compact = step?.letters?.isNotEmpty() == true)
        if (step == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Text(stringResource(R.string.story_complete), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.story_summary), style = MaterialTheme.typography.bodyLarge)
                if (state.skippedWords > 0) Text(stringResource(R.string.story_skipped_words, state.skippedWords),
                    style = MaterialTheme.typography.bodyLarge)
                SaveStatus(state.save, model::retrySave)
                AslButton(stringResource(R.string.story_again), model::replay, Modifier.fillMaxWidth(),
                    enabled = state.save != SessionSave.SAVING && state.save != SessionSave.FAILED)
                AslButton(stringResource(R.string.pro_return_packs), onBack, Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
            }
            return@Column
        }
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.story_step_count, state.stepIndex + 1, steps.size),
                style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.labelSecondary)
            LinearProgressIndicator(progress = { state.stepIndex.toFloat() / steps.size }, Modifier.fillMaxWidth(),
                color = LocalAslColors.current.label, trackColor = LocalAslColors.current.surface)
            if (step.letters.isNotEmpty()) Text(stringResource(R.string.story_spell_progress, step.spellWord.orEmpty(),
                state.letterIndex + 1, step.letters.size), style = MaterialTheme.typography.titleLarge)
            if (state.save == SessionSave.FAILED) SaveStatus(state.save, model::retrySave)
        }
        if (step.letters.isEmpty()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                AslCard { Text(step.narration.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.xl)) }
                AslButton(stringResource(R.string.story_next), { model.continueNarration(state.stepIndex) }, Modifier.fillMaxWidth())
            }
        } else {
            val currentLetter = step.letters.getOrNull(state.letterIndex)
            val drill = drills.firstOrNull { it.letter == currentLetter }
            if (drill != null) LetterDrillRoute(
                drill, drills, detector, catalog, progress, onBack,
                onSkip = { model.letterResult(state.stepIndex, state.letterIndex, false) },
                onMatch = { model.letterResult(state.stepIndex, state.letterIndex, true) },
                modifier = Modifier.weight(1f), sessionKey = "story-${pack.packId}-${state.generation}-${state.stepIndex}-${state.letterIndex}",
                showHeader = false,
            )
        }
    }
}

@Composable
private fun SpeedPack(pack: ContentPack, drills: List<PackItem.Drill>, detector: SignDetector,
                      catalog: CanonicalHandshapeCatalog, progress: ProgressStore, onBack: () -> Unit) {
    val rounds = remember(pack) { pack.items.filterIsInstance<PackItem.SpeedRound>() }
    val model: SpeedSessionViewModel = viewModel(key = "speed-${pack.packId}", factory = SpeedSessionViewModel.factory(progress))
    val state by model.state.collectAsStateWithLifecycle()
    val history by progress.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var exitDialog by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }
    fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun leave() { model.pause(); exitDialog = true }
    val back: () -> Unit = {
        if (state.started && !state.finished) leave() else onBack()
    }
    BackHandler(state.started && !state.finished, onBack = ::leave)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) { if (state.started) model.resume() else model.start() }
    }
    LaunchedEffect(pack.packId) { model.initialize(rounds) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) model.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { model.pause(); lifecycle.removeObserver(observer) }
    }
    val round = rounds.firstOrNull { it.id == state.roundId }
    val unavailable = round != null && round.letters.any { letter -> drills.none { it.letter == letter } }
    if (exitDialog) AlertDialog(
        onDismissRequest = { exitDialog = false },
        title = { Text(stringResource(R.string.speed_exit_title)) },
        text = { Text(stringResource(R.string.speed_exit_body), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = { exitDialog = false; onBack() }) { Text(stringResource(R.string.speed_end_round)) } },
        dismissButton = { TextButton(onClick = { exitDialog = false }) { Text(stringResource(R.string.speed_keep_playing)) } },
    )
    Column(Modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        ScreenHeader(round?.title ?: pack.title, back, compact = state.started && !state.finished)
        when {
            round == null -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.speed_rounds_description), style = MaterialTheme.typography.bodyLarge)
                rounds.forEach { option ->
                    val best = history?.speedRuns?.filter { it.roundId == option.id }?.maxOfOrNull { it.correct }
                    AslCard(Modifier.sizeIn(minHeight = Spacing.touchTarget).clickable(role = Role.Button) { model.choose(option) }) {
                        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(stringResource(R.string.speed_round_option, option.title, option.durationSeconds), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.speed_target, option.targetCorrect), style = MaterialTheme.typography.bodyMedium,
                                color = LocalAslColors.current.onSurfaceSecondary)
                            Text(if (best == null) stringResource(R.string.speed_no_best) else stringResource(R.string.speed_best, best),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            unavailable -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Text(stringResource(R.string.pro_missing_letters), style = MaterialTheme.typography.bodyLarge)
                AslButton(stringResource(R.string.speed_choose_round), { model.choose(null) }, Modifier.fillMaxWidth())
            }
            state.finished -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Text(stringResource(R.string.speed_result, state.score), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.speed_result_detail, round.durationSeconds, round.targetCorrect), style = MaterialTheme.typography.bodyLarge)
                val best = history?.speedRuns?.filter { it.roundId == round.id }?.maxOfOrNull { it.correct }
                if (best != null) Text(stringResource(R.string.speed_best, best), style = MaterialTheme.typography.bodyLarge)
                SaveStatus(state.save, model::retrySave)
                AslButton(stringResource(R.string.speed_again), { model.choose(round) }, Modifier.fillMaxWidth(),
                    enabled = state.save == SessionSave.SAVED)
                AslButton(stringResource(R.string.speed_choose_round), { model.choose(null) }, Modifier.fillMaxWidth(),
                    style = AslButtonStyle.Secondary, enabled = state.save == SessionSave.SAVED)
            }
            !state.started || state.paused -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Text(stringResource(if (state.paused) R.string.speed_paused else R.string.speed_intro,
                    if (state.paused) state.remainingSeconds else round.durationSeconds), style = MaterialTheme.typography.bodyLarge)
                if (state.paused) Text(stringResource(R.string.speed_paused_body, state.remainingSeconds), style = MaterialTheme.typography.bodyLarge)
                else {
                    Text(stringResource(R.string.speed_target, round.targetCorrect), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.speed_letters, round.letters.joinToString(" · ") { it.display }),
                        style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.labelSecondary)
                }
                if (cameraDenied || !cameraGranted()) Text(stringResource(R.string.speed_camera_required), style = MaterialTheme.typography.bodyLarge)
                if (cameraDenied) AslButton(stringResource(R.string.open_settings), {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                }, Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
                AslButton(stringResource(if (state.paused) R.string.speed_resume else R.string.speed_start), {
                    if (!cameraGranted()) permission.launch(Manifest.permission.CAMERA)
                    else if (state.paused) model.resume() else model.start()
                }, Modifier.fillMaxWidth())
                if (!state.started) AslButton(stringResource(R.string.speed_choose_round), { model.choose(null) },
                    Modifier.fillMaxWidth(), style = AslButtonStyle.Secondary)
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.speed_live, state.remainingSeconds, state.score), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = model::pause, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                        Text(stringResource(R.string.speed_pause), color = LocalAslColors.current.label)
                    }
                }
                val letter = round.letters[state.promptIndex % round.letters.size]
                val drill = drills.first { it.letter == letter }
                LetterDrillRoute(drill, drills, detector, catalog, progress, back,
                    onSkip = { model.letterResult(state.promptIndex, false) },
                    onMatch = { model.letterResult(state.promptIndex, true) }, modifier = Modifier.weight(1f),
                    sessionKey = "speed-${round.id}-${state.generation}-${state.promptIndex}", showHeader = false)
            }
        }
    }
}

@Composable
private fun SaveStatus(status: SessionSave, onRetry: () -> Unit) {
    val text = when (status) {
        SessionSave.IDLE -> return
        SessionSave.SAVING -> R.string.pro_progress_saving
        SessionSave.SAVED -> R.string.pro_progress_saved
        SessionSave.FAILED -> R.string.pro_progress_save_failed
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (status == SessionSave.FAILED) AslButton(stringResource(R.string.pro_retry_save), onRetry, Modifier.fillMaxWidth(),
            style = AslButtonStyle.Secondary)
    }
}
