package dev.handspell.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.handspell.app.R
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.ui.theme.AslText
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val SEGMENT_MS = 2400

/** A looping clock from 0 to [max]. With reduce motion on it stays at 0, so every visual is a still picture. */
@Composable
private fun loopClock(periodMs: Int, max: Float = 1f): State<Float> {
    if (LocalReduceMotion.current) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "onboarding loop")
    return transition.animateFloat(
        initialValue = 0f, targetValue = max,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "clock",
    )
}

private fun DrawScope.drawHand(points: List<Offset>, origin: Offset, side: Float, color: androidx.compose.ui.graphics.Color, line: Float, dot: Float) {
    fun at(i: Int) = Offset(origin.x + points[i].x * side, origin.y + points[i].y * side)
    for (connection in HandLandmarker.HAND_CONNECTIONS) {
        drawLine(color, at(connection.start()), at(connection.end()), strokeWidth = line, cap = StrokeCap.Round)
    }
    points.indices.forEach { drawCircle(color, dot, at(it)) }
}

/**
 * Step 1: the app's own recorded hand shapes, turning from one letter into the next, with a scanning ring and a pulse
 * when a shape "matches". The poses come from the letter catalogue; nothing here is drawn by hand.
 */
@Composable
internal fun HandMorphVisual(shapes: List<CanonicalHandshape>, modifier: Modifier = Modifier) {
    val colors = LocalAslColors.current
    val fitted = remember(shapes) { shapes.map(::fitToUnitSquare) }
    val n = fitted.size
    val clock = loopClock(periodMs = (n.coerceAtLeast(1)) * SEGMENT_MS, max = n.coerceAtLeast(1).toFloat())
    val letter by remember(shapes) {
        derivedStateOf {
            if (n == 0) null else {
                val c = clock.value
                val seg = c.toInt().coerceIn(0, n - 1)
                shapes[if (morphProgress(c - seg) < 0.5f) seg else (seg + 1) % n].letter
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width, size.height)
            // The ring sits a little high so the letter label has its own room beneath it.
            val center = Offset(size.width / 2f, size.height * 0.45f)
            val ring = side * 0.34f
            drawCircle(colors.accent.copy(alpha = 0.10f), ring, center)
            val c = clock.value
            // Scanning arc travelling round the ring.
            drawArc(
                colors.accent.copy(alpha = 0.85f), startAngle = c * 220f, sweepAngle = 64f, useCenter = false,
                topLeft = Offset(center.x - ring, center.y - ring), size = Size(ring * 2, ring * 2),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )
            if (n > 0) {
                val seg = c.toInt().coerceIn(0, n - 1)
                val frac = c - seg
                // The "match" pulse: a ring that swells and fades just before the pose changes.
                val pulse = ((frac - 0.30f) / 0.25f).coerceIn(0f, 1f)
                if (frac in 0.30f..HOLD_FRACTION) {
                    drawCircle(colors.accent.copy(alpha = (1f - pulse) * 0.5f), ring * (0.92f + 0.32f * pulse), center,
                        style = Stroke(3.dp.toPx()))
                }
                val pose = blend(fitted[seg], fitted[(seg + 1) % n], morphProgress(frac))
                val hand = side * 0.46f
                drawHand(pose, Offset(center.x - hand / 2f, center.y - hand / 2f), hand, colors.accent, 3.dp.toPx(), 4.dp.toPx())
            }
        }
        letter?.let { shown ->
            Crossfade(shown, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), label = "letter") { l ->
                Text(l.display, style = AslText.title1, color = colors.label)
            }
        }
    }
}

/**
 * Step 2: a phone propped up facing a "hand in frame" bracket (holding a real recorded pose), a distance line that
 * flows between them, and a sun whose rays turn slowly: arm's length, hand in frame, good light.
 */
