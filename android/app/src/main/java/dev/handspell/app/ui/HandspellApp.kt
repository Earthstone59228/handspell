package dev.handspell.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
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
import dev.handspell.app.ui.components.TimerMark
import dev.handspell.app.ui.menu.MenuRowCard
import dev.handspell.app.ui.speed.SpeedAccess
import dev.handspell.app.ui.speed.SpeedChallengeRoute
import dev.handspell.app.ui.speed.speedAccess
import dev.handspell.app.ui.pro.ProMention
import dev.handspell.app.ui.pro.ProMenuCard
import dev.handspell.app.ui.pro.shouldShowRewardProLine
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.saveable.rememberSaveable
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
private const val SPEED_ROUTE = "speed"
private const val WORDS_ROUTE = "words"
private const val WORD_DRILL_ROUTE = "word"
private const val PRACTICE_ROUTE = "practice"
private const val STORIES_ROUTE = "stories"
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
private const val ABOUT_ROUTE = "about"
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
    var paywallWord by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entitlementGate) {
        entitlementGate.paywallRequests.collect { source ->
            if (source != PaywallSource.WORDS) paywallWord = null
            // A request is never dropped by the tap guard, but two requests never stack two paywalls.
            if (navController.currentDestination?.route != PAYWALL_ROUTE) {
                navController.navigate(PAYWALL_ROUTE) { launchSingleTop = true }
            }
        }
    }
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(contentRepository, progressStore, entitlementGate))
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val progress by progressStore.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val isPro by entitlementGate.isPro.collectAsStateWithLifecycle()
    val leftHanded by preferences.leftHanded.collectAsStateWithLifecycle(initialValue = false)
    val entitlementStatus by entitlementGate.status.collectAsStateWithLifecycle()
    fun openPack(pack: ContentPack) {
        routePackTap(pack, isPro, entitlementGate) { navController.safeNavigate("$PACK_ROUTE/$it") }
    }
    val context = LocalContext.current
    val words by produceState<List<WordEntry>?>(null, context) { value = WordCatalog.load(context.assets) }
    val wordReferences by produceState(emptyMap<String, dev.handspell.app.content.WordReference>(), context) {
        value = dev.handspell.app.content.WordReferences.load(context.assets)
    }
    // The word model is slow to load; do it once, off the main thread, so opening Words or Speed never stalls a frame.
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { wordDetector() } }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    // Decided once per launch, when saved progress has loaded: first-run introduction, otherwise the menu.
    var gate by remember { mutableStateOf(RootGate.Loading) }
    LaunchedEffect(progress) { gate = resolveGate(gate, progress?.onboardingCompleted) }
    when (gate) {
        RootGate.Loading -> Box(Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped))
        RootGate.Onboarding -> OnboardingRoute(progressStore, preferences, canonicalHandshapeCatalog) { gate = RootGate.Menu }
        RootGate.Menu -> {

    // The alphabet is a WebView that draws under the status and navigation bars itself; every native screen
    // is inset here instead, so no native screen has to know about system bars.
    val isAlphabet = currentRoute == ALPHABET_ROUTE
    val reduceMotion = dev.handspell.app.ui.theme.LocalReduceMotion.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var reward by remember { mutableStateOf<Reward?>(null) }
    // What the reward's Continue button does after closing it: a drill match moves on to the next letter or word.
    var rewardContinue by remember { mutableStateOf<(() -> Unit)?>(null) }
    // "Not now" on a Pro mention hides every mention until the app is next started.
    var proMentionDismissed by rememberSaveable { mutableStateOf(false) }
    // Pro promotions, each dismissible for this session ("Not now").
    var menuProDismissed by rememberSaveable { mutableStateOf(false) }
    var showMenuPro by rememberSaveable { mutableStateOf(false) }
    // The unprompted popup appears once per launch, not every time the menu comes back into view.
    var menuProAutoShown by rememberSaveable { mutableStateOf(false) }
    var wordsProDismissed by rememberSaveable { mutableStateOf(false) }
    var rewardProLine by remember { mutableStateOf(false) }
    val proRewardLineDay by preferences.proRewardLineDay.collectAsStateWithLifecycle(initialValue = null)
    val proWordCount = words.orEmpty().count { it.tier == dev.handspell.app.content.Tier.PRO }
    fun showReward(subject: RewardSubject) {
        rewardContinue = null
        val now = System.currentTimeMillis()
        val today = localPracticeDay(now)
        // Decided once when the reward opens, so recording the day doesn't hide the line while it is on screen.
        rewardProLine = shouldShowRewardProLine(isPro, proMentionDismissed, proRewardLineDay, today)
        if (rewardProLine) scope.launch { preferences.setProRewardLineDay(today) }
        reward = rewardFor(subject, progress, now)
    }
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
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentWindowInsets = WindowInsets(0)) { padding -> Box(Modifier.fillMaxSize().background(dev.handspell.app.ui.theme.atmosphereBrush())) { NavHost(
        navController = navController,
        startDestination = MENU_ROUTE,
        // A short slide with a fade: the new screen glides in from the side it came from, the old one eases away.
        enterTransition = { if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(240, 50)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 9 } },
        exitTransition = { if (reduceMotion) fadeOut(tween(0)) else fadeOut(tween(140)) + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 14 } },
        popEnterTransition = { if (reduceMotion) fadeIn(tween(0)) else fadeIn(tween(240, 50)) + slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 14 } },
        popExitTransition = { if (reduceMotion) fadeOut(tween(0)) else fadeOut(tween(140)) + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 9 } },
        modifier = when {
            isAlphabet -> Modifier
            else -> Modifier.padding(padding).navigationBarsPadding()
        },
    ) {
        composable(MENU_ROUTE) {
            val menuWords = WordsMenuState(loading = false, words = words.orEmpty(), isPro = isPro).practicable
            val menuState = mainMenuState(progress, menuWords.map { it.gloss }, isPro, System.currentTimeMillis())
            MainMenuScreen(
                state = menuState,
                actions = MainMenuActions(
                    onLetters = { navController.safeNavigate(ALPHABET_ROUTE) },
                    onWords = { navController.safeNavigate(WORDS_ROUTE) },
                    onProgress = { navController.safeNavigate(PROGRESS_ROUTE) },
                    onSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
                    onSpeed = { navController.safeNavigate(SPEED_ROUTE) },
                    onPro = { showMenuPro = true },
                    onStories = { if (isPro) navController.safeNavigate(STORIES_ROUTE) else showMenuPro = true },
                ),
                belowEntries = {
                    val quest = questProgress(progress, questDay)
                    if (progress != null) QuestCard(quest) {
                        navController.safeNavigate(when (quest.quest) {
                            Quest.LETTERS -> ALPHABET_ROUTE
                            Quest.WORDS -> WORDS_ROUTE
                            Quest.SPEED_ROUND -> SPEED_ROUTE
                        })
                    }
                },
            )
            val isProNow by androidx.compose.runtime.rememberUpdatedState(isPro)
            LaunchedEffect(menuProDismissed, menuProAutoShown) {
                if (menuProDismissed || menuProAutoShown) return@LaunchedEffect
                // The plan is read again after the pause, so a Pro user whose status is still loading never sees it.
                kotlinx.coroutines.delay(1200)
                if (!isProNow) { menuProAutoShown = true; showMenuPro = true }
            }
            val trial by entitlementGate.demoTrial.collectAsStateWithLifecycle()
            dev.handspell.app.ui.components.AslSheet(
                visible = showMenuPro,
                onDismiss = { showMenuPro = false; menuProDismissed = true },
            ) {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    ProMenuCard(
                        isPro = isPro, trialLabel = dev.handspell.app.ui.pro.demoTrialTitle(trial),
                        proWordCount = proWordCount,
                        demoAvailable = trial == dev.handspell.app.billing.DemoTrialState.NotStarted,
                        dismissed = false,
                        onSeePro = {
                            showMenuPro = false; menuProDismissed = true
                            entitlementGate.requestPaywall(PaywallSource.MAIN_MENU)
                        },
                        onStartDemo = {
                            showMenuPro = false; menuProDismissed = true
                            scope.launch { entitlementGate.startDemoTrial() }
                        },
                        onDismiss = { showMenuPro = false; menuProDismissed = true },
                    )
                }
            }
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
                progressStore = progressStore, snapshot = progress, isPro = isPro, references = wordReferences,
                proMentionDismissed = proMentionDismissed, onDismissProMention = { proMentionDismissed = true },
                onSeePro = { entitlementGate.requestPaywall(PaywallSource.SPEED_LIMIT) },
                onBack = { navController.safePop() },
            )
        }
        composable(WORDS_ROUTE) {
            val list = words
            WordsMenuScreen(
                state = WordsMenuState(loading = list == null, words = list.orEmpty(),
                    records = progress?.words.orEmpty(), isPro = isPro),
                onBack = { navController.safePop() },
                onPractice = { word -> navController.safeNavigate("$WORD_DRILL_ROUTE/${word.gloss}") },
                onMarkComplete = { word, complete ->
                    if (complete) showReward(RewardSubject(RewardSubject.Kind.WORD, word.gloss, word.displayTitle))
                    scope.launch { progressStore.setWordMarkedComplete(word.gloss, complete) }
                },
                onLocked = { word -> paywallWord = word.displayTitle; entitlementGate.requestPaywall(PaywallSource.WORDS) },
                proStrip = !isPro && !wordsProDismissed && proWordCount > 0,
                onDismissProStrip = { wordsProDismissed = true },
                references = wordReferences,
                onSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
                leftHanded = leftHanded,
            )
        }
        composable("$WORD_DRILL_ROUTE/{gloss}") { entry ->
            val gloss = entry.arguments?.getString("gloss").orEmpty()
            val practicable = WordsMenuState(loading = false, words = words.orEmpty(), isPro = isPro).practicable
            val word = practicable.firstOrNull { it.gloss == gloss }
            if (word == null) Box(Modifier.fillMaxSize().background(LocalAslColors.current.backgroundGrouped))
            else WordDrillRoute(
                word = word, words = practicable, detector = wordDetector(), progressStore = progressStore,
                reference = wordReferences[word.gloss],
                onBack = { navController.safePop() },
                onMatched = { matched ->
                    showReward(RewardSubject(RewardSubject.Kind.WORD, matched.gloss, matched.display))
                    val next = practicable.getOrNull((practicable.indexOfFirst { it.gloss == matched.gloss } + 1)
                        .let { if (it >= practicable.size) 0 else it })
                    if (next != null && next.gloss != matched.gloss) rewardContinue = {
                        navController.safeNavigate("$WORD_DRILL_ROUTE/${next.gloss}") {
                            popUpTo(entry.destination.route ?: "") { inclusive = true }
                        }
                    }
                },
                onNext = { next ->
                    navController.safeNavigate("$WORD_DRILL_ROUTE/${next.gloss}") {
                        popUpTo(entry.destination.route ?: "") { inclusive = true }
                    }
                },
            )
        }
        composable(ALPHABET_ROUTE) {
            AlphabetScreen(
                onExit = { navController.safePop() },
                onMarkedComplete = { letter ->
                    showReward(RewardSubject(RewardSubject.Kind.LETTER, letter, letter))
                    scope.launch { progressStore.recordLetterMarked(letter) }
                },
                onCompletedChanged = { letters ->
                    if (progress != null && letters != progress?.alphabetCompleted) scope.launch { progressStore.setAlphabetCompleted(letters) }
                },
                onPractice = { letter ->
                    navController.safeNavigate("$REQUESTED_DRILL_ROUTE/$letter")
                },
                onSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
                onNativePractice = { navController.safeNavigate(PRACTICE_ROUTE) },
                onProgress = { navController.safeNavigate(PROGRESS_ROUTE) },
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
                homeState.isLoading -> Column(Modifier.fillMaxSize()) {
                    dev.handspell.app.ui.components.ScreenHeader("", { navController.safePop() }, compact = true)
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.content_loading), color = LocalAslColors.current.labelSecondary)
                    }
                }
                homeState.error -> Column(Modifier.fillMaxSize()) {
                    dev.handspell.app.ui.components.ScreenHeader("", { navController.safePop() }, compact = true)
                    Column(
                        modifier = Modifier.fillMaxSize().padding(dev.handspell.app.ui.theme.Spacing.md),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.content_unavailable_body))
                        AslButton(stringResource(R.string.retry), homeViewModel::reload, Modifier.fillMaxWidth())
                    }
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
            SetupScreen(
                title = stringResource(R.string.letter_unavailable_soon, letter),
                body = stringResource(R.string.letter_unavailable_body),
                actionLabel = stringResource(R.string.back),
                onAction = { navController.safePop() },
                onBack = { navController.safePop() },
            )
        }
        composable(STORIES_ROUTE) {
            HomeScreen(
                state = homeState,
                onBack = { navController.safePop() },
                onSelectDrill = { drill -> navController.safeNavigate("$DRILL_ROUTE/${drill.id}") },
                onRetry = homeViewModel::reload,
                onOpenSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
                onOpenPack = ::openPack,
                showProPacks = true,
                lettersSection = false,
            )
        }
        composable(PRACTICE_ROUTE) {
            HomeScreen(
                state = homeState,
                onBack = { navController.safePop() },
                onSelectDrill = { drill -> navController.safeNavigate("$DRILL_ROUTE/${drill.id}") },
                onRetry = homeViewModel::reload,
                onOpenSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
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
                    onSelectDrill = { selected -> navController.safeNavigate("$DRILL_ROUTE/${selected.id}") },
                    onRetry = homeViewModel::reload,
                    onOpenSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
                    onOpenPack = ::openPack,
                    showProPacks = true,
                    onBack = { navController.safePop() },
                )
            } else {
                LetterDrillRoute(
                    drill = drill,
                    drills = homeState.drills,
                    signDetector = signDetector,
                    canonicalHandshapeCatalog = canonicalHandshapeCatalog,
                    progressStore = progressStore,
                    onBack = { navController.safePop() },
                    onCompleted = { letter ->
                        showReward(RewardSubject(RewardSubject.Kind.LETTER, letter.name, letter.display))
                        val drills = homeState.drills
                        val next = drills.getOrNull((drills.indexOfFirst { it.id == drill.id } + 1).let { if (it >= drills.size) 0 else it })
                        if (next != null && next.id != drill.id) rewardContinue = {
                            navController.safeNavigate("$DRILL_ROUTE/${next.id}") {
                                popUpTo(entry.destination.route ?: "") { inclusive = true }
                            }
                        }
                    },
                    onSkip = { next ->
                        navController.safeNavigate("$DRILL_ROUTE/${next.id}") {
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
                clearAlphabetData = { AlphabetStorage.clear(context) },
                entitlementGate = entitlementGate,
                // INTERIM entry to the Story and speed packs (owner has not decided where they live).
                onOpenPacks = { navController.safeNavigate(STORIES_ROUTE) },
                classifierModelId = signDetector.classifierModelId,
                buildInfo = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                onBack = { navController.safePop() },
                onOpenCapture = onOpenCapture,
                onOpenDocuments = { navController.safeNavigate(PAPER_ROUTE) },
                onOpenAbout = { navController.safeNavigate(ABOUT_ROUTE) },
            )
        }
        composable(ABOUT_ROUTE) {
            dev.handspell.app.ui.settings.AboutScreen(
                classifierModelId = signDetector.classifierModelId,
                buildInfo = BuildInfo(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                onBack = { navController.safePop() },
                onOpenCapture = onOpenCapture,
            )
        }
        composable(PROGRESS_ROUTE) {
            ProgressRoute(progressStore,
                onBack = { navController.safePop() },
                onPractice = { navController.safePop() },
                onSettings = { navController.safeNavigate(SETTINGS_ROUTE) },
                isPro = isPro,
                onSeePro = { entitlementGate.requestPaywall(PaywallSource.PROGRESS_DETAIL) },
            )
        }
        composable("$PACK_ROUTE/{packId}") { entry ->
            val packId = entry.arguments?.getString("packId")
            if (packId != null && isPro) ProPackRoute(
                packId, contentRepository, homeState.drills, signDetector, canonicalHandshapeCatalog,
                progressStore, onBack = { navController.safePop() },
                contentLoading = homeState.isLoading, onRetryContent = homeViewModel::reload,
            ) else ProPackAccess(
                status = entitlementStatus,
                billingConfigured = entitlementGate.billingConfigured,
                onBack = { navController.safePop() },
                onPaywall = {
                    val kind = homeState.packs.firstOrNull { it.packId == packId }?.kind
                    entitlementGate.requestPaywall(if (kind == PackKind.SPEED) PaywallSource.SPEED_CHALLENGE else PaywallSource.STORY_LESSON)
                },
                onRetry = { scope.launch { runCatching { entitlementGate.refresh() } } },
            )
        }
        composable(LICENSES_ROUTE) {
            LegalScreen(stringResource(R.string.paper_notices), "NOTICE.txt") { navController.safePop() }
        }
        composable(PRIVACY_ROUTE) {
            LegalScreen(stringResource(R.string.settings_privacy_notice), "PRIVACY.md") { navController.safePop() }
        }
        composable(PAPER_ROUTE) {
            PaperScreen(
                onBack = { navController.safePop() },
                onPrivacy = { navController.safeNavigate(PRIVACY_ROUTE) },
                onLicense = { navController.safeNavigate(MIT_LICENSE_ROUTE) },
                onNotices = { navController.safeNavigate(LICENSES_ROUTE) },
                onModelLicense = { navController.safeNavigate(MODEL_LICENSE_ROUTE) },
                onCapacitorCoreLicense = { navController.safeNavigate(CAPACITOR_CORE_LICENSE_ROUTE) },
                onCapacitorSplashLicense = { navController.safeNavigate(CAPACITOR_SPLASH_LICENSE_ROUTE) },
            )
        }
        composable(MIT_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_mit_license), "LICENSE.txt") { navController.safePop() }
        }
        composable(MODEL_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_model_license), "MODEL_LICENSE.txt") { navController.safePop() }
        }
        composable(CAPACITOR_CORE_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_capacitor_core_license), "CAPACITOR_CORE_LICENSE.txt") { navController.safePop() }
        }
        composable(CAPACITOR_SPLASH_LICENSE_ROUTE) {
            LegalScreen(stringResource(R.string.paper_capacitor_splash_license), "CAPACITOR_SPLASH_LICENSE.txt") { navController.safePop() }
        }
        composable(PAYWALL_ROUTE) {
            PaywallRoute(entitlementGate, homeState.packs, onBack = { paywallWord = null; navController.safePop() },
                lockedWord = paywallWord)
        }
    }
    // Above every screen, the alphabet WebView included; a tap on Continue or the backdrop closes it.
    RewardSheet(reward, onDismiss = { reward = null; rewardContinue = null }, onContinue = {
        val go = rewardContinue
        reward = null; rewardContinue = null
        go?.invoke()
    }, proMention = { _ ->
        if (rewardProLine && !isPro && !proMentionDismissed) ProMention(
            text = if (proWordCount > 0) pluralStringResource(R.plurals.pro_reward_line, proWordCount, proWordCount)
            else stringResource(R.string.pro_reward_line_plain),
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

/** True while this entry is fully on screen; taps that arrive mid-transition are ignored instead of stacking. */
private fun NavController.isSettled(): Boolean =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

/** Navigates once per tap: ignored mid-transition or when the destination is already on top. */
private fun NavController.safeNavigate(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
    if (!isSettled() || currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true; builder() }
}

/** Pops once per tap, and never pops the menu itself (which would leave a blank, empty back stack). */
private fun NavController.safePop() {
    if (!isSettled() || previousBackStackEntry == null) return
    popBackStack()
}
