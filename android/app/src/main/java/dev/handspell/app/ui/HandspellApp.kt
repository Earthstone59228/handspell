package dev.handspell.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.home.HomeScreen
import dev.handspell.app.ui.home.HomeViewModel
import dev.handspell.app.vision.SignDetector

private const val PRACTICE_ROUTE = "practice"
private const val DRILL_ROUTE = "drill"

@Composable
fun HandspellApp(signDetector: SignDetector, contentRepository: ContentRepository) {
    val navController = rememberNavController()
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(contentRepository))
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = PRACTICE_ROUTE) {
        composable(PRACTICE_ROUTE) {
            HomeScreen(homeState, onSelectDrill = { drill -> navController.navigate("$DRILL_ROUTE/${drill.id}") }, onRetry = homeViewModel::reload)
        }
        composable("$DRILL_ROUTE/{drillId}") { entry ->
            val drillId = entry.arguments?.getString("drillId")
            val drill = homeState.drills.firstOrNull { it.id == drillId }
            if (drill == null) {
                HomeScreen(homeState, onSelectDrill = { selected -> navController.navigate("$DRILL_ROUTE/${selected.id}") }, onRetry = homeViewModel::reload)
            } else {
                LetterDrillRoute(
                    drill = drill,
                    drills = homeState.drills,
                    signDetector = signDetector,
                    onBack = { navController.popBackStack() },
                    onSkip = { next ->
                        navController.navigate("$DRILL_ROUTE/${next.id}") {
                            popUpTo(entry.destination.route ?: "") { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
