# Design

Apple HIG *principles* and *token discipline* applied to Jetpack Compose on Android. We are not
building an iOS look-alike: no iOS tab bar, no back-chevron-with-label, no SF Pro, no SF Symbols
(licensing), no systemBlue. What we take is the part that is actually good — content leads and chrome
recedes, one spacing scale, one type scale, semantic adaptive color, brief purposeful motion, and a
hard floor on tap targets.

## 1. Token table

All tokens live in `ui/theme/`. Nothing in a screen file may contain a raw `dp`, hex or duration.

### Spacing — `Spacing` object, 4/8 grid

| Token | dp | Typical use |
|---|---|---|
| `xxs` | 4 | icon-to-label gap |
| `xs` | 8 | inside a chip |
| `sm` | 12 | list row vertical padding |
| `md` | 16 | **screen horizontal margin (compact)** |
| `lg` | 20 | screen margin at ≥600dp width |
| `xl` | 24 | between content groups |
| `xxl` | 32 | above a screen's primary action |
| `xxxl` | 48 | hero spacing on onboarding |

### Type — Material 3 `Typography` slots filled with the HIG ladder, plus named aliases

| HIG role | sp / line | Weight | Compose slot | Alias |
|---|---|---|---|---|
| largeTitle | 34/41 | Bold | `displaySmall` | `AslText.largeTitle` |
| title1 | 28/34 | Bold | `headlineMedium` | `AslText.title1` |
| title2 | 22/28 | Bold | `headlineSmall` | `AslText.title2` |
| title3 | 20/25 | Medium | `titleLarge` | `AslText.title3` |
| headline | 17/22 | Medium | `titleMedium` | `AslText.headline` |
| body | 17/22 | Regular | `bodyLarge` | `AslText.body` |
| callout | 16/21 | Regular | `bodyMedium` | `AslText.callout` |
| subhead | 15/20 | Regular | `bodySmall` | `AslText.subhead` |
| footnote | 13/18 | Regular | `labelMedium` | `AslText.footnote` |
| caption | 12/16 | Regular | `labelSmall` | `AslText.caption` |

17sp body is the legibility floor; nothing a user reads goes below 13sp. All sizes in `sp`, never
`dp`, so Android font scaling works. Layouts must survive 200% font scale — see §7.

**Font: Roboto, the platform default (`FontFamily.Default`).** Zero APK bytes, no license question,
already tuned for Android rendering, and it dodges both traps — it is neither an SF Pro knock-off nor
Inter, which the quality rubric lists as a generic-AI-template tell. HIG's semibold (600) maps to
Roboto Medium (500); Roboto has no 600.

**Icons: Material Symbols Rounded (Apache-2.0), imported as individual vector drawables.** Only the
~12 glyphs we actually use, committed as XML, attributed in `NOTICE`. Not
`material-icons-extended` — it is a large dependency for a handful of icons. Rounded, not Outlined,
because it sits better beside Roboto and reads warmer for a learning app.

### Color — semantic, adaptive, defined once per mode

| Token | Light | Dark | Contrast on its background |
|---|---|---|---|
| `label` | `#0A0A0B` | `#F2F3F5` | 20.1:1 / 19.0:1 |
| `labelSecondary` | `#55595F` | `#B6BBC2` | 7.0:1 / 10.9:1 |
| `labelTertiary` | `#6E7278` | `#8A8F96` | 4.9:1 / 6.5:1 (metadata only) |
| `background` | `#FFFFFF` | `#000000` | — |
| `backgroundGrouped` | `#F2F3F5` | `#101113` | — |
| `surface` | `#FFFFFF` | `#1B1D20` | — |
| `separator` | `#D3D6DA` | `#2E3136` | — |
| `accent` | `#0A6C74` | `#4FD1D9` | 6.2:1 / 11.5:1 |
| `feedbackMatch` | `#0E6F3C` | `#3DDC84` | 6.3:1 / 11.7:1 |
| `feedbackAdjust` | `#8A5A00` | `#FFC65C` | 5.9:1 / 13.6:1 |
| `feedbackNeutral` | `#55595F` | `#B6BBC2` | 7.0:1 / 10.9:1 |
| `destructive` | `#B3261E` | `#FF6B61` | 6.5:1 / 7.5:1 |

