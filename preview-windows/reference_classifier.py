"""Runs the app's stage-1 identification pipeline on the desktop.

This is the same algorithm the Android build runs, ported rather than
invented, so the preview can answer "does this actually recognise my hand?"
before anything in `android/` is touched:

* normalisation      -> `handspell.normalize` from the repo's training package,
                        the exact module the golden-vector tests pin the Kotlin
                        `DefaultHandNormalizer` against.
* k-nearest-neighbour -> `KnnLetterClassifier.kt`: Euclidean distance in the
                        66-d space with the orientation block weighted 0.5,
                        k = 5, each neighbour voting 1/(d + 0.01).
* thresholds          -> `FeedbackThresholds` in `FeedbackEngine.kt`.

The one deliberate difference is the target. The app compares against the
letter a drill is asking for; there is no drill here, so the gates are always
evaluated against the letter the classifier currently ranks first. That makes
the readout mean "if this letter were the target, would the app accept it?" --
which is exactly the question the preview exists to answer.

Nothing here imports Android, and nothing in `android/` imports this.
"""

from __future__ import annotations

import csv
import sys
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

SHAPE_DIM = 60
VECTOR_DIM = 66

# --- KnnLetterClassifier.kt ------------------------------------------------------------------
K = 5
ORIENTATION_WEIGHT = 0.5
DIRECTION_AGNOSTIC_LETTERS = frozenset("ABCD")
DISTANCE_EPSILON = 0.01

# --- FeedbackThresholds (FeedbackEngine.kt) --------------------------------------------------
MATCH_PROBABILITY = 0.85
ADJUST_PROBABILITY = 0.45
MATCH_MARGIN = 0.20
REJECT_DISTANCE = 0.45
MATCH_DISTANCE = 0.32
EMA_ALPHA = 0.35
HOLD_TO_CONFIRM_MS = 400
MATCH_LATCH_MS = 800

MATCH = "MATCH"
ADJUST = "ADJUST"
NOT_RECOGNISED = "NOT RECOGNISED"
NO_HAND = "NO HAND"


def load_repo_modules(repo: Path) -> dict:
    """Import the repo's normaliser and letter list.

    Imported rather than copied so this cannot silently drift from what the
    recorder wrote and what the Android app reads.
    """
    training = repo / "training"
    if not (training / "handspell" / "normalize.py").is_file():
        raise FileNotFoundError(
            f"{training} does not look like the handspell training package. "
            "Pass --repo <path to the handspell checkout>."
        )
    if str(training) not in sys.path:
        sys.path.insert(0, str(training))

    from handspell.normalize import SPEC_VERSION, VECTOR_DIM as dim, normalize
    from handspell.references import STATIC_LETTERS

    return {
        "SPEC_VERSION": SPEC_VERSION,
        "VECTOR_DIM": dim,
        "normalize": normalize,
        "STATIC_LETTERS": STATIC_LETTERS,
    }


@dataclass
class ReferenceSet:
    """The exemplars from `references-v1.csv`, parsed the way the Kotlin loader does."""

    path: Path
    letters: list[str]
    exemplar_letters: list[str]
    vectors: np.ndarray
    spec_version: int
    counts: dict[str, int] = field(default_factory=dict)

    @property
    def total(self) -> int:
        return len(self.exemplar_letters)

    def summary(self) -> str:
        return (
            f"{self.path.name}: {self.total} exemplars over {len(self.letters)} letters "
            f"(spec {self.spec_version})"
        )


def world_array(landmarks) -> np.ndarray:
    """MediaPipe world landmarks (objects with .x/.y/.z) -> a (21, 3) float64 array."""
    return np.asarray([[lm.x, lm.y, lm.z] for lm in landmarks], dtype=np.float64)


def load_reference_set(path: Path, static_letters: tuple[str, ...]) -> ReferenceSet:
    """Parse a reference CSV, rejecting anything `KnnLetterClassifier.load` would reject."""
    if not path.is_file():
        raise FileNotFoundError(f"no reference set at {path}")

    spec_version: int | None = None
    header_seen = False
    exemplar_letters: list[str] = []
    rows: list[list[float]] = []

    with path.open(newline="", encoding="utf-8") as handle:
        for index, raw in enumerate(csv.reader(handle), start=1):
            if not raw:
                continue
            if raw[0].lstrip().startswith("#"):
                body = raw[0].lstrip()[1:].strip()
                if body.startswith("spec_version"):
                    spec_version = int(body.split("=", 1)[1].strip())
                continue
            if not header_seen:
                if spec_version is None:
                    raise ValueError(f"{path}:{index}: no '# spec_version=' comment before the header")
                if len(raw) != VECTOR_DIM + 1 or raw[0].strip() != "letter":
                    raise ValueError(f"{path}:{index}: unexpected header row")
                header_seen = True
                continue
            if len(raw) != VECTOR_DIM + 1:
                raise ValueError(f"{path}:{index}: {len(raw)} fields, expected {VECTOR_DIM + 1}")
            letter = raw[0].strip().upper()
            if letter not in static_letters:
                raise ValueError(f"{path}:{index}: '{letter}' is not a static letter")
            values = [float(text) for text in raw[1:]]
            if not all(np.isfinite(values)):
                raise ValueError(f"{path}:{index}: non-finite value")
            exemplar_letters.append(letter)
            rows.append(values)

    if not rows:
        raise ValueError(f"{path}: no exemplar rows")

    counts: dict[str, int] = {}
    for letter in exemplar_letters:
        counts[letter] = counts.get(letter, 0) + 1

    return ReferenceSet(
        path=path,
        letters=sorted(counts),
        exemplar_letters=exemplar_letters,
        vectors=np.asarray(rows, dtype=np.float32),
        spec_version=spec_version,
        counts=counts,
    )


