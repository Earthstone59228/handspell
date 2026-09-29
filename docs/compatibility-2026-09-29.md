# Handspell APK compatibility and sharing — 2026-09-29

## What is known

The report is “my friend can't download it.” The friend’s platform, link, and exact error have not been provided. Download failure, installation failure, and launch failure are different problems; the evidence below does not identify which happened.

I checked `artifacts/handspell-mlp-debug-20260929.apk` with `scripts/check-apk-compat.sh`. It is 77,597,354 bytes (74.0 MiB), package `dev.handspell.app`, versionCode 1, minSdk 24, targetSdk 37, and contains arm64-v8a, armeabi-v7a, x86, and x86_64 libraries. `zipalign -c -P 16 -v 4` passes. All 20 `.so` files have LOAD segments aligned to at least 16 KB with matching file and virtual-address offsets. `apksigner verify` passes with v2 signing; its signer is `CN=Android Debug`, certificate SHA-256 `4147bdcc26ead05dda378431a3af1751c5da9368c043a456b19921e4b1ae5673`. `apkanalyzer manifest print` confirms `android:debuggable="true"`. The APK's SHA-256 is `8bddca88614d20bf2335152226bfca3e4e1601952d6bfbf5f361d4b7ba80e981`. These package checks do not prove installation on the friend's device.

Recheck a shared copy, without installing it:

```bash
source env.sh
scripts/check-apk-compat.sh path/to/downloaded.apk
sha256sum path/to/downloaded.apk
```

Android's [16 KB guidance](https://developer.android.com/guide/practices/page-sizes) requires suitable ZIP and ELF alignment for native libraries; the [zipalign documentation](https://developer.android.com/tools/zipalign) identifies `-c -P 16 -v 4` as a read-only check. This APK passes both checks, so 16 KB alignment is not the likely cause for this file.

## Diagnose from the friend's symptom

| Symptom or check | Likely cause | How to tell and fix |
| --- | --- | --- |
| Link gives 404 or asks for GitHub access before any APK downloads | The old prerelease was on a **private** GitHub repo. GitHub can return 404 for a private resource without access. | Open the exact link while signed out or in a private browser. If it fails there, the owner should share this APK through a link the friend can access, or make the repo/release public when ready. Do not change the APK. [GitHub explains private-resource 404s.](https://docs.github.com/en/rest/using-the-rest-api/troubleshooting-the-rest-api) |
| APK downloads, but Android says the browser or file app may not install unknown apps | Android's per-source install permission is off. | On the friend's Android device, enable **Install unknown apps** for the app that opened the APK, then retry the same file. Android documents the per-source setting for Android 8 and later. [Source.](https://developer.android.com/distribute/marketing-tools/alternative-distribution) |
| Play Protect shows a warning or blocks the installation | A separate device security decision, not a Gradle compile error. | Record the exact warning. Check that the file hash matches the owner-shared APK. If Play Protect flags it as harmful, investigate or appeal the flag rather than asking the friend to disable protection. [Google's Play Protect guidance.](https://support.google.com/googleplay/answer/2812853?hl=en) |
| Android says “App not installed,” and Handspell is already installed | An earlier APK with the same package name may have a different signing certificate, especially if it was built on another machine's debug key. A higher installed versionCode can also block a downgrade. | Compare the prior APK's `apksigner` certificate SHA-256 and versionCode with the values above. If signing keys differ, build future updates with the same key. For tonight, uninstalling the earlier app and installing this APK is possible **but erases that app's local progress**; ask the friend before doing it. Android requires matching signing certificates for updates. [Source.](https://developer.android.com/google/play/app-updates) |
| Install says the Android version is unsupported | Device API level is below minSdk 24 (Android 7.0). | Check the device's Android version in Settings. This build cannot install below API 24. Lowering minSdk would require dependency and device validation; it is not a safe last-minute change. |
| Install says no matching native architecture | Device ABI is not included. | Compare its CPU ABI with the four listed above. A 32-bit ARM (`armeabi-v7a`) or x86 device **is already covered** by this APK, so “32-bit only” by itself is not an explanation. An ABI-specific future APK must match the friend's device. |
| APK installs on a 16 KB page device but native code fails to load | A native library or package alignment problem. | Run the script on the exact downloaded APK, check ZIP and each ELF result, and verify its SHA-256. This build passes; if another build fails, update/rebuild the affected native dependency with 16 KB support and rerun both checks. [Android's native-library guidance.](https://developer.android.com/guide/practices/page-sizes) |
| Download stalls, truncates, or the copied file fails signature verification | The 77.6 MB transfer or host may be the problem. | Compare downloaded byte count and SHA-256 with the known values. Retry from a stable, accessible file host. Only if size is confirmed to be the obstacle, consider per-ABI APKs after identifying the friend's ABI and checking each output. |
| Friend uses an iPhone | An Android APK cannot install on iOS. | The current project has no iOS build. Share the demo video instead; producing an iOS app is a separate project. |

## One build to share tonight

Share the **existing universal debug APK** above through a direct link accessible to the friend, with its SHA-256 beside the link. Keep this exact file for all testers so its package, version, and debug certificate stay consistent. It contains the RevenueCat Test Store demo flow and all four ABIs. Ask the friend first whether the link fails, the download fails, or Android rejects the install; request the exact message and device platform before changing the build.

RevenueCat's [Test Store documentation](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store) says the Android SDK intentionally crashes a build using a Test Store API key when `android:debuggable` is false. The default release build is non-debuggable; a Test Store demo build must remain **debuggable**. The current debug build meets that requirement. A real store release would need the platform-specific RevenueCat key and a separate billing and distribution review. Do not submit the Test Store APK to Google Play.

## Proposed code changes only if evidence calls for them

No Gradle or app diff is needed for the current universal debug APK: it covers the common ABIs and passes the 16 KB checks. If the friend confirms that file size is the blocker, a later `android/app/build.gradle.kts` diff could add ABI splits for the **debug** build while keeping a universal APK for general sharing:

```kotlin
android {
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}
```

That change would produce several files, so each tester would need the correct one; it is a proposal, not tonight's distribution plan. [Android documents the ABI split DSL.](https://developer.android.com/build/configure-apk-splits) If a signing conflict is confirmed, a later build configuration should use one controlled signing key for all tester updates, kept out of tracked source. Do not put passwords or keys in `build.gradle.kts`.