@Composable
internal fun PhoneSetupVisual(shape: CanonicalHandshape?, modifier: Modifier = Modifier) {
    val colors = LocalAslColors.current
    val clock = loopClock(periodMs = 3600)
    val pose = remember(shape) { shape?.let(::fitToUnitSquare) }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = clock.value
        val stroke = 3.dp.toPx()
        // The phone, on the left, with its camera.
        val phoneW = w * 0.20f
        val phoneH = h * 0.56f
        val phoneLeft = w * 0.12f
        val phoneTop = (h - phoneH) / 2f
        drawRoundRect(colors.label.copy(alpha = 0.9f), Offset(phoneLeft, phoneTop), Size(phoneW, phoneH),
            CornerRadius(phoneW * 0.22f), style = Stroke(stroke))
        drawCircle(colors.label.copy(alpha = 0.9f), 3.5.dp.toPx(), Offset(phoneLeft + phoneW / 2f, phoneTop + phoneH * 0.09f))
        // The frame the hand must fit in, on the right, breathing gently.
        val breath = 1f + 0.035f * sin(2f * PI.toFloat() * c)
        val half = w * 0.17f * breath
        val fc = Offset(w * 0.72f, h * 0.52f)
        val arm = half * 0.42f
        fun corner(sx: Float, sy: Float) {
            val p = Offset(fc.x + sx * half, fc.y + sy * half)
            drawLine(colors.accent, p, Offset(p.x - sx * arm, p.y), strokeWidth = stroke * 1.4f, cap = StrokeCap.Round)
            drawLine(colors.accent, p, Offset(p.x, p.y - sy * arm), strokeWidth = stroke * 1.4f, cap = StrokeCap.Round)
        }
        corner(-1f, -1f); corner(1f, -1f); corner(-1f, 1f); corner(1f, 1f)
        pose?.let { drawHand(it, Offset(fc.x - half * 0.62f, fc.y - half * 0.62f), half * 1.24f, colors.accent, 2.5.dp.toPx(), 3.dp.toPx()) }
        // The distance between them: a dashed line that flows towards the frame, arrow heads at both ends.
        val y = h * 0.52f
        val x0 = phoneLeft + phoneW + 10.dp.toPx()
        val x1 = fc.x - half - 12.dp.toPx()
        drawLine(colors.labelSecondary, Offset(x0, y), Offset(x1, y), strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), -c * 36.dp.toPx()))
        val head = 6.dp.toPx()
        drawLine(colors.labelSecondary, Offset(x0, y), Offset(x0 + head, y - head), 2.dp.toPx(), StrokeCap.Round)
        drawLine(colors.labelSecondary, Offset(x0, y), Offset(x0 + head, y + head), 2.dp.toPx(), StrokeCap.Round)
        drawLine(colors.labelSecondary, Offset(x1, y), Offset(x1 - head, y - head), 2.dp.toPx(), StrokeCap.Round)
        drawLine(colors.labelSecondary, Offset(x1, y), Offset(x1 - head, y + head), 2.dp.toPx(), StrokeCap.Round)
        // Light: a small sun, top right, its rays turning.
        val sun = Offset(w * 0.84f, h * 0.17f)
        val r = 7.dp.toPx()
        drawCircle(colors.label.copy(alpha = 0.9f), r, sun, style = Stroke(stroke))
        for (i in 0 until 8) {
            val a = (i * 45f + c * 60f) * PI.toFloat() / 180f
            drawLine(colors.label.copy(alpha = 0.7f), Offset(sun.x + cos(a) * (r + 4.dp.toPx()), sun.y + sin(a) * (r + 4.dp.toPx())),
                Offset(sun.x + cos(a) * (r + 9.dp.toPx()), sun.y + sin(a) * (r + 9.dp.toPx())), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/**
 * Step 3: a phone whose camera signal rings swell but never leave the phone, with a lock that springs in when the
 * step comes into view: the picture stays on the device.
 */
@Composable
internal fun PrivacyVisual(active: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalAslColors.current
    val reduce = LocalReduceMotion.current
    val clock = loopClock(periodMs = 2800)
    val lock by animateFloatAsState(
        if (active) 1f else 0f,
        if (reduce) tween(0) else spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow), label = "lock",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val phoneW = w * 0.36f
            val phoneH = h * 0.80f
            val left = (w - phoneW) / 2f
            val top = (h - phoneH) / 2f
            val stroke = 3.dp.toPx()
            val lens = Offset(w / 2f, top + phoneH * 0.28f)
            // Signal rings, kept inside the phone's screen: what the camera sees stays on the phone.
            clipRect(left, top, left + phoneW, top + phoneH) {
                val c = clock.value
                for (i in 0 until 3) {
                    val t = (c + i / 3f) % 1f
                    drawCircle(colors.accent.copy(alpha = (1f - t) * 0.55f), 8.dp.toPx() + t * phoneW * 0.9f, lens,
                        style = Stroke(2.dp.toPx()))
                }
            }
            drawRoundRect(colors.label.copy(alpha = 0.9f), Offset(left, top), Size(phoneW, phoneH),
                CornerRadius(phoneW * 0.16f), style = Stroke(stroke))
            drawCircle(colors.label.copy(alpha = 0.9f), 8.dp.toPx(), lens)
            drawCircle(colors.accent, 4.dp.toPx(), lens)
        }
        Image(
            painterResource(R.drawable.ic_pro_lock), contentDescription = null,
            colorFilter = ColorFilter.tint(colors.accent),
            modifier = Modifier.align(Alignment.Center).padding(top = 84.dp).size(46.dp)
                .graphicsLayer { scaleX = lock; scaleY = lock; alpha = lock.coerceIn(0f, 1f) },
        )
    }
}
