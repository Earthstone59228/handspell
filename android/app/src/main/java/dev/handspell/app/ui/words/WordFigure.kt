package dev.handspell.app.ui.words

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import dev.handspell.app.content.WordReference

/**
 * The person the hand is signed against: a friendly front-view line drawing (head with ears, brows, eyes, nose and
 * smile, a neck, a neckline, and shoulders that run into the arms). Drawn from the reference's body anchors, in
 * shoulder-width units with the origin at the nose, so it stays in proportion at every size. The source paths are
 * SVG (see docs/design/word-figure.svg for the same drawing).
 */
private object FigurePaths {
    private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

    val head = path("M0,-0.36C0.15,-0.36 0.23,-0.24 0.23,-0.06C0.23,0.12 0.15,0.26 0,0.26C-0.15,0.26 -0.23,0.12 -0.23,-0.06C-0.23,-0.24 -0.15,-0.36 0,-0.36Z")
    val strokes = listOf(
        // ears
        "M-0.23,-0.07C-0.29,-0.08 -0.29,0.04 -0.23,0.04", "M0.23,-0.07C0.29,-0.08 0.29,0.04 0.23,0.04",
        // brows
        "M-0.155,-0.15Q-0.10,-0.185 -0.05,-0.155", "M0.155,-0.15Q0.10,-0.185 0.05,-0.155",
        // nose and smile
        "M0.006,-0.075C0.012,-0.03 0.024,0.005 0.02,0.038C0.012,0.062 -0.018,0.066 -0.032,0.046",
        "M-0.08,0.135C-0.035,0.175 0.035,0.175 0.08,0.135",
        // neck
        "M-0.10,0.21C-0.10,0.30 -0.11,0.36 -0.135,0.405", "M0.10,0.21C0.10,0.30 0.11,0.36 0.135,0.405",
    ).map(::path)
    val eyes = listOf(Offset(-0.10f, -0.09f), Offset(0.10f, -0.09f))

    // Torso parts sit slightly lower or higher with the signer's real shoulder height (see drawWordFigure).
    val bodyFill = path("M-0.135,0.405C-0.30,0.43 -0.44,0.44 -0.52,0.53C-0.585,0.60 -0.60,0.75 -0.60,3.0L0.60,3.0C0.60,0.75 0.585,0.60 0.52,0.53C0.44,0.44 0.30,0.43 0.135,0.405C0.07,0.52 -0.07,0.52 -0.135,0.405Z")
    val torsoStrokes = listOf(
        "M-0.135,0.405C-0.30,0.43 -0.44,0.44 -0.52,0.53C-0.585,0.60 -0.60,0.75 -0.60,3.0",
        "M0.135,0.405C0.30,0.43 0.44,0.44 0.52,0.53C0.585,0.60 0.60,0.75 0.60,3.0",
        "M-0.135,0.405C-0.07,0.52 0.07,0.52 0.135,0.405",
    ).map(::path)
}

private const val TYPICAL_SHOULDER_DROP = 0.42f
private const val MAX_TORSO_SHIFT = 0.12f

/** Draws the figure for [reference] filling the current scope (a square, x and y in the unit square of the data). */
internal fun DrawScope.drawWordFigure(reference: WordReference, lineColor: Color, strokePx: Float) {
    val body = reference.body
    val nose = Offset(body[0] * size.width, body[1] * size.height)
    val shoulderWidth = (body[4] - body[2]) * size.width
    if (shoulderWidth <= 0f) return
    val drop = (((body[3] + body[5]) / 2f - body[1]) * size.height / shoulderWidth - TYPICAL_SHOULDER_DROP)
        .coerceIn(-MAX_TORSO_SHIFT, MAX_TORSO_SHIFT)
    val line = Stroke(strokePx / shoulderWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val fill = lineColor.copy(alpha = lineColor.alpha * 0.3f)
    withTransform({
        translate(nose.x, nose.y)
        scale(shoulderWidth, shoulderWidth, Offset.Zero)
    }) {
        withTransform({ translate(0f, drop) }) {
            drawPath(FigurePaths.bodyFill, fill, style = Fill)
            FigurePaths.torsoStrokes.forEach { drawPath(it, lineColor, style = line) }
        }
        drawPath(FigurePaths.head, fill, style = Fill)
        drawPath(FigurePaths.head, lineColor, style = line)
        FigurePaths.strokes.forEach { drawPath(it, lineColor, style = line) }
        FigurePaths.eyes.forEach { drawCircle(lineColor, 0.02f, it) }
    }
}
