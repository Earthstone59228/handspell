#!/usr/bin/env python3
"""HandSpell Windows camera preview.

Opens the default Windows webcam, runs MediaPipe Hand Landmarker on every
frame, draws the 21-landmark hand skeleton for up to two hands, and -- when a
reference set exists -- identifies the letter being held using the same
normaliser, k-NN and thresholds the Android app runs (reference_classifier.py).

This is a desktop preview only. Nothing under `android/` imports it and it does
not replace the app's detector stack; it exists so the recognition pipeline can
be measured on a Windows laptop before an Android build is involved.

Controls:
    q / ESC   quit
    m         toggle selfie mirroring
    h         toggle the head-up info overlay
    i         toggle the identification readout
    e         print a leave-one-out accuracy report for the reference set
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from pathlib import Path

# mediapipe imports matplotlib in its package init chain. Point the matplotlib
# font cache inside this folder so previewing never writes (or fails to write)
# under %LOCALAPPDATA%.
os.environ.setdefault(
    "MPLCONFIGDIR", str(Path(__file__).resolve().parent / ".mpl-cache")
)

import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

from reference_classifier import (
    ADJUST,
    MATCH,
    MATCH_DISTANCE,
    NO_HAND,
    NOT_RECOGNISED,
    HandIdentifier,
    evaluate_leave_one_out,
    load_reference_set,
    load_repo_modules,
    world_array,
)

WINDOW_TITLE = "HandSpell preview (q to quit)"

# MediaPipe's canonical hand landmark connections (21 points).
HAND_CONNECTIONS = (
    (0, 1), (1, 2), (2, 3), (3, 4),            # thumb
    (0, 5), (5, 6), (6, 7), (7, 8),            # index finger
    (5, 9), (9, 10), (10, 11), (11, 12),       # middle finger
    (9, 13), (13, 14), (14, 15), (15, 16),     # ring finger
    (13, 17), (17, 18), (18, 19), (19, 20),    # pinky
    (0, 17),                                   # palm base
)

# BGR colours. Handedness labels come from MediaPipe and assume a mirrored
# (selfie) image, so with mirroring on "Left" is the viewer's left hand.
HAND_COLOURS = {
    "Left": (0, 140, 255),   # orange
    "Right": (0, 255, 80),   # green
}
FALLBACK_COLOUR = (200, 200, 200)

# This script lives at <repo>/preview-windows/, so the checkout is one level up.
# The model is read from the app's own assets rather than a second copy: it is
# the same file, and an 8 MB duplicate in git buys nothing.
REPO_DIR = Path(__file__).resolve().parents[1]

CANDIDATE_MODEL_PATHS = (
    REPO_DIR
    / "android"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "models"
    / "hand_landmarker.task",
    Path(__file__).resolve().parent / "models" / "hand_landmarker.task",
)

DEFAULT_REFERENCE = (
    REPO_DIR
    / "android"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "classifier"
    / "references-v1.csv"
)

MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/"
    "hand_landmarker/float16/latest/hand_landmarker.task"
)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Show MediaPipe hand skeletons from a Windows webcam."
    )
    parser.add_argument(
        "--camera",
        type=int,
        default=0,
        help="OpenCV camera index (default: 0). Try 1, 2, ... for other cams.",
    )
    parser.add_argument(
        "--model",
        type=Path,
        default=None,
        help="Path to hand_landmarker.task (default: models/ next to this script).",
    )
    parser.add_argument(
        "--width",
        type=int,
        default=1280,
        help="Requested capture width (default: 1280).",
    )
    parser.add_argument(
        "--height",
        type=int,
        default=720,
        help="Requested capture height (default: 720).",
    )
    parser.add_argument(
        "--max-hands",
        type=int,
        default=2,
        help="Maximum number of hands to detect (default: 2).",
    )
    parser.add_argument(
        "--min-detection",
        type=float,
        default=0.5,
        help="Minimum hand detection confidence (default: 0.5).",
    )
    parser.add_argument(
        "--no-flip",
        action="store_true",
        help="Do not mirror the preview (handedness labels then invert).",
    )
    parser.add_argument(
        "--repo",
        type=Path,
        default=REPO_DIR,
        help=f"Handspell checkout, for the normaliser and the default reference set (default: {REPO_DIR}).",
    )
    parser.add_argument(
        "--references",
        type=Path,
        default=None,
        help="references-v1.csv to identify against (default: the repo's assets copy).",
    )
    parser.add_argument(
        "--no-identify",
        action="store_true",
        help="Skip the reference set entirely and draw landmarks only.",
    )
    parser.add_argument(
        "--eval",
        action="store_true",
        help="Print a leave-one-out accuracy report for the reference set, then exit.",
    )
    return parser.parse_args(argv)


def find_model(explicit: Path | None) -> Path:
    if explicit is not None:
        if explicit.is_file():
            return explicit.resolve()
        raise FileNotFoundError(f"Model not found at: {explicit}")
    for candidate in CANDIDATE_MODEL_PATHS:
        if candidate.is_file():
            return candidate
    raise FileNotFoundError(
        "hand_landmarker.task not found. Copy the model from the Android "
        "assets, or download it from:\n"
        f"{MODEL_URL}\n"
        "and place it at models/hand_landmarker.task next to this script."
    )


def open_camera(index: int, attempts: int = 5, delay: float = 0.4) -> cv2.VideoCapture:
    """Open the camera, trying the most reliable Windows backends first, then
    retrying.

    The retry is not superstition. On the machine this was written on, an ASUS
    FHD webcam rejects the first open on both DirectShow and Media Foundation
    with "backend is generally available but can't be used to capture by index",
    then succeeds a moment later -- which looks exactly like a camera some other
    app is holding. Five attempts cost under two seconds in the worst case and
    turn an intermittent "could not open camera" into a start that works.
    """
    if sys.platform == "win32":
        backends = (cv2.CAP_DSHOW, cv2.CAP_ANY)
    else:
        backends = (cv2.CAP_ANY,)

    last_error = None
    for attempt in range(attempts):
        for backend in backends:
            cap = cv2.VideoCapture(index, backend)
            if cap.isOpened():
                return cap
            cap.release()
            last_error = f"camera index {index} (backend {backend})"
        if attempt + 1 < attempts:
            time.sleep(delay)

    raise RuntimeError(
        f"Could not open {last_error} after {attempts} attempts. Check the index "
        "with --camera and make sure no other app is using the webcam."
    )


def draw_hand(frame, hand_landmarks, handedness) -> None:
    height, width = frame.shape[:2]

    label = "?"
    if handedness:
        label = handedness[0].category_name or label
    colour = HAND_COLOURS.get(label, FALLBACK_COLOUR)

    points = [(round(lm.x * width), round(lm.y * height)) for lm in hand_landmarks]

    for start, end in HAND_CONNECTIONS:
        cv2.line(frame, points[start], points[end], colour, 2, cv2.LINE_AA)

    for index, point in enumerate(points):
        radius = 5 if index == 0 else 3
        cv2.circle(frame, point, radius, colour, thickness=-1, lineType=cv2.LINE_AA)

    wrist_x, wrist_y = points[0]
    text_x = min(max(wrist_x + 10, 8), width - 80)
    text_y = max(wrist_y - 10, 20)
    cv2.putText(
        frame,
        label,
        (text_x, text_y),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        colour,
        2,
        cv2.LINE_AA,
    )


def draw_hud(frame, fps: float, hand_count: int, mirrored: bool, resolution) -> None:
    lines = [
        f"fps: {fps:4.1f}",
        f"hands: {hand_count}",
        f"mirror: {'on' if mirrored else 'off'} (m)",
        f"camera: {resolution[0]}x{resolution[1]}",
        "quit: q",
    ]
    for row, text in enumerate(lines):
        cv2.putText(
            frame,
            text,
            (10, 24 + row * 22),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.55,
            (230, 230, 230),
            1,
            cv2.LINE_AA,
        )


VERDICT_COLOURS = {
    MATCH: (90, 240, 120),          # the app would accept this pose
    ADJUST: (80, 200, 255),         # close, but a gate is not satisfied yet
    NOT_RECOGNISED: (170, 170, 170),
    NO_HAND: (130, 130, 130),
}


def draw_identification(frame, reading) -> None:
    """Draw the letter the classifier picked and the numbers behind the verdict."""
    width = frame.shape[1]
    colour = VERDICT_COLOURS.get(reading.verdict, (200, 200, 200))
    left = max(width - 300, 10)

    cv2.putText(
        frame,
        reading.top or "-",
        (left, 92),
        cv2.FONT_HERSHEY_SIMPLEX,
        2.4,
        colour,
        4,
        cv2.LINE_AA,
    )

    rows = [
        f"p {reading.top_probability:4.2f}   margin {reading.margin:+.2f}",
        f"dist {reading.nearest_distance:5.3f} / {MATCH_DISTANCE:.2f}",
        f"hold {reading.hold_progress * 100:3.0f}%   {reading.verdict}",
    ]
    if reading.runner_up:
        rows.append(f"next {reading.runner_up} {reading.runner_up_probability:4.2f}")

    for index, text in enumerate(rows):
        cv2.putText(
            frame,
            text,
            (left, 124 + index * 22),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            colour,
            1,
            cv2.LINE_AA,
        )


def print_leave_one_out(reference_set) -> None:
    """Report how well the reference set recognises hands that are not in it."""
    report = evaluate_leave_one_out(reference_set)
    print()
    print(f"leave-one-out over {reference_set.summary()}")
    print(f"  accuracy            {report['correct']}/{report['tested']} = {report['accuracy']:.1%}")
    print(f"  median nearest dist {report['median_distance']:.4f}   (match gate {MATCH_DISTANCE})")
    for letter, bucket in sorted(report["per_letter"].items()):
        print(f"  {letter}: {bucket['correct']}/{bucket['tested']}")
    if report["untestable"]:
        print(f"  only one exemplar, not testable: {report['untestable']}")
    if report["confusions"]:
        print(f"  confused with: {report['confusions']}")
    sys.stdout.flush()


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)

    reference_path = args.references or (
        args.repo
        / "android"
        / "app"
        / "src"
        / "main"
        / "assets"
        / "classifier"
        / "references-v1.csv"
    )
    reference_set = None
    identifier = None
    if not args.no_identify:
        try:
            modules = load_repo_modules(args.repo)
            reference_set = load_reference_set(reference_path, modules["STATIC_LETTERS"])
            identifier = HandIdentifier(reference_set, modules["normalize"])
        except (FileNotFoundError, ValueError) as error:
            print(f"identification off: {error}", file=sys.stderr)

    if args.eval:
        if reference_set is None:
            print("--eval needs a reference set; see --references.", file=sys.stderr)
            return 2
        print_leave_one_out(reference_set)
        return 0

    model_path = find_model(args.model)

    options = vision.HandLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=str(model_path)),
        running_mode=vision.RunningMode.VIDEO,
        num_hands=args.max_hands,
        min_hand_detection_confidence=args.min_detection,
        min_hand_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    cap = open_camera(args.camera)
    try:
        cap.set(cv2.CAP_PROP_FRAME_WIDTH, args.width)
        cap.set(cv2.CAP_PROP_FRAME_HEIGHT, args.height)
        actual_width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        actual_height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))

        mirrored = not args.no_flip
        show_hud = True
        show_identification = True
        start_time = time.monotonic()
        frame_count = 0
        fps_timer = time.monotonic()
        fps = 0.0

        print(f"Model: {model_path}")
        print(f"Camera {args.camera}: {actual_width}x{actual_height}")
        if reference_set is not None:
            print(f"References: {reference_set.summary()}")
            print(f"Identifying: {', '.join(reference_set.letters)}")
        else:
            print("Identifying: off")
        print("Controls: q quit | m mirror | h hud | i identify | e accuracy")
        print(f"Window: {WINDOW_TITLE}")

        with vision.HandLandmarker.create_from_options(options) as landmarker:
            while True:
                ok, frame = cap.read()
                if not ok:
                    print("Lost camera frames, retrying...", file=sys.stderr)
                    time.sleep(0.1)
                    continue

                if mirrored:
                    frame = cv2.flip(frame, 1)

                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                timestamp_ms = int((time.monotonic() - start_time) * 1000)
                result = landmarker.detect_for_video(mp_image, timestamp_ms)

                hand_landmarks = result.hand_landmarks or []
                world_landmarks = result.hand_world_landmarks or []
                handedness = result.handedness or []
                for landmarks, hand_info in zip(hand_landmarks, handedness):
                    draw_hand(frame, landmarks, hand_info)

                reading = None
                if identifier is not None:
                    if hand_landmarks and world_landmarks:
                        label = "RIGHT"
                        if handedness and handedness[0]:
                            label = handedness[0][0].category_name or label
                        reading = identifier.update(
                            world_array(world_landmarks[0]), label, timestamp_ms
                        )
                    else:
                        reading = identifier.no_hand(timestamp_ms)
                    if show_identification:
                        draw_identification(frame, reading)

                frame_count += 1
                now = time.monotonic()
                if now - fps_timer >= 0.5:
                    fps = frame_count / (now - fps_timer)
                    frame_count = 0
                    fps_timer = now

                if show_hud:
                    draw_hud(
                        frame, fps, len(hand_landmarks), mirrored,
                        (actual_width, actual_height),
                    )

                cv2.imshow(WINDOW_TITLE, frame)
                key = cv2.waitKey(1) & 0xFF
                if key in (ord("q"), 27):
                    break
                if key == ord("m"):
                    mirrored = not mirrored
                if key == ord("h"):
                    show_hud = not show_hud
                if key == ord("i"):
                    show_identification = not show_identification
                if key == ord("e") and reference_set is not None:
                    print_leave_one_out(reference_set)
    finally:
        cap.release()
        cv2.destroyAllWindows()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
