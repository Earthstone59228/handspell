"""Print Kotlin expected probability arrays for the real MLP asset parity test.

Run from the repo root:
  training/.venv/bin/python training/scripts/generate_mlp_asset_parity.py

The selected team CSV row indices are embedded in MlpAssetParityTest. This
script reads only the saved CSV and exported .npz; it does not write an asset.
"""

import csv
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
INDICES = (6, 16, 33, 50, 96, 97)


def main() -> None:
    with (ROOT / "training/data/team-references-v1.csv").open() as handle:
        rows = list(csv.reader(line for line in handle if not line.startswith("#")))[1:]
    with np.load(ROOT / "training/runs/mlp-v1.npz", allow_pickle=False) as data:
        labels = list(map(str, data["labels"]))
        print("labels:", ", ".join(labels))
        for index in INDICES:
            values = np.asarray(rows[index][1:67], dtype=np.float32).astype(np.float64)
            layer = 0
            while f"W{layer}" in data:
                weights = np.asarray(data[f"W{layer}"], dtype=np.float32).astype(np.float64)
                bias = np.asarray(data[f"b{layer}"], dtype=np.float32).astype(np.float64)
                values = weights @ values + bias
                if f"W{layer + 1}" in data:
                    values = np.maximum(values, 0.0)
                layer += 1
            exponents = np.exp(values - values.max())
            probabilities = exponents / exponents.sum()
            print(f"// CSV row {index}: {rows[index][0]}")
            print("doubleArrayOf(" + ", ".join(f"{float(p):.10g}" for p in probabilities) + "),")


if __name__ == "__main__":
    main()
