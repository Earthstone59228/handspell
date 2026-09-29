# k-NN photo-to-live adaptation sweep — 2026-09-29

## Question and data

Can a mean shift learned from saved live A–D examples make photo references pass the drill gate without accepting many wrong targets? This is an exploratory saved-data check, not a live-camera result.

The reference source is `android/app/src/main/assets/classifier/references-v1.csv`. Removing the 98 team-recorded live A–D rows leaves 1,438 ASLYset photo references. The evaluation samples are those 98 team rows. For each held-out letter in A–D, the shift is the mean of three per-letter differences: mean live vector minus mean photo vector for the *other* three letters. The held-out letter's live vectors are never used to fit its shift. The shift, scaled by α, is applied to the photo references for every static letter. No live references are added to the classifier. Each held-out letter is then classified with k-NN using k=3, 5, or 9.

The score uses the Kotlin k-NN distance and weighted votes from `training/scripts/evaluate_live_gate.py`. The gate uses target probability and runner-up margin, plus one of three distance rules:

- **Absolute:** Kotlin's global nearest distance, using nearest *shape* distance for A–D, must be at most the listed threshold.
- **Relative:** distance to the nearest reference *of the target letter*, divided by that letter's median within-reference nearest distance, must be at most the listed ratio. A–D use shape distance in both terms; other letters use weighted full distance.
- **p/m only:** probability and margin, with no distance check.

The sweep considers α={0, 0.5, 1}, k={3, 5, 9}, probability thresholds {0.70, 0.80, 0.85, 0.90, 0.95}, margins {0.10, 0.20, 0.30}, absolute thresholds {0.32, 0.45, 0.60, 0.80}, and relative ratios {0.75, 1, 1.5, 2, 3, 4}. For each α/k/gate family, the table shows the highest mean true-accept setting that meets both false-accept limits, if one exists. Otherwise it shows the highest true-accept setting and marks it infeasible. **Mean true-accept** averages the four held-out letter rates. **False-accept** tests each saved pose against every *other* static letter as a drill target. Feasibility requires aggregate false-accept ≤5% and each target letter's false-accept ≤10%.

| shift α | k | mean top-1 | gate | threshold | p | margin | mean true-accept | false-accept | worst target | feasible |
| ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |
| 0.0 | 3 | 0.480 | absolute | 0.60 | 0.95 | 0.30 | 0.376 | 0.006 | S 0.071 | yes |
| 0.0 | 3 | 0.480 | relative | 4.00 | 0.95 | 0.30 | 0.339 | 0.009 | S 0.071 | yes |
| 0.0 | 3 | 0.480 | p/m only | — | 0.95 | 0.30 | 0.451 | 0.020 | X 0.194 | no |
| 0.0 | 5 | 0.480 | absolute | 0.80 | 0.95 | 0.30 | 0.377 | 0.010 | X 0.092 | yes |
| 0.0 | 5 | 0.480 | relative | 4.00 | 0.80 | 0.30 | 0.328 | 0.010 | S 0.071 | yes |
| 0.0 | 5 | 0.480 | p/m only | — | 0.80 | 0.30 | 0.433 | 0.021 | X 0.214 | no |
| 0.0 | 9 | 0.451 | absolute | 0.60 | 0.70 | 0.30 | 0.306 | 0.005 | S 0.071 | yes |
| 0.0 | 9 | 0.451 | relative | 4.00 | 0.70 | 0.30 | 0.260 | 0.009 | S 0.071 | yes |
| 0.0 | 9 | 0.451 | p/m only | — | 0.70 | 0.30 | 0.330 | 0.019 | X 0.194 | no |
| 0.5 | 3 | 0.550 | absolute | 0.80 | 0.95 | 0.30 | **0.503** | 0.013 | L 0.092 | **yes** |
| 0.5 | 3 | 0.550 | relative | 4.00 | 0.95 | 0.30 | 0.434 | 0.010 | S 0.071 | yes |
| 0.5 | 3 | 0.550 | p/m only | — | 0.95 | 0.30 | 0.503 | 0.017 | X 0.122 | no |
| 0.5 | 5 | 0.522 | absolute | 0.80 | 0.80 | 0.30 | 0.472 | 0.013 | L 0.092 | yes |
| 0.5 | 5 | 0.522 | relative | 4.00 | 0.80 | 0.30 | 0.403 | 0.010 | S 0.061 | yes |
| 0.5 | 5 | 0.522 | p/m only | — | 0.80 | 0.30 | 0.472 | 0.017 | X 0.122 | no |
| 0.5 | 9 | 0.490 | absolute | 0.80 | 0.70 | 0.30 | 0.444 | 0.013 | L 0.092 | yes |
| 0.5 | 9 | 0.490 | relative | 4.00 | 0.70 | 0.30 | 0.374 | 0.010 | O 0.061 | yes |
| 0.5 | 9 | 0.490 | p/m only | — | 0.70 | 0.30 | 0.444 | 0.018 | X 0.122 | no |
| 1.0 | 3 | 0.609 | absolute | 0.60 | 0.95 | 0.30 | 0.376 | 0.002 | L 0.031 | yes |
| 1.0 | 3 | 0.609 | relative | 3.00 | 0.95 | 0.30 | 0.112 | 0.000 | L 0.010 | yes |
| 1.0 | 3 | 0.609 | p/m only | — | 0.95 | 0.30 | 0.550 | 0.014 | L 0.163 | no |
| 1.0 | 5 | 0.619 | absolute | 0.60 | 0.70 | 0.30 | 0.384 | 0.003 | L 0.031 | yes |
| 1.0 | 5 | 0.619 | relative | 3.00 | 0.70 | 0.30 | 0.120 | 0.000 | L 0.010 | yes |
| 1.0 | 5 | 0.619 | p/m only | — | 0.70 | 0.30 | 0.543 | 0.018 | L 0.163 | no |
| 1.0 | 9 | 0.556 | absolute | 0.60 | 0.90 | 0.30 | 0.326 | 0.002 | L 0.031 | yes |
| 1.0 | 9 | 0.556 | relative | 3.00 | 0.90 | 0.30 | 0.094 | 0.000 | L 0.010 | yes |
| 1.0 | 9 | 0.556 | p/m only | — | 0.70 | 0.30 | 0.467 | 0.017 | L 0.163 | no |

## Selected setting and limits

The best setting **within this sweep's constraints** is α=0.5, k=3, absolute distance ≤0.80, target probability ≥0.95, and margin ≥0.30. Mean true-accept is 0.503; aggregate false-accept is 0.013, and the worst target is L at 0.092. True-accept by held-out letter is A 0.458, B 0.889, C 0.542, D 0.125. Even this selected setting rejects most held-out D samples.

These thresholds were selected on the same four held-out letters used to report them. There is no independent live E–Y set, no new signer test, and no temporal sequence for evaluating hold-to-confirm. The 0.80 distance threshold is much looser than the app's current 0.32 gate. This is a research candidate for comparison with the MLP, not a demonstrated live-app fix. No app code or shipped reference asset was changed.

Reproduce the table with:

```bash
training/.venv/bin/python training/scripts/sweep_knn_adaptation.py --refs android/app/src/main/assets/classifier/references-v1.csv --live training/data/team-references-v1.csv
```
