package dev.handspell.app.di

import android.content.Context
import android.content.res.AssetManager
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

    val signDetector: SignDetector = buildSignDetector(context)

    private fun buildSignDetector(context: Context): SignDetector {
        val classifier = try {
            loadClassifier(context.assets)
        } catch (failure: ClassifierAssetException) {
            return CameraSignDetector(
                context = context,
                normalizer = DefaultHandNormalizer(),
                classifier = null,
                feedbackEngine = DefaultFeedbackEngine(),
                initialFailure = failure,
            )
        }

        return CameraSignDetector(
            context = context,
            normalizer = DefaultHandNormalizer(),
            classifier = classifier,
            feedbackEngine = DefaultFeedbackEngine(),
        )
    }

    private fun loadClassifier(assets: AssetManager): LetterClassifier {
        val stage1 = KnnLetterClassifier.load(
            source = { assets.open(KnnLetterClassifier.ASSET_NAME) },
        )
        return try {
            val weights = MlpWeights.parse { assets.open(MlpWeights.ASSET_NAME) }
            MlpLetterClassifier(weights)
        } catch (_: ClassifierAssetException) {
            // Stage 2 missing or unreadable: run stage 1. The visible "stage 1" notice is a UI
            // workstream (docs/CLASSIFIER.md §4).
            stage1
        }
    }
}
