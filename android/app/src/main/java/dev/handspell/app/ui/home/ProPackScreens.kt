package dev.handspell.app.ui.home

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.content.PackItem
import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.SpeedRunResult
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.ui.theme.AslMotion
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ProPackRoute(
    packId: String,
    repository: ContentRepository,
    drills: List<PackItem.Drill>,
    detector: SignDetector,
    catalog: CanonicalHandshapeCatalog,
    progress: ProgressStore,
    onBack: () -> Unit,
) {
    var pack by remember(packId) { mutableStateOf<ContentPack?>(null) }
    var failed by remember(packId) { mutableStateOf(false) }
    LaunchedEffect(packId) {
        pack = runCatching { repository.pack(packId) }.getOrNull()
        failed = pack == null
    }
    when {
        failed -> PackMessage(stringResource(R.string.pro_pack_unavailable), onBack)
        pack == null -> PackMessage(stringResource(R.string.content_loading), onBack)
        drills.isEmpty() -> PackMessage(stringResource(R.string.content_unavailable_body), onBack)
        pack!!.items.firstOrNull() is PackItem.StoryStep -> StoryPack(pack!!, drills, detector, catalog, progress, onBack)
        pack!!.items.firstOrNull() is PackItem.SpeedRound -> SpeedPack(pack!!, drills, detector, catalog, progress, onBack)
        else -> PackMessage(stringResource(R.string.pro_pack_unavailable), onBack)
    }
}

@Composable
private fun PackMessage(message: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) { Text(stringResource(R.string.back)) }
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StoryPack(pack: ContentPack, drills: List<PackItem.Drill>, detector: SignDetector,
                      catalog: CanonicalHandshapeCatalog, progress: ProgressStore, onBack: () -> Unit) {
    val steps = remember(pack) { pack.items.filterIsInstance<PackItem.StoryStep>() }
    val scope = rememberCoroutineScope()
    var stepIndex by rememberSaveable(pack.packId) { mutableStateOf(0) }
    var letterIndex by rememberSaveable(pack.packId) { mutableStateOf(0) }
    var skipped by rememberSaveable(pack.packId) { mutableStateOf(false) }
    fun advanceStep(step: PackItem.StoryStep, completed: Boolean) {
        if (completed) scope.launch { progress.recordStoryStep(step.id) }
        stepIndex++
        letterIndex = 0
        skipped = false
    }
    val step = steps.getOrNull(stepIndex)
    if (step == null) {
        Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(pack.title, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.story_complete), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { stepIndex = 0 }, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                Text(stringResource(R.string.story_again))
            }
            TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) { Text(stringResource(R.string.back)) }
        }
        return
    }
    val currentLetter = step.letters.getOrNull(letterIndex)
    val drill = drills.firstOrNull { it.letter == currentLetter }
    Column(Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.story_step_count, stepIndex + 1, steps.size), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.md))
        }
        Text(pack.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.md))
        if (currentLetter == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                Text(step.narration.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { advanceStep(step, true) }, modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)) {
                    Text(stringResource(R.string.story_next))
                }
            }
        } else if (drill == null) {
            PackMessage(stringResource(R.string.pro_pack_unavailable), onBack)
        } else {
            Text(stringResource(R.string.story_spell_progress, step.spellWord.orEmpty(), letterIndex + 1, step.letters.size),
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = Spacing.md))
            LetterDrillRoute(
                drill = drill, drills = drills, signDetector = detector, canonicalHandshapeCatalog = catalog,
                progressStore = progress, onBack = onBack,
                onSkip = {
                    skipped = true
                    if (letterIndex + 1 == step.letters.size) advanceStep(step, false) else letterIndex++
                },
                onMatch = {
                    if (letterIndex + 1 == step.letters.size) advanceStep(step, !skipped) else letterIndex++
                },
                modifier = Modifier.weight(1f),
                sessionKey = "story-${stepIndex}-${letterIndex}",
            )
        }
    }
}

@Composable
private fun SpeedPack(pack: ContentPack, drills: List<PackItem.Drill>, detector: SignDetector,
                      catalog: CanonicalHandshapeCatalog, progress: ProgressStore, onBack: () -> Unit) {
    val rounds = remember(pack) { pack.items.filterIsInstance<PackItem.SpeedRound>() }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable(pack.packId) { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var remaining by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var promptIndex by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val round = rounds.firstOrNull { it.id == selected }
    LaunchedEffect(startedAt, round?.id) {
        val start = startedAt ?: return@LaunchedEffect
        val activeRound = round ?: return@LaunchedEffect
        while (true) {
            remaining = (activeRound.durationSeconds - ((SystemClock.elapsedRealtime() - start) / 1000).toInt()).coerceAtLeast(0)
            if (remaining == 0) { finished = true; startedAt = null; break }
            delay(AslMotion.quickMillis.toLong())
        }
    }
    LaunchedEffect(finished) {
        if (finished && round != null) progress.recordSpeedRun(SpeedRunResult(round.id, System.currentTimeMillis(), score, round.durationSeconds))
    }
    Column(Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped)) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = Spacing.md).sizeIn(minHeight = Spacing.touchTarget)) {
            Text(stringResource(R.string.back))
        }
        Text(pack.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = Spacing.md))
        when {
            round == null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                rounds.forEach { option ->
                    Button(onClick = { selected = option.id; remaining = option.durationSeconds },
                        modifier = Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)) {
                        Text(stringResource(R.string.speed_round_option, option.title, option.durationSeconds))
                    }
                }
            }
            finished -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.speed_result, score), style = MaterialTheme.typography.titleLarge)
                Button(onClick = { score = 0; promptIndex = 0; finished = false; remaining = round.durationSeconds;
                    startedAt = SystemClock.elapsedRealtime() }, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                    Text(stringResource(R.string.speed_again))
                }
            }
            startedAt == null -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(stringResource(R.string.speed_intro, round.durationSeconds), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { score = 0; promptIndex = 0; remaining = round.durationSeconds;
                    startedAt = SystemClock.elapsedRealtime() }, modifier = Modifier.sizeIn(minHeight = Spacing.touchTarget)) {
                    Text(stringResource(R.string.speed_start))
                }
            }
            else -> {
                Text(stringResource(R.string.speed_live, remaining, score), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = Spacing.md))
                val letter = round.letters.takeIf { it.isNotEmpty() }?.get(promptIndex % round.letters.size)
                val drill = drills.firstOrNull { it.letter == letter }
                if (drill == null) PackMessage(stringResource(R.string.pro_pack_unavailable), onBack)
                else LetterDrillRoute(drill, drills, detector, catalog, progress, onBack,
                    onSkip = { promptIndex++ }, onMatch = { score++; promptIndex++ }, modifier = Modifier.weight(1f),
                    sessionKey = "speed-${round.id}-$promptIndex")
            }
        }
    }
}
