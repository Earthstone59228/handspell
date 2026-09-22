package dev.handspell.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dev.handspell.app.R
import dev.handspell.app.ui.theme.HandspellTheme

/**
 * Dev-only capture screen (docs/CLASSIFIER.md §7): records signer-labelled landmark frames to CSV
 * for building the k-NN reference set and training the stage-2 MLP. Lives entirely in `src/debug`
 * so `assembleRelease` never contains it (docs/QUALITY.md §8). Launched from adb, not from any UI:
 *
 * Opened only from the debug build's internal Settings launcher. It stays non-exported, so an
 * external adb shell or another app cannot start a screen that writes raw calibration landmarks.
 */
class CaptureActivity : ComponentActivity() {

    private enum class PermissionState { NOT_REQUESTED, GRANTED, DENIED_CAN_RETRY, DENIED_PERMANENTLY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HandspellTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CapturePermissionGate()
                }
            }
        }
    }

    @Composable
    private fun CapturePermissionGate() {
        var state by remember {
            mutableStateOf(
                if (hasCameraPermission()) PermissionState.GRANTED else PermissionState.NOT_REQUESTED,
            )
        }
        val launcher = rememberLauncherForCameraPermission { granted ->
            state = when {
                granted -> PermissionState.GRANTED
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ->
                    PermissionState.DENIED_CAN_RETRY
                else -> PermissionState.DENIED_PERMANENTLY
            }
        }

        when (state) {
            PermissionState.GRANTED -> CaptureScreen()
            PermissionState.NOT_REQUESTED, PermissionState.DENIED_CAN_RETRY -> {
                CameraPermissionRequest(
                    showRationale = state == PermissionState.DENIED_CAN_RETRY,
                    onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                )
            }
            PermissionState.DENIED_PERMANENTLY -> CameraPermissionPermanentlyDenied()
        }
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    @Composable
    private fun rememberLauncherForCameraPermission(onResult: (Boolean) -> Unit) =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
}

@Composable
private fun CameraPermissionRequest(showRationale: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (showRationale) R.string.capture_permission_rationale else R.string.capture_permission_explainer,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.capture_grant_camera))
        }
    }
}

@Composable
private fun CameraPermissionPermanentlyDenied() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.capture_permission_denied_permanently),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
