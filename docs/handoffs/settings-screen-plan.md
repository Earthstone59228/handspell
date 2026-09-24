# Settings screen upgrade — implementation plan

Planning pass only (Opus, 2026-09-23). No code was written. This file is the handoff for whoever
implements it next — read section 7 before writing any UI code.

## 0. Two ground-truth findings that shape the scope

Both contradict assumptions a naive read of the interfaces would make:

1. **`ProgressStore` and `EntitlementGate` are interfaces with no implementation anywhere in the
   repo.** There is no `DataStoreProgressStore`, no RevenueCat `EntitlementGate` impl, no paywall
   screen, and `AppContainer` exposes neither. "Wire Settings to `ProgressStore.clearAll()`" means
   *write the store first*.
2. **`SignDetector` does not expose `HandLandmarks`** — only `feedback: Flow<SignFeedbackState>` and
   `overlay: StateFlow<HandOverlay?>`. `PersonalCalibrationSession.accept()` requires `HandLandmarks`.
   A calibration capture screen therefore cannot reuse the drill pipeline; it must drive
   `HandLandmarkerHelper` + `FrameConverter` directly (like the debug `CaptureScreen.kt`), or the
   `SignDetector` contract must be widened. This is the single most expensive item on the candidate
   list — see §1 on why it's deferred.

Two smaller gaps: `AslColors` in `HandspellTheme.kt` has **no `destructive` token**, though
`docs/DESIGN.md` §1 specifies one (`#B3261E` / `#FF6B61`); and there is no haptics implementation
anywhere, so a haptics toggle would be exactly the non-functional preference the current screen doc
comment rightly refuses.

## 1. Scope for this pass vs. deferred

**In scope (build now):** manual theme override, practice-progress summary + destructive "Delete
practice data", the subscription group wired through `EntitlementGate` with `PaywallSource.SETTINGS`
(including restore, Test Store disclosure, and the cancellation sentence), and About/legal — version,
recognition-model disclosure, open-source licenses, privacy notice. Plus the two prerequisites those
depend on: a new `AppPreferencesStore` and a real `DataStoreProgressStore`.

**Deferred (explicitly, with reasons):** the calibration capture flow and calibration data
management. Three independent reasons, any one sufficient:
(a) it needs its own camera + landmarker + hold-to-record + 8-sample-with-dedupe UI that cannot reuse
`SignDetector` — realistically 1.5–2 days of the ~5 remaining, competing directly with the RevenueCat
integration that judging actually rewards;
(b) per `docs/handoffs/calibration-frontend.md`, `ReloadableKnnLetterClassifier` is deliberately *not*
wired into `AppContainer`, and whether personal exemplars act as fallback / stage-1 opt-in / offline
retrain is an unresolved product decision — an "Improve recognition" feature shipped this week would
demonstrably not improve recognition, which is a trust problem in an app whose whole pitch is honesty;
(c) calibration *management* rows are dead UI if nothing can create calibration data, so they defer
with the capture flow, not separately.
`docs/PRIVACY.md` already says the feature "is not currently exposed" — keeping it that way keeps the
shipped privacy notice true with zero edits. Settings should carry **no** "Improve recognition" row at
all rather than a disabled/coming-soon one (`docs/QUALITY.md` §6 greps for "coming soon").

Why this selection beats the alternatives against judging criteria: the progress group turns
`docs/QUALITY.md` §8's "Delete path — Settings → delete all data actually removes the DataStore file"
from an unmet pass criterion into a demoable one; the subscription group is the only place a judge can
see RevenueCat state and a restore path outside the purchase flow itself, and it carries the mandatory
Test Store disclosure (§2, "non-negotiable"); the theme override closes a documented-but-false claim
in `docs/DESIGN.md`, which is a correctness fix, not polish.

## 2. Screen structure, section by section

Grammar throughout: HIG grouped-inset lists — the existing `SettingsGroup`/`SettingsRow`/
`SettingsDivider` private composables in `SettingsScreen.kt` are already the right pattern and the
right visual language; extend them, do not introduce a new card system. Per the Apple HIG skill:
content leads, chrome recedes — one surface, hairline separators, no nested cards, no shadows,
concentric radii (a control inside a group padded by `Spacing.md` inside an `AslShapes.large` group
takes `AslShapes.small`). Per the frontend-design skill: no ALL-CAPS eyebrow labels for section
headers (use sentence-case `titleMedium` above each group, which is also the HIG grouped-list header),
no "→" on rows, no decorative accent gradients, explanatory copy as a *group footer* in
`labelMedium`/`labelSecondary` rather than a paragraph under every row. Spend the one bit of boldness
on the theme segmented control; everything else stays quiet.

