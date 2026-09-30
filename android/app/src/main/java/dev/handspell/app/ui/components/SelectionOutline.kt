package dev.handspell.app.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Gap between a selected row's outline and the edge of the row (and so of the card the row sits in). */
private val OutlineInset = Spacing.xxs

/**
 * Clips the row (so a press ripple stays inside its rounded shape) and, when [selected], draws a 2 dp accent outline
 * inset from every edge. The outline is fully inside the row, so a row flush with its card or the screen edge never
 * has its stroke cut off, and its corners are concentric with the row's own.
 */
@Composable
fun Modifier.selectionOutline(selected: Boolean, radius: Dp = AslShapes.large): Modifier {
    val accent = LocalAslColors.current.accent
    return this.clip(RoundedCornerShape(radius)).drawWithContent {
        drawContent()
        if (selected) {
            val stroke = Spacing.stroke.toPx()
            val inset = OutlineInset.toPx() + stroke / 2
            drawRoundRect(
                accent,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                cornerRadius = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f)),
                style = Stroke(stroke),
            )
        }
    }
}
