package dev.handspell.app.ui.settings

import dev.handspell.app.billing.BillingPeriod
import dev.handspell.app.billing.PaywallPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnualSavingTest {
    @Test fun showsOnlyRealSameCurrencySavings() {
        val monthly = PaywallPackage("month", "Monthly", "$10", BillingPeriod.MONTH, 10_000_000, "USD")
        val annual = PaywallPackage("year", "Yearly", "$90", BillingPeriod.YEAR, 90_000_000, "USD")
        assertEquals(25, annualSavingPercent(monthly, annual))
        assertNull(annualSavingPercent(monthly, annual.copy(amountMicros = 120_000_000)))
        assertNull(annualSavingPercent(monthly, annual.copy(currencyCode = "EUR")))
    }
}
