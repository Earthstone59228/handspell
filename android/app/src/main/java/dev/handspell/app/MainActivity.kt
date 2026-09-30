package dev.handspell.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.prefs.ThemeMode
import dev.handspell.app.ui.HandspellApp
import dev.handspell.app.ui.theme.HandspellTheme
import dev.handspell.app.ui.theme.resolveDark
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect

/**
 * Single activity (docs/ARCHITECTURE.md §1). `singleTop` because RevenueCat's paywall flow
 * requires it (docs/research/revenuecat.md §1).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars so screens can draw under the status bar; icon colours are set again per appearance below.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as HandspellApplication).container
        setContent {
            // Null until the saved appearance has loaded, so the app never flashes the wrong theme first.
            val loadedTheme by container.appPreferencesStore.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = loadedTheme ?: return@setContent
            val dark = resolveDark(themeMode, isSystemInDarkTheme())
            // Status and navigation bar icons follow the appearance: light icons on the dark ground, dark on light.
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
                )
            }
            HandspellTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HandspellApp(
                        signDetector = container.signDetector,
                        contentRepository = container.contentRepository,
                        canonicalHandshapeCatalog = container.canonicalHandshapeCatalog,
                        preferences = container.appPreferencesStore,
                        progressStore = container.progressStore,
                        entitlementGate = container.entitlementGate,
                        wordDetector = { container.wordDetector },
                        onOpenCapture = if (BuildConfig.DEBUG) {
                            {
                                startActivity(Intent().setClassName(this, DEBUG_CAPTURE_ACTIVITY))
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    private companion object {
        // The activity exists only in src/debug and remains non-exported. A class-name string lets
        // this release-compiled activity launch it internally without a release dependency.
        const val DEBUG_CAPTURE_ACTIVITY = "dev.handspell.app.ui.capture.CaptureActivity"
    }
}
