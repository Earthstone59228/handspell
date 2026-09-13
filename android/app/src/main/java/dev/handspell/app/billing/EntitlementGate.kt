package dev.handspell.app.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where a paywall was triggered from. Logged to RevenueCat as the presentation source. */
enum class PaywallSource { STORY_LESSON, SPEED_CHALLENGE, SETTINGS, PROGRESS_DETAIL }

/** Entitlement fetch lifecycle, so the UI can distinguish "not Pro" from "don't know yet". */
sealed interface EntitlementStatus {
    data object Unknown : EntitlementStatus
    data object Loading : EntitlementStatus
    data class Resolved(val isPro: Boolean) : EntitlementStatus

    /** Offline or SDK error. The app stays usable; Pro content stays locked but says why. */
    data class Unavailable(val cause: Throwable?) : EntitlementStatus
}

/**
 * The only thing the app knows about purchases: is the `pro` entitlement active, and please show the
 * paywall. Everything RevenueCat-specific lives behind this.
 *
 * Presentation is a request, not a command: the gate emits on [paywallRequests] and the composable
 * hosting the RevenueCat paywall decides how to show it, because the paywall needs a composition and
 * an Activity that a plain interface should not hold.
 */
interface EntitlementGate {
    /** Convenience projection of [status]; false while unknown, so content never leaks. */
    val isPro: StateFlow<Boolean>

    val status: StateFlow<EntitlementStatus>

    val paywallRequests: Flow<PaywallSource>

    /** Entitlement identifier configured in the RevenueCat dashboard. */
    val entitlementId: String

    fun requestPaywall(source: PaywallSource)

    /** Re-reads customer info; called on app foreground and after a purchase or restore completes. */
    suspend fun refresh()
}
