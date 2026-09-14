# Windows hand-skeleton preview

A standalone desktop preview that opens the Windows webcam, runs MediaPipe
Hand Landmarker on every frame, and draws the 21-landmark skeleton for up to
two hands. It exists so gesture work can be eyeballed on a Windows laptop
without an Android build.

It also identifies the letter being held, by running the app's own stage-1
pipeline: the same normaliser (`handspell/normalize.py`), the same k-NN
(`KnnLetterClassifier`: 66-d Euclidean with the orientation block weighted 0.5,
`k = 5`, neighbours voting `1 / (d + 0.01)`) and the same `FeedbackThresholds`.
The port lives in `reference_classifier.py`.

The point is to measure recognition on a laptop before any Android build is
involved. Nothing under `android/` imports this, and it does not replace the
app's detector stack — the Android side still wires the real
`CameraSignDetector` (see `di/AppContainer.kt`).

## Layout

The preview lives in a separate folder, `preview-windows/`, next to this repo's
checkout (not inside it):

```
preview-windows/
├── hand_preview.py           # the preview app
├── reference_classifier.py   # the app's stage-1 pipeline, ported to Python
├── run.bat                   # double-click launcher
├── setup.bat                 # one-time environment setup
├── requirements.txt
└── models/
    ├── hand_landmarker.task
    ├── LICENSE
    └── ATTRIBUTION.txt
```

The model is copied from
`android/app/src/main/assets/models/hand_landmarker.task`, so the preview uses
exactly the same model file as the app.

## Prerequisites

- Windows 10/11 with a working webcam
- [uv](https://docs.astral.sh/uv/) on `PATH`
- No system Python required; `setup.bat` installs Python 3.12 locally into the
  preview folder via uv

## Setup (once)

Double-click `setup.bat`, or run the equivalent:

```powershell
cd preview-windows
uv python install 3.12
uv venv .venv --python 3.12
uv pip install --python .venv\Scripts\python.exe -r requirements.txt
```

Notes:

- The setup scripts clear `HTTP_PROXY` / `HTTPS_PROXY` / `ALL_PROXY` for the
  window, because the proxy environment variables on the original machine break
  package downloads.
- Everything (Python, cache, virtualenv) stays inside `preview-windows/`; the
  system is not modified. Deleting the folder removes it all.

## Run

Double-click `run.bat`, or:

```powershell
.venv\Scripts\python.exe hand_preview.py
```

## Controls

| Key | Action |
| --- | --- |
| `q` / `Esc` | quit |
| `m` | toggle selfie mirroring |
| `h` | toggle the head-up info overlay |
| `i` | toggle the identification readout |
| `e` | print a leave-one-out accuracy report for the reference set |

The head-up overlay shows FPS, detected hand count, mirror state, and
resolution.

## Identification

The readout sits top-right: the letter the classifier ranked first, its
smoothed probability and its margin over the runner-up, the nearest-exemplar
distance against the 0.32 match gate, and how far the hold-to-confirm timer has
run. The verdict reuses the app's own names:

| Verdict | Meaning |
| --- | --- |
| `MATCH` | every gate passed continuously for 400 ms, so the app would advance |
| `ADJUST` | close, but at least one gate is not satisfied yet |
| `NOT RECOGNISED` | the nearest exemplar is further than 0.45, so nothing is named |
| `NO HAND` | fewer than three debounce frames without landmarks |

One deliberate difference: the app compares against the letter a drill is
asking for, and there is no drill here, so the gates are evaluated against the
letter the classifier currently ranks first. The readout therefore means "if
this letter were the target, would the app accept it?" — which is the question
the preview exists to answer.

The reference set is read from
`android/app/src/main/assets/classifier/references-v1.csv`, the file the
desktop recorder writes. If it is absent the preview says so and draws
landmarks only.

Press `e`, or run `--eval`, for a leave-one-out report: every exemplar is
classified against the set with that exemplar removed. That is the honest
offline question, because a real hand is never a member of the reference set;
scoring the set against itself would only be measuring memory.

## CLI options

```
--camera N         OpenCV camera index (default 0)
--model PATH       alternate hand_landmarker.task path
--width N          requested capture width (default 1280)
--height N         requested capture height (default 720)
--max-hands N      max hands to detect (default 2)
--min-detection X  min detection confidence (default 0.5)
--no-flip          do not mirror the preview
--repo PATH        Handspell checkout, for the normaliser (default: the sibling
                   handspell/ folder)
--references PATH  references-v1.csv to identify against (default: the repo's
                   assets copy)
--no-identify      skip the reference set and draw landmarks only
--eval             print the leave-one-out report and exit; no camera needed
```

## Handedness

MediaPipe's handedness label assumes a mirrored (selfie) image. With mirroring
on (the default), `Left` is the viewer's left hand. Turning mirroring off with
`m` inverts the labels, which is correct if the camera is used as a
third-person view.

Identification is designed to survive that toggle. With an unmirrored frame
MediaPipe reports the opposite handedness for the same physical hand, and the
normaliser's first step negates x back, so the two cancel out. That rests on the
handedness head being right, which is why mirroring stays on by default.

## Model and licensing

`hand_landmarker.task` is the MediaPipe hand landmark model, Apache-2.0
licensed. `models/LICENSE` and `models/ATTRIBUTION.txt` are copied alongside it
to satisfy the attribution requirements. The URL used to obtain the model:

```
https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task
```

## Troubleshooting

- **Blank window or "Could not open camera index 0"**: try
  `hand_preview.py --camera 1` (then 2, ...). Close anything else using the
  webcam (Teams, OBS, browser).
- **uv not found**: install it from https://docs.astral.sh/uv/ and re-open the
  terminal.
- **Slow first start**: the model loads once at startup; subsequent frames are
  real-time. Lower `--width`/`--height` to 640x480 on weak machines.
- **Download failures in `setup.bat`**: verify the proxy variables are cleared
  as described above, then retry.
