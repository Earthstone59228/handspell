package dev.handspell.app.di

import android.content.Context
import android.content.res.AssetManager
import dev.handspell.app.billing.EntitlementGate
import dev.handspell.app.billing.NoopEntitlementGate
import dev.handspell.app.billing.RevenueCatEntitlementGate
import dev.handspell.app.billing.TrialAwareEntitlementGate
import dev.handspell.app.BuildConfig
import dev.handspell.app.content.AssetContentRepository
import dev.handspell.app.content.ContentRepository
import dev.handspell.app.prefs.AppPreferencesStore
import dev.handspell.app.prefs.DataStoreAppPreferencesStore
import dev.handspell.app.progress.DataStoreProgressStore
import dev.handspell.app.progress.ProgressStore
import dev.handspell.app.vision.LetterClassifier
import dev.handspell.app.vision.SignDetector
import dev.handspell.app.vision.classify.ClassifierAssetException
import dev.handspell.app.vision.classify.CanonicalHandshapeCatalog
import dev.handspell.app.vision.classify.KnnLetterClassifier
import dev.handspell.app.vision.classify.MlpLetterClassifier
import dev.handspell.app.vision.classify.MlpWeights
import dev.handspell.app.vision.detector.CameraSignDetector
import dev.handspell.app.vision.feedback.DefaultFeedbackEngine
import dev.handspell.app.vision.normalize.DefaultHandNormalizer
import dev.handspell.app.vision.words.CameraWordDetector
import dev.handspell.app.vision.words.WordClassifier
import dev.handspell.app.vision.words.WordDetector

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

    val appPreferencesStore: AppPreferencesStore = DataStoreAppPreferencesStore(context)
    val progressStore: ProgressStore = DataStoreProgressStore(context)
    val entitlementGate: EntitlementGate = TrialAwareEntitlementGate(
        inner = if (BuildConfig.REVENUECAT_API_KEY.isBlank()) {
            NoopEntitlementGate()
        } else {
            RevenueCatEntitlementGate(context, BuildConfig.REVENUECAT_API_KEY)
        },
        trialStartedAt = appPreferencesStore.demoTrialStartedAt,
        startTrial = appPreferencesStore::setDemoTrialStartedAt,
    )

    private val classifierLoad = try {
        ClassifierLoad(classifier = loadClassifier(context.assets), failure = null)
    } catch (failure: ClassifierAssetException) {
        ClassifierLoad(classifier = null, failure = failure)
    }
    private val classifier get() = classifierLoad.classifier
    private val classifierFailure get() = classifierLoad.failure

    val contentRepository: ContentRepository = AssetContentRepository(context.assets) {
        classifier?.supportedLetters.orEmpty()
    }

    val canonicalHandshapeCatalog = CanonicalHandshapeCatalog(context.assets)

    val signDetector: SignDetector = CameraSignDetector(
        context = context,
        normalizer = DefaultHandNormalizer(),
        classifier = classifier,
        feedbackEngine = DefaultFeedbackEngine(),
        initialFailure = classifierFailure,
    )

    /**
     * Word signs. Built on first use so the letter menu never pays for it. A missing or unreadable model gives a null
     * classifier, which puts the detector in its Failed state; the word drill then says word signs aren't available
     * in this build instead of crashing.
     */
    val wordDetector: WordDetector by lazy {
        val free = loadWordModel(context.assets, WordClassifier.ASSET_NAME)
        val pro = loadWordModel(context.assets, WordClassifier.PRO_ASSET_NAME)
        CameraWordDetector(context.applicationContext, free, extraClassifiers = listOfNotNull(pro))
    }

    /** A word model, or null when its asset is missing or unreadable (never a crash); the reason is logged. */
    private fun loadWordModel(assets: AssetManager, name: String): WordClassifier? = try {
        WordClassifier.load({ assets.open(name) }, name)
    } catch (error: ClassifierAssetException) {
        android.util.Log.w(TAG, "word model $name not loaded", error)
        null
    } catch (error: IllegalArgumentException) {
        android.util.Log.w(TAG, "word model $name not loaded", error)
        null
    } catch (error: IllegalStateException) {
        android.util.Log.w(TAG, "word model $name not loaded", error)
        null
    } catch (error: java.io.IOException) {
        android.util.Log.w(TAG, "word model $name not loaded", error)
        null
    }

    private fun loadClassifier(assets: AssetManager): LetterClassifier {
        return try {
            // Named argument, not a trailing lambda: `parse`'s last parameter is `assetName:
            // String`, so a trailing lambda would bind to that parameter and leave `source` unset.
            val weights = MlpWeights.parse(source = { assets.open(MlpWeights.ASSET_NAME) })
            MlpLetterClassifier(weights)
        } catch (_: ClassifierAssetException) {
            // Stage 2 missing or unreadable: run stage 1. Parsed only here, so a shipped MLP skips
            // the 1.2 MB reference CSV at startup. The visible "stage 1" notice is a UI workstream
            // (docs/CLASSIFIER.md §4).
            KnnLetterClassifier.load(source = { assets.open(KnnLetterClassifier.ASSET_NAME) })
        }
    }

    private data class ClassifierLoad(
        val classifier: LetterClassifier?,
        val failure: ClassifierAssetException?,
    )

    private companion object {
        const val TAG = "HandspellContainer"
    }
}
