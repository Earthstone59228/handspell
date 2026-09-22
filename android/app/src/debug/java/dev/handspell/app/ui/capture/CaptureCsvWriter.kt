package dev.handspell.app.ui.capture

import android.content.Context
import dev.handspell.app.calibration.DebugSignerId
import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Letter
import java.io.File
import java.io.FileOutputStream

/** Frame convention tag written into every row (docs/CLASSIFIER.md §1, §7). */
const val CAPTURE_FRAME_CONVENTION = "selfie-upright-v1"

/** `schema_version` written into every row; bump if the column layout below ever changes. */
const val CAPTURE_SCHEMA_VERSION = 1

/** Opaque signer labels the capture screen offers — never a real name (docs/CLASSIFIER.md §7). */
val CAPTURE_SIGNER_IDS: List<String> = DebugSignerId.quickChoices.map(DebugSignerId::value)

/**
 * Column order for the capture CSV, exactly per docs/CLASSIFIER.md §7: 15 metadata columns, 21
 * world landmarks (x,y,z), 21 image landmarks (x,y,z) = 141 columns.
 */
val CAPTURE_CSV_HEADER: List<String> = buildList {
    addAll(
        listOf(
            "schema_version", "frame_convention", "session_id", "signer_id", "letter",
            "captured_at_iso", "timestamp_ms", "handedness", "handedness_score", "image_width",
            "image_height", "rotation_degrees", "device_model", "app_version", "landmarker_model",
        ),
    )
    for (i in 0 until HandLandmarks.LANDMARK_COUNT) addAll(listOf("wx$i", "wy$i", "wz$i"))
    for (i in 0 until HandLandmarks.LANDMARK_COUNT) addAll(listOf("ix$i", "iy$i", "iz$i"))
}

/** Everything about one capture that is not already inside [HandLandmarks]. */
data class CaptureRowContext(
    val sessionId: String,
    val signerId: String,
    val letter: Letter,
    val capturedAtIso: String,
    val rotationDegrees: Int,
    val deviceModel: String,
    val appVersion: String,
    val landmarkerModel: String,
)

/**
 * Builds one CSV row (as string cells, in [CAPTURE_CSV_HEADER] order) from a single accepted
 * frame. Pure function — no I/O, no Android types beyond the shared model classes — so
 * `CaptureCsvRowTest` can assert the 141-column layout without a device or a `Context`.
 */
fun buildCaptureCsvRow(context: CaptureRowContext, landmarks: HandLandmarks): List<String> {
    require(DebugSignerId.parse(context.signerId) != null) {
        "signer id must be an opaque s-prefixed number, not a name or free text"
    }
    val metadata = listOf(
        CAPTURE_SCHEMA_VERSION.toString(),
        CAPTURE_FRAME_CONVENTION,
        context.sessionId,
        context.signerId,
        context.letter.name,
        context.capturedAtIso,
        landmarks.timestampMs.toString(),
        landmarks.handedness.name,
        landmarks.handednessScore.toString(),
        landmarks.imageWidth.toString(),
        landmarks.imageHeight.toString(),
        context.rotationDegrees.toString(),
        context.deviceModel,
        context.appVersion,
        context.landmarkerModel,
    )
    val world = landmarks.world.flatMap { listOf(it.x.toString(), it.y.toString(), it.z.toString()) }
    val image = landmarks.image.flatMap { listOf(it.x.toString(), it.y.toString(), it.z.toString()) }
    return metadata + world + image
}

private fun escapeCsvCell(cell: String): String =
    if (cell.any { it == ',' || it == '"' || it == '\n' }) {
        "\"" + cell.replace("\"", "\"\"") + "\""
    } else {
        cell
    }

/**
 * Appends capture rows to `<app external files>/captures/<signer>/<letter>_<session>.csv`
 * (docs/CLASSIFIER.md §7), writing the header once per file. The only thing this debug tool ever
 * writes to storage (docs/QUALITY.md §8).
 */
class CaptureCsvWriter(private val context: Context) {

    fun writeRow(rowContext: CaptureRowContext, landmarks: HandLandmarks) {
        val file = fileFor(rowContext.signerId, rowContext.letter, rowContext.sessionId)
        val isNewFile = !file.exists()
        FileOutputStream(file, true).bufferedWriter().use { writer ->
            if (isNewFile) {
                writer.appendLine(CAPTURE_CSV_HEADER.joinToString(","))
            }
            val row = buildCaptureCsvRow(rowContext, landmarks).joinToString(",") { escapeCsvCell(it) }
            writer.appendLine(row)
        }
    }

    private fun fileFor(signerId: String, letter: Letter, sessionId: String): File {
        val dir = File(context.getExternalFilesDir(null), "captures/$signerId")
        dir.mkdirs()
        return File(dir, "${letter.name}_$sessionId.csv")
    }
}
