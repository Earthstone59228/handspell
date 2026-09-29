"""Independently retrain the MLP with each live letter held out completely.

Imports only the trainer's data preparation recipe; scoring uses the separate
evaluate_live_gate MLP loader and gate. Writes temporary .npz files under /tmp.
"""

from __future__ import annotations

import tempfile
from pathlib import Path

import numpy as np
from sklearn.neural_network import MLPClassifier

import train_mlp as recipe
from evaluate_live_gate import Mlp, STATIC, gate


def main() -> None:
    with np.load(recipe.PHOTO_NPZ, allow_pickle=False) as data:
        photo = data["vectors"].astype(np.float64)
        photo_letters = data["letters"]
    live, live_letters = recipe.read_live(recipe.LIVE_CSV)
    letters = sorted(set(live_letters))
    print("seed held true_accept false_accept worst_target worst_rate")
    for seed in (0, 1, 2):
        results = []
        for held in letters:
            training_mask = live_letters != held
            shift = recipe.live_offset(photo, photo_letters, live[training_mask], live_letters[training_mask])
            vectors = np.vstack([photo + shift, np.repeat(live[training_mask], recipe.LIVE_REPEAT, axis=0)])
            labels = np.concatenate([photo_letters, np.repeat(live_letters[training_mask], recipe.LIVE_REPEAT)])
            vectors, labels = recipe.augment(vectors, labels, np.random.default_rng(seed))
            model = MLPClassifier((recipe.HIDDEN,), alpha=recipe.ALPHA, max_iter=300, random_state=seed).fit(vectors, labels)
            arrays = {f"W{i}": w.T.astype(np.float32) for i, w in enumerate(model.coefs_)}
            arrays.update({f"b{i}": b.astype(np.float32) for i, b in enumerate(model.intercepts_)})
            arrays["labels"] = np.asarray(model.classes_)
            arrays["activations"] = np.asarray(["relu", "identity"])
            with tempfile.TemporaryDirectory(prefix="handspell-mlp-verify-") as folder:
                path = Path(folder) / "weights.npz"
                np.savez(path, **arrays)
                verifier = Mlp(path)
                samples = live[live_letters == held]
                true = 0
                false_by_target = {target: 0 for target in STATIC if target != held}
                for vector in samples:
                    score = verifier.classify(vector)
                    true += gate(score, held)
                    for target in false_by_target:
                        false_by_target[target] += gate(score, target)
            n = len(samples)
            false = sum(false_by_target.values()) / (n * len(false_by_target))
            worst_target = max(false_by_target, key=false_by_target.get)
            worst_rate = false_by_target[worst_target] / n
            print(f"{seed} {held} {true/n:.4f} {false:.4f} {worst_target} {worst_rate:.4f}", flush=True)
            results.append((true / n, false, worst_rate))
        print(f"seed {seed} mean_true={np.mean([r[0] for r in results]):.4f} "
              f"mean_false={np.mean([r[1] for r in results]):.4f} "
              f"worst_target={max(r[2] for r in results):.4f}", flush=True)


if __name__ == "__main__":
    main()
