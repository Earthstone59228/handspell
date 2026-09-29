package dev.handspell.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
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
import dev.handspell.app.content.Tier
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.billing.PaywallSource
import dev.handspell.app.BuildConfig
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.ui.drill.LetterDrillRoute
import dev.handspell.app.ui.alphabet.AlphabetScreen
import dev.handspell.app.ui.alphabet.AlphabetStorage
import dev.handspell.app.ui.home.HomeScreen
import dev.handspell.app.ui.home.HomeViewModel
import dev.handspell.app.ui.home.ProPackAccess
import dev.handspell.app.ui.home.ProPackRoute
import dev.handspell.app.ui.home.OnboardingRoute
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
import dev.handspell.app.ui.reward.Reward
import dev.handspell.app.progress.localPracticeDay
import dev.handspell.app.ui.quest.Quest
import dev.handspell.app.ui.quest.QuestCard
import dev.handspell.app.ui.quest.questFor
import dev.handspell.app.ui.quest.questProgress
import dev.handspell.app.ui.quest.questTitle
import dev.handspell.app.ui.quest.shouldCelebrateQuest
import dev.handspell.app.ui.theme.LocalDarkTheme
import dev.handspell.app.ui.theme.toggledTheme
import dev.handspell.app.ui.menu.ThemeToggle
import dev.handspell.app.ui.components.TimerMark
import dev.handspell.app.ui.menu.MenuRowCard
import dev.handspell.app.ui.speed.SpeedAccess
import dev.handspell.app.ui.speed.SpeedChallengeRoute
import dev.handspell.app.ui.speed.speedAccess
import dev.handspell.app.ui.pro.ProMention
import dev.handspell.app.ui.pro.ProMenuCard
import dev.handspell.app.ui.pro.shouldMentionPro
import androidx.compose.runtime.saveable.rememberSaveable
import dev.handspell.app.ui.streak.StreakRoute
import dev.handspell.app.ui.reward.RewardSheet
import dev.handspell.app.ui.reward.RewardSubject
import dev.handspell.app.ui.reward.rewardFor
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.handspell.app.content.WordCatalog
import dev.handspell.app.content.WordEntry
import dev.handspell.app.ui.menu.MainMenuActions
import dev.handspell.app.ui.menu.MainMenuScreen
import dev.handspell.app.ui.menu.mainMenuState
import dev.handspell.app.ui.words.WordDrillRoute
import dev.handspell.app.ui.words.WordsMenuScreen
import dev.handspell.app.ui.words.WordsMenuState
import dev.handspell.app.vision.words.WordDetector

