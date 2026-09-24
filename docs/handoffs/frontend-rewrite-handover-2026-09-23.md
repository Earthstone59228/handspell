# Handspell frontend rewrite — handover (2026-09-23)

## User request and working location

The user explicitly clarified that **every app screen** is in scope, not just Settings. They then
asked to stop implementation now, set Codex to the maximum context window, and write this handover
for the next agent. Resume the full frontend rewrite from the checkout below.

- Original project: `/mnt/drive/Work/Project/Shipaton` — normal writes were rejected as read-only,
  but an approved elevated copy succeeded. All 28 changed/new checkout files were synced there.
- Writable working clone with all changes: `/home/earthstone/Shipaton-rewrite`.
- Original handoff plan: `docs/handoffs/settings-screen-plan.md` in both locations. Its Settings
  structure and constraints remain useful, but the user's newer instruction expands scope to all
  existing app screens.
- Knowledge base: `/mnt/drive/Claude/`. Read
  `memory/shipaton-settings-screen-plan-2026-09-23.md`,
  `memory/shipaton-orchestration-prefs.md`, and `projects/shipaton-2026-guidelines.md`. Add a dated
  build-log entry and a memory update when writing becomes possible.
- `~/.codex/config.toml` now has `model_context_window = 1050000` and
  `model_auto_compact_token_limit = 1000000` for `gpt-6-sol`. TOML parsing verified. A new Codex
  session may be needed for the change to take effect; this session's runtime context may still be
  controlled by the host.

## Required design sources

Read before more UI code:

1. `docs/DESIGN.md`, `docs/QUALITY.md`, `docs/PRIVACY.md` in the checkout.
2. Apple HIG skill at
   `/home/earthstone/.claude/skills/synced/6bf1c47d-9c0f-4ec0-8da6-12ac256105ef_587757e8-f7ec-4b97-a1a0-fbff2bbdccbf/apple-hig/SKILL.md`
   and its `references/patterns.md`.
3. Frontend design skill at
   `/home/earthstone/.claude/plugins/marketplaces/claude-code-plugins/plugins/frontend-design/skills/frontend-design/SKILL.md`.

Use HIG principles, not iOS chrome or CSS tokens. Compose screens use the existing `Spacing`,
`AslShapes`, `AslText`/Material typography, `AslMotion`, and `LocalAslColors` only. No raw dp, hex,
or duration literals in screen files. Avoid gradients, shadows, nested cards, all-caps eyebrow
labels, arrows appended to row labels, and filler copy. Keep 48dp tap targets, both palettes, 200%
font scale, and TalkBack semantics in view.

## Work already in the writable checkout

- Rebuilt Settings as grouped sections: Appearance (persisted System/Light/Dark), practice summary
  and confirmed deletion, Handspell Pro status/restore/Test Store disclosure, privacy/model
  disclosure, About/legal, and debug tools in debug builds.
- Added `DataStoreAppPreferencesStore`, `DataStoreProgressStore`, Settings ViewModel/components,
  offline legal assets, and `destructive` palette tokens.
- Added RevenueCat Test Store entitlement gate, restore, offerings/purchase interface, and a basic
  custom Compose paywall route. With no key, an honest `NoopEntitlementGate` hides dead actions.
  The original project's `android/local.properties` has a RevenueCat key; the clone does not.
- Wired confirmed drill matches and attempted skips to progress persistence.
- Rebuilt Practice home with a featured next letter and progress-aware letter grid. This was the
  first step after the user expanded scope.
- Reconciled deletion wording in `docs/ARCHITECTURE.md`, `docs/QUALITY.md`, and `docs/PRIVACY.md`.
  `clearAll()` atomically clears DataStore preferences; it does not physically unlink the open file.
- Removed the unfinished contact placeholder from `docs/PRIVACY.md` and bundled it as
  `assets/legal/PRIVACY.md`.

The checkout is **uncommitted**. `git status --short` shows the full changed/new file list.
`./gradlew :app:compileDebugKotlin --offline --no-daemon` passed on 2026-09-23 after the Home
rewrite. In this sandbox, Gradle needs `exec_command` with `sandbox_permissions=require_escalated`
because its file-lock service cannot bind a wildcard IP otherwise. No unit tests, lint, release
build, emulator run, screenshots, or device check have been done for these changes.

## What the next agent should do

1. **Continue the user-authorized full-screen rewrite.** Practice and Settings are partly rebuilt;
   the camera drill still has its old visual layout, the paywall and legal screens are functional but
   need design review, and debug capture still uses old layout. Review every reachable app screen in
   both palettes at 320dp and 200% font scale. Keep camera preview/feedback semantics and the
   four-state feedback model intact. The app currently has no separate Progress, story lesson, or
   speed challenge routes despite `docs/DESIGN.md` describing them; decide how much of those missing
   flows is needed to fulfill the broader request, and avoid dead navigation.
2. **Verify behavior.** Add focused tests for progress persistence/deletion and Settings state
   mapping; run `testDebugUnitTest`, lint, debug and release builds. Review the RevenueCat Test Store
   API use against the pinned 10.21.1 AAR and official docs. Test actual purchases and restores with
   the original project's local key on a device. Check that Pro stays locked on fetch errors.
3. **Review known risks.** `PaywallScreen.kt` is a first pass: inspect above-the-fold pricing/dismiss
   at small sizes and enforce renewal copy directly beside plan pricing. Its annual saving is not
   shown yet. `DataStoreProgressStore` uses UTC day boundaries for streaks; decide whether local-day
   boundaries are required. `DrillViewModel` records one confirmed match per letter and a skip only
   after a hand was seen. Confirm these count semantics with the product copy. The bundled privacy
   Markdown is rendered as plain paragraphs; improve heading/list presentation without adding a
   network dependency. `HomeScreen.kt` has not had visual screenshot review.
4. **Continue in the original project or writable clone.** The synced original now contains the
   same uncommitted code and this handover. Normal shell writes to `/mnt/drive` fail, but an approved
   elevated copy worked. The next agent can edit `/home/earthstone/Shipaton-rewrite` and sync again
   if necessary. Preserve original untracked `docs/handoffs/settings-screen-plan.md` and `docs/logs/`.
   Do not assume a `git push` to the file-path origin will work while normal writes are blocked.

## Original Settings-plan decisions applied

- Calibration remains deferred: raw `HandLandmarks` are not exposed by `SignDetector`, and the
  personal-exemplar product decision is unresolved. There is no disabled "Improve recognition" row.
- For Test Store builds, active Pro shows status plus the cancellation path; it does not open an
  empty Play subscription page.
- `EntitlementGate` now has a real restore method; the RevenueCat implementation calls
  `restorePurchases()`, rather than using `refresh()` as a fake restore.
- Privacy notice is bundled offline. Deletion clears DataStore content in place for safety.
- Confirmed drill matches and attempted skips now record progress, so Settings need not ship as a
  permanently empty summary.
