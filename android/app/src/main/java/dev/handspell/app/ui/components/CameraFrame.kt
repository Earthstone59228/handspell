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
 * (docs/CLASSIFIER.md §7) drives [dev.handspell.app.vision.landmarker.HandLandmarkerEngine]
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

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply {
                // Selfie mirror: CameraX's front-camera output is not flipped by default, so the
                // preview is mirrored here to match what the user sees in an actual mirror and to
                // match the mirror-then-rotate convention FrameConverter feeds MediaPipe
                // (docs/CLASSIFIER.md §1). The overlay draws on top with no extra flip.
                scaleX = -1f
            }
        },
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        if (!cameraProvider.hasCamera(cameraSelector)) {
                            onCameraError(CameraBindError.NoFrontCamera)
                            return@addListener
                        }

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis.setAnalyzer(analyzerExecutor, analyzer)

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis,
                        )
                    } catch (t: Throwable) {
                        onCameraError(CameraBindError.BindFailed(t))
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        },
    )
}
