"""Exploratory leave-one-letter-out k-NN/domain-shift sweep on saved A–D data.

Runs only on CSVs. No camera, Android device, app asset write, or model export.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from pathlib import Path

import numpy as np

from evaluate_live_gate import STATIC, Sample, gate, knn, read_csv, same_pose

LETTERS = "ABCD"
ALPHAS = (0.0, 0.5, 1.0)
KS = (3, 5, 9)
PROBABILITIES = (0.70, 0.80, 0.85, 0.90, 0.95)
MARGINS = (0.10, 0.20, 0.30)
ABSOLUTE = (0.32, 0.45, 0.60, 0.80)
RELATIVE = (0.75, 1.0, 1.5, 2.0, 3.0, 4.0)


def mean_shift(photo: list[Sample], live: list[Sample], held_letter: str) -> np.ndarray:
    shifts = []
    for letter in LETTERS:
        if letter == held_letter:
            continue
        p = np.stack([r.vector for r in photo if r.letter == letter]).astype(np.float64)
        l = np.stack([r.vector for r in live if r.letter == letter]).astype(np.float64)
        shifts.append(l.mean(axis=0) - p.mean(axis=0))
    return np.mean(shifts, axis=0)


def adapted_refs(photo: list[Sample], shift: np.ndarray, alpha: float) -> list[Sample]:
    return [Sample(r.letter, np.asarray(r.vector.astype(np.float64) + alpha * shift, dtype=np.float32)) for r in photo]


def relative_scales(refs: list[Sample]) -> dict[str, float]:
    """Median nearest same-letter reference distance; shape block for A–D."""
    scales = {}
    for letter in STATIC:
        vectors = np.stack([r.vector for r in refs if r.letter == letter]).astype(np.float64)
        differences = vectors[:, None, :60] - vectors[None, :, :60]
        shape_sq = np.sum(differences * differences, axis=2)
        if letter in LETTERS:
            distances = np.sqrt(shape_sq)
        else:
            orient = vectors[:, None, 60:] - vectors[None, :, 60:]
            distances = np.sqrt(shape_sq + 0.5 * np.sum(orient * orient, axis=2))
        np.fill_diagonal(distances, np.inf)
        scales[letter] = float(np.median(np.min(distances, axis=1)))
        if scales[letter] <= 0:
            raise ValueError(f"zero within-reference scale for {letter}")
    return scales


def target_nearest(refs: list[Sample], vector: np.ndarray) -> dict[str, float]:
    by_letter = {}
    query = vector.astype(np.float64)
    for letter in STATIC:
        vectors = np.stack([r.vector for r in refs if r.letter == letter]).astype(np.float64)
        delta = vectors[:, :60] - query[:60]
        squared = np.sum(delta * delta, axis=1)
        if letter not in LETTERS:
            orient = vectors[:, 60:] - query[60:]
            squared += 0.5 * np.sum(orient * orient, axis=1)
        by_letter[letter] = float(np.sqrt(np.min(squared)))
    return by_letter


def observations(photo: list[Sample], live: list[Sample], alpha: float, k: int) -> list[dict]:
    rows = []
    for held in LETTERS:
        shift = mean_shift(photo, live, held)
        refs = adapted_refs(photo, shift, alpha)
        scales = relative_scales(refs)
        for sample in (s for s in live if s.letter == held):
            score = knn(refs, sample.vector, k)
            rows.append({"actual": held, "score": score, "nearest": target_nearest(refs, sample.vector), "scales": scales})
    return rows


def assess(rows: list[dict], variant: str, threshold: float | None, probability: float, margin: float) -> dict:
    true = defaultdict(lambda: [0, 0])
    false = defaultdict(lambda: [0, 0])
    for row in rows:
        score = row["score"]
        for target in STATIC:
            passes = gate(score, target, probability, margin, float("inf"))
            if variant == "absolute":
                passes = passes and gate(score, target, probability, margin, threshold)
            elif variant == "relative":
                passes = passes and row["nearest"][target] / row["scales"][target] <= threshold
            if target == row["actual"]:
                true[target][0] += bool(passes)
                true[target][1] += 1
            else:
                false[target][0] += bool(passes)
                false[target][1] += 1
    mean_true = float(np.mean([true[c][0] / true[c][1] for c in LETTERS]))
    all_false = sum(false[c][0] for c in STATIC) / sum(false[c][1] for c in STATIC)
    worst_letter = max(STATIC, key=lambda c: false[c][0] / false[c][1])
    worst = false[worst_letter][0] / false[worst_letter][1]
    return {"true": mean_true, "false": all_false, "worst": worst, "worst_letter": worst_letter,
            "per_letter": {c: true[c][0] / true[c][1] for c in LETTERS}}


def sweep(photo: list[Sample], live: list[Sample]) -> tuple[list[dict], dict]:
    best_rows = []
    for alpha in ALPHAS:
        for k in KS:
            rows = observations(photo, live, alpha, k)
            top1 = float(np.mean([
                np.mean([r["score"].top1 == c for r in rows if r["actual"] == c]) for c in LETTERS
            ]))
            for variant, thresholds in (("absolute", ABSOLUTE), ("relative", RELATIVE), ("p/m only", (None,))):
                candidates = []
                for threshold in thresholds:
                    for probability in PROBABILITIES:
                        for margin in MARGINS:
                            metrics = assess(rows, variant, threshold, probability, margin)
                            candidates.append({"alpha": alpha, "k": k, "top1": top1, "variant": variant,
                                               "threshold": threshold, "p": probability, "m": margin, **metrics})
                feasible = [c for c in candidates if c["false"] <= 0.05 and c["worst"] <= 0.10]
                best = max(feasible or candidates, key=lambda c: (c["true"], -c["false"], -c["worst"], c["p"], c["m"]))
                best["feasible"] = bool(feasible)
                best_rows.append(best)
    chosen = max((r for r in best_rows if r["feasible"]), key=lambda c: (c["true"], -c["false"], -c["worst"]))
    return best_rows, chosen


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refs", type=Path, required=True, help="current mixed references CSV")
    parser.add_argument("--live", type=Path, required=True, help="saved team A-D CSV")
    args = parser.parse_args()
    live = read_csv(args.live)
    photo = [r for r in read_csv(args.refs) if not any(same_pose(r, s) for s in live)]
    rows, chosen = sweep(photo, live)
    print("| shift α | k | mean top-1 | gate | threshold | p | margin | mean true-accept | false-accept | worst target | feasible |")
    print("| ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- | --- |")
    for r in rows:
        threshold = "—" if r["threshold"] is None else f"{r['threshold']:.2f}"
        print(f"| {r['alpha']:.1f} | {r['k']} | {r['top1']:.3f} | {r['variant']} | {threshold} | "
              f"{r['p']:.2f} | {r['m']:.2f} | {r['true']:.3f} | {r['false']:.3f} | "
              f"{r['worst_letter']} {r['worst']:.3f} | {'yes' if r['feasible'] else 'no'} |")
    print("CHOSEN", chosen)


if __name__ == "__main__":
    main()
