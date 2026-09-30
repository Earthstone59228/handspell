package dev.handspell.app.vision.classify

import android.content.res.AssetManager
import dev.handspell.app.core.model.CanonicalHandshape
import dev.handspell.app.core.model.Letter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reconstructs a compact guide from the first stage-1 exemplar per letter, using its shape and
 * orientation blocks. These poses are visual guidance only, never fed back into classification.
 */
class CanonicalHandshapeCatalog(private val assets: AssetManager) {
    private var cached: Map<Letter, CanonicalHandshape>? = null

    suspend fun handshapeFor(letter: Letter): CanonicalHandshape? = withContext(Dispatchers.IO) {
        val poses = cached ?: load().also { cached = it }
        val startingLetter = when (letter) { Letter.J -> Letter.I; Letter.Z -> Letter.D; else -> letter }
        poses[startingLetter]?.let { CanonicalHandshape(letter, it.landmarks) }
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
        val values = fields.drop(1).mapOrNull { it.toFloatOrNull()?.takeIf(Float::isFinite) } ?: return null
        val landmarks = ReferenceHandshapeProjection.landmarks(values)
        return CanonicalHandshape(letter, landmarks)
    }

    private companion object {
        const val VECTOR_COLUMNS = 67 // Letter plus the 66-float classifier vector.
    }
}

private inline fun <T, R> Iterable<T>.mapOrNull(transform: (T) -> R?): List<R>? {
    val result = ArrayList<R>()
    for (element in this) result += transform(element) ?: return null
    return result
}
