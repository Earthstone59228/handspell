package dev.handspell.app.ui.quest

import dev.handspell.app.progress.ProgressSnapshot

/** The small daily goals. [target] is how many; progress comes from today's completions. */
enum class Quest(val target: Int) {
    LETTERS(3),
    WORDS(2),
    SPEED_ROUND(1),
}

/**
 * Today's quest, chosen deterministically from the local day (an epoch day), so it is the same all day on every
 * screen and changes at midnight. Rotates through the three so no goal repeats two days running.
 */
fun questFor(day: Long): Quest = Quest.entries[Math.floorMod(day, Quest.entries.size.toLong()).toInt()]

data class QuestProgress(val quest: Quest, val done: Int, val complete: Boolean) {
    val fraction: Float get() = (done.toFloat() / quest.target).coerceIn(0f, 1f)
}

/** Progress on [day]'s quest from today's completions; a record from another day counts as nothing. */
fun questProgress(snapshot: ProgressSnapshot?, day: Long): QuestProgress {
    val quest = questFor(day)
    val today = snapshot?.today?.takeIf { it.day == day }
    val count = when (quest) {
        Quest.LETTERS -> today?.letters?.size ?: 0
        Quest.WORDS -> today?.words?.size ?: 0
        Quest.SPEED_ROUND -> today?.speedRounds ?: 0
    }
    val done = count.coerceAtMost(quest.target)
    return QuestProgress(quest, done, done >= quest.target)
}

/** The quest's reward shows once: when it is complete and has not been celebrated today. */
fun shouldCelebrateQuest(progress: QuestProgress, celebratedDay: Long?, day: Long): Boolean =
    progress.complete && celebratedDay != day
