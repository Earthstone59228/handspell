"""Tests for handspell.references: the capture CSV -> reference set build in
docs/CLASSIFIER.md section 3.

Captures are synthetic here -- a parametric hand whose fingers curl by a
parameter -- because the properties under test are all about selection and
serialisation, not about anatomy: near-identical frames get dropped, distinct
poses get kept, the per-letter cap holds, and a row from the wrong frame
convention stops the build instead of quietly poisoning the reference set.
"""

from __future__ import annotations

import csv
import math
from pathlib import Path

import numpy as np
import pytest
from handspell.normalize import VECTOR_DIM, normalize
from handspell.references import (
    DEDUPE_DISTANCE,
    FRAME_CONVENTION,
    MAX_EXEMPLARS_PER_LETTER,
    format_reference_csv,
    select_exemplars,
    write_reference_csv,
)

_METADATA_COLUMNS = [
    "schema_version",
    "frame_convention",
    "session_id",
    "signer_id",
    "letter",
    "captured_at_iso",
    "timestamp_ms",
    "handedness",
    "handedness_score",
    "image_width",
    "image_height",
    "rotation_degrees",
    "device_model",
    "app_version",
    "landmarker_model",
]
_WORLD_COLUMNS = [f"w{axis}{i}" for i in range(21) for axis in "xyz"]
_IMAGE_COLUMNS = [f"i{axis}{i}" for i in range(21) for axis in "xyz"]
_HEADER = _METADATA_COLUMNS + _WORLD_COLUMNS + _IMAGE_COLUMNS

_MCP = {
    1: (0.025, 0.020, 0.010),
    5: (0.020, 0.085, 0.0),
    9: (0.0, 0.095, 0.0),
    13: (-0.020, 0.088, 0.0),
    17: (-0.038, 0.078, 0.0),
}
_SEGMENTS = {1: 0.030, 5: 0.038, 9: 0.042, 13: 0.038, 17: 0.030}


def _hand(curl: float) -> np.ndarray:
    """21 world landmarks for a hand whose fingers curl toward the palm by
    `curl` radians per segment. curl=0 is a flat hand."""
    points = np.zeros((21, 3))
    for mcp_index, position in _MCP.items():
        points[mcp_index] = position
        length = _SEGMENTS[mcp_index]
        joint = np.array(position, dtype=np.float64)
        for step in range(1, 4):
            angle = curl * step
            joint = joint + length * np.array([0.0, math.cos(angle), -math.sin(angle)])
            points[mcp_index + step] = joint
    return points


def _row(letter: str, world: np.ndarray, frame_convention: str = FRAME_CONVENTION) -> dict[str, str]:
    row = {
        "schema_version": "1",
        "frame_convention": frame_convention,
        "session_id": "sess-1",
        "signer_id": "s1",
        "letter": letter,
        "captured_at_iso": "2026-09-13T10:00:00Z",
        "timestamp_ms": "1000",
        "handedness": "RIGHT",
        "handedness_score": "0.98",
        "image_width": "1280",
        "image_height": "720",
        "rotation_degrees": "270",
        "device_model": "test",
        "app_version": "0.1.0",
        "landmarker_model": "hand_landmarker.task",
    }
    for i in range(21):
        for axis_index, axis in enumerate("xyz"):
            row[f"w{axis}{i}"] = repr(float(world[i, axis_index]))
            row[f"i{axis}{i}"] = "0.5"
    return row


def _write_capture(directory: Path, name: str, rows: list[dict[str, str]]) -> Path:
    path = directory / name
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=_HEADER)
        writer.writeheader()
        writer.writerows(rows)
    return path


def test_near_duplicate_frames_collapse_to_one(tmp_path: Path) -> None:
    world = _hand(curl=0.0)
    _write_capture(tmp_path / "s1", "A_sess.csv", [_row("A", world) for _ in range(12)])

    exemplars, stats = select_exemplars(tmp_path)

    assert list(exemplars) == ["A"]
    assert len(exemplars["A"]) == 1
    assert stats.rows == 12
    assert stats.near_duplicate == 11


def test_distinct_poses_are_all_kept(tmp_path: Path) -> None:
    curls = [0.0, 0.35, 0.7, 1.05]
    _write_capture(tmp_path / "s1", "A_sess.csv", [_row("A", _hand(curl)) for curl in curls])

    exemplars, stats = select_exemplars(tmp_path)

    assert len(exemplars["A"]) == len(curls)
    assert stats.near_duplicate == 0
    # The poses really are separated by more than the dedupe radius, so this
    # test is asserting selection behaviour rather than a lucky generator.
    vectors = [normalize(_hand(curl), "RIGHT") for curl in curls]
    for i in range(len(vectors)):
        for j in range(i + 1, len(vectors)):
            assert float(np.linalg.norm(vectors[i] - vectors[j])) >= DEDUPE_DISTANCE