Screen title: keep the existing top bar row; change the page headline from "Your privacy" to
**"Settings"** and move privacy into its own group (copy below).

### Group 1 — Appearance

| | |
|---|---|
| Row | **Theme** |
| Control | Single-row segmented control: `System` / `Light` / `Dark`, full width, ≥48dp tall, each segment `Modifier.selectable(role = Role.RadioButton)` |
| Footer copy | "Light and dark use tuned palettes, not an inversion." |
| Wired to | new `AppPreferencesStore.themeMode` / `setThemeMode(ThemeMode)` |
| States | Always available; selection reflects the persisted value (`System` until the first DataStore emission). Applies immediately, app-wide, no restart. |

### Group 2 — Practice progress

Header: "Practice progress". Rows are read-only summary, then the destructive action at the group's
end (HIG: destructive last, visually separated).

| Row | Value / control | Wired to | States |
|---|---|---|---|
| **Current streak** | "3 days" / "No streak yet" | `ProgressStore.snapshot.currentStreakDays` | loading / empty / value |
| **Letters practised** | "7 of 24" | `letters.count { it.value.attempts > 0 }` vs `Letter.staticLetters.size` | same |
| **Attempts recorded** | "142 attempts, 96 matched" | `letters.values` sums | same |
| **Delete practice data** | Destructive text button row, label in `colors.destructive` | confirmation dialog → `ProgressStore.clearAll()` | idle / confirming / deleting (row disabled) / deleted |

Empty state (nothing practised yet): collapse the three summary rows into one row reading "No practice
recorded yet — every drill you finish is counted here." and **keep the delete row visible but
disabled**, so the privacy promise is still visible and the control does not appear and disappear.
Group footer: "Counts only — no photos, video or camera frames are ever stored. Deleting is permanent
and cannot be undone."

### Group 3 — Handspell Pro

Header: "Handspell Pro". This is the RevenueCat-visible surface.

| Row | Control | Wired to | States |
|---|---|---|---|
| **Status** | Read-only value | `EntitlementGate.status` | `Unknown`/`Loading` → "Checking…"; `Resolved(false)` → "Free — all 24 letter drills included"; `Resolved(true)` → "Pro active"; `Unavailable` → "Can't check right now. Pro content stays locked until this succeeds." + a **Try again** action calling `refresh()` |
| **See what Pro adds** (free) / **Manage subscription** (pro) | Text button row | `requestPaywall(PaywallSource.SETTINGS)` | hidden while status is `Unknown`/`Loading`; see open question §8.1 for the Pro variant |
| **Restore purchases** | Text button row | `EntitlementGate.refresh()` (see §8.2) | idle / working / "Purchases restored" / "Nothing to restore" / "Couldn't reach the store" — all inline, no silent no-op |

Group footer, verbatim requirement from `docs/DESIGN.md` §5 and `docs/QUALITY.md` §2: "Purchases in
this build run through RevenueCat's Test Store. Nothing is charged and no real money changes hands."
plus the one-sentence real cancellation path.

**Degradation requirement:** `AppContainer` must supply *some* `EntitlementGate`. If the RevenueCat
implementation has not landed when this is built, add a `NoopEntitlementGate` returning
`EntitlementStatus.Unavailable(null)` — Settings then renders the honest "can't check" state and the
paywall button is hidden. Never render a button that does nothing.

### Group 4 — Privacy

Keep the three existing rows (`settings_camera_*`, `settings_data_*`, `settings_network_*`) unchanged
— they are good copy and they match `docs/PRIVACY.md`. Add one row:

| Row | Body | Wired to |
|---|---|---|
| **Recognition model** | "Letters are matched by the <model id> model bundled with the app, on this phone. It recognises 24 letters; J and Z need motion, so they are not drilled." | `SignDetector.classifierModelId` (`docs/CONTRACTS.md` line 33 says this belongs in settings), falling back to "not loaded" when null |

### Group 5 — About

| Row | Value | Wired to |
|---|---|---|
| **Version** | "0.1.0 (1)" | `BuildConfig.VERSION_NAME` / `VERSION_CODE` |
| **Open-source licenses** | navigates | new `licenses` route |
| **Privacy notice** | navigates or expands | see §8.4 |

### Group 6 — Debug tools (debug builds only)

