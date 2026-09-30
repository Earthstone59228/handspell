# Android test build

[Download Handspell APK](https://github.com/Earthstone59228/handspell/raw/refs/heads/main/artifacts/handspell-debug.apk)

This is the debug APK built from app source commit `9186953`, verified with 224 passing JVM tests and Android lint, and installed on the test phone. It includes the J/Z recognition audit fixes and motion guides. J/Z recognition remains experimental. Purchases use RevenueCat Test Store with no real charge.

Install with `adb install -r handspell-debug.apk`, or open the downloaded APK on Android and allow installation from the download app when prompted. The `-r` option retains existing app data when the installed signing certificate matches.

Verify the download from this directory with `sha256sum -c handspell-debug.apk.sha256`.
