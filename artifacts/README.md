# Android test build

[Download Handspell APK](https://github.com/Earthstone59228/handspell/raw/refs/heads/main/artifacts/handspell-debug.apk)

This debug APK contains the UI redesign, on-device letter and word recognition, and the Pro paywall. It was built after the existing JVM test suite passed. The bundled build uses a public RevenueCat Test Store key: purchases are simulated and no money is charged. The visible 3-day local demo trial is a separate route to Pro and never starts a store purchase.

Install with `adb install -r handspell-debug.apk`, or open the downloaded APK on Android. The `-r` option retains app data when the installed signing certificate matches.

Verify the download from this directory with `sha256sum -c handspell-debug.apk.sha256`.
