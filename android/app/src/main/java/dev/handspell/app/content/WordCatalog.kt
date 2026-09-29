package dev.handspell.app.content

import android.content.res.AssetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One word sign the Words menu lists. [gloss] is the model's label; [display] is what the learner reads. */
data class WordEntry(val gloss: String, val display: String, val tier: Tier)

/**
 * `assets/content/words.json`: `{"version":2,"words":[{"gloss","display","tier"}]}`. Data-driven so Pro words can be
 * added without code: a `"pro"` entry shows the neutral lock and opens the paywall on tap, as the packs do.
 */
object WordCatalog {
    const val ASSET_NAME = "content/words.json"
    const val SUPPORTED_VERSION = 2

    @Serializable
    private data class Document(val version: Int, val words: List<Item>)

    @Serializable
    private data class Item(val gloss: String, val display: String, val tier: String = "free")

    private val json = Json { ignoreUnknownKeys = true }

    /** Parsed entries, or null when the document is malformed or from a newer version. Duplicate glosses keep the first. */
    fun parse(text: String): List<WordEntry>? {
        val document = try {
            json.decodeFromString<Document>(text)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (document.version != SUPPORTED_VERSION) return null
        return document.words
            .filter { it.gloss.isNotBlank() && it.display.isNotBlank() }
            .map { WordEntry(it.gloss, it.display, if (it.tier.equals("pro", ignoreCase = true)) Tier.PRO else Tier.FREE) }
            .distinctBy { it.gloss }
    }

    /** Reads the bundled list off the main thread; a missing or broken file is an empty list, never a crash. */
    suspend fun load(assets: AssetManager): List<WordEntry> = withContext(Dispatchers.IO) {
        try {
            assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            emptyList()
        }
    }
}
