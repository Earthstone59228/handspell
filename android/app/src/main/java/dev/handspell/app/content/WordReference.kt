package dev.handspell.app.content

import android.content.res.AssetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An example of signing one word, as a short loop of hand skeletons over a simple head-and-shoulders outline.
 * Coordinates are in the unit square as the learner sees themselves (mirrored preview, dominant hand on the right).
 * [frames] each hold up to two hands of 42 floats (x,y for the 21 MediaPipe landmarks) or null.
 */
class WordReference(
    val fps: Int,
    /** Nose x,y, then left and right shoulder x,y. */
    val body: FloatArray,
    /** Head oval radii (x, y), centred on the nose. */
    val head: FloatArray,
    val frames: List<List<FloatArray?>>,
) {
    val frameMillis: Long get() = 1000L / fps.coerceAtLeast(1)
}

/** `assets/content/word-refs.json`, read the same way as [WordCatalog]: a bad or missing file means no references. */
object WordReferences {
    const val ASSET_NAME = "content/word-refs.json"
    const val SUPPORTED_VERSION = 1
    const val HAND_FLOATS = 42

    @Serializable
    private data class Document(val version: Int, val fps: Int, val words: Map<String, Item>)

    @Serializable
    private data class Item(val body: List<Float>, val head: List<Float>, val frames: List<List<List<Float>?>>)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * References by gloss, or null for an unreadable document. A word whose data is malformed (wrong lengths,
     * non-finite numbers, no frames, no hand at all) is left out rather than drawn wrong.
     */
    fun parse(text: String): Map<String, WordReference>? {
        val document = try {
            json.decodeFromString<Document>(text)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (document.version != SUPPORTED_VERSION || document.fps <= 0) return null
        return document.words.mapNotNull { (gloss, item) -> item.toReference(document.fps)?.let { gloss to it } }.toMap()
    }

    private fun Item.toReference(fps: Int): WordReference? {
        if (body.size != 6 || head.size != 2 || frames.isEmpty()) return null
        if (!(body + head).all { it.isFinite() }) return null
        val parsed = frames.map { frame ->
            if (frame.isEmpty() || frame.size > 2) return null
            frame.map { hand ->
                if (hand == null) null
                else if (hand.size != HAND_FLOATS || !hand.all { it.isFinite() }) return null
                else hand.toFloatArray()
            }
        }
        if (parsed.all { frame -> frame.all { it == null } }) return null
        return WordReference(fps, body.toFloatArray(), head.toFloatArray(), parsed)
    }

    suspend fun load(assets: AssetManager): Map<String, WordReference> = withContext(Dispatchers.IO) {
        try {
            assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }.orEmpty()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: java.io.IOException) {
            emptyMap()
        }
    }
}
