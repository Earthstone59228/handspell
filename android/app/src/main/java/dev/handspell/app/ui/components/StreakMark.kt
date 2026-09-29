package dev.handspell.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.handspell.app.R
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** The streak flame: blue once today counts, a quiet grey while today is still open. Decorative; text carries it. */
@Composable
fun StreakMark(active: Boolean, modifier: Modifier = Modifier, size: Dp = Spacing.xxl) {
    val colors = LocalAslColors.current
    Icon(
        painterResource(R.drawable.ic_streak_flame), contentDescription = null,
        tint = if (active) colors.accent else colors.labelSecondary,
        modifier = modifier.size(size),
    )
}
