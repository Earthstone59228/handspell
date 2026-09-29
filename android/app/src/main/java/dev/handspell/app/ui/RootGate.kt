package dev.handspell.app.ui

/** What the app root shows: nothing yet, the first-run introduction, or the Alphabet menu. */
enum class RootGate { Loading, Onboarding, Menu }

/** [onboardingCompleted] is null until the saved progress has loaded. */
fun rootGate(onboardingCompleted: Boolean?): RootGate = when (onboardingCompleted) {
    null -> RootGate.Loading
    false -> RootGate.Onboarding
    true -> RootGate.Menu
}

/**
 * The gate is decided once per launch. Once it has left Loading it stays put, so deleting practice data
 * from Settings (which clears the onboarding flag too) does not pull the user out of the menu.
 */
fun resolveGate(current: RootGate, onboardingCompleted: Boolean?): RootGate =
    if (current == RootGate.Loading) rootGate(onboardingCompleted) else current
