"""Extract ASLYset's original right-hand images into Handspell spec-v1 vectors.

Download ASLYset.rar from https://data.mendeley.com/datasets/xs6mvhx6rh/1,
then run this with --archive, --work-dir, --out and --base-references.
The raw archive and images stay outside the repository. Derived vectors retain
the original user IDs for signer-held-out evaluation; no RGB images ship in the app.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import subprocess
import sys
from collections import Counter
from pathlib import Path

# Same headless workaround as smoke_landmarker.py; must precede MediaPipe import.
for key in list(os.environ):
    if key.startswith(("XDG_", "WAYLAND_", "DBUS_", "HYPRLAND_", "HYPRCURSOR_", "KITTY_", "UWSM_")) or key in (
        "DISPLAY", "TERM", "SYSTEMD_EXEC_PID", "JOURNAL_STREAM", "INVOCATION_ID", "NOTIFY_SOCKET",
    ):
        os.environ.pop(key, None)

import mediapipe as mp
import numpy as np
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.core.base_options import BaseOptions
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from handspell.normalize import normalize
from handspell.references import STATIC_LETTERS, write_reference_csv

ARCHIVE_SHA256 = "e50202c30dfff4795b88762b765bbeb357e184c13fe22b8f6e0d836d02235c80"
SOURCE = "https://data.mendeley.com/datasets/xs6mvhx6rh/1"


def balanced_exemplars(vectors, letters, signers, limit=64):
    """Interleave signers before deduplication so the first user cannot fill the cap."""
    selected = {}
    for letter in STATIC_LETTERS:
        groups = [list(np.flatnonzero((letters == letter) & (signers == signer))) for signer in sorted(set(signers))]
        kept = []
        for row in range(max(map(len, groups), default=0)):
            for group in groups:
                if row >= len(group):
                    continue
                vector = vectors[group[row]]
                if len(kept) < limit and all(np.linalg.norm(vector - previous) >= .05 for previous in kept):
                    kept.append(vector)
        if kept:
            # Use a real central exemplar for the guide, never an averaged/invented pose.
            matrix = np.asarray(kept)
            distances = np.linalg.norm(matrix[:, None, :60] - matrix[None, :, :60], axis=2)
            medoid = int(np.argmin(distances.sum(axis=1)))
            kept.insert(0, kept.pop(medoid))
            selected[letter] = kept
    return selected


def read_references(path):
    result = {}
    with path.open() as handle:
        for row in csv.DictReader(line for line in handle if not line.startswith("#")):
            result.setdefault(row["letter"], []).append(np.array([float(row[f"f{i}"]) for i in range(66)], dtype=np.float32))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--work-dir", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True, help="derived vectors/provenance directory")
    parser.add_argument("--base-references", type=Path, required=True)
    args = parser.parse_args()
    with args.archive.open("rb") as handle:
        digest = hashlib.file_digest(handle, "sha256").hexdigest()
    if digest != ARCHIVE_SHA256:
        raise SystemExit("Original ASLYset archive SHA256 mismatch; refusing to import")
    names = subprocess.check_output(["bsdtar", "-tf", str(args.archive)], text=True).splitlines()
    images = [name for name in names if name.lower().endswith(".png") and name.startswith("ASLYset/images/")]
    if any(".." in Path(name).parts for name in images):
        raise SystemExit("Unsafe archive path")
    args.work_dir.mkdir(parents=True, exist_ok=True)
    subprocess.run(["bsdtar", "-xf", str(args.archive), "-C", str(args.work_dir), *images], check=True)
    args.out.mkdir(parents=True, exist_ok=True)
    model = Path(__file__).resolve().parents[1] / "models/hand_landmarker.task"
    options = vision.HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(model)),
        running_mode=vision.RunningMode.IMAGE, num_hands=2,
        min_hand_detection_confidence=.5, min_hand_presence_confidence=.5,
    )
    vectors, labels, signers, filenames, handedness_labels = [], [], [], [], []
    skipped = Counter()
    detected = Counter()
    with vision.HandLandmarker.create_from_options(options) as landmarker:
        for index, name in enumerate(sorted(images)):
            path = args.work_dir / name
            letter = path.parent.name.upper()
            if letter not in STATIC_LETTERS:
                skipped["non_letter"] += 1
                continue
            # Original release says all images depict the physical right hand.
            # Mirror the RGB image into the same upright selfie frame as the app.
            rgb = np.asarray(Image.open(path).convert("RGB"))[:, ::-1].copy()
            result = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
            if len(result.hand_world_landmarks) != 1:
                skipped[f"{letter}:no_or_multiple_hands"] += 1
                continue
            world = np.array([[point.x, point.y, point.z] for point in result.hand_world_landmarks[0]])
            # Use the reported label exactly as CameraSignDetector does. Source-camera mirroring
            # can differ from its documented physical hand; forcing RIGHT breaks interoperability.
            handedness = result.handedness[0][0].category_name.upper()
            vector = normalize(world, handedness)
            if vector is None or not np.isfinite(vector).all():
                skipped[f"{letter}:degenerate"] += 1
                continue
            vectors.append(vector); labels.append(letter); signers.append(path.parent.parent.name); filenames.append(name)
            handedness_labels.append(handedness)
            detected[letter] += 1
            if index % 250 == 0:
                print(f"Processed {index + 1}/{len(images)} images; {len(vectors)} usable", flush=True)
    vectors, labels, signers = np.asarray(vectors), np.asarray(labels), np.asarray(signers)
    np.savez_compressed(args.out / "landmarks-v1.npz", vectors=vectors, letters=labels, signers=signers, files=np.asarray(filenames), handedness=np.asarray(handedness_labels))
    exemplars = balanced_exemplars(vectors, labels, signers)
    base = read_references(args.base_references)
    # Preserve the existing team's A–D guide and examples; supplement them with public data.
    for letter, originals in base.items():
        exemplars[letter] = originals + exemplars.get(letter, [])[:max(0, 64 - len(originals))]
    missing = [letter for letter in STATIC_LETTERS if len(exemplars.get(letter, [])) < 5]
    if missing:
        raise SystemExit(f"Insufficient public examples for {missing}; derived data saved, reference export refused")
    write_reference_csv(args.out / "references-v1.csv", exemplars)
    metadata = {
        "dataset": "ASLYset", "creator": "Miguel Rivera", "year": 2019,
        "doi": "10.17632/xs6mvhx6rh.1", "source": SOURCE,
        "license": "CC BY 4.0", "license_url": "https://creativecommons.org/licenses/by/4.0/",
        "archive_sha256": digest,
        "model_sha256": hashlib.sha256(model.read_bytes()).hexdigest(),
        "mediapipe_version": mp.__version__, "normalization_spec_version": 1,
        "frame_convention": "selfie-upright-v1", "physical_hand": "RIGHT (original release)",
        "normalization_handedness": "MediaPipe reported label, identical to Android",
        "reported_handedness_counts": dict(Counter(handedness_labels)),
        "modifications": "Horizontal image mirroring; MediaPipe world-landmark extraction; spec-v1 normalization; signer-interleaved deduplication capped at 64 per letter; existing A–D retained.",
        "detected_per_letter": dict(sorted(detected.items())), "skipped": dict(sorted(skipped.items())),
        "reference_counts": {letter: len(rows) for letter, rows in exemplars.items()},
        "reference_sha256": hashlib.sha256((args.out / "references-v1.csv").read_bytes()).hexdigest(),
        "signers": sorted(set(signers.tolist())),
        "validation": "Public image extraction only; signer-held-out evaluation and live-device checks required.",
    }
    (args.out / "provenance.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata, indent=2), flush=True)


if __name__ == "__main__":
    main()
