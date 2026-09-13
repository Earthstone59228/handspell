# RevenueCat — research notes

verified 2026-09-13

## 1. Maven Central versions & compatibility

- `com.revenuecat.purchases:purchases` — latest stable **10.21.1** (orchestrator-verified against Maven Central maven-metadata.xml on 2026-09-13; the agent draft said 10.15.1 which was stale), `purchases-ui` tracks the same line (10.21.1 confirmed) ([Maven Central](https://central.sonatype.com/artifact/com.revenuecat.purchases/purchases), [install docs](https://www.revenuecat.com/docs/getting-started/installation/android)). A `10.22.0-alpha.01` pre-release also exists; don't use alphas here. GitHub releases show newer 10.2x tags (e.g. 10.21.x, Paywalls v2 fixes) — re-check [purchases-android/releases](https://github.com/RevenueCat/purchases-android/releases) at implementation time and pin matching `purchases`/`purchases-ui` versions.
- Gradle:
  ```kotlin
  implementation("com.revenuecat.purchases:purchases:10.21.1")
  implementation("com.revenuecat.purchases:purchases-ui:10.21.1")
  ```
- minSdk: SDK ships on Play Billing Library and requires **minSdk 23** (Android 6.0) ([RevenueCat KMP codelab notes](https://revenuecat.github.io/codelabs/kmp.html)). Kotlin 2.x and current Compose BOM are supported; the [Android + Compose codelab](https://revenuecat.github.io/codelabs/android.html) is the authoritative compatibility source — walk it before finalizing versions.
- Activity `launchMode` must be `standard` or `singleTop` or purchase verification can be cancelled mid-flow ([install docs](https://www.revenuecat.com/docs/getting-started/installation/android)).
- SDK ships its own ProGuard rules — no manual keep rules needed ([install docs](https://www.revenuecat.com/docs/getting-started/installation/android)).

## 2. Test Store — exists, and it's the right fit

**Verdict: use Test Store.** It requires zero Google Play Console setup.

- Test Store is RevenueCat's built-in testing environment, "automatically provisioned with every new project," and "works immediately without platform setup" ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store), [announcement blog](https://www.revenuecat.com/blog/company/revenuecat-test-store)).
- Test purchases "behave like real purchases and subscriptions: they update CustomerInfo, trigger entitlements, and appear in your RevenueCat dashboard" ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)).
- Setup: Dashboard → **Apps and providers** → *Test configuration* → create Test Store → copy the **Test Store API key** (a distinct key type, separate from Android/iOS platform keys) ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)).
- Renewal/expiry behavior: test subscriptions auto-renew up to 5 times on accelerated cycles (weekly ≈5 min/cycle, yearly ≈1 hr/cycle), then cancel — a yearly product fully expires in ~5 hours, so trial/renewal/expiry flows are demoable in one sitting ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)).
- Physical device / restore: the docs don't explicitly confirm restore or physical-device behavior for Test Store; since it bypasses platform billing entirely it should work identically on emulator and device, but verify restore behavior empirically before recording the demo video.
- Known limitation: product identifier/duration/price are **immutable after creation** — to change them, create a new product and swap it into the offering ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)).
- Hard rule: **never** ship a build configured with the Test Store key to a real store listing ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)) — not a concern for Next Gen since no store publish happens.
- Fallback (not needed here): Play Console internal testing + license testers per [Sandbox Testing overview](https://www.revenuecat.com/docs/test-and-launch/sandbox) — requires a $25 one-time Play Console developer account and app upload, which the "zero Play Console" constraint rules out.

## 3. Dashboard setup — 15 minute path

1. Create a RevenueCat account at [revenuecat.com](https://www.revenuecat.com) → create a new **Project**.
2. In the project, go to **Apps and providers** → add a **Test Store** app (no store credentials needed) ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)). Copy the Test Store API key.
3. Go to **Product Catalog → Products** → create two test products: a monthly and an annual subscription; attach a free trial to at least one (introductory offer config lives on the product) ([Test Store docs](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)).
4. Go to **Entitlements** → create entitlement id **`pro`** → attach both products to it.
5. Go to **Offerings** → create/confirm the **`default`** offering → add both products as **packages** (e.g. `$rc_monthly`, `$rc_annual`) inside it.
6. Go to **Paywalls** → attach a paywall to the `default` offering, use the dashboard's template builder (no code) to set copy/branding, then publish it as the current paywall for that offering ([Displaying Paywalls docs](https://www.revenuecat.com/docs/tools/paywalls/displaying-paywalls)).
7. Confirm in dashboard preview that the paywall renders both packages before wiring the app.

## 4. Minimal Kotlin (current API names)