Unchanged, still gated on `onOpenCapture != null`. Move it to the very bottom, after About.

## 3. New and changed files

**New**
- `android/app/src/main/java/dev/handspell/app/prefs/AppPreferencesStore.kt` — `ThemeMode` enum +
  `AppPreferencesStore` interface + `DataStoreAppPreferencesStore` (Preferences DataStore named
  `app_preferences`) + an in-memory fake for previews/tests. The only genuinely new persistence
  mechanism this pass needs.
- `android/app/src/main/java/dev/handspell/app/progress/DataStoreProgressStore.kt` — the missing
  `ProgressStore` implementation: one kotlinx-serialization JSON document in a DataStore named
  `progress`, per `docs/ARCHITECTURE.md` §7, schema-versioned by `ProgressSnapshot.SCHEMA_VERSION`,
  unreadable/newer document replaced with a fresh one rather than crashing.
- `android/app/src/main/java/dev/handspell/app/ui/settings/SettingsViewModel.kt` —
  `SettingsUiState` + `SettingsViewModel` + `factory(...)`, following `HomeViewModel.factory` exactly.
- `android/app/src/main/java/dev/handspell/app/ui/settings/SettingsComponents.kt` — promote
  `SettingsGroup`/`SettingsRow`/`SettingsDivider` out of `SettingsScreen.kt` and add
  `SettingsValueRow`, `SettingsActionRow`, `SettingsDestructiveRow`, `ThemeSegmentedControl`,
  `SettingsGroupHeader`, `SettingsGroupFooter`. Needed to stay under `docs/QUALITY.md` §6's
  ~300-line file / ~120-line composable limits.
- `android/app/src/main/java/dev/handspell/app/ui/settings/LicensesScreen.kt` — scrollable license
  text, own route.
- `android/app/src/main/assets/legal/NOTICE.txt` — copy of the repo-root `NOTICE` so it can be read
  at runtime (it is currently outside `assets/`).
- Tests: `progress/DataStoreProgressStoreTest.kt` (JSON round-trip, corrupt-document recovery,
  `clearAll` leaves nothing readable), `prefs/AppPreferencesStoreTest.kt`,
  `ui/settings/SettingsViewModelTest.kt` (state mapping over fake stores + fake gate, including
  empty/unavailable states), and a `ui/theme/ContrastTest.kt` covering the new `destructive` token
  (`docs/QUALITY.md` §4 requires this test and it does not exist yet).

**Changed**
- `ui/settings/SettingsScreen.kt` — becomes `SettingsRoute` (owns the ViewModel, receives nothing
  but nav callbacks) + a pure `SettingsScreen(state, callbacks…)`, per `docs/ARCHITECTURE.md` §5 and
  the `LetterDrillRoute`/`LetterDrillScreen` reference pair. **Delete the KDoc sentence "there are no
  account, telemetry, or camera-storage controls for this app to configure"** and replace it with one
  describing what it now controls; keep the "no telemetry toggle because there is no telemetry"
  spirit as an explicit note so a future agent does not invent one.
- `ui/theme/HandspellTheme.kt` — add `destructive` to `AslColors` (both palettes, values from
  `docs/DESIGN.md` §1); add `themeMode: ThemeMode = ThemeMode.SYSTEM` parameter to `HandspellTheme`,
  resolving `dark = when (mode) { SYSTEM -> isSystemInDarkTheme(); LIGHT -> false; DARK -> true }`.
- `MainActivity.kt` — collect `container.appPreferencesStore.themeMode` with
  `collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)` and pass it to `HandspellTheme`.
- `di/AppContainer.kt` — construct and expose `appPreferencesStore`, `progressStore`,
  `entitlementGate` (real or `Noop`), and expose `signDetector.classifierModelId` indirectly
  (already reachable).
- `ui/HandspellApp.kt` — pass the container-held stores down to the settings composable (or take an
  `AppContainer`-shaped parameter bundle; prefer explicit parameters to match the current style), and
  add **one** new route: `licenses`. Keep `settings` as-is. No `calibration` route this pass.
- `res/values/strings.xml` — ~35 new strings. All sentence case, active voice, no exclamation marks,
  and grep-clean against the `docs/QUALITY.md` §7 banned-phrase list.
- `docs/DESIGN.md` / `docs/PRIVACY.md` — no edits needed if calibration stays deferred;
  `docs/PRIVACY.md`'s "Settings → Delete all data" wording should be reconciled with the actual label
  chosen ("Delete practice data").

