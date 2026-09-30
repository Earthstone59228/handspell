package dev.handspell.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.handspell.app.R
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import dev.handspell.app.ui.theme.Spacing

/**
 * The streak flame: blue once today counts, a quiet grey while today is still open. Decorative; text carries it.
 * With [flicker] on, a lit flame sways and breathes from its base (still with reduce motion on).
 */
@Composable
fun StreakMark(active: Boolean, modifier: Modifier = Modifier, size: Dp = Spacing.xxl, flicker: Boolean = false) {
    val colors = LocalAslColors.current
    val animate = flicker && active && !LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "flame")
    val sway = if (animate) transition.animateFloat(-3f, 3f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway") else null
    val breathe = if (animate) transition.animateFloat(0.94f, 1.08f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe") else null
    Icon(
        painterResource(R.drawable.ic_streak_flame), contentDescription = null,
        tint = if (active) colors.accent else colors.labelSecondary,
        modifier = modifier.size(size).graphicsLayer {
            transformOrigin = TransformOrigin(0.5f, 1f)
            rotationZ = sway?.value ?: 0f
            scaleY = breathe?.value ?: 1f
            scaleX = 1f + ((breathe?.value ?: 1f) - 1f) * 0.4f
        },
    )
}