Deep teal accent, deliberately not systemBlue and deliberately not the purple→pink gradient the
rubric flags. **Red is reserved for destructive actions only** — "not recognised" is neutral grey,
never red. A learner holding a hand the model cannot read has not done anything wrong, and the model
is more likely to be at fault than they are. Every listed pair is ≥4.5:1; `labelTertiary` is capped
at metadata use because it is the only one near the line.

Ratios are computed against `background` in each mode and re-verified in CI (§ QUALITY.md).
Exposed as a `CompositionLocal` (`LocalAslColors`) alongside a Material 3 `ColorScheme` built from the
same values, so Material components inherit correctly. Dark mode follows the system, with a manual
override in Settings.

### Shape and motion

Radii: `extraSmall` 8, `small` 12, `medium` 16, `large` 20, `extraLarge` 28, plus `full` for pills.
Nested shapes are concentric: a child's radius is the parent's radius minus the padding between them.

Durations `quick` 200ms, `standard` 350ms, `slow` 500ms. Easing `standard` =
`CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)`, `emphasized` = `CubicBezierEasing(0.16f, 1f, 0.3f, 1f)`.
Motion answers "what just happened" or it is cut. `LocalReduceMotion` reads
`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`; when true every duration collapses to 0 and the
match animation becomes an instant state change.

**Minimum tap target 48dp** (Android's floor, stricter than HIG's 44pt) enforced with
`Modifier.minimumInteractiveComponentSize()` or explicit `sizeIn`, even when the drawn icon is 24dp.

## 2. Screens

Navigation: Compose Navigation, three peer destinations in a Material 3 `NavigationBar` — Practice,
Progress, Settings. A bottom bar because there are genuinely three co-equal sections, not because iOS
has one.

| Screen | Purpose |
|---|---|
| Onboarding | Three cards: what the app is (a fingerspelling practice aid, not an authority), that J and Z are excluded and why, and the camera-permission rationale — then the system prompt. |
| Camera permission rationale | Shown before the system dialog and again after a denial: "Handspell uses the front camera to see your hand shape. Frames are processed on your phone and never recorded, saved or uploaded." Denied-permanently state offers a Settings deep link and a browse-only mode. |
| Home (Practice) | Letter grid of the shipped letters plus the pack list (free and Pro, Pro marked with a lock and a price-free "Pro" chip). |
| Letter drill | The core loop: camera, prompt, live three-state feedback, hint, next letter. Free. |
| Story lesson | A pack of narration and spell-a-word beats, progress along the story. Pro. |
| Speed challenge | Timed round: spell as many prompts as possible, score, best score. Pro. |
| Progress | Per-letter attempts/matches, streak, recent speed runs, and a clear "delete all my data" action. |
| Paywall | Our own Compose screen (not a RevenueCat template): what Pro adds, the two packages with price and period, purchase, restore, an equal-weight "Not now", and the Test Store disclosure. Rules in §5. |
| Settings | Theme, subscription status, model status, and the delete-data control. |
| Documents | Privacy notice, project license, third-party notices and hand-model license, opened from the Alphabet paper icon. |
| Dev capture (debug only) | Pick signer id and letter, record landmark CSV, live overlay, frame counter. Never in a release build. |

## 3. Three-state feedback — never color-only

Each state differs in **shape, icon, text and color** simultaneously, so it reads correctly in
greyscale, at any contrast setting, and to someone who is colorblind.

| State | Shape | Icon | Text | Color | Haptic |
|---|---|---|---|---|---|
| `NoHand` | dotted outline circle | open hand | "Show your hand to the camera" | `feedbackNeutral` | none |
| `NotRecognized` | dashed outline circle | question mark | "Not sure yet — try turning your hand toward the camera" | `feedbackNeutral` | none |
| `Adjust` | solid ring filling clockwise with `holdProgress` | arrows-adjust | "Close. " + the authored hint | `feedbackAdjust` | none |
| `Match` | filled circle, 1.0→1.08→1.0 scale over `quick` | check | "That's <letter>" | `feedbackMatch` | one `CONFIRM` tick |

