package dev.handspell.app.ui.words

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handspell.app.R
import dev.handspell.app.content.WordEntry
import dev.handspell.app.content.WordReference
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.onSizeChanged
import dev.handspell.app.ui.theme.AslPalette
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslTextButton
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.CameraFrame
import dev.handspell.app.ui.components.FrameGeometry
import dev.handspell.app.ui.components.LandmarkOverlay
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.components.SetupScreen
import dev.handspell.app.ui.drill.CAMERA_FRAME_MAX_WIDTH
import dev.handspell.app.ui.drill.CAMERA_PREVIEW_ASPECT_RATIO
import dev.handspell.app.ui.drill.CameraPermissionState
import dev.handspell.app.ui.drill.FeedbackBadgeView
import dev.handspell.app.ui.drill.FeedbackRing
import dev.handspell.app.ui.drill.hasCameraPermission
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.words.WordDetector
import dev.handspell.app.vision.words.WordProgress
import kotlinx.coroutines.launch

/** Screen root for one word. Same layout and lifecycle as [dev.handspell.app.ui.drill.LetterDrillRoute]. */
@Composable
fun WordDrillRoute(
    word: WordEntry,
    words: List<WordEntry>,
    detector: WordDetector,
    progressStore: ProgressStore,
    onBack: () -> Unit,
    onNext: (WordEntry) -> Unit,
    onMatched: (WordEntry) -> Unit = {},
    showHeader: Boolean = true,
    sessionKey: String = word.gloss,
    modifier: Modifier = Modifier,
    reference: WordReference? = null,
) {
    val viewModel: WordDrillViewModel = viewModel(key = "word-drill", factory = WordDrillViewModel.factory(detector, progressStore))
    LaunchedEffect(word, words, sessionKey) { viewModel.setWord(word, words, sessionKey) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lastMatch by viewModel.lastMatch.collectAsStateWithLifecycle()
    LaunchedEffect(lastMatch) {
        if (lastMatch == word.gloss) {
            onMatched(word)
            viewModel.consumeMatch()
        }
    }
    val scope = rememberCoroutineScope()
    WordDrillScreen(
        state = if (state.sessionKey == sessionKey) state else WordDrillUiState(),
        onBack = onBack,
        onSkip = { next -> scope.launch { viewModel.recordSkip(); onNext(next) } },
        onContinue = onNext,
        onStartDetector = viewModel::startDetector,
        onRetry = viewModel::retryDetector,
        onCameraUnavailable = viewModel::onCameraUnavailable,
        showHeader = showHeader,
        modifier = modifier,
        reference = reference,
        thumbnails = viewModel.previewThumbnail,
    )
}

@Composable
private fun WordDrillScreen(
    state: WordDrillUiState,
    onBack: () -> Unit,
    onSkip: (WordEntry) -> Unit,
    onContinue: (WordEntry) -> Unit,
    onStartDetector: () -> Unit,
    onRetry: () -> Unit,
    onCameraUnavailable: () -> Unit,
    showHeader: Boolean,
    modifier: Modifier,
    reference: WordReference?,
    thumbnails: kotlinx.coroutines.flow.StateFlow<android.graphics.Bitmap?>? = null,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionAsked = true
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionGranted = hasCameraPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val word = state.word
    // Without a model there is no camera to ask for: say so plainly, before any permission prompt.
    if (state.wordsUnavailable) {
        SetupScreen(
            title = stringResource(R.string.words_unavailable_title),
            body = stringResource(R.string.words_unavailable_body),
            actionLabel = stringResource(R.string.back),
            onAction = onBack,
            onBack = onBack,
        )
        return
    }
    val permanentlyDenied = permissionAsked && activity != null &&
        !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    if (word != null && !permissionGranted) {
        CameraPermissionState(
            permanentlyDenied = permanentlyDenied,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            onBack = onBack,
            modifier = Modifier,
        )
        return
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showHeader) ScreenHeader(stringResource(R.string.word_drill_title), onBack, compact = true)
        if (word == null) return@Column
        LaunchedEffect(word.gloss) { onStartDetector() }
        when {
            state.detectorStatus is DetectorStatus.Failed -> Column(
                Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.word_drill_failed_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.error_landmarker_failed), Modifier.padding(top = Spacing.xs),
                    style = MaterialTheme.typography.bodyLarge, color = LocalAslColors.current.labelSecondary)
                AslButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth().padding(top = Spacing.xl))
            }
            state.cameraUnavailable -> Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.Center) {
                AslCard {
                    Text(stringResource(R.string.camera_unavailable), Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyLarge)
                }
                AslButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth().padding(top = Spacing.md))
            }
            else -> ActiveWordDrill(state, word, reference, thumbnails, onSkip, onContinue, onCameraUnavailable)
        }
    }
}

