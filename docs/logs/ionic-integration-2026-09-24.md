# 2026-09-24 Ionic alphabet integration

Bundled the unchanged Ionic alphabet build into the native Handspell Android project with relative
Vite asset URLs. The alphabet's Settings icon opens native Settings, its paper icon opens bundled
legal documents, and Confirm starts a native camera drill for classifier-supported letters. The
request route waits for content loading and shows an honest unavailable state for unsupported letters.
Existing native practice, Progress, Pro packs, camera, classifier, calibration foundation, and debug
capture code remain available.

The alphabet's self-reported checkmarks remain separate from classifier-confirmed DataStore counts.
The confirmed Settings deletion clears both stores. Privacy and progress copy now explain the split.
The supplied `Downloads/logo` PNG is used as the Android launcher icon. The Documents screen includes
the current privacy notice, Handspell MIT license, third-party notices, model license, and the two
Capacitor licenses. No Terms or responsible-party details were invented; see `docs/LEGAL_OPEN_ITEMS.md`.

`npm run build -- --base=./` passed for the web bundle. Android compile, unit tests, lint and debug
build are the verification gates for this change; device camera, document navigation, deletion and
accessibility checks remain to be run on a connected phone.
