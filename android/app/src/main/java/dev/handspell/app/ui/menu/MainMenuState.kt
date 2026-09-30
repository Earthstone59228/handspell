package dev.handspell.app.ui.menu

import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import java.util.TimeZone

/** Everything the main menu shows, derived from saved progress by [mainMenuState]. */
data class MainMenuState(
    val loading: Boolean = true,
    val lettersComplete: Int = 0,
    val lettersTotal: Int = Letter.entries.size,
    val wordsComplete: Int = 0,
    val wordsTotal: Int = 0,
    val streakDays: Int = 0,
    val practisedToday: Boolean = false,
    val isPro: Boolean = false,
)

/**
 * The one definition of "letters complete", shared by the menu and the Progress screen so the two never disagree.
 * The alphabet page decides what shows as complete (it keeps "Undo completion" even after a camera match), so once it
 * has reported, its list is the count; before that, camera matches are the best we know.
 */
fun lettersCompleteCount(snapshot: ProgressSnapshot): Int {
    val static = Letter.entries.map { it.name }.toSet()
    val complete = snapshot.alphabetCompleted
        ?: snapshot.letters.filterValues { it.matches > 0 }.keys.map { it.name }.toSet()
    return complete.count { it in static }
}

/**
 * Letters count as complete when the alphabet page shows them complete (its own mark or a camera match it has
 * merged). Words count when matched or marked. The streak is live only if the last
 * practice day is today or yesterday, the same rule as the Progress screen.
 */
fun mainMenuState(
    snapshot: ProgressSnapshot?,
    wordGlosses: List<String>,
    isPro: Boolean,
    nowMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): MainMenuState {
    if (snapshot == null) return MainMenuState(loading = true, wordsTotal = wordGlosses.size, isPro = isPro)
    val lettersComplete = lettersCompleteCount(snapshot)
    val today = localPracticeDay(nowMs, zone)
    val live = snapshot.lastPracticeDay == today || snapshot.lastPracticeDay == today - 1
    return MainMenuState(
        loading = false,
        lettersComplete = lettersComplete,
        lettersTotal = Letter.entries.size,
        wordsComplete = wordGlosses.count { snapshot.words[it]?.complete == true },
        wordsTotal = wordGlosses.size,
        streakDays = if (live) snapshot.currentStreakDays else 0,
        practisedToday = snapshot.lastPracticeDay == today,
        isPro = isPro,
    )
}
