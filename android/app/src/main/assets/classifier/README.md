# Classifier assets

These files live here, and **none is ever written by hand**:

| File | Produced by | Read by |
|---|---|---|
| `references-v1.csv` | `training/scripts/build_references.py` or `training/scripts/import_aslyset.py` | `vision/classify/KnnLetterClassifier.kt` |
| `mlp-v1.bin` (+ `mlp-v1.json`) | `training/scripts/export_weights.py` | `vision/classify/MlpWeights.kt` |
| `words-v3.bin` (+ `.json`): 12 free words + "other" | `training/scripts/train_words_conv.py` (ISLR landmarks, CC BY 4.0) | `vision/words/WordNet.kt` |
| `words-pro-v3.bin` (+ `.json`): 24 Pro words + "other" | `training/scripts/train_words_conv.py` | `vision/words/WordNet.kt` |

The letter files encode the normalisation spec version in their own bytes (`# spec_version=1` in the CSV, an
int32 in the HSML header) and the loaders refuse anything that does not match
`NormalizedHand.SPEC_VERSION`; the word files carry the HSCN container's own spec version and CRC32 (`training/handspell/words_net.py`). Editing a float by hand would pass that check and silently move an
exemplar, so don't: re-run the script and commit its output.

## Rebuilding the reference set

```
adb pull /sdcard/Android/data/dev.handspell.app/files/captures ./captures
# or, in the debug build's capture screen, tap "Export ZIP" and share it; unzip it into ./captures
cd training
uv run python scripts/build_references.py --captures ../captures
```

The script normalises every captured frame through `handspell/normalize.py` — the same spec the app
runs — dedupes to one exemplar per distinct pose (0.05 apart, 64 per letter max) and writes
`references-v1.csv` here. It prints a per-letter count; a letter with zero exemplars is a letter the
app will not offer, which is the intended behaviour, not a bug to paper over.

## Rebuilding the weights

```
cd training
uv run python scripts/export_weights.py --npz runs/mlp-v1.npz
```

## When `references-v1.csv` is absent

The file is bundled in this build. If it is absent or damaged in another build, the required container behavior is:

1. `KnnLetterClassifier.load` throws `ClassifierAssetException.Missing` when `AssetManager.open`
   cannot find the file. It never returns an empty classifier — a classifier with no exemplars would
   report `supportedLetters = []`, and every drill, story and speed screen would render empty with no
   explanation.
2. `AppContainer` catches that at construction and wires the real `CameraSignDetector` anyway, with
   `classifier = null` and `initialFailure` set. Its `status` is `Failed` from the moment it is
   constructed, so the camera screen never binds the analyser and no frame is ever scored against a
   missing classifier. `FakeSignDetector` is **not** wired here; it stays available for UI work and
   screenshot tests only.
3. A missing reference set surfaces as `DetectorStatus.Failed(exception.messageId, exception)` — the
   camera screen shows the real message from `strings.xml` and a retry, per docs/QUALITY.md §3. It
   must not fall back to the fake detector, and must not show a camera preview that silently scores
   nothing.
4. A corrupt, mis-versioned or unreadable file is a *different* failure (`Malformed`,
   `SpecVersionMismatch`, `ChecksumMismatch`, `Unreadable`) with its own message, because "we haven't
   recorded the data yet" and "the data in your install is damaged" are different things to tell
   someone.

The same rule applies to `mlp-v1.bin` with one addition: if stage 1 loaded successfully and stage 2
fails for any reason, the container uses the stage-1 classifier and shows a visible notice
(docs/CLASSIFIER.md §4), rather than failing the detector outright.

## Public-data expansion

The expanded prototype includes real ASLYset-derived static-letter data (CC BY 4.0), retains
original team A–D examples, and excludes J/Z. See `NOTICE`, `training/DATA.md`, and the
reproducible import/evaluation commands in `docs/research/static-alphabet-integration-2026-09-28.md`.
All data and illustrations retain their original attribution/license.
