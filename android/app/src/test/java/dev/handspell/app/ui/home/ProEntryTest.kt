package dev.handspell.app.ui.home

import android.app.Activity
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.billing.EntitlementStatus
import dev.handspell.app.billing.NoopEntitlementGate
import dev.handspell.app.billing.PaywallPackage
import dev.handspell.app.billing.PaywallSource
import dev.handspell.app.billing.PurchaseResult
import dev.handspell.app.billing.RestoreResult
import dev.handspell.app.content.ContentError
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.content.PackItem
import dev.handspell.app.content.PackKind
import dev.handspell.app.content.Tier
import dev.handspell.app.core.model.Letter
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.prefs.ThemeMode
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.progress.SpeedRunResult
import dev.handspell.app.ui.routePackTap
import dev.handspell.app.ui.settings.BuildInfo
import dev.handspell.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProEntryTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun `home state follows entitlement changes`() = runTest(dispatcher) {
        val gate = FakeGate()
        val model = HomeViewModel(FakeRepository(), FakeProgress(), gate)
        runCurrent()
        assertFalse(model.uiState.value.isPro)
        gate.isPro.value = true
        runCurrent()
        assertTrue(model.uiState.value.isPro)
    }

    @Test fun `paywall requests require a pack or settings tap`() = runTest(dispatcher) {
        val gate = FakeGate()
        val settings = SettingsViewModel(FakePreferences(), FakeProgress(), {}, gate, null, BuildInfo("test", 1))
        runCurrent()
        assertTrue(gate.requests.isEmpty())
        val pack = ContentPack(1, "story", 1, PackKind.STORY, Tier.PRO, "Story", "Summary", "2026-09-29", emptyList())
        val navigated = mutableListOf<String>()
        routePackTap(pack, false, gate, navigated::add)
        assertEquals(listOf(PaywallSource.STORY_LESSON), gate.requests)
        assertTrue(navigated.isEmpty())
        gate.isPro.value = true
        routePackTap(pack, true, gate, navigated::add)
        assertEquals(listOf("story"), navigated)
        assertEquals(1, gate.requests.size)
        settings.openPaywall()
        assertEquals(listOf(PaywallSource.STORY_LESSON, PaywallSource.SETTINGS), gate.requests)
        val unavailableRoute = mutableListOf<String>()
        routePackTap(pack, false, NoopEntitlementGate(), unavailableRoute::add)
        assertEquals(listOf("story"), unavailableRoute)
    }

    private class FakeGate : EntitlementGate {
        override val isPro = MutableStateFlow(false)
        override val status = MutableStateFlow<EntitlementStatus>(EntitlementStatus.Resolved(false))
        private val flow = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 4)
        override val paywallRequests = flow
        override val entitlementId = "pro"
        val requests = mutableListOf<PaywallSource>()
        override fun requestPaywall(source: PaywallSource) { requests += source; flow.tryEmit(source) }
        override suspend fun refresh() = Unit
        override suspend fun restorePurchases() = RestoreResult.NOTHING_TO_RESTORE
        override suspend fun loadPackages(): List<PaywallPackage> = emptyList()
        override suspend fun purchase(packageId: String, activity: Activity) = PurchaseResult.CANCELLED
    }

    private class FakeRepository : ContentRepository {
        override val packs = MutableStateFlow<List<ContentPack>>(emptyList())
        override val errors = MutableStateFlow<List<ContentError>>(emptyList())
        override suspend fun pack(packId: String): ContentPack? = null
        override suspend fun availableDrills(): List<PackItem.Drill> = emptyList()
    }

    private class FakePreferences : AppPreferencesStore {
        override val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }
    }

    private class FakeProgress : ProgressStore {
        override val snapshot = MutableStateFlow(ProgressSnapshot(1, emptyMap(), emptySet(), emptyList(), 0, 0, true))
        override suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?) = Unit
        override suspend fun recordStoryStep(stepId: String) = Unit
        override suspend fun recordSpeedRun(result: SpeedRunResult) = Unit
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun clearAll() = Unit
    }
}
