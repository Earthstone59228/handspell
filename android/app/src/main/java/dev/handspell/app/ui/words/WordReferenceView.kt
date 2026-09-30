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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import dev.handspell.app.ui.theme.AslPalette
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

/** The frame a still thumbnail shows: about 9/20 of the way in (frames 8–10 of 20), where the sign is under way. */
fun thumbnailFrameIndex(frameCount: Int): Int = (frameCount * 9 / 20).coerceIn(0, (frameCount - 1).coerceAtLeast(0))

/** Bounding box (minX, minY, maxX, maxY) of a 42-float hand. */
fun handBounds(hand: FloatArray): FloatArray {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (i in 0 until 21) {
        val x = hand[i * 2]; val y = hand[i * 2 + 1]
        if (x < minX) minX = x; if (x > maxX) maxX = x
        if (y < minY) minY = y; if (y > maxY) maxY = y
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}

/**
 * The example of a word: a faint front-view figure (WordFigure.kt) so the hand's position reads against the head and
 * body, and the signing hand's skeleton in the accent colour on top.
 * It loops at the data's frame rate with a short hold on the first frame. With reduce motion on it shows one still
 * frame and a small Play control that plays the sign once.
 */
@Composable
fun WordReferenceView(
    reference: WordReference,
    word: String,
    modifier: Modifier = Modifier,
    handColor: Color = LocalAslColors.current.accent,
    mutedColor: Color = AslPalette.FigureGrey,
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
    val density = LocalDensity.current
    val outline = with(density) { FACE_STROKE.toPx() }
    val handStroke = with(density) { HAND_STROKE.toPx() }
    val joint = with(density) { JOINT_RADIUS.toPx() }
    Box(modifier.aspectRatio(1f).semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxSize()) {
            // Thin lines scale down with small views (the drill card) so the outline never outweighs the hand.
            val scale = (size.width / with(density) { Spacing.practiceTile.toPx() }).coerceIn(0.5f, 1f)
            drawWordFigure(reference, mutedColor, outline * scale)
            frames.getOrNull(index)?.forEach { hand ->
                if (hand != null) drawHand(hand, handColor, handStroke * scale, joint * scale) { x, y ->
                    Offset(x * size.width, y * size.height)
                }
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

/**
 * A still hand for the word card's thumbnail (the letters' wireframe slot): one representative frame, hand only,
 * scaled to fill the box like the letter wireframes.
 */
@Composable
fun WordHandThumbnail(reference: WordReference, modifier: Modifier = Modifier, color: Color = LocalAslColors.current.accent) {
    val hand = remember(reference) {
        val preferred = thumbnailFrameIndex(reference.frames.size)
        (listOf(preferred) + reference.frames.indices).firstNotNullOfOrNull { reference.frames[it].firstOrNull { h -> h != null } }
    } ?: return
    val bounds = remember(hand) { handBounds(hand) }
    val density = LocalDensity.current
    val stroke = with(density) { THUMB_STROKE.toPx() }
    Canvas(modifier.clearAndSetSemantics {}) {
        val pad = size.minDimension * 0.14f
        val rangeX = (bounds[2] - bounds[0]).coerceAtLeast(0.0001f)
        val rangeY = (bounds[3] - bounds[1]).coerceAtLeast(0.0001f)
        val k = minOf((size.width - 2 * pad) / rangeX, (size.height - 2 * pad) / rangeY)
        val ox = (size.width - rangeX * k) / 2f
        val oy = (size.height - rangeY * k) / 2f
        drawHand(hand, color, stroke, stroke * 0.9f) { x, y -> Offset((x - bounds[0]) * k + ox, (y - bounds[1]) * k + oy) }
    }
}

private fun DrawScope.drawHand(hand: FloatArray, color: Color, stroke: Float, joint: Float, map: (Float, Float) -> Offset) {
    fun p(i: Int) = map(hand[i * 2], hand[i * 2 + 1])
    for (connection in HandLandmarker.HAND_CONNECTIONS) {
        drawLine(color, p(connection.start()), p(connection.end()), stroke, StrokeCap.Round)
    }
    for (i in 0 until 21) drawCircle(color, joint, p(i))
}

private val FACE_STROKE = 1.5.dp
private val HAND_STROKE = 2.5.dp
private val JOINT_RADIUS = 2.dp
private val THUMB_STROKE = 1.9.dp