def distances_to_exemplars(vector: np.ndarray, vectors: np.ndarray) -> np.ndarray:
    """Euclidean distance with the orientation block's squared differences weighted 0.5."""
    delta = vectors - vector
    squared = delta * delta
    total = squared[:, :SHAPE_DIM].sum(axis=1) + ORIENTATION_WEIGHT * squared[:, SHAPE_DIM:].sum(axis=1)
    return np.sqrt(total)


def shape_distances_to_exemplars(vector: np.ndarray, vectors: np.ndarray) -> np.ndarray:
    """Orientation-invariant shape distances exposed alongside the weighted k-NN metric."""
    delta = vectors[:, :SHAPE_DIM] - vector[:SHAPE_DIM]
    return np.sqrt((delta * delta).sum(axis=1))


def classify_frame(
    vector: np.ndarray,
    vectors: np.ndarray,
    exemplar_letters: list[str],
    candidates: list[str],
    k: int = K,
) -> tuple[list[tuple[str, float]], float, float]:
    """Return (ranked [(letter, probability)], weighted nearest, paired shape distance).

    Mirrors `KnnLetterClassifier.classify`: the k nearest exemplars each vote
    `1 / (d + 0.01)`, votes are summed per letter and normalised, and ties fall
    back to letter order because the candidates are already sorted.
    """
    if len(vectors) == 0:
        return [(letter, 0.0) for letter in candidates], float("inf"), float("inf")

    distances = distances_to_exemplars(vector, vectors)
    shape_distances = shape_distances_to_exemplars(vector, vectors)
    # Stable sort so equidistant exemplars keep file order, matching the Kotlin
    # insertion loop, which only displaces a neighbour on a strictly smaller distance.
    nearest = np.argsort(distances, kind="stable")[:k]

    weight = dict.fromkeys(candidates, 0.0)
    total = 0.0
    for index in nearest:
        vote = 1.0 / (float(distances[index]) + DISTANCE_EPSILON)
        weight[exemplar_letters[int(index)]] += vote
        total += vote

    ranked = sorted(
        ((letter, weight[letter] / total) for letter in candidates),
        key=lambda pair: (-pair[1], pair[0]),
    )
    nearest_index = int(nearest[0])
    return ranked, float(distances[nearest_index]), float(shape_distances[nearest_index])


@dataclass
class Reading:
    """What the overlay prints for one frame."""

    top: str | None
    top_probability: float
    runner_up: str | None
    runner_up_probability: float
    nearest_distance: float
    verdict: str
    hold_progress: float
    ranked: list[tuple[str, float]] = field(default_factory=list)

    @property
    def margin(self) -> float:
        return self.top_probability - self.runner_up_probability


