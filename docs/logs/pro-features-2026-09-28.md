# Pro features implementation — 28 September 2026

Pro now exposes the existing three story packs and three timed speed rounds through native Practice. The alphabet's Practice action opens that catalogue. All 24 static-letter drills remain free; J and Z stay excluded.

## Behaviour

- Fixed the bundled story parser: narration beats omit `letters`, so the previous parser rejected all three stories. Each story now loads five narration beats and five word prompts. Parsing checks word/prompt agreement, static letters, unique item IDs and playable speed-round settings. Pack kind/tier must match the index.
- Pack loading, missing content, empty packs and unavailable classifier letters have explicit screens. Read failures can be retried. Lessons are local assets and require no content download.
- RevenueCat access checks show loading, offline/error with retry, unconfigured and free states. A pack remains in the navigation stack while its entitlement refreshes or its paywall opens. Pro packs remain visible when billing is unavailable; that state is explained. No synthetic entitlement or billing bypass was added.
- Story sessions resume from the first incomplete saved step. A partly spelled word survives activity recreation. Skipping any letter leaves that word incomplete. Replay changes the camera-session key. Failed progress writes stay pending with a retry action.
- Speed rounds show duration, authored practice target, prompt letters and best stored score. Camera permission precedes the clock. Permission denial exposes app Settings. A monotonic clock prevents device-clock changes or late match callbacks from extending a round. Pause/resume, background pause, unfinished-round exit confirmation, zero-score results, save status and retry are supported. Restored unfinished rounds stay paused. Completed result IDs remain stable across retries; DataStore suppresses duplicate records.
- Story/speed camera screens reuse the existing drill, with its extra header hidden. Skips remain available. Scores count matches rather than grade ASL correctness.
- Settings has the alphabet's pinned frosted hero treatment. It samples the actual scrolling rows through local Compose graphics layers on Android 31+. Older versions use a solid dark header. No stock image, remote asset or new palette was introduced.
- The paywall describes the shipped three stories/three rounds, makes no monthly-content promise, requires intentional plan selection, retries empty offerings and shows active store work. Price/period/renewal, Restore and the full-width Not now control remain present. The Test Store disclosure remains explicit: no money is charged.

## Validation

Ran `source ../env.sh` and `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin --offline --console=plain` from `android/`.

Result: BUILD SUCCESSFUL; 100 unit tests, zero failures and errors. This includes four content-parser regressions and six session tests covering skipped/stale callbacks, resume, write retries, deadlines, pause and zero-score results. The parent's all-24 bundled classifier test ran and passed.

The initial Gradle sandbox launch could not open its local daemon socket. Its first automatic approval review timed out; the permitted retry succeeded. Small paywall/camera-denial UI refinements were added after this test run and are included in the parent's consolidated lint/APK checks.

No Android device or emulator was connected. Native rendering, TalkBack, large-font layouts, camera permission/lifecycle interaction and RevenueCat Test Store purchase/restore have not been exercised on a device. Recognition remains experimental; R/T/U are less reliable in the parent's held-out data checks. Nothing here establishes release readiness or ASL certification. Legal-owner/contact completeness is handled by the separate audit.

## Files

- `content/ContentPackParser.kt` and `content/AssetContentRepository.kt`
- `ui/home/ProPackScreens.kt` and `ui/home/ProSessionViewModels.kt`
- `ui/HandspellApp.kt`
- `ui/settings/SettingsScreen.kt`, `FrostedSettingsHero.kt` and `PaywallScreen.kt`
- `progress/DataStoreProgressStore.kt`
- `res/values/pro_features.xml`
- `test/.../content/ContentPackParserTest.kt` and `test/.../ui/home/ProSessionViewModelsTest.kt`

Paths above are relative to the relevant Android source-set directories. Parent-owned `LetterDrillRoute` adds the `showHeader` argument used by Pro screens.
