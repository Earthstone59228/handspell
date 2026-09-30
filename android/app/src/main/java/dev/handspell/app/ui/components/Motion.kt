package dev.handspell.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.handspell.app.ui.theme.LocalReduceMotion

/** Shrinks slightly while pressed and springs back, so a tap is felt. Share the source with the element's clickable. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.965f): Modifier {
    if (LocalReduceMotion.current) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.55f, stiffness = 520f), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Fades in and rises a little when it first appears, a beat after [delayMillis]; instant with reduce motion on. */
@Composable
fun Modifier.appear(delayMillis: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(480, delayMillis, FastOutSlowInEasing), label = "appear")
    val lift = with(LocalDensity.current) { 22.dp.toPx() }
    return this.graphicsLayer { alpha = progress; translationY = (1f - progress) * lift }
}
