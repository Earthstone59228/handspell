package dev.handspell.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import dev.handspell.app.ui.theme.atmosphereBrush

/**
 * The one top bar of every scrolling menu: the back chevron and large title row, one reserved line beneath it, and a
 * fixed gap, all over a blurred copy of whatever scrolls beneath. Because the geometry is fixed here (and the web
 * Alphabet page uses the same numbers), no menu has a taller, shorter or differently blurred header than another.
 *
 * [content] receives the layer to capture for the blur (apply [captureBackdrop] to the scrolling container) and the
 * header's height, to use as its top padding. [overlay] draws above everything, such as a bottom sheet.
 */
@Composable
internal fun FrostedHeaderLayout(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subline: String? = null,
    sublineStyle: TextStyle? = null,
    sublineDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (layer: GraphicsLayer, topPadding: Dp) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    var headerHeight by remember { mutableIntStateOf(0) }
    val topPadding = with(LocalDensity.current) { headerHeight.toDp() }
    Box(modifier.fillMaxSize().background(atmosphereBrush())) {
        content(layer, topPadding)
        Column(
            Modifier.fillMaxWidth().onSizeChanged { headerHeight = it.height }
                .clipToBounds().frostedBackdrop(layer).statusBarsPadding(),
        ) {
            HeaderTitleRow(title, onBack, actions = actions)
            HeaderSubline(
                subline,
                if (sublineDescription != null) Modifier.semantics { contentDescription = sublineDescription } else Modifier,
                sublineStyle ?: androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(HeaderBottomSpace))
        }
        overlay()
    }
}
