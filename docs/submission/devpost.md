# Handspell

## Inspiration

Practicing ASL fingerspelling alone leaves a learner without someone to check a handshape. We built Handspell to give feedback during camera practice and to keep a local record of progress.

## What it does

Handspell is an Android ASL fingerspelling trainer. Its free alphabet menu opens camera drills for all 24 static letters, shows checkmarks, and saves progress on the device, with a streak chip on the menu, a Progress screen, a left-handed layout that moves the A–Z scrubber to the left edge, and a short first-run introduction. Recognition runs on the phone; there is no backend. J and Z are disabled because they require motion. Pro adds 3 story packs with 5 word prompts each and 3 timed speed rounds with pause, resume, and best scores. Pro access is gated through RevenueCat, and the paywall only opens after a tap on a locked pack or on Settings. Purchases use the RevenueCat Test Store and are simulated; this is not a live paid subscription.

## How we built it

The Android app uses Kotlin, Jetpack Compose, CameraX, and MediaPipe Hand Landmarker. A plain-Kotlin k-NN/MLP classifier reads normalized hand landmarks. Temporal smoothing and hold-to-confirm reduce fleeting matches. The alphabet menu is an Ionic web view bundled with the app. Versioned JSON packs hold the practice content, and progress is stored locally. There is no backend. We built the paywall in Compose and connected Pro access to the RevenueCat SDK and Test Store.

## Challenges

Handshapes vary by signer and hand. We normalize landmarks to the wrist origin and palm scale, and mirror left hands, so the classifier receives a consistent representation. A match in one frame can flicker, so we added temporal smoothing and hold-to-confirm. Static poses cannot represent the motion needed for J and Z, so those letters remain disabled. Recognition for R, T, and U is weaker, and the app flags them as experimental.

## Accomplishments

We have free camera drills for 24 static letters and Pro story and speed packs behind the paywall. Recognition reached about 92.5% macro F1 on held-out signers in still images, using public ASLYset data and 98 team-recorded A–D exemplars. The project passes 127 JVM tests; lint reports 0 errors.

## What we learned

Holding out whole signers gives a more useful check of how recognition handles hands beyond the examples used for training. Normalized landmarks let the classifier work from handshape and hand position data instead of raw camera pixels. Still-image evaluation does not measure every condition in a live camera drill.

## What's next

We would like to explore motion recognition for J and Z and word signs (a first attempt on PopSign data did not work well enough to ship), add finger-by-finger correction hints and a free mode that names any letter, gather more signer data to improve R, T, and U, and add content through the versioned JSON pack format. A future store release could replace simulated Test Store purchases with live billing.

## Built with

Kotlin, Jetpack Compose, CameraX, MediaPipe Hand Landmarker, Ionic, RevenueCat SDK and Test Store, bundled JSON content, and public ASLYset data.
