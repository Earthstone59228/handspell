# Handspell project state — 28 September 2026

Canonical source is `/mnt/drive/Work/Project/Shipaton`; the September28 feature changes are
synced there, uncommitted. No push or public release was performed. The first completed debug APK
is `artifacts/handspell-static-alphabet-debug-20260928.apk` (APK assets hash-verified).

## Current features

Free: all 24 static-letter camera drills, alphabet checkmarks, local practice progress. J/Z require
motion and remain disabled. Recognition uses the original 98 team A–D exemplars plus real public
ASLYset derivatives, 64 references per static letter. Both menu and camera have data-derived guides
with preserved orientation. Public-data license and modifications are in root/bundled NOTICE.

Pro: three story packs, each with five word prompts, plus three timed speed rounds. Stories resume
progress; skipped words remain incomplete. Speed rounds support permission-before-clock, pause,
background pause, best scores, saved results and save retry. RevenueCat Pro gating remains real;
Test Store purchases are simulated. Bundled packs do not automatically download monthly updates.
Settings uses a pinned frosted hero over scrolling rows on Android 31+, with a dark fallback earlier.
Back chevrons have no square touch indication and give slightly during drag with a spring return.

## Validation and remaining gaps

100 JVM tests, 60 Python tests passed. LintDebug has 0 errors (49 warnings, 2 hints). Debug APK and
release Kotlin compilation passed. Browser checks cover all 24 wireframes, J/Z exclusion and back
interaction/accessibility/reduced motion. No phone was connected; native appearance, camera
alignment, TalkBack and RevenueCat purchase/restore still require device checks.

Public still-image signer-held-out macro F1 is ~92.5%; conservative match gates accept ~25.3% of
held-out images. R/T/U are weaker and show an experimental-recognition note. Full 24 coverage is
a prototype expansion, not proof of the live-device release criteria. The camera correction rotates
to display-upright BEFORE display-space mirroring; the former 180-degree overlay/thumbnail
compensation is removed. This supersedes the September24 memory note for analysis orientation.

Legal audit's pre-integration scoped score: 9/28 (32.1%). Operator/contact/Terms and RevenueCat
retention/request process remain unresolved. Attribution and HTTP(S) document links were corrected.
Release compilation does not resolve RevenueCat's Test Store non-debuggable-release restriction.

## Backups and optimization

Original changed files are backed up in `~/projects/shipaton-before-static-alphabet-20260928.zip`.
The finished source copy is `~/projects/shipaton-static-alphabet`. User-requested optimization has
a separate baseline at `~/projects/shipaton-optimization-baseline-20260928`, candidate at
`~/projects/shipaton-optimized-20260928`, and independent reports/harnesses at
`~/projects/shipaton-optimization-validation-20260928`. Baseline application source must remain
unchanged. Promote only app patches that match original outputs and show measured improvement;
validation-only benchmark harnesses are not production changes.

## Accepted optimization

After independent original-versus-candidate validation, the one-file Kotlin classifier patch was
promoted into canonical Shipaton and the finished working copy. The baseline folder and first
feature APK remain unchanged. The optimized APK is `artifacts/handspell-optimized-debug-20260928.apk`.
11,018 output records matched exactly; all application/source assets other than the classifier
were unchanged. Repeated desktop JVM classifier time fell by 37.8% for real poses, 35.7% noisy,
25.4% mixed and 8.4% far inputs. These are classifier microbenchmarks, not phone/whole-app measurements.
Independent validation/report: `docs/audits/optimization-validation-2026-09-28.md`.
