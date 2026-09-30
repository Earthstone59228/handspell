package dev.handspell.app.ui.words

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.handspell.app.R
import dev.handspell.app.content.WordReference
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import dev.handspell.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/** How long the first frame is held before each loop, so the start position reads before the hand moves. */
const val WORD_REFERENCE_HOLD_MS = 600L

/** The frame shown still when motion is off: the middle of the sign, where the movement is under way. */
fun stillFrameIndex(frameCount: Int): Int = (frameCount / 2).coerceIn(0, (frameCount - 1).coerceAtLeast(0))

/**
 * The frame to draw [elapsedMs] into a loop that holds frame 0 for [holdMs] and then steps through every frame at
 * [frameMs]. Pure, so the timing can be tested.
 */
fun loopFrameIndex(elapsedMs: Long, frameCount: Int, frameMs: Long, holdMs: Long = WORD_REFERENCE_HOLD_MS): Int {
    if (frameCount <= 1) return 0
    val cycle = holdMs + frameCount * frameMs
    val t = Math.floorMod(elapsedMs, cycle)
    return if (t < holdMs) 0 else ((t - holdMs) / frameMs).toInt().coerceIn(0, frameCount - 1)
}

/**
 * The animated example of a word: a head oval and shoulder line in a muted tone, the signing hand's skeleton in the
 * accent colour, looped at the data's frame rate with a short hold on the first frame. With reduce motion on it shows
 * one still frame and a small Play control that plays the sign once.
 */
@Composable
fun WordReferenceView(
    reference: WordReference,
    word: String,
    modifier: Modifier = Modifier,
    handColor: Color = LocalAslColors.current.accent,
    mutedColor: Color = LocalAslColors.current.onSurfaceSecondary,
) {
    val reduceMotion = LocalReduceMotion.current
    val frames = reference.frames
    var index by remember(reference) { mutableIntStateOf(if (reduceMotion) stillFrameIndex(frames.size) else 0) }
    var playOnce by remember(reference) { mutableIntStateOf(0) }
    var playing by remember(reference) { mutableStateOf(false) }
    if (!reduceMotion) LaunchedEffect(reference) {
        val start = System.nanoTime()
        while (true) {
            index = loopFrameIndex((System.nanoTime() - start) / 1_000_000, frames.size, reference.frameMillis)
            delay(reference.frameMillis / 2)
        }
    } else LaunchedEffect(reference, playOnce) {
        if (playOnce == 0) return@LaunchedEffect
        playing = true
        for (i in frames.indices) { index = i; delay(reference.frameMillis) }
        index = stillFrameIndex(frames.size)
        playing = false
    }
    val description = stringResource(R.string.word_example_description, word)
    Box(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.018f
            val body = reference.body
            val rx = reference.head[0] * w
            val ry = reference.head[1] * h
            drawOval(mutedColor, topLeft = Offset(body[0] * w - rx, body[1] * h - ry), size = Size(rx * 2, ry * 2),
                style = Stroke(stroke))
            drawLine(mutedColor, Offset(body[2] * w, body[3] * h), Offset(body[4] * w, body[5] * h), stroke, StrokeCap.Round)
            frames.getOrNull(index)?.forEach { hand ->
                if (hand == null) return@forEach
                fun p(i: Int) = Offset(hand[i * 2] * w, hand[i * 2 + 1] * h)
                for (connection in HandLandmarker.HAND_CONNECTIONS) {
                    drawLine(handColor, p(connection.start()), p(connection.end()), stroke, StrokeCap.Round)
                }
                for (i in 0 until 21) drawCircle(handColor, stroke * 1.2f, p(i))
            }
        }
        if (reduceMotion && !playing) Box(
            Modifier.align(Alignment.BottomEnd).sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
                .clickable(role = Role.Button) { playOnce++ },
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(if (playOnce == 0) R.string.word_example_play else R.string.word_example_replay),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = handColor,
                modifier = Modifier.padding(Spacing.xs))
        }
    }
}
