package dev.handspell.app.vision.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage

/** A converted frame plus the pixel size MediaPipe saw, used to map normalised landmarks to pixels. */
data class ConvertedFrame(
    val image: MPImage,
    val width: Int,
    val height: Int,
)

/**
 * Copies an [ImageProxy] into a MediaPipe image using the selfie-mirrored, display-upright
 * convention from docs/CLASSIFIER.md §1.
 *
 * The analysis use case must be configured with `OUTPUT_IMAGE_FORMAT_RGBA_8888`, so plane 0 is a
 * contiguous RGBA buffer with no row padding; the bitmap is built from it directly. The mirror is
 * applied before the rotation, exactly as the spec requires, because MediaPipe's handedness head
 * assumes a mirrored selfie image.
 */
class FrameConverter {

    fun convert(imageProxy: ImageProxy): ConvertedFrame {
        val width = imageProxy.width
        val height = imageProxy.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val buffer = imageProxy.planes[0].buffer
        buffer.rewind()
        bitmap.copyPixelsFromBuffer(buffer)

        val matrix = Matrix().apply {
            postScale(-1f, 1f, width / 2f, height / 2f)
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)

        return ConvertedFrame(
            image = BitmapImageBuilder(upright).build(),
            width = upright.width,
            height = upright.height,
        )
    }
}
