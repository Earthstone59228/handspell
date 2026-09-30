package dev.handspell.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.R
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.Letter
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.selectionOutline
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.AslText
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.LocalReduceMotion
import dev.handspell.app.ui.theme.Spacing
import dev.handspell.app.ui.theme.atmosphereBrush
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import kotlinx.coroutines.launch

private const val STEPS = 3

/** The two writes the first-run screen makes; kept out of the composable so they can be tested. */
class OnboardingActions(private val progressStore: ProgressStore, private val preferences: AppPreferencesStore) {
    suspend fun setLeftHanded(enabled: Boolean) = preferences.setLeftHanded(enabled)
    suspend fun finish() = progressStore.setOnboardingCompleted(true)
}

@Composable
fun OnboardingRoute(
    progressStore: ProgressStore,
    preferences: AppPreferencesStore,
    catalog: CanonicalHandshapeCatalog,
    onFinished: () -> Unit,
) {
    val actions = remember(progressStore, preferences) { OnboardingActions(progressStore, preferences) }
    val scope = rememberCoroutineScope()
    val leftHanded by preferences.leftHanded.collectAsStateWithLifecycle(initialValue = false)
    // Real recorded poses for the first animation; a letter without a recording is simply left out.
    val shapes by produceState(emptyList<CanonicalHandshape>(), catalog) {
        value = listOf(Letter.A, Letter.B, Letter.C, Letter.L).mapNotNull { runCatching { catalog.handshapeFor(it) }.getOrNull() }
    }
    OnboardingScreen(
        leftHanded = leftHanded,
        onLeftHanded = { scope.launch { actions.setLeftHanded(it) } },
        onContinue = {
            onFinished()
            scope.launch { actions.finish() }
        },
        shapes = shapes,
    )
}

/**
 * First run: three short, swipeable steps (what the app does, how to set the phone up, privacy and handedness), each
 * with one looping picture and a line or two of text. Skip is always one tap away; Back steps to the previous step.
 */
@Composable
fun OnboardingScreen(
    leftHanded: Boolean,
    onLeftHanded: (Boolean) -> Unit,
    onContinue: () -> Unit,
    shapes: List<CanonicalHandshape> = emptyList(),
) {
    val colors = LocalAslColors.current
    val reduceMotion = LocalReduceMotion.current
    val pager = rememberPagerState(pageCount = { STEPS })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == STEPS - 1
    fun goTo(page: Int) = scope.launch { if (reduceMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page) }
    BackHandler(enabled = pager.currentPage > 0) { goTo(pager.currentPage - 1) }
    Column(Modifier.fillMaxSize().background(atmosphereBrush()).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(Spacing.touchTarget).padding(horizontal = Spacing.sm), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            val skipDescription = stringResource(R.string.onboarding_skip_description)
            if (!last) Text(
                stringResource(R.string.onboarding_skip), style = AslText.headline, color = colors.labelSecondary,
                modifier = Modifier.clip(RoundedCornerShape(AslShapes.tile)).clickable(role = Role.Button, onClick = onContinue)
                    .semantics { contentDescription = skipDescription }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            OnboardingPage(page, pager, shapes, leftHanded, onLeftHanded)
        }
        StepDots(pager, Modifier.align(Alignment.CenterHorizontally).padding(vertical = Spacing.md))
        AslButton(
            stringResource(if (last) R.string.onboarding_continue else R.string.onboarding_next),
            { if (last) onContinue() else goTo(pager.currentPage + 1) },
            Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        )
    }
}

