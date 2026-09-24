# Quality

The general slop rubric (`Claude/reference/ai-slop-rubric.md`) is written for data-collecting web
SaaS. Most of it does not apply here: no server, no auth, no cookies, no email, no UGC, no LLM
feature, no analytics, no account, no payments we process ourselves. What follows is only the items
that *do* apply, each with a pass criterion you can actually check on this app.

## 1. Licensing and provenance (rubric §1.9)

| Check | Pass criterion |
|---|---|
| Repo license | `LICENSE` (MIT) at repo root, visible on the public GitHub page. |
| Model redistribution | `hand_landmarker.task` ships with Apache-2.0 license text alongside it and an attribution line naming Google LLC and the download URL. Apache-2.0 inside MIT is fine as long as that notice survives. |
| `NOTICE` file | Lists every Apache-2.0 component we redistribute or adapt: MediaPipe Tasks + model, the official `mediapipe-samples` code we adapt (HandLandmarkerHelper / OverlayView patterns), Material Symbols icons, AndroidX. RevenueCat SDK (MIT) listed too. |
| No copyleft | `./gradlew :app:dependencies` and `pip-licenses` output contain no `GPL`, `AGPL`, `CC-BY-SA`, `SSPL`, `BUSL`, `Commons Clause`. Re-run before submission. |
| No Kaggle data | Zero third-party datasets in the repo. All training data is self-recorded by named teammates who agreed to it; the consent note lives in `training/DATA.md`. Kaggle ASL Alphabet is single-signer with an unverified license, Sign Language MNIST is 28×28 and unusable — both documented as rejected, not silently omitted. |
| No Lifeprint content | **Lifeprint / ASL University forbids app use of its material.** No handshape text or image in the app is copied or closely paraphrased from it. In-app descriptions use the original wording in `docs/research/data-and-asl-reference.md` §2 (Helen Keller Services, DeafBlind.com, Fiveable). Lifeprint appears only as an outbound "learn more" link in docs. Grep the pack files against that table before submission. |
| Adapted sample code | Any file adapted from `mediapipe-samples` keeps its Apache-2.0 header and a one-line "adapted from" comment. |

## 2. Monetization honesty (rubric §2.2)

| Check | Pass criterion |
|---|---|
| Price up front | Price, period and renewal terms render above the fold on the paywall, in body type. |
| Dismiss is equal | "Not now" is full-width, ≥48dp, normal text-button styling, above the fold. Screenshot it. |
| No confirmshaming | No decline copy that disparages the user. Read every string in the paywall aloud. |
| No fake urgency | No timers, no counters, no "N people upgraded". Grep the paywall config for "left", "hurry", "today only". |
| No pre-checked opt-ins | There are no opt-ins at all. |
| Free tier stated truthfully | The paywall says all 24 letter drills are free forever. That must remain true in code: no drill path checks `isPro`. |
| Cancellation | One sentence with the real path, on both the paywall and Settings. |
| Test Store disclosed | Paywall header and Settings say purchases are simulated via RevenueCat's Test Store and no money is charged. Non-negotiable — an undisclosed fake purchase flow is the rubric's bait-and-switch item. |

## 3. UX states (rubric §3.1)

Every one of these must be a designed screen or inline state, reachable in a build, not a spinner:

- Camera permission: not yet asked / rationale / denied once / denied permanently (deep link to
  Settings) / granted.
- Camera unavailable: no front camera, or another app holds it.
- Detector `Failed`: model asset missing or corrupt — a real message plus a retry, never an exception
  string or a stack trace.
- Classifier asset mismatch: weight file has the wrong spec version or a bad CRC → fall back to stage
  1 with a visible notice.
- Content: pack unreadable, pack schema too new, empty pack list.
- Vision runtime: no hand, hand unrecognised, low light.
- Entitlement: unknown / loading / free / pro / offline-can't-check (Pro stays locked and says why).
- Progress: empty (never practised) state with a next action.
- No `catch (e) {}` and no `catch { Log.e(...) }` as the whole handler. Every `ImageProxy` is closed
  in a `finally`.

## 4. Accessibility and mobile (rubric §3.12, §3.15)

| Check | Pass criterion |
|---|---|
| Content descriptions | Every `Image`/`Icon`/`IconButton` has a non-null `contentDescription` or an explicit `null` with a comment saying it is decorative. Lint rule enabled and failing the build. |
| Tap targets | Every clickable ≥48×48dp with ≥8dp separation. Verified with Layout Inspector on the drill and paywall screens. |
| Contrast | Every foreground/background pair in `DESIGN.md` §1 measured ≥4.5:1 (≥3:1 for ≥22sp and meaningful icons). A unit test computes the ratios from the token values so a palette edit fails CI. |
| Never color-only | Each feedback state differs in shape, icon and text as well as color. Check by screenshotting in greyscale. |
| TalkBack | Full traversal of onboarding, home, progress, settings, paywall with TalkBack on. Feedback badge is a polite live region that announces on state change only. |
| Font scale | Every screen usable at `fontScale = 2.0` with no clipping or horizontal scroll. |
| Small screen | No horizontal scroll at 320dp width. |
| Reduced motion | With animator duration scale 0, no animation runs, including the match pulse. |
| Dark mode | Every screen checked in both modes. |
| No audio dependence | Nothing is conveyed by sound alone, ever. |
| Performance | ≥15 fps end-to-end on the lowest-end device the team owns; frame drops degrade by skipping analysis, never by queueing. |

## 5. Dependencies (rubric §4.2)

