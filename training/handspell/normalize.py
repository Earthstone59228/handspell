"""Normalisation spec v1: raw MediaPipe world landmarks -> a 66-float vector
that is invariant to hand position, scale, and (for the shape block) rotation,
while keeping absolute palm orientation separately so that pairs like K/P and
G/Q -- identical handshape, different wrist rotation -- stay distinguishable.

Follows docs/CLASSIFIER.md sections 1-2 literally. Landmark index convention
(docs/CONTRACTS.md section 1): 0 = wrist, 5 = index MCP, 9 = middle MCP,
17 = pinky MCP; all 21 points are MediaPipe world landmarks, in metres, in the
mirrored-selfie/display-upright frame described there.

All arithmetic is done in float64; only the returned array is narrowed to
float32, per spec.
"""

from __future__ import annotations

import numpy as np

SPEC_VERSION = 1
SHAPE_DIM = 60
ORIENTATION_DIM = 6
VECTOR_DIM = SHAPE_DIM + ORIENTATION_DIM

_DEGENERATE_EPS = 1e-6

# Landmark indices used directly by the spec (docs/CONTRACTS.md section 1).
_WRIST = 0
_INDEX_MCP = 5
_MIDDLE_MCP = 9
_PINKY_MCP = 17

# Column name order of the CLASSIFIER.md section 7 capture CSV: 15 metadata
# columns, then 63 world columns (wx0,wy0,wz0,...,wx20,wy20,wz20), then 63
# image columns (unused by the normalizer).
_WORLD_COLUMN_PREFIXES = ("wx", "wy", "wz")


def normalize(world: np.ndarray, handedness: str) -> np.ndarray | None:
    """Map 21 MediaPipe world landmarks (metres) plus a handedness label to
    the 66-float normalized vector, or None if the geometry is degenerate.

    `world` must have shape (21, 3): rows are landmarks in MediaPipe index
    order, columns are (x, y, z) in the selfie-mirrored, display-upright
    frame (docs/CONTRACTS.md section 1). `handedness` is "LEFT" or "RIGHT"
    (case-insensitive), the label MediaPipe reports for the physical hand.
    """
    points = np.asarray(world, dtype=np.float64)
    if points.shape != (21, 3):
        raise ValueError(f"world must have shape (21, 3), got {points.shape}")

    # 1. Mirror. Canonical space is "a right hand as seen in a mirror", so a
    # LEFT-hand pose is reflected into that space and a RIGHT-hand pose is
    # left untouched.
    p = points.copy()
    if handedness.strip().upper() == "LEFT":
        p[:, 0] = -p[:, 0]

    # 2. Translate to the wrist, the only landmark finger articulation never
    # moves.
    p = p - p[_WRIST]

    # 3. Rotation-align: build an orthonormal palm frame from two palm
    # vectors, then rotate every point into it.
    u = p[_MIDDLE_MCP]
    norm_u = float(np.linalg.norm(u))
    if norm_u < _DEGENERATE_EPS:
        return None
    e1 = u / norm_u

    v = p[_INDEX_MCP] - p[_PINKY_MCP]
    w = v - np.dot(v, e1) * e1
    norm_w = float(np.linalg.norm(w))
    if norm_w < _DEGENERATE_EPS:
        return None
    e2 = w / norm_w

    e3 = np.cross(e1, e2)

    rotation = np.stack([e1, e2, e3])  # rows = [e1; e2; e3]
    q = p @ rotation.T  # q_i = R . p_i, done row-wise for every landmark

    # 4. Scale by the wrist-to-middle-MCP span (computed before rotation,
    # which does not change vector length, so norm_u is exactly |u|).
    q = q / norm_u

    # 5. Shape block: landmarks 1..20, xyz, in index order -> 60 floats.
    shape = q[1:21].reshape(-1)

    # 6. Orientation block: e1 then e3 -> 6 floats.
    orientation = np.concatenate([e1, e3])

    vector = np.concatenate([shape, orientation])
    return vector.astype(np.float32)


def normalize_capture_row(row: dict) -> np.ndarray | None:
    """Map one CLASSIFIER.md section 7 capture-CSV row (as a dict of column
    name -> value) to a `normalize` call. Column mapping only -- the caller
    is responsible for reading the CSV itself.
    """
    world = np.empty((21, 3), dtype=np.float64)
    for i in range(21):
        for axis_index, prefix in enumerate(_WORLD_COLUMN_PREFIXES):
            world[i, axis_index] = float(row[f"{prefix}{i}"])
    return normalize(world, str(row["handedness"]))
