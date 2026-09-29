# Handspell

Handspell is an Android app for practising ASL fingerspelling with your phone's front camera. It shows a
letter, watches your hand, and tells you whether the hand shape matches. Recognition runs entirely on the
device. Built for the RevenueCat Shipaton 2026, Next Gen track.

**Testing it:** a debug APK is attached to the GitHub pre-release `debug-20260929-27852be`. Purchases in this
build use RevenueCat's **Test Store**: they are simulated and no money changes hands.

## What it does

Free:
- Camera drills for all 24 static letters (A–Z without J and Z). The app overlays your hand landmarks, shows
  three-state feedback (no hand, adjust, match) and confirms a match after a short hold. After a match you can
  Continue or Skip.
- An alphabet menu with checkmarks for letters you have practised, an A–Z scrubber, and a streak chip. Tapping
  the chip opens Progress.
- A Progress screen (streak, longest streak, letters practised, attempts, per-letter counts), all stored
  locally.
- A left-handed layout (Settings, or on the first-run intro) that moves the scrubber to the left edge.
  Recognition already mirrors left hands.
- A first-run introduction: what the app does, how to hold the phone, and that the camera stays on the device.
- Settings for theme, subscription status, data deletion and the legal documents.

Handspell Pro (sold through RevenueCat, Test Store only):
- 3 story packs of 5 word prompts each, and 3 timed speed rounds with pause/resume and best scores.
- The paywall is an ordinary page with a header and back. It lists what Pro contains, shows the price and
  period before the purchase button, and offers Continue and Restore. It opens only after a tap on a locked
  pack or on the Pro row in Settings. Locked packs in the menu show a neutral lock, and nothing shows once you
  have Pro.
- The packs are also reachable from Settings → Handspell Pro → Open story and speed packs.

## Honest limits

- **J and Z are disabled.** They need motion, and the classifier works on single frames.
- **R, T and U are weaker** and are flagged as experimental in the app. Look-alike letters (A/E/M/N/S/T) are
  the main confusion.
- Measured recognition is about 92.5% macro F1 on held-out signers, on still images. It has not been
  measured on a large set of live signers. The training data is public ASLYset (CC BY 4.0) plus 98
  team-recorded A–D exemplars. Method and numbers: [`docs/research/`](docs/research/) and
  [`docs/CLASSIFIER.md`](docs/CLASSIFIER.md).
- **Word signs are not included.** A spike on PopSign data failed (0.19 top-1); see
  [`docs/research/words-v1-2026-09-30.md`](docs/research/words-v1-2026-09-30.md). The "word prompts" in Pro
  packs are words you fingerspell letter by letter.
- Purchases are Test Store only. There is no store listing.
- The app is portrait-only and Android-only.

## Privacy

Hand landmarking and classification run on the phone. Camera frames are never saved, uploaded or logged.
There is no backend, account, analytics or crash reporter. The only network traffic is RevenueCat's
entitlement check. Progress lives in local storage, and Settings deletes it. Details: `docs/PRIVACY.md` and
`docs/ARCHITECTURE.md` §10. Owner details still needed for the final notice and Terms are in
`docs/LEGAL_OPEN_ITEMS.md`; operator, contact and jurisdiction are `[owner to provide]`.

## How it is built

Kotlin, Jetpack Compose, CameraX, MediaPipe Hand Landmarker (21 landmarks). Landmarks are normalised (wrist
origin, palm scale, left hands mirrored), classified by a small on-device MLP, then smoothed with a
hold-to-confirm. The alphabet menu is an Ionic web page in `web/`, bundled as assets. RevenueCat SDK against
the Test Store; the paywall is hand-built Compose. Content comes from bundled versioned JSON packs.
Architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Design system: [`docs/DESIGN.md`](docs/DESIGN.md).

## Build and run

Machine setup (JDK, Android SDK) is in [`docs/DEV_SETUP.md`](docs/DEV_SETUP.md). Then:

```bash
source env.sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # JVM tests, lint, debug APK
./gradlew :app:installDebug                                          # to a connected device
```

The web menu source is `web/`. After changing it:

```bash
cd web && npm ci && npm run build -- --base=./
cd .. && scripts/sync-ionic-frontend.sh web/dist
```

### RevenueCat Test Store key

The key is read from `android/local.properties` (gitignored), never from source:

```bash
cp android/local.properties.example android/local.properties
# set revenuecat.apiKey to your Test Store key (RevenueCat dashboard → Apps and providers → Test Store)
```

Leaving it blank still builds and runs; Pro purchases are then reported as unavailable. Test Store
purchases only work in debuggable builds. See [`docs/research/revenuecat.md`](docs/research/revenuecat.md).

## Repo layout

```
android/     Gradle project, single :app module (dev.handspell.app)
web/         Ionic alphabet menu source (built and copied into Android assets)
scripts/     Web sync and APK compatibility check
docs/        Architecture, design, classifier, privacy, research, audits, submission notes
training/    Python pipeline that trains the classifier from landmark data
recorder/    Desktop tool that recorded the A–D reference set
preview-windows/  Desktop camera preview of the recognition pipeline
```

## Roadmap

Practice-depth features (finger-by-finger feedback, free mode, streak reminders), word signs and motion
letters, and a store launch are planned in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## License

MIT, see `LICENSE`. Third-party components and data (MediaPipe model and Tasks, AndroidX, Material Symbols,
RevenueCat SDK, ASLYset) are credited with their licences in `NOTICE`.
