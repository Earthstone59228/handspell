# Design continuity audit, 2026-09-30

Scope: every screen of the Android app, web Letters page (Ionic WebView) vs native Compose. Screenshots (before and
after, dark and light) are in the session scratchpad `continuity-shots/`. Status is after the three commits on `main`.

## Decision: one card surface

Dark-mode cards (Home Letters/Words entries, Letters list, Words list, buttons of the "card" style) now use the
`Slate` surface (`#2C2C2E`, token `card`, equal to `surface`) with `Paper` text, the same fill the Progress,
Settings, Pro and Documents rows already used. Reasons: it is the majority of the app, it is what `AslPalette` documents
("one lighter grey for cards and menus so they read as raised without turning white"), and near-white slabs on a dark
screen were the loudest seam on Home and in both lists. Light mode is unchanged (white cards on Paper). The one
deliberate exception is the **media tile** (sheet and reward letter/word example, token `tile`): it stays
Paper/White in dark mode because the hand wireframe and word figure are drawn for a light ground, like a photo mat.
The web page mirrors the tokens as CSS variables (`--card`, `--on-card`, `--on-card-2`, `--on-card-accent`,
`--card-line`) with a `html.theme-light` override.

## Findings

| # | Area | Problem (before) | Status |
|---|---|---|---|
| 1 | Words header | Flat 94%-alpha bar, no blur; chevron/title/gear in a rounded pill and the "2 / 12" counter in a separate bar | Fixed: `FrostedHeaderScaffold` + `FrostedHeaderContent` (one block, real blur API 31+, solid below) |
| 2 | Header on Documents, Paywall, Pro pages, Speed pause/result, Practice (pack list) | Flat, unblurred `ScreenHeader`, chevron row above a separate title | Fixed: all use the shared frosted header |
| 3 | Header layout | Settings/Progress/Speed stacked chevron above title; Letters/Words had chevron and title on one row | Fixed: chevron, title, icons on one row everywhere (as web `.top-area`); optional description or counter under it |
| 4 | Card colour | Near-white cards on Home (Letters/Words), Words list, Letters list vs dark slate elsewhere | Fixed (decision above), native and web |
| 5 | Web vs native list card | Same size (100), radius (20), thumb (58 box, 13 radius) already; list gap 12 vs 11, "complete" colour and meta grey differed | Fixed: gap 11, meta and done colours from tokens |
| 6 | Screen gutter | Grouped screens 16, Words/Letters 20, titles 20 | Fixed: content gutter 20 on every frosted screen, so cards align with the title |
| 7 | Quiet text action | Web "Mark as not done" underlined; native plain | Fixed: web underline removed |
| 8 | Sheet secondary button | Web "Mark complete" always Paper; native card style | Fixed: both use the card token |
| 9 | Reward sheet tile | Used the card colour | Fixed: uses `tile`, like the letter/word sheet |
| 10 | Camera drills (letter, word, story, speed round) | Compact 22sp title beside chevron, unblurred | Skipped, intentional: camera screens need the height and have no scrolling content under the bar |
| 11 | Web header vs native | Web counter row is 48dp tall with a streak chip; native counter line is tighter, and web has a 66px right gutter for the A-Z rail | Skipped: rail exists only on Letters; counter spacing left as is |
| 12 | Light-mode cards | Web light cards have a 1px ring, native light cards do not | Skipped: low visibility |
| 13 | Section labels | Eyebrow labels on Settings/Progress/Speed; Documents sections use 22sp headings, Words "Included with Pro" uses 17sp medium | Skipped: different levels of hierarchy, not a seam |
| 14 | Lock/Pro badge | Lock plus "Pro" label on cards, Pro eyebrow on promo cards | Consistent, no change |
| 15 | Web sheet vs native sheet | Both: 28 radius, hairline top border, X close, big tile, Practice button | Consistent, no change |

## Not verified

Below API 31 (solid fallback) was not exercised on a device; the device runs API 31+.
