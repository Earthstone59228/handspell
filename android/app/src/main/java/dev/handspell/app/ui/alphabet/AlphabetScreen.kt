package dev.handspell.app.ui.alphabet

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import dev.handspell.app.R
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import dev.handspell.app.ui.theme.Spacing
import java.io.ByteArrayInputStream

private const val ALPHABET_URL = "https://appassets.androidplatform.net/assets/web/index.html"
private const val ASSET_HOST = "appassets.androidplatform.net"

/**
 * Displays the unchanged Ionic alphabet build. Only the three existing blank destinations are
 * handed to native screens. Self-reported web completions remain distinct from camera matches.
 */
// Lint sees remember's generic T at addJavascriptInterface; AlphabetBridge's exposed methods are annotated.
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun AlphabetScreen(
    onPractice: (String) -> Unit,
    onSettings: () -> Unit,
    onPaper: () -> Unit,
    onNativePractice: () -> Unit,
    onProgress: () -> Unit,
    progressSnapshot: ProgressSnapshot? = null,
    leftHanded: Boolean = false,
    /** Camera-confirmed match counts by letter name; the web menu marks those letters complete. */
    cameraMatches: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var pageReady by remember { mutableStateOf(false) }
    val hasBundle = remember(context) {
        runCatching {
            val html = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
            val linkedAssets = Regex("(?:src|href)=\"\\./(assets/[^\"]+)\"")
                .findAll(html).map { it.groupValues[1] }.toList()
            linkedAssets.isNotEmpty() && (linkedAssets + "phone-stand-animation.html").all { path ->
                context.assets.open("web/$path").use { }
                true
            }
        }.getOrDefault(false)
    }
    if (!hasBundle) {
        AlphabetUnavailable(onNativePractice, modifier)
        return
    }
    val currentPractice = rememberUpdatedState(onPractice)
    val currentSettings = rememberUpdatedState(onSettings)
    val currentPaper = rememberUpdatedState(onPaper)
    val currentProgress = rememberUpdatedState(onProgress)
    val currentSnapshot = rememberUpdatedState(progressSnapshot)
    val currentLeftHanded = rememberUpdatedState(leftHanded)
    val currentMatches = rememberUpdatedState(cameraMatches)
    val callbacks = remember {
        AlphabetBridge(
            context.applicationContext,
            { letter -> currentPractice.value(letter) },
            { currentSettings.value() },
            { currentPaper.value() },
            { currentMatches.value },
            { currentSnapshot.value },
            { currentProgress.value() },
            { currentLeftHanded.value },
        )
    }
    val webView = remember(context) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            isVerticalScrollBarEnabled = false
            addJavascriptInterface(callbacks, "HandspellBridge")
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    if (request.url.scheme == "https" && request.url.host == ASSET_HOST)
                        loader.shouldInterceptRequest(request.url) ?: missingAsset()
                    else missingAsset()

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme != "https" || request.url.host != ASSET_HOST

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame || request.url.path?.startsWith("/assets/web/assets/") == true) {
                        isLoading = false
                        loadFailed = true
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                    if (request.isForMainFrame || request.url.path?.startsWith("/assets/web/assets/") == true) {
                        isLoading = false
                        loadFailed = true
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (url == ALPHABET_URL) {
                        isLoading = false
                        if (!loadFailed) {
                            view.evaluateJavascript(BRIDGE_SCRIPT, null)
                            pageReady = true
                        }
                    }
                }
            }
            loadUrl(ALPHABET_URL)
        }
    }
    BackHandler {
        if (loadFailed) onNativePractice()
        else webView.evaluateJavascript("window.dispatchEvent(new Event('aslNativeBack'))", null)
    }
    Box(modifier.fillMaxSize()) {
        if (loadFailed) AlphabetUnavailable(onNativePractice, Modifier.fillMaxSize())
        else {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (isLoading) Text(stringResource(R.string.content_loading), Modifier.align(Alignment.Center))
        }
    }
    // WebView does not receive Android system-bar insets as env(safe-area-inset-*), so they are handed to the page
    // as CSS variables (CSS px == dp). The page's header and scrubber use them, letting the blur reach the top edge.
    val density = LocalDensity.current
    val safeTop = WindowInsets.statusBars.getTop(density) / density.density
    val safeBottom = WindowInsets.navigationBars.getBottom(density) / density.density
    LaunchedEffect(pageReady, safeTop, safeBottom) {
        if (pageReady) webView.evaluateJavascript(
            "document.documentElement.style.setProperty('--safe-top','${safeTop}px');" +
                "document.documentElement.style.setProperty('--safe-bottom','${safeBottom}px');", null,
        )
    }
    LaunchedEffect(pageReady, cameraMatches, progressSnapshot, leftHanded) {
        if (pageReady) webView.evaluateJavascript("window.dispatchEvent(new Event('aslNativeProgress'))", null)
    }
    DisposableEffect(webView) {
        onDispose {
            webView.removeJavascriptInterface("HandspellBridge")
            webView.destroy()
        }
    }
}

