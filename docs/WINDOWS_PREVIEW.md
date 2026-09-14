# Windows hand-skeleton preview

A standalone desktop preview that opens the Windows webcam, runs MediaPipe
Hand Landmarker on every frame, and draws the 21-landmark skeleton for up to
two hands. It exists so gesture work can be eyeballed on a Windows laptop
without an Android build.

This preview is intentionally independent of the app's detector stack: it draws
landmarks only and does not run the k-NN/MLP classifiers. The Android app now
wires the real `CameraSignDetector` (see `di/AppContainer.kt`), so the preview
stays a lightweight way to eyeball landmark quality without an Android build.

## Layout

The preview lives in a separate folder, `preview-windows/`, next to this repo's
checkout (not inside it):

```
preview-windows/
├── hand_preview.py   # the preview app
├── run.bat           # double-click launcher
├── setup.bat         # one-time environment setup
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

The overlay shows FPS, detected hand count, mirror state, and resolution.

## CLI options

```
--camera N         OpenCV camera index (default 0)
--model PATH       alternate hand_landmarker.task path
--width N          requested capture width (default 1280)
--height N         requested capture height (default 720)
--max-hands N      max hands to detect (default 2)
--min-detection X  min detection confidence (default 0.5)
--no-flip          do not mirror the preview
```

## Handedness

MediaPipe's handedness label assumes a mirrored (selfie) image. With mirroring
on (the default), `Left` is the viewer's left hand. Turning mirroring off with
`m` inverts the labels, which is correct if the camera is used as a
third-person view.

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
