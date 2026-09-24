package dev.handspell.app.ui.components

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executor

/**
 * Why [CameraFrame] could not show a live picture. Reported via [onCameraError] so the caller can
 * show a real state (docs/QUALITY.md §3) rather than a blank preview.
 */
sealed interface CameraBindError {
    data object NoFrontCamera : CameraBindError
    data class BindFailed(val cause: Throwable) : CameraBindError
}

private val TARGET_RESOLUTION = Size(640, 480)

/**
 * Front-camera live preview, mirrored to match a selfie mirror, with [analyzer] bound as the
 * `ImageAnalysis` use case's frame callback on [analyzerExecutor].
 *
 * Deliberately generic over [analyzer]/[analyzerExecutor] rather than taking a
 * `dev.handspell.app.vision.SignDetector` directly: the debug capture screen
 * (docs/CLASSIFIER.md §7) drives [dev.handspell.app.vision.landmarker.HandLandmarkerHelper]
 * straight from its own analyzer, with no classifier and no `SignDetector` in the picture, and
 * reuses this same composable.
 *
 * Camera permission is the caller's responsibility — this composable assumes it is already
 * granted and reports a bind failure (which includes a missing permission) via [onCameraError]
 * rather than requesting anything itself.
 */
@Composable
fun CameraFrame(
    analyzer: ImageAnalysis.Analyzer,
    analyzerExecutor: Executor,
    modifier: Modifier = Modifier,
    onCameraError: (CameraBindError) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    TARGET_RESOLUTION,
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                ),
            )
            .build()
    }

    val previewView = remember(context) {
        PreviewView(context).apply {
            // No scaleX flip here: PreviewView already renders the front camera as a selfie mirror. An earlier version
            // flipped it again, which un-mirrored the picture so left and right looked inverted. LandmarkOverlay maps
            // the analysed frame onto this same picture (see its documentation).
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val providerFuture = remember(context) { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(lifecycleOwner, providerFuture, analyzer, analyzerExecutor, resolutionSelector) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val provider = providerFuture.get()
                val selector = CameraSelector.DEFAULT_FRONT_CAMERA
                if (!provider.hasCamera(selector)) {
                    onCameraError(CameraBindError.NoFrontCamera)
                    return@addListener
                }
                val builtPreview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val builtAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analyzerExecutor, analyzer) }
                preview = builtPreview
                analysis = builtAnalysis
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, builtPreview, builtAnalysis)
                boundProvider = provider
            } catch (error: Throwable) {
                onCameraError(CameraBindError.BindFailed(error))
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            val provider = boundProvider
            val boundPreview = preview
            val boundAnalysis = analysis
            if (provider != null && boundPreview != null && boundAnalysis != null) {
                provider.unbind(boundPreview, boundAnalysis)
            }
        }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { previewView })
}
