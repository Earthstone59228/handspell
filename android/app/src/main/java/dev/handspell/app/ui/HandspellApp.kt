package dev.handspell.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.home.HomeScreen
import dev.handspell.app.ui.home.HomeViewModel
import dev.handspell.app.ui.settings.SettingsScreen
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog

private const val PRACTICE_ROUTE = "practice"
private const val DRILL_ROUTE = "drill"
private const val SETTINGS_ROUTE = "settings"

@Composable
fun HandspellApp(
    signDetector: SignDetector,
    contentRepository: ContentRepository,
    canonicalHandshapeCatalog: CanonicalHandshapeCatalog,
    onOpenCapture: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(contentRepository))
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = PRACTICE_ROUTE,
        modifier = androidx.compose.ui.Modifier.safeDrawingPadding(),
    ) {
        composable(PRACTICE_ROUTE) {
            HomeScreen(
                state = homeState,
                onSelectDrill = { drill -> navController.navigate("$DRILL_ROUTE/${drill.id}") },
                onRetry = homeViewModel::reload,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        composable("$DRILL_ROUTE/{drillId}") { entry ->
            val drillId = entry.arguments?.getString("drillId")
            val drill = homeState.drills.firstOrNull { it.id == drillId }
            if (drill == null) {
                HomeScreen(
                    state = homeState,
                    onSelectDrill = { selected -> navController.navigate("$DRILL_ROUTE/${selected.id}") },
                    onRetry = homeViewModel::reload,
                    onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                )
            } else {
                LetterDrillRoute(
                    drill = drill,
                    drills = homeState.drills,
                    signDetector = signDetector,
                    canonicalHandshapeCatalog = canonicalHandshapeCatalog,
                    onBack = { navController.popBackStack() },
                    onSkip = { next ->
                        navController.navigate("$DRILL_ROUTE/${next.id}") {
                            popUpTo(entry.destination.route ?: "") { inclusive = true }
                        }
                    },
                )
            }
        }
        composable(SETTINGS_ROUTE) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCapture = onOpenCapture,
            )
        }
    }
}
