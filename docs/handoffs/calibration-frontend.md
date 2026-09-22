# Calibration frontend handoff

Backend support is ready; this note deliberately does **not** add a screen or navigation route.

## Two distinct flows

### Debug research capture — debug builds only

Keep the existing `src/debug` capture activity debug-only. `DebugSignerId.parse(value)` accepts only
opaque labels `s` plus a non-zero number of up to six digits (`s1`, `s42`, `s999999`). It rejects
names, whitespace, and arbitrary text. The existing quick choices are still `s1`–`s5`.

If an advanced debug signer field is added, parse it before selecting it and show an inline error on
failure. Never offer a name field, persist a name, or put debug capture in a release menu.

### Personal calibration — release-safe, local-only

Place a simple **Improve recognition** entry in Settings. Before capture, say in plain language:

> Handspell saves up to 24 small, normalized handshape measurements for this letter on this phone.
> It does not save photos, video, or camera frames. You can delete them here at any time.

Suggested flow: choose one static letter → show the existing canonical guide and its authored cue →
hold the capture control in a comfortable, well-lit pose → collect at least 8 varied samples → review
the count and save. Do not make the user understand signer IDs, CSVs, classifier models, or raw
landmarks. J and Z must not appear because a one-frame calibration cannot represent motion.

Use a `ViewModel` and a `CalibrationUiState` with explicit loading, empty, capture, save-ready,
storage-error, and saved states. The screen-root composable receives state and callbacks only, per
`ARCHITECTURE.md` §5.

## Backend API

```kotlin
val store = DataStorePersonalCalibrationStore(applicationContext)
val session = PersonalCalibrationSession(DefaultHandNormalizer(), store)

session.begin(letter)              // rejects J/Z
session.accept(handLandmarks)      // call only while the user deliberately holds Record
session.state                      // accepted count, duplicates, rejected frames, canSave
session.save()                     // false until 8 distinct samples; replaces this letter's set
session.discard()

store.snapshot                     // Flow<PersonalCalibrationSnapshot>
store.delete(letter)
store.clearAll()
```

The store persists only finite 66-float `NormalizedHand` vectors in app-private Preferences
DataStore. It caps a letter at 24 samples and surfaces unreadable/corrupt storage via
`storageIssue`; show a recovery action rather than silently treating that as an empty calibration.
`clearAll()` removes the calibration document. Add both **Delete B calibration** and **Delete all
calibration** with confirmation, separately from practice-progress deletion unless the product
explicitly combines the disclosures and actions.

## Applying saved samples

`ReloadableKnnLetterClassifier` is a thread-safe stage-1 helper:

```kotlin
val personalized = ReloadableKnnLetterClassifier(bundledKnn)
personalized.replacePersonalExemplars(snapshot.exemplarsByLetter)
```

It atomically replaces only the local portion: a CameraX frame sees either the old complete model or
the new complete model. It intentionally does **not** get wired into `AppContainer` in this change.
The app currently prefers the shipped MLP when available; replacing it wholesale with personal k-NN
would be a product/accuracy decision, not a UI implementation detail. Before wiring, decide and
document whether personal examples (a) provide a target-letter fallback only, (b) opt the user into
stage-1 for calibrated letters, or (c) train/export a new offline model. Do not silently downgrade
all letters to k-NN.

## Acceptance checks for the screen

- The first page has a clear local-only consent explanation and a Cancel action; recording begins
  only from a deliberate hold gesture.
- The guide is visibly upright and labels its target letter; no hand image from the camera is stored.
- Save remains disabled until eight distinct accepted samples. Explain duplicate/rejected counts in
  normal language, e.g. “Move slightly between samples” / “We could not read that hand.”
- Settings shows calibrated letters, sample count, delete-one and delete-all paths, including the
  `storageIssue` recovery state.
- No signer ID or debug capture affordance ships in release builds.
