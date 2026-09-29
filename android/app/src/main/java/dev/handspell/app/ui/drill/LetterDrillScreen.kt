package dev.handspell.app.ui.drill

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handspell.app.R
import dev.handspell.app.content.PackItem
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.core.model.Letter
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.CameraFrame
import dev.handspell.app.ui.components.FrameGeometry
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.components.SetupScreen
import dev.handspell.app.ui.components.LandmarkOverlay
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import kotlinx.coroutines.launch

internal const val CAMERA_PREVIEW_ASPECT_RATIO = 0.75f
internal val CAMERA_FRAME_MAX_WIDTH = 420.dp

/** Screen root: it owns the ViewModel; all children receive immutable state and callbacks. */
@Composable
fun LetterDrillRoute(
    drill: PackItem.Drill,
    drills: List<PackItem.Drill>,
    signDetector: SignDetector,
    canonicalHandshapeCatalog: CanonicalHandshapeCatalog,
    progressStore: ProgressStore,
    onBack: () -> Unit,
    onSkip: (PackItem.Drill) -> Unit,
    onMatch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sessionKey: String = drill.id,
    showHeader: Boolean = true,
    /** Called once per confirmed match (never for a skip), for the reward. Packs leave it null. */
    onCompleted: ((Letter) -> Unit)? = null,
) {
    val viewModel: DrillViewModel = viewModel(
        key = "drill",
        factory = DrillViewModel.factory(signDetector, canonicalHandshapeCatalog, progressStore),
    )
    LaunchedEffect(drill, drills, sessionKey) { viewModel.setDrill(drill, drills, sessionKey) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completion by viewModel.completion.collectAsStateWithLifecycle()
    LaunchedEffect(completion) {
        val letter = completion ?: return@LaunchedEffect
        if (onCompleted != null && state.sessionKey == sessionKey) onCompleted(letter)
        viewModel.consumeCompletion()
    }
    val scope = rememberCoroutineScope()
    LetterDrillScreen(
        state = if (state.sessionKey == sessionKey) state else DrillUiState(),
        onBack = onBack,
        onSkip = { next -> scope.launch { viewModel.recordSkip(); onSkip(next) } },
        onContinue = { next -> if (onMatch != null) onMatch() else onSkip(next) },
        onMatch = onMatch,
        modifier = modifier,
        thumbnails = viewModel.previewThumbnail,
        onStartDetector = viewModel::startDetector,
        onRetry = viewModel::retryDetector,
        onCameraUnavailable = viewModel::onCameraUnavailable,
        onDismissLowLight = viewModel::dismissLowLightNotice,
        showHeader = showHeader,
    )
}

@Composable
private fun LetterDrillScreen(
    state: DrillUiState,
    onBack: () -> Unit,
    onSkip: (PackItem.Drill) -> Unit,
    onContinue: (PackItem.Drill) -> Unit,
    onMatch: (() -> Unit)?,
    modifier: Modifier,
    thumbnails: kotlinx.coroutines.flow.StateFlow<android.graphics.Bitmap?>,
    onStartDetector: () -> Unit,
    onRetry: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onDismissLowLight: () -> Unit,
    showHeader: Boolean,
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
    val permanentlyDenied = permissionAsked && activity != null &&
        !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
    LaunchedEffect(state.feedback, state.drill?.id) {
        if (state.feedback is SignFeedbackState.Match && state.feedback.target == state.drill?.letter) onMatch?.invoke()
    }

    if (!state.isLoading && state.drill != null && !permissionGranted) {
        CameraPermissionState(
            permanentlyDenied = permanentlyDenied,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            onBack = onBack,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showHeader) DrillHeader(onBack)
        when {
            state.isLoading || state.drill == null -> DrillLoading()
            else -> {
                LaunchedEffect(state.drill.id) { onStartDetector() }
                ActiveDrill(state, thumbnails, onSkip, onContinue, onRetry, onCameraUnavailable, onDismissLowLight)
            }
        }
    }
}

@Composable
private fun DrillHeader(onBack: () -> Unit) = ScreenHeader(stringResource(R.string.drill_title), onBack, compact = true)

@Composable
private fun DrillLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge, color = LocalAslColors.current.labelSecondary)
}

@Composable
internal fun CameraPermissionState(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) = SetupScreen(
    illustration = "camera-permission-animation.html",
    title = stringResource(R.string.camera_permission_title),
    body = stringResource(R.string.camera_permission_body),
    actionLabel = stringResource(if (permanentlyDenied) R.string.open_settings else R.string.allow_camera),
    onAction = if (permanentlyDenied) onOpenSettings else onRequest,
    onBack = onBack,
    modifier = modifier,
)

