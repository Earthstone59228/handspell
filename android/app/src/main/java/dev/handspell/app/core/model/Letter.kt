package dev.handspell.app.core.model

/**
 * The 26 letters of the ASL manual alphabet.
 *
 * J and Z are produced with motion rather than a held handshape, so they cannot be recognised by a
 * single-frame handshape classifier. They exist in this enum so content authors can reference them,
 * but they are never drilled or scored — see [staticLetters].
 */
enum class Letter(val requiresMotion: Boolean = false) {
    A, B, C, D, E, F, G, H, I,
    J(requiresMotion = true),
    K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y,
    Z(requiresMotion = true);

    /** Single uppercase character, for UI prompts and pack files. */
    val display: String get() = name

    companion object {
        /** The 24 letters this app can classify. Index in this list is the classifier's class index. */
        val staticLetters: List<Letter> = entries.filterNot { it.requiresMotion }

        /** Case-insensitive lookup over all 26 letters; null if [name] is not a letter name. */
        fun fromNameOrNull(name: String): Letter? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}
