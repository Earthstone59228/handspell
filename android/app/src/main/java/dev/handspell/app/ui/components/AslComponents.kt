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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Dp
import dev.handspell.app.ui.theme.LocalInsetCornerRadius
import androidx.compose.runtime.CompositionLocalProvider
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

/** Space below every menu header's last line, so all screens start their content at the same distance. */
internal val HeaderBottomSpace = Spacing.xl

/**
 * The one title row of every menu screen: the back chevron, then a single-line large title, then optional icons.
 * The chevron sits at the same spot everywhere, and a long title shrinks to fit instead of wrapping or clipping.
 */
@Composable
internal fun HeaderTitleRow(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = BackChevronStart, end = Spacing.sm, top = BackChevronTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) BackChevron(onBack)
        HeaderTitle(title, Modifier.weight(1f).padding(start = if (onBack == null) Spacing.sm else 0.dp))
        actions()
    }
}

/** The large title, one line; the size steps down until the whole title fits its space. */
@Composable
internal fun HeaderTitle(title: String, modifier: Modifier = Modifier) {
    var scale by remember(title) { mutableStateOf(1f) }
    val base = dev.handspell.app.ui.theme.AslText.largeTitle
    Text(
        title,
        style = base.copy(fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale),
        color = LocalAslColors.current.label,
        maxLines = 1, softWrap = false,
        onTextLayout = { if (it.hasVisualOverflow && scale > 0.55f) scale -= 0.06f },
        modifier = modifier.semantics { heading() },
    )
}

/** One line, always reserved under a header's title (empty or not), so every menu's top bar is exactly the same height. */
internal val HeaderSublineSlot = 30.dp

/** The line under a header's title: progress or one short sentence. Same inset, slot height and single line everywhere. */
@Composable
internal fun HeaderSubline(
    text: String?,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Box(modifier.fillMaxWidth().height(HeaderSublineSlot).padding(horizontal = Spacing.lg), contentAlignment = Alignment.CenterStart) {
        if (text != null) Text(text, style = style, color = LocalAslColors.current.labelSecondary, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/**
 * Header for the screens that manage their own scrolling below it (cameras, packs). Menu screens that scroll under a
 * frosted header use FrostedSettingsHero, which draws the same [HeaderTitleRow]. [compact] gives the camera screens a
 * smaller title so the picture keeps its height.
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
    Column(modifier.fillMaxWidth().statusBarsPadding()) {
        if (compact) Row(
            Modifier.fillMaxWidth().padding(start = BackChevronStart, end = Spacing.sm, top = BackChevronTop),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) BackChevron(onBack)
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.label,
                modifier = Modifier.weight(1f).padding(start = if (onBack == null) Spacing.md else 0.dp)
                    .semantics { heading() },
            )
            actions()
        } else {
            HeaderTitleRow(title, onBack, actions = actions)
            HeaderSubline(null)
            Spacer(Modifier.height(HeaderBottomSpace))
        }
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
    cornerRadius: Dp = LocalInsetCornerRadius.current ?: AslShapes.button,
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
    val interactions = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        modifier = modifier.pressScale(interactions).sizeIn(minHeight = Spacing.primaryButton)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        shape = RoundedCornerShape(cornerRadius),
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
fun AslCard(
    modifier: Modifier = Modifier,
    contentInset: Dp? = null,
    cornerRadius: Dp = LocalInsetCornerRadius.current ?: AslShapes.large,
    content: @Composable () -> Unit,
) {
    val colors = LocalAslColors.current
    androidx.compose.material3.Surface(
        modifier = Modifier.clip(RoundedCornerShape(cornerRadius)).then(modifier).fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = colors.surface,
        contentColor = colors.onSurface,
        content = {
            CompositionLocalProvider(LocalInsetCornerRadius provides contentInset?.let { AslShapes.inner(cornerRadius, it) }) {
                content()
            }
        },
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
                    .selectionOutline(selected)
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
