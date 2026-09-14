#!/usr/bin/env python3
"""Record stage-1 reference exemplars for the Handspell k-NN classifier.

Opens the webcam, runs MediaPipe Hand Landmarker on every frame, and turns the
pose you are holding into a 66-float vector when you press a letter key. The
result is written straight to `references-v1.csv` in the exact format
`KnnLetterClassifier.load` reads (docs/CLASSIFIER.md sections 3 and 7).

Each keypress records one sample, so a letter can hold many exemplars -- the
spec's per-letter cap is 64. Near-identical samples are dropped by the same
greedy 0.05-distance dedupe `training/handspell/references.py` uses, so holding
a pose and tapping the key repeatedly adds variety rather than duplicates.

The normaliser and the CSV writer are imported from the repo's `training`
package rather than reimplemented here, so what this tool records is by
construction in the same space the Android app measures distances in.

Controls:
    a-z     record the current pose as that letter (J and Z are not static)
    ENTER   save, then copy the saved file to the clipboard
    BKSP    undo the last sample
    TAB     toggle the dropped-sample readout
    -       toggle mirroring
    ESC     save and quit
"""

from __future__ import annotations

import argparse
import csv
import os
import subprocess
import sys
import time
from pathlib import Path

# mediapipe imports matplotlib somewhere in its package init chain. Keep the
# font cache next to this script instead of under %LOCALAPPDATA%.
os.environ.setdefault("MPLCONFIGDIR", str(Path(__file__).resolve().parent / ".mpl-cache"))

import numpy as np

WINDOW_TITLE = "Handspell reference recorder"

# This script lives at <repo>/recorder/, so the checkout is one level up. The
# environment override stays useful for running a copy from outside the repo,
# but the default needs no machine-specific path.
DEFAULT_REPO = Path(os.environ.get("HANDSPELL_REPO") or Path(__file__).resolve().parents[1])

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
    "hand_landmarker/float16/latest/hand_landmarker.task"
)

# MediaPipe's canonical hand landmark connections (21 points).
HAND_CONNECTIONS = (
    (0, 1), (1, 2), (2, 3), (3, 4),
    (0, 5), (5, 6), (6, 7), (7, 8),
    (5, 9), (9, 10), (10, 11), (11, 12),
    (9, 13), (13, 14), (14, 15), (15, 16),
    (13, 17), (17, 18), (18, 19), (19, 20),
    (0, 17),
)

SKELETON_COLOUR = (0, 255, 80)
TEXT_COLOUR = (235, 235, 235)
DIM_COLOUR = (150, 150, 150)
OK_COLOUR = (90, 240, 120)
WARN_COLOUR = (80, 180, 255)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Record hand-pose exemplars into references-v1.csv.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--repo", type=Path, default=DEFAULT_REPO,
        help=f"Handspell checkout (default: {DEFAULT_REPO})",
    )
    parser.add_argument(
        "--out", type=Path, default=None,
        help="output CSV (default: <repo>/android/app/src/main/assets/classifier/references-v1.csv)",
    )
    parser.add_argument(
        "--model", type=Path, default=None,
        help="hand_landmarker.task (default: <repo>/android/app/src/main/assets/models/hand_landmarker.task)",
    )
    parser.add_argument("--camera", type=int, default=0, help="OpenCV camera index (default: 0)")
    parser.add_argument("--width", type=int, default=1280)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument(
        "--min-detection", type=float, default=0.5,
        help="minimum hand detection confidence (default: 0.5)",
    )
    parser.add_argument(
        "--fresh", action="store_true",
        help="ignore an existing output file and start from empty",
    )
    parser.add_argument(
        "--selftest", action="store_true",
        help="no camera: exercise normalise + write + read back, then exit",
    )
    parser.add_argument(
        "--no-mirror", action="store_true",
        help="do not mirror the frame before MediaPipe (default: mirrored, like the app)",
    )
    parser.add_argument(
        "--no-clipboard", action="store_true",
        help="do not copy the saved CSV to the clipboard",
    )
    return parser.parse_args(argv)


