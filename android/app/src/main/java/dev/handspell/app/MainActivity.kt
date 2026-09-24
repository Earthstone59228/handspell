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

/**
 * Single activity (docs/ARCHITECTURE.md §1). `singleTop` because RevenueCat's paywall flow
 * requires it (docs/research/revenuecat.md §1).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-only: transparent bars with light icons, so screens can draw under the status bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val container = (application as HandspellApplication).container
        setContent {
            val themeMode by container.appPreferencesStore.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            HandspellTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HandspellApp(
                        signDetector = container.signDetector,
                        contentRepository = container.contentRepository,
                        canonicalHandshapeCatalog = container.canonicalHandshapeCatalog,
                        preferences = container.appPreferencesStore,
                        progressStore = container.progressStore,
                        entitlementGate = container.entitlementGate,
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