@Composable
private fun ActiveDrill(
    state: DrillUiState,
    thumbnails: kotlinx.coroutines.flow.StateFlow<android.graphics.Bitmap?>,
    onSkip: (PackItem.Drill) -> Unit,
    onContinue: (PackItem.Drill) -> Unit,
    onRetry: () -> Unit,
    onCameraUnavailable: () -> Unit,
    onDismissLowLight: () -> Unit,
) {
    val drill = state.drill ?: return
    when {
        state.detectorStatus is DetectorStatus.Failed -> DetectorFailure(state.detectorStatus, onRetry)
        state.cameraUnavailable -> CameraUnavailable(onRetry)
        else -> {
            // Everything is sized to fit a normal phone without scrolling. The camera frame keeps its fixed
            // aspect ratio and is never squeezed; only when the screen is too short for the whole column does
            // it become scrollable (scrolling is switched off while it all fits, so there is no rubber-banding).
            val scroll = rememberScrollState()
            var frameSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            Column(
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(scroll, enabled = scroll.maxValue > 0)
                    .padding(horizontal = Spacing.md),
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
                        CameraFrame(
                            analyzer = binding.analyzer,
                            analyzerExecutor = binding.analyzerExecutor,
                            onCameraError = { onCameraUnavailable() },
                        )
                    }
                    LandmarkOverlay(state.overlay, Modifier.fillMaxSize().semantics {
                        contentDescription = cameraDescription
                    })
                    dev.handspell.app.ui.components.HandshapeGuide(
                        handshape = state.canonicalHandshape,
                        thumbnails = thumbnails,
                        frameSize = frameSize,
                        modifier = Modifier.align(Alignment.TopStart).padding(FrameGeometry.guideInset),
                    )
                }
            }
            Text(
                drill.prompt, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth().widthIn(max = CAMERA_FRAME_MAX_WIDTH),
            )
            if (state.lowLightNotice) Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.low_light_notice), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f), color = LocalAslColors.current.labelSecondary)
                TextButton(onClick = onDismissLowLight, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget),
                    colors = ButtonDefaults.textButtonColors(contentColor = LocalAslColors.current.label)) {
                    Text(stringResource(R.string.dismiss), fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                Modifier.fillMaxWidth().widthIn(max = CAMERA_FRAME_MAX_WIDTH),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                FeedbackBadge(state.feedback)
                Text(drill.description, style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.labelSecondary)
                if (drill.letter in setOf(Letter.R, Letter.T, Letter.U)) {
                    Text(stringResource(R.string.recognition_experimental_letter, drill.letter.display),
                        style = MaterialTheme.typography.bodySmall, color = LocalAslColors.current.labelSecondary)
                }
                if (state.classifierModelId == "knn-v1") {
                    Text(stringResource(R.string.classifier_stage1_notice), style = MaterialTheme.typography.labelMedium, color = LocalAslColors.current.labelTertiary)
                }
                val next = state.drills.nextAfter(drill)
                if (next != null) {
                    if (state.matched) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            AslButton(stringResource(R.string.skip_letter), { onSkip(next) }, Modifier.weight(1f))
                            AslButton(stringResource(R.string.continue_letter), { onContinue(next) }, Modifier.weight(1f))
                        }
                    } else {
                        AslButton(stringResource(R.string.skip_letter), { onSkip(next) }, Modifier.fillMaxWidth())
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

private fun List<PackItem.Drill>.nextAfter(drill: PackItem.Drill): PackItem.Drill? =
    takeIf { isNotEmpty() }?.get((indexOfFirst { it.id == drill.id }.coerceAtLeast(0) + 1).mod(size))

@Composable
private fun DetectorFailure(failure: DetectorStatus.Failed, onRetry: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(Spacing.md),
    verticalArrangement = Arrangement.Center,
) {
    Text(stringResource(R.string.drill_unavailable_title), style = MaterialTheme.typography.headlineSmall)
    Text(detectorMessage(failure.messageId), Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.bodyLarge,
        color = LocalAslColors.current.labelSecondary)
    AslButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth().padding(top = Spacing.xl))
}

@Composable
private fun CameraUnavailable(onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.Center) {
    AslCard {
        Text(stringResource(R.string.camera_unavailable), Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyLarge)
    }
    AslButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth().padding(top = Spacing.md))
}

