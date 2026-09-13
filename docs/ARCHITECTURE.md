# Architecture

Shipaton 2026, Next Gen track. Written 2026-09-13. Decisions here are settled; raise a change in the
build log rather than in code.

## 1. Identity

| Thing | Value | Why |
|---|---|---|
| Working app name | **Handspell** | Descriptive, pronounceable, not a trademark we could find in the ASL-app space (unlike Handspeak, Lifeprint, SignAll). Rename later is a one-commit find-and-replace. |
| `applicationId` / Kotlin root package | `dev.handspell.app` | Reverse-DNS, no `com.example` (Play rejects it), no personal name to strip later. |
| Module | single `:app` | One module builds faster than four in a 17-day window and there is no second consumer of any layer. |
| `minSdk` / `targetSdk` | 24 / current stable | MediaPipe Tasks requires 24; RevenueCat requires 23. 24 wins. |
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Google's first-party, best-documented path; see the build plan's stack decision. |

## 2. Package layout (`android/app/src/main/java/dev/handspell/app/`)

```
HandspellApplication.kt      Application; builds AppContainer, configures RevenueCat.
MainActivity.kt              Single activity, launchMode=singleTop (RevenueCat requires it).
di/AppContainer.kt           Hand-written dependency graph. Constructed once in Application.
core/model/                  Letter, HandLandmarks, NormalizedHand, Classification,
                             SignFeedbackState. Pure Kotlin, no Android imports except none.
core/time/                   Clock abstraction so the feedback engine is testable without a device.
vision/                      HandNormalizer, LetterClassifier, FeedbackEngine, SignDetector
                             contracts + FakeSignDetector.
vision/camera/               CameraX binding, ImageProxy -> MPImage conversion.
vision/landmarker/           MediaPipe HandLandmarker wrapper (LIVE_STREAM mode).
vision/normalize/            The one Kotlin implementation of the normalisation spec.
vision/classify/             KnnLetterClassifier (stage 1), MlpLetterClassifier (stage 2),
                             MlpWeights parser for assets/classifier/*.bin.
vision/feedback/             DefaultFeedbackEngine: EMA smoothing + hold-to-confirm.
vision/detector/             CameraSignDetector: wires the above into the SignDetector façade.
content/                     ContentRepository + pack data classes + JSON parsing.
progress/                    ProgressStore over DataStore.
billing/                     EntitlementGate + RevenueCat implementation.
ui/theme/                    Tokens: Spacing, AslColors, AslTypography, Shapes, Motion.
ui/components/               FeedbackBadge, CameraFrame, LetterTile, PrimaryButton, StateScaffold.
ui/onboarding|home|drill|story|speed|progress|paywall|settings/
                             One package per screen: Screen.kt (composables) + ViewModel.kt +
                             UiState.kt. Nothing else imports another screen's package.
```

`src/debug/java/dev/handspell/app/ui/capture/` holds the dev capture screen. It is in the debug
source set, so it cannot ship in a release build — no feature flag to forget to flip.

## 3. Data flow

```
CameraX Preview  ─────────────────────────────────────────► PreviewView (mirrored, main thread)

CameraX ImageAnalysis (RGBA_8888, 640x480 target,
  STRATEGY_KEEP_ONLY_LATEST, single-thread "handspell-analysis" executor)
        │ ImageProxy
        ▼
  FrameConverter        copy plane 0 -> Bitmap, Matrix: postScale(-1,1) then
        │               postRotate(imageInfo.rotationDegrees) -> selfie-mirrored,
        │               display-upright bitmap -> MPImage
        ▼
  HandLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        │                                        (monotonic, never repeats)
        ▼  result listener, MediaPipe's own thread
  HandLandmarks (21 world points + 21 image points + handedness + timestamp + size)
        │
        ├──► OverlayState (image points only) ──► Compose overlay
        ▼
  HandNormalizer.normalize()        66 floats, spec v1 (docs/CLASSIFIER.md §2)
        │                           null on degenerate geometry -> treated as no hand
        ▼
  LetterClassifier.classify()       ranked probabilities + nearest-exemplar distance
        ▼
  FeedbackEngine.onFrame()          EMA smoothing, margin check, hold-to-confirm timer
        ▼
  MutableStateFlow<SignFeedbackState>
        ▼  collectAsStateWithLifecycle
  DrillViewModel ──► DrillUiState ──► Compose  (+ haptic on Match, + ProgressStore write on Dispatchers.IO)
```

Nothing in this chain touches the network. The frame `Bitmap` is reused per analysis thread and the
`ImageProxy` is closed in a `finally` block on every path.

## 4. Threading

