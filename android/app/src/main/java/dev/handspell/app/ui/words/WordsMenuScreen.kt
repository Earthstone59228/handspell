package dev.handspell.app.ui.words

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.handspell.app.R
import dev.handspell.app.content.Tier
import dev.handspell.app.content.WordEntry
import dev.handspell.app.content.WordReference
import androidx.compose.ui.text.style.TextAlign
import dev.handspell.app.progress.WordRecord
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.components.AslButtonPair
import dev.handspell.app.ui.components.AslSheet
import dev.handspell.app.ui.pro.WordsProStrip
import dev.handspell.app.ui.components.ProLockLabel
import dev.handspell.app.ui.settings.FrostedSettingsHero
import dev.handspell.app.ui.settings.SettingsGroupFooter
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/** What the Words menu shows. [loading] until the bundled list has been read. */
data class WordsMenuState(
    val loading: Boolean = true,
    val words: List<WordEntry> = emptyList(),
    val records: Map<String, WordRecord> = emptyMap(),
    val isPro: Boolean = false,
) {
    /** Complete words among those the learner can open (locked Pro words are not counted against them). */
    val completeCount: Int get() = practicable.count { records[it.gloss]?.complete == true }

    fun isLocked(word: WordEntry): Boolean = word.tier == Tier.PRO && !isPro

    /** Words the drill cycles through: everything the learner can open. */
    val practicable: List<WordEntry> get() = words.filterNot(::isLocked)
}

/**
 * The words menu: the alphabet's header and its off-white cards, one per word. A tap opens the same practice sheet
 * as a letter (Practice, Mark complete); a Pro word shows the neutral lock and asks for the paywall instead.
 */
@Composable
fun WordsMenuScreen(
    state: WordsMenuState,
    onBack: () -> Unit,
    onPractice: (WordEntry) -> Unit,
    onMarkComplete: (WordEntry, Boolean) -> Unit,
    onLocked: (WordEntry) -> Unit,
    proStrip: Boolean = false,
    onDismissProStrip: () -> Unit = {},
    references: Map<String, WordReference> = emptyMap(),
) {
    var selectedGloss by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.words.firstOrNull { it.gloss == selectedGloss }
    Box {
        FrostedSettingsHero(
            onBack = onBack,
            title = stringResource(R.string.words_title),
            body = if (state.loading) null
            else stringResource(R.string.words_hero_body, state.completeCount, state.practicable.size),
        ) {
            when {
                state.loading -> Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge)
                state.words.isEmpty() -> Text(stringResource(R.string.words_empty), style = MaterialTheme.typography.bodyLarge)
                else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    state.words.forEachIndexed { index, word ->
                        WordCard(word, index, state.records[word.gloss], state.isLocked(word)) {
                            if (state.isLocked(word)) onLocked(word) else selectedGloss = word.gloss
                        }
                    }
                    SettingsGroupFooter(stringResource(R.string.words_footer))
                    val locked = state.words.filter(state::isLocked)
                    if (proStrip && locked.isNotEmpty()) WordsProStrip(
                        proWordCount = locked.size, examples = locked.take(3).map { it.display },
                        onSeePro = { onLocked(locked.first()) }, onDismiss = onDismissProStrip,
                    )
                }
            }
        }
        AslSheet(visible = selected != null, onDismiss = { selectedGloss = null }) {
            if (selected != null) WordSheet(
                word = selected,
                reference = references[selected.gloss],
                complete = state.records[selected.gloss]?.complete == true,
                markedComplete = state.records[selected.gloss]?.markedComplete == true,
                onPractice = { selectedGloss = null; onPractice(selected) },
                onMarkComplete = { complete -> selectedGloss = null; onMarkComplete(selected, complete) },
            )
        }
    }
}

@Composable
private fun WordCard(word: WordEntry, index: Int, record: WordRecord?, locked: Boolean, onClick: () -> Unit) {
    val colors = LocalAslColors.current
    val complete = record?.complete == true
    val description = when {
        locked -> stringResource(R.string.words_card_locked_description, word.display)
        complete -> stringResource(R.string.words_card_complete_description, word.display)
        else -> stringResource(R.string.words_card_description, word.display)
    }
    Surface(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.referenceGuide)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(AslShapes.large),
        color = colors.card,
        contentColor = colors.onCard,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                word.display, style = MaterialTheme.typography.displaySmall, color = colors.onCard,
                modifier = Modifier.weight(1f).alpha(if (locked) 0.62f else 1f),
            )
            when {
                locked -> ProLockLabel(colors.onCardSecondary)
                complete -> Text(stringResource(R.string.words_card_complete), style = MaterialTheme.typography.labelSmall,
                    color = colors.accent)
                else -> Text((index + 1).toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall,
                    color = colors.onCardSecondary)
            }
        }
    }
}

/** Practice is always offered, also for a completed word; the second action marks or unmarks it. */
@Composable
private fun WordSheet(
    word: WordEntry,
    reference: WordReference?,
    complete: Boolean,
    markedComplete: Boolean,
    onPractice: () -> Unit,
    onMarkComplete: (Boolean) -> Unit,
) {
    val colors = LocalAslColors.current
    Surface(
        Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.referenceGuide * 1.5f),
        shape = RoundedCornerShape(AslShapes.extraLarge),
        color = colors.card,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Like the letter sheet's tile, but showing how the word is signed; the word itself is the title below.
            if (reference != null) WordReferenceView(reference, word.display,
                Modifier.padding(Spacing.sm).sizeIn(maxWidth = Spacing.referenceGuide * 2.2f).fillMaxWidth(),
                mutedColor = colors.onCardSecondary)
            else Text(word.display, style = MaterialTheme.typography.displaySmall, color = colors.accent,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(Spacing.xl))
        }
    }
    Text(
        stringResource(if (complete) R.string.words_sheet_title_complete else R.string.words_sheet_title, word.display),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(stringResource(R.string.word_drill_hint), style = MaterialTheme.typography.bodyMedium,
        color = colors.labelSecondary, textAlign = TextAlign.Center)
    word.tip?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary, textAlign = TextAlign.Center) }
    val undo = stringResource(R.string.mark_complete_undo)
    AslButtonPair(
        first = { modifier -> AslButton(stringResource(R.string.words_practice), onPractice, modifier, singleLine = true) },
        second = when {
            markedComplete -> { modifier ->
                AslButton(stringResource(R.string.mark_complete_undo_short), { onMarkComplete(false) }, modifier,
                    style = AslButtonStyle.Secondary, contentDescription = undo, singleLine = true)
            }
            !complete -> { modifier ->
                AslButton(stringResource(R.string.mark_complete), { onMarkComplete(true) }, modifier,
                    style = AslButtonStyle.Secondary, singleLine = true)
            }
            else -> null
        },
    )
}
