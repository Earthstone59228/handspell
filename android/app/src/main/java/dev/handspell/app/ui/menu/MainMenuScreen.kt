package dev.handspell.app.ui.menu

import androidx.compose.foundation.Canvas
import dev.handspell.app.ui.components.fadedScrollEdges
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import dev.handspell.app.ui.theme.AslText
import dev.handspell.app.ui.theme.LocalDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.ui.settings.SettingsGroup
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** Extra space between a tile's ring (or glyph) and its title. */
private val TileRingGap = Spacing.md

/** The progress ring (and the Stories glyph box) on each home tile. */
private val TileRingSize = 68.dp

/** Callbacks of the main menu, grouped so the screen signature stays readable. */
data class MainMenuActions(
    val onLetters: () -> Unit,
    val onWords: () -> Unit,
    val onProgress: () -> Unit,
    val onSettings: () -> Unit,
    val onSpeed: () -> Unit = {},
    val onStories: () -> Unit = {},
    /** The plan chip beside settings: opens the Pro menu for both Free and Pro. */
    val onPro: () -> Unit = {},
)

/**
 * The app's first screen after the introduction: the alphabet menu's header (large title, icons on the right) over
 * two large entries in the alphabet's off-white card style, then the streak and the quieter rows.
 */
@Composable
fun MainMenuScreen(
    state: MainMenuState,
    actions: MainMenuActions,
    belowEntries: @Composable () -> Unit = {},
) {
    val colors = LocalAslColors.current
    val scrollState = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                PlanChip(state.isPro, actions.onPro)
                HeaderIcon(R.drawable.ic_settings, stringResource(R.string.menu_open_settings), actions.onSettings)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().fadedScrollEdges(scrollState).verticalScroll(scrollState)
                .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Spacer(Modifier.height(Spacing.xxxl))
                Column(Modifier.padding(horizontal = Spacing.xs).padding(bottom = Spacing.xxl)) {
                    Text("Hi, signer.", style = AslText.largeTitle, color = colors.labelSecondary)
                    Text("What will you\nlearn today?", style = AslText.largeTitle, color = colors.label,
                        modifier = Modifier.semantics { heading() })
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CompletionCard("Letters", state.lettersComplete, state.lettersTotal,
                        state.loading, actions.onLetters, Modifier.weight(1f))
                    CompletionCard("Words", state.wordsComplete, state.wordsTotal,
                        state.loading, actions.onWords, Modifier.weight(1f))
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CompletionCard("Progress", state.lettersComplete + state.wordsComplete,
                        state.lettersTotal + state.wordsTotal, state.loading, actions.onProgress, Modifier.weight(1f))
                    MenuEntryCard("Stories", "Story lessons", actions.onStories, "✎", Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.xs))
                belowEntries()
                // Room to scroll the last row clear of the bottom fade.
                Spacer(Modifier.height(Spacing.scrollFade))
            }
            }
        }

    }
}


@Composable
private fun CompletionCard(title: String, complete: Int, total: Int, loading: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAslColors.current
    val fraction = if (total > 0) (complete.toFloat() / total).coerceIn(0f, 1f) else 0f
    Surface(
        modifier.fillMaxWidth().fillMaxHeight().clip(RoundedCornerShape(AslShapes.extraLarge))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $complete of $total signs complete" },
        shape = RoundedCornerShape(AslShapes.extraLarge), color = colors.surface.copy(alpha = if (LocalDarkTheme.current) 0.65f else 0.92f),
        border = androidx.compose.foundation.BorderStroke(Spacing.hairline, colors.separator),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(TileRingSize), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                    val stroke = size.width * 0.08f
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val origin = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(colors.label.copy(alpha = 0.10f), 0f, 360f, false, origin, arcSize, style = Stroke(stroke))
                    drawArc(colors.accent, -90f, 360f * fraction, false, origin, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("${(fraction * 100).toInt()}%", style = AslText.headline, color = colors.label,
                    modifier = Modifier.clearAndSetSemantics {})
            }
            }
            Spacer(Modifier.height(TileRingGap))
            Text(title, style = AslText.title3, color = colors.label,
                maxLines = 2)
            Text(if (loading) "Loading progress" else "$complete / $total Complete",
                style = AslText.footnote, color = colors.labelSecondary)
        }
    }
}

/** "Free" or "Pro", in the header beside settings; a tap opens the Pro menu. */
@Composable
private fun PlanChip(isPro: Boolean, onClick: () -> Unit) {
    val colors = LocalAslColors.current
    val label = stringResource(if (isPro) R.string.menu_plan_pro else R.string.menu_plan_free)
    val description = stringResource(if (isPro) R.string.menu_plan_pro_description else R.string.menu_plan_free_description)
    val shape = RoundedCornerShape(AslShapes.tile)
    Box(
        Modifier.sizeIn(minHeight = Spacing.touchTarget).padding(vertical = Spacing.xxs).clip(shape)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AslText.headline, color = if (isPro) colors.accent else colors.labelSecondary,
            modifier = Modifier.padding(horizontal = Spacing.sm).clearAndSetSemantics {})
    }
}

/** 48dp header icon, drawn in the label colour like the alphabet menu's gear and paper. */
@Composable
internal fun HeaderIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(Spacing.touchTarget).clip(RoundedCornerShape(AslShapes.tile)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = LocalAslColors.current.label,
            modifier = Modifier.size(Spacing.xl + Spacing.xxs))
    }
}

/** A large entry in the alphabet's letter-card style: off-white card, dark title, small meta line, blue chevron. */
@Composable
private fun MenuEntryCard(title: String, meta: String?, onClick: () -> Unit, mark: String, modifier: Modifier) {
    val colors = LocalAslColors.current
    Surface(
        modifier.fillMaxWidth().fillMaxHeight()
            .clip(RoundedCornerShape(AslShapes.extraLarge)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(AslShapes.extraLarge),
        color = colors.surface.copy(alpha = if (LocalDarkTheme.current) 0.65f else 0.92f),
        contentColor = colors.label,
        border = androidx.compose.foundation.BorderStroke(Spacing.hairline, colors.separator),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Box(Modifier.fillMaxWidth().height(TileRingSize), contentAlignment = Alignment.Center) {
                Text(mark, style = AslText.title1, color = colors.labelTertiary,
                    modifier = Modifier.clearAndSetSemantics {})
            }
            Spacer(Modifier.height(TileRingGap))
            Text(title, style = AslText.title3,
                maxLines = 2)
            if (meta != null) Text(meta, style = AslText.footnote, color = colors.labelSecondary, maxLines = 2)
        }
    }
}

/** A grey row card (the Settings group style) with a leading mark, a title, one line of detail and a blue chevron. */
@Composable
fun MenuRowCard(title: String, body: String, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val colors = LocalAslColors.current
    SettingsGroup {
        Row(
            Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget).clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = colors.accent, modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
