"""Tests for handspell.normalize against docs/CLASSIFIER.md sections 1-2.

Two kinds of coverage, deliberately kept separate:

- Golden-vector reproduction (test_golden_cases, test_golden_is_fresh): the
  fixture at training/testdata/normalizer_golden.json is the cross-language
  contract the Kotlin implementation is also tested against.
- Analytic invariants (everything else): properties that follow from the
  spec's math and hold regardless of what is in the golden file, so a
  regression in normalize.py fails here even if nobody remembered to
  regenerate the fixture.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest
from handspell.golden import build_cases, golden_json_text
from handspell.normalize import (
    ORIENTATION_DIM,
    SHAPE_DIM,
    VECTOR_DIM,
    normalize,
    normalize_capture_row,
)

_GOLDEN_PATH = Path(__file__).resolve().parent.parent / "testdata" / "normalizer_golden.json"
_ABS_TOL = 1e-6


def _load_golden() -> list[dict]:
    return json.loads(_GOLDEN_PATH.read_text())


@pytest.mark.parametrize("case", _load_golden(), ids=lambda case: case["name"])
def test_golden_cases(case: dict) -> None:
    world = np.array(case["world"], dtype=np.float64)
    result = normalize(world, case["handedness"])
    if case["expected"] is None:
        assert result is None
    else:
        assert result is not None
        expected = np.array(case["expected"], dtype=np.float64)
        np.testing.assert_allclose(result.astype(np.float64), expected, atol=_ABS_TOL)


def test_golden_is_fresh() -> None:
    """Regenerating the golden cases in memory must equal the committed file
    byte-for-byte. If this fails, normalize.py changed behaviour and nobody
    ran `python -m handspell.golden` to update the fixture."""
    assert golden_json_text() == _GOLDEN_PATH.read_text()


def _flat_hand(seed: int = 0) -> np.ndarray:
    """A plausible, mildly-jittered flat right hand: wrist at origin, MCPs
    spread along +y with a small arc in x, fingers extended along +y."""
    rng = np.random.default_rng(seed)
    points = np.zeros((21, 3))
    mcp = {1: (0.025, 0.02, 0.01), 5: (0.020, 0.085, 0.0), 9: (0.0, 0.095, 0.0), 13: (-0.020, 0.088, 0.0), 17: (-0.038, 0.078, 0.0)}
    chains = {
        1: [(0.06, 0.0, 0.01), (0.09, 0.0, 0.015), (0.115, 0.0, 0.02)],
        5: [(0.020, 0.125, 0.0), (0.020, 0.150, 0.0), (0.020, 0.170, 0.0)],
        9: [(0.0, 0.140, 0.0), (0.0, 0.168, 0.0), (0.0, 0.190, 0.0)],
        13: [(-0.020, 0.130, 0.0), (-0.020, 0.156, 0.0), (-0.020, 0.176, 0.0)],
        17: [(-0.038, 0.110, 0.0), (-0.038, 0.130, 0.0), (-0.038, 0.148, 0.0)],
    }
    for mcp_index, coords in mcp.items():
        points[mcp_index] = coords
    for mcp_index, joints in chains.items():
        for offset, coords in enumerate(joints, start=1):
            points[mcp_index + offset] = coords
    points += rng.normal(scale=0.001, size=points.shape)
    points[0] = 0.0  # wrist stays exactly at the origin
    return points


def test_vector_dims() -> None:
    assert VECTOR_DIM == SHAPE_DIM + ORIENTATION_DIM == 66


def test_landmark_9_is_unit_x() -> None:
    world = _flat_hand(seed=1)
    result = normalize(world, "RIGHT")
    assert result is not None
    # Shape index (i - 1) * 3 maps to landmark i; landmark 9 is (1, 0, 0) by
    # construction (docs/CLASSIFIER.md step 5).
    landmark_9 = result[(9 - 1) * 3 : (9 - 1) * 3 + 3]
    np.testing.assert_allclose(landmark_9, [1.0, 0.0, 0.0], atol=_ABS_TOL)


def test_orientation_vectors_are_orthonormal() -> None:
    world = _flat_hand(seed=2)
    result = normalize(world, "RIGHT")
    assert result is not None
    e1 = result[60:63].astype(np.float64)
    e3 = result[63:66].astype(np.float64)
    assert abs(np.linalg.norm(e1) - 1.0) < _ABS_TOL
    assert abs(np.linalg.norm(e3) - 1.0) < _ABS_TOL
    assert abs(np.dot(e1, e3)) < _ABS_TOL


def test_mirroring_right_to_left_preserves_shape_block() -> None:
    world = _flat_hand(seed=3)
    right = normalize(world, "RIGHT")
    mirrored_world = world.copy()
    mirrored_world[:, 0] = -mirrored_world[:, 0]
    left = normalize(mirrored_world, "LEFT")
    assert right is not None and left is not None
    np.testing.assert_allclose(left[:60].astype(np.float64), right[:60].astype(np.float64), atol=_ABS_TOL)
    # Mirroring a LEFT capture back into canonical space reproduces the exact
    # RIGHT vector, orientation block included.
    np.testing.assert_allclose(left.astype(np.float64), right.astype(np.float64), atol=_ABS_TOL)


def test_rigid_rotation_changes_only_orientation_block() -> None:
    world = _flat_hand(seed=4)
    baseline = normalize(world, "RIGHT")
    assert baseline is not None

    theta = 0.7
    rotation = np.array(
        [
            [np.cos(theta), 0.0, np.sin(theta)],
            [0.0, 1.0, 0.0],
            [-np.sin(theta), 0.0, np.cos(theta)],
        ]
    )
    rotated_world = world @ rotation.T
    rotated = normalize(rotated_world, "RIGHT")
    assert rotated is not None

    np.testing.assert_allclose(rotated[:60].astype(np.float64), baseline[:60].astype(np.float64), atol=_ABS_TOL)
    assert not np.allclose(rotated[60:66].astype(np.float64), baseline[60:66].astype(np.float64), atol=1e-3)


def test_uniform_scaling_is_invariant() -> None:
    world = _flat_hand(seed=5)
    baseline = normalize(world, "RIGHT")
    assert baseline is not None

    scaled = normalize(world * 1.8, "RIGHT")
    assert scaled is not None
    np.testing.assert_allclose(scaled.astype(np.float64), baseline.astype(np.float64), atol=_ABS_TOL)


def test_degenerate_wrist_coincident_with_middle_mcp_returns_none() -> None:
    world = _flat_hand(seed=6)
    world[9] = world[0]
    assert normalize(world, "RIGHT") is None


def test_degenerate_collinear_palm_returns_none() -> None:
    world = _flat_hand(seed=7)
    middle_mcp_vector = world[9] - world[0]
    world[5] = world[0] + 0.6 * middle_mcp_vector
    world[17] = world[0] + 1.4 * middle_mcp_vector
    assert normalize(world, "RIGHT") is None


def test_normalize_capture_row_matches_direct_call() -> None:
    world = _flat_hand(seed=8)
    direct = normalize(world, "RIGHT")
    assert direct is not None

    row = {"handedness": "RIGHT"}
    for i in range(21):
        row[f"wx{i}"] = world[i, 0]
        row[f"wy{i}"] = world[i, 1]
        row[f"wz{i}"] = world[i, 2]
    via_row = normalize_capture_row(row)
    assert via_row is not None
    np.testing.assert_allclose(via_row.astype(np.float64), direct.astype(np.float64), atol=_ABS_TOL)


def test_build_cases_count_and_groups_covered() -> None:
    cases = build_cases()
    assert len(cases) >= 12
    names = {case["name"] for case in cases}
    for expected_letter in ("A", "S", "M", "N", "K", "P", "G", "Q", "H", "U", "V", "R", "D", "F"):
        assert any(name.startswith(expected_letter + "_") for name in names), expected_letter
    assert sum(1 for case in cases if case["expected"] is None) == 2
    assert {case["handedness"] for case in cases} == {"LEFT", "RIGHT"}