Rules: `Match` appears only after the hold-to-confirm window; the ring filling is the only thing that
moves before then, and it is honest — it shows real accumulated hold, not a fake progress animation.
No sound, ever: this app is for Deaf and hard-of-hearing users among others, and audio feedback would
be both useless and insulting. No "wrong" / "incorrect" / red X anywhere.

## 4. Camera layout

Portrait only. `PreviewView` fills the width at 3:4, top-aligned, corner radius `extraLarge`, front
camera, **mirrored** so the user sees themselves as in a mirror. The landmark overlay draws on top in
`accent` at 60% alpha, 2dp strokes. Below the preview, in order: the prompt letter (largeTitle), the
feedback badge from §3, the authored handshape description (callout, `labelSecondary`), and a
48dp-tall "Skip this letter" text button. Primary actions sit within thumb reach at the bottom; the
camera never sits under a scrim or a sheet while the user is expected to be signing.

Low-light: if the landmarker returns no hand for 5 seconds while the frame's mean luma is low, show a
one-line "It's a bit dark — more light will help" notice. It is dismissible and does not return for
the rest of the session.

## 5. Paywall copy rules

No dark patterns. Concretely, and checkable:

- Price, billing period and renewal terms are visible **before** the purchase button, in `body`, not
  in a footnote.
- The dismiss control is a full-width, 48dp, normally-styled text button reading "Not now". It is not
  a 12sp grey link and it is not below the fold. No confirmshaming — never "No thanks, I'll stay slow".
- No countdown timers, no "X people upgraded today", no fabricated scarcity, no pre-checked anything.
- The free tier is described accurately: all 24 letter drills are free, forever, and the paywall says
  so. Pro is story lessons and speed challenges, plus the packs added after them.
- Cancellation is explained in one sentence with the actual path, on both the paywall and Settings.
- **Test Store disclosure.** Every build we ship or demo uses RevenueCat's Test Store, so no money
  changes hands. The paywall header and the Settings subscription row both say this in plain words.
  Pretending otherwise would be the exact bait-and-switch the rubric names.
- One offering, two packages (monthly, annual), annual's saving stated as a real percentage of the
  monthly price. No third "decoy" tier.

## 6. Voice

Sentence case everywhere, including buttons ("Start practising", not "Start Practising"). Short verb
phrases. Write to the person: "Show your hand to the camera", not "No hand detected". No exclamation
marks, no "unlock the power of", no "seamless", no emoji in headings. The app describes itself as a
fingerspelling practice aid and links out to Deaf-led instruction; it never claims to certify,
validate or teach "correct" ASL.

## 7. Accessibility

- Every icon, image and the camera preview has a `contentDescription`; decorative dividers use
  `null`. The letter grid tiles read as "Letter A, practised 6 times", not "A".
- The feedback badge is a single node with `liveRegion = LiveRegionMode.Polite`, announcing only on
  state *change*, not per frame — otherwise TalkBack would talk over itself 30 times a second.
- Text contrast ≥4.5:1 (≥3:1 for ≥22sp and for icons that carry meaning); §1 lists measured values.
- All targets ≥48dp with ≥8dp between adjacent ones.
- Layouts survive 200% font scale and a 320dp-wide screen: no fixed-height text containers, no
  horizontal scroll. Verified on the Pixel "small phone" preview and with `fontScale = 2.0`.
- `LocalReduceMotion` honoured by every animation, including the match pulse.
- Dark mode is a tuned palette, not an inversion, and is tested on both modes for every screen.
- **Honest limit**: the core loop is visual — a blind user cannot practise a handshape by watching
  feedback they cannot see. We do not claim otherwise. Navigation, progress, settings, privacy and the
  paywall are fully TalkBack-operable, which is the part that must not be excluded.