def import_training(repo: Path) -> dict:
    """Import the repo's normaliser and reference writer -- the same code the
    golden-vector tests pin the Kotlin implementation against."""
    training = repo / "training"
    if not (training / "handspell" / "normalize.py").is_file():
        raise FileNotFoundError(
            f"{training} does not look like the handspell training package. "
            "Pass --repo <path to the handspell checkout>."
        )
    sys.path.insert(0, str(training))

    from handspell.normalize import SPEC_VERSION, VECTOR_DIM, normalize
    from handspell.references import (
        DEDUPE_DISTANCE,
        MAX_EXEMPLARS_PER_LETTER,
        STATIC_LETTERS,
        format_reference_csv,
        write_reference_csv,
    )

    return {
        "SPEC_VERSION": SPEC_VERSION,
        "VECTOR_DIM": VECTOR_DIM,
        "normalize": normalize,
        "DEDUPE_DISTANCE": DEDUPE_DISTANCE,
        "MAX_EXEMPLARS_PER_LETTER": MAX_EXEMPLARS_PER_LETTER,
        "STATIC_LETTERS": STATIC_LETTERS,
        "format_reference_csv": format_reference_csv,
        "write_reference_csv": write_reference_csv,
    }


def find_model(repo: Path, explicit: Path | None) -> Path:
    candidates = ([explicit] if explicit is not None else []) + [
        repo / "android" / "app" / "src" / "main" / "assets" / "models" / "hand_landmarker.task",
        Path(__file__).resolve().parent / "models" / "hand_landmarker.task",
    ]
    for candidate in candidates:
        if candidate.is_file():
            return candidate.resolve()
    raise FileNotFoundError(
        "hand_landmarker.task not found. Download it from:\n"
        f"{MODEL_URL}\n"
        "and place it at models/hand_landmarker.task next to this script."
    )


def load_existing(path: Path, vector_dim: int) -> dict[str, list[np.ndarray]]:
    """Read a reference CSV back into exemplars, so a later session appends to
    what earlier sessions recorded instead of replacing it."""
    exemplars: dict[str, list[np.ndarray]] = {}
    if not path.is_file():
        return exemplars

    with path.open(newline="", encoding="utf-8") as handle:
        rows = csv.reader(handle)
        header_seen = False
        for row in rows:
            if not row:
                continue
            if row[0].lstrip().startswith("#"):
                continue
            if not header_seen:
                header_seen = True
                continue
            letter = row[0].strip().upper()
            values = [float(text) for text in row[1:]]
            if len(values) != vector_dim:
                raise ValueError(
                    f"{path}: '{letter}' row has {len(values)} floats, expected {vector_dim}"
                )
            exemplars.setdefault(letter, []).append(np.asarray(values, dtype=np.float32))
    return exemplars


def copy_to_clipboard(text: str) -> str | None:
    """Put `text` on the clipboard. Returns None on success, or a short reason.

    The CSV is pure ASCII, so the platform tools need no encoding gymnastics.
    A clipboard failure is reported and never treated as a reason to lose the
    recording -- the file is already written by the time this runs.
    """
    if sys.platform == "win32":
        command = ["clip"]
    elif sys.platform == "darwin":
        command = ["pbcopy"]
    else:
        command = ["xclip", "-selection", "clipboard"]
    try:
        subprocess.run(command, input=text.encode("utf-8"), check=True)
        return None
    except Exception as error:  # noqa: BLE001 - reporting, not handling
        return str(error)


