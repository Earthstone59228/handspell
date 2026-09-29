package dev.handspell.app.vision.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import java.nio.ByteBuffer

/** A converted frame plus the pixel size MediaPipe saw, used to map normalised landmarks to pixels. */
data class ConvertedFrame(
    val image: MPImage,
    val width: Int,
    val height: Int,
    /** A tiny copy of the same upright, mirrored frame, for the live frosted-glass backdrop in the UI. */
    val thumbnail: Bitmap,
)

/**
 * Copies an [ImageProxy] into a MediaPipe image using the selfie-mirrored, display-upright
 * convention from docs/CLASSIFIER.md §1.
 *
 * The analysis use case must be configured with `OUTPUT_IMAGE_FORMAT_RGBA_8888`, so plane 0 is
 * RGBA. Some devices pad each row (rowStride > width * 4); those rows are packed before the copy,
 * or the image shears and MediaPipe sees garbage. The mirror is applied after rotation in display
 * space. Mirroring in sensor space turns a portrait selfie upside down relative to PreviewView,
 * which reverses the K/P and G/Q orientation features.
 *
 * Not thread-safe: CameraX calls the analyzer on one executor. The sensor-orientation bitmap is
 * reused across frames because `createBitmap(source, …, matrix, …)` copies it; the upright bitmap
 * is not, since MediaPipe's LIVE_STREAM mode reads it asynchronously.
 */
class FrameConverter {

    private var source: Bitmap? = null
    private var packedRows: ByteBuffer? = null

    fun convert(imageProxy: ImageProxy): ConvertedFrame {
        val width = imageProxy.width
        val height = imageProxy.height

        val bitmap = source?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { source = it }
        val plane = imageProxy.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val rowBytes = width * BYTES_PER_PIXEL
        if (plane.rowStride == rowBytes) {
            bitmap.copyPixelsFromBuffer(buffer)
        } else {
            bitmap.copyPixelsFromBuffer(packRows(buffer, plane.rowStride, rowBytes, height))
        }

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            postScale(-1f, 1f)
        }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

        return ConvertedFrame(
            image = BitmapImageBuilder(upright).build(),
            width = upright.width,
            height = upright.height,
            thumbnail = displayOrientedThumbnail(upright),
        )
    }

    /**
     * Analysis and PreviewView now use the same upright selfie frame; no extra rotation is needed.
     */
    private fun displayOrientedThumbnail(upright: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(
            upright, THUMBNAIL_WIDTH, (THUMBNAIL_WIDTH * upright.height / upright.width).coerceAtLeast(1), true,
        )
    }

    private fun packRows(padded: ByteBuffer, rowStride: Int, rowBytes: Int, height: Int): ByteBuffer {
        val packed = packedRows?.takeIf { it.capacity() == rowBytes * height }
            ?: ByteBuffer.allocateDirect(rowBytes * height).also { packedRows = it }
        return packRgbaRows(padded, rowStride, rowBytes, height, packed)
    }

    private companion object {
        const val THUMBNAIL_WIDTH = 96
        const val BYTES_PER_PIXEL = 4
    }
}

/**
 * Copies [height] rows of [rowBytes] out of [padded], whose rows start every [rowStride] bytes,
 * into [into] (capacity at least rowBytes * height), and returns [into] ready to read. Only
 * bytes inside [padded]'s limit are read; a plane too short for its declared stride is rejected,
 * as `copyPixelsFromBuffer` would reject an unpadded buffer that is too small.
 */
internal fun packRgbaRows(padded: ByteBuffer, rowStride: Int, rowBytes: Int, height: Int, into: ByteBuffer): ByteBuffer {
    require(rowStride >= rowBytes) { "rowStride $rowStride < rowBytes $rowBytes" }
    require(height == 0 || (height - 1).toLong() * rowStride + rowBytes <= padded.limit()) {
        "plane limit ${padded.limit()} too small for $height rows of stride $rowStride"
    }
    into.clear()
    val row = padded.duplicate()
    for (y in 0 until height) {
        val start = y * rowStride
        row.limit(start + rowBytes)
        row.position(start)
        into.put(row)
    }
    into.flip()
    return into
}
