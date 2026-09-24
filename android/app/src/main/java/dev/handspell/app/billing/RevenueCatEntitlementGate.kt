package dev.handspell.app.billing

import android.content.Context
import android.app.Activity
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.models.StoreTransaction
import dev.handspell.app.billing.BillingPeriod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class RevenueCatEntitlementGate(context: Context, apiKey: String) : EntitlementGate {
    private val purchases = if (Purchases.isConfigured) Purchases.sharedInstance else Purchases.configure(
        PurchasesConfiguration.Builder(context.applicationContext, apiKey).build(),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val requests = MutableSharedFlow<PaywallSource>(extraBufferCapacity = 1)
    private var availablePackages: Map<String, Package> = emptyMap()

    override val entitlementId = "pro"
    override val isPro = MutableStateFlow(false)
    override val status = MutableStateFlow<EntitlementStatus>(EntitlementStatus.Unknown)
    override val paywallRequests = requests

    init {
        purchases.updatedCustomerInfoListener = com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { info ->
            applyInfo(info)
        }
        scope.launch { refresh() }
    }

    override fun requestPaywall(source: PaywallSource) {
        requests.tryEmit(source)
    }

    override suspend fun refresh() {
        status.value = EntitlementStatus.Loading
        isPro.value = false
        val result = suspendCancellableCoroutine<Result<CustomerInfo>> { continuation ->
            purchases.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    if (continuation.isActive) continuation.resume(Result.success(customerInfo))
                }

                override fun onError(error: PurchasesError) {
                    if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(error.message)))
                }
            })
        }
        result.onSuccess(::applyInfo).onFailure {
            isPro.value = false
            status.value = EntitlementStatus.Unavailable(it)
        }
    }

    override suspend fun restorePurchases(): RestoreResult {
        val result = suspendCancellableCoroutine<Result<CustomerInfo>> { continuation ->
            purchases.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    if (continuation.isActive) continuation.resume(Result.success(customerInfo))
                }

                override fun onError(error: PurchasesError) {
                    if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(error.message)))
                }
            })
        }
        return result.fold(
            onSuccess = { info ->
                applyInfo(info)
                if (info.entitlements.active.containsKey(entitlementId)) RestoreResult.RESTORED
                else RestoreResult.NOTHING_TO_RESTORE
            },
            onFailure = {
                isPro.value = false
                status.value = EntitlementStatus.Unavailable(it)
                RestoreResult.FAILED
            },
        )
    }

    override suspend fun loadPackages(): List<PaywallPackage> {
        val offerings = suspendCancellableCoroutine<Result<Offerings>> { continuation ->
            purchases.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    if (continuation.isActive) continuation.resume(Result.success(offerings))
                }

                override fun onError(error: PurchasesError) {
                    if (continuation.isActive) continuation.resume(Result.failure(IllegalStateException(error.message)))
                }
            })
        }.getOrThrow()
        val packages = offerings.current?.availablePackages.orEmpty()
            .filter { it.packageType == PackageType.MONTHLY || it.packageType == PackageType.ANNUAL }
        availablePackages = packages.associateBy { it.identifier }
        return packages.map { item ->
            PaywallPackage(
                id = item.identifier,
                title = item.product.title,
                price = item.product.price.formatted,
                period = if (item.packageType == PackageType.ANNUAL) BillingPeriod.YEAR else BillingPeriod.MONTH,
                amountMicros = item.product.price.amountMicros,
                currencyCode = item.product.price.currencyCode,
            )
        }
    }

    override suspend fun purchase(packageId: String, activity: Activity): PurchaseResult {
        val item = availablePackages[packageId] ?: return PurchaseResult.FAILED
        return suspendCancellableCoroutine { continuation ->
            purchases.purchase(PurchaseParams.Builder(activity, item).build(), object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    applyInfo(customerInfo)
                    if (continuation.isActive) continuation.resume(
                        if (customerInfo.entitlements.active.containsKey(entitlementId)) PurchaseResult.PURCHASED
                        else PurchaseResult.FAILED,
                    )
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    if (continuation.isActive) continuation.resume(
                        if (userCancelled) PurchaseResult.CANCELLED else PurchaseResult.FAILED,
                    )
                }
            })
        }
    }

    private fun applyInfo(info: CustomerInfo) {
        val active = info.entitlements.active.containsKey(entitlementId)
        isPro.value = active
        status.value = EntitlementStatus.Resolved(active)
    }
}
