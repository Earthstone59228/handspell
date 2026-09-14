package dev.handspell.app.ui.capture

import android.os.Build
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.handspell.app.BuildConfig
import dev.handspell.app.R
import dev.handspell.app.core.model.HandOverlay
import dev.handspell.app.core.model.Letter
import dev.handspell.app.ui.components.CameraFrame
import dev.handspell.app.ui.components.LandmarkOverlay
import dev.handspell.app.vision.camera.FrameConverter
import dev.handspell.app.vision.landmarker.HandLandmarkerHelper
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

private const val SAMPLE_INTERVAL_MS = 333L // ~3 rows/second while held (docs/CLASSIFIER.md §7)

private val CaptureSelectionSaver = mapSaver(
    save = { selection: Pair<String?, String?> -> mapOf("signer" to selection.first, "letter" to selection.second) },
    restore = { it["signer"] as String? to it["letter"] as String? },
)

private fun isoTimestampNow(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(System.currentTimeMillis())
}

/**
 * The dev capture screen body: signer id, letter picker, live preview with overlay, hold-to-record
 * button, per-letter frame counters. Requires a signer id before it writes anything
 * (docs/CLASSIFIER.md §7).
 *
 * Drives [HandLandmarkerHelper] directly rather than through [dev.handspell.app.vision.SignDetector]
 * — there is no classifier and no drill target here, just raw landmarks to record.
 */
@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val overlayFlow = remember { MutableStateFlow<HandOverlay?>(null) }
    val landmarksFlow = remember { MutableStateFlow<dev.handspell.app.core.model.HandLandmarks?>(null) }
    val errorFlow = remember { MutableStateFlow<RuntimeException?>(null) }
    val frameConverter = remember { FrameConverter() }
    val analysisExecutor = remember {
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "capture-analysis") }
    }
    val lastRotationDegrees = remember { AtomicInteger(0) }
    val csvWriter = remember { CaptureCsvWriter(context.applicationContext) }
    val sessionId = remember { System.currentTimeMillis().toString() }

    var helper by remember { mutableStateOf<HandLandmarkerHelper?>(null) }

    DisposableEffect(Unit) {
        helper = try {
            HandLandmarkerHelper(
                context = context.applicationContext,
                onResults = { hands ->
                    val hand = hands.firstOrNull()
                    landmarksFlow.value = hand
                    overlayFlow.value = hand?.let {
                        HandOverlay(
                            imageLandmarks = it.image,
                            imageWidth = it.imageWidth,
                            imageHeight = it.imageHeight,
                            timestampMs = it.timestampMs,
                        )
                    }
                },
                onError = { error -> errorFlow.value = error },
            )
        } catch (error: RuntimeException) {
            errorFlow.value = error
            null
        }
        onDispose {
            helper?.close()
            analysisExecutor.shutdown()
        }
    }

    val analyzer = remember {
        ImageAnalysis.Analyzer { imageProxy ->
            try {
                lastRotationDegrees.set(imageProxy.imageInfo.rotationDegrees)
                val frame = frameConverter.convert(imageProxy)
                helper?.detect(frame.image, SystemClock.uptimeMillis(), frame.width, frame.height)
            } finally {
                imageProxy.close()
            }
        }
    }

    val overlay by overlayFlow.collectAsState()
    val latestLandmarks by landmarksFlow.collectAsState()
    val engineError by errorFlow.collectAsState()

    var selection by rememberSaveable(stateSaver = CaptureSelectionSaver) {
        mutableStateOf<Pair<String?, String?>>(null to null)
    }
    val signerId = selection.first
    val letterName = selection.second
    val selectedLetter = letterName?.let(Letter::fromNameOrNull)

    val counts = remember { mutableStateMapOf<Letter, Int>() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val canRecord = signerId != null && selectedLetter != null

    LaunchedEffect(isPressed, signerId, selectedLetter) {
        val signer = signerId
        val letter = selectedLetter
        if (isPressed && signer != null && letter != null) {
            while (isActive) {
                val landmarks = latestLandmarks
                if (landmarks != null) {
                    csvWriter.writeRow(
                        CaptureRowContext(
                            sessionId = sessionId,
                            signerId = signer,
                            letter = letter,
                            capturedAtIso = isoTimestampNow(),
                            rotationDegrees = lastRotationDegrees.get(),
                            deviceModel = Build.MODEL,
                            appVersion = BuildConfig.VERSION_NAME,
                            landmarkerModel = HandLandmarkerHelper.MODEL_ASSET_PATH,
                        ),
                        landmarks,
                    )
                    counts[letter] = (counts[letter] ?: 0) + 1
                }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        SignerRow(selected = signerId, onSelect = { selection = it to letterName })
        LetterGrid(
            selected = selectedLetter,
            counts = counts,
            onSelect = { selection = signerId to it.name },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            CameraFrame(analyzer = analyzer, analyzerExecutor = analysisExecutor)
            LandmarkOverlay(overlay = overlay, modifier = Modifier.fillMaxSize())
        }
        CaptureStatusRow(
            signerId = signerId,
            selectedLetter = selectedLetter,
            hasHand = latestLandmarks != null,
            isRecording = isPressed && canRecord,
            frameCount = selectedLetter?.let { counts[it] } ?: 0,
            engineError = engineError,
        )
        RecordButton(
            enabled = canRecord,
            interactionSource = interactionSource,
        )
    }
}

@Composable
private fun SignerRow(selected: String?, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (signer in CAPTURE_SIGNER_IDS) {
            FilterChip(
                selected = signer == selected,
                onClick = { onSelect(signer) },
                label = { Text(signer) },
                modifier = Modifier.height(48.dp),
            )
        }
    }
}

@Composable
private fun LetterGrid(selected: Letter?, counts: Map<Letter, Int>, onSelect: (Letter) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(Letter.staticLetters) { letter ->
            FilterChip(
                selected = letter == selected,
                onClick = { onSelect(letter) },
                label = { Text("${letter.display} ${counts[letter] ?: 0}") },
                modifier = Modifier.padding(2.dp).height(48.dp),
            )
        }
    }
}

@Composable
private fun CaptureStatusRow(
    signerId: String?,
    selectedLetter: Letter?,
    hasHand: Boolean,
    isRecording: Boolean,
    frameCount: Int,
    engineError: RuntimeException?,
) {
    val message = when {
        engineError != null -> stringResource(R.string.capture_engine_error)
        signerId == null -> stringResource(R.string.capture_select_signer)
        selectedLetter == null -> stringResource(R.string.capture_select_letter)
        isRecording -> stringResource(R.string.capture_recording, frameCount)
        !hasHand -> stringResource(R.string.capture_no_hand)
        else -> stringResource(R.string.capture_ready, frameCount)
    }
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun RecordButton(enabled: Boolean, interactionSource: MutableInteractionSource) {
    val isPressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = {},
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (isPressed) R.string.capture_hold_recording else R.string.capture_hold_to_record,
            ),
        )
    }
}
