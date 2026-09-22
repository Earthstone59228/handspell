package dev.handspell.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.handspell.app.ui.HandspellApp
import dev.handspell.app.ui.theme.HandspellTheme

/**
 * Single activity (docs/ARCHITECTURE.md §1). `singleTop` because RevenueCat's paywall flow
 * requires it (docs/research/revenuecat.md §1).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as HandspellApplication).container
        setContent {
            HandspellTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HandspellApp(
                        signDetector = container.signDetector,
                        contentRepository = container.contentRepository,
                        canonicalHandshapeCatalog = container.canonicalHandshapeCatalog,
                    )
                }
            }
        }
    }
}