@Composable
private fun ActiveWordDrill(
    state: WordDrillUiState,
    word: WordEntry,
    reference: WordReference?,
    thumbnails: kotlinx.coroutines.flow.StateFlow<android.graphics.Bitmap?>?,
    onSkip: (WordEntry) -> Unit,
    onContinue: (WordEntry) -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    val colors = LocalAslColors.current
    val scroll = rememberScrollState()
    var frameSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll, enabled = scroll.maxValue > 0).padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val binding = state.cameraBinding
        if (binding != null) {
            val cameraDescription = stringResource(R.string.camera_preview_content_description)
            Box(
                Modifier.fillMaxWidth().widthIn(max = CAMERA_FRAME_MAX_WIDTH).aspectRatio(CAMERA_PREVIEW_ASPECT_RATIO)
                    .clip(RoundedCornerShape(FrameGeometry.outerRadius))
                    .onSizeChanged { frameSize = it },
            ) {
                androidx.compose.runtime.key(state.cameraSession) {
                    CameraFrame(binding.analyzer, binding.analyzerExecutor, onCameraError = { onCameraUnavailable() })
                }
                LandmarkOverlay(state.overlay, Modifier.fillMaxSize().semantics { contentDescription = cameraDescription })
                WordReferenceCard(word, reference, thumbnails, frameSize,
                    Modifier.align(Alignment.TopStart).padding(FrameGeometry.guideInset))
            }
        }
        Text(
            stringResource(if (state.matched) R.string.word_prompt_matched else R.string.word_prompt),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth().widthIn(max = CAMERA_FRAME_MAX_WIDTH),
        )
        Column(
            Modifier.fillMaxWidth().widthIn(max = CAMERA_FRAME_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            WordFeedback(state.progress, state.matched, word)
            Text(stringResource(R.string.word_drill_hint), style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary)
            word.tip?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary) }
            val next = nextWord(state.words, word)
            if (next != null) {
                if (state.matched) AslButton(stringResource(R.string.continue_letter), { onContinue(next) }, Modifier.fillMaxWidth())
                AslTextButton(stringResource(R.string.skip_letter), { onSkip(next) }, Modifier.align(Alignment.CenterHorizontally))
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
}

/**
 * The word to sign, on the letter drill's reference card: same corner, inset, radius and live frosted patch of the
 * camera picture as HandshapeGuide, with the looping example and the word as its caption. Text is always light here
 * because the card sits on a darkened camera patch in both appearances.
 */
@Composable
private fun WordReferenceCard(
    word: WordEntry,
    reference: WordReference?,
    thumbnails: kotlinx.coroutines.flow.StateFlow<android.graphics.Bitmap?>?,
    frameSize: androidx.compose.ui.unit.IntSize,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.word_reference, word.display)
    var cardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    Box(
        modifier.clip(RoundedCornerShape(FrameGeometry.guideRadius)).onSizeChanged { cardSize = it }
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        dev.handspell.app.ui.components.LiveBackdrop(thumbnails, frameSize, cardSize, Modifier.matchParentSize())
        Column(
            Modifier.padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (reference != null) WordReferenceView(reference, word.display, Modifier.size(Spacing.referenceGuide),
                mutedColor = AslPalette.Paper.copy(alpha = 0.35f))
            Text(word.display, style = if (reference != null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.headlineSmall,
                color = AslPalette.Paper)
        }
    }
}

/** Never red: no hand is a dotted ring, signing a ring filling with the model's confidence, a match a filled disc. */
@Composable
private fun WordFeedback(progress: WordProgress, matched: Boolean, word: WordEntry) {
    val colors = LocalAslColors.current
    when {
        matched || progress == WordProgress.Matched -> FeedbackBadgeView(
            stringResource(R.string.feedback_match_glyph), stringResource(R.string.word_feedback_match, word.display),
            colors.feedbackMatch, FeedbackRing.Filled, null,
        )
        progress is WordProgress.Trying -> FeedbackBadgeView(
            stringResource(R.string.feedback_adjust_glyph), stringResource(R.string.word_feedback_trying),
            colors.feedbackAdjust, FeedbackRing.Solid, progress.probability.toFloat(),
        )
        else -> FeedbackBadgeView(
            stringResource(R.string.feedback_no_hand_glyph), stringResource(R.string.word_feedback_no_hand),
            colors.feedbackNeutral, FeedbackRing.Dotted, null,
        )
    }
}
