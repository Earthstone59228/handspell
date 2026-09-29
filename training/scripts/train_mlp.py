"""Trains the stage-2 MLP (mlp-v1) from ASLYset photo vectors plus team live A–D vectors.

    .venv/bin/python scripts/train_mlp.py --out runs/mlp-v1.npz --metadata runs/mlp-v1.json
    .venv/bin/python scripts/export_weights.py --npz runs/mlp-v1.npz --metadata runs/mlp-v1.json

Why this recipe (docs/research/mlp-v1-2026-09-29.md has the numbers):
- The photo vectors sit systematically away from live-camera vectors. About 72% of that offset
  is shared across letters, so the mean live-minus-photo offset learned on A–D (the only letters
  with live data) is added to every photo vector.
- Live A–D vectors are repeated so 98 rows are not drowned out by 4786 photo rows.
- Gaussian landmark/orientation noise plus a small scale jitter stands in for signer variety.
The app's existing gate (p >= 0.85, margin >= 0.20, no distance check on stage 2) is unchanged.
"""
from __future__ import annotations

import argparse
import csv
import json
import subprocess
from pathlib import Path

import numpy as np
from sklearn.neural_network import MLPClassifier

_TRAINING_ROOT = Path(__file__).resolve().parents[1]
PHOTO_NPZ = _TRAINING_ROOT / "data" / "aslyset" / "landmarks-v1.npz"
LIVE_CSV = _TRAINING_ROOT / "data" / "team-references-v1.csv"

HIDDEN = 64
LIVE_REPEAT = 8
AUG_COPIES = 4
SHAPE_NOISE = 0.07
ORIENTATION_NOISE = 0.15
SCALE_JITTER = 0.10
ALPHA = 1e-3
SEED = 0


def read_live(path: Path) -> tuple[np.ndarray, np.ndarray]:
    with path.open() as handle:
        rows = list(csv.DictReader(line for line in handle if not line.startswith("#")))
    vectors = np.array([[float(row[f"f{i}"]) for i in range(66)] for row in rows])
    return vectors, np.array([row["letter"] for row in rows])


def live_offset(photo, photo_letters, live, live_letters) -> np.ndarray:
    letters = sorted(set(live_letters))
    return np.mean([live[live_letters == l].mean(0) - photo[photo_letters == l].mean(0) for l in letters], axis=0)


def augment(vectors, labels, rng) -> tuple[np.ndarray, np.ndarray]:
    copies = [vectors]
    for _ in range(AUG_COPIES):
        noisy = vectors.copy()
        noisy[:, :60] += rng.normal(0, SHAPE_NOISE, noisy[:, :60].shape)
        noisy[:, 60:] += rng.normal(0, ORIENTATION_NOISE, noisy[:, 60:].shape)
        noisy[:, :60] *= rng.uniform(1 - SCALE_JITTER, 1 + SCALE_JITTER, (len(noisy), 1))
        copies.append(noisy)
    return np.vstack(copies), np.tile(labels, AUG_COPIES + 1)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True, help="output .npz for export_weights.py")
    parser.add_argument("--metadata", type=Path, help="optional training-metadata JSON")
    args = parser.parse_args()

    photo_archive = np.load(PHOTO_NPZ)
    photo, photo_letters = photo_archive["vectors"].astype(np.float64), photo_archive["letters"]
    live, live_letters = read_live(LIVE_CSV)
    offset = live_offset(photo, photo_letters, live, live_letters)

    vectors = np.vstack([photo + offset, np.repeat(live, LIVE_REPEAT, axis=0)])
    labels = np.concatenate([photo_letters, np.repeat(live_letters, LIVE_REPEAT)])
    vectors, labels = augment(vectors, labels, np.random.default_rng(SEED))

    model = MLPClassifier((HIDDEN,), alpha=ALPHA, max_iter=300, random_state=SEED).fit(vectors, labels)

    arrays = {}
    for index, (weights, bias) in enumerate(zip(model.coefs_, model.intercepts_)):
        arrays[f"W{index}"] = weights.T.astype(np.float32)  # HSML wants (out_dim, in_dim)
        arrays[f"b{index}"] = bias.astype(np.float32)
    arrays["labels"] = np.array(model.classes_)
    arrays["activations"] = np.array(["relu"] * (len(model.coefs_) - 1) + ["identity"])
    args.out.parent.mkdir(parents=True, exist_ok=True)
    np.savez(args.out, **arrays)

    if args.metadata:
        try:
            sha = subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], cwd=_TRAINING_ROOT, text=True).strip()
        except (OSError, subprocess.CalledProcessError):
            sha = "unknown"
        args.metadata.write_text(json.dumps({
            "trainer": "scripts/train_mlp.py",
            "git_sha": sha,
            "photo_rows": int(len(photo)),
            "photo_signers": sorted(set(map(str, photo_archive["signers"]))),
            "live_rows": int(len(live)),
            "live_letters": sorted(set(map(str, live_letters))),
            "hidden": [HIDDEN],
            "augmentation": {"copies": AUG_COPIES, "shape_noise": SHAPE_NOISE,
                             "orientation_noise": ORIENTATION_NOISE, "scale_jitter": SCALE_JITTER},
            "validation": "docs/research/mlp-v1-2026-09-29.md",
        }, indent=2) + "\n")
    print(f"wrote {args.out}: {len(model.classes_)} classes, train rows {len(vectors)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
