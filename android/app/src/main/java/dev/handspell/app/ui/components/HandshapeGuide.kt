package dev.handspell.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.handspell.app.R
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.ui.theme.AslPalette
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import kotlinx.coroutines.flow.StateFlow

private const val GUIDE_PADDING_FRACTION = 0.16f
private const val GUIDE_DOT_RADIUS_PX = 3f
private const val GUIDE_LINE_WIDTH_PX = 2.5f

/**
 * Corner geometry of the camera frame and the reference card inside it. Concentric rounded corners need
 * inner radius = outer radius - inset, so the card's radius is derived here and nowhere else.
 */
object FrameGeometry {
    val outerRadius = AslShapes.extraLarge
    val guideInset = Spacing.sm
    val guideRadius = AslShapes.inner(outerRadius, guideInset)
}

/**
 * Compact, non-interactive reference for the selected letter, on a live frosted patch of the camera picture.
 * [thumbnails] is the detector's per-frame thumbnail; [frameSize] is the camera frame the card sits in, so the
 * patch behind the card can be cut from the right place. The landmarks are deliberately supplied by the
 * catalog rather than invented by the UI, so a missing reference stays absent.
 */
@Composable
fun HandshapeGuide(
    handshape: CanonicalHandshape?,
    modifier: Modifier = Modifier,
    thumbnails: StateFlow<Bitmap?>? = null,
    frameSize: IntSize = IntSize.Zero,
) {
    if (handshape == null) return
    val colors = LocalAslColors.current
    val label = stringResource(R.string.reference_handshape, handshape.letter.display)
    var guideSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier.clip(RoundedCornerShape(FrameGeometry.guideRadius)).onSizeChanged { guideSize = it }
            .semantics { contentDescription = label },
    ) {
        LiveBackdrop(thumbnails, frameSize, guideSize, Modifier.matchParentSize())
        Column(
            modifier = Modifier.padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            // Always light text: the card sits on a darkened patch of the camera picture in both appearances.
            Text(label, style = MaterialTheme.typography.labelMedium, color = AslPalette.Paper)
            Canvas(Modifier.size(Spacing.referenceGuide)) {
                if (handshape.letter.requiresMotion) {
                    drawMotionGuide(handshape, colors.accent)
                    return@Canvas
                }
                // Catalog restores the recorded display orientation, including down-pointing P/Q.
                val points = handshape.landmarks.map { landmark ->
                    Offset(x = landmark.x, y = landmark.y)
                }
                val minX = points.minOf { it.x }
                val maxX = points.maxOf { it.x }
                val minY = points.minOf { it.y }
                val maxY = points.maxOf { it.y }
                val rangeX = (maxX - minX).coerceAtLeast(0.0001f)
                val rangeY = (maxY - minY).coerceAtLeast(0.0001f)
                val usableWidth = size.width * (1f - 2f * GUIDE_PADDING_FRACTION)
                val usableHeight = size.height * (1f - 2f * GUIDE_PADDING_FRACTION)
                val scale = minOf(usableWidth / rangeX, usableHeight / rangeY)
                val offsetX = (size.width - rangeX * scale) / 2f
                val offsetY = (size.height - rangeY * scale) / 2f

                fun point(index: Int): Offset {
                    val landmark = points[index]
                    return Offset(
                        x = (landmark.x - minX) * scale + offsetX,
                        y = (landmark.y - minY) * scale + offsetY,
                    )
                }

                for (connection in HandLandmarker.HAND_CONNECTIONS) {
                    drawLine(
                        color = colors.accent,
                        start = point(connection.start()),
                        end = point(connection.end()),
                        strokeWidth = GUIDE_LINE_WIDTH_PX,
                        cap = Stroke.DefaultCap,
                    )
                }
                points.indices.forEach { drawCircle(colors.accent, GUIDE_DOT_RADIUS_PX, point(it)) }
            }
        }
    }
}

/**
 * Frosted glass over the live camera picture. Each analysed frame arrives as a small thumbnail; the part of it
 * that lies behind the card is drawn stretched to the card and blurred, then given a dark tint so the white text
 * and blue skeleton stay legible. Reading the frame inside the draw block means a new frame redraws only this
 * layer. Before the first frame the card is plain grey.
 */
@Composable
internal fun LiveBackdrop(thumbnails: StateFlow<Bitmap?>?, frameSize: IntSize, guideSize: IntSize, modifier: Modifier) {
    val thumbnail = thumbnails?.collectAsState()
    val insetPx = with(androidx.compose.ui.platform.LocalDensity.current) { FrameGeometry.guideInset.toPx() }
    Box(modifier.background(LocalAslColors.current.surface)) {
        Canvas(Modifier.matchParentSize().blur(BACKDROP_BLUR)) {
            val frame = thumbnail?.value ?: return@Canvas
            if (frameSize == IntSize.Zero || guideSize == IntSize.Zero) return@Canvas
            // The preview fills the frame with a centre crop; undo that to find the card's rectangle in the thumbnail.
            val scale = maxOf(frameSize.width / frame.width.toFloat(), frameSize.height / frame.height.toFloat())
            val originX = (frameSize.width - frame.width * scale) / 2f
            val originY = (frameSize.height - frame.height * scale) / 2f
            val srcX = ((insetPx - originX) / scale).toInt().coerceIn(0, frame.width - 1)
            val srcY = ((insetPx - originY) / scale).toInt().coerceIn(0, frame.height - 1)
            val srcW = (guideSize.width / scale).toInt().coerceIn(1, frame.width - srcX)
            val srcH = (guideSize.height / scale).toInt().coerceIn(1, frame.height - srcY)
            drawImage(
                frame.asImageBitmap(),
                srcOffset = IntOffset(srcX, srcY), srcSize = IntSize(srcW, srcH),
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.High,
            )
        }
        Box(Modifier.matchParentSize().background(AslPalette.Ink.copy(alpha = BACKDROP_SCRIM)))
    }
}

private val BACKDROP_BLUR = 8.dp
private const val BACKDROP_SCRIM = 0.5f
