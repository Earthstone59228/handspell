"""Generates training/testdata/words_features_golden.json: fixed-seed sign attempts and the feature vectors that
handspell.words_features produces for them, so the Kotlin port (WordFeatures.kt) is pinned to the Python reference.

Same approach as handspell.golden and handspell.mlp_golden. Inputs are rounded to 9 significant digits before the
expected outputs are computed, so both sides start from bit-identical numbers. Run `python -m handspell.words_golden`.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from handspell.words_features import FEATURE_DIM, mirror_features, sequence_features

_PATH = Path(__file__).resolve().parent.parent / "testdata" / "words_features_golden.json"


def _r9(a: np.ndarray) -> np.ndarray:
    return np.vectorize(lambda v: float(f"{float(v):.9g}") if v == v else v)(a).astype(np.float32)


def _hand(rng: np.random.Generator, centre: np.ndarray, size: float) -> np.ndarray:
    base = np.array([[0, 0], [-.3, -.3], [-.5, -.7], [-.6, -1.0], [-.7, -1.3], [-.1, -.9], [-.1, -1.4], [-.1, -1.7],
                     [-.1, -2.0], [.15, -.9], [.15, -1.5], [.15, -1.85], [.15, -2.2], [.4, -.85], [.42, -1.4],
                     [.43, -1.7], [.44, -2.0], [.65, -.75], [.7, -1.2], [.72, -1.45], [.74, -1.7]]) * size * 0.45
    return (centre + base + rng.normal(0, size * 0.02, base.shape)).astype(np.float32)


def _case(rng: np.random.Generator, frames: int, two_hands: bool, drop: float, pose_gaps: bool):
    hands = np.full((frames, 2, 21, 2), np.nan, np.float32)
    pose = np.full((frames, 3, 2), np.nan, np.float32)
    slot = int(rng.integers(0, 2))
    for f in range(frames):
        t = f / max(frames - 1, 1)
        if rng.random() > drop:
            hands[f, slot] = _hand(rng, np.array([0.35 + 0.3 * t, 0.55 - 0.25 * t]), 0.18)
        if two_hands and rng.random() > drop:
            hands[f, 1 - slot] = _hand(rng, np.array([0.7 - 0.1 * t, 0.7]), 0.16)
        if not (pose_gaps and f % 3 == 0):
            pose[f] = [[0.5, 0.32], [0.62, 0.55], [0.38, 0.56]] + rng.normal(0, 0.004, (3, 2))
    hands[0, slot] = _hand(rng, np.array([0.35, 0.55]), 0.18)  # guarantee at least one hand
    return _r9(hands), _r9(pose)


def build() -> dict:
    rng = np.random.default_rng(20260930)
    specs = [
        ("one_hand_16", 16, False, 0.1, False),
        ("two_hands_gappy_23", 23, True, 0.25, True),
        ("long_40", 40, True, 0.05, False),
        ("short_7", 7, False, 0.0, False),
        ("too_short_3", 3, False, 0.0, False),
        ("padded_rest_20", 20, True, 0.1, False),
    ]
    cases = []
    for name, frames, two, drop, gaps in specs:
        hands, pose = _case(rng, frames, two, drop, gaps)
        if name.startswith("padded"):  # rest frames (body visible, no hands) before and after the sign
            rest_pose = np.repeat(pose[:1], 6, axis=0)
            rest_hands = np.full((6, 2, 21, 2), np.nan, np.float32)
            hands = np.concatenate([rest_hands, hands, rest_hands]); pose = np.concatenate([rest_pose, pose, rest_pose])
            frames = len(hands)
        expected = sequence_features(hands, pose)
        frames_json = [
            {"hands": [None if np.isnan(hands[f, h, 0, 0]) else hands[f, h].reshape(-1).tolist() for h in range(2)],
             "pose": None if np.isnan(pose[f]).all() else [None if np.isnan(v) else float(v) for v in pose[f].reshape(-1)]}
            for f in range(frames)
        ]
        cases.append({
            "name": name,
            "frames": frames_json,
            "expected": None if expected is None else [float(f"{float(v):.9g}") for v in expected],
            "expectedMirrored": None if expected is None else [float(f"{float(v):.9g}") for v in mirror_features(expected)],
        })
    return {"featureDim": FEATURE_DIM, "cases": cases}


if __name__ == "__main__":
    _PATH.write_text(json.dumps(build()) + "\n", encoding="utf-8")
    print("wrote", _PATH)
