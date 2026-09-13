"""Generates training/testdata/normalizer_golden.json: the cross-language
golden-vector fixture described in docs/CLASSIFIER.md section 2. Both
tests/test_normalize.py (this package) and the Kotlin
NormalizerGoldenTest.kt read the same file and must agree with `normalize()`
to 1e-6 absolute.

Cases are built from a small parametric hand-pose model (not a biomechanical
simulator): a wrist-anchored skeleton with four fingers, each a 3-segment
chain that curls around its own abduction-adjusted axis, plus a thumb chain
with an independent swing/depth/flex, all placed by a rigid "global" rotation
and translation representing however the physical hand happens to be held
in front of the camera. It exists to produce distinct, plausible 21-point
hand geometries -- not to be an ASL-correctness reference.

Run as `python -m handspell.golden` to regenerate the fixture after any
change to `normalize()` or to the cases below.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from handspell.normalize import normalize

_GOLDEN_PATH = Path(__file__).resolve().parent.parent / "testdata" / "normalizer_golden.json"

_SEED = 20260913

# Segment lengths (metres), MCP -> PIP -> DIP -> TIP, roughly adult-hand scale.
_FINGER_LENGTHS = {
    "index": (0.040, 0.025, 0.020),
    "middle": (0.045, 0.028, 0.022),
    "ring": (0.042, 0.026, 0.020),
    "pinky": (0.032, 0.020, 0.018),
}
_THUMB_LENGTHS = (0.035, 0.030, 0.025)

# MCP anchor positions relative to the wrist, local hand frame: +x toward the
# index side, +y toward the fingers, +z out of the palm.
_MCP_POSITIONS = {
    "index": np.array([0.020, 0.085, 0.000]),
    "middle": np.array([0.000, 0.095, 0.000]),
    "ring": np.array([-0.020, 0.088, 0.000]),
    "pinky": np.array([-0.038, 0.078, 0.000]),
}
_THUMB_CMC = np.array([0.025, 0.020, 0.010])

_STRAIGHT = (0.05, 0.05, 0.05)
_CURL = (1.30, 1.60, 1.40)
_HALF_CURL = (0.55, 0.85, 0.65)


def _rotmat_axis_angle(axis: np.ndarray, theta: float) -> np.ndarray:
    axis = axis / np.linalg.norm(axis)
    x, y, z = axis
    k = np.array([[0, -z, y], [z, 0, -x], [-y, x, 0]])
    return np.eye(3) + np.sin(theta) * k + (1 - np.cos(theta)) * (k @ k)


def _rotz(theta: float) -> np.ndarray:
    return _rotmat_axis_angle(np.array([0.0, 0.0, 1.0]), theta)


def _rotx(theta: float) -> np.ndarray:
    return _rotmat_axis_angle(np.array([1.0, 0.0, 0.0]), theta)


def _roty(theta: float) -> np.ndarray:
    return _rotmat_axis_angle(np.array([0.0, 1.0, 0.0]), theta)


def _finger_chain(mcp: np.ndarray, lengths: tuple, curls: tuple, abduction: float) -> list[np.ndarray]:
    """Returns [PIP, DIP, TIP] for a finger rooted at `mcp`, curling around
    its own x-axis (flexion) after being splayed sideways by `abduction`
    (rotation about z, in the palm plane)."""
    base_dir = _rotz(abduction) @ np.array([0.0, 1.0, 0.0])
    points = []
    point = mcp.copy()
    cumulative = 0.0
    for length, curl in zip(lengths, curls):
        cumulative += curl
        direction = _rotx(cumulative) @ base_dir
        point = point + length * direction
        points.append(point.copy())
    return points


def _thumb_chain(swing: float, depth: float, flex1: float, flex2: float) -> list[np.ndarray]:
    """Returns [MCP, IP, TIP] for the thumb, rooted at the fixed CMC anchor.
    `swing` sweeps the thumb across the palm (rotation about z); `depth`
    lifts it toward or away from the palm plane (rotation about y); `flex1`
    /`flex2` are additional MCP/IP flexion applied cumulatively."""
    base_dir = _rotz(swing) @ _roty(depth) @ np.array([1.0, 0.0, 0.0])
    points = []
    point = _THUMB_CMC.copy()
    cumulative = 0.0
    flexes = (0.0, flex1, flex2)
    for length, flex in zip(_THUMB_LENGTHS, flexes):
        cumulative += flex
        direction = _rotx(cumulative) @ base_dir
        point = point + length * direction
        points.append(point.copy())
    return points


def _build_hand(
    finger_curls: dict[str, tuple],
    finger_abduction: dict[str, float],
    thumb: tuple,
    global_rotation: np.ndarray,
    translation: np.ndarray,
    jitter_rng: np.random.Generator | None,
) -> np.ndarray:
    """Builds the 21 raw (pre-normalize) landmarks for one hand pose."""
    wrist = np.zeros(3)
    thumb_pts = _thumb_chain(*thumb)
    index_pts = _finger_chain(_MCP_POSITIONS["index"], _FINGER_LENGTHS["index"], finger_curls["index"], finger_abduction["index"])
    middle_pts = _finger_chain(_MCP_POSITIONS["middle"], _FINGER_LENGTHS["middle"], finger_curls["middle"], finger_abduction["middle"])
    ring_pts = _finger_chain(_MCP_POSITIONS["ring"], _FINGER_LENGTHS["ring"], finger_curls["ring"], finger_abduction["ring"])
    pinky_pts = _finger_chain(_MCP_POSITIONS["pinky"], _FINGER_LENGTHS["pinky"], finger_curls["pinky"], finger_abduction["pinky"])

    # MediaPipe hand landmark order: wrist, thumb (CMC..TIP), then each
    # finger MCP..TIP (index, middle, ring, pinky).
    local_points = np.stack(
        [wrist, _THUMB_CMC, *thumb_pts]
        + [_MCP_POSITIONS["index"], *index_pts]
        + [_MCP_POSITIONS["middle"], *middle_pts]
        + [_MCP_POSITIONS["ring"], *ring_pts]
        + [_MCP_POSITIONS["pinky"], *pinky_pts]
    )

    if jitter_rng is not None:
        local_points = local_points + jitter_rng.normal(scale=0.0015, size=local_points.shape)

    world = (global_rotation @ local_points.T).T + translation
    return world


# Global rotations representing how the physical hand is held relative to
# the camera. "UP" is the identity-ish baseline; "DOWN" tips the wrist by
# ~150 degrees about x so the fingers point down-screen instead of up-screen
# (this is what separates K from P, and G from Q -- same handshape, opposite
# e1.y sign); "SIDE" turns the hand ~90 degrees about z (separates H from U).
_ROT_UP = _rotx(0.15)
_ROT_DOWN = _rotx(np.pi - 0.35)
_ROT_SIDE = _rotz(-np.pi / 2) @ _rotx(0.15)

_TRANSLATION = np.array([0.02, -0.05, -0.35])


def _case(name: str, handedness: str, world: np.ndarray) -> dict:
    expected = normalize(world, handedness)
    return {
        "name": name,
        "handedness": handedness,
        "world": [[float(c) for c in point] for point in world],
        "expected": None if expected is None else [float(v) for v in expected],
    }


def build_cases() -> list[dict]:
    rng = np.random.default_rng(_SEED)

    def curls(index, middle, ring, pinky):
        return {"index": index, "middle": middle, "ring": ring, "pinky": pinky}

    def abduction(index=0.0, middle=0.0, ring=0.0, pinky=0.0):
        return {"index": index, "middle": middle, "ring": ring, "pinky": pinky}

    cases = []

    # --- A / S: fist with thumb at the side vs. wrapped across the front.
    fist_curls = curls(_CURL, _CURL, _CURL, _CURL)
    fist_abduction = abduction()
    a_world = _build_hand(fist_curls, fist_abduction, thumb=(0.15, 0.05, 0.10, 0.05), global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("A_right", "RIGHT", a_world))

    s_world = _build_hand(fist_curls, fist_abduction, thumb=(0.55, 1.05, 0.20, 0.15), global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("S_right", "RIGHT", s_world))

    # --- M / N: thumb tucked under 3 fingers vs. 2.
    m_world = _build_hand(fist_curls, fist_abduction, thumb=(1.55, 0.15, 0.35, 0.20), global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("M_right", "RIGHT", m_world))

    n_world = _build_hand(fist_curls, fist_abduction, thumb=(1.05, 0.15, 0.35, 0.20), global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("N_right", "RIGHT", n_world))

    # --- K / P: identical handshape (index + middle extended and slightly
    # splayed, ring/pinky curled, thumb between index and middle), wrist
    # rotated forward-up (K) vs. down (P).
    k_curls = curls(_STRAIGHT, _STRAIGHT, _CURL, _CURL)
    k_abduction = abduction(index=-0.05, middle=0.20)
    k_thumb = (0.75, 0.45, 0.10, 0.10)
    k_world = _build_hand(k_curls, k_abduction, thumb=k_thumb, global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("K_right", "RIGHT", k_world))

    p_world = _build_hand(k_curls, k_abduction, thumb=k_thumb, global_rotation=_ROT_DOWN, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("P_right", "RIGHT", p_world))

    # --- G / Q: identical handshape (index extended pointing sideways,
    # thumb parallel to it, other fingers curled), wrist forward-up (G) vs.
    # down (Q).
    g_curls = curls(_STRAIGHT, _CURL, _CURL, _CURL)
    g_abduction = abduction(index=0.10)
    g_thumb = (0.10, 0.05, 0.10, 0.05)
    g_world = _build_hand(g_curls, g_abduction, thumb=g_thumb, global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("G_right", "RIGHT", g_world))

    q_world = _build_hand(g_curls, g_abduction, thumb=g_thumb, global_rotation=_ROT_DOWN, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("Q_right", "RIGHT", q_world))

    # --- H / U / V: together+sideways / together+up / spread+up.
    together_curls = curls(_STRAIGHT, _STRAIGHT, _CURL, _CURL)
    together_abduction = abduction(index=0.05, middle=-0.05)
    spread_abduction = abduction(index=0.35, middle=-0.15)
    together_thumb = (0.10, 0.05, 0.20, 0.10)

    h_world = _build_hand(together_curls, together_abduction, thumb=together_thumb, global_rotation=_ROT_SIDE, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("H_right", "RIGHT", h_world))

    u_world = _build_hand(together_curls, together_abduction, thumb=together_thumb, global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("U_right", "RIGHT", u_world))

    v_world = _build_hand(together_curls, spread_abduction, thumb=together_thumb, global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("V_right", "RIGHT", v_world))

    # --- R / U: index crossed over middle vs. parallel (U from above is the
    # "parallel" reference for this pair too).
    crossed_abduction = abduction(index=-0.30, middle=0.08)
    r_world = _build_hand(together_curls, crossed_abduction, thumb=together_thumb, global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("R_right", "RIGHT", r_world))

    # --- D / F: index straight vs. pinched with thumb, other fingers curled
    # vs. straight.
    d_curls = curls(_STRAIGHT, _CURL, _CURL, _CURL)
    d_world = _build_hand(d_curls, abduction(), thumb=(0.35, 0.10, 0.55, 0.30), global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("D_right", "RIGHT", d_world))

    f_curls = curls(_HALF_CURL, _STRAIGHT, _STRAIGHT, _STRAIGHT)
    f_world = _build_hand(f_curls, abduction(), thumb=(0.45, 0.30, 0.70, 0.55), global_rotation=_ROT_UP, translation=_TRANSLATION, jitter_rng=rng)
    cases.append(_case("F_right", "RIGHT", f_world))

    # --- Same pose as LEFT and RIGHT hands: mirror the A pose's raw world
    # landmarks (negate x) and label it LEFT. normalize() must produce the
    # same output as "A_right" above.
    a_left_world = a_world.copy()
    a_left_world[:, 0] = -a_left_world[:, 0]
    cases.append(_case("A_left", "LEFT", a_left_world))

    # --- Degenerate: wrist coincident with middle MCP (|u| == 0).
    degenerate_curls = curls(_STRAIGHT, _STRAIGHT, _STRAIGHT, _STRAIGHT)
    degenerate_wrist_world = _build_hand(degenerate_curls, abduction(), thumb=(0.10, 0.05, 0.10, 0.05), global_rotation=np.eye(3), translation=_TRANSLATION, jitter_rng=None)
    degenerate_wrist_world[9] = degenerate_wrist_world[0]  # middle MCP == wrist
    cases.append(_case("degenerate_wrist_coincident_with_middle_mcp", "RIGHT", degenerate_wrist_world))

    # --- Degenerate: collinear palm (index MCP, middle MCP, pinky MCP all on
    # the wrist->middle-MCP line, so v is parallel to e1 and w == 0).
    collinear_world = _build_hand(degenerate_curls, abduction(), thumb=(0.10, 0.05, 0.10, 0.05), global_rotation=np.eye(3), translation=_TRANSLATION, jitter_rng=None)
    middle_mcp = collinear_world[9] - collinear_world[0]
    collinear_world[5] = collinear_world[0] + 0.6 * middle_mcp  # index MCP on the wrist-middle line
    collinear_world[17] = collinear_world[0] + 1.4 * middle_mcp  # pinky MCP on the same line
    cases.append(_case("degenerate_collinear_palm", "RIGHT", collinear_world))

    return cases


def _format_float(value: float) -> float:
    # json.dumps with a custom float repr isn't supported directly, so
    # rounding to 9 significant digits is done before serialisation instead.
    if value == 0.0:
        return 0.0
    return float(f"{value:.9g}")


def _round_case(case: dict) -> dict:
    rounded = {
        "name": case["name"],
        "handedness": case["handedness"],
        "world": [[_format_float(c) for c in point] for point in case["world"]],
        "expected": None if case["expected"] is None else [_format_float(v) for v in case["expected"]],
    }
    return rounded


def golden_json_text() -> str:
    cases = [_round_case(case) for case in build_cases()]
    return json.dumps(cases, indent=2) + "\n"


def main() -> None:
    _GOLDEN_PATH.parent.mkdir(parents=True, exist_ok=True)
    _GOLDEN_PATH.write_text(golden_json_text())
    print(f"Wrote {_GOLDEN_PATH}")


if __name__ == "__main__":
    main()
