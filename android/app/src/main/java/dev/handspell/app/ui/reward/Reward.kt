package dev.handspell.app.ui.reward

import dev.handspell.app.progress.ProgressSnapshot
import dev.handspell.app.progress.localPracticeDay
import java.util.TimeZone

/** What was just completed: a letter ("A") or a word (gloss "thankyou", shown as "thank you"). */
data class RewardSubject(val kind: Kind, val id: String, val display: String) {
    enum class Kind { LETTER, WORD, QUEST }
}

/** The numbers a reward shows. [nextMilestone] is null past the last milestone. */
data class Reward(
    val subject: RewardSubject,
    val completedToday: Int,
    val streakDays: Int,
    val nextMilestone: Int?,
    /** True while the streak stands exactly on a milestone (3, 7, 14, 30 days), i.e. all of that day. */
    val milestoneReached: Boolean,
)

/** Playful but honest streak markers (item 5), shared by rewards, the streak screen and Pro mentions. */
val STREAK_MILESTONES = listOf(3, 7, 14, 30)

fun nextMilestone(streakDays: Int): Int? = STREAK_MILESTONES.firstOrNull { it > streakDays }

/**
 * The reward for [subject], computed as if the completion has already been saved, so the card shows the same numbers
 * before and after the write lands: today's set includes [subject], and the streak includes today.
 */
fun rewardFor(
    subject: RewardSubject,
    snapshot: ProgressSnapshot?,
    nowMs: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Reward {
    val today = localPracticeDay(nowMs, zone)
    val activity = snapshot?.today?.takeIf { it.day == today }
    val letters = activity?.letters.orEmpty() + if (subject.kind == RewardSubject.Kind.LETTER) setOf(subject.id) else emptySet()
    val words = activity?.words.orEmpty() + if (subject.kind == RewardSubject.Kind.WORD) setOf(subject.id) else emptySet()
    val last = snapshot?.lastPracticeDay
    val stored = snapshot?.currentStreakDays ?: 0
    val streak = when (last) {
        today -> stored.coerceAtLeast(1)
        today - 1 -> stored + 1
        else -> 1
    }
    return Reward(
        subject = subject,
        completedToday = letters.size + words.size,
        streakDays = streak,
        nextMilestone = nextMilestone(streak),
        milestoneReached = streak in STREAK_MILESTONES,
    )
}
