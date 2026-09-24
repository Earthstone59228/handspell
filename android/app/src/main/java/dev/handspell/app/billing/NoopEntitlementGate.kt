package dev.handspell.app.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Honest offline state until a Test Store key and a purchase flow are configured. */
class NoopEntitlementGate : EntitlementGate {
    override val isPro = MutableStateFlow(false)
    override val status = MutableStateFlow<EntitlementStatus>(EntitlementStatus.Unavailable(null))
    override val paywallRequests: Flow<PaywallSource> = emptyFlow()
    override val entitlementId: String = "pro"

    override fun requestPaywall(source: PaywallSource) = Unit
    override suspend fun refresh() = Unit
    override suspend fun restorePurchases() = RestoreResult.FAILED
    override suspend fun loadPackages(): List<PaywallPackage> = emptyList()
    override suspend fun purchase(packageId: String, activity: Activity) = PurchaseResult.FAILED
}
