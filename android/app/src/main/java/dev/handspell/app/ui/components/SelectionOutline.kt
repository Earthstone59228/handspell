package dev.handspell.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Selection stays inside the same rounded hit area on every native menu. */
@Composable
fun Modifier.selectionOutline(selected: Boolean, radius: Dp = AslShapes.large): Modifier {
    val shape = RoundedCornerShape(radius)
    val colors = LocalAslColors.current
    return this.clip(shape).then(if (selected) Modifier.border(Spacing.stroke, colors.accent, shape) else Modifier)
}
