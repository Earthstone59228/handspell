package dev.handspell.app.di

import android.content.Context
import android.content.res.AssetManager
import dev.handspell.app.content.AssetContentRepository
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.vision.LetterClassifier
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.ClassifierAssetException
import dev.handspell.app.vision.classify.KnnLetterClassifier
import dev.handspell.app.vision.classify.MlpLetterClassifier
import dev.handspell.app.vision.classify.MlpWeights
import dev.handspell.app.vision.detector.CameraSignDetector
import dev.handspell.app.vision.feedback.DefaultFeedbackEngine
import dev.handspell.app.vision.normalize.DefaultHandNormalizer

/**
 * Hand-written dependency graph (docs/ARCHITECTURE.md §6) — no Hilt. Constructed once in
 * [dev.handspell.app.HandspellApplication].
 *
 * [signDetector] is the real [CameraSignDetector]. When the stage-1 reference set has not been
 * captured yet it is still the real detector, but constructed in the `Failed` state so the camera
 * screen shows the honest "no handshape data" message instead of a scripted demo
 * (assets/classifier/README.md). [dev.handspell.app.vision.FakeSignDetector] remains available for
 * UI work and screenshot tests, but is no longer wired here.
 */
class AppContainer(context: Context) {

    private val classifier: LetterClassifier?
    private val classifierFailure: ClassifierAssetException?

    init {
        try {
            classifier = loadClassifier(context.assets)
            classifierFailure = null
        } catch (failure: ClassifierAssetException) {
            classifier = null
            classifierFailure = failure
        }
    }

    val contentRepository: ContentRepository = AssetContentRepository(context.assets) {
        classifier?.supportedLetters.orEmpty()
    }

    val signDetector: SignDetector = CameraSignDetector(
        context = context,
        normalizer = DefaultHandNormalizer(),
        classifier = classifier,
        feedbackEngine = DefaultFeedbackEngine(),
        initialFailure = classifierFailure,
    )

    private fun loadClassifier(assets: AssetManager): LetterClassifier {
        val stage1 = KnnLetterClassifier.load(
            source = { assets.open(KnnLetterClassifier.ASSET_NAME) },
        )
        return try {
            // Named argument, not a trailing lambda: `parse`'s last parameter is `assetName:
            // String`, so a trailing lambda would bind to that parameter and leave `source` unset.
            val weights = MlpWeights.parse(source = { assets.open(MlpWeights.ASSET_NAME) })
            MlpLetterClassifier(weights)
        } catch (_: ClassifierAssetException) {
            // Stage 2 missing or unreadable: run stage 1. The visible "stage 1" notice is a UI
            // workstream (docs/CLASSIFIER.md §4).
            stage1
        }
    }
}
