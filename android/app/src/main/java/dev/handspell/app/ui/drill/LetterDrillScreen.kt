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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.handspell.app.R
import dev.handspell.app.content.PackItem
import dev.handspell.app.core.model.SignFeedbackState
import dev.handspell.app.ui.components.CameraFrame
import dev.handspell.app.ui.components.LandmarkOverlay
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.vision.DetectorStatus
import dev.handspell.app.vision.SignDetector

private const val CAMERA_PREVIEW_ASPECT_RATIO = 0.75f

/** Screen root: it owns the ViewModel; all children receive immutable state and callbacks. */
@Composable
fun LetterDrillRoute(
    drill: PackItem.Drill,
    drills: List<PackItem.Drill>,
    signDetector: SignDetector,
    onBack: () -> Unit,
    onSkip: (PackItem.Drill) -> Unit,
) {
    val viewModel: DrillViewModel = viewModel(
        key = "drill-${drill.id}",
        factory = DrillViewModel.factory(signDetector),
    )
    LaunchedEffect(drill, drills) { viewModel.setDrill(drill, drills) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LetterDrillScreen(
        state = state,
        onBack = onBack,
        onSkip = onSkip,
        onStartDetector = viewModel::startDetector,
        onRetry = viewModel::retryDetector,
        onCameraUnavailable = viewModel::onCameraUnavailable,
    )
}

@Composable
private fun LetterDrillScreen(
    state: DrillUiState,
    onBack: () -> Unit,
    onSkip: (PackItem.Drill) -> Unit,
    onStartDetector: () -> Unit,
    onRetry: () -> Unit,
    onCameraUnavailable: () -> Unit,
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

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DrillHeader(onBack)
        when {
            state.isLoading || state.drill == null -> DrillLoading()
            !permissionGranted -> CameraPermissionState(
                permanentlyDenied = permanentlyDenied,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
            )
            else -> {
                LaunchedEffect(state.drill.id) { onStartDetector() }
                ActiveDrill(state, onSkip, onRetry, onCameraUnavailable)
            }
        }
    }
}

@Composable
private fun DrillHeader(onBack: () -> Unit) = Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
) {
    TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
        Text(stringResource(R.string.back))
    }
    Text(stringResource(R.string.drill_title), style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun DrillLoading() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun CameraPermissionState(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(Spacing.md),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    Text(stringResource(R.string.camera_permission_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.camera_permission_body), Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.bodyLarge)
    Button(
        onClick = if (permanentlyDenied) onOpenSettings else onRequest,
        modifier = Modifier.padding(top = Spacing.lg).sizeIn(minHeight = Spacing.touchTarget),
    ) { Text(stringResource(if (permanentlyDenied) R.string.open_settings else R.string.allow_camera)) }
}

@Composable
private fun ActiveDrill(
    state: DrillUiState,
    onSkip: (PackItem.Drill) -> Unit,
    onRetry: () -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    val drill = state.drill ?: return
    when {
        state.detectorStatus is DetectorStatus.Failed -> DetectorFailure(state.detectorStatus, onRetry)
        state.cameraUnavailable -> CameraUnavailable(onRetry)
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            val binding = state.cameraBinding
            if (binding != null) {
                val cameraDescription = stringResource(R.string.camera_preview_content_description)
                Box(Modifier.fillMaxWidth().aspectRatio(CAMERA_PREVIEW_ASPECT_RATIO).clip(MaterialTheme.shapes.extraLarge)) {
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
                }
            }
            Text(drill.prompt, style = MaterialTheme.typography.displaySmall)
            if (state.classifierModelId == "knn-v1") {
                Text(stringResource(R.string.classifier_stage1_notice), style = MaterialTheme.typography.bodySmall, color = LocalAslColors.current.labelSecondary)
            }
            FeedbackBadge(state.feedback)
            Text(drill.description, style = MaterialTheme.typography.bodyMedium, color = LocalAslColors.current.labelSecondary)
            val next = state.drills.nextAfter(drill)
            if (next != null) {
                TextButton(onClick = { onSkip(next) }, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                    Text(stringResource(R.string.skip_letter))
                }
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
    Text(detectorMessage(failure.messageId), Modifier.padding(top = Spacing.xs), style = MaterialTheme.typography.bodyLarge)
    Button(onClick = onRetry, modifier = Modifier.padding(top = Spacing.lg).sizeIn(minHeight = Spacing.touchTarget)) {
        Text(stringResource(R.string.retry))
    }
}

@Composable
private fun CameraUnavailable(onRetry: () -> Unit) = Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.Center) {
    Surface(shape = MaterialTheme.shapes.medium, color = LocalAslColors.current.backgroundGrouped) {
        Text(stringResource(R.string.camera_unavailable), Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodyLarge)
    }
    Button(onClick = onRetry, modifier = Modifier.padding(top = Spacing.lg).sizeIn(minHeight = Spacing.touchTarget)) {
        Text(stringResource(R.string.retry))
    }
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
    Row(Modifier.semantics { contentDescription = message; liveRegion = LiveRegionMode.Polite }, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(Spacing.touchTarget), shape = CircleShape,
            color = if (visual.filled) visual.color else MaterialTheme.colorScheme.background,
            border = if (visual.filled) null else BorderStroke(Spacing.stroke, visual.color),
        ) {
            Box(contentAlignment = Alignment.Center) { Text(stringResource(visual.glyph), color = if (visual.filled) MaterialTheme.colorScheme.onPrimary else visual.color, style = MaterialTheme.typography.titleLarge) }
        }
        Text(message, style = MaterialTheme.typography.bodyLarge, color = visual.color)
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

private fun hasCameraPermission(context: android.content.Context): Boolean =
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
