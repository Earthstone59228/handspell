# Privacy notice

Revised 2026-09-30. Handspell is a student hackathon project (Next Gen), not a company product.

- Responsible person or team: **Earthstone59228 (project maintainer)**
- Privacy and support contact: **https://github.com/Earthstone59228/handspell/issues**

## Short version

Handspell uses your camera to find your hand (and, for word signs, your nose and shoulders) and to
tell you which letter or word you signed. That happens on your phone: camera frames are not saved,
not turned into a photo or video file, and not sent anywhere. The app has no accounts, no analytics
and no crash reporting. Your practice progress is saved only on your phone, and you can delete it
from Settings. Purchases and the Pro status check go through RevenueCat, described below; builds
shown for judging use RevenueCat's Test Store, so no real money is charged.

## What the app does

Handspell is a practice aid for ASL fingerspelling and a small set of word signs. It watches your
hand through the camera, tells you which letter or word your hand matches, and tracks your practice
over time. Recognition feedback can be wrong. Handspell provides practice feedback but does not
certify or assess ASL proficiency. Word signs are taught as one-handed versions, and real ASL signs
can differ by person and region.

## What the camera sees, and where it goes

Camera frames are decoded into memory, turned into landmark coordinates, classified, and then
discarded. For letters, the app uses the joints of one hand. For word practice, the app also
estimates body landmarks and uses the positions of your nose and shoulders, together with your hand
joints over the last couple of seconds, to interpret hand movement. It does not identify you or
recognise your face. No frame, image or landmark data is written to storage, added to your photo
library, uploaded, or logged. Camera processing runs locally, so it works without a connection.

Debug builds of the app, which include the tester APK on GitHub, also contain a developer tool for
recording hand landmark data on the phone it runs on. It is not part of a release build, saves only
to that phone, and nothing is sent anywhere automatically. Developers can export those files by
USB or the system share sheet.

## What is stored on your phone, and how to delete it

The native app keeps one small file of camera drill counts (letters and words), story steps, up to
your last 50 speed-run results, streaks and the days you practised, today's quest progress, and
whether you completed onboarding. A preferences file keeps your settings (appearance, layout, the
daily reminder and its time, and when a demo trial was started). The embedded Alphabet screen
separately keeps self-reported checkmarks and its streak in WebView local storage on this phone. An
Alphabet checkmark does not mean the camera recognised a sign. None of these stores holds photos,
video, or raw landmark data.

This data stays on the phone until you delete it or uninstall the app. **Settings → Delete practice
data** removes the practice records above (both progress stores). It does not clear your preferences
or anything held by RevenueCat. Personal calibration is not available in this version.

## Network use and RevenueCat

When purchase services are configured in the build, Handspell uses RevenueCat to load the available
plans, manage purchases, restore access and check whether Pro is active. The SDK starts when the app
starts, so this can happen before you buy anything. RevenueCat receives an app-specific user
identifier (not your name or email) and device and purchase information, such as device type,
operating system, purchase receipt or token data and last-seen time. RevenueCat describes what it
collects in its own privacy policy: https://www.revenuecat.com/privacy. RevenueCat acts for the app
as its purchase and entitlement provider and handles end-user requests through the app.

The app's network configuration disables unencrypted traffic. Apart from RevenueCat, the app makes no
network calls of its own: there are no ads, analytics or content downloads, because lesson content
ships inside the app. Deleting practice data on the phone does not ask RevenueCat to delete anything.
To make a privacy request about information held by RevenueCat, use the contact above at https://github.com/Earthstone59228/handspell/issues (do not post receipts or personal data publicly; request a private channel). Where RevenueCat keeps information, for how long and under which
transfer terms is set by RevenueCat's own policy and agreements, not by this notice.

## Test Store and the demo trial

Builds shown for judging in this hackathon are configured against RevenueCat's Test Store, not a
real Google Play billing account. Purchases in those builds are simulated: no card is charged and no
real money changes hands. The paywall and Settings say so. The 3-day demo trial is separate: it
starts locally on your phone, never touches the store, and ends by itself.

## Permissions and why

- **Camera**: to find your hand (and, for word practice, your nose and shoulders) and interpret your
  sign. Camera access is required for live practice. You can browse the Alphabet, the Words list,
  Progress and Settings without it.
- **Internet**: for RevenueCat, described above.
- **Vibration**: light vibration feedback when you use the alphabet scrubber or mark a letter
  complete.
- **Notifications** (Android 13 and later, optional): only for the daily streak reminder, which is off
  until you turn it on in Settings → Reminders. It is scheduled and decided on the phone (WorkManager
  reads your local practice record and stays quiet if you have already practised that day). Nothing is
  sent over the network and no push service is involved. Turn it off in the same place, or deny the
  permission, and no reminder is shown.

The app does not request a microphone, location, contacts, storage access or an account.

## Children

Handspell is not directed at children under 13. It has no account and does not ask for a name or
email. RevenueCat receives the app-specific user identifier and the device and purchase information
described above; camera frames and practice progress stay on this phone. The intended audience and
markets are limited to the current Android hackathon prototype; wider distribution requires review.

## Changes to this notice

Updated notices are included with app updates. The revision date appears at the top.
