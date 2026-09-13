"""Smoke test: construct a MediaPipe HandLandmarker in IMAGE mode and run it
on a synthetic blank image, verifying the model file loads and inference runs
without error (no assertion on hand-detection results, since a blank image
has no hands).

Usage:
    uv run training/scripts/smoke_landmarker.py
(or, from within training/:  uv run scripts/smoke_landmarker.py)
"""

import os
from pathlib import Path

# Work around a reproducible SIGKILL when MediaPipe/TFLite initializes while
# desktop/session env vars (Wayland/X11/D-Bus/Hyprland/terminal) are present
# (observed on this box's graphical session). This task runs fully headless
# (CPU delegate, IMAGE mode) and needs none of them. Must happen before
# importing mediapipe.
_SESSION_VAR_PREFIXES = ("XDG_", "WAYLAND_", "DBUS_", "HYPRLAND_", "HYPRCURSOR_", "KITTY_", "UWSM_")
_SESSION_VARS = ("DISPLAY", "TERM", "SYSTEMD_EXEC_PID", "JOURNAL_STREAM", "INVOCATION_ID", "NOTIFY_SOCKET")
for _k in list(os.environ):
    if _k.startswith(_SESSION_VAR_PREFIXES) or _k in _SESSION_VARS:
        os.environ.pop(_k, None)

import numpy as np
import mediapipe as mp
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.core.base_options import BaseOptions

MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "hand_landmarker.task"


def main() -> None:
    if not MODEL_PATH.exists():
        raise SystemExit(f"Model file not found: {MODEL_PATH}")

    options = vision.HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(MODEL_PATH)),
        running_mode=vision.RunningMode.IMAGE,
        num_hands=2,
    )

    with vision.HandLandmarker.create_from_options(options) as landmarker:
        # Synthetic blank (black) RGB image.
        blank = np.zeros((480, 640, 3), dtype=np.uint8)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=blank)

        result = landmarker.detect(mp_image)

        print("HandLandmarker constructed and ran successfully.")
        print(f"Detected hands: {len(result.hand_landmarks)} (expected 0 on a blank image)")


if __name__ == "__main__":
    main()
