# Frontend rewrite progress — 2026-09-23

Continues `frontend-rewrite-handover-2026-09-23.md` in the writable checkout at
`/home/earthstone/Shipaton-rewrite`.

## Implemented after the handover

- Added first-run onboarding with practice, letter-coverage, and camera/privacy explanations.
- Added Practice, Progress, and Settings navigation. Progress shows letter counts, matches,
  streak, completed story steps, recent speed results, and a path to data deletion.
- Made the bundled Pro story and speed packs reachable when the entitlement is active. Both reuse
  the confirmed camera-drill feedback; stories save completed steps, speed rounds save scored runs.
  Locked packs request the paywall. Builds without a RevenueCat key hide those actions.
- Updated the drill feedback badge with distinct dotted/dashed/ring/filled shapes and real hold
  progress. Repeated letters reset the detector session and cannot inherit a previous match.
- Put Test Store disclosure in the paywall header, annual savings beside pricing when genuine,
  renewal terms beside each plan, and a persistent full-width dismiss button. A purchase only
  reports success if the returned customer info contains the Pro entitlement.
- Improved offline privacy-document headings and lists, debug capture picker layout, and local-day
  streak boundaries. Added focused summary/savings unit tests and a DataStore instrumentation test.
- Added a timed, dismissible low-light notice from sampled camera frames. Updated README scope and
  capture status after the rubric review.
- Enabled Gradle dependency locking and generated the app lockfile. Corrected the privacy notice's
  contradictory child-data wording; responsible-party contact and legal details still need owner input.

## Verification

- `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleRelease`, and
  `compileDebugAndroidTestKotlin` passed in the writable checkout. Release packaging needed one
  Compose mapping artifact downloaded once, then built offline.
- After syncing, `:app:compileDebugKotlin --offline --no-daemon` also passed in the original
  checkout with its existing ignored Test Store key.
- The DataStore instrumentation test compiled but could not run: no Android device is attached.
- No screenshots, 320dp/200% font-scale visual pass, TalkBack traversal, camera trial, or Test Store
  purchase/restore trial were possible without a device. The clone has no local Test Store key;
  the original project's ignored `android/local.properties` did before its mount disappeared.
- RevenueCat's pinned 10.21.1 AAR was inspected for `Price.amountMicros` and `currencyCode`;
  official documentation confirms Test Store, purchase, and restore flow shapes.

## Remaining handoff work

- Run on a device with the original Test Store key: complete and cancel purchases, restore, Pro
  locking on fetch error, story and speed camera flows, and release/debug capture separation.
- Review every reachable screen in light and dark at 320dp and 200% font scale, plus TalkBack.
- The checkout was synced to `/mnt/drive/Work/Project/Shipaton` after the mount became writable;
  all 46 changed/new files matched byte for byte. The earlier read-only copy attempt changed
  nothing. A local snapshot remains at `/home/earthstone/Shipaton-rewrite-frontend-2026-09-23.tar.gz`.