class HandIdentifier:
    """Normalise -> classify -> smooth -> judge, mirroring the app's pipeline."""

    def __init__(self, reference: ReferenceSet, normalizer, k: int = K) -> None:
        self.reference = reference
        self.normalize = normalizer
        self.k = k
        self.smoothed: dict[str, float] = dict.fromkeys(reference.letters, 0.0)
        self.seen_frame = False
        self.hold_start_ms: int | None = None
        self.matched_at_ms: int | None = None
        self.hand_free_frames = 0

    def reset(self) -> None:
        self.smoothed = dict.fromkeys(self.reference.letters, 0.0)
        self.seen_frame = False
        self.hold_start_ms = None
        self.matched_at_ms = None
        self.hand_free_frames = 0

    def update(self, world: np.ndarray, handedness: str, timestamp_ms: int) -> Reading | None:
        """One frame with a hand. Returns None when the pose is degenerate."""
        vector = self.normalize(world, handedness)
        if vector is None:
            return self.no_hand(timestamp_ms)

        self.hand_free_frames = 0
        ranked, nearest, nearest_shape = classify_frame(
            vector,
            self.reference.vectors,
            self.reference.exemplar_letters,
            self.reference.letters,
            self.k,
        )

        incoming = dict.fromkeys(self.reference.letters, 0.0)
        for letter, probability in ranked:
            incoming[letter] = probability
        if not self.seen_frame:
            # Seeding with the raw frame rather than ramping up from zero, as the engine does.
            self.smoothed = dict(incoming)
            self.seen_frame = True
        else:
            for letter in self.reference.letters:
                self.smoothed[letter] = EMA_ALPHA * incoming[letter] + (1.0 - EMA_ALPHA) * self.smoothed[letter]

        ordered = sorted(self.reference.letters, key=lambda letter: (-self.smoothed[letter], letter))
        top = ordered[0]
        top_probability = self.smoothed[top]
        runner_up = ordered[1] if len(ordered) > 1 else None
        runner_up_probability = self.smoothed[runner_up] if runner_up else 0.0
        acceptance_distance = nearest_shape if top in DIRECTION_AGNOSTIC_LETTERS else nearest

        conditions_met = (
            top_probability >= MATCH_PROBABILITY
            and top_probability - runner_up_probability >= MATCH_MARGIN
            and acceptance_distance <= MATCH_DISTANCE
        )

        hold_progress = 0.0
        verdict = NOT_RECOGNISED

        # The latch keeps a confirmed match visible through a brief dip, as the engine does.
        if self.matched_at_ms is not None and timestamp_ms - self.matched_at_ms < MATCH_LATCH_MS:
            verdict = MATCH
            if conditions_met and self.hold_start_ms is not None:
                hold_progress = self._progress(timestamp_ms - self.hold_start_ms)
        else:
            self.matched_at_ms = None
            if conditions_met:
                if self.hold_start_ms is None:
                    self.hold_start_ms = timestamp_ms
                elapsed = timestamp_ms - self.hold_start_ms
                hold_progress = self._progress(elapsed)
                if elapsed >= HOLD_TO_CONFIRM_MS:
                    self.matched_at_ms = timestamp_ms
                    verdict = MATCH
                else:
                    verdict = ADJUST
            else:
                # Any failed condition breaks continuity; the next qualifying frame starts over.
                self.hold_start_ms = None
                if acceptance_distance > REJECT_DISTANCE:
                    verdict = NOT_RECOGNISED
                elif top_probability >= ADJUST_PROBABILITY:
                    verdict = ADJUST

        return Reading(
            top=top,
            top_probability=top_probability,
            runner_up=runner_up,
            runner_up_probability=runner_up_probability,
            nearest_distance=acceptance_distance,
            verdict=verdict,
            hold_progress=hold_progress,
            ranked=ranked,
        )

    def no_hand(self, timestamp_ms: int) -> Reading:
        self.hand_free_frames += 1
        if self.hand_free_frames < 3:
            # Debounce: a frame or two without landmarks is usually the landmarker blinking.
            self.hand_free_frames = 3
        return Reading(
            top=None,
            top_probability=0.0,
            runner_up=None,
            runner_up_probability=0.0,
            nearest_distance=float("inf"),
            verdict=NO_HAND,
            hold_progress=0.0,
        )

    @staticmethod
    def _progress(elapsed_ms: int) -> float:
        return min(max(elapsed_ms / HOLD_TO_CONFIRM_MS, 0.0), 1.0)


def evaluate_leave_one_out(reference: ReferenceSet, k: int = K) -> dict:
    """Classify every exemplar against the set with that exemplar removed.

    This is the honest offline question: a real hand is never a member of the
    reference set, so scoring the set against itself would be measuring memory
    rather than recognition. A letter that holds only one exemplar cannot be
    tested this way and is reported separately instead of being counted as
    either a hit or a miss.
    """
    vectors = reference.vectors
    letters = reference.exemplar_letters
    total = len(letters)

    per_letter: dict[str, dict[str, int]] = {}
    confusions: dict[str, dict[str, int]] = {}
    distances: list[float] = []
    untestable: dict[str, int] = {}

    for index in range(total):
        truth = letters[index]
        keep = np.ones(total, dtype=bool)
        keep[index] = False
        remaining_letters = [letters[i] for i in range(total) if keep[i]]
        if truth not in remaining_letters:
            untestable[truth] = untestable.get(truth, 0) + 1
            continue
        candidates = sorted(set(remaining_letters))
        ranked, nearest, _ = classify_frame(
            vectors[index], vectors[keep], remaining_letters, candidates, k
        )
        predicted = ranked[0][0] if ranked else None
        bucket = per_letter.setdefault(truth, {"tested": 0, "correct": 0})
        bucket["tested"] += 1
        if predicted == truth:
            bucket["correct"] += 1
        else:
            confusions.setdefault(truth, {})
            confusions[truth][predicted] = confusions[truth].get(predicted, 0) + 1
        distances.append(nearest)

    tested = sum(bucket["tested"] for bucket in per_letter.values())
    correct = sum(bucket["correct"] for bucket in per_letter.values())
    return {
        "total": total,
        "tested": tested,
        "correct": correct,
        "accuracy": (correct / tested) if tested else 0.0,
        "per_letter": per_letter,
        "confusions": confusions,
        "untestable": untestable,
        "median_distance": float(np.median(distances)) if distances else float("nan"),
    }
