package dev.handspell.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import java.io.ByteArrayInputStream

private const val ASSET_HOST = "appassets.androidplatform.net"

/**
 * A full screen in the alphabet menu's "lean your phone" style: an optional looping line-art animation, one short
 * heading, an optional line of body text and one blue button. The animation is one of the bundled
 * `*-animation.html` pages in `assets/web` (sources live in `ionic-app/src/public`), passed as [illustration]
 * (a `#hash` after the file name is forwarded to the page).
 */
@Composable
fun SetupScreen(
    title: String,
    illustration: String? = null,
    modifier: Modifier = Modifier,
    body: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalAslColors.current
    Box(modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl, vertical = Spacing.xxxl * 2),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl - 2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (illustration != null) {
                AnimatedIllustration(illustration, Modifier.fillMaxWidth(0.7f).widthIn(max = 280.dp).aspectRatio(1f))
            }
            Column(
                Modifier.widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    color = colors.label, textAlign = TextAlign.Center,
                )
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary, textAlign = TextAlign.Center)
            }
            if (actionLabel != null && onAction != null) {
                AslButton(actionLabel, onAction, Modifier.fillMaxWidth().widthIn(max = 320.dp))
            }
        }
        if (onBack != null) BackChevron(
            onBack,
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = BackChevronStart, top = BackChevronTop),
        )
    }
}

/** Transparent WebView showing a bundled animation page. Decorative: hidden from accessibility. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AnimatedIllustration(asset: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val webView = remember(context, asset) {
        WebView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    (if (request.url.scheme == "https" && request.url.host == ASSET_HOST) loader.shouldInterceptRequest(request.url) else null)
                        ?: WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

            }
            loadUrl("https://$ASSET_HOST/assets/web/$asset")
        }
    }
    AndroidView(factory = { webView }, modifier = modifier.clearAndSetSemantics {})
    DisposableEffect(webView) { onDispose { webView.destroy() } }
}