| Work | Thread |
|---|---|
| Camera preview rendering | main / render thread (CameraX owns it) |
| `ImageAnalysis` callback, bitmap conversion, `detectAsync` | single-thread executor `handspell-analysis` |
| Landmarker inference | MediaPipe internal (CPU delegate); result on its listener thread |
| Normalise + classify + smooth | the listener thread, synchronously — a 66-float k-NN over ≤1,536 exemplars is ~100k multiply-adds, well under a frame budget |
| StateFlow collection, recomposition | main, via `collectAsStateWithLifecycle` |
| DataStore reads/writes, asset parsing | `Dispatchers.IO` inside `viewModelScope` |
| RevenueCat SDK | its own; results surface as StateFlow |

No `runBlocking`. No work posted to the main thread from the analysis path other than StateFlow
emission. Backpressure is handled by dropping frames, never by queueing them.

## 5. State management

ViewModel + `StateFlow<XUiState>` per screen; `UiState` is one immutable data class with explicit
`isLoading` / `error` / content fields, so loading, empty and error states are impossible to forget.
One-shot events (navigate, show paywall, haptic) go through a `Channel(Channel.BUFFERED)` exposed as
a `Flow`, not as state, so they do not replay on rotation. Composables receive state and lambdas
only — no ViewModel reference below the screen-root composable.

## 6. Dependency injection

**Hand-written `AppContainer`, no Hilt.** The graph is roughly a dozen objects with no scoping
beyond "singleton" and "per-screen"; Hilt would add KSP, a plugin, generated-code build time and a
whole class of annotation errors to debug for no benefit at this size. ViewModels are created via a
`ViewModelProvider.Factory` that reads from the container. Swapping `FakeSignDetector` for
`CameraSignDetector` is one line in `AppContainer`.

## 7. Storage

**DataStore (Preferences) holding one kotlinx-serialization JSON document**, not Room. The entire
dataset is 24 letter counters, a set of completed story step ids, ≤50 speed runs and two streak
numbers — a few kilobytes with no queries, no joins and no partial updates. Room would mean a schema,
migrations, a compiler plugin and DAO tests for a document that is always read whole. Versioned by
`ProgressSnapshot.SCHEMA_VERSION`; an unreadable or newer document is replaced with a fresh one
rather than crashing. `clearAll()` deletes the file, and it is reachable from Settings.

Reference exemplars and MLP weights are read-only assets, not storage. Dev captures are written as
CSV to the app-specific external files dir in debug builds only, pulled with `adb pull`.

## 8. Content packs

Versioned JSON in `assets/content/`, described by `docs/schemas/content-pack.schema.json`.
`index.json` lists the packs; each pack declares `schemaVersion`, `packId`, `packVersion`, `kind`
(drill / story / speed) and `tier` (free / pro). The repository refuses a pack whose `schemaVersion`
it does not know and reports a `ContentError` rather than dropping it silently. Pro packs are listed
for free users so they can see what the subscription contains; their items are only handed out when
the `pro` entitlement is active. Adding a pack post-hackathon is a new JSON file plus an index entry
— which is what makes "new packs added regularly" a true statement rather than a pitch claim.

## 9. Monetization

RevenueCat `purchases` SDK, configured against a **Test Store** (no Play Console). Entitlement id
`pro`, offering `default` with monthly + annual packages. **The paywall is our own Compose screen**
(decided 2026-09-13: RevenueCat's dashboard templates don't fit the design language), built on
`Purchases.getOfferings()` → package list → `Purchases.purchase(PurchaseParams)` →
`restorePurchases()`, following DESIGN.md §5. `purchases-ui` is not a dependency.
The public Test Store key is read from `local.properties` into `BuildConfig` so the repo stays clean.
`EntitlementGate` is the only thing the rest of the app knows about purchases; `isPro` is false while
the status is unknown, so Pro content can never leak during a slow fetch. Because Test Store
purchases are simulated, the paywall and the settings screen both say so in plain words in every
build we ship for judging.

## 10. No backend. Nothing leaves the device.

There is no server, no account, no login, no analytics SDK, no crash reporter, no remote config and
no content CDN. Camera frames exist only as a `Bitmap` inside the analysis thread and are never
written to disk, encoded, uploaded or logged. Landmarks are never persisted outside the debug-only
capture screen. Progress is a local file.

The app does declare `android.permission.INTERNET`, because the RevenueCat SDK needs it to validate
the `pro` entitlement. That is the only reason, the only network user, and `api.revenuecat.com` is
the only host — enforced by a `network_security_config.xml` domain allowlist and verified by watching
traffic during QA (docs/QUALITY.md). We say exactly this in the privacy screen; we do not claim the
app is offline when it is not.

## 11. What runs where

| Component | Where |
|---|---|
| Hand landmarking, normalisation, classification, smoothing | on the phone, CPU, in-process |
| Reference exemplars / MLP weights | bundled in `assets/`, read-only |
| Lesson content | bundled in `assets/`, read-only |
| Progress and settings | DataStore file in app-private storage |
| MLP training, evaluation, weight export | `training/`, Python on a laptop, offline, never on the phone |
| Entitlement check and purchase | RevenueCat SDK ↔ RevenueCat Test Store |
| Dev capture CSV | debug builds only, app-specific external files dir, pulled over adb |
