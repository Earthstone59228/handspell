"""Word-sign sequence features (spec words-v2). The Kotlin port must reproduce this exactly.

Input is one sign attempt as F frames:

    hands[F, 2, 21, 2]  image x, y (0..1) of up to two hands, NaN where a hand is absent. Slot order is arbitrary
                        (MediaPipe returns hands in any order; Holistic uses left/right labels), so nothing here
                        depends on it and nothing depends on handedness labels.
    pose[F, 3, 2]       image x, y of the pose landmarks nose (0), left shoulder (11), right shoulder (12); NaN if
                        the body was not found in that frame.

Steps:
  0. Crop the frames to the active span: from the first frame that has a hand to the last one. Clips and live windows
     carry rest frames before and after the sign; cropping makes both look alike. Fewer than MIN_FRAMES left = None.
  1. Slot A = the hand present in the most frames (ties: the larger total wrist travel). Slot B = the other one, whose
     features are then left at zero: the training data (ISLR) is signed with one hand, so the model only ever sees slot A.
  2. Body frame for the whole sample: origin = median nose, scale = median shoulder distance (min 0.05).
  3. Per frame and slot, if present, 55 floats: the 21 points relative to that hand's wrist divided by hand size
     (wrist to middle-finger MCP distance, min 1e-3) = 42; the wrist and the index fingertip relative to the origin
     divided by the scale = 4; the thumb, middle, ring and pinky fingertips relative to the origin over the scale = 8;
     presence 1.0. Absent = 55 zeros.
  4. Resample to STEPS = 16 frames by nearest index over linspace(0, F-1, STEPS).
  5. Append slot A's wrist velocity: the wrist track (in body units) at the 16 steps, gaps filled from the nearest
     present step, then the 15 first differences (dx, dy) = 30 floats.
  Total = 16 * 2 * 55 + 30 = 1790.

Chirality is not normalised: training adds mirrored copies (negate every x feature, see mirror_features), so left
and right handed signing both work and no label convention has to agree between datasets and the phone.
"""

from __future__ import annotations

import numpy as np

STEPS = 16
SLOT_DIM = 55
FEATURE_DIM = STEPS * 2 * SLOT_DIM + (STEPS - 1) * 2
MIN_FRAMES = 5
MIN_SCALE = 0.05
MIN_HAND = 1e-3
NOSE, L_SHOULDER, R_SHOULDER = 0, 1, 2


def _nan_median(a: np.ndarray) -> float | None:
    a = a[~np.isnan(a)]
    return float(np.median(a)) if a.size else None


def sequence_features(hands: np.ndarray, pose: np.ndarray) -> np.ndarray | None:
    if hands.shape[0] == 0:
        return None
    seen_any = np.flatnonzero(~np.isnan(hands[:, :, 0, 0]).all(axis=1))
    if seen_any.size == 0:
        return None
    hands, pose = hands[seen_any[0]:seen_any[-1] + 1], pose[seen_any[0]:seen_any[-1] + 1]
    frames = hands.shape[0]
    if frames < MIN_FRAMES:
        return None
    present = ~np.isnan(hands[:, :, 0, 0])  # [F, 2]

    travel = np.zeros(2)
    for h in range(2):
        idx = np.flatnonzero(present[:, h])
        if idx.size > 1:
            travel[h] = np.linalg.norm(np.diff(hands[idx, h, 0, :], axis=0), axis=1).sum()
    a = max((0, 1), key=lambda h: (int(present[:, h].sum()), travel[h]))
    slots = (a, 1 - a)

    nose_x, nose_y = _nan_median(pose[:, NOSE, 0]), _nan_median(pose[:, NOSE, 1])
    if nose_x is None or nose_y is None:
        return None
    width = np.linalg.norm(pose[:, L_SHOULDER, :] - pose[:, R_SHOULDER, :], axis=1)
    scale = max(_nan_median(width) or 0.0, MIN_SCALE)
    origin = np.array([nose_x, nose_y])

    per_frame = np.zeros((frames, 2, SLOT_DIM), np.float32)
    wrist_a = np.full((frames, 2), np.nan)
    for f in range(frames):
        for s, h in enumerate(slots):
            if not present[f, h]:
                continue
            pts = hands[f, h]
            wrist = pts[0]
            size = max(float(np.linalg.norm(pts[9] - wrist)), MIN_HAND)
            per_frame[f, s, :42] = ((pts - wrist) / size).reshape(-1)
            per_frame[f, s, 42:44] = (wrist - origin) / scale
            per_frame[f, s, 44:46] = (pts[8] - origin) / scale
            for k, tip in enumerate((4, 12, 16, 20)):
                per_frame[f, s, 46 + 2 * k:48 + 2 * k] = (pts[tip] - origin) / scale
            per_frame[f, s, 54] = 1.0
            if s == 0:
                wrist_a[f] = (wrist - origin) / scale

    per_frame[:, 1, :] = 0  # dominant hand only: the training data (one-handed signing) never has a second hand
    idx = np.rint(np.linspace(0, frames - 1, STEPS)).astype(int)
    steps = per_frame[idx]
    track = wrist_a[idx]
    seen = ~np.isnan(track[:, 0])
    if not seen.any():
        return None
    seen_idx = np.flatnonzero(seen)
    for i in range(STEPS):
        if not seen[i]:
            track[i] = track[seen_idx[np.argmin(np.abs(seen_idx - i))]]
    motion = np.diff(track, axis=0).reshape(-1)
    return np.concatenate([steps.reshape(-1), motion]).astype(np.float32)


def _x_columns() -> np.ndarray:
    """Indices of every feature that is an x coordinate or x velocity."""
    cols = []
    for step in range(STEPS):
        for slot in range(2):
            base = (step * 2 + slot) * SLOT_DIM
            cols.extend(base + 2 * p for p in range(21))  # 21 points, (x, y) interleaved
            cols.append(base + 42)  # wrist x
            cols.append(base + 44)  # index tip x
            cols.extend(base + 46 + 2 * k for k in range(4))  # thumb, middle, ring, pinky tip x
    base = STEPS * 2 * SLOT_DIM
    cols.extend(base + 2 * i for i in range(STEPS - 1))  # velocity dx
    return np.asarray(cols)


X_COLUMNS = _x_columns()


def mirror_features(x: np.ndarray) -> np.ndarray:
    """The features of the left-right mirrored sign (works on [N, FEATURE_DIM] or [FEATURE_DIM])."""
    out = np.array(x, copy=True)
    out[..., X_COLUMNS] *= -1.0
    return out
