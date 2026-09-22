# Contracts

The four workstreams build in parallel against these interfaces. They are real files, already at
their final paths; the Gradle scaffold builds around them. Change one only by editing this document
and the stub together, and say so in the build log — someone else is compiling against it.

Root package: `dev.handspell.app`. All paths below are relative to
`android/app/src/main/java/dev/handspell/app/`.

## 1. Core model — `core/model/`

| File | Type | Notes |
|---|---|---|
| `Letter.kt` | `enum Letter` | 26 entries. `J` and `Z` carry `requiresMotion = true`; `Letter.staticLetters` is the 24-entry list whose **index is the classifier's class index**. |
| `HandLandmarks.kt` | `Handedness`, `Landmark3`, `HandLandmarks` | 21 world points + 21 image points + handedness + score + monotonic `timestampMs` + frame size. Constructor validates both lists are 21 long. Index constants `WRIST`, `INDEX_MCP`, `MIDDLE_MCP`, `PINKY_MCP` live here. |
| `NormalizedHand.kt` | `value class NormalizedHand(FloatArray)` | 66 floats: 60 shape + 6 orientation. `SPEC_VERSION`, `SHAPE_DIM`, `ORIENTATION_DIM`, `VECTOR_DIM`. |
| `Classification.kt` | `LetterScore`, `Classification` | `ranked` is every known letter, descending. `nearestDistance` is the weighted stage-1 distance; `nearestShapeDistance` is its paired orientation-invariant component (both `NaN` from the MLP). Feedback uses shape distance only for direction-agnostic A–D. `probabilityOf(letter)` returns 0 for unknown letters. |
| `SignFeedbackState.kt` | sealed `SignFeedbackState`, `FeedbackHint` | `NoHand`, `NotRecognized`, `Adjust`, `Match`. `Adjust` and `Match` have a non-null `target`; the other two allow null for the dev capture screen. `Adjust.holdProgress` is 0..1 and drives the progress ring. `FeedbackHint.id` keys a string resource. |

Frame convention is baked into the KDoc on `Landmark3` and `Handedness`: all coordinates are in the
selfie-mirrored, display-upright frame, so `+x` is screen-right and `+y` is screen-down, and the
handedness label is the user's actual hand. Anyone producing `HandLandmarks` must honour that.

## 2. Vision — `vision/`

```kotlin
interface HandNormalizer {
    val specVersion: Int
    fun normalize(landmarks: HandLandmarks): NormalizedHand?   // null = degenerate geometry
}

interface LetterClassifier {
    val modelId: String                    // "knn-v1" / "mlp-v1", shown in settings
    val specVersion: Int                   // refuse to load if != NormalizedHand.SPEC_VERSION
    val supportedLetters: List<Letter>     // letters that passed the eval gate; drives every screen
    fun classify(hand: NormalizedHand, timestampMs: Long): Classification
}

interface FeedbackEngine {                 // pure logic, no Android types, no coroutines
    fun setTarget(target: Letter?)         // resets all smoothing state
    fun onFrame(classification: Classification): SignFeedbackState
    fun onNoHand(timestampMs: Long): SignFeedbackState
    fun reset()
}

data class FeedbackThresholds(/* the §6 table in CLASSIFIER.md, all with defaults */)

interface SignDetector {
    val status: StateFlow<DetectorStatus>          // Idle / Starting / Running / Failed
    val classifierModelId: String?                 // `knn-v1` / `mlp-v1`, null if unavailable
    val feedback: Flow<SignFeedbackState>          // at most one per analysed frame
    val overlay: StateFlow<HandOverlay?>           // current image landmarks, null = no hand
    val analyzer: ImageAnalysis.Analyzer           // bind to CameraX; must close every ImageProxy
    val analyzerExecutor: Executor                 // single `handspell-analysis` executor
    fun setTarget(target: Letter?)                 // null = report the top letter freely
    fun start()
    fun stop()
}
```

`SignDetector` is deliberately the *only* vision type the UI touches. It exposes the live overlay,
the CameraX `ImageAnalysis.Analyzer`, and its single serial executor rather than hiding CameraX
behind another abstraction: CameraX is an app-wide dependency either way, and a pass-through wrapper
would buy nothing but a layer to debug. The camera screen owns the `Preview` and `ImageAnalysis` use
cases and hands both the analyzer and executor to CameraX. The overlay is required for the drill as
well as debug capture: it confirms that the on-device detector sees the learner's hand without
exposing world landmarks or classifier internals.

`FakeSignDetector(scope, stepMillis = 900)` implements the same contract with no camera, no model
and no permission: it cycles NoHand → NotRecognized → Adjust(0.3) → Adjust(0.8) → Match on a timer
and drops every frame it is given. UI and content work was built against it from day one; the swap
to the real detector in `AppContainer` has since happened, and the fake now serves UI work and
screenshot tests only.

`DetectorStatus.Failed(messageId, cause)` carries a string-resource key, not a message — the UI never
renders an exception.