private const val MENU_ROUTE = "menu"
private const val STREAK_ROUTE = "streak"
private const val SPEED_ROUTE = "speed"
private const val WORDS_ROUTE = "words"
private const val WORD_DRILL_ROUTE = "word"
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
    wordDetector: () -> WordDetector,
    onOpenCapture: (() -> Unit)? = null,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    LaunchedEffect(entitlementGate) {
        entitlementGate.paywallRequests.collect { navController.navigate(PAYWALL_ROUTE) }
    }
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(contentRepository, progressStore, entitlementGate))
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val progress by progressStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val isPro by entitlementGate.isPro.collectAsStateWithLifecycle()
    val leftHanded by preferences.leftHanded.collectAsStateWithLifecycle(initialValue = false)
    val entitlementStatus by entitlementGate.status.collectAsStateWithLifecycle()
    fun openPack(pack: ContentPack) {
        routePackTap(pack, isPro, entitlementGate) { navController.navigate("$PACK_ROUTE/$it") }
    }
    val context = LocalContext.current
    val words by produceState<List<WordEntry>?>(null, context) { value = WordCatalog.load(context.assets) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Decided once per launch, when saved progress has loaded: first-run introduction, otherwise the menu.
    var gate by remember { mutableStateOf(RootGate.Loading) }
    LaunchedEffect(progress) { gate = resolveGate(gate, progress?.onboardingCompleted) }
    when (gate) {
        RootGate.Loading -> Box(Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped))
        RootGate.Onboarding -> OnboardingRoute(progressStore, preferences) { gate = RootGate.Menu }
        RootGate.Menu -> {

    // The alphabet is a WebView that draws under the status and navigation bars itself; every native screen
    // is inset here instead, so no native screen has to know about system bars.
    val isAlphabet = currentRoute == ALPHABET_ROUTE
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var reward by remember { mutableStateOf<Reward?>(null) }
    // "Not now" on a Pro mention hides every mention until the app is next started.
    var proMentionDismissed by rememberSaveable { mutableStateOf(false) }
    fun showReward(subject: RewardSubject) { reward = rewardFor(subject, progress, System.currentTimeMillis()) }
    // Today's quest: celebrated once, after any reward on screen has been closed.
    val questDay = localPracticeDay(System.currentTimeMillis())
    val questText = questTitle(questFor(questDay))
    LaunchedEffect(progress, reward) {
        val snapshot = progress ?: return@LaunchedEffect
        if (reward != null) return@LaunchedEffect
        if (shouldCelebrateQuest(questProgress(snapshot, questDay), snapshot.questCelebratedDay, questDay)) {
            showReward(RewardSubject(RewardSubject.Kind.QUEST, "quest", questText))
            progressStore.setQuestCelebrated(questDay)
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { padding -> Box { NavHost(
        navController = navController,
        startDestination = MENU_ROUTE,
        modifier = when {
            isAlphabet -> Modifier
            else -> Modifier.padding(padding).padding(top = statusBarTop).navigationBarsPadding()
        },
    ) {
        composable(MENU_ROUTE) {
            val menuWords = WordsMenuState(loading = false, words = words.orEmpty(), isPro = isPro).practicable
            val menuState = mainMenuState(progress, menuWords.map { it.gloss }, isPro, System.currentTimeMillis())
            val darkNow = LocalDarkTheme.current
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = dev.handspell.app.prefs.ThemeMode.SYSTEM)
            MainMenuScreen(
                state = menuState,
                headerActions = {
                    ThemeToggle(dark = darkNow) {
                        scope.launch { preferences.setThemeMode(toggledTheme(themeMode, systemDark)) }
                    }
                },
                actions = MainMenuActions(
                    onLetters = { navController.navigate(ALPHABET_ROUTE) },
                    onWords = { navController.navigate(WORDS_ROUTE) },
                    onStreak = { navController.navigate(STREAK_ROUTE) },
                    onProgress = { navController.navigate(PROGRESS_ROUTE) },
                    onSettings = { navController.navigate(SETTINGS_ROUTE) },
                    onPaper = { navController.navigate(PAPER_ROUTE) },
                ),
                belowEntries = {
                    val quest = questProgress(progress, questDay)
                    if (progress != null) QuestCard(quest) {
                        navController.navigate(when (quest.quest) {
                            Quest.LETTERS -> ALPHABET_ROUTE
                            Quest.WORDS -> WORDS_ROUTE
                            Quest.SPEED_ROUND -> SPEED_ROUTE
                        })
                    }
                    val speed = speedAccess(isPro, progress?.freeSpeedChallengeDay, System.currentTimeMillis())
                    MenuRowCard(
                        stringResource(R.string.menu_speed_title),
                        stringResource(when (speed) {
                            SpeedAccess.Unlimited -> R.string.menu_speed_pro
                            SpeedAccess.FreeAvailable -> R.string.menu_speed_free
                            is SpeedAccess.UsedToday -> R.string.menu_speed_used
                        }),
                        onClick = { navController.navigate(SPEED_ROUTE) },
                    ) { TimerMark(active = speed.canStart) }
                    val trial by entitlementGate.demoTrial.collectAsStateWithLifecycle()
                    ProMenuCard(
                        isPro = isPro, trialLabel = dev.handspell.app.ui.pro.demoTrialTitle(trial),
                        onSeePro = { entitlementGate.requestPaywall(PaywallSource.MAIN_MENU) },
                        onOpenPacks = { navController.navigate(PRACTICE_ROUTE) },
                    )
                },
            )
        }
        composable(SPEED_ROUTE) {
            val detector = wordDetector()
            val wordStatus by detector.status.collectAsStateWithLifecycle()
            val available = (wordStatus as? dev.handspell.app.vision.DetectorStatus.Failed)?.messageId != "error_words_unavailable"
            SpeedChallengeRoute(
                drills = homeState.drills,
                words = WordsMenuState(loading = false, words = words.orEmpty(), isPro = isPro).practicable,
                wordsAvailable = available,
                signDetector = signDetector, catalog = canonicalHandshapeCatalog, wordDetector = wordDetector,
                progressStore = progressStore, snapshot = progress, isPro = isPro,
                proMentionDismissed = proMentionDismissed, onDismissProMention = { proMentionDismissed = true },
                onSeePro = { entitlementGate.requestPaywall(PaywallSource.SPEED_LIMIT) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(STREAK_ROUTE) {
            StreakRoute(progressStore, onBack = { navController.popBackStack() })
        }
        composable(WORDS_ROUTE) {
            val list = words
            WordsMenuScreen(
                state = WordsMenuState(loading = list == null, words = list.orEmpty(),
                    records = progress?.words.orEmpty(), isPro = isPro),
                onBack = { navController.popBackStack() },
                onPractice = { word -> navController.navigate("$WORD_DRILL_ROUTE/${word.gloss}") },
                onMarkComplete = { word, complete ->
                    if (complete) showReward(RewardSubject(RewardSubject.Kind.WORD, word.gloss, word.display))
                    scope.launch { progressStore.setWordMarkedComplete(word.gloss, complete) }
                },
                onLocked = { entitlementGate.requestPaywall(PaywallSource.WORDS) },
            )
        }
        composable("$WORD_DRILL_ROUTE/{gloss}") { entry ->
            val gloss = entry.arguments?.getString("gloss").orEmpty()
            val practicable = WordsMenuState(loading = false, words = words.orEmpty(), isPro = isPro).practicable
            val word = practicable.firstOrNull { it.gloss == gloss }
            if (word == null) Box(Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped))
            else WordDrillRoute(
                word = word, words = practicable, detector = wordDetector(), progressStore = progressStore,
                onBack = { navController.popBackStack() },
                onMatched = { matched -> showReward(RewardSubject(RewardSubject.Kind.WORD, matched.gloss, matched.display)) },
                onNext = { next ->
                    navController.navigate("$WORD_DRILL_ROUTE/${next.gloss}") {
                        popUpTo(entry.destination.route ?: "") { inclusive = true }
                    }
                },
            )
        }
        composable(ALPHABET_ROUTE) {
            AlphabetScreen(
                onExit = { navController.popBackStack() },
                onMarkedComplete = { letter ->
                    showReward(RewardSubject(RewardSubject.Kind.LETTER, letter, letter))
                    scope.launch { progressStore.recordLetterMarked(letter) }
                },
                onCompletedChanged = { letters ->
                    if (progress != null && letters != progress?.alphabetCompleted) scope.launch { progressStore.setAlphabetCompleted(letters) }
                },
                onPractice = { letter ->
                    navController.navigate("$REQUESTED_DRILL_ROUTE/$letter")
                },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                onPaper = { navController.navigate(PAPER_ROUTE) },
                onNativePractice = { navController.navigate(PRACTICE_ROUTE) },
                onProgress = { navController.navigate(STREAK_ROUTE) },
                progressSnapshot = progress,
                leftHanded = leftHanded,
                darkTheme = LocalDarkTheme.current,
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
            HomeScreen(
                state = homeState,
                onBack = { navController.popBackStack() },
                onSelectDrill = { drill -> navController.navigate("$DRILL_ROUTE/${drill.id}") },
                onRetry = homeViewModel::reload,
                onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                onOpenPack = ::openPack,
                showProPacks = true,
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
                    showProPacks = true,
                    onBack = { navController.popBackStack() },
                )
            } else {
                LetterDrillRoute(
                    drill = drill,
                    drills = homeState.drills,
                    signDetector = signDetector,
                    canonicalHandshapeCatalog = canonicalHandshapeCatalog,
                    progressStore = progressStore,
                    onBack = { navController.popBackStack() },
                    onCompleted = { letter -> showReward(RewardSubject(RewardSubject.Kind.LETTER, letter.name, letter.display)) },
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
                // INTERIM entry to the Story and speed packs (owner has not decided where they live).
                onOpenPacks = { navController.navigate(PRACTICE_ROUTE) },
                classifierModelId = signDetector.classifierModelId,
                buildInfo = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                onBack = { navController.popBackStack() },
                onOpenCapture = onOpenCapture,
            )
        }
        composable(PROGRESS_ROUTE) {
            ProgressRoute(progressStore,
                onBack = { navController.popBackStack() },
                onPractice = { navController.popBackStack() },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                isPro = isPro,
                onSeePro = { entitlementGate.requestPaywall(PaywallSource.PROGRESS_DETAIL) },
            )
        }
        composable("$PACK_ROUTE/{packId}") { entry ->
            val packId = entry.arguments?.getString("packId")
            if (packId != null && isPro) ProPackRoute(
                packId, contentRepository, homeState.drills, signDetector, canonicalHandshapeCatalog,
                progressStore, onBack = { navController.popBackStack() },
                contentLoading = homeState.isLoading, onRetryContent = homeViewModel::reload,
            ) else ProPackAccess(
                status = entitlementStatus,
                billingConfigured = entitlementGate.billingConfigured,
                onBack = { navController.popBackStack() },
                onPaywall = {
                    val kind = homeState.packs.firstOrNull { it.packId == packId }?.kind
                    entitlementGate.requestPaywall(if (kind == PackKind.SPEED) PaywallSource.SPEED_CHALLENGE else PaywallSource.STORY_LESSON)
                },
                onRetry = { scope.launch { runCatching { entitlementGate.refresh() } } },
            )
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
            PaywallRoute(entitlementGate, homeState.packs) { navController.popBackStack() }
        }
    }
    // Above every screen, the alphabet WebView included; a tap on Continue or the backdrop closes it.
    RewardSheet(reward, onDismiss = { reward = null }, proMention = { shownReward ->
        if (shouldMentionPro(shownReward.milestoneReached, isPro, proMentionDismissed)) ProMention(
            text = stringResource(R.string.pro_mention_milestone),
            onSeePro = { reward = null; entitlementGate.requestPaywall(PaywallSource.STREAK_MILESTONE) },
            onDismiss = { proMentionDismissed = true },
        )
    })
    } }
        }
    }
}

internal fun routePackTap(
    pack: ContentPack, isPro: Boolean, gate: EntitlementGate, navigate: (String) -> Unit,
) {
    if (pack.tier == Tier.PRO && !isPro && gate.billingConfigured) {
        gate.requestPaywall(if (pack.kind == PackKind.SPEED) PaywallSource.SPEED_CHALLENGE else PaywallSource.STORY_LESSON)
    } else navigate(pack.packId)
}
