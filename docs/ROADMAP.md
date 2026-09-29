# Handspell roadmap

Last updated 2026-09-29. Shipaton submission (Next Gen track) closes Sep 30 2026, 11:45 pm PT. The team is aiming to submit by 5:45 pm PT.

## Rules that apply to every phase

- **Same design language everywhere.** Native screens use `AslButton`, `LocalAslColors`, `Spacing`, `AslShapes` and the typography in `docs/DESIGN.md`. The alphabet menu uses the existing `web/src/css/style.css` tokens. Don't use raw Material defaults or add a new visual style.
- **Recognition stays on the device.** No uploads, and no backend unless a phase below says otherwise.
- **Data provenance.** Only licensed public data or consented recordings. Record every source in `NOTICE`.
- **Honest claims.** Recognition numbers in the app, docs and video must come from `docs/research/`.

## Phase 0: submission (now until the freeze on Sep 30, 18:00 +07)

These are small features that make the demo and the judges' first run better, plus the submission work that's still open. Anything that isn't green by the freeze gets deferred, not rushed.

| # | Item | Why it's in Phase 0 | Size |
| --- | --- | --- | --- |
| 0.1 | **Pro feels part of the app, not an ad.** The paywall becomes a normal page with a header and back, and it leads with what's in Pro. Menu packs get a quiet lock instead of a blue "Handspell Pro" tag, and nothing opens the paywall without a tap. | Judges must see the RevenueCat paywall in the video, and right now the paywall and the menu both read as an upsell. | S |
| 0.2 | **Streak on the main menu.** A streak chip in the alphabet menu header. The data already exists in `ProgressStore`. | It's the retention hook, and it shows in the first seconds of the video. | S |
| 0.3 | **First-run introduction.** Rebuild the existing `OnboardingScreen` in the design language: what the app does, how to hold the phone, and that the camera stays on the device. | Judges install fresh, and the current screen uses stock Material. | S |
| 0.4 | **Left-handed mode.** A setting that moves the A–Z scrubber to the left edge (also offered during onboarding). | It's cheap, and it's a real accessibility point. Recognition already mirrors left hands. | S |
| 0.5 | Audit bug fixes (T26 items 1, 2 and 4 only) | Correctness | S–M |
| 0.6 | Secret audit, judge README, device QA, video, public repo, Devpost | Required for submission | — |

## Phase 1: v1.1, practice depth (October)

| # | Item | Notes |
| --- | --- | --- |
| 1.1 | **Deeper feedback.** Replace generic prompts with a finger-by-finger correction, for example "curl your index finger" or "thumb goes across the fingers, not beside them". | Compare the live landmarks with the target reference by joint angles and fingertip distances, and name the one finger that's furthest off. It's deterministic and explainable, and it runs on the device. |
| 1.2 | **Free mode (letters).** Show any letter and the app says what it sees. | The MLP already scores all 24 letters. It needs a no-target confidence gate and a top-3 display so look-alike letters (A/E/M/N/S/T) aren't presented as certain. |
| 1.3 | **Streak reminders.** A daily local notification ("keep your 5-day streak"), switched off by default and controlled in Settings. | Use WorkManager with local notifications. No server is needed. Update `PRIVACY.md` and handle the Android 13+ notification permission. |
| 1.4 | **More live data for E–Y**, from consented recordings by several signers | This is the biggest remaining accuracy gain. Retraining takes about 10 minutes (`training/scripts/train_mlp.py`). |
| 1.5 | Remaining T26 audit items (plurals, content loading, thumbnails, landmarker off the main thread) | Hygiene and performance |

## Phase 2: v1.2, words and motion

| # | Item | Notes |
| --- | --- | --- |
| 2.1 | **Word signs**, added next to the letters in the menu. A spike on 2026-09-30 failed on low-fps data; see `docs/research/words-v1-2026-09-30.md`. The plan is 12 free words and the rest bundled but Pro-locked (not downloaded, because the model is tiny). | Word signs move, but today's pipeline classifies single frames. This needs a sequence model over landmark windows. Candidate data: PopSign ASL (250 signs, smartphone selfie camera, MediaPipe landmarks) and ASL Citizen. **Check each licence before use**, because some are research-only. |
| 2.2 | **J and Z** | These are the same motion problem as 2.1, so they come from the same model. |
| 2.3 | **Free mode (words)** | Depends on 2.1. |

## Phase 3: launch and platforms

| # | Item | Notes |
| --- | --- | --- |
| 3.1 | **Fingerspelling-to-text translator** | Train on Google's **FSboard** dataset: 3.2M characters of smartphone fingerspelling, collected for Gboard research, CC BY 4.0. We use the public data and open research, not Gboard's code or model. It's a sequence-to-text (CTC) model, so it builds on 2.1. |
| 3.2 | Store release (Google Play and/or Galaxy Store) with real billing | The RevenueCat Test Store only works in debuggable builds, so a store build needs a real store product. |
| 3.3 | Push messaging with **OneSignal** | Needs a live store app. Moves reminders from 1.3 onto OneSignal campaigns. |
| 3.4 | **Foldables and large screens** (Galaxy Z Fold/Flip) | Today the app is locked to portrait. Add window-size classes, a two-pane drill on the inner screen, and camera-preview behaviour on hinge changes. |
| 3.5 | **Kotlin Multiplatform** (iOS) | Move the classifier, feedback engine and content into shared KMP modules, then add a Compose Multiplatform UI. |

## Sponsor awards: why they aren't in Phase 0

| Award | Requirement | Where we are |
| --- | --- | --- |
| OneSignal "Keep Them Coming Back" | A live store app, plus a description of the OneSignal messaging you deployed | No store listing. The Next Gen track doesn't need one. |
| Samsung "Best App for Galaxy" | A live Galaxy Store listing URL, plus Galaxy-specific work such as foldables | No listing. Store review won't finish before Sep 30. |
| JetBrains "Ship Kotlin Everywhere" | **Kotlin Multiplatform** on several platforms | Handspell is Kotlin on Android only, so it doesn't qualify. |

All three become realistic in Phase 3, when the app launches in a store.

## How the work is split

- **Claude (orchestrator):** plans, writes task specs on `.coord/BOARD.md`, reviews every diff and test run, installs on the test phone, commits and pushes. Claude also takes the work that needs judgement: models and datasets (1.2, 1.4, 2.x, 3.1), the feedback algorithm (1.1), and design review of every new screen against `DESIGN.md`.
- **Codex (executor):** does most of the implementation, one task at a time: UI, wiring, tests and docs. It follows `.coord/PROTOCOL.md`.
- **Cross-checks:** whoever builds something doesn't grade it. Codex re-validates Claude's models, and Claude reviews Codex's UI on the device.
- **Owner:** makes product calls, tests on the device, records the video and submits.

Every task follows the same loop: spec (files, acceptance criteria, test command), then implement, then run `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` until it's green, then review, then install on the device, then commit.