class Recorder:
    """Holds the exemplar set and applies the documented dedupe and cap rules."""

    def __init__(
        self, out: Path, training: dict, dedupe: float, cap: int, clipboard: bool = True
    ) -> None:
        self.out = out
        self.training = training
        self.dedupe = dedupe
        self.cap = cap
        self.clipboard = clipboard
        self.exemplars = load_existing(out, training["VECTOR_DIM"])
        self.order: list[tuple[str, np.ndarray]] = []
        self.degenerate = 0
        self.near_duplicate = 0
        self.over_cap = 0
        self.dirty = False

    @property
    def total(self) -> int:
        return sum(len(vectors) for vectors in self.exemplars.values())

    def count(self, letter: str) -> int:
        return len(self.exemplars.get(letter, ()))

    def add(self, letter: str, vector: np.ndarray | None) -> tuple[bool, str]:
        if vector is None:
            self.degenerate += 1
            return False, "pose too flat to normalise (hand edge-on to the camera)"

        bucket = self.exemplars.setdefault(letter, [])
        if len(bucket) >= self.cap:
            self.over_cap += 1
            return False, f"{letter} already has the maximum {self.cap} exemplars"
        if any(float(np.linalg.norm(vector - other)) < self.dedupe for other in bucket):
            self.near_duplicate += 1
            return False, f"same pose as an existing {letter} exemplar -- vary your hand a little"

        bucket.append(vector)
        self.order.append((letter, vector))
        self.dirty = True
        return True, f"recorded {letter}  (#{len(bucket)})"

    def undo(self) -> str:
        if not self.order:
            return "nothing recorded in this session to undo"
        letter, vector = self.order.pop()
        bucket = self.exemplars.get(letter, [])
        for index in range(len(bucket) - 1, -1, -1):
            if bucket[index] is vector:
                del bucket[index]
                break
        if not bucket:
            self.exemplars.pop(letter, None)
        self.dirty = True
        return f"undid the last {letter}"

    def save(self) -> str:
        if not self.exemplars:
            return "nothing to save yet"
        self.training["write_reference_csv"](self.out, self.exemplars)
        self.dirty = False
        message = f"saved {self.total} exemplars to {self.out}"
        if self.clipboard:
            # Copy what actually landed on disk rather than the in-memory set, so
            # what you paste is by definition what the app will read.
            failure = copy_to_clipboard(self.out.read_text(encoding="utf-8"))
            message += f" | clipboard failed: {failure}" if failure else " | copied to clipboard"
        return message


def open_camera(index: int):
    import cv2

    backends = (cv2.CAP_DSHOW, cv2.CAP_ANY) if sys.platform == "win32" else (cv2.CAP_ANY,)
    last = None
    for backend in backends:
        cap = cv2.VideoCapture(index, backend)
        if cap.isOpened():
            return cap
        cap.release()
        last = f"index {index} (backend {backend})"
    raise RuntimeError(
        f"Could not open camera {last}. Try --camera 1, and close anything else using the webcam."
    )


def key_to_letter(key: int) -> str | None:
    if 97 <= key <= 122:
        return chr(key - 32)
    if 65 <= key <= 90:
        return chr(key)
    return None


def draw_skeleton(frame, landmarks) -> None:
    import cv2

    height, width = frame.shape[:2]
    points = [(round(lm.x * width), round(lm.y * height)) for lm in landmarks]
    for start, end in HAND_CONNECTIONS:
        cv2.line(frame, points[start], points[end], SKELETON_COLOUR, 2, cv2.LINE_AA)
    for index, point in enumerate(points):
        cv2.circle(
            frame, point, 5 if index == 0 else 3, SKELETON_COLOUR, thickness=-1, lineType=cv2.LINE_AA
        )


