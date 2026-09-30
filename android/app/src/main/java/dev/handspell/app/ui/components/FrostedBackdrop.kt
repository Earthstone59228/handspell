package dev.handspell.app.ui.components

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.ui.theme.atmosphereBrush

/** Capture the viewport, including its ground, so the header samples the real scrolling content. */
@Composable
internal fun Modifier.captureBackdrop(layer: GraphicsLayer): Modifier {
    val ground = atmosphereBrush()
    return drawWithContent {
        layer.record {
            drawRect(ground)
            this@drawWithContent.drawContent()
        }
        drawLayer(layer)
    }
}

/** Blur only the sampled background and feather its lower edge; header text remains crisp. */
@Composable
internal fun Modifier.frostedBackdrop(contentLayer: GraphicsLayer): Modifier {
    val backdrop = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { Spacing.lg.toPx() }
    val feather = with(LocalDensity.current) { Spacing.xl.toPx() }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    backdrop.renderEffect = if (supportsBlur) BlurEffect(radius, radius, TileMode.Clamp) else null
    return drawWithContent {
        if (supportsBlur) {
            backdrop.record { drawLayer(contentLayer) }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
                drawLayer(backdrop)
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White, Color.Transparent),
                        startY = (size.height - feather).coerceAtLeast(0f), endY = size.height,
                    ), blendMode = BlendMode.DstIn,
                )
                canvas.restore()
            }
        }
        drawContent()
    }
}


/**
 * Fades scrolling content into the screen's single background, but only at an edge that has more content beyond it,
 * so the last tile is never dimmed when everything already fits or the list is scrolled to its end.
 */
@Composable
internal fun Modifier.fadedScrollEdges(state: androidx.compose.foundation.ScrollState): Modifier {
    val fadePx = with(LocalDensity.current) { Spacing.scrollFade.toPx() }
    val top by animateFloatAsState(if (state.canScrollBackward) 1f else 0f, label = "fade top")
    val bottom by animateFloatAsState(if (state.canScrollForward) 1f else 0f, label = "fade bottom")
    return this.graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fraction = (fadePx / size.height).coerceIn(0f, 0.25f)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 1f - top),
                    fraction to Color.White,
                    (1f - fraction) to Color.White,
                    1f to Color.White.copy(alpha = 1f - bottom),
                ), blendMode = BlendMode.DstIn,
            )
        }
}
