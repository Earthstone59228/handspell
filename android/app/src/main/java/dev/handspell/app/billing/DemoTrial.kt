package dev.handspell.app.billing

/**
 * The demo of the free-trial flow: three days of Pro on this phone, started by a tap and ending by itself. It never
 * touches the store, never becomes a purchase, and is labelled as a demo wherever it shows.
 */
sealed interface DemoTrialState {
    data object NotStarted : DemoTrialState
    data class Active(val endsAtMs: Long) : DemoTrialState
    data class Ended(val endedAtMs: Long) : DemoTrialState
}

const val DEMO_TRIAL_DAYS = 3
const val DEMO_TRIAL_MS = DEMO_TRIAL_DAYS * 24L * 60 * 60 * 1000

/** Pure function of when it started and the clock. A start in the future (clock changed) counts as not started. */
fun demoTrialState(startedAtMs: Long?, nowMs: Long): DemoTrialState = when {
    startedAtMs == null || startedAtMs > nowMs -> DemoTrialState.NotStarted
    nowMs < startedAtMs + DEMO_TRIAL_MS -> DemoTrialState.Active(startedAtMs + DEMO_TRIAL_MS)
    else -> DemoTrialState.Ended(startedAtMs + DEMO_TRIAL_MS)
}

/** The entitlement gate's rule: a real Pro entitlement or an active demo trial unlocks Pro. */
fun effectivePro(realPro: Boolean, trial: DemoTrialState): Boolean = realPro || trial is DemoTrialState.Active

/** One demo per install: offered only before it has ever started. */
fun canStartDemoTrial(trial: DemoTrialState): Boolean = trial == DemoTrialState.NotStarted

/** A store free-trial phase on a package: [length] of [unit], then the package's normal price. */
data class FreeTrialTerms(val length: Int, val unit: TrialUnit)

enum class TrialUnit { DAY, WEEK, MONTH, YEAR }
