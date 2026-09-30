package dev.handspell.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.Letter

/** Starting pose plus directional fingertip path, also legible with all animation disabled. */
internal fun DrawScope.drawMotionGuide(handshape: CanonicalHandshape, color: Color) {
    val points = handshape.landmarks.map { Offset(it.x, it.y) }
    val minX = points.minOf { it.x }
    val minY = points.minOf { it.y }
    val rangeX = (points.maxOf { it.x } - minX).coerceAtLeast(0.0001f)
    val rangeY = (points.maxOf { it.y } - minY).coerceAtLeast(0.0001f)
    val unit = size.minDimension / 64f
    val scale = minOf(43.52f / rangeX, 43.52f / rangeY) * 0.42f * unit
    val tip = points[if (handshape.letter == Letter.J) 20 else 8]
    val origin = Offset(if (handshape.letter == Letter.J) 43f else 13f, 8f) * unit
    fun point(index: Int) = origin + (points[index] - tip) * scale
    for (connection in HandLandmarker.HAND_CONNECTIONS) {
        drawLine(color, point(connection.start()), point(connection.end()), strokeWidth = 2.5f)
    }
    points.indices.forEach { drawCircle(color, 2f, point(it)) }
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        if (handshape.letter == Letter.J) {
            lineTo(origin.x, origin.y + 25f * unit)
            quadraticTo(origin.x, origin.y + 35f * unit, origin.x - 10f * unit, origin.y + 35f * unit)
            quadraticTo(origin.x - 17f * unit, origin.y + 35f * unit, origin.x - 17f * unit, origin.y + 27f * unit)
        } else {
            lineTo(origin.x + 34f * unit, origin.y)
            lineTo(origin.x, origin.y + 24f * unit)
            lineTo(origin.x + 34f * unit, origin.y + 24f * unit)
        }
    }
    drawPath(path, color.copy(alpha = 0.65f), style = Stroke(width = 2.5f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * unit, 3f * unit))))
    val arrow = Path().apply {
        if (handshape.letter == Letter.J) {
            moveTo(23f * unit, 38f * unit); lineTo(26f * unit, 35f * unit); lineTo(29f * unit, 38f * unit)
        } else {
            moveTo(42f * unit, 28f * unit); lineTo(47f * unit, 32f * unit); lineTo(42f * unit, 36f * unit)
        }
    }
    drawPath(arrow, color, style = Stroke(width = 2.5f))
}