| Check | Pass criterion |
|---|---|
| Every coordinate real | Each Gradle dependency confirmed to exist on Maven Central or Google's Maven (`tasks-vision` is **Google's Maven only**, not Central) by opening the metadata URL — not by trusting a model's memory. |
| Every PyPI package real | Same, against pypi.org, with a published date and a repo link. |
| Pinned | All Android versions in `gradle/libs.versions.toml` with exact versions. No `+`, no `latest.release`, no `-SNAPSHOT`, no alpha/beta unless there is no stable alternative and the reason is written down. `requirements.txt` uses `==`. |
| Locked | Gradle dependency locking enabled and lockfiles committed; `training/uv.lock` frozen from a working environment. |
| API shapes verified | Every MediaPipe and RevenueCat call checked against current official docs, not recalled. The research notes in `docs/research/` are the reference. |
| minSdk consistent | 24 (MediaPipe's floor, above RevenueCat's 23). |

## 6. Leftover artifacts and code quality (rubric §4.1, §4.4)

- `grep -rniE "todo|fixme|lorem ipsum|your.?company|example\.com|placeholder|changeme|xxx|coming soon" android/app/src training/` returns nothing outside test fixtures.
- No `println`, no `Log.d`/`Log.v` in release code; logging goes through one wrapper that is a no-op
  in release.
- No AI meta-text in comments ("In a real application you would…", "Here's the code for…").
- No default scaffold content: app label, launcher icon, `MainActivity` body, README are all ours.
- One name per concept — `SignFeedbackState`, not also `FeedbackResult` or `DetectionOutcome`.
- No file over ~300 lines; no composable over ~120. Screens are state + lambdas, logic is in the
  ViewModel or in pure functions.
- Every magic number that affects behaviour lives in `FeedbackThresholds`, `Spacing`, `AslColors` or
  the pack JSON — never inline.
- Comments explain why (e.g. why the orientation block exists), not what the next line does.

## 7. Design and copy tells (rubric §4.3)

- No purple/indigo→pink gradient, no glassmorphism, no oversized radii everywhere. Accent is a deep
  teal; radii come from the five-value scale.
- No LLM boilerplate: grep the string resources for "seamless", "unlock the power", "in today's",
  "elevate", "game-chang", "at your fingertips", "revolution". Zero hits.
- No emoji in headings or section labels.
- The home screen says concretely what the app does in one sentence.
- Icon and screenshots are ours, made for this app.

## 8. Privacy and configuration (rubric §4.5, contest §non-negotiables)

| Check | Pass criterion |
|---|---|
| Nothing uploaded | No code path writes a frame, bitmap or landmark to the network or to shared storage. Verified by reading every call site of the analysis pipeline **and** by capturing traffic during a full session: the only host contacted is `api.revenuecat.com`. |
| Network allowlist | `network_security_config.xml` restricts cleartext and documents the single domain. |
| Permissions | `CAMERA` and `INTERNET` only. No `RECORD_AUDIO`, no location, no storage. Each justified in the privacy screen. |
| Privacy screen | Plain-language, specific to this app, in-app and in the README: what the camera sees, that it stays on the device, what INTERNET is for, what is stored locally, and how to delete it. |
| Delete path | Settings → Delete practice data clears every stored progress value; verified by re-launching. |
| Debug surface excluded | The capture screen is in `src/debug`; `./gradlew assembleRelease` then checking the APK shows no `ui.capture` class. |
| No secrets | Test Store public key read from gitignored `local.properties` into `BuildConfig`; no key literal in git history. `local.properties.example` committed. |
| Release config | `isDebuggable false`, no `android:debuggable`, R8 on, no verbose logging. |

## 9. Tests and CI (rubric §3.8)

- Golden-vector test in **both** languages over `training/testdata/normalizer_golden.json`,
  asserting 1e-6 agreement. This is the single most important test in the repo.
- `FeedbackEngine` unit tests: a synthetic sequence proves no `Match` before the hold window, the
  latch prevents flicker, a target change resets state, and a below-margin frame never matches.
- `LetterClassifier` test: a known exemplar classifies to its own letter; a random vector is rejected.
- Content test: the sample pack validates against the JSON schema; an unknown `schemaVersion`
  produces a `ContentError` rather than a crash; every `letter` in every pack is in `staticLetters`.
- Contrast test over the color tokens.
- `.github/workflows/ci.yml`: `./gradlew assembleDebug testDebugUnitTest lint` plus `pytest` and
  `ruff` for `training/`. Green on `main` before submission.

## 10. Contest deliverables

Repo public with visible MIT license; demo video <2 min, public, showing the app on a real device
with the paywall triggering and a Test Store purchase completing; 1024×1024 icon; 1179×2556
screenshot with no device frame; RevenueCat SDK usage easy to find in the repo. Next Gen entries are
judged from the video and repo and are exempt from store-download testing, so no promo code is
needed — but the README must say how a reviewer can run the Test Store purchase themselves.

## Definition of done — every task, no exceptions

1. No `TODO`, `FIXME`, placeholder copy, or commented-out code left in the change.
2. Pure logic (normalisation, feedback engine, parsing, scoring) has unit tests. UI has at least a
   preview per state.
3. Any new dependency was verified to exist on Maven Central / Google's Maven / PyPI by opening its
   metadata, and is pinned in the version catalog or `requirements.txt` with the lockfile updated.
4. All user-visible text is in `strings.xml`, not a Kotlin literal. Sentence case.
5. Every new icon/image has a content description; every new clickable is ≥48dp.
6. Every new async surface has loading, empty, error and — where relevant — permission-denied states.
7. Nothing new writes to the network, to shared storage, or to a log in release.
8. It builds, `testDebugUnitTest` and `lint` pass, and it was run once on a real device.
9. The build log entry says what changed and what is still open.