def draw_panel(
    frame, recorder, training, fps, hand_count, handedness, message, ok, show_drops, mirrored
):
    import cv2

    columns = 8
    for index, letter in enumerate(training["STATIC_LETTERS"]):
        row, column = divmod(index, columns)
        count = recorder.count(letter)
        cv2.putText(
            frame,
            f"{letter}:{count}",
            (14 + column * 62, 66 + row * 26),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.52,
            OK_COLOUR if count else DIM_COLOUR,
            1,
            cv2.LINE_AA,
        )

    cv2.putText(
        frame,
        f"fps {fps:4.1f}   hands {hand_count}   handedness {handedness}   "
        f"mirror {'on' if mirrored else 'OFF'} (-)   total {recorder.total}",
        (14, 26),
        cv2.FONT_HERSHEY_SIMPLEX, 0.55, TEXT_COLOUR, 1, cv2.LINE_AA,
    )
    if show_drops:
        cv2.putText(
            frame,
            f"dropped this session: degenerate {recorder.degenerate}, "
            f"near-duplicate {recorder.near_duplicate}, over-cap {recorder.over_cap}",
            (14, 156),
            cv2.FONT_HERSHEY_SIMPLEX, 0.48, DIM_COLOUR, 1, cv2.LINE_AA,
        )
    if not mirrored:
        cv2.putText(
            frame,
            "mirror OFF -- the Android app runs mirrored; only do this deliberately",
            (14, 182),
            cv2.FONT_HERSHEY_SIMPLEX, 0.48, WARN_COLOUR, 1, cv2.LINE_AA,
        )
    cv2.putText(
        frame,
        "record: a-z   save+copy: ENTER   undo: BKSP   readout: TAB   mirror: -   quit: ESC",
        (14, frame.shape[0] - 40),
        cv2.FONT_HERSHEY_SIMPLEX, 0.52, TEXT_COLOUR, 1, cv2.LINE_AA,
    )
    cv2.putText(
        frame,
        message[:110],
        (14, frame.shape[0] - 16),
        cv2.FONT_HERSHEY_SIMPLEX, 0.52, OK_COLOUR if ok else WARN_COLOUR, 1, cv2.LINE_AA,
    )