## 4. State shape

```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val progress: ProgressSummary = ProgressSummary.Loading,
    val subscription: SubscriptionUi = SubscriptionUi.Checking,
    val restore: RestoreUi = RestoreUi.Idle,
    val pendingDialog: SettingsDialog? = null,      // survives rotation; see §5
    val deletion: DeletionUi = DeletionUi.Idle,
    val classifierModelId: String?,                  // null -> "not loaded"
    val versionName: String,
    val versionCode: Int,
    val showDebugTools: Boolean,
)

sealed interface ProgressSummary {
    data object Loading : ProgressSummary
    data object Empty : ProgressSummary
    data class Recorded(
        val currentStreakDays: Int,
        val longestStreakDays: Int,
        val lettersPractised: Int,
        val lettersTotal: Int,                       // Letter.staticLetters.size
        val attempts: Int,
        val matches: Int,
    ) : ProgressSummary
}

sealed interface SubscriptionUi {
    data object Checking : SubscriptionUi
    data object Free : SubscriptionUi
    data object Pro : SubscriptionUi
    data object Unavailable : SubscriptionUi          // offline / SDK error / Noop gate
}

sealed interface RestoreUi { Idle; InProgress; Restored; NothingToRestore; Failed }
sealed interface DeletionUi { Idle; InProgress; Done }   // Done drives the live-region message
sealed interface SettingsDialog { data object ConfirmClearProgress : SettingsDialog }

class SettingsViewModel(
    private val preferences: AppPreferencesStore,
    private val progressStore: ProgressStore,
    private val entitlementGate: EntitlementGate,
    classifierModelId: String?,
    buildInfo: BuildInfo,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        combine(
            preferences.themeMode,
            progressStore.snapshot,
            entitlementGate.status,
            localUi,                                   // MutableStateFlow of dialog/restore/deletion
        ) { theme, snapshot, status, local -> /* pure mapping fns, unit-testable */ }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(...))

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { preferences.setThemeMode(mode) }

    fun askClearProgress()  { localUi.update { it.copy(dialog = ConfirmClearProgress) } }
    fun dismissDialog()     { localUi.update { it.copy(dialog = null) } }
    fun confirmClearProgress() = viewModelScope.launch {
        localUi.update { it.copy(dialog = null, deletion = InProgress) }
        runCatching { progressStore.clearAll() }
        localUi.update { it.copy(deletion = Done) }     // summary re-emits Empty from the store flow
    }

    fun openPaywall() = entitlementGate.requestPaywall(PaywallSource.SETTINGS)
    fun restorePurchases() = viewModelScope.launch { /* see §8.2 */ }
    fun retryEntitlement() = viewModelScope.launch { runCatching { entitlementGate.refresh() } }
}
```

Data flow: stores own truth, ViewModel is a pure projection plus a small local-UI flow, screen is
state + lambdas. `DeletionUi.Done` is state rather than a one-shot event on purpose — the deletion
confirmation must remain readable by TalkBack, not flash past; reset it on the next user interaction.
Navigation and paywall presentation stay one-shot and go through the existing NavHost callbacks /
`EntitlementGate.paywallRequests`, per `docs/ARCHITECTURE.md` §5.

## 5. Destructive-action UX

**Delete practice data**
- Trigger: a row, not a filled button. Label "Delete practice data" in `colors.destructive`, ≥48dp,
  at the bottom of its group, separated from the summary rows by a divider.
- Confirmation: Material 3 `AlertDialog`, unavoidable (no swipe-to-delete, no undo snackbar — there
  is nothing to undo).
  - Title: "Delete practice data?"
  - Body: "This removes your letter counts, story progress, speed-run results and streaks from this
    phone. It can't be undone." — name the contents exactly, matching `ProgressSnapshot`'s fields and
    `docs/PRIVACY.md`'s list.
  - Confirm: **"Delete"** in `colors.destructive`. Dismiss: **"Cancel"**, normal styling, equal
    weight. Confirm is not pre-focused.
  - Verb consistency (frontend-design skill): the row says Delete, the dialog says Delete, the result
    says "Practice data deleted."
- During deletion: row disabled, label unchanged (no spinner for a sub-100ms DataStore write; if it
  fails, show "Couldn't delete — try again" inline in `destructive`, never a silent catch, per
  `docs/QUALITY.md` §3).
- After: the summary rows re-render as the Empty state from the store flow, and an inline
  `liveRegion = Assertive` text "Practice data deleted." appears under the group.
