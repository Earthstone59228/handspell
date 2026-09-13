package dev.handspell.app.content

import kotlinx.coroutines.flow.Flow

/** Why a pack could not be shown, so the UI can say something specific instead of failing blank. */
sealed interface ContentError {
    /** The pack file is newer than this build understands. */
    data class UnsupportedSchema(val packId: String, val found: Int, val supported: Int) : ContentError

    /** Malformed or missing asset. Only reachable if the APK is corrupt or a pack was mis-authored. */
    data class Unreadable(val packId: String, val cause: Throwable?) : ContentError
}

/**
 * Reads versioned JSON packs from `assets/content/`. Everything is bundled — there is no network
 * fetch, no cache invalidation, and no partial state.
 *
 * Packs whose [ContentPack.tier] is [Tier.PRO] are still listed for free users (so they can see what
 * Pro contains) but their items are only handed out once the entitlement is active.
 */
interface ContentRepository {
    /** Emits once after load, then again only if the process re-reads assets (e.g. after a locale change). */
    val packs: Flow<List<ContentPack>>

    /** Problems found while parsing, surfaced so the UI can show an error state rather than an empty list. */
    val errors: Flow<List<ContentError>>

    suspend fun pack(packId: String): ContentPack?

    /** Drill items limited to letters the shipped classifier actually supports. */
    suspend fun availableDrills(): List<PackItem.Drill>
}
