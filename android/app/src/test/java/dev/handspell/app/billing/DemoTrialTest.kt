package dev.handspell.app.billing

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTrialTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun `trial runs three days from its start and then ends`() {
        val start = 1_000_000L
        assertEquals(DemoTrialState.NotStarted, demoTrialState(null, start))
        assertEquals(DemoTrialState.Active(start + 3 * day), demoTrialState(start, start))
        assertEquals(DemoTrialState.Active(start + 3 * day), demoTrialState(start, start + 3 * day - 1))
        assertEquals(DemoTrialState.Ended(start + 3 * day), demoTrialState(start, start + 3 * day))
        // A start in the future (clock moved back) is not a running trial.
        assertEquals(DemoTrialState.NotStarted, demoTrialState(start, start - 1))
    }

    @Test fun `an active trial counts as Pro and it can be started once`() {
        assertTrue(effectivePro(false, DemoTrialState.Active(10)))
        assertFalse(effectivePro(false, DemoTrialState.Ended(10)))
        assertTrue(effectivePro(true, DemoTrialState.NotStarted))
        assertTrue(canStartDemoTrial(DemoTrialState.NotStarted))
        assertFalse(canStartDemoTrial(DemoTrialState.Active(10)))
        assertFalse(canStartDemoTrial(DemoTrialState.Ended(10)))
    }

    @Test fun `store free phase is read only when it is a real positive period`() {
        assertEquals(FreeTrialTerms(7, TrialUnit.DAY), freeTrialTerms(7, "DAY"))
        assertEquals(FreeTrialTerms(1, TrialUnit.MONTH), freeTrialTerms(1, "MONTH"))
        assertNull(freeTrialTerms(0, "DAY"))
        assertNull(freeTrialTerms(3, "UNKNOWN"))
    }

    @Test fun `gate unlocks during the trial, locks at its end, and never purchases`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val inner = FakeGate()
        val started = MutableStateFlow<Long?>(null)
        val gate = TrialAwareEntitlementGate(
            inner, started, startTrial = { started.value = it },
            clock = { dispatcher.scheduler.currentTime }, scope = scope.backgroundScope,
        )
        scope.runCurrent()
        assertFalse(gate.isPro.value)
        scope.backgroundScope.launchStart(gate)
        scope.runCurrent()
        assertTrue(gate.demoTrial.value is DemoTrialState.Active)
        assertTrue(gate.isPro.value)
        assertEquals(EntitlementStatus.Resolved(true), gate.status.value)
        // A second start does nothing; the start time is kept.
        scope.backgroundScope.launchStart(gate)
        scope.runCurrent()
        assertEquals(0L, started.value)
        scope.advanceTimeBy(3 * day + 1)
        scope.runCurrent()
        assertTrue(gate.demoTrial.value is DemoTrialState.Ended)
        assertFalse(gate.isPro.value)
        assertEquals(EntitlementStatus.Resolved(false), gate.status.value)
        assertEquals(0, inner.purchases)
    }

    private fun kotlinx.coroutines.CoroutineScope.launchStart(gate: EntitlementGate) =
        launch { gate.startDemoTrial() }

    private class FakeGate : EntitlementGate {
        override val isPro = MutableStateFlow(false)
        override val status = MutableStateFlow<EntitlementStatus>(EntitlementStatus.Resolved(false))
        override val paywallRequests = MutableSharedFlow<PaywallSource>()
        override val entitlementId = "pro"
        var purchases = 0
        override fun requestPaywall(source: PaywallSource) = Unit
        override suspend fun refresh() = Unit
        override suspend fun restorePurchases() = RestoreResult.NOTHING_TO_RESTORE
        override suspend fun loadPackages(): List<PaywallPackage> = emptyList()
        override suspend fun purchase(packageId: String, activity: Activity): PurchaseResult { purchases++; return PurchaseResult.CANCELLED }
    }
}