- Rotation: hold `pendingDialog` in the ViewModel (as above) so the dialog survives configuration
  change; do not use a `remember { mutableStateOf }` in the composable.
- **Do not combine** this with any calibration deletion — `docs/handoffs/calibration-frontend.md`
  requires the disclosures stay separate. When calibration lands later it gets its own dialog with
  its own body text ("up to 24 normalized handshape measurements per letter"), and a per-letter
  variant ("Delete B calibration?"), both inside the Improve recognition screen, not here.

## 6. Accessibility checklist for the new controls

- **Theme segmented control:** implement as a `Row` of three `Modifier.selectable(selected, role =
  Role.RadioButton)` items, each `sizeIn(minHeight = Spacing.touchTarget)` and `weight(1f)` — not a
  custom canvas. Wrap the row in `Modifier.selectableGroup()` so TalkBack announces "1 of 3".
  Selection must be conveyed by more than the accent fill: selected segment gets the filled
  background **and** medium weight **and** `stateDescription`. At 200% font scale the three labels
  must wrap or the control must fall back to a vertical list — verify at `fontScale = 2.0` and 320dp
  width, no horizontal scroll (`docs/QUALITY.md` §4).
- **Every row** ≥48dp (`Spacing.touchTarget`) with ≥8dp between adjacent interactive rows;
  non-interactive value rows must not be clickable at all (no fake affordance).
- **Value rows** merge title and value into one semantics node
  (`Modifier.semantics(mergeDescendants = true)`) so TalkBack reads "Current streak, 3 days", not two
  separate stops. The letters row reads "Letters practised, 7 of 24".
- **Content descriptions:** no new icons are required by this plan — prefer text-only rows, which
  sidesteps the whole `contentDescription` lint surface. If a chevron is added to navigating rows it
  must be `contentDescription = null` with a comment saying it is decorative (the row label carries
  the meaning).
- **Live regions:** the post-deletion message and the restore-result message are the only ones.
  Deletion → `LiveRegionMode.Assertive` (it is a confirmation of an irreversible act). Restore result
  → `Polite`. Nothing else on this screen announces; do not make the subscription status row a live
  region or it will chatter on every entitlement refresh.
- **Contrast:** `destructive` on `surface` must be ≥4.5:1 in both palettes — add it to the token
  contrast test. Destructive red is the only red on the screen; do not use `feedbackAdjust` or red for
  the "can't check entitlement" state, which is neutral information, not an error the user caused
  (`docs/DESIGN.md` §1).
- **Dialog:** focus moves into the dialog on open and back to the trigger row on dismiss (Compose
  `AlertDialog` does this; verify with TalkBack). Dialog body text at `bodyLarge` (17sp floor), not
  `labelMedium`.
- **Motion:** the theme change must not animate a cross-fade of the whole app; it is an instant
  recomposition. Any group expand/collapse must honour reduced motion.
- **Both palettes:** screenshot every group in light and dark, plus greyscale for the destructive row
  (it must still read as destructive from its position and label, not only its colour).

## 7. Before you implement — required reading

> **To the implementing agent: load both of these skills before you write any Settings UI code, and
> follow them.**
>
> 1. **Apple HIG skill** — invoke as `anthropic-skills:apple-hig`; source at
>    `/home/earthstone/.claude/skills/synced/6bf1c47d-9c0f-4ec0-8da6-12ac256105ef_587757e8-f7ec-4b97-a1a0-fbff2bbdccbf/apple-hig`.
>    Read `SKILL.md`, and read `references/patterns.md` before finalising the grouped-list and
>    dark-mode behaviour. Take from it: the grouped-inset list grammar (single surface, hairline
>    separators, section header above / explanatory footer below — not a paragraph per row), "content
>    leads, chrome recedes" (no nested cards, no shadows, no decorative fills), concentric corner
>    radii, semantic-colour-only, the 44pt/48dp tap-target floor, and motion that orients rather than
>    decorates. **Do not take its CSS tokens, glass/blur materials, systemBlue, or any iOS chrome** —
>    this is Compose on Android and `docs/DESIGN.md` explicitly forbids an iOS look-alike.
> 2. **Frontend design skill** — invoke as `frontend-design:frontend-design`; source at
>    `/home/earthstone/.claude/plugins/marketplaces/claude-code-plugins/plugins/frontend-design/skills/frontend-design`.
>    Take from it: the copywriting discipline (plain active voice, one job per string, the same verb
>    through a whole flow, errors that say what happened and what to do, empty states as invitations)
>    and the list of generated-design tells to avoid — specifically no ALL-CAPS eyebrow labels above
>    sections, no "→" appended to row labels, no middle-dot meta strings, no identical-card kit, no
>    gradient washes.
>
> **Non-negotiable constraint over both:** the visual language is already decided by
> `/mnt/drive/Work/Project/Shipaton/android/app/src/main/java/dev/handspell/app/ui/theme/HandspellTheme.kt`
> and `docs/DESIGN.md`. Use only `Spacing`, `AslShapes`, `AslText`/Material typography slots,
> `AslMotion` and `LocalAslColors`. **No raw `dp`, hex or duration literal may appear in any screen
> file** — `docs/QUALITY.md` enforces this. If you need a colour that does not exist (you will need
> `destructive`), add it to `AslColors` with the value already specified in `docs/DESIGN.md` §1 and
> cover it with the contrast test; do not inline it. Neither skill's palette, font stack or component
> CSS is to be imported.