def run_selftest(training: dict, out: Path) -> int:
    """Exercise the whole normalise -> write -> read-back path with no camera."""
    world = np.zeros((21, 3), dtype=np.float64)
    world[9] = (0.0, -0.085, 0.0)
    world[5] = (-0.028, -0.078, 0.0)
    world[17] = (0.030, -0.075, 0.0)
    for index in range(1, 21):
        world[index] = world[index] + np.array([0.0, 0.0, 0.01 * index])

    vector = training["normalize"](world, "RIGHT")
    assert vector is not None, "synthetic hand normalised to None"
    assert vector.shape == (training["VECTOR_DIM"],), f"got shape {vector.shape}"

    probe = Path(out).with_suffix(".selftest.csv")
    training["write_reference_csv"](probe, {"A": [vector, vector * 0.9]})
    text = probe.read_text(encoding="utf-8")
    probe.unlink()

    lines = text.strip().splitlines()
    expected_columns = training["VECTOR_DIM"] + 1
    assert lines[0] == f"# spec_version={training['SPEC_VERSION']}", lines[0]
    assert lines[1].startswith("letter,f0,f1,"), lines[1]
    assert len(lines[1].split(",")) == expected_columns, "header width"
    assert len(lines) == 4, f"expected 2 exemplar rows, got {len(lines) - 2}"
    assert all(len(line.split(",")) == expected_columns for line in lines[2:]), "row width"

    print("selftest OK")
    print(f"  spec_version   {training['SPEC_VERSION']}")
    print(f"  vector dim     {training['VECTOR_DIM']}")
    print(f"  static letters {len(training['STATIC_LETTERS'])}")
    print(f"  dedupe         {training['DEDUPE_DISTANCE']}")
    print(f"  per-letter cap {training['MAX_EXEMPLARS_PER_LETTER']}")
    return 0


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    training = import_training(args.repo)
    out = args.out or (
        args.repo / "android" / "app" / "src" / "main" / "assets" / "classifier" / "references-v1.csv"
    )

    if args.selftest:
        return run_selftest(training, out)

    import cv2
    import mediapipe as mp
    from mediapipe.tasks import python as mp_python
    from mediapipe.tasks.python import vision

    model_path = find_model(args.repo, args.model)
    if args.fresh and out.is_file():
        out.unlink()
    recorder = Recorder(
        out=out,
        training=training,
        dedupe=training["DEDUPE_DISTANCE"],
        cap=training["MAX_EXEMPLARS_PER_LETTER"],
        clipboard=not args.no_clipboard,
    )

    options = vision.HandLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=str(model_path)),
        running_mode=vision.RunningMode.VIDEO,
        num_hands=1,
        min_hand_detection_confidence=args.min_detection,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    cap = open_camera(args.camera)
    try:
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, args.width)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, args.height)

        print(f"model      {model_path}")
        print(f"output     {out}")
        print(f"loaded     {recorder.total} existing exemplars")
        print(
            "mirror     "
            + (
                "on (what the Android app does)"
                if not args.no_mirror
                else "OFF -- only if you know the handedness compensation matches"
            )
        )
        print("clipboard  " + ("off" if args.no_clipboard else "saved file is copied on every save"))
        print("controls   a-z record | ENTER save | BKSP undo | TAB readout | - mirror | ESC quit")

        message = "hold a pose and press its letter"
        ok = True
        show_drops = True
        mirrored = not args.no_mirror
        fps = 0.0
        frames = 0
        fps_timer = time.monotonic()
        start = time.monotonic()

        with vision.HandLandmarker.create_from_options(options) as landmarker:
            while True:
                good, frame = cap.read()
                if not good:
                    time.sleep(0.1)
                    continue

                # Mirror before inference, exactly like the Android FrameConverter. The
                # normalisation spec (docs/CLASSIFIER.md section 1) is defined in that
                # selfie-mirrored, display-upright frame, so the default is the path the app
                # itself runs. With mirroring off, MediaPipe reports the opposite handedness
                # for the same physical hand and the normaliser's mirror step negates x back,
                # which is meant to cancel out -- but that leans on the handedness head being
                # right, so it is opt-in rather than the default.
                if mirrored:
                    frame = cv2.flip(frame, 1)
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                timestamp_ms = int((time.monotonic() - start) * 1000)
                result = landmarker.detect_for_video(image, timestamp_ms)

                hand_landmarks = result.hand_landmarks or []
                world_landmarks = result.hand_world_landmarks or []
                handedness = result.handedness or []

                current_vector = None
                current_handedness = "?"
                if hand_landmarks and world_landmarks:
                    draw_skeleton(frame, hand_landmarks[0])
                    if handedness and handedness[0]:
                        current_handedness = handedness[0][0].category_name or "?"
                    world = np.asarray(
                        [[lm.x, lm.y, lm.z] for lm in world_landmarks[0]], dtype=np.float64
                    )
                    current_vector = training["normalize"](world, current_handedness)

                frames += 1
                now = time.monotonic()
                if now - fps_timer >= 0.5:
                    fps = frames / (now - fps_timer)
                    frames = 0
                    fps_timer = now

                draw_panel(
                    frame, recorder, training, fps, len(hand_landmarks),
                    current_handedness, message, ok, show_drops, mirrored,
                )
                cv2.imshow(WINDOW_TITLE, frame)

                key = cv2.waitKey(1) & 0xFF
                if key == 27:
                    break
                if key == ord("-"):
                    mirrored = not mirrored
                    message, ok = f"mirror {'on' if mirrored else 'OFF'}", mirrored
                    print(f"mirror {'on' if mirrored else 'off'}")
                    continue
                if key == 13:
                    message, ok = recorder.save(), True
                    continue
                if key == 8:
                    message, ok = recorder.undo(), True
                    continue
                if key == 9:
                    show_drops = not show_drops
                    continue

                letter = key_to_letter(key)
                if letter is None:
                    continue
                if letter not in training["STATIC_LETTERS"]:
                    message, ok = f"{letter} is a motion letter (J/Z) with no static exemplar", False
                    continue
                if current_vector is None:
                    message, ok = "no usable hand in frame -- show your hand first", False
                    continue
                ok, message = recorder.add(letter, current_vector)
    finally:
        cap.release()
        cv2.destroyAllWindows()

    if recorder.dirty:
        print(recorder.save())
    print(f"exemplars: {recorder.total}")
    for letter in training["STATIC_LETTERS"]:
        count = recorder.count(letter)
        print(f"  {letter}: {count}{'   <- no exemplars' if count == 0 else ''}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
