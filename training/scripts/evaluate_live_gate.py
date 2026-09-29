"""Evaluate single-frame drill gates on saved reference and live-sample CSV files.

This measures the strict enter gate of DefaultFeedbackEngine, before EMA,
hysteresis and hold time. Saved vectors have no frame timing, so a gate pass is
necessary but not sufficient for a confirmed drill match.

Examples:
  python training/scripts/evaluate_live_gate.py --refs refs.csv --samples live.csv
  python training/scripts/evaluate_live_gate.py --refs mixed.csv --live-refs live.csv --samples live.csv --compare
  python training/scripts/evaluate_live_gate.py --model mlp --npz weights.npz --samples live.csv
"""

from __future__ import annotations

import argparse
import csv
from dataclasses import dataclass
from pathlib import Path

import numpy as np

STATIC = tuple(c for c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ" if c not in "JZ")
DIRECTION_AGNOSTIC = frozenset("ABCD")


@dataclass(frozen=True)
class Sample:
    letter: str
    vector: np.ndarray
    signer: str = ""
    session: str = ""


@dataclass(frozen=True)
class Score:
    labels: tuple[str, ...]
    probabilities: np.ndarray
    nearest_distance: float = float("nan")
    nearest_shape_distance: float = float("nan")

    @property
    def top1(self) -> str:
        # LetterScore sort: probability descending, then Letter enum order.
        return min(self.labels, key=lambda label: (-self.probability(label), label))

    def probability(self, letter: str) -> float:
        try:
            return float(self.probabilities[self.labels.index(letter)])
        except ValueError:
            return 0.0


def read_csv(path: Path) -> list[Sample]:
    with path.open(newline="", encoding="utf-8") as handle:
        rows = csv.DictReader(line for line in handle if not line.lstrip().startswith("#"))
        expected = [f"f{i}" for i in range(66)]
        if rows.fieldnames is None or "letter" not in rows.fieldnames or any(k not in rows.fieldnames for k in expected):
            raise ValueError(f"{path}: expected letter and f0..f65 columns")
        out = []
        for line, row in enumerate(rows, 2):
            letter = row["letter"].strip().upper()
            if letter not in STATIC:
                raise ValueError(f"{path}:{line}: invalid static letter {letter!r}")
            vector = np.asarray([float(row[k]) for k in expected], dtype=np.float32)
            if not np.isfinite(vector).all():
                raise ValueError(f"{path}:{line}: nonfinite vector")
            out.append(Sample(letter, vector, (row.get("signer") or "").strip(), (row.get("session") or "").strip()))
    if not out:
        raise ValueError(f"{path}: no samples")
    return out


def same_pose(a: Sample, b: Sample) -> bool:
    return a.letter == b.letter and np.array_equal(a.vector, b.vector)


def held_out(ref: Sample, sample: Sample) -> bool:
    # A session is scoped to a signer when both fields exist. Otherwise a signer
    # id alone is a fold. Legacy CSVs have neither, so remove the exact pose.
    if sample.session and ref.session:
        if sample.signer and ref.signer:
            return (ref.signer, ref.session) == (sample.signer, sample.session)
        return ref.session == sample.session
    if sample.signer and ref.signer:
        return ref.signer == sample.signer
    return same_pose(ref, sample)


def knn(refs: list[Sample], vector: np.ndarray, k: int = 5) -> Score:
    """Kotlin k-NN: float32 inputs, 60 shape + 0.5*6 orientation, stable ties."""
    if not refs or k < 1:
        raise ValueError("k-NN needs references and k >= 1")
    matrix = np.stack([r.vector for r in refs]).astype(np.float64)
    delta = matrix - np.asarray(vector, dtype=np.float32).astype(np.float64)
    shape_sq = np.sum(delta[:, :60] ** 2, axis=1, dtype=np.float64)
    orientation_sq = np.sum(delta[:, 60:] ** 2, axis=1, dtype=np.float64)
    distances = np.sqrt(shape_sq + 0.5 * orientation_sq)
    # Kotlin's insertion list retains the earlier CSV row on an equal distance.
    chosen = np.argsort(distances, kind="stable")[:k]
    labels = tuple(sorted({r.letter for r in refs}))
    votes = {letter: 0.0 for letter in labels}
    total = 0.0
    for i in chosen:
        weight = 1.0 / (float(distances[i]) + 0.01)
        votes[refs[int(i)].letter] += weight
        total += weight
    probabilities = np.asarray([np.float32(votes[label] / total) for label in labels], dtype=np.float32)
    first = int(chosen[0])
    return Score(labels, probabilities, float(np.float32(distances[first])), float(np.float32(np.sqrt(shape_sq[first]))))


class Mlp:
    def __init__(self, path: Path):
        with np.load(path, allow_pickle=False) as archive:
            self.labels = tuple(str(v).strip().upper() for v in archive["labels"].ravel())
            if not self.labels or len(set(self.labels)) != len(self.labels) or any(c not in STATIC for c in self.labels):
                raise ValueError("MLP labels must be distinct static letters")
            self.layers = []
            index = 0
            while f"W{index}" in archive:
                weights = np.asarray(archive[f"W{index}"], dtype=np.float32)
                bias = np.asarray(archive[f"b{index}"], dtype=np.float32)
                name = str(archive["activations"][index]).lower() if "activations" in archive else ("identity" if f"W{index + 1}" not in archive else "relu")
                if weights.ndim != 2 or bias.shape != (weights.shape[0],) or name not in ("relu", "identity"):
                    raise ValueError(f"invalid MLP layer {index}")
                self.layers.append((weights.astype(np.float64), bias.astype(np.float64), name))
                index += 1
        if not self.layers or self.layers[0][0].shape[1] != 66 or self.layers[-1][0].shape[0] != len(self.labels):
            raise ValueError("MLP input/output dimension mismatch")
        if any(self.layers[i][0].shape[0] != self.layers[i + 1][0].shape[1] for i in range(len(self.layers) - 1)):
            raise ValueError("MLP hidden dimension mismatch")

    def classify(self, vector: np.ndarray) -> Score:
        values = np.asarray(vector, dtype=np.float32).astype(np.float64)
        for weights, bias, activation in self.layers:
            values = weights @ values + bias
            if activation == "relu":
                values = np.maximum(values, 0.0)
        exponents = np.exp(values - values.max())
        probabilities = (exponents / exponents.sum()).astype(np.float32)
        return Score(self.labels, probabilities)


def gate(score: Score, target: str, probability: float = 0.85, margin: float = 0.20, distance: float = 0.32) -> bool:
    """Strict, unengaged matchConditionsMet; NaN distance means MLP stage 2."""
    if target not in score.labels:
        return False
    p = score.probability(target)
    runner_up = max((score.probability(c) for c in score.labels if c != target), default=0.0)
    acceptance_distance = score.nearest_shape_distance if target in DIRECTION_AGNOSTIC else score.nearest_distance
    return p >= probability and p - runner_up >= margin and (np.isnan(score.nearest_distance) or acceptance_distance <= distance)


def evaluate(refs: list[Sample] | None, samples: list[Sample], model: str = "knn", mlp: Mlp | None = None, k: int = 5,
             probability: float = 0.85, margin: float = 0.20, distance: float = 0.32) -> dict:
    if model not in ("knn", "mlp") or (model == "mlp" and mlp is None):
        raise ValueError("choose knn with references or mlp with weights")
    targets = sorted({s.letter for s in samples})
    stats = {letter: {"n": 0, "top1": 0, "gate": 0, "distances": [], "false": 0, "negatives": 0} for letter in targets}
    for sample in samples:
        if model == "knn":
            eligible = [r for r in (refs or []) if not held_out(r, sample)]
            score = knn(eligible, sample.vector, k)
        else:
            score = mlp.classify(sample.vector)
        own = stats[sample.letter]
        own["n"] += 1
        own["top1"] += score.top1 == sample.letter
        own["gate"] += gate(score, sample.letter, probability, margin, distance)
        if np.isfinite(score.nearest_distance):
            own["distances"].append(score.nearest_distance)
        for target in targets:
            if target != sample.letter:
                stats[target]["negatives"] += 1
                stats[target]["false"] += gate(score, target, probability, margin, distance)
    rows = {}
    for letter, counts in stats.items():
        rows[letter] = {
            "n": counts["n"], "top1": counts["top1"] / counts["n"],
            "gate_pass": counts["gate"] / counts["n"],
            "median_nearest": float(np.median(counts["distances"])) if counts["distances"] else float("nan"),
            "false_accept": counts["false"] / counts["negatives"] if counts["negatives"] else float("nan"),
        }
    n = sum(v["n"] for v in stats.values())
    false = sum(v["false"] for v in stats.values())
    negatives = sum(v["negatives"] for v in stats.values())
    return {"letters": rows, "n": n, "top1": sum(v["top1"] for v in stats.values()) / n,
            "true_accept": sum(v["gate"] for v in stats.values()) / n,
            "false_accept": false / negatives if negatives else float("nan"),
            "worst_target": max(rows, key=lambda c: rows[c]["false_accept"] if np.isfinite(rows[c]["false_accept"]) else -1)}


def print_report(name: str, result: dict) -> None:
    print(name)
    print("letter  n  top1  gate-pass  median-nearest  false-accept")
    for letter, row in result["letters"].items():
        print(f"{letter:>6} {row['n']:>3} {row['top1']:>5.2f} {row['gate_pass']:>10.2f} {row['median_nearest']:>15.3f} {row['false_accept']:>13.3f}")
    print(f"total n={result['n']} top1={result['top1']:.3f} true-accept={result['true_accept']:.3f} "
          f"false-accept={result['false_accept']:.3f} worst-target={result['worst_target']}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--refs", type=Path, help="reference CSV (required for knn)")
    parser.add_argument("--samples", type=Path, required=True, help="live sample CSV")
    parser.add_argument("--model", choices=("knn", "mlp"), default="knn")
    parser.add_argument("--npz", type=Path, help="MLP W0,b0,...,labels archive")
    parser.add_argument("--live-refs", type=Path, help="live-only reference CSV for --compare")
    parser.add_argument("--compare", action="store_true", help="compare live-only with mixed references")
    parser.add_argument("--exclude-refs", type=Path, help="remove matching rows from --refs, e.g. to get photo-only refs")
    parser.add_argument("--k", type=int, default=5)
    args = parser.parse_args(argv)
    if args.model == "knn" and args.refs is None:
        parser.error("--refs is required for knn")
    if args.model == "mlp" and args.npz is None:
        parser.error("--npz is required for mlp")
    if args.compare and (args.model != "knn" or args.live_refs is None or args.refs is None):
        parser.error("--compare requires knn, --refs, and --live-refs")
    samples = read_csv(args.samples)
    refs = read_csv(args.refs) if args.refs else None
    if args.exclude_refs:
        excluded = read_csv(args.exclude_refs)
        refs = [r for r in refs if not any(same_pose(r, e) for e in excluded)]
    if args.compare:
        print_report("live-only references", evaluate(read_csv(args.live_refs), samples, k=args.k))
        print_report("live + photo references", evaluate(refs, samples, k=args.k))
    else:
        classifier = Mlp(args.npz) if args.model == "mlp" else None
        print_report(args.model, evaluate(refs, samples, model=args.model, mlp=classifier, k=args.k))
    print("Gate pass is a single-frame enter condition, not an EMA/hold confirmation.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
