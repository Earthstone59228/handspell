package dev.handspell.app.vision.classify

import android.content.res.AssetManager
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.Letter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reconstructs a compact teaching skeleton from the first validated stage-1 exemplar per letter.
 * The first 60 fields are the normalised shape block (landmarks 1..20); the wrist is the canonical
 * origin. These poses are visual guidance only, never fed back into classification.
 */
class CanonicalHandshapeCatalog(private val assets: AssetManager) {
    private var cached: Map<Letter, CanonicalHandshape>? = null

    suspend fun handshapeFor(letter: Letter): CanonicalHandshape? = withContext(Dispatchers.IO) {
        val poses = cached ?: load().also { cached = it }
        poses[letter]
    }

    private fun load(): Map<Letter, CanonicalHandshape> = assets.open(KnnLetterClassifier.ASSET_NAME)
        .bufferedReader()
        .useLines { lines ->
            lines.drop(2)
                .mapNotNull(::parse)
                .distinctBy { it.letter }
                .associateBy { it.letter }
        }

    private fun parse(row: String): CanonicalHandshape? {
        val fields = row.split(',')
        if (fields.size != VECTOR_COLUMNS) return null
        val letter = Letter.fromNameOrNull(fields.first()) ?: return null
        if (letter.requiresMotion) return null
        val values = fields.drop(1).take(SHAPE_DIM).mapOrNull { it.toFloatOrNull() } ?: return null
        val landmarks = buildList(HandLandmarks.LANDMARK_COUNT) {
            add(Landmark3(0f, 0f, 0f))
            for (index in 0 until HandLandmarks.LANDMARK_COUNT - 1) {
                add(Landmark3(values[index * 3], values[index * 3 + 1], values[index * 3 + 2]))
            }
        }
        return CanonicalHandshape(letter, landmarks)
    }

    private companion object {
        const val SHAPE_DIM = 60
        const val VECTOR_COLUMNS = 67 // Letter plus the 66-float classifier vector.
    }
}

private inline fun <T, R> Iterable<T>.mapOrNull(transform: (T) -> R?): List<R>? {
    val result = ArrayList<R>()
    for (element in this) result += transform(element) ?: return null
    return result
}
