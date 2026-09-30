package dev.handspell.app.ui.words

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.handspell.app.R
import dev.handspell.app.content.Tier
import dev.handspell.app.content.WordEntry
import dev.handspell.app.content.WordReference
import dev.handspell.app.progress.WordRecord
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonPair
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.components.AslSheet
import dev.handspell.app.ui.components.BackChevron
import dev.handspell.app.ui.components.ProLockLabel
import dev.handspell.app.ui.menu.HeaderIcon
import dev.handspell.app.ui.pro.WordsProStrip
import dev.handspell.app.ui.settings.SettingsGroupFooter
import dev.handspell.app.ui.theme.AslPalette
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.AslText
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import dev.handspell.app.ui.theme.Spacing
import kotlinx.coroutines.launch

/** What the Words menu shows. [loading] until the bundled list has been read. */
data class WordsMenuState(
    val loading: Boolean = true,
    val words: List<WordEntry> = emptyList(),
    val records: Map<String, WordRecord> = emptyMap(),
    val isPro: Boolean = false,
) {
    /** Complete words among those the learner can open (locked Pro words are not counted against them). */
    val completeCount: Int get() = practicable.count { records[it.gloss]?.complete == true }

    /** The header's "N / M" total: the words this learner can open (free set for free users, all for Pro), like the menu. */
    val openTotal: Int get() = practicable.size

    fun isLocked(word: WordEntry): Boolean = word.tier == Tier.PRO && !isPro

    /** Words the drill cycles through: everything the learner can open. */
    val practicable: List<WordEntry> get() = words.filterNot(::isLocked)
}

/** The header's progress line, as the alphabet writes it: "3 / 36". */
fun wordsProgressLine(complete: Int, total: Int): String = "$complete / $total"

/**
 * The words menu, built as the alphabet page is: a frosted header (back, bold title, settings and documents icons,
 * "N / M"), the off-white cards (word on the left, index or "complete" top right, the hand wireframe in the bordered
 * slot bottom right). A tap opens the practice sheet; a
 * Pro word keeps the neutral lock and asks for the paywall.
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
    onSettings: () -> Unit = {},
    onPaper: () -> Unit = {},
    leftHanded: Boolean = false,
) {
    val colors = LocalAslColors.current
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current
    var selectedGloss by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = state.words.firstOrNull { it.gloss == selectedGloss }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var headerHeight by remember { mutableIntStateOf(0) }
    val headerDp = with(density) { headerHeight.toDp() }
    Box(Modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = headerDp + Spacing.xs, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            when {
                state.loading -> item { Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge) }
                state.words.isEmpty() -> item { Text(stringResource(R.string.words_empty), style = MaterialTheme.typography.bodyLarge) }
                else -> {
                    itemsIndexed(state.words, key = { _, word -> word.gloss }) { index, word ->
                        WordCard(word, index, state.records[word.gloss], state.isLocked(word), references[word.gloss],
                            selected = word.gloss == selectedGloss) {
                            if (state.isLocked(word)) onLocked(word) else selectedGloss = word.gloss
                        }
                    }
                    item { SettingsGroupFooter(stringResource(R.string.words_footer)) }
                    val locked = state.words.filter(state::isLocked)
                    if (proStrip && locked.isNotEmpty()) item {
                        WordsProStrip(
                            proWordCount = locked.size, examples = locked.take(3).map { it.display },
                            onSeePro = { onLocked(locked.first()) }, onDismiss = onDismissProStrip,
                        )
                    }
                }
            }
        }
        // Header over the list, like the alphabet's frosted .top-area.
        Column(
            Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                .background(colors.backgroundGrouped.copy(alpha = 0.94f)),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.xs, end = Spacing.sm, top = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically) {
                BackChevron(onBack)
                Text(stringResource(R.string.words_title), style = AslText.largeTitle, color = colors.label,
                    modifier = Modifier.weight(1f).semantics { heading() })
                HeaderIcon(R.drawable.ic_settings, stringResource(R.string.menu_open_settings), onSettings)
                HeaderIcon(R.drawable.ic_paper, stringResource(R.string.menu_open_paper), onPaper)
            }
            if (!state.loading) Text(
                wordsProgressLine(state.completeCount, state.openTotal), style = AslText.progressLine,
                color = colors.label.copy(alpha = 0.62f),
                modifier = Modifier.padding(start = Spacing.lg, top = Spacing.xs, bottom = Spacing.sm)
                    .semantics { contentDescription = "${state.completeCount} of ${state.openTotal}" },
            )
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
private fun WordCard(
    word: WordEntry,
    index: Int,
    record: WordRecord?,
    locked: Boolean,
    reference: WordReference?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAslColors.current
    val complete = record?.complete == true
    val description = when {
        locked -> stringResource(R.string.words_card_locked_description, word.display)
        complete -> stringResource(R.string.words_card_complete_description, word.display)
        else -> stringResource(R.string.words_card_description, word.display)
    }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val shape = RoundedCornerShape(AslShapes.large)
    Box(
        Modifier.fillMaxWidth().height(Spacing.letterCard)
            .graphicsLayer { val s = if (pressed) 0.985f else 1f; scaleX = s; scaleY = s }
            .clip(shape).background(colors.card)
            .then(if (selected) Modifier.border(Spacing.hairline, colors.accent.copy(alpha = 0.6f), shape) else Modifier)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = WORD_CARD_PAD_H, vertical = Spacing.sm),
    ) {
        BasicText(
            word.display,
            style = AslText.wordCard.copy(color = colors.onCard),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = AslText.wordCard.fontSize),
            modifier = Modifier.align(Alignment.CenterStart)
                .padding(end = Spacing.cardThumbWidth + Spacing.sm).alpha(if (locked) 0.62f else 1f)
                .clearAndSetSemantics {},
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            when {
                locked -> ProLockLabel(colors.onCardSecondary)
                complete -> Text(stringResource(R.string.words_card_complete), style = AslText.cardMeta, color = AslPalette.BlueText)
                else -> Text((index + 1).toString().padStart(2, '0'), style = AslText.cardMeta, color = AslPalette.MetaGrey)
            }
        }
        val thumbShape = RoundedCornerShape(AslShapes.thumb)
        Box(
            Modifier.align(Alignment.BottomEnd).size(Spacing.cardThumbWidth, Spacing.cardThumbHeight)
                .clip(thumbShape).border(Spacing.hairline, colors.onCard.copy(alpha = 0.14f), thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            if (reference != null) WordHandThumbnail(reference, Modifier.fillMaxSize())
        }
    }
}

/** The web card's 19px side padding. */
private val WORD_CARD_PAD_H = Spacing.lg - Spacing.hairline

