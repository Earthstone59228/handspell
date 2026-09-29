# Static alphabet, back arrow and integration validation — 2026-09-28

Canonical source: `/mnt/drive/Work/Project/Shipaton` at `4bcca5d`, clean before this task.
Work completed in `/home/earthstone/projects/shipaton-static-alphabet` because the canonical path
is outside the writable sandbox. Changes are prepared for a checked sync; no commit/push was requested.

- 4,786 public ASLYset image examples imported with original archive SHA256 verified.
- 1,536 bundled references: all24 static letters, J/Z excluded; original98 A–D preserved.
- All24 menu placeholders replaced by real-reference illustrations; orientation preserved in both guides.
- Camera rotates to upright before selfie mirroring; overlay/thumbnail now share that frame.
- Web/native back arrows have resisted drag and spring return; square touch indication removed.
- Pro/session/settings work reviewed from the implementation subagent (see pro-features log).
- Legal subagent report retained with scoped pre-integration grade and remaining owner-only gaps.
- `:app:testDebugUnitTest`: 100 tests, 0 failures/errors.
- Python training tests: 60 passed.
- `:app:lintDebug`: 0 errors, 49 warnings, 2 hints.
- `:app:assembleDebug` and `:app:compileReleaseKotlin`: passed, final build 1m2s.
- Browser checks: every static menu illustration, J/Z disabled, back press/drag/return/cancel/tap/keyboard/reduced motion passed.
- APK assets verified against source reference/provenance/notices/web bundle hashes.

The debug APK is built with the copied local RevenueCat configuration; no key was added to source,
reports or sync manifests. No phone/emulator was connected, so camera alignment, native UI/gesture,
TalkBack, large-font and RevenueCat purchase/restore remain device checks. Release compilation is
not proof that a Test Store key may run in a non-debuggable release; that audit issue remains open.

ASLYset signer-held-out still-image macro F1 ~92.5%. R/T/U are weaker; conservative per-frame gates
accept ~25.3% of held-out images. Expanded24-letter coverage is prototype behavior, not a declaration
that the prior live-device release criteria were passed. No MLP weights were bundled.
