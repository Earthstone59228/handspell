package dev.handspell.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.MaterialTheme
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslTabBar
import dev.handspell.app.ui.components.SetupScreen
import dev.handspell.app.ui.theme.LocalAslColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.PackKind
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.billing.PaywallSource
import dev.handspell.app.billing.NoopEntitlementGate
import dev.handspell.app.BuildConfig
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.alphabet.AlphabetScreen
import dev.handspell.app.ui.alphabet.AlphabetStorage
import dev.handspell.app.ui.home.HomeScreen
import dev.handspell.app.ui.home.HomeViewModel
import dev.handspell.app.ui.home.ProPackRoute
import dev.handspell.app.ui.home.OnboardingScreen
import dev.handspell.app.ui.progress.ProgressRoute
import dev.handspell.app.ui.settings.BuildInfo
import dev.handspell.app.ui.settings.LegalScreen
import dev.handspell.app.ui.settings.PaperScreen
import dev.handspell.app.ui.settings.SettingsRoute
import dev.handspell.app.ui.settings.PaywallRoute
import androidx.compose.ui.res.stringResource
import dev.handspell.app.R
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

private const val PRACTICE_ROUTE = "practice"
private const val ALPHABET_ROUTE = "alphabet"
private const val UNAVAILABLE_ROUTE = "unavailable"
private const val REQUESTED_DRILL_ROUTE = "requested-drill"
private const val DRILL_ROUTE = "drill"
private const val SETTINGS_ROUTE = "settings"
private const val PROGRESS_ROUTE = "progress"
private const val LICENSES_ROUTE = "licenses"
private const val PRIVACY_ROUTE = "privacy"
private const val PAPER_ROUTE = "paper"
private const val MIT_LICENSE_ROUTE = "mit-license"
private const val MODEL_LICENSE_ROUTE = "model-license"
private const val CAPACITOR_CORE_LICENSE_ROUTE = "capacitor-core-license"
private const val CAPACITOR_SPLASH_LICENSE_ROUTE = "capacitor-splash-license"
private const val PAYWALL_ROUTE = "paywall"
private const val PACK_ROUTE = "pack"