## 8. Open questions needing the project owner's sign-off

1. **"Manage subscription" behaviour for an active Pro user.** `purchases-ui` is deliberately not a
   dependency (`docs/ARCHITECTURE.md` §9), so RevenueCat's Customer Center is not available. Options:
   (a) relaunch the in-app paywall showing current plan; (b) fire an `Intent` to the Play Store
   subscriptions page — but on a Test Store build there is no Play subscription, so that page would
   be empty and misleading; (c) a plain text row stating the cancellation path with no button.
   Recommendation: **(c) for Test Store builds**, since it is the only honest option, with the copy
   naming the real path. Needs a decision because it changes what `PaywallSource.SETTINGS` is used
   for.
2. **Restore purchases has no interface.** `EntitlementGate` exposes only `refresh()`; RevenueCat's
   `restorePurchases()` is a distinct call with distinct results ("nothing to restore" vs
   "restored"). Either add `suspend fun restorePurchases(): RestoreResult` to `EntitlementGate`
   (recommended — it is the only purchases abstraction, per its own KDoc) or drop the restore row
   this pass. Adding to the interface may collide with whoever owns the billing workstream; confirm
   ownership first.
3. **Does "delete" have to remove the file, or just the data?** `ProgressStore`'s KDoc says clearAll
   "must leave no file behind" and `docs/QUALITY.md` §8 says "actually removes the DataStore file".
   Deleting a Preferences DataStore file out from under an open instance is unsafe; the safe
   implementation is `edit { clear() }`, which leaves a zero-content `progress.preferences_pb`.
   Recommendation: implement `edit { clear() }` and soften the doc wording to "removes all stored
   practice data". Needs sign-off because it edits a stated commitment.
4. **Privacy notice presentation.** `docs/PRIVACY.md` is a repo file, ~2 pages. Options: bundle it as
   an asset and render it in a `privacy` route (no network, fully offline, recommended); or link out
   to GitHub (an outbound browser intent — allowed, but the app currently contacts no host but
   `api.revenuecat.com` and a link invites questions); or leave only the four in-app privacy rows.
   Recommendation: bundle as an asset, reusing the same scrollable screen as the licenses route.
5. **Is calibration genuinely deferred?** This plan assumes yes. If the owner wants the "Improve
   recognition" story visible for judging, that is a separate ~2-day workstream that also requires
   deciding how personal exemplars affect classification (handoff doc's (a)/(b)/(c)) — it should not
   be squeezed in alongside this pass, and it would need a `calibration` NavHost route plus either a
   `SignDetector` contract change or a second landmarker analyzer.
6. **Where does `ProgressStore` recording get wired?** This pass creates the store and the
   read/delete surface, but nothing calls `recordAttempt` — so the summary will read "No practice
   recorded yet" on a real device until the drill screen is wired. Confirm whether the same agent
   should also wire `DrillViewModel` → `recordAttempt`, or whether the Settings summary ships showing
   the empty state.

### Critical files for implementation
- `android/app/src/main/java/dev/handspell/app/ui/settings/SettingsScreen.kt`
- `android/app/src/main/java/dev/handspell/app/ui/theme/HandspellTheme.kt`
- `android/app/src/main/java/dev/handspell/app/di/AppContainer.kt`
- `android/app/src/main/java/dev/handspell/app/progress/ProgressStore.kt`
- `android/app/src/main/java/dev/handspell/app/ui/HandspellApp.kt`
