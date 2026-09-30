package dev.handspell.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.handspell.app.R
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import kotlin.math.tanh

internal val BackChevronStart = 12.dp

/** Above this font scale, paired buttons stack full width and labels may wrap (DESIGN.md §7, 200% font scale). */
const val LARGE_FONT_SCALE = 1.3f

/** Two actions side by side, single-line labels; stacked full width at large font scales so nothing is cut off. */
@Composable
fun AslButtonPair(first: @Composable (Modifier) -> Unit, second: (@Composable (Modifier) -> Unit)?) {
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE || second == null) Column(
        Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        first(Modifier.fillMaxWidth())
        second?.invoke(Modifier.fillMaxWidth())
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}
// 4dp below the status bar (was 16): the owner found the frosted top bars sat too low. The 48dp touch box is unchanged,
// and the alphabet page moved by the same 12px (web/src/css/style.css .top-area / .screen-back).
internal val BackChevronTop = 4.dp

/**
 * Header shared by every secondary screen: the alphabet menu's single back chevron at top left, then a large,
 * tightly tracked title. [compact] puts both on one row for screens that need the height (the camera).
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = LocalAslColors.current
    Column(modifier.fillMaxWidth().background(colors.backgroundGrouped)) {
        Row(
            // Same spot as the alphabet menu's back button (top 6, left 14, 44 box): centre 36 from the left, 28 down.
            Modifier.fillMaxWidth().padding(start = BackChevronStart, end = Spacing.md, top = BackChevronTop),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) BackChevron(onBack)
            if (compact) Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.label,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) Spacing.md else 0.dp)
                    .semantics { heading() },
            ) else Box(Modifier.weight(1f))
            actions()
        }
        if (!compact) Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.label,
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xxs, bottom = Spacing.md)
                .semantics { heading() },
        )
    }
}

@Composable
fun BackChevron(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val color = LocalAslColors.current.label
    val description = stringResource(R.string.back)
    val density = LocalDensity.current
    val limit = with(density) { 8.dp.toPx() }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var target by remember { mutableStateOf(Offset.Zero) }
    val position by animateOffsetAsState(target, spring(dampingRatio = 0.8f, stiffness = 350f), label = "back drag")
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier.size(Spacing.touchTarget)
            .pointerInput(limit) {
                detectDragGestures(
                    onDragStart = { drag = Offset.Zero },
                    onDragEnd = { drag = Offset.Zero; target = Offset.Zero },
                    onDragCancel = { drag = Offset.Zero; target = Offset.Zero },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                    target = Offset(limit * tanh(drag.x / (limit * 4)), limit * tanh(drag.y / (limit * 4)))
                }
            }
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onBack)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp).graphicsLayer { translationX = position.x; translationY = position.y }) {
            // The alphabet's #icon-back symbol (32-unit viewBox, "M19.5 6.5 10 16l9.5 9.5", stroke 2.3) at 30dp.
            val unit = size.width / 32f
            val path = Path().apply {
                moveTo(19.5f * unit, 6.5f * unit)
                lineTo(10f * unit, 16f * unit)
                lineTo(19.5f * unit, 25.5f * unit)
            }
            drawPath(path, color, style = Stroke(2.3f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Primary: blue. Secondary: grey surface. Card: the alphabet sheet's off-white "Mark complete" button. */
enum class AslButtonStyle { Primary, Secondary, Card }

/** Blue fill with white text, or a lighter-grey fill with white text for the secondary action. */
@Composable
fun AslButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AslButtonStyle = AslButtonStyle.Primary,
    enabled: Boolean = true,
    /** What TalkBack says when the visible label is shortened (e.g. "Undo" → "Undo completion"). */
    contentDescription: String? = null,
    /** Keep the label on one line (side-by-side buttons); wrapping is still allowed at large font scales. */
    singleLine: Boolean = false,
) {
    val colors = LocalAslColors.current
    val container = when (style) {
        AslButtonStyle.Primary -> colors.accent
        AslButtonStyle.Secondary -> colors.surface
        AslButtonStyle.Card -> colors.card
    }
    val content = when (style) {
        AslButtonStyle.Primary -> colors.onAccent
        AslButtonStyle.Secondary -> colors.onSurface
        AslButtonStyle.Card -> colors.onCard
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.sizeIn(minHeight = Spacing.primaryButton)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        shape = RoundedCornerShape(AslShapes.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f), disabledContentColor = content.copy(alpha = 0.7f),
        ),
    ) {
        val wrapAllowed = LocalDensity.current.fontScale > LARGE_FONT_SCALE
        Text(text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge,
            maxLines = if (singleLine && !wrapAllowed) 1 else Int.MAX_VALUE, softWrap = !singleLine || wrapAllowed,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier)
    }
}

/** Rounded lighter-grey card with white text. */
@Composable
fun AslCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = LocalAslColors.current
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AslShapes.large),
        color = colors.surface,
        contentColor = colors.onSurface,
        content = content,
    )
}

@Composable
fun AslSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalAslColors.current.labelSecondary,
        modifier = modifier.padding(start = Spacing.xs).semantics { heading() },
    )
}

/** Bottom tab bar: text only, selected tab in bold off-white with a blue bar above it. */
@Composable
fun AslTabBar(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAslColors.current
    Row(
        modifier.fillMaxWidth().background(colors.backgroundGrouped).navigationBarsPadding()
            .semantics { selectableGroup() },
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Column(
                Modifier.weight(1f).sizeIn(minHeight = Spacing.huge)
                    .selectable(selected = selected, role = Role.Tab) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(width = Spacing.xxl, height = 3.dp)
                        .background(if (selected) colors.accent else Color.Transparent, RoundedCornerShape(2.dp)),
                )
                Text(
                    label, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) colors.label else colors.labelSecondary,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
    }
}

/** The neutral grey lock and "Pro" label on locked content: never the accent colour, never an advert. */
@Composable
fun ProLockLabel(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        ProLockIcon(color)
        Text(stringResource(R.string.home_pro_label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** The Pro lock: a rounded body with a keyhole and a round-capped shackle (res/drawable/ic_pro_lock.xml, one tint). */
@Composable
fun ProLockIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_pro_lock),
        contentDescription = null,
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
        modifier = modifier.size(Spacing.lg),
    )
}
