package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.Classification
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.vision.LetterClassifier
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe bridge between the calibration store and CameraX's analyser thread.
 *
 * A save constructs a complete immutable [KnnLetterClassifier] before this class swaps one atomic
 * reference. A frame therefore sees either the old reference set or the complete new one, never a
 * partially appended FloatArray. This is deliberately a stage-1 component: callers must choose
 * explicitly whether a user's calibration should take precedence over a shipped MLP.
 */
class ReloadableKnnLetterClassifier(
    private val bundled: KnnLetterClassifier,
) : LetterClassifier {
    private val active = AtomicReference(bundled)

    override val modelId: String get() = active.get().modelId
    override val specVersion: Int get() = bundled.specVersion
    override val supportedLetters: List<Letter> get() = bundled.supportedLetters
    val hasPersonalExemplars: Boolean get() = active.get() !== bundled

    override fun classify(hand: NormalizedHand, timestampMs: Long): Classification =
        active.get().classify(hand, timestampMs)

    /** Replaces, rather than accumulates, the personal portion so delete/reset takes effect at once. */
    fun replacePersonalExemplars(exemplarsByLetter: Map<Letter, List<NormalizedHand>>) {
        active.set(bundled.withPersonalExemplars(exemplarsByLetter))
    }
}