def test_per_letter_cap_is_enforced(tmp_path: Path) -> None:
    rows = [_row("B", _hand(curl=0.05 * step)) for step in range(12)]
    _write_capture(tmp_path / "s1", "B_sess.csv", rows)

    exemplars, stats = select_exemplars(tmp_path, dedupe_distance=0.0, max_per_letter=4)

    assert len(exemplars["B"]) == 4
    assert stats.over_cap == 8


def test_default_cap_and_radius_match_the_spec() -> None:
    assert MAX_EXEMPLARS_PER_LETTER == 64
    assert DEDUPE_DISTANCE == 0.05


def test_letters_from_several_signers_and_files_merge(tmp_path: Path) -> None:
    _write_capture(tmp_path / "s1", "A_sess.csv", [_row("A", _hand(0.0))])
    _write_capture(tmp_path / "s2", "A_sess.csv", [_row("A", _hand(0.7))])
    _write_capture(tmp_path / "s2", "C_sess.csv", [_row("C", _hand(1.05))])

    exemplars, stats = select_exemplars(tmp_path)

    assert stats.files == 3
    assert len(exemplars["A"]) == 2
    assert len(exemplars["C"]) == 1


def test_degenerate_rows_are_skipped_not_fatal(tmp_path: Path) -> None:
    degenerate = _hand(curl=0.0)
    degenerate[9] = degenerate[0]  # middle MCP on the wrist: |u| == 0
    rows = [_row("A", degenerate), _row("A", _hand(curl=0.0))]
    _write_capture(tmp_path / "s1", "A_sess.csv", rows)

    exemplars, stats = select_exemplars(tmp_path)

    assert stats.degenerate == 1
    assert len(exemplars["A"]) == 1


def test_wrong_frame_convention_is_fatal(tmp_path: Path) -> None:
    rows = [_row("A", _hand(0.0), frame_convention="selfie-upright-v2")]
    _write_capture(tmp_path / "s1", "A_sess.csv", rows)

    with pytest.raises(ValueError, match="frame_convention"):
        select_exemplars(tmp_path)


def test_motion_letter_is_fatal(tmp_path: Path) -> None:
    _write_capture(tmp_path / "s1", "J_sess.csv", [_row("J", _hand(0.0))])

    with pytest.raises(ValueError, match="static letters"):
        select_exemplars(tmp_path)


def test_csv_has_spec_comment_header_and_one_row_per_exemplar(tmp_path: Path) -> None:
    _write_capture(tmp_path / "s1", "A_sess.csv", [_row("A", _hand(curl)) for curl in (0.0, 0.7)])
    _write_capture(tmp_path / "s1", "B_sess.csv", [_row("B", _hand(1.05))])

    exemplars, _ = select_exemplars(tmp_path)
    out = tmp_path / "references-v1.csv"
    write_reference_csv(out, exemplars)
    lines = out.read_text(encoding="utf-8").splitlines()

    assert lines[0] == "# spec_version=1"
    assert lines[1] == ",".join(["letter", *(f"f{i}" for i in range(VECTOR_DIM))])
    assert len(lines) == 2 + 3
    # Letters are alphabetical, so the file is stable across capture orders.
    assert [line.split(",", 1)[0] for line in lines[2:]] == ["A", "A", "B"]
    for line in lines[2:]:
        assert len(line.split(",")) == VECTOR_DIM + 1


def test_csv_values_round_trip_to_the_normalized_vector(tmp_path: Path) -> None:
    world = _hand(curl=0.4)
    _write_capture(tmp_path / "s1", "A_sess.csv", [_row("A", world)])

    exemplars, _ = select_exemplars(tmp_path)
    text = format_reference_csv(exemplars)
    row = text.splitlines()[2].split(",")

    expected = normalize(world, "RIGHT")
    parsed = np.array([float(value) for value in row[1:]], dtype=np.float64)
    np.testing.assert_allclose(parsed, expected.astype(np.float64), atol=1e-6)


def test_empty_reference_set_is_refused() -> None:
    with pytest.raises(ValueError, match="empty reference set"):
        format_reference_csv({})
