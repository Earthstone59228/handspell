package dev.handspell.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** A small drawn stopwatch for the speed challenge (no icon asset): blue while a round is available, grey when not. */
@Composable
fun TimerMark(active: Boolean, modifier: Modifier = Modifier, size: Dp = Spacing.xxl) {
    val colors = LocalAslColors.current
    val color = if (active) colors.accent else colors.labelSecondary
    Canvas(modifier.size(size)) {
        val stroke = this.size.width * 0.08f
        val center = Offset(this.size.width / 2, this.size.height * 0.56f)
        val radius = this.size.width * 0.36f
        drawCircle(color, radius, center, style = Stroke(stroke))
        drawLine(color, Offset(center.x, center.y - radius - stroke), Offset(center.x, this.size.height * 0.08f), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x - radius * 0.35f, this.size.height * 0.08f),
            Offset(center.x + radius * 0.35f, this.size.height * 0.08f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(center.x + radius * 0.45f, center.y - radius * 0.45f), stroke, StrokeCap.Round)
    }
}
