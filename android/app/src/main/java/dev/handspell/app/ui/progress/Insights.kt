package dev.handspell.app.ui.progress

import dev.handspell.app.core.model.Letter
import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import java.util.TimeZone

/** A letter's camera match rate, only for letters tried often enough to say anything. */
data class LetterRate(val letter: Letter, val matches: Int, val attempts: Int) {
    val rate: Float get() = if (attempts == 0) 0f else matches.toFloat() / attempts
}

/** Progress insights (Pro). Everything is computed from saved progress on the phone. */
data class Insights(
    val weakest: List<LetterRate>,
    val practisedThisWeek: Int,
    val averageMatchMs: Long?,
    val bestSpeedScore: Int?,
    /** One or two letters to practise next, or empty when nothing stands out. */
    val suggestion: List<Letter>,
)

const val MIN_ATTEMPTS_FOR_RATE = 2

/**
 * Weakest letters: tried at least [MIN_ATTEMPTS_FOR_RATE] times and not always matched, lowest match rate first (more
 * attempts first on a tie, then A–Z). This week: letters with an attempt in the last seven local days, today
 * included. Average time: over matches that recorded a time. Suggestion: the two weakest, topped up with letters never
 * tried (A–Z), so a new learner is pointed somewhere useful.
 */
fun insightsFor(snapshot: ProgressSnapshot?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Insights {
    val letters = snapshot?.letters?.values.orEmpty().filter { !it.letter.requiresMotion }
    val weakest = letters.filter { it.attempts >= MIN_ATTEMPTS_FOR_RATE && it.matches < it.attempts }
        .map { LetterRate(it.letter, it.matches, it.attempts) }
        .sortedWith(compareBy<LetterRate> { it.rate }.thenByDescending { it.attempts }.thenBy { it.letter.name })
        .take(3)
    val today = localPracticeDay(nowMs, zone)
    val thisWeek = letters.count { item ->
        item.lastPractisedAt?.let { localPracticeDay(it, zone) in (today - 6)..today } == true
    }
    val timed = letters.sumOf { it.timedMatches }
    val average = if (timed == 0) null else letters.sumOf { it.totalMatchMs } / timed
    val tried = letters.filter { it.attempts > 0 }.map { it.letter }.toSet()
    val untried = Letter.staticLetters.filter { it !in tried }
    val suggestion = (weakest.map { it.letter } + untried).distinct().take(2)
    return Insights(
        weakest = weakest,
        practisedThisWeek = thisWeek,
        averageMatchMs = average,
        bestSpeedScore = snapshot?.speedRuns?.maxOfOrNull { it.correct },
        suggestion = suggestion,
    )
}
