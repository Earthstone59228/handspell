# ASL alphabet frontend session notes

This repository is a Capacitor/Vite frontend used to prototype the ASL menu before applying it to Shipaton. The browser development server live reloads changes under `src/`. Android uses bundled assets after `npm run build && npx cap sync android`. Native changes made here: Android `VIBRATE` permission for scrubber ticks and a back-gesture handler in `MainActivity`.

## Current menu

- The screen uses `#1D1D1F` as its dark background, `#F5F5F7` for letter cards, and `#007AFF` for highlights. The supplied phone animation has some additional light fills within the asset itself.
- The Alphabet header spans edge to edge and uses a dark `backdrop-filter` blur. Its height leaves room before the scrubber begins. Two flat white SVG icons, settings and paper, sit beside the title. Each opens a separate blank dark screen with a top-left Back button; both the button and Android Back return to the alphabet.
- A–Z cards scroll vertically. Each card shows a letter, its number or `complete`, and a sign illustration placeholder.
- The right A–Z scrubber is fixed. Dragging jumps the list to a letter and requests one short vibration when the selected letter changes. Its outline and letters form a restrained bell curve while touched, then retract. Cards narrow according to their on-screen distance from the visible curve, then return to full width on release. Jumps clamp to the real list ends near A and Z.
- Manual scrolling has a small resisted pull at the start and end. Completion is saved in `localStorage` under `sign-by-sign-progress-v1`. A streak value is still maintained there, but its display was removed at the user's request.
- Android Back closes an open letter sheet or phone placement screen. At the Alphabet root, Back keeps the app open. `MainActivity` forwards Back to the frontend as `aslNativeBack`.

## ASL sign placeholder: important for Shipaton

Every card has a `sign-placeholder` element with `data-sign-letter="A"` through `"Z"` in `src/js/app.js`. The four corner marks and center dot are a neutral reserved slot. They are **not an ASL handshape** and must not be treated as sign instruction or validation. Replace each slot with the correct letter-specific gesture artwork or live preview when those assets are ready. Keep the A–Z mapping tied to `data-sign-letter`, and verify the handshape, orientation, movement, and accessibility description for each sign before release.

The large alphabet letter in the popup is also typographic only. No gesture image, camera recognition, scoring, or sign correctness check has been implemented. `Mark practice` is a self-reported completion action.

## Card popup and practice setup

- Tapping an incomplete card opens a dark blurred sheet with two actions: blue `Practice` on the left and white `Mark practice` on the right. A completed card shows only a blue `Undo completion` button with white text.
- `Mark practice` saves the letter as complete and slides the sheet down.
- `Practice` slides the sheet down and opens a full-screen phone placement screen. `Undo completion` clears a completed letter and slides the sheet down; the streak history is retained.
- Navigation icons are standardized: the letter sheet uses the single X symbol to dismiss; the phone placement and blank utility screens use the single back-chevron symbol at top left. Both symbols are defined once in `src/index.html`.
- The placement screen keeps the dark background. It centers the animated phone and says: “Find a place to lean your phone against.” A blue `#007AFF` Confirm button with white text opens a blank practice screen. Back from either screen returns to the alphabet. The animation runs only while the placement screen is open.
- The current visual comes from the latest downloaded `/home/earthstone/Downloads/phone_stand_yaw_rotation_ring_animation.html`. Its app copy is `src/public/phone-stand-animation.html`. The source HTML's white background rectangle and demo pause/slider controls were removed. The original Downloads file was left untouched. An earlier static SVG prototype was replaced. The phone-on-stand visual is **not** an ASL gesture placeholder.

## Main files

- `src/index.html`: screen structure and modal/setup markup.
- `src/css/style.css`: palette, layout, glass surfaces, and scrubber/card movement.
- `src/js/app.js`: A–Z rendering, local progress, index gesture handling, sheet actions, and setup navigation.
- `src/public/phone-stand-animation.html`: phone placement animation from the downloaded HTML, with transparent background.
- `android/app/src/main/AndroidManifest.xml`: `VIBRATE` permission for Android WebView haptics.
- `android/app/src/main/java/com/example/ionicapp/MainActivity.java`: Android Back forwarding to the frontend.

The frontend currently uses the browser Vibration API for ticks on supported devices. The app does not include a native haptics plugin. Port the interaction and design to Shipaton with its own data model, sign assets, and practice flow when available.
