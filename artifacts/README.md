# Android test build

[Download Handspell APK](https://github.com/Earthstone59228/handspell/raw/refs/heads/main/artifacts/handspell-debug.apk)

This debug APK contains the frontend revamp, on-device letter and word recognition, and the Pro paywall. It was built after 225 passing JVM tests and Android lint. The bundled build has no RevenueCat API key, so judges can use the visible 3-day local demo trial without entering payment details. To test RevenueCat Test Store purchases, build with a public Test Store key in `android/local.properties`.

Install with `adb install -r handspell-debug.apk`, or open the downloaded APK on Android. The `-r` option retains app data when the installed signing certificate matches.

Verify the download from this directory with `sha256sum -c handspell-debug.apk.sha256`.