@Composable
private fun FeedbackBadge(feedback: SignFeedbackState) {
    val colors = LocalAslColors.current
    val visual = when (feedback) {
        is SignFeedbackState.NoHand -> FeedbackVisual(R.string.feedback_no_hand_glyph, R.string.feedback_no_hand, colors.feedbackNeutral, false, null)
        is SignFeedbackState.NotRecognized -> FeedbackVisual(R.string.feedback_not_recognized_glyph, R.string.feedback_not_recognized, colors.feedbackNeutral, false, null)
        is SignFeedbackState.Adjust -> FeedbackVisual(R.string.feedback_adjust_glyph, R.string.feedback_adjust, colors.feedbackAdjust, false, feedback.holdProgress)
        is SignFeedbackState.Match -> FeedbackVisual(R.string.feedback_match_glyph, R.string.feedback_match, colors.feedbackMatch, true, null)
    }
    val message = when (feedback) {
        is SignFeedbackState.Adjust -> stringResource(visual.message, hintText(feedback.hint?.id))
        is SignFeedbackState.Match -> stringResource(visual.message, feedback.target.display)
        else -> stringResource(visual.message)
    }
    val ring = when (feedback) {
        is SignFeedbackState.NoHand -> FeedbackRing.Dotted
        is SignFeedbackState.NotRecognized -> FeedbackRing.Dashed
        is SignFeedbackState.Adjust -> FeedbackRing.Solid
        is SignFeedbackState.Match -> FeedbackRing.Filled
    }
    FeedbackBadgeView(stringResource(visual.glyph), message, visual.color, ring, visual.holdProgress)
}

/** Ring shapes of the three-state feedback (docs/DESIGN.md §3): each state differs in shape, not only colour. */
internal enum class FeedbackRing { Dotted, Dashed, Solid, Filled }

/** The drawn feedback badge, shared by the letter and word drills. */
@Composable
internal fun FeedbackBadgeView(
    glyph: String,
    message: String,
    color: androidx.compose.ui.graphics.Color,
    ring: FeedbackRing,
    holdProgress: Float?,
) {
    val colors = LocalAslColors.current
    Row(Modifier.semantics { contentDescription = message; liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(Spacing.touchTarget).drawBehind {
                val stroke = Spacing.stroke.toPx()
                val radius = size.minDimension / 2 - stroke / 2
                if (ring == FeedbackRing.Filled) drawCircle(color, radius = radius)
                else {
                    val pattern = when (ring) {
                        FeedbackRing.Dotted -> floatArrayOf(stroke, stroke * 2)
                        FeedbackRing.Dashed -> floatArrayOf(stroke * 3, stroke * 2)
                        else -> null
                    }
                    drawCircle(color, radius = radius,
                        style = Stroke(stroke, pathEffect = pattern?.let { PathEffect.dashPathEffect(it) }))
                    holdProgress?.let { progress ->
                        drawArc(color, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f),
                            useCenter = false, style = Stroke(stroke * 2))
                    }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, color = if (ring == FeedbackRing.Filled) MaterialTheme.colorScheme.onPrimary else color,
                style = MaterialTheme.typography.titleLarge)
        }
        Text(message, style = MaterialTheme.typography.bodyLarge, color = colors.label)
    }
}

private data class FeedbackVisual(val glyph: Int, val message: Int, val color: androidx.compose.ui.graphics.Color, val filled: Boolean, val holdProgress: Float?)

@Composable
private fun detectorMessage(id: String): String = stringResource(when (id) {
    "error_classifier_asset_missing" -> R.string.error_classifier_asset_missing
    "error_classifier_asset_unreadable" -> R.string.error_classifier_asset_unreadable
    "error_classifier_asset_malformed" -> R.string.error_classifier_asset_malformed
    "error_classifier_spec_mismatch" -> R.string.error_classifier_spec_mismatch
    "error_classifier_asset_corrupt" -> R.string.error_classifier_asset_corrupt
    else -> R.string.error_landmarker_failed
})

@Composable
private fun hintText(id: String?): String = stringResource(when (id) {
    "hint_thumb_between_index_middle" -> R.string.hint_thumb_between_index_middle
    "hint_thumb_across_front" -> R.string.hint_thumb_across_front
    "hint_thumb_to_side" -> R.string.hint_thumb_to_side
    "hint_thumb_under_three" -> R.string.hint_thumb_under_three
    "hint_thumb_under_two" -> R.string.hint_thumb_under_two
    "hint_spread_fingers" -> R.string.hint_spread_fingers
    "hint_fingers_together" -> R.string.hint_fingers_together
    "hint_cross_fingers" -> R.string.hint_cross_fingers
    "hint_point_down" -> R.string.hint_point_down
    "hint_point_forward" -> R.string.hint_point_forward
    "hint_index_up_curled" -> R.string.hint_index_up_curled
    "hint_three_straight" -> R.string.hint_three_straight
    "hint_hook_index" -> R.string.hint_hook_index
    else -> R.string.hint_generic
})

internal fun hasCameraPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