## 3. Content — `content/`

```kotlin
enum class Tier { FREE, PRO }
enum class PackKind { DRILL, STORY, SPEED }

data class ContentIndex(schemaVersion, packs: List<PackRef>)
data class ContentPack(schemaVersion, packId, packVersion, kind, tier, title, summary,
                       releasedOn, items: List<PackItem>)

sealed interface PackItem {
    data class Drill(id, letter, prompt, description, hintId)
    data class StoryStep(id, narration, spellWord, letters)
    data class SpeedRound(id, title, durationSeconds, letters, targetCorrect)
}

sealed interface ContentError { UnsupportedSchema(packId, found, supported); Unreadable(packId, cause) }

interface ContentRepository {
    val packs: Flow<List<ContentPack>>
    val errors: Flow<List<ContentError>>
    suspend fun pack(packId: String): ContentPack?
    suspend fun availableDrills(): List<PackItem.Drill>   // filtered to supportedLetters
}
```

`availableDrills()` filters by `LetterClassifier.supportedLetters`, which is how a letter cut at the
Sep 18 checkpoint disappears from the UI without anyone editing a screen.

Schema: `docs/schemas/content-pack.schema.json` (JSON Schema 2020-12, `additionalProperties: false`,
`kind` selects which item shape is allowed). Real drill pack:
`android/app/src/main/assets/content/packs/drills-core.json` (24 items) plus
`android/app/src/main/assets/content/index.json`. The loader, the error states and the drill screen
run against `drills-core.json` (24 items), not a placeholder sample.

Content authors: `description` must be original wording. Lifeprint / ASL University material may not
be embedded or closely paraphrased — its terms forbid app use. Use the original descriptions in
`docs/research/data-and-asl-reference.md` §2.

## 4. Progress — `progress/`

```kotlin
data class LetterProgress(letter, attempts, matches, bestTimeToMatchMs, lastPractisedAt)
data class SpeedRunResult(roundId, completedAt, correct, durationSeconds)
data class ProgressSnapshot(schemaVersion, letters, completedStoryStepIds, speedRuns,
                            currentStreakDays, longestStreakDays, onboardingCompleted)

interface ProgressStore {
    val snapshot: Flow<ProgressSnapshot>
    suspend fun recordAttempt(letter: Letter, matched: Boolean, timeToMatchMs: Long?)
    suspend fun recordStoryStep(stepId: String)
    suspend fun recordSpeedRun(result: SpeedRunResult)
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun clearAll()
}
```

Counts only — no frames, no landmarks, no timestamps that could reconstruct a session. `speedRuns` is
capped at `MAX_SPEED_RUNS = 50`, newest first. `clearAll()` is the user-facing data-deletion path in
Settings, not a debug hook, and must leave no file behind.

## 5. Entitlement — `billing/`

```kotlin
enum class PaywallSource { STORY_LESSON, SPEED_CHALLENGE, SETTINGS, PROGRESS_DETAIL }

sealed interface EntitlementStatus { Unknown; Loading; Resolved(isPro); Unavailable(cause) }

interface EntitlementGate {
    val isPro: StateFlow<Boolean>            // false while Unknown/Loading — content never leaks
    val status: StateFlow<EntitlementStatus> // so the UI can say "checking" vs "locked" vs "offline"
    val paywallRequests: Flow<PaywallSource>
    val entitlementId: String                // "pro"
    fun requestPaywall(source: PaywallSource)
    suspend fun refresh()
}
```

Presentation is a *request*, not a command: the gate emits on `paywallRequests` and a composable near
the nav host collects it and shows **our own Compose paywall** (`ui/paywall/`, DESIGN.md §5), which
reads packages from `Purchases.getOfferings()` and purchases via `Purchases.purchase(...)`. An
interface should not be holding an Activity or a composition.

`Unavailable` exists because offline is a real state on a phone. Pro content stays locked, but the UI
says "can't check your subscription right now" rather than "upgrade".

## 6. Ownership and the day-one swap points

| Workstream | Implements | Consumes (already exists) |
|---|---|---|
| Vision | `HandNormalizer`, `LetterClassifier`, `FeedbackEngine`, `CameraSignDetector` | `core/model` |
| UI + content | screens, ViewModels, `ContentRepository` impl, packs | `SignDetector` (via `CameraSignDetector`), `ProgressStore`, `EntitlementGate` |
| Monetization | `EntitlementGate` impl, RevenueCat config, paywall host | `PaywallSource` |
| Docs | README, privacy copy, NOTICE, CI | all of the above |

Both swaps have landed in `AppContainer`: the real `CameraSignDetector` is wired (constructed in a
`Failed` state while `assets/classifier/references-v1.csv` is absent, so nothing is scored against a
missing classifier), and the stage-1 `KnnLetterClassifier` is used only when the stage-2
`MlpLetterClassifier` weights cannot be loaded. If a workstream needs a third swap point, it is a
design smell — say so before adding it.
