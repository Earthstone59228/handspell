# Classifier optimization — 2026-09-28

The finished application was copied before this work. The original feature build remains at
`/mnt/drive/Work/Project/Shipaton` and `/home/earthstone/projects/shipaton-static-alphabet`.
The read-only optimization baseline is `/home/earthstone/projects/shipaton-optimization-baseline-20260928`.
Only `/home/earthstone/projects/shipaton-optimized-20260928` contains the proposed optimization.

Two agents coordinated this work: one implemented the candidate; the other captured the original
results first, built an independent validation harness, and compared the optimized result.

## Implementation

The live classifier scans 1,536 reference vectors for every frame. Its previous implementation
calculated all 66 dimensions and a square root for every reference, then calculated the 60 shape
dimensions again whenever a reference entered the five-neighbor list.

The candidate keeps Kotlin and the existing classifier interface. It accumulates shape differences
in their original order and tests a conservative distance bound after 30 and 60 shape dimensions.
The two fixed loop ranges let the runtime optimize the loop while checking an intermediate bound.
Every unprocessed squared difference is nonnegative, so a partial sum above that bound cannot
produce an eligible neighbor. Surviving candidates retain the original orientation arithmetic,
square root, strict distance comparisons, stable insertion order, vote calculation and ranking.

The bound is `nextUp(nextUp(worstDistance) * nextUp(worstDistance))`. Rounding upward before and
after squaring keeps references whose square roots could round to a distance tie eligible for
the original final comparison. The bound remains open while the neighbor list is incomplete;
infinite and NaN bounds do not introduce an early rejection.

The nearest reference's shape sum also supplies its shape distance. Other neighbors' shape
distances are never returned, so one local scalar replaces a scratch array and its shift/copy
work. Reference arrays remain immutable and all per-call scratch state remains local.

No model/data reduction, thresholds, numerical precision, features, dependencies, permissions,
network behavior, billing, legal text, or UI changes are part of this optimization. Kotlin avoids
the extra packaging, runtime and interoperability costs of a language rewrite for this loop.

## Validation

Independent validation artifacts and the runner are at
`/home/earthstone/projects/shipaton-optimization-validation-20260928`.

Before scoring-path edits, the validator captured 11,018 exact output records, including all 1,536
bundled references, noisy inputs, midpoints, orientation changes, far/random/extreme inputs,
different neighbor counts, personal examples, synthetic ties and neighboring floating-point
values, feedback streams, and acceptance-threshold boundaries. All 24 static letters are retained;
J and Z remain excluded. The original 100 JVM tests and 60 Python tests passed.

All final-candidate runs matched the 11,018 records bit for bit. The baseline and candidate
signature files share SHA-256
`72dd9027a366777dc944caf190192fc6ab2a38e61c1bda30306e59ea32b0099d`.

The independent validator repeated adjacent original/candidate runs twice. The following warmed
medians aggregate 18 intervals of 6,144 classifications per workload per variant (110,592 calls):

| Workload | Baseline | Candidate | Less time |
| --- | ---: | ---: | ---: |
| Bundled reference poses | 71.271 µs | 44.364 µs | 37.75% |
| Noisy poses | 70.147 µs | 45.107 µs | 35.70% |
| Mixed poses | 70.901 µs | 52.857 µs | 25.45% |
| Far poses | 70.108 µs | 64.250 µs | 8.36% |

Every measured final-candidate interval was faster than every original interval for the same
workload. Measured allocation fell from approximately 1,296 bytes per classification to
1,240–1,256 across runtime repeats, a 40–56 byte reduction (aggregate median: 1,248 bytes).
The five-element shape-distance scratch array is removed; measured totals vary slightly between
JVM runs. Detailed timing intervals, comparison order and
statistics are in `final-benchmark-summary.json` in the external validation directory.

The independent validator compared 137 application source/asset files and found exactly one
application source changed. All 54 protected files remained identical. Final candidate validation
passed: 100 JVM tests with no failures/errors/skips, 60 Python tests, `lintDebug` with zero errors
(49 warnings and two hints), and `assembleDebug`.

The validator accepted the patch. Its independent review is
`/home/earthstone/projects/shipaton-optimization-validation-20260928/optimization-validation-2026-09-28.md`.
The candidate source SHA-256 is
`99d7374ee81d3759d8ae2309caaf2e114060675eb85c325b87d3468140686338`.

The separately built candidate APK is
`android/app/build/outputs/apk/debug/app-debug.apk` (77,577,313 bytes), SHA-256
`3c5e4416584b22a62f44ef68e7f0d938e26a4051d7c0207cb2ab7cbbfe83cdea`.
The candidate remains in its separate folder for parent review; no promotion or deployment was
performed by either optimization agent.

These are warmed desktop JVM classifier microbenchmarks with fixed input order, heap and garbage
collector. They exclude CSV loading, MediaPipe, camera processing, UI, and startup. They establish
improved classifier cost on these workloads; no Android device is connected, so they do not
establish whole-app performance, Android runtime speed, battery savings, or live recognition accuracy.

Reproduce the independent harness with:

```sh
python /home/earthstone/projects/shipaton-optimization-validation-20260928/run_validation.py \
  /home/earthstone/projects/shipaton-optimized-20260928 candidate-recheck all
```

The application runtime jar must be rebuilt after any source change, using offline Gradle:

```sh
cd /home/earthstone/projects/shipaton-optimized-20260928/android
source ../env.sh
./gradlew --offline --console=plain :app:bundleDebugClassesToRuntimeJar
```

## Changed files

- `android/app/src/main/java/dev/handspell/app/vision/classify/KnnLetterClassifier.kt`
- `docs/logs/optimization-2026-09-28.md`

Build artifacts and the external validation harness are not application source changes.

## Promotion after independent acceptance

The validated one-file patch was copied into canonical Shipaton and the finished working copy.
The original feature source is preserved in the optimization baseline folder, and its original
APK remains at artifacts/handspell-static-alphabet-debug-20260928.apk. The optimized APK is
artifacts/handspell-optimized-debug-20260928.apk. Benchmark harnesses were not promoted.
