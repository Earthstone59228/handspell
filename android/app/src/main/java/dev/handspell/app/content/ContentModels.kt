package dev.handspell.app.content

import dev.handspell.app.core.model.Letter

/** Which tier a pack belongs to. Enforced by [dev.handspell.app.billing.EntitlementGate]. */
enum class Tier { FREE, PRO }

/** Shape of a pack's items. A pack holds exactly one kind. */
enum class PackKind { DRILL, STORY, SPEED }

/**
 * `assets/content/index.json` — the list of packs this build ships, so the repository never has to
 * enumerate the assets directory.
 */
data class ContentIndex(
    val schemaVersion: Int,
    val packs: List<PackRef>,
) {
    data class PackRef(val packId: String, val path: String, val tier: Tier, val kind: PackKind)
}

/**
 * One versioned JSON pack from assets. [packVersion] increments whenever content changes so
 * progress records can note which version they were earned against; [schemaVersion] gates parsing.
 */
data class ContentPack(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: Int,
    val kind: PackKind,
    val tier: Tier,
    val title: String,
    val summary: String,
    /** ISO-8601 date, used to sort and to show "added <date>" on the pack list. */
    val releasedOn: String,
    val items: List<PackItem>,
)

sealed interface PackItem {
    val id: String

    /** One letter to hold in front of the camera. */
    data class Drill(
        override val id: String,
        val letter: Letter,
        val prompt: String,
        /** Authored handshape description; see docs/QUALITY.md on sourcing. */
        val description: String,
        val hintId: String?,
    ) : PackItem

    /** One beat of a story lesson: either narration to read, or a word to fingerspell. */
    data class StoryStep(
        override val id: String,
        val narration: String?,
        val spellWord: String?,
        val letters: List<Letter>,
    ) : PackItem

    /** One timed round: spell as many prompts as possible before the clock runs out. */
    data class SpeedRound(
        override val id: String,
        val title: String,
        val durationSeconds: Int,
        val letters: List<Letter>,
        val targetCorrect: Int,
    ) : PackItem
}
