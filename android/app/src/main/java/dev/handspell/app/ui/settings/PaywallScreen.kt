package dev.handspell.app.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import dev.handspell.app.R
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.billing.BillingPeriod
import dev.handspell.app.billing.PaywallPackage
import dev.handspell.app.billing.PurchaseResult
import dev.handspell.app.billing.RestoreResult
import dev.handspell.app.billing.DemoTrialState
import dev.handspell.app.ui.pro.DemoTrialGroup
import dev.handspell.app.ui.pro.trialLength
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.handspell.app.content.ContentPack
import dev.handspell.app.content.PackKind
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.handspell.app.ui.components.AslButton
import dev.handspell.app.ui.components.AslButtonStyle
import dev.handspell.app.ui.components.ScreenHeader
import dev.handspell.app.ui.theme.AslShapes
import dev.handspell.app.ui.theme.LocalAslColors
import dev.handspell.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun PaywallRoute(gate: EntitlementGate, packs: List<ContentPack>, onBack: () -> Unit, lockedWord: String? = null) {
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<PaywallPackage>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PurchaseResult?>(null) }
    var restore by remember { mutableStateOf<RestoreResult?>(null) }
    val demoTrial by gate.demoTrial.collectAsStateWithLifecycle()
    val isPro by gate.isPro.collectAsStateWithLifecycle()
    LaunchedEffect(gate) {
        runCatchingUnlessCancelled { gate.loadPackages() }.onSuccess {
            packages = it
            selectedId = null
        }.onFailure { error = true }
    }
    PaywallScreen(
        packs = packs.filter { it.kind != PackKind.DRILL },
        packages = packages,
        selectedId = selectedId,
        error = error,
        working = working,
        result = result,
        restore = restore,
        onSelect = { selectedId = it },
        onRetry = {
            error = false
            packages = null
            scope.launch {
                runCatchingUnlessCancelled { gate.loadPackages() }.onSuccess {
                    packages = it
                    selectedId = null
                }.onFailure { error = true }
            }
        },
        onPurchase = {
            val id = selectedId
            if (id != null && activity != null) {
                working = true
                scope.launch {
                    result = runCatchingUnlessCancelled { gate.purchase(id, activity) }.getOrDefault(PurchaseResult.FAILED)
                    working = false
                    if (result == PurchaseResult.PURCHASED) onBack()
                }
            }
        },
        onRestore = {
            working = true
            scope.launch {
                restore = runCatchingUnlessCancelled { gate.restorePurchases() }.getOrDefault(RestoreResult.FAILED)
                working = false
                if (restore == RestoreResult.RESTORED) onBack()
            }
        },
        onBack = onBack,
        demoTrial = demoTrial,
        isPro = isPro,
        onStartDemoTrial = { scope.launch { gate.startDemoTrial(); onBack() } },
        lockedWord = lockedWord,
    )
}

