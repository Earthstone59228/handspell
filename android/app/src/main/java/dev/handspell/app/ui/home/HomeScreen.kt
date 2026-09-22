package dev.handspell.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.content.PackItem
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectDrill: (PackItem.Drill) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) = when {
    state.isLoading -> LoadingHome(modifier)
    state.error -> UnavailableHome(onRetry, modifier)
    else -> PracticeHome(state.drills, onSelectDrill, modifier)
}

@Composable
private fun PracticeHome(drills: List<PackItem.Drill>, onSelectDrill: (PackItem.Drill) -> Unit, modifier: Modifier) {
    val colors = LocalAslColors.current
    Column(
        modifier = modifier.fillMaxSize().background(colors.backgroundGrouped),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = stringResource(R.string.practice_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.home_description), style = MaterialTheme.typography.bodyLarge, color = colors.labelSecondary)
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = stringResource(R.string.practice_letters_heading), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.practice_letters_summary, drills.size), style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = Spacing.letterTile),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(drills, key = { it.letter.name }) { drill -> LetterTile(drill, onSelectDrill) }
        }
    }
}

@Composable
private fun LetterTile(drill: PackItem.Drill, onSelectDrill: (PackItem.Drill) -> Unit) {
    val colors = LocalAslColors.current
    val description = stringResource(R.string.letter_tile_content_description, drill.letter.display)
    Column(
        modifier = Modifier
            .sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget)
            .heightIn(min = Spacing.letterTile)
            .background(colors.surface, RoundedCornerShape(AslShapes.medium))
            .clickable(role = Role.Button, onClick = { onSelectDrill(drill) })
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = drill.letter.display, style = MaterialTheme.typography.titleLarge, color = colors.accent)
    }
}

@Composable
private fun LoadingHome(modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Text(text = stringResource(R.string.content_loading), modifier = Modifier.padding(top = Spacing.sm))
    }
}

@Composable
private fun UnavailableHome(onRetry: () -> Unit, modifier: Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.content_unavailable_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.content_unavailable_body), modifier = Modifier.padding(top = Spacing.xs))
        Button(onClick = onRetry, modifier = Modifier.padding(top = Spacing.lg).sizeIn(minHeight = Spacing.touchTarget)) {
            Text(stringResource(R.string.retry))
        }
    }
}