private fun missingAsset(): WebResourceResponse = WebResourceResponse(
    "text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)),
)

@Composable
private fun AlphabetUnavailable(onNativePractice: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.alphabet_load_failed))
        Button(onClick = onNativePractice) { Text(stringResource(R.string.alphabet_open_native_practice)) }
    }
}

class AlphabetBridge(
    private val context: android.content.Context,
    private val onPractice: (String) -> Unit,
    private val onSettings: () -> Unit,
    private val onPaper: () -> Unit,
    private val matchesProvider: () -> Map<String, Int>,
    private val streakProvider: () -> ProgressSnapshot?,
    private val onProgress: () -> Unit,
    private val handednessProvider: () -> Boolean,
) {
    /** `{"A":2,"B":1}`: letters the camera has confirmed, with how many times. Read by the page on load/resume. */
    @JavascriptInterface
    fun cameraMatches(): String =
        matchesProvider().entries.joinToString(",", "{", "}") { (letter, count) -> "\"$letter\":$count" }

    @JavascriptInterface
    fun streak(): String = streakJson(streakProvider(), System.currentTimeMillis())

    @JavascriptInterface
    fun handedness(): String = handednessLabel(handednessProvider())

    /** Very light detent for the A-Z scrubber. */
    @JavascriptInterface
    fun tick() { Haptics.tick(context) }

    /** Slightly firmer confirmation, used when a letter is marked complete. */
    @JavascriptInterface
    fun confirm() { Haptics.confirm(context) }

    @JavascriptInterface
    fun openPractice(letter: String) {
        if (letter.length == 1 && letter[0] in 'A'..'Z') dispatch { onPractice(letter) }
    }

    @JavascriptInterface
    fun openSettings() { dispatch(onSettings) }

    @JavascriptInterface
    fun openPaper() { dispatch(onPaper) }

    @JavascriptInterface
    fun openProgress() { dispatch(onProgress) }

    private fun dispatch(action: () -> Unit) = android.os.Handler(android.os.Looper.getMainLooper()).post(action)
}

private val BRIDGE_SCRIPT = """
    (() => {
      if (window.__handspellBridgeReady) return;
      window.__handspellBridgeReady = true;
      document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button) return;
        if (button.id === 'confirm-setup') {
          const letter = document.querySelector('#practice-letter')?.textContent?.trim();
          if (!/^[A-Z]$/.test(letter || '')) return;
          event.preventDefault();
          event.stopImmediatePropagation();
          document.querySelector('#close-setup')?.click();
          HandspellBridge.openPractice(letter);
        } else if (button.id === 'open-settings' || button.id === 'open-paper' || button.id === 'open-progress') {
          event.preventDefault();
          event.stopImmediatePropagation();
          if (button.id === 'open-settings') HandspellBridge.openSettings();
          else if (button.id === 'open-paper') HandspellBridge.openPaper();
          else HandspellBridge.openProgress();
        }
      }, true);
    })();
""".trimIndent()

internal fun streakJson(snapshot: ProgressSnapshot?, nowMs: Long): String {
    val today = localPracticeDay(nowMs)
    val practisedToday = snapshot?.lastPracticeDay == today
    val current = snapshot?.let {
        if (it.lastPracticeDay == today || it.lastPracticeDay == today - 1) it.currentStreakDays else 0
    } ?: 0
    return "{\"current\":$current,\"longest\":${snapshot?.longestStreakDays ?: 0},\"today\":$practisedToday}"
}

internal fun handednessLabel(leftHanded: Boolean): String = if (leftHanded) "left" else "right"
