package dev.handspell.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.handspell.app.ui.theme.AslMotion
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import dev.handspell.app.ui.theme.Spacing

private val SHEET_MAX_WIDTH = 560.dp

/**
 * The alphabet menu's practice sheet, natively: a dimmed backdrop and a card rising from the bottom edge with a
 * hairline border and 28dp top corners. Tapping the backdrop or pressing back dismisses it. With reduce motion on it
 * appears and disappears without sliding.
 */
@Composable
fun AslSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAslColors.current
    val reduceMotion = LocalReduceMotion.current
    val fadeMs = if (reduceMotion) 0 else AslMotion.quickMillis
    val slideMs = if (reduceMotion) 0 else AslMotion.standardMillis
    if (visible) BackHandler(onBack = onDismiss)
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(fadeMs)), exit = fadeOut(tween(fadeMs))) {
            Box(
                Modifier.fillMaxSize().background(colors.backgroundGrouped.copy(alpha = 0.6f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(slideMs)) { it } + fadeIn(tween(fadeMs)),
            exit = slideOutVertically(tween(slideMs)) { it } + fadeOut(tween(fadeMs)),
        ) {
            val shape = RoundedCornerShape(topStart = AslShapes.extraLarge, topEnd = AslShapes.extraLarge)
            Column(
                Modifier.widthIn(max = SHEET_MAX_WIDTH).fillMaxWidth().clip(shape)
                    .border(Spacing.hairline, colors.separator, shape)
                    .background(colors.backgroundGrouped)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { dialog() }
                    .navigationBarsPadding()
                    .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.xl + Spacing.xxs, bottom = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                content = content,
            )
        }
    }
}
