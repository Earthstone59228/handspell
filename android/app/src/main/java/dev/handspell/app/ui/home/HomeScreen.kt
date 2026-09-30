package dev.handspell.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.content.PackItem
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.PackKind
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.core.model.Letter
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectDrill: (PackItem.Drill) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPack: (ContentPack) -> Unit,
    showProPacks: Boolean = true,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalAslColors.current
    Column(modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        // The large "Practice" title is the first item of the catalogue, so the bar carries only the back chevron.
        ScreenHeader(title = "", onBack = onBack, compact = true)
        when {
            state.isLoading -> HomeMessage(stringResource(R.string.content_loading), null, null, Modifier.weight(1f))
            state.error -> HomeMessage(
                stringResource(R.string.content_unavailable_title),
                stringResource(R.string.content_unavailable_body), onRetry, Modifier.weight(1f),
            )
            else -> PracticeCatalogue(state, onSelectDrill, onOpenPack, showProPacks, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PracticeCatalogue(state: HomeUiState, onSelectDrill: (PackItem.Drill) -> Unit,
                              onOpenPack: (ContentPack) -> Unit, showProPacks: Boolean, modifier: Modifier) {
    val practised = state.attemptCounts.values.count { it > 0 }
    val next = state.drills.firstOrNull { (state.attemptCounts[it.letter] ?: 0) == 0 } ?: state.drills.firstOrNull()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Spacing.letterTile),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(stringResource(R.string.practice_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.home_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalAslColors.current.labelSecondary,
                )
            }
        }
        if (next != null) item(span = { GridItemSpan(maxLineSpan) }) {
            FeaturedLetter(next, practised, onSelectDrill, Modifier.padding(bottom = Spacing.xl))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(stringResource(R.string.practice_letters_heading), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.practice_letters_progress, practised, state.drills.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAslColors.current.labelSecondary,
                )
            }
        }
        items(state.drills, key = { it.letter.name }) { drill ->
            LetterTile(drill, state.attemptCounts[drill.letter] ?: 0, onSelectDrill)
        }
        val proPacks = state.packs.filter { showProPacks && it.kind != PackKind.DRILL }
        if (proPacks.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.home_pro_packs), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.xl))
        }
        items(proPacks, key = { it.packId }, span = { GridItemSpan(maxLineSpan) }) { pack ->
            Surface(
                modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
                    .clickable(role = Role.Button) { onOpenPack(pack) },
                shape = RoundedCornerShape(AslShapes.large), color = LocalAslColors.current.surface,
                contentColor = LocalAslColors.current.onSurface,
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(pack.title, style = MaterialTheme.typography.titleMedium)
                    Text(pack.summary, style = MaterialTheme.typography.bodyMedium,
                        color = LocalAslColors.current.onSurfaceSecondary)
                    if (!state.isPro) dev.handspell.app.ui.components.ProLockLabel(LocalAslColors.current.onSurfaceSecondary)
                }
            }
        }
    }
}

@Composable
private fun FeaturedLetter(
    drill: PackItem.Drill,
    practised: Int,
    onSelectDrill: (PackItem.Drill) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAslColors.current
    val description = stringResource(R.string.home_continue_description, drill.letter.display)
    Surface(
        modifier.fillMaxWidth().sizeIn(minHeight = Spacing.referenceGuide)
            .clickable(role = Role.Button) { onSelectDrill(drill) }
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(AslShapes.large),
        color = colors.accent,
    ) {
        Row(
            Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(drill.letter.display, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimary)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    stringResource(if (practised == 0) R.string.home_start_letter else R.string.home_continue_letter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(drill.prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun LetterTile(drill: PackItem.Drill, attempts: Int, onSelectDrill: (PackItem.Drill) -> Unit) {
    val colors = LocalAslColors.current
    val description = stringResource(R.string.letter_tile_progress_description, drill.letter.display, attempts)
    Surface(
        modifier = Modifier.fillMaxWidth().sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.letterTile)
            .clickable(role = Role.Button) { onSelectDrill(drill) }
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(AslShapes.large),
        color = colors.surface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(drill.letter.display, style = MaterialTheme.typography.titleLarge, color = colors.accent)
        }
    }
}

@Composable
private fun HomeMessage(title: String, body: String?, onRetry: (() -> Unit)?, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.xs))
        if (onRetry != null) AslButton(
            stringResource(R.string.retry), onRetry,
            Modifier.padding(top = Spacing.lg).sizeIn(minHeight = Spacing.touchTarget),
        )
    }
}
