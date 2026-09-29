package dev.handspell.app.billing

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Wraps the real gate so an active demo trial counts as Pro everywhere the app checks entitlement. Purchases,
 * restores and the store's own status are passed straight through; the trial never reaches the store.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrialAwareEntitlementGate(
    private val inner: EntitlementGate,
    trialStartedAt: Flow<Long?>,
    private val startTrial: suspend (Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : EntitlementGate by inner {

    override val demoTrial: StateFlow<DemoTrialState> = trialStartedAt.flatMapLatest { started ->
        flow {
            val state = demoTrialState(started, clock())
            emit(state)
            // Re-evaluate at the moment it ends, so Pro locks again without a restart.
            if (state is DemoTrialState.Active) {
                delay((state.endsAtMs - clock()).coerceAtLeast(0))
                emit(demoTrialState(started, clock()))
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, DemoTrialState.NotStarted)

    override val isPro: StateFlow<Boolean> = combine(inner.isPro, demoTrial) { real, trial -> effectivePro(real, trial) }
        .stateIn(scope, SharingStarted.Eagerly, inner.isPro.value)

    override val status: StateFlow<EntitlementStatus> = combine(inner.status, demoTrial) { real, trial ->
        if (trial is DemoTrialState.Active && !(real is EntitlementStatus.Resolved && real.isPro)) EntitlementStatus.Resolved(true)
        else real
    }.stateIn(scope, SharingStarted.Eagerly, inner.status.value)

    override suspend fun startDemoTrial() {
        if (canStartDemoTrial(demoTrial.value)) startTrial(clock())
    }

    override suspend fun purchase(packageId: String, activity: Activity): PurchaseResult = inner.purchase(packageId, activity)
}
