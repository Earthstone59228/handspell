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
import dev.handspell.app.ui.components.FrostedHeaderLayout
import dev.handspell.app.ui.components.captureBackdrop
import dev.handspell.app.ui.components.BackChevronStart
import dev.handspell.app.ui.components.BackChevronTop
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing

/**
 * Settings, Progress, Documents and the other grouped screens: the shared frosted top bar over a scrolling column of
 * cards. [body] is the one short line under the title (or null for none; the slot is reserved either way).
 */
@Composable
internal fun FrostedSettingsHero(
    onBack: (() -> Unit)?,
    title: String = stringResource(R.string.settings),
    body: String? = stringResource(R.string.settings_hero_body),
    modifier: Modifier = Modifier,
    /** Header icons on the title row, right-aligned (settings on the alphabet menu). */
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    FrostedHeaderLayout(title, onBack, modifier, subline = body, actions = { actions?.invoke(this) }) { layer, top ->
        Column(
            Modifier.fillMaxSize().captureBackdrop(layer).verticalScroll(rememberScrollState())
                .padding(start = Spacing.md, end = Spacing.md, top = top + Spacing.md, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            content = content,
        )
    }
}
