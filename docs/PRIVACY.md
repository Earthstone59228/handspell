# Privacy notice

Dated 2026-09-22. Handspell is a student hackathon project (Next Gen), not a company product.

## Short version

Handspell reads your camera to find your hand on the screen and tell you which letter you signed.
That happens entirely on your phone: camera frames are never saved, never turned into a photo or
video file, and never sent anywhere. The app has no accounts, no analytics and no crash reporting.
Your practice progress (letter counts, story steps, speed-run results, streaks) is saved only on
your phone, and you can delete all of it from Settings. If the optional **Improve recognition**
feature is enabled in a later app update, its local calibration measurements will be disclosed and
deletable in Settings before recording begins. The only network connection the app makes is
to RevenueCat, to check whether you have Pro — and every purchase shown in demo builds runs through
RevenueCat's Test Store, so no real money is charged.

## What the app does

Handspell is a practice aid for ASL fingerspelling. It watches your hand through the camera, tells
you which letter your handshape matches, and tracks your practice over time. It is not an ASL
authority and it does not certify or grade your signing — treat it as a drill partner, not a teacher.

## What the camera sees, and where it goes

The camera preview shows your hand so MediaPipe's hand landmark model can find its joints. Each frame
is decoded to a bitmap in memory, turned into landmark coordinates, classified, and then discarded.
No frame, bitmap or landmark image is ever written to disk, added to your photo library, uploaded to
a server, or written to a log. This happens even when you are offline, because none of it touches the
network in the first place — it is a local computation on the phone's processor.

The one exception is a debug-only capture screen used by the development team to record their own
practice sessions for building the letter classifier. It does not exist in the version of the app you
install or that judges see (`assembleRelease` does not include it), and it only writes to the phone
that's running it, pulled off by a teammate over a USB cable — never automatically and never to us.

## What is stored on your phone, and how to delete it

The app keeps one small file of your progress: how many times you have practised each letter, which
story steps you have finished, up to your last 50 speed-run results, your current and best streak,
and whether you have completed onboarding. That's it — no photos, no video, no raw landmark data.
The backend foundation for the optional **Improve recognition** feature is not exposed in this build.
When it is exposed, it will separately keep up to 24 normalized 66-number handshape measurements for
each letter the user deliberately saves. They are derived locally from a held camera sample; they are
not photos, video, raw landmarks, a signer name, or a capture history, and they never leave the phone.

To delete progress, go to **Settings → Delete all data**. The optional calibration feature is not
currently exposed. When enabled, it will offer deletion for one letter or all saved calibration in
**Settings → Improve recognition**; those actions will remove the corresponding local data
immediately.

## Network use and RevenueCat

The app makes exactly one kind of network call: to RevenueCat, to check and record whether your
subscription entitlement (`pro`) is active. `api.revenuecat.com` is the only host the app talks to.
The app's network configuration disables unencrypted traffic and documents that single host, and we
verify the claim by watching the phone's network traffic during testing — a configuration file cannot
enforce a host allowlist on its own, so we do not claim it does.

RevenueCat's SDK generates an anonymous app user id for you (not your name or email) and sends it
purchase and entitlement information — RevenueCat documents what it collects from end users of apps
that integrate its SDK, including device type and operating system, purchase receipt/token data, and
last-seen time, in its own privacy policy: https://www.revenuecat.com/privacy (see "Personal Data We
Collect" → end user information). RevenueCat processes this data on our behalf as the app's payment
and entitlement provider; it is not shared with anyone else.

## Test Store — no real charges

Every build shown for judging in this hackathon is configured against RevenueCat's Test Store, not a
real Google Play billing account. Purchases you see or make in these builds are simulated: no card is
charged and no real money changes hands. The paywall and Settings both say so in plain words.

## Permissions and why

- **Camera** — to find your hand and classify the letter you're signing. Required for the app to
  work at all.
- **Internet** — only for the RevenueCat entitlement check described above. The app does not use it
  for anything else: no ads, no analytics, no content downloads (lesson content ships inside the app).

The app asks for nothing else: no microphone, no location, no contacts, no storage access, no
accounts.

## Children

Handspell is not directed at children under 13. It collects no personal data from anyone, regardless
of age — there is no account, no name, no email, and nothing identifying is ever sent off the device
except the anonymous entitlement check described above.

## Changes to this notice

If what the app collects or where data goes changes, this notice will be updated and dated at the
top. There is no mailing list or notification system to announce changes; check back here.

## Contact

Questions about this notice or your data: [contact email — team to fill before submission]. Handspell
is built by a student team for a hackathon; there is no company behind it.