@Composable
fun HandspellApp(
    signDetector: SignDetector,
    contentRepository: ContentRepository,
    canonicalHandshapeCatalog: CanonicalHandshapeCatalog,
    preferences: AppPreferencesStore,
    progressStore: ProgressStore,
    entitlementGate: EntitlementGate,
    onOpenCapture: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    LaunchedEffect(entitlementGate) {
        entitlementGate.paywallRequests.collect { navController.navigate(PAYWALL_ROUTE) }
    }
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(contentRepository, progressStore))
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val progress by progressStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val isPro by entitlementGate.isPro.collectAsStateWithLifecycle()
    fun openPack(pack: ContentPack) {
        if (isPro) navController.navigate("$PACK_ROUTE/${pack.packId}")
        else entitlementGate.requestPaywall(
            if (pack.kind == PackKind.STORY) PaywallSource.STORY_LESSON else PaywallSource.SPEED_CHALLENGE,
        )
    }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val topRoutes = listOf(PRACTICE_ROUTE, PROGRESS_ROUTE)

    // The alphabet is a WebView that draws under the status and navigation bars itself; every native screen
    // is inset here instead, so no native screen has to know about system bars.
    val isAlphabet = currentRoute == null || currentRoute == ALPHABET_ROUTE
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0), bottomBar = {
        if (currentRoute in topRoutes) AslTabBar(
            labels = topRoutes.map { route ->
                stringResource(if (route == PROGRESS_ROUTE) R.string.progress_title else R.string.practice_title)
            },
            selectedIndex = topRoutes.indexOf(currentRoute),
            onSelect = { index ->
                navController.navigate(topRoutes[index]) {
                    popUpTo(ALPHABET_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )
    }) { padding -> NavHost(
        navController = navController,
        startDestination = ALPHABET_ROUTE,
        modifier = when {
            isAlphabet -> Modifier
            currentRoute in topRoutes -> Modifier.padding(padding).padding(top = statusBarTop)
            else -> Modifier.padding(padding).padding(top = statusBarTop).navigationBarsPadding()
        },
    ) {
        composable(ALPHABET_ROUTE) {
            AlphabetScreen(
                onPractice = { letter ->
                    navController.navigate("$REQUESTED_DRILL_ROUTE/$letter")
                },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                onPaper = { navController.navigate(PAPER_ROUTE) },
                onNativePractice = { navController.navigate(PRACTICE_ROUTE) },
                cameraMatches = progress?.letters
                    ?.filterValues { it.matches > 0 }
                    ?.map { (letter, item) -> letter.name to item.matches }?.toMap().orEmpty(),
            )
        }
        composable("$REQUESTED_DRILL_ROUTE/{letter}") { entry ->
            val letter = entry.arguments?.getString("letter").orEmpty()
            when {
                homeState.isLoading -> Text(stringResource(R.string.content_loading))
                homeState.error -> Column(
                    modifier = Modifier.fillMaxSize().padding(dev.handspell.app.ui.theme.Spacing.md),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.content_unavailable_body))
                    AslButton(stringResource(R.string.retry), homeViewModel::reload, Modifier.fillMaxWidth())
                }
                else -> {
                    val drill = homeState.drills.firstOrNull { it.letter.name == letter }
                    LaunchedEffect(letter, drill?.id) {
                        navController.navigate(if (drill == null) "$UNAVAILABLE_ROUTE/$letter" else "$DRILL_ROUTE/${drill.id}") {
                            popUpTo(entry.destination.route ?: "") { inclusive = true }
                        }
                    }
                }
            }
        }
        composable("$UNAVAILABLE_ROUTE/{letter}") { entry ->
            val letter = entry.arguments?.getString("letter").orEmpty()
            val needsMotion = dev.handspell.app.core.model.Letter.fromNameOrNull(letter)?.requiresMotion == true
            SetupScreen(
                title = stringResource(if (needsMotion) R.string.letter_unavailable_motion else R.string.letter_unavailable_soon, letter),
                body = stringResource(R.string.letter_unavailable_body),
                actionLabel = stringResource(R.string.back),
                onAction = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(PRACTICE_ROUTE) {
            if (progress == null) Text(stringResource(R.string.content_loading))
            else if (progress?.onboardingCompleted == false) OnboardingScreen {
                scope.launch { progressStore.setOnboardingCompleted(true) }
            }
            else HomeScreen(
                state = homeState,
                onSelectDrill = { drill -> navController.navigate("$DRILL_ROUTE/${drill.id}") },
                onRetry = homeViewModel::reload,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onOpenPack = ::openPack,
                showProPacks = entitlementGate !is NoopEntitlementGate,
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
                    onOpenPack = ::openPack,
                    showProPacks = entitlementGate !is NoopEntitlementGate,
                )
            } else {
                LetterDrillRoute(
                    drill = drill,
                    drills = homeState.drills,
                    signDetector = signDetector,
                    canonicalHandshapeCatalog = canonicalHandshapeCatalog,
                    progressStore = progressStore,
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
            SettingsRoute(
                preferences = preferences,
                progressStore = progressStore,
                clearAlphabetData = AlphabetStorage::clear,
                entitlementGate = entitlementGate,
                classifierModelId = signDetector.classifierModelId,
                buildInfo = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                onBack = { navController.popBackStack() },
                onOpenCapture = onOpenCapture,
            )
        }
        composable(PROGRESS_ROUTE) {
            ProgressRoute(progressStore,
                onPractice = { navController.navigate(PRACTICE_ROUTE) { launchSingleTop = true } },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
            )
        }
        composable("$PACK_ROUTE/{packId}") { entry ->
            val packId = entry.arguments?.getString("packId")
            if (packId != null && isPro) ProPackRoute(
                packId, contentRepository, homeState.drills, signDetector, canonicalHandshapeCatalog,
                progressStore, onBack = { navController.popBackStack() },
            ) else LaunchedEffect(packId) { navController.popBackStack() }
        }
        composable(LICENSES_ROUTE) {
            LegalScreen(stringResource(R.string.paper_notices), "NOTICE.txt") { navController.popBackStack() }
        }
        composable(PRIVACY_ROUTE) {
            LegalScreen(stringResource(R.string.settings_privacy_notice), "PRIVACY.md") { navController.popBackStack() }
        }
        composable(PAPER_ROUTE) {
            PaperScreen(
                onBack = { navController.popBackStack() },
                onPrivacy = { navController.navigate(PRIVACY_ROUTE) },
                onLicense = { navController.navigate(MIT_LICENSE_ROUTE) },
                onNotices = { navController.navigate(LICENSES_ROUTE) },
                onModelLicense = { navController.navigate(MODEL_LICENSE_ROUTE) },
                onCapacitorCoreLicense = { navController.navigate(CAPACITOR_CORE_LICENSE_ROUTE) },
                onCapacitorSplashLicense = { navController.navigate(CAPACITOR_SPLASH_LICENSE_ROUTE) },
            )
        }
        composable(MIT_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_mit_license), "LICENSE.txt") { navController.popBackStack() }
        }
        composable(MODEL_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_model_license), "MODEL_LICENSE.txt") { navController.popBackStack() }
        }
        composable(CAPACITOR_CORE_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_capacitor_core_license), "CAPACITOR_CORE_LICENSE.txt") { navController.popBackStack() }
        }
        composable(CAPACITOR_SPLASH_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_capacitor_splash_license), "CAPACITOR_SPLASH_LICENSE.txt") { navController.popBackStack() }
        }
        composable(PAYWALL_ROUTE) {
            PaywallRoute(entitlementGate) { navController.popBackStack() }
        }
    } }
}
