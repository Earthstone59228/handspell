# Device QA checklist: Galaxy S25 Ultra, Android 16

Run this on the S25 Ultra only. Mark each item Pass or Fail; on Fail, write what you saw in Notes.

- Build: `debug-20260929-27852be` (or later; put the commit here: ______)
- Installed from: [ ] APK download  [ ] `adb install`
- Tester / date: ______
- RevenueCat Test Store key in the build: [ ] yes  [ ] no

## 1. Install and first run

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 1.1 | Fresh install (uninstall first) | The APK installs with no "App not installed" error and the app opens | | |
| 1.2 | First-run intro | Before the alphabet menu, one scrolling intro shows: what the app does, how to hold the phone, camera privacy | | |
| 1.3 | Intro left-hand toggle | "I sign with my left hand" is on the intro and, when on, is reflected in Settings | | |
| 1.4 | Get started | "Get started" opens the alphabet menu, and the back gesture from the menu leaves the app rather than returning to the intro | | |
| 1.5 | Relaunch | After force-quit and reopen, the intro does not show again | | |

## 2. Camera permission

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 2.1 | Grant | Opening a letter shows a rationale, then the system prompt. After Allow, the front camera preview appears | | |
| 2.2 | Deny | After Deny, the app stays usable, explains the camera is needed, and offers a way to open system settings. No crash | | |
| 2.3 | Grant later | After enabling the camera in system settings and returning, the preview appears without reinstalling | | |

## 3. Alphabet menu and drills

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 3.1 | All letters reachable | A to Z are all listed and scrollable; the scrubber jumps to a chosen letter | | |
| 3.2 | J and Z disabled | J and Z are visibly disabled and do not open a drill | | |
| 3.3 | The 24 others open | Spot-check A, D, L, Y, and every letter you have time for: each opens a drill for that letter | | |
| 3.4 | Overlay alignment | In portrait, the hand landmarks sit on your real hand within about a finger's width, and follow it without lag | | |
| 3.5 | Mirror | The preview is mirrored (moving your right hand moves the hand on the right side of the screen) | | |
| 3.6 | Recognition | 5 easy letters (for example A, B, L, V, Y) each reach a Match within 10 seconds, held steadily | | |
| 3.7 | Three-state feedback | No hand shows "Show your hand"; a wrong or unclear shape shows neutral grey (never red); a correct shape shows a Match | | |
| 3.8 | Continue and Skip | Before a match only Skip shows. After a match, Skip and Continue show side by side, both blue with white text | | |
| 3.9 | Continue is not a skip | After Continue, the letter counts as practised and the next letter opens with the hand overlay still working | | |
| 3.10 | Letter change stability | Cycling through 5 letters with Continue/Skip: the hand overlay and the blurred backdrop behind the reference card show every time | | |
| 3.11 | Checkmark | A matched letter shows a checkmark in the menu after you go back | | |

## 4. Streak and Progress

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 4.1 | Chip hidden at zero | On a fresh install with no practice, no streak chip shows | | |
| 4.2 | Chip appears | After one match, the chip shows "1-day streak" | | |
| 4.3 | Chip opens Progress | Tapping the chip opens Progress | | |
| 4.4 | Progress layout | Progress has a header with a back arrow, no bottom tab bar, and groups for summary and "By letter" | | |
| 4.5 | Plurals | With a streak of 1 it reads "1 day" (not "1 days"); with 1 attempt "1 attempt". The same in Settings | | |
| 4.6 | Manage data link | The link on Progress opens Settings | | |

## 5. Left-handed layout

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 5.1 | Default | The A–Z scrubber is on the right edge | | |
| 5.2 | Toggle | Settings → "Left-handed layout" on: the scrubber moves to the left edge, and the letter grid order and text are not mirrored | | |
| 5.3 | Persists | After force-quit and reopen, the setting is still on | | |
| 5.4 | Left-hand recognition | With the setting on, sign one easy letter with your left hand and get a Match | | |

