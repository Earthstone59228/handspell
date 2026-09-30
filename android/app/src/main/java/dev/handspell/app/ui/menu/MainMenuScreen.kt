package dev.handspell.app.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import dev.handspell.app.ui.components.captureBackdrop
import dev.handspell.app.ui.components.scrollEdgeBackdrop
import androidx.compose.runtime.mutableIntStateOf
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

/** Callbacks of the main menu, grouped so the screen signature stays readable. */
data class MainMenuActions(
    val onLetters: () -> Unit,
    val onWords: () -> Unit,
    val onProgress: () -> Unit,
    val onSettings: () -> Unit,
    val onPaper: () -> Unit,
    val onSpeed: () -> Unit = {},
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
    val contentLayer = rememberGraphicsLayer()
    var paneHeight by remember { mutableIntStateOf(0) }
    val fadePx = with(LocalDensity.current) { Spacing.scrollFade.toPx() }
    val tileHeight = with(LocalDensity.current) {
        Spacing.progressRing + Spacing.lg * 2 + AslText.title3.lineHeight.toDp() * 2 +
            AslText.footnote.lineHeight.toDp() * 2 + Spacing.xxs * 2
    }
    Box(Modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Spacing.lg).padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HeaderIcon(R.drawable.ic_paper, stringResource(R.string.menu_open_paper), actions.onPaper)
                Spacer(Modifier.weight(1f))
                Text("Pro", style = AslText.headline,
                    modifier = Modifier.clip(RoundedCornerShape(AslShapes.tile))
                        .clickable(role = Role.Button, onClick = actions.onPro).padding(Spacing.sm))
                HeaderIcon(R.drawable.ic_settings, stringResource(R.string.menu_open_settings), actions.onSettings)
            }
            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { paneHeight = it.height }) {
            Column(Modifier.fillMaxSize().captureBackdrop(contentLayer).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Spacer(Modifier.height(Spacing.xxxl))
                Column(Modifier.padding(horizontal = Spacing.xs).padding(bottom = Spacing.xxl)) {
                    Text("Hi, signer.", style = AslText.largeTitle, color = colors.labelSecondary)
                    Text("What will you\nlearn today?", style = AslText.largeTitle, color = colors.label,
                        modifier = Modifier.semantics { heading() })
                }
                Row(Modifier.height(tileHeight), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CompletionCard("Letters", state.lettersComplete, state.lettersTotal,
                        state.loading, actions.onLetters, Modifier.weight(1f))
                    CompletionCard("Words", state.wordsComplete, state.wordsTotal,
                        state.loading, actions.onWords, Modifier.weight(1f))
                }
                Row(Modifier.height(tileHeight), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CompletionCard("Progress", state.lettersComplete + state.wordsComplete,
                        state.lettersTotal + state.wordsTotal, state.loading, actions.onProgress, Modifier.weight(1f))
                    MenuEntryCard("Speed challenge", "Sign against the clock", actions.onSpeed, "◷", Modifier.weight(1f))
                }
                Spacer(Modifier.height(Spacing.xs))
                belowEntries()
            }
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(Spacing.scrollFade)
                .scrollEdgeBackdrop(contentLayer, 0f, true, colors.backgroundGrouped))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(Spacing.scrollFade)
                .scrollEdgeBackdrop(contentLayer, paneHeight - fadePx, false, colors.backgroundGrouped))
            }
        }

    }
}


@Composable
private fun CompletionCard(title: String, complete: Int, total: Int, loading: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAslColors.current
    val fraction = if (total > 0) (complete.toFloat() / total).coerceIn(0f, 1f) else 0f
    Surface(
        modifier.fillMaxWidth().fillMaxHeight().sizeIn(minHeight = Spacing.featureTile).clip(RoundedCornerShape(AslShapes.extraLarge))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $complete of $total signs complete" },
        shape = RoundedCornerShape(AslShapes.extraLarge), color = colors.surface.copy(alpha = if (LocalDarkTheme.current) 0.65f else 0.92f),
        border = androidx.compose.foundation.BorderStroke(Spacing.hairline, colors.separator),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Box(Modifier.size(Spacing.progressRing), contentAlignment = Alignment.Center) {
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
            Text(title, style = AslText.title3, color = colors.label,
                modifier = Modifier.height(with(LocalDensity.current) { AslText.title3.lineHeight.toDp() * 2 }), maxLines = 2)
            Text(if (loading) "Loading progress" else "$complete / $total complete",
                style = AslText.footnote, color = colors.labelSecondary)
        }
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
        modifier.fillMaxWidth().fillMaxHeight().sizeIn(minHeight = Spacing.featureTile)
            .clip(RoundedCornerShape(AslShapes.extraLarge)).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(AslShapes.extraLarge),
        color = colors.surface.copy(alpha = if (LocalDarkTheme.current) 0.65f else 0.92f),
        contentColor = colors.label,
        border = androidx.compose.foundation.BorderStroke(Spacing.hairline, colors.separator),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Box(Modifier.size(Spacing.progressRing), contentAlignment = Alignment.CenterStart) {
                Text(mark, style = AslText.title1, color = colors.labelTertiary,
                    modifier = Modifier.clearAndSetSemantics {})
            }
            Text(title, style = AslText.title3,
                modifier = Modifier.height(with(LocalDensity.current) { AslText.title3.lineHeight.toDp() * 2 }), maxLines = 2)
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
