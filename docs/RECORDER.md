# Reference recorder

`recorder/` is a desktop tool that records stage-1 hand-pose exemplars and writes them to
`android/app/src/main/assets/classifier/references-v1.csv` — the only file `KnnLetterClassifier`
reads (`docs/CLASSIFIER.md` §3).

For calibration evidence, prefer the in-app debug-only capture screen (`docs/CLASSIFIER.md` §7).
It is opened from **Settings → Calibration capture** in a debug build, uses an opaque signer ID, and
writes full 141-column CSVs under the app's external-files `captures/<signer_id>/` directory. Pull
those files outside the repository, review consent, then use
`training/scripts/build_references.py` to derive a candidate reference set. The activity deliberately
remains non-exported; it cannot be opened directly with `adb shell am start`.

## Why it is trustworthy

The tool does not reimplement the pipeline. It imports

- `handspell/normalize.py` — the same normaliser the Kotlin `DefaultHandNormalizer` is pinned to by
  the golden-vector tests, and
- `handspell/references.py` — the same `write_reference_csv` that `build_references.py` uses,

so what it records is by construction in the same 66-d space the app measures distances in, and the
file it produces parses under the same rules the Android loader enforces: a `# spec_version=1`
comment, a `letter,f0..f65` header, one row per exemplar with 66 finite floats, letters in the
static set, and never J or Z.

## Frame convention

The webcam frame is mirrored **before** MediaPipe sees it, exactly as the Android `FrameConverter`
does. The normalisation spec (`docs/CLASSIFIER.md` §1) is defined in that selfie-mirrored,
display-upright frame, which is why the mirror is on by default and why it is a key
(`-`, or `--no-mirror`) rather than something baked in.

With mirroring off, MediaPipe reports the opposite handedness for the same physical hand and the
normaliser's first step negates x back, so the two are meant to cancel out. That is a documented
MediaPipe behaviour rather than something this tool can verify, so the window shows a warning while
mirroring is off, and the mirrored path — the one the app itself runs — is the default.

## Setup

Windows: double-click `setup.bat`. Any OS, with `uv` on `PATH`:

```bash
cd recorder
uv python install 3.12
uv venv .venv --python 3.12
uv pip install --python .venv/bin/python -r requirements.txt
```

`.venv/` and the matplotlib font cache are gitignored; `setup.bat` recreates the first from
`requirements.txt`.

## Run

```
run.bat --selftest     # no camera
run.bat                # record
```

## Controls

| Key | Action |
| --- | --- |
| `a`–`z` | record the current pose as that letter |
| `Enter` | save, then copy the saved file to the clipboard |
| `Backspace` | undo the last sample |
| `Tab` | toggle the dropped-sample readout |
| `-` | toggle mirroring |
| `Esc` | save and quit |

Each keypress records one sample, so a letter can hold many exemplars. The 0.05-distance greedy
dedupe from `docs/CLASSIFIER.md` §3 is applied as you go — hold a pose and tap the key repeatedly and
the near-identical frames are dropped rather than stacked, so varying the hand slightly between
presses is what actually adds coverage. The per-letter cap is 64.

J and Z are excluded: they are produced with motion and have no single-frame handshape, and
`KnnLetterClassifier` rejects a reference row that names them.

## Saving

Every save writes the file first and then puts the **file's contents** on the clipboard, read back
from disk rather than from the in-memory set, so what you paste is by definition what the app will
read. `--no-clipboard` turns this off. A clipboard failure is reported in the window and never costs
you the recording.

Re-running the tool appends to an existing file rather than replacing it, so a session can be split
across days. `--fresh` starts over.

## Verification

`run.bat --selftest` exercises normalise → write → read back with no camera and prints the spec
version, vector dimension and thresholds it picked up, which is enough to tell a broken environment
from a broken recording.

Accuracy is **not** measured here. The classifier lives in exactly one place — the ported stage-1
pipeline in the desktop preview — and `preview-windows/run.bat --eval` runs a leave-one-out report
over whatever `references-v1.csv` holds (`docs/WINDOWS_PREVIEW.md`). Splitting the k-NN across two
tools would be two implementations to keep in step, which is how the numbers stop meaning anything.

## Options

```
--repo PATH          Handspell checkout (default: the repo this script lives in, or
                     $HANDSPELL_REPO)
--out PATH           output CSV (default: the Android assets path above)
--model PATH         hand_landmarker.task (default: the repo's Android assets copy)
--camera N           OpenCV camera index (default: 0)
--width / --height   requested capture size (default: 1280x720)
--min-detection X    minimum hand detection confidence (default: 0.5)
--fresh              ignore an existing output file
--no-mirror          do not mirror the frame before MediaPipe
--no-clipboard       do not copy the saved CSV to the clipboard
--selftest           no camera, just prove the pipeline works
```

## What this does not do

Two gaps, both consequences of skipping the capture screen, and both worth closing when it lands:

- **No `signer_id`.** Capture CSVs carry an opaque signer label and are organised
  `<signer_id>/<letter>_<session>.csv`. `references-v1.csv` is a fixed 67-column format the Android
  loader parses strictly, so provenance cannot ride along inside it. Until the capture screen
  exists, signer attribution lives outside the file, and the leave-one-signer-out protocol in
  `docs/CLASSIFIER.md` §8 cannot be run on it — which also means `--eval` reports same-signer
  accuracy wherever it is run, an optimistic number rather than a cross-signer one.
- **No raw capture rows.** The image landmarks, timestamps and device metadata the capture schema
  records are never written, so the xy-image-space fallback in `docs/CLASSIFIER.md` §3 cannot be
  evaluated offline from what this tool leaves behind.