Application configure:
```kotlin
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
        )
        // no appUserID passed -> SDK generates an anonymous app user id
    }
}
```
([Quickstart docs](https://www.revenuecat.com/docs/getting-started/quickstart))

Reading entitlement `pro`:
```kotlin
Purchases.sharedInstance.getCustomerInfoWith(
    onError = { error -> /* handle */ },
    onSuccess = { customerInfo ->
        val isPro = customerInfo.entitlements["pro"]?.isActive == true
    }
)
```
CustomerInfo update listener (fires on `getCustomerInfo()`, `purchase()`, `restorePurchases()` — [Customer Info docs](https://www.revenuecat.com/docs/customers/customer-info)):
```kotlin
Purchases.sharedInstance.delegate = object : PurchasesDelegate {
    override fun onCustomerInfoUpdated(customerInfo: CustomerInfo) {
        val isPro = customerInfo.entitlements["pro"]?.isActive == true
        // update app state
    }
}
```
Presenting a paywall in Compose — current API is `PaywallDialog` (or the `Paywall` composable for manual nav) from `purchases-ui`:
```kotlin
@OptIn(ExperimentalPreviewRevenueCatUIPurchasesAPI::class)
@Composable
private fun LockedScreen() {
    YourContent()
    PaywallDialog(
        PaywallDialogOptions.Builder()
            .setRequiredEntitlementIdentifier("pro")
            .setListener(object : PaywallListener {
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {}
                override fun onRestoreCompleted(customerInfo: CustomerInfo) {}
            })
            .build()
    )
}
```
([Displaying Paywalls docs](https://www.revenuecat.com/docs/tools/paywalls/displaying-paywalls)). Note the API is still gated behind `@ExperimentalPreviewRevenueCatUIPurchasesAPI` as of this doc snapshot.

Restore purchases:
```kotlin
Purchases.sharedInstance.restorePurchases(
    onError = { error -> /* handle */ },
    onSuccess = { customerInfo -> /* check entitlements["pro"] */ }
)
```
([Restoring Purchases docs](https://www.revenuecat.com/docs/getting-started/restoring-purchases))

Content-gating pattern: gate the Composable tree on `customerInfo.entitlements["pro"]?.isActive`, driven by state updated from both the initial `getCustomerInfo()` call and the `PurchasesDelegate` listener — the `PaywallDialog`'s `setRequiredEntitlementIdentifier` auto-hides itself once that entitlement is active, so it can wrap the gated screen directly rather than needing separate boolean logic.

## 5. What Next Gen judges need to see

- Judges assess whether the entrant "thoughtfully use[s] RevenueCat to support subscriptions, in-app purchases, web purchases, ads, or another monetization flow" — visible SDK integration in the video/repo is the bar, not store-verified revenue ([Official Rules](https://revenuecat-shipaton-2026.devpost.com/rules)).
- Next Gen entrants submit a **demo video (<2 min, public YouTube/Vimeo link)** and a **public open-source repo** with a visible license file, instead of a store listing ([Official Rules](https://revenuecat-shipaton-2026.devpost.com/rules)).
- Next Gen projects are explicitly **exempt from store-download testing** and are "evaluated using the demonstration video and code repository" — no promo codes or free-trial access needs to be arranged for judges ([Official Rules](https://revenuecat-shipaton-2026.devpost.com/rules)).
- Practical implication: the demo video should visibly show the paywall triggering and a purchase/trial completing (Test Store makes this safe and fast), and the repo should make the RevenueCat SDK usage easy to find in code review.

## 6. API key placement

- Public/platform SDK keys (and the Test Store key) are safe to ship in a compiled app — "It's safe to store the public API key in your code... Public keys are obtainable by anyone who can decompile your app" ([Public API key best practices, RevenueCat Community](https://community.revenuecat.com/sdks-51/public-api-key-best-practices-152)).
- Secret keys (`sk_...`) must never be embedded client-side and are irrelevant for this SDK-only setup ([Public API key best practices](https://community.revenuecat.com/sdks-51/public-api-key-best-practices-152)).
- Recommended pattern for a public repo: keep the key out of committed source anyway — store it in `local.properties` (gitignored), read it in `build.gradle.kts` into `BuildConfig.REVENUECAT_API_KEY`, matching RevenueCat's own example of loading the Test Store key "from BuildConfig, which reads from local.properties, which keeps secrets out of version control" ([Testing Test Store engineering blog](https://www.revenuecat.com/blog/engineering/testing-test-store)).

## Recommendation

Use RevenueCat Test Store end-to-end for this hackathon — it needs no Google Play Console account, matches the Next Gen judging bar (video + repo only), and its accelerated renew/expire cycle lets the demo video show trial → renewal → expiry in one take. Pin `purchases`/`purchases-ui` to the same stable 10.x release (verify exact patch via GitHub releases at build time), gate `pro` content on `entitlements["pro"].isActive`, present the dashboard-built paywall via `PaywallDialog`, and load the Test Store key from `local.properties` → `BuildConfig` even though it's not a real secret, for repo hygiene.
