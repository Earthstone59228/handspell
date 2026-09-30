package dev.handspell.app.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.ui.components.BackChevron
import dev.handspell.app.ui.components.BackChevronStart
import dev.handspell.app.ui.components.BackChevronTop
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/**
 * The shared pinned, frosted header block for every native screen: [header] is pinned over [content] and the content
 * that scrolls beneath it is really blurred (API 31+; a solid ground below), like the Letters page's `.top-area`.
 * [content] receives the header's measured height so a list can pad its first item below it. Works with any scrolling
 * content (a Column with verticalScroll or a LazyColumn).
 */
@Composable
internal fun FrostedHeaderScaffold(
    header: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (headerHeight: androidx.compose.ui.unit.Dp) -> Unit,
) {
    val colors = LocalAslColors.current
    val density = LocalDensity.current
    val contentLayer = rememberGraphicsLayer()
    val backdropLayer = rememberGraphicsLayer()
    var heroHeight by remember { mutableIntStateOf(0) }
    val heroPadding = with(density) { heroHeight.toDp() }
    val blurRadius = with(density) { Spacing.lg.toPx() }
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    backdropLayer.renderEffect = if (supportsBlur) BlurEffect(blurRadius, blurRadius, TileMode.Clamp) else null
    Box(modifier.fillMaxSize().background(colors.backgroundGrouped)) {
        Box(Modifier.fillMaxSize().drawWithContent {
            contentLayer.record { this@drawWithContent.drawContent() }
            drawLayer(contentLayer)
        }) { content(heroPadding) }
        Column(
            Modifier.fillMaxWidth().onSizeChanged { heroHeight = it.height }.clipToBounds().drawWithContent {
                // The sampled list is transparent between rows. Cover its live, sharp copy before
                // drawing the blurred sample, or the translucent scrim reveals sharp text below.
                drawRect(colors.backgroundGrouped)
                if (supportsBlur) {
                    backdropLayer.record { drawLayer(contentLayer) }
                    drawLayer(backdropLayer)
                    drawRect(colors.backgroundGrouped.copy(alpha = 0.80f))
                }
                // Older Android versions keep the solid grouped-background header.
                drawContent()
            },
            content = header,
        )
    }
}

/**
 * Title row of the shared header, as the Letters page draws it: back chevron, bold title and right-aligned icons on
 * one row, then an optional [below] line (the counter, or a description). Without [onBack] the title sits at the
 * same left margin the chevron's title would.
 */
@Composable
internal fun ColumnScope.FrostedHeaderContent(
    onBack: (() -> Unit)?,
    title: String,
    body: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = LocalAslColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = if (onBack != null) BackChevronStart else Spacing.lg, end = Spacing.sm, top = BackChevronTop),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) BackChevron(onBack)
        Text(title, style = MaterialTheme.typography.displaySmall, color = colors.label,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.xxs)
                .padding(start = if (onBack != null) Spacing.xxs else 0.dp).semantics { heading() })
        actions?.invoke(this)
    }
    if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary,
        modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xxs))
    below?.invoke(this)
    Spacer(Modifier.height(Spacing.md))
}

/**
 * The grouped-screen form of the shared header: [FrostedHeaderScaffold] over a vertically scrolling column. Settings,
 * Progress, Speed, Paywall, Documents and the first-run introduction use it.
 */
@Composable
internal fun FrostedSettingsHero(
    onBack: (() -> Unit)?,
    title: String = stringResource(R.string.settings),
    body: String? = stringResource(R.string.settings_hero_body),
    modifier: Modifier = Modifier,
    /** Header icons on the title row, right-aligned, as on the alphabet menu (settings, paper). */
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FrostedHeaderScaffold(
        header = { FrostedHeaderContent(onBack, title, body, actions) },
        modifier = modifier,
    ) { heroPadding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = Spacing.lg, end = Spacing.lg, top = heroPadding + Spacing.xs, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            content = content,
        )
    }
}
