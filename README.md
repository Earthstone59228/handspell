# Handspell

Handspell is an Android app that teaches the ASL manual alphabet: hold a letter in front of your
front camera and get live, on-device feedback on whether your handshape matches. It was built for
the RevenueCat Shipaton 2026, Next Gen track.

## Privacy

All hand landmarking, normalisation and classification runs on the phone, in-process. Camera frames
exist only as a `Bitmap` on the analysis thread and are never written to disk, encoded, uploaded or
logged. The only data leaving the device is the RevenueCat SDK's entitlement check, which talks to
`api.revenuecat.com` and nothing else — there is no backend, no account, no analytics SDK and no
crash reporter. Progress (letter counters, streaks, completed lessons) is a local file you can
delete at any time from Settings. See `docs/ARCHITECTURE.md` §10 for the full accounting.

## Build and run

Full machine setup (JDK, Android SDK, env vars) is in `docs/DEV_SETUP.md`. Once that's done:

```bash
source env.sh
cd android
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint
```

Install on a connected device with `./gradlew :app:installDebug`, or open `android/` in Android
Studio.

### RevenueCat Test Store key

The app reads its RevenueCat key from `android/local.properties` (gitignored), never from source:

```bash
cp android/local.properties.example android/local.properties
# edit android/local.properties and set revenuecat.apiKey to your Test Store key
```

Get a Test Store key from the RevenueCat dashboard: **Apps and providers → Test configuration →
Test Store**, no Google Play Console account needed (`docs/research/revenuecat.md` §2–3). Test Store
purchases behave like real ones (they update entitlements) but charge no money; the app says so on
the paywall and in Settings. Leaving `revenuecat.apiKey` blank still builds and runs — the
entitlement gate just reports `Unavailable`.

## Repo layout

```
android/    Gradle project, single :app module (dev.handspell.app)
docs/       Architecture, contracts, quality bar, and research notes this build follows
preview-windows/
            Desktop camera preview: skeleton overlay plus the app's stage-1 pipeline, so
            recognition can be measured on a laptop (docs/WINDOWS_PREVIEW.md)
recorder/   Desktop tool that records the stage-1 reference set; stands in for the debug-only
            capture screen that is not written yet (docs/RECORDER.md)
training/   Python pipeline that records landmark data and trains the offline classifier
```

Inside `android/app/src/main/java/dev/handspell/app/`, see `docs/ARCHITECTURE.md` §2 for the package
layout and `docs/CONTRACTS.md` for the interfaces each workstream builds against.

## License

MIT — see `LICENSE`. Third-party components redistributed or adapted (the MediaPipe hand-landmarker
model, MediaPipe Tasks, AndroidX, Material Symbols, the RevenueCat SDK) are listed with their own
licenses in `NOTICE`.