@Composable
private fun PaywallScreen(
    packs: List<ContentPack>,
    packages: List<PaywallPackage>?,
    selectedId: String?,
    error: Boolean,
    working: Boolean,
    result: PurchaseResult?,
    restore: RestoreResult?,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit,
    demoTrial: DemoTrialState = DemoTrialState.NotStarted,
    isPro: Boolean = false,
    onStartDemoTrial: () -> Unit = {},
    lockedWord: String? = null,
) {
    val colors = LocalAslColors.current
    Column(Modifier.fillMaxSize().background(colors.backgroundGrouped)) {
      ScreenHeader(stringResource(R.string.paywall_title), onBack)
      Column(
          Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.md),
          verticalArrangement = Arrangement.spacedBy(Spacing.md),
      ) {
        if (lockedWord != null) Text(stringResource(R.string.paywall_word_locked, lockedWord),
            style = MaterialTheme.typography.titleLarge, color = colors.label)
        when {
            error -> {
                Text(stringResource(R.string.paywall_load_failed), style = MaterialTheme.typography.bodyLarge)
                SettingsActionRow(stringResource(R.string.retry), onClick = onRetry)
            }
            packages == null -> Text(stringResource(R.string.paywall_loading), style = MaterialTheme.typography.bodyLarge)
            packages.isEmpty() -> {
                Text(stringResource(R.string.paywall_empty), style = MaterialTheme.typography.bodyLarge)
                SettingsActionRow(stringResource(R.string.retry), onClick = onRetry)
            }
            else -> {
                val monthly = packages.firstOrNull { it.period == BillingPeriod.MONTH }
                SettingsGroup {
                    Column(Modifier.selectableGroup()) {
                        packages.forEachIndexed { index, item ->
                            if (index > 0) SettingsDivider()
                            Column(
                                Modifier.fillMaxWidth().sizeIn(minHeight = Spacing.touchTarget)
                                    .selectable(selected = selectedId == item.id, role = Role.RadioButton) { onSelect(item.id) }
                                    .then(if (selectedId == item.id) Modifier.border(Spacing.stroke, colors.accent, RoundedCornerShape(AslShapes.large)) else Modifier)
                                    .padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                            ) {
                                Text(
                                    stringResource(if (selectedId == item.id) R.string.paywall_selected else R.string.paywall_option,
                                        // The store's product title can say "Premium"; the product is called Pro everywhere else.
                                        stringResource(if (item.period == BillingPeriod.YEAR) R.string.paywall_plan_year else R.string.paywall_plan_month)),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(stringResource(
                                    R.string.paywall_price, item.price,
                                    stringResource(if (item.period == BillingPeriod.YEAR) R.string.paywall_year else R.string.paywall_month),
                                ), style = MaterialTheme.typography.bodyLarge)
                                item.freeTrial?.let { terms ->
                                    Text(stringResource(R.string.trial_store_terms, trialLength(terms), item.price,
                                        stringResource(if (item.period == BillingPeriod.YEAR) R.string.paywall_year else R.string.paywall_month)),
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                                Text(stringResource(R.string.paywall_renewal), style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceSecondary)
                                val saving = annualSavingPercent(monthly, item)
                                if (saving != null) Text(stringResource(R.string.paywall_annual_saving, saving),
                                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceSecondary)
                            }
                        }
                    }
                }
                AslButton(
                    stringResource(R.string.paywall_continue), onPurchase,
                    Modifier.fillMaxWidth(), enabled = !working && selectedId != null,
                )
            }
        }
        if (working) Text(stringResource(R.string.pro_billing_working), style = MaterialTheme.typography.bodyLarge)
        if (packs.isNotEmpty()) SettingsGroup {
            Column {
                packs.forEachIndexed { index, pack ->
                    if (index > 0) SettingsDivider()
                    SettingsInfoRow(pack.title, pack.summary)
                }
            }
        } else Text(stringResource(R.string.content_loading), style = MaterialTheme.typography.bodyLarge)
        dev.handspell.app.ui.pro.ProComparison()
        // A real Pro user (not on the demo) has nothing to try.
        DemoTrialGroup(demoTrial, isRealPro = isPro && demoTrial !is DemoTrialState.Active, onStart = onStartDemoTrial)
        Text(stringResource(R.string.paywall_free), style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary)
        Text(stringResource(R.string.paywall_test_store), style = MaterialTheme.typography.bodyLarge, color = colors.labelSecondary)
        AslButton(stringResource(R.string.settings_restore), onRestore, Modifier.fillMaxWidth(),
            style = AslButtonStyle.Secondary, enabled = !working)
        Text(stringResource(R.string.settings_cancel_subscription), style = MaterialTheme.typography.bodyMedium, color = colors.labelSecondary)
        if (result == PurchaseResult.FAILED) Text(stringResource(R.string.paywall_purchase_failed), style = MaterialTheme.typography.bodyLarge)
        if (restore == RestoreResult.FAILED) Text(stringResource(R.string.settings_restore_failed), style = MaterialTheme.typography.bodyLarge)
        if (restore == RestoreResult.NOTHING_TO_RESTORE) Text(stringResource(R.string.settings_nothing_to_restore), style = MaterialTheme.typography.bodyLarge)
      }
    }
}

internal fun annualSavingPercent(monthly: PaywallPackage?, annual: PaywallPackage): Int? {
    if (monthly == null || annual.period != BillingPeriod.YEAR || monthly.amountMicros <= 0 ||
        annual.amountMicros <= 0 || monthly.currencyCode != annual.currencyCode) return null
    val yearlyMonthly = monthly.amountMicros * 12
    if (annual.amountMicros >= yearlyMonthly) return null
    return ((yearlyMonthly - annual.amountMicros) * 100.0 / yearlyMonthly).toInt()
}
