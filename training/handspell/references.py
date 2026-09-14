"""Builds assets/classifier/references-v1.csv -- the stage-1 k-NN reference
set described in docs/CLASSIFIER.md section 3 -- from the capture CSVs the
in-app capture screen writes (section 7).

Two jobs, both deliberately boring:

- Normalise every captured frame through `normalize.normalize_capture_row`,
  the same code path the Android app runs, so the exemplars live in exactly
  the space the classifier measures distances in.
- Throw most of them away. A 3-per-second capture of a held pose produces
  near-identical frames; keeping them would make the k-NN vote count how long
  a signer held still rather than how a letter looks. The greedy dedupe keeps
  a frame only if it is at least `DEDUPE_DISTANCE` from every frame already
  kept for that letter, which preserves pose variety and drops the rest.

Nothing here is a training step: there is no fitting, no learned parameter
and no randomness, so running it twice on the same captures produces the same
file byte for byte.
"""

from __future__ import annotations

import csv
from collections import OrderedDict
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

from handspell.normalize import SPEC_VERSION, VECTOR_DIM, normalize_capture_row

# Minimum Euclidean distance (unweighted, in the 66-d normalised space) between
# two kept exemplars of the same letter. docs/CLASSIFIER.md section 3.
DEDUPE_DISTANCE = 0.05

# Per-letter cap, so one signer's long capture session cannot dominate a vote.
MAX_EXEMPLARS_PER_LETTER = 64

# Every capture row must declare this; a future change to the camera frame
# convention has to be loud rather than silently mirroring the whole set.
FRAME_CONVENTION = "selfie-upright-v1"

# Mirrors Letter.staticLetters in the Android app: J and Z are produced with
# motion and have no single-frame handshape.
STATIC_LETTERS = tuple("ABCDEFGHIKLMNOPQRSTUVWXY")

_SPEC_VERSION_COMMENT = f"# spec_version={SPEC_VERSION}"
_REQUIRED_COLUMNS = ("frame_convention", "letter", "handedness")


@dataclass
class BuildStats:
    """What the build did, so the script can print it instead of a bare count."""

    files: int = 0
    rows: int = 0
    degenerate: int = 0
    near_duplicate: int = 0
    over_cap: int = 0
    kept_per_letter: dict[str, int] = field(default_factory=dict)

    @property
    def kept(self) -> int:
        return sum(self.kept_per_letter.values())


def capture_files(capture_dir: Path) -> list[Path]:
    """Every capture CSV under `capture_dir`, in a stable order.

    The layout is `<capture_dir>/<signer_id>/<letter>_<session_id>.csv`, but
    this only sorts paths -- the authoritative letter is the `letter` column,
    because a mis-named file should not silently relabel exemplars.
    """
    return sorted(path for path in capture_dir.rglob("*.csv") if path.is_file())


def _validate_row(row: dict, path: Path, line: int) -> str:
    for column in _REQUIRED_COLUMNS:
        if column not in row or row[column] is None:
            raise ValueError(f"{path}:{line}: capture row is missing the '{column}' column")
    convention = row["frame_convention"].strip()
    if convention != FRAME_CONVENTION:
        raise ValueError(
            f"{path}:{line}: frame_convention is '{convention}', expected '{FRAME_CONVENTION}' "
            "-- these landmarks are in a different frame and must not be mixed in"
        )
    letter = row["letter"].strip().upper()
    if letter not in STATIC_LETTERS:
        raise ValueError(
            f"{path}:{line}: letter is '{letter}', which is not one of the "
            f"{len(STATIC_LETTERS)} static letters"
        )
    return letter


def select_exemplars(
    capture_dir: Path,
    dedupe_distance: float = DEDUPE_DISTANCE,
    max_per_letter: int = MAX_EXEMPLARS_PER_LETTER,
) -> tuple[OrderedDict[str, list[np.ndarray]], BuildStats]:
    """Normalises every capture row and greedily dedupes it into at most
    `max_per_letter` exemplars per letter, in capture order.

    Raises ValueError for a row whose frame convention, letter or landmark
    columns are wrong -- a malformed capture is a data bug to fix, not
    something to skip quietly. Degenerate geometry (normalize returns None) is
    counted and skipped, because a hand at a bad angle for one frame is
    normal.
    """
    stats = BuildStats()
    kept: OrderedDict[str, list[np.ndarray]] = OrderedDict()

    for path in capture_files(capture_dir):
        stats.files += 1
        with path.open(newline="", encoding="utf-8") as handle:
            for line, row in enumerate(csv.DictReader(handle), start=2):
                stats.rows += 1
                letter = _validate_row(row, path, line)
                try:
                    vector = normalize_capture_row(row)
                except (KeyError, TypeError, ValueError) as error:
                    raise ValueError(f"{path}:{line}: unreadable landmark columns ({error})") from error
                if vector is None:
                    stats.degenerate += 1
                    continue

                letter_exemplars = kept.setdefault(letter, [])
                if len(letter_exemplars) >= max_per_letter:
                    stats.over_cap += 1
                    continue
                if any(
                    float(np.linalg.norm(vector - existing)) < dedupe_distance
                    for existing in letter_exemplars
                ):
                    stats.near_duplicate += 1
                    continue
                letter_exemplars.append(vector)

    stats.kept_per_letter = {letter: len(vectors) for letter, vectors in sorted(kept.items())}
    return kept, stats


def _format_float(value: float) -> str:
    return f"{float(value):.9g}"


def format_reference_csv(exemplars: dict[str, list[np.ndarray]]) -> str:
    """Serialises the reference set exactly as KnnLetterClassifier.load reads
    it: a spec-version comment, a `letter,f0..f65` header, then one row per
    exemplar, letters in alphabetical order and exemplars in capture order.
    """
    if not exemplars:
        raise ValueError("refusing to write an empty reference set")

    header = ",".join(["letter", *(f"f{i}" for i in range(VECTOR_DIM))])
    lines = [_SPEC_VERSION_COMMENT, header]
    for letter in sorted(exemplars):
        for vector in exemplars[letter]:
            if vector.shape != (VECTOR_DIM,):
                raise ValueError(f"{letter}: exemplar has shape {vector.shape}, expected ({VECTOR_DIM},)")
            lines.append(",".join([letter, *(_format_float(value) for value in vector)]))
    return "\n".join(lines) + "\n"


def write_reference_csv(path: Path, exemplars: dict[str, list[np.ndarray]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(format_reference_csv(exemplars), encoding="utf-8")
