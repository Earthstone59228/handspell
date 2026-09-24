package dev.handspell.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.handspell.app.R
import dev.handspell.app.ui.components.AslCard
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

@Composable
fun LegalScreen(title: String, assetName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAslColors.current
    val blocks = remember(assetName) {
        runCatching { context.assets.open("legal/$assetName").bufferedReader().use { it.readText() } }
            .getOrNull()?.let(::parseDocument)
    }
    Column(Modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        ScreenHeader(title, onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (blocks == null) {
                Text(stringResource(R.string.settings_legal_unavailable), style = MaterialTheme.typography.bodyLarge, color = colors.label)
            } else {
                val sections = blocks.toSections()
                sections.forEach { section ->
                    if (section.heading == null) {
                        // Text before the first heading (e.g. the notice's date line) reads as a subtitle.
                        DocumentBlocks(section.blocks, onCard = false)
                    } else {
                        Text(
                            section.heading,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.label,
                            modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm).semantics { heading() },
                        )
                        AslCard { Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            DocumentBlocks(section.blocks, onCard = true)
                        } }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentBlocks(blocks: List<DocBlock>, onCard: Boolean) {
    val colors = LocalAslColors.current
    val text = if (onCard) colors.onSurface else colors.labelSecondary
    val body = MaterialTheme.typography.bodyLarge
    blocks.forEach { block ->
        when (block) {
            is DocBlock.Heading -> Unit // consumed by toSections()
            is DocBlock.Paragraph -> Text(inline(block.text), style = body, color = text,
                modifier = if (onCard) Modifier else Modifier.padding(horizontal = Spacing.xs))
            is DocBlock.Item -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    block.marker, style = body, color = if (onCard) colors.accent else text,
                    fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 18.dp),
                )
                Text(inline(block.text), style = body, color = text, modifier = Modifier.weight(1f))
            }
        }
    }
}

private sealed interface DocBlock {
    data class Heading(val level: Int, val text: String) : DocBlock
    data class Paragraph(val text: String) : DocBlock
    data class Item(val marker: String, val text: String) : DocBlock
}

private class DocSection(val heading: String?, val blocks: List<DocBlock>)

/** Splits at `##` headings; a leading `# Title` is dropped because the screen header already shows it. */
private fun List<DocBlock>.toSections(): List<DocSection> {
    val out = mutableListOf<DocSection>()
    var heading: String? = null
    var current = mutableListOf<DocBlock>()
    fun flush() { if (heading != null || current.isNotEmpty()) out += DocSection(heading, current) }
    for (block in this) {
        if (block is DocBlock.Heading && block.level <= 2) {
            if (block.level == 2) { flush(); heading = block.text; current = mutableListOf() }
        } else current += block
    }
    flush()
    return out
}

/**
 * Small Markdown reader for the bundled documents: headings, bullets, numbered items, wrapped continuation lines.
 * Plain `.txt` licences go through the same path, so hard-wrapped lines rejoin into paragraphs.
 */
private fun parseDocument(source: String): List<DocBlock> {
    val blocks = mutableListOf<DocBlock>()
    var paragraph: StringBuilder? = null
    var item: Pair<String, StringBuilder>? = null
    fun flush() {
        paragraph?.let { blocks += DocBlock.Paragraph(it.toString()) }
        item?.let { blocks += DocBlock.Item(it.first, it.second.toString()) }
        paragraph = null; item = null
    }
    val bullet = Regex("^\\s{0,3}[-*•]\\s+(.*)$")
    val numbered = Regex("^\\s{0,3}(\\d+[.)])\\s+(.*)$")
    for (raw in source.lines()) {
        val line = raw.trimEnd()
        val heading = Regex("^(#{1,6})\\s+(.*)$").find(line)
        when {
            line.isBlank() -> flush()
            heading != null -> {
                flush()
                val level = heading.groupValues[1].length
                // Levels 1-2 split sections (see toSections); deeper headings stay as bold lines inside a card.
                blocks += if (level <= 2) DocBlock.Heading(level, heading.groupValues[2])
                else DocBlock.Paragraph("**${heading.groupValues[2]}**")
            }
            bullet.matches(line) -> { flush(); item = "•" to StringBuilder(bullet.find(line)!!.groupValues[1]) }
            numbered.matches(line) -> {
                flush()
                val m = numbered.find(line)!!
                item = m.groupValues[1] to StringBuilder(m.groupValues[2])
            }
            item != null -> item!!.second.append(' ').append(line.trim())
            paragraph != null -> paragraph!!.append(' ').append(line.trim())
            else -> paragraph = StringBuilder(line.trim())
        }
    }
    flush()
    return blocks
}

/** `**bold**`, `` `code` `` and `[label](url)` become styled text; nothing is left as raw Markdown. */
private fun inline(source: String): AnnotatedString = buildAnnotatedString {
    val token = Regex("\\*\\*(.+?)\\*\\*|`(.+?)`|\\[(.+?)]\\((.+?)\\)")
    var index = 0
    for (match in token.findAll(source)) {
        append(source.substring(index, match.range.first))
        val (bold, code, label) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            else -> append(label)
        }
        index = match.range.last + 1
    }
    append(source.substring(index))
}
