package dev.handspell.app.ui.settings

import androidx.compose.foundation.layout.statusBarsPadding
import dev.handspell.app.ui.components.captureBackdrop
import dev.handspell.app.ui.components.frostedBackdrop
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.handspell.app.R
import dev.handspell.app.ui.components.BackChevron
import dev.handspell.app.ui.components.BackChevronStart
import dev.handspell.app.ui.components.BackChevronTop
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/**
 * Alphabet's pinned dark frosted hero, sampling the actual rows as they scroll beneath it. Settings, Progress and
 * the first-run introduction share it, so every grouped screen has the same large title and blurred header.
 * Without [onBack] (first run) the chevron row is replaced by the same amount of space.
 */
@Composable
internal fun FrostedSettingsHero(
    onBack: (() -> Unit)?,
    title: String = stringResource(R.string.settings),
    body: String? = stringResource(R.string.settings_hero_body),
    modifier: Modifier = Modifier,
    /** Header icons on the title row, right-aligned, as on the alphabet menu (settings, paper). */
    actions: (@Composable RowScope.() -> Unit)? = null,
    /** Space above the title when there is no back chevron. */
    topSpace: androidx.compose.ui.unit.Dp = Spacing.xxxl,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalAslColors.current
    val density = LocalDensity.current
    val contentLayer = rememberGraphicsLayer()
    var heroHeight by remember { mutableIntStateOf(0) }
    val heroPadding = with(density) { heroHeight.toDp() }
    Box(modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        Column(
            Modifier.fillMaxSize().captureBackdrop(contentLayer).verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, top = heroPadding + Spacing.md, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().onSizeChanged { heroHeight = it.height }
                .clipToBounds().frostedBackdrop(contentLayer).statusBarsPadding(),
        ) {
            if (onBack != null) Row(
                Modifier.fillMaxWidth().padding(start = BackChevronStart, end = Spacing.lg, top = BackChevronTop),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackChevron(onBack)
                Text(title, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                actions?.invoke(this)
            } else {
                Spacer(Modifier.height(topSpace))
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.displaySmall,
                        modifier = Modifier.weight(1f).semantics { heading() })
                    actions?.invoke(this)
                }
            }
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium,
                color = colors.labelSecondary,
                modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm))
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
