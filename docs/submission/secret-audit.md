# Git-history secret audit (T02)

Run 2026-09-30 01:05 +07 on `main` at `41eb2a3`. Counts only; no secret value is printed here.

| Check | Result |
| --- | --- |
| Commits scanned (`git rev-list --all`) | 35, across 14 refs |
| Values from `android/local.properties` (the RevenueCat key and `sdk.dir`) found in any file at any commit | 0 |
| Same values found in the current tracked tree | 0 |
| `android/local.properties` ever tracked | No (0 commits touch it; it is in `.gitignore`) |
| RevenueCat/store key patterns (`goog_`, `appl_`, `amzn_`, `sk_`, `rcb_`, `test_` + 16 or more characters) in any commit | 0 |
| Keystores, `.env` files tracked | None. Only `android/local.properties.example` |
| `docs/logs/*` tracked | 5 files. They contain none of the values above |

Method: each value was read from `local.properties` into a scratch file and searched with `git grep -F -f` in every commit. Only match counts were printed.

Caveat: this covers the exact values present today. A key that was rotated before this check would not be found by value, but the pattern scan above also found nothing.

Re-run this before making the repo public and again if any commit is added.
