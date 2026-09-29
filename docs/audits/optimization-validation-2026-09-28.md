# Independent optimization validation — 2026-09-28

The final candidate passes the original regressions and reproduces all 11,018 captured result records exactly. It reduces classifier CPU time across every measured workload. This evidence supports promoting the single Kotlin classifier change; the parent agent owns source promotion and APK delivery.

The baseline is `/home/earthstone/projects/shipaton-optimization-baseline-20260928`. The candidate is `/home/earthstone/projects/shipaton-optimized-20260928`. Validation artifacts are in this report’s directory. Neither the canonical project nor the finished-feature project was edited by this validator. The only baseline edit was the explicitly separate validation harness in test sources; application code and assets remain original.

Before optimizer edits, the baseline passed 100 Android JVM tests and 60 Python tests. The external harness then captured an immutable original oracle and pre-edit timings. The frozen final candidate passed all 100 existing JVM tests, all 60 Python tests, Android lint, and debug APK compilation. No test failures, errors, or skips occurred. Lint reported zero errors, 49 warnings, and two hints. The candidate APK is `android/app/build/outputs/apk/debug/app-debug.apk` (77,577,313 bytes); the parent can deliver this verified APK under a separate optimized filename.

The application change is `android/app/src/main/java/dev/handspell/app/vision/classify/KnnLetterClassifier.kt`. It checks conservative squared-distance bounds after fixed 30- and 60-dimension shape loops, preserves original Double accumulation order and strict neighbor comparisons, and reuses the nearest neighbor’s shape sum. Scratch state remains local to each call. A 137-file comparison of application source/assets found this one file changed. All 54 explicitly protected asset, golden-fixture, feedback, and UI files remain byte-identical. Probability thresholds, orientation weight, distance gates, smoothing, confirmation time, and tie ordering remain unchanged.

Final source SHA256: `99d7374ee81d3759d8ae2309caaf2e114060675eb85c325b87d3468140686338`.

The original implementation alone produced the oracle. The candidate was never used to generate expected results. Every snapshot contains ranked letters and probability Float raw bits, nearest-distance and nearest-shape-distance Float raw bits, timestamps, and feedback states. There was no added tolerance for original-versus-candidate comparison. Existing Kotlin/Python MLP golden tests retain their established 1e-5 tolerance.

Coverage includes all 1,536 bundled real-hand references (public ASLYset-derived vectors plus retained team references), 512 noisy vectors, 256 each of midpoint, orientation-reversed, far, and random vectors, and six extreme inputs. Those extreme inputs include zeros, subnormal values, maximum finite Float, NaN, and both infinity signs. Further checks cover k=1/2/9 and k larger than the reference count; personal exemplars; 160 independently seeded synthetic datasets with exact ties, duplicates, tiny/large coordinates, and nextUp/nextDown offsets; all 26 target feedback streams, no-hand gaps, and probability/distance/hold-time boundary sequences. All 24 static letters remain supported and J/Z remain excluded by the classifier. The original bundled-guide regression also confirms each static letter after its hold window.

All original and final-candidate snapshots share SHA256 `72dd9027a366777dc944caf190192fc6ab2a38e61c1bda30306e59ea32b0099d`. Each full snapshot has 11,018 records. The checksums accumulated while consuming benchmark results also agree exactly (`417887293514721`). These checks establish equivalence for the tested cases; they do not establish camera accuracy for every signer.

Measurements use a standalone JVM launcher after compilation, so Gradle work and CSV parsing are excluded. The machine is Linux x86_64 with an AMD Ryzen 7 6800HS and Android Studio JBR OpenJDK 25.0.3. All comparison processes use `-Xms512m -Xmx512m -XX:+UseSerialGC`. Each process warms 18,432 classifications, then records nine rotated intervals per workload, each with 6,144 classifications. The validator repeated original then final candidate twice, without overlapping agent benchmarks/builds. The table pools 18 intervals and 110,592 calls per workload per variant. Values are microseconds per classification, shown as median [minimum, maximum] interval averages.

| Workload | Original µs | Optimized µs | Time reduction |
|---|---:|---:|---:|
| Bundled real exemplars | 71.271 [69.492, 73.266] | 44.364 [43.242, 46.282] | 37.75% |
| Small landmark perturbations | 70.147 [69.010, 71.932] | 45.107 [44.395, 47.455] | 35.70% |
| Real, noisy, midpoint, orientation | 70.901 [69.235, 72.266] | 52.857 [51.789, 55.185] | 25.45% |
| Far from references | 70.108 [68.020, 71.543] | 64.250 [63.585, 65.395] | 8.36% |

Observed thread allocations average approximately 1,296 bytes/call in the original and 1,240–1,256 bytes/call in final candidate runs (median 1,248). The reduction is 40–56 bytes/call; variation reflects JVM optimization between processes. MXBean reads occur outside measured time intervals and add a negligible approximately 0.008 bytes/call to allocation totals.

The initial dynamic 15-dimension block-loop experiment improved normal inputs but slowed far inputs by about 12%. It was rejected. The final fixed-range implementation improves the far-input workload too. No flaky unit-test speed threshold was added.

These are desktop/JVM classifier measurements. Phone/ART timing, camera pipeline latency, frame rate, and battery use remain unmeasured because no Android device is connected. This change’s measured gain applies to classifier execution rather than total app speed.

Reproduce the independent comparisons after compiling the relevant runtime jar:

```bash
python run_validation.py /home/earthstone/projects/shipaton-optimization-baseline-20260928 baseline-repeat all
python run_validation.py /home/earthstone/projects/shipaton-optimized-20260928 candidate-repeat all
python summarize_timings.py baseline-repeat candidate-repeat
```

`OptimizationValidationTest.kt`, `InvokeValidation.java`, and `run_validation.py` are external validation tooling. The launcher loads the immutable baseline harness against the selected application jar; it reads original reference data. Keep these tools outside production source sets.

Machine-readable evidence: `final-benchmark-summary.json`, `final-regression-results.json`, `source-asset-audit.json`, and `protected-baseline-hashes.json`. Raw final comparison intervals are `baseline-standalone-2-timings.csv`, `candidate-final-1-timings.csv`, `baseline-standalone-3-timings.csv`, and `candidate-final-2-timings.csv`; their corresponding signature files contain the exact outputs. Full candidate build/test/lint output is `candidate-final-gradle.log`; Python output is `candidate-final-pytest.log`.

Candidate APK SHA256: `3c5e4416584b22a62f44ef68e7f0d938e26a4051d7c0207cb2ab7cbbfe83cdea`.
