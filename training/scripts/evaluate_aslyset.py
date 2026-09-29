"""Signer-held-out k-NN evaluation of the imported public static-letter data.

Matches Kotlin's weighted distance, five-neighbour vote and strict entry gates.
This evaluates individual images, not temporal false matches or live-device accuracy.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from handspell.references import STATIC_LETTERS
from import_aslyset import balanced_exemplars


def score(train, test):
    references = np.array([vector for letter in STATIC_LETTERS for vector in train[letter]])
    labels = np.array([letter for letter in STATIC_LETTERS for _ in train[letter]])
    scale = np.array([1.] * 60 + [np.sqrt(.5)] * 6)
    weighted = references * scale
    predictions, probabilities, distances, shape_distances = [], [], [], []
    for start in range(0, len(test), 64):
        batch = test[start:start + 64] * scale
        squared = (batch**2).sum(axis=1)[:, None] + (weighted**2).sum(axis=1)[None, :] - 2 * batch @ weighted.T
        # Stable sort retains original reference order for equal distances, like Kotlin insertion.
        neighbours = np.argsort(squared, axis=1, kind="stable")[:, :5]
        ds = np.sqrt(np.maximum(0, np.take_along_axis(squared, neighbours, axis=1)))
        votes = np.zeros((len(batch), len(STATIC_LETTERS)))
        for column, letter in enumerate(STATIC_LETTERS):
            votes[:, column] = ((labels[neighbours] == letter) / (ds + .01)).sum(axis=1)
        votes /= votes.sum(axis=1, keepdims=True)
        predictions.extend(np.array(STATIC_LETTERS)[np.argmax(votes, axis=1)])
        probabilities.extend(votes)
        distances.extend(ds[:, 0])
        shape_distances.extend(np.linalg.norm(test[start:start + 64, :60] - references[neighbours[:, 0], :60], axis=1))
    return np.array(predictions), np.array(probabilities), np.array(distances), np.array(shape_distances)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    data = np.load(args.data, allow_pickle=False)
    vectors, letters, signers = data["vectors"], data["letters"], data["signers"]
    matrix = np.zeros((24, 24), dtype=int)
    accepted_matrix = np.zeros((24, 24), dtype=int)
    folds = []
    for signer in sorted(set(signers)):
        held_out = signers == signer
        train = balanced_exemplars(vectors[~held_out], letters[~held_out], signers[~held_out])
        missing = set(STATIC_LETTERS) - train.keys()
        if missing:
            raise SystemExit(f"Fold {signer} missing {missing}")
        predictions, p, distance, shape_distance = score(train, vectors[held_out])
        actual = letters[held_out]
        fold_matrix = np.zeros_like(matrix)
        for index, (truth, predicted) in enumerate(zip(actual, predictions)):
            a, b = STATIC_LETTERS.index(truth), STATIC_LETTERS.index(predicted)
            matrix[a, b] += 1; fold_matrix[a, b] += 1
            ranked = sorted(p[index], reverse=True)
            acceptance_distance = shape_distance[index] if predicted in "ABCD" else distance[index]
            if ranked[0] >= .85 and ranked[0] - ranked[1] >= .20 and acceptance_distance <= .32:
                accepted_matrix[a, b] += 1
        recall = np.diag(fold_matrix) / np.maximum(fold_matrix.sum(axis=1), 1)
        folds.append({"signer": str(signer), "top1_recall": dict(zip(STATIC_LETTERS, recall.tolist()))})
        print(f"Held out {signer}: macro recall {recall.mean():.3f}", flush=True)
    recall = np.diag(matrix) / np.maximum(matrix.sum(axis=1), 1)
    precision = np.diag(matrix) / np.maximum(matrix.sum(axis=0), 1)
    f1 = 2 * precision * recall / np.maximum(precision + recall, 1e-12)
    accepted_recall = np.diag(accepted_matrix) / np.maximum(matrix.sum(axis=1), 1)
    report = {
        "protocol": "Four leave-one-ASLYset-user-out folds; signer-balanced 64 reference cap; no team A–D data in folds; no augmentations; k=5; orientation squared weight=.5; epsilon=.01",
        "limitations": "Still-image extraction and per-frame entry gates only. Does not establish live-camera accuracy, temporal false-match rates, correctness of source annotations, or generalization beyond four original volunteers.",
        "letters": list(STATIC_LETTERS), "images": len(letters), "folds": folds,
        "macro_f1": float(f1.mean()), "macro_recall": float(recall.mean()),
        "per_letter": {letter: {"images": int(matrix[i].sum()), "precision": float(precision[i]), "recall": float(recall[i]), "strict_entry_recall": float(accepted_recall[i]), "wrong_strict_entries": int(accepted_matrix[:, i].sum() - accepted_matrix[i, i])} for i, letter in enumerate(STATIC_LETTERS)},
        "confusion_matrix": matrix.tolist(), "strict_entry_confusion_matrix": accepted_matrix.tolist(),
        "live_device_trials": "Not run: no connected device", "release_validation": "Pending",
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Macro F1 {f1.mean():.3f}; strict-entry recall {accepted_recall.mean():.3f}")
    for letter, row in report["per_letter"].items():
        print(f"{letter}: n={row['images']} recall={row['recall']:.3f} strict={row['strict_entry_recall']:.3f} wrong_entries={row['wrong_strict_entries']}")


if __name__ == "__main__":
    main()