## 6. Pro and the paywall (Test Store)

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 6.1 | No unprompted paywall | Using the app for a few minutes (drills, menu, Settings) never opens the paywall by itself | | |
| 6.2 | Locked packs, not Pro | Story and speed packs show a small lock and a grey "Pro" label, not a blue one | | |
| 6.3 | Entry points | The paywall opens from a tap on a locked pack, and from the Pro row in Settings | | |
| 6.4 | Paywall layout | Header with a back arrow; no "Not now" button at the bottom; the pack contents are listed | | |
| 6.5 | Price before button | Both plans show price and period above the Continue button; the annual saving is a percentage | | |
| 6.6 | Test Store note | The paywall says the purchase is a Test Store purchase and no money is charged | | |
| 6.7 | Back | The header back arrow and the system back gesture both close the paywall | | |
| 6.8 | Purchase | Choose a plan, tap Continue: the Test Store purchase sheet completes, and Settings then shows "Pro active" | | |
| 6.9 | Pro unlocked | Locked packs open and their lock and "Pro" label disappear from the menu | | |
| 6.10 | Story pack | A story pack opens, shows its 5 prompts, and a prompt can be completed with the camera | | |
| 6.11 | Speed round | A round starts, can be paused and resumed, and ends with a score; the best score is kept | | |
| 6.12 | Restore | After clearing app data (or on a reinstall) and tapping Restore, Pro returns; if nothing to restore, a clear message shows | | |
| 6.13 | Packs from Settings | Settings → Handspell Pro → "Open story and speed packs" opens the packs | | |

## 7. Persistence, offline and navigation

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 7.1 | Force-quit | Swipe the app away and reopen: checkmarks, streak, Progress counts and Pro status are unchanged | | |
| 7.2 | Airplane mode | With airplane mode on, all 24 letter drills work and recognise a letter. Pro packs already unlocked still open | | |
| 7.3 | Back gestures | From a drill, Progress, Settings, a pack and the paywall, the back gesture goes back one screen and never exits unexpectedly. From the menu it exits | | |
| 7.4 | Rotation | The app stays portrait when the phone is turned | | |
| 7.5 | Delete data | Settings → Delete practice data shows an app-styled dialog. Cancel changes nothing. Delete clears checkmarks, counts and streak | | |
| 7.6 | Kill during drill | Force-stop during a drill and reopen: no crash, and the previous progress is intact | | |

## 8. Accessibility and display

| # | Check | Pass when | Result | Notes |
| --- | --- | --- | --- | --- |
| 8.1 | TalkBack, menu | Every letter reads as "Letter X" (with practice count if any); the streak chip reads "N-day streak"; focus order is top to bottom | | |
| 8.2 | TalkBack, paywall | Header, each plan (with "selected" state), price, Continue and Restore are all reachable and named | | |
| 8.3 | TalkBack, drill | Feedback changes are announced once per change, not repeatedly | | |
| 8.4 | Dark mode | With system dark mode, every screen (menu, drill, Progress, Settings, paywall, intro) is readable, with no white flashes or unreadable text | | |
| 8.5 | Light mode | Same check in light mode | | |
| 8.6 | Font 200% | Set system font size to the largest and display size to maximum: text on the menu, Settings, Progress and paywall wraps, with no clipped buttons and no horizontal scrolling | | |
| 8.7 | Tap targets | Buttons and rows are easy to hit with a thumb; adjacent targets are not mis-tapped | | |

## 9. Results

| Section | Items | Passed | Failed |
| --- | --- | --- | --- |
| 1 Install and first run | 5 | | |
| 2 Camera permission | 3 | | |
| 3 Menu and drills | 11 | | |
| 4 Streak and Progress | 6 | | |
| 5 Left-handed | 4 | | |
| 6 Pro and paywall | 13 | | |
| 7 Persistence and navigation | 6 | | |
| 8 Accessibility and display | 7 | | |

Blocking failures (must fix before recording the video): ______