/**
 * The letter sheet for a word: the big off-white tile with the animated example, the word as the title, "COMPLETE"
 * when it is, the tip and the one-hand hint, then Practice and Mark complete (or Undo) side by side.
 */
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
    Box(
        Modifier.size(Spacing.practiceTile).clip(RoundedCornerShape(AslShapes.tile)).background(colors.card),
        contentAlignment = Alignment.Center,
    ) {
        if (reference != null) WordReferenceView(reference, word.display, Modifier.fillMaxSize().padding(Spacing.xs))
        else Text(word.display, style = AslText.wordCard, color = colors.accent)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(word.display, style = AslText.sheetTitle, color = colors.label, textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() })
        if (complete) Text(stringResource(R.string.words_card_complete).uppercase(), style = AslText.progressLine,
            color = colors.label.copy(alpha = 0.62f))
        word.tip?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary, textAlign = TextAlign.Center) }
        Text(stringResource(R.string.word_drill_hint), style = MaterialTheme.typography.bodyMedium,
            color = colors.labelSecondary, textAlign = TextAlign.Center)
    }
    val undo = stringResource(R.string.mark_complete_undo)
    AslButtonPair(
        first = { modifier -> AslButton(stringResource(R.string.words_practice), onPractice, modifier, singleLine = true) },
        second = when {
            markedComplete -> { modifier ->
                AslButton(stringResource(R.string.mark_complete_undo_short), { onMarkComplete(false) }, modifier,
                    style = AslButtonStyle.Card, contentDescription = undo, singleLine = true)
            }
            !complete -> { modifier ->
                AslButton(stringResource(R.string.mark_complete), { onMarkComplete(true) }, modifier,
                    style = AslButtonStyle.Card, singleLine = true)
            }
            else -> null
        },
    )
}