@Composable
private fun OnboardingPage(
    page: Int,
    pager: PagerState,
    shapes: List<CanonicalHandshape>,
    leftHanded: Boolean,
    onLeftHanded: (Boolean) -> Unit,
) {
    val colors = LocalAslColors.current
    val active = pager.currentPage == page
    val eyebrow = stringResource(when (page) { 0 -> R.string.onboarding_eyebrow_1; 1 -> R.string.onboarding_eyebrow_2; else -> R.string.onboarding_eyebrow_3 })
    val title = stringResource(when (page) { 0 -> R.string.onboarding_p1_title; 1 -> R.string.onboarding_p2_title; else -> R.string.onboarding_p3_title })
    val body = stringResource(when (page) { 0 -> R.string.onboarding_p1_body; 1 -> R.string.onboarding_p2_body; else -> R.string.onboarding_p3_body })
    val visualDescription = stringResource(when (page) { 0 -> R.string.onboarding_visual_hand; 1 -> R.string.onboarding_visual_phone; else -> R.string.onboarding_visual_privacy })
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardHeight = (maxHeight * 0.46f).coerceIn(170.dp, 340.dp)
        Column(
            Modifier.fillMaxSize().padding(horizontal = Spacing.lg)
                // The whole step drifts and fades slightly as it is swiped in or out.
                .graphicsLayer {
                    val offset = (page - pager.currentPage) - pager.currentPageOffsetFraction
                    alpha = 1f - minOf(1f, kotlin.math.abs(offset)) * 0.6f
                    translationX = offset * 36.dp.toPx()
                },
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(Modifier.height(Spacing.md))
            Box(
                Modifier.fillMaxWidth().height(cardHeight).clip(RoundedCornerShape(AslShapes.sheet))
                    .background(colors.surface.copy(alpha = 0.6f)).border(Spacing.hairline, colors.separator, RoundedCornerShape(AslShapes.sheet))
                    .semantics { contentDescription = visualDescription },
            ) {
                when (page) {
                    0 -> HandMorphVisual(shapes, Modifier.fillMaxSize())
                    1 -> PhoneSetupVisual(shapes.getOrNull(1) ?: shapes.firstOrNull(), Modifier.fillMaxSize())
                    else -> PrivacyVisual(active, Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            StaggerIn(active, 0) {
                Text(eyebrow.uppercase(), style = AslText.eyebrow, color = colors.accent)
            }
            Spacer(Modifier.height(Spacing.xs))
            StaggerIn(active, 1) {
                Text(title, style = AslText.title1, color = colors.label, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(Spacing.xs))
            StaggerIn(active, 2) {
                Text(body, style = AslText.body, color = colors.labelSecondary)
            }
            Spacer(Modifier.height(Spacing.lg))
            StaggerIn(active, 3) {
                when (page) {
                    1 -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        TipChip(stringResource(R.string.onboarding_tip_distance))
                        TipChip(stringResource(R.string.onboarding_tip_frame))
                        TipChip(stringResource(R.string.onboarding_tip_light))
                    }
                    2 -> HandChoice(leftHanded, onLeftHanded)
                    else -> Unit
                }
            }
        }
    }
}

/** Fades and lifts its content in, a beat after the one before it, each time its step comes into view. */
@Composable
private fun StaggerIn(active: Boolean, index: Int, content: @Composable () -> Unit) {
    val reduce = LocalReduceMotion.current
    val progress by animateFloatAsState(
        if (active) 1f else 0f,
        tween(if (reduce) 0 else 420, delayMillis = if (active && !reduce) 90 * index else 0), label = "stagger",
    )
    val lift = with(LocalDensity.current) { 18.dp.toPx() }
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * lift }) { content() }
}

@Composable
private fun TipChip(text: String) {
    val colors = LocalAslColors.current
    Text(
        text, style = AslText.footnote, color = colors.label, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.accent.copy(alpha = 0.14f))
            .border(Spacing.hairline, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

/** Right or Left: the same setting as Settings, asked once, in plain words, with the practice-aid note beneath. */
@Composable
private fun HandChoice(leftHanded: Boolean, onLeftHanded: (Boolean) -> Unit) {
    val colors = LocalAslColors.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(stringResource(R.string.onboarding_hand_question), style = AslText.subhead, color = colors.labelSecondary)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            HandOption(stringResource(R.string.onboarding_right_hand), !leftHanded, Modifier.weight(1f)) { onLeftHanded(false) }
            HandOption(stringResource(R.string.onboarding_left_hand_short), leftHanded, Modifier.weight(1f)) { onLeftHanded(true) }
        }
        Text(stringResource(R.string.onboarding_practice_note), style = AslText.footnote, color = colors.labelTertiary,
            modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Composable
private fun HandOption(label: String, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    val colors = LocalAslColors.current
    Box(
        modifier.heightIn(min = Spacing.touchTarget).selectionOutline(selected)
            .background(colors.surface.copy(alpha = 0.8f))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AslText.headline, color = if (selected) colors.accent else colors.label)
    }
}

/** One dot per step; the current one stretches into a pill in the accent colour. */
@Composable
private fun StepDots(pager: PagerState, modifier: Modifier = Modifier) {
    val colors = LocalAslColors.current
    val description = stringResource(R.string.onboarding_step_of, pager.currentPage + 1, STEPS)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(STEPS) { index ->
            val selected = pager.currentPage == index
            val width: Dp by animateDpAsState(if (selected) 24.dp else 8.dp, tween(220), label = "dot")
            Box(Modifier.size(width = width, height = 8.dp).clip(CircleShape)
                .background(if (selected) colors.accent else colors.labelTertiary))
        }
    }
}
