# Handspell

Handspell is an Android app for practising ASL fingerspelling and first word signs with your phone's front
camera. It shows a letter or word, watches your hand, and tells you whether you signed it. Recognition runs
entirely on the device. Built for the RevenueCat Shipaton 2026, Next Gen track.

**Try it:** [download the debug APK](https://github.com/Earthstone59228/handspell/raw/refs/heads/main/artifacts/handspell-debug.apk)
(also attached to the pre-release `debug-20260930-2f5143f`; install notes and SHA-256 in
[`artifacts/README.md`](artifacts/README.md)). Purchases use RevenueCat's **Test Store**: they are simulated and
no money changes hands.

## Features

### Free

- **All 26 letters.** Camera drills for A–Z. The 24 static letters use a single-frame classifier with a short
  hold-to-confirm; **J and Z** are traced motions (starting handshape plus a fingertip-path match) and are marked
  experimental. The drill shows a reference guide, your hand landmarks, three-state feedback (no hand / adjust /
  match) and a written hint for each letter.
- **Alphabet menu** with a hand-shape guide on every letter, completion checkmarks, an A–Z scrubber, and a
  letter sheet to practise again or undo a completion.
- **12 word signs** (hello, bye, thank you, yes, happy, sad, hungry, drink, water, mom, dad, home) with an
  animated front-view example, a camera drill and a Mark complete option. Recognition uses hand and body
  landmarks from your dominant hand.
- **Speed challenge:** sign as many letters or words as you can in 60 seconds. One free round a day.
- **Progress:** current and longest streak, a month calendar, 3/7/14/30-day milestones, letters practised,
  attempts and matches, per-letter counts.
- **Today's quest** on the home screen, and an optional daily streak reminder (off by default, made on the phone,
  nothing sent).
- **Setup help:** a "lean your phone" step and a camera-permission explainer before the first drill.
- **Settings:** System / Light / Dark theme, left-handed layout (moves the
  scrubber; recognition already mirrors left hands), reminders, delete practice data, and the privacy and license
  documents.

### Handspell Pro (RevenueCat, Test Store only)

- 24 more word signs (36 in total).
- Unlimited speed challenges.
- Three story lessons (Market morning, River walk, Bedtime routine; five fingerspelled words each, with resume)
  and three timed speed packs, from distinct letters up to the trickiest look-alikes, with pause and best scores.
- Progress insights: what to practise next, lowest match rate, letters practised this week, average time to a
  match, best speed score.
- Paywall with a Free vs Pro table, monthly ($2.00) and yearly ($10.00) plans, Restore purchases, and cancellation
  instructions. A clearly labelled **3-day demo trial** unlocks Pro locally and never touches the store.

## Honest limits

- **J and Z are experimental.** Their motion thresholds are backed by synthetic tests only; accuracy on real
  signers has not been measured.
- **R, T and U are weaker** and are flagged as experimental in the app. Look-alike letters (A/E/M/N/S/T) are
  the main confusion.
- Static-letter recognition is about 92.5% macro F1 on held-out signers, on still images. It has not been
  measured on a large set of live signers. Training data is public ASLYset (CC BY 4.0) plus 98 team-recorded
  A–D exemplars. Method and numbers: [`docs/research/`](docs/research/) and
  [`docs/CLASSIFIER.md`](docs/CLASSIFIER.md).
- **Word signs are measured on landmark data only.** On unseen signers the 12 free words score 0.84 top-1 and the
  24 Pro words 0.83 (false accepts about 0.5%); see
  [`docs/research/words-v3-2026-09-30.md`](docs/research/words-v3-2026-09-30.md). Not yet measured through the
  phone's own camera, so expect them to be less reliable than letters. Signs made near the face are the hardest.
  An earlier PopSign attempt failed ([`words-v1`](docs/research/words-v1-2026-09-30.md)).
- Purchases are Test Store only, and Test Store purchases only work in debuggable builds. There is no store
  listing.
- Portrait-only, Android-only.

## Privacy

Hand landmarking and classification run on the phone. Camera frames are never saved, uploaded or logged.
There is no backend, account, analytics or crash reporter. The only network traffic is RevenueCat's
entitlement check. Progress lives in local storage, and Settings deletes it. Details: `docs/PRIVACY.md` and
`docs/ARCHITECTURE.md` §10. Owner details still needed for the final notice and Terms are in
`docs/LEGAL_OPEN_ITEMS.md`. Project contact: [Earthstone59228](https://github.com/Earthstone59228/handspell/issues).

## How it is built

Kotlin, Jetpack Compose, CameraX, MediaPipe Hand Landmarker (21 landmarks) and Pose Landmarker lite (for word signs). Landmarks are normalised (wrist
origin, palm scale, left hands mirrored), classified by a small on-device MLP, then smoothed with a
hold-to-confirm; J and Z go through a separate fingertip-path recognizer. Word signs use a small on-device
convolutional network over hand and pose landmarks. The alphabet menu is an Ionic web page in `web/`, bundled as assets. RevenueCat SDK against
the Test Store; the paywall is hand-built Compose. Content comes from bundled versioned JSON packs.
Architecture: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). Design system: [`docs/DESIGN.md`](docs/DESIGN.md).

## Build and run

Machine setup (JDK, Android SDK) is in [`docs/DEV_SETUP.md`](docs/DEV_SETUP.md). Then:

```bash
source env.sh
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug   # 224 JVM tests, lint, debug APK
./gradlew :app:installDebug                                          # to a connected device
```

The compiled web menu is tracked in `android/app/src/main/assets/web/`, so Android builds do not need Node.js. The web menu source is `web/`. After changing it:

```bash
cd web && npm ci && npm run build -- --base=./
cd .. && scripts/sync-ionic-frontend.sh web/dist
```

### RevenueCat public API keys

The key is read from `android/local.properties` (gitignored), never from source:

```bash
cp android/local.properties.example android/local.properties
# set revenuecat.apiKey to your Test Store key (RevenueCat dashboard → Apps and providers → Test Store)
```

For Google Play billing, use an Android public `goog_` key with matching RevenueCat products, offering and `pro` entitlement. Set `revenuecat.releaseApiKey` for release builds; it falls back to `revenuecat.apiKey` when that key is not a Test Store key. Test Store keys are omitted from release builds so demo mode remains usable. Never use a secret key or Apple `appl_` key in this Android app.

Leaving it blank still builds and runs; Pro purchases are then reported as unavailable. Test Store
purchases only work in debuggable builds. See [`docs/research/revenuecat.md`](docs/research/revenuecat.md).

## Repo layout

```
android/     Gradle project, single :app module (dev.handspell.app)
web/         Ionic alphabet menu source (built and copied into Android assets)
artifacts/   Latest verified debug APK + checksum
scripts/     Web sync and APK compatibility check
docs/        Architecture, design, classifier, privacy, research, audits, submission notes
training/    Python pipelines that train the letter classifier and word models
recorder/    Desktop tool that recorded the A–D reference set
preview-windows/  Desktop camera preview of the recognition pipeline
```

## Roadmap

Practice-depth features (finger-by-finger feedback, free mode), more word signs, live-signer accuracy
measurement, and a store launch are planned in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## License

MIT, see `LICENSE`. Third-party components and data (MediaPipe models and Tasks, AndroidX, Material Symbols,
RevenueCat SDK, Capacitor, ASLYset, Google ISLR word data) are credited with their licences in `NOTICE`.
