# Classifier assets

Two files live here, and **neither is ever written by hand**:

| File | Produced by | Read by |
|---|---|---|
| `references-v1.csv` | `training/scripts/build_references.py` | `vision/classify/KnnLetterClassifier.kt` |
| `mlp-v1.bin` (+ `mlp-v1.json`) | `training/scripts/export_weights.py` | `vision/classify/MlpWeights.kt` |

Both encode the normalisation spec version in their own bytes (`# spec_version=1` in the CSV, an
int32 in the HSML header) and the loaders refuse anything that does not match
`NormalizedHand.SPEC_VERSION`. Editing a float by hand would pass that check and silently move an
exemplar, so don't: re-run the script and commit its output.

## Rebuilding the reference set

```
adb pull /sdcard/Android/data/dev.handspell.app/files/captures ./captures
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

It is absent right now: no capture session has happened yet, and an empty or invented reference set
would be worse than none. The required container behaviour, in order:

1. `KnnLetterClassifier.load` throws `ClassifierAssetException.Missing` when `AssetManager.open`
   cannot find the file. It never returns an empty classifier — a classifier with no exemplars would
   report `supportedLetters = []`, and every drill, story and speed screen would render empty with no
   explanation.
2. `AppContainer` catches that at construction and **keeps `FakeSignDetector` wired** as the
   `SignDetector`. The fake is a scripted demo, not a classifier, so this is only acceptable while
   the app is visibly pre-capture; it is not a release fallback.
3. Once `CameraSignDetector` is wired, a missing reference set must surface as
   `DetectorStatus.Failed(exception.messageId, exception)` — the camera screen then shows the real
   message from `strings.xml` and a retry, per docs/QUALITY.md §3. It must not fall back to the fake
   detector, and must not show a camera preview that silently scores nothing.
4. A corrupt, mis-versioned or unreadable file is a *different* failure (`Malformed`,
   `SpecVersionMismatch`, `ChecksumMismatch`, `Unreadable`) with its own message, because "we haven't
   recorded the data yet" and "the data in your install is damaged" are different things to tell
   someone.

The same rule applies to `mlp-v1.bin` with one addition: if stage 1 loaded successfully and stage 2
fails for any reason, the container uses the stage-1 classifier and shows a visible notice
(docs/CLASSIFIER.md §4), rather than failing the detector outright.
