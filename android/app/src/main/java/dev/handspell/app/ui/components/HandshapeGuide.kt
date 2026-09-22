package dev.handspell.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.handspell.app.R
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

private const val GUIDE_PADDING_FRACTION = 0.16f
private const val GUIDE_DOT_RADIUS_PX = 3f
private const val GUIDE_LINE_WIDTH_PX = 2.5f

/**
 * Compact, non-interactive reference for the selected letter. The landmarks are deliberately
 * supplied by the catalog rather than invented by the UI, so a missing reference stays absent.
 */
@Composable
fun HandshapeGuide(handshape: CanonicalHandshape?, modifier: Modifier = Modifier) {
    if (handshape == null) return
    val colors = LocalAslColors.current
    val label = stringResource(R.string.reference_handshape, handshape.letter.display)
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = MaterialTheme.shapes.medium,
        color = colors.surface.copy(alpha = 0.94f),
        border = androidx.compose.foundation.BorderStroke(Spacing.hairline, colors.separator),
        shadowElevation = Spacing.xxs,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.label)
            Canvas(Modifier.size(Spacing.referenceGuide)) {
                // The normalizer's x-axis points wrist → middle MCP and y-axis spans the palm.
                // Rotate that palm-local basis into the conventional guide view: wrist at the
                // bottom and fingers at the top, rather than wrist-to-finger pointing right.
                val points = handshape.landmarks.map { landmark ->
                    Offset(x = landmark.y, y = -landmark.x)
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
