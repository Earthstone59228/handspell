# Static alphabet integration — 2026-09-28

The expanded prototype includes all **24 static letters A–Y, excluding J and Z**. D already had
team references; the new public data supplements A–D and supplies E–Y. Every static alphabet
menu placeholder is replaced with a wireframe derived from a real reference. J/Z retain their
motion-disabled state. No synthetic letter poses or instructional images from Lifeprint are used.

## Source and reproducibility

Miguel Rivera (2019), [ASLYset V1](https://data.mendeley.com/datasets/xs6mvhx6rh/1),
DOI 10.17632/xs6mvhx6rh.1, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
The original archive's published SHA256 was verified. Of 4,800 letter images, 4,786 yielded one
nondegenerate hand; SP/FN were excluded. The importer uses the same bundled hand model and
normalizer as Android, including MediaPipe's reported handedness. It records User1–User4 for
held-out-signer evaluation. The source release's physical-right-hand description does not override
the model label: source camera mirroring differs. Source photos/archive stay outside the repository.

```bash
# From training/, using the project's Python 3.12 environment:
python scripts/import_aslyset.py \
  --archive ~/datasets/aslyset/ASLYset.rar \
  --work-dir ~/datasets/aslyset/extracted \
  --out data/aslyset \
  --base-references /path/to/original-team-references-v1.csv
python scripts/evaluate_aslyset.py \
  --data data/aslyset/landmarks-v1.npz \
  --out ../docs/research/aslyset-evaluation-2026-09-28.json
# Copy generated data/aslyset/references-v1.csv to the classifier assets, then:
cd ..
scripts/refresh-letters.sh
```

The original 98 team A–D exemplars are retained at the start of each letter's block. To make reruns
self-contained, `training/data/team-references-v1.csv` stores the original input unchanged.
The combined set caps each letter at 64, totaling 1,536 examples. Source/license/modifications and
checksums are in `training/data/aslyset/provenance.json`, the root/bundled `NOTICE`, and the dataset
license record. ASLYset-derived vectors and illustrations retain CC BY 4.0; application code is MIT.

## Camera and reference orientation

Analysis now rotates to display-upright **before** mirroring in display space. The former
sensor-space mirror made portrait analysis differ from the preview by 180 degrees. Overlay and
thumbnail consume the same upright frame. This was necessary for directional G/Q and K/P; live
phone verification remains pending. Existing desktop recordings already use the upright selfie frame.

Both reference guides restore the stored orientation block instead of rotating every hand upward.
The first real exemplar produces the same pose in the menu and native guide. A shared golden test
checks reconstruction against raw input hands, including left-hand examples.

## Evaluation and practical limitations

Four signer-held-out k-NN folds, each with signer-balanced 64-per-letter references and no original
team data in the folds: **macro F1 92.5%**. This is still-image classifier performance,
not live-camera accuracy. The strict per-frame confirmation-entry conditions accept only about
25.3% of correctly labeled held-out images; the existing distance/probability/margin/hold limits
were retained. Four incorrect per-frame entries occurred across the folds; temporal false-match
rates cannot be inferred from still images. An experimental two-hidden-layer MLP baseline did not
beat k-NN overall and was not exported or bundled.

| Letter | Extracted images | Top-1 recall | Correct strict entries | Wrong entries into this letter |
|---|---:|---:|---:|---:|
| A | 200 | 94.5% | 10.5% | 0 |
| B | 198 | 100.0% | 85.9% | 0 |
| C | 200 | 95.0% | 25.0% | 0 |
| D | 200 | 94.0% | 2.5% | 0 |
| E | 200 | 99.5% | 62.0% | 0 |
| F | 200 | 96.0% | 2.0% | 0 |
| G | 199 | 97.5% | 31.7% | 0 |
| H | 200 | 95.5% | 2.5% | 0 |
| I | 200 | 100.0% | 41.5% | 0 |
| K | 200 | 100.0% | 42.0% | 0 |
| L | 200 | 100.0% | 25.5% | 0 |
| M | 198 | 90.4% | 42.9% | 1 |
| N | 199 | 93.0% | 28.1% | 0 |
| O | 196 | 99.5% | 38.8% | 0 |
| P | 199 | 97.0% | 4.0% | 0 |
| Q | 200 | 90.0% | 6.0% | 0 |
| R | 200 | 54.0% | 5.5% | 2 |
| S | 200 | 91.0% | 10.0% | 0 |
| T | 200 | 68.0% | 10.0% | 0 |
| U | 199 | 75.4% | 21.6% | 1 |
| V | 198 | 92.9% | 21.7% | 0 |
| W | 200 | 100.0% | 17.5% | 0 |
| X | 200 | 98.5% | 49.5% | 0 |
| Y | 200 | 100.0% | 21.5% | 0 |

R, T and U fall below the earlier 80% top-1 recall target and have a concise experimental-recognition
note in the shared camera drill. All added letters still require live held-out-device trials before
meeting `docs/CLASSIFIER.md` §9's release criteria. The full 24-letter expansion is available for
prototype practice and evaluation, **not designated release-validated**. Skip remains available
when recognition is difficult. Do not treat an absent match as proof of incorrect signing.

## Back arrow and validation

Web and native back chevrons have no square touch indication. Dragging gives at most 8 pixels
(web) or 8 dp (native) of resisted movement, followed by a spring return. Drag release cancels
navigation; taps and accessible clicks remain available. The web preserves a blue chevron cue for
keyboard focus and honors reduced motion.

Browser interaction checks passed for long press, drag resistance, spring return, drag cancellation,
tap, keyboard activation and reduced motion. All 24 static menu cards have a reference wireframe;
J/Z are disabled. Kotlin tests load the actual bundled CSV and confirm every reference guide through
the real classifier and hold-to-confirm engine. Python training tests pass (60). Final Android unit,
lint and APK results are recorded in the implementation log. No Android device was connected for
native gestures, camera orientation, TalkBack or Test Store purchase testing.
