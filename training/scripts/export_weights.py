"""Exports trained MLP weights to assets/classifier/mlp-v1.bin (HSML).

    uv run python scripts/export_weights.py --npz runs/mlp-v1.npz

The .npz is produced by the training task and must contain, for a model with
L layers:

    W0, b0, ... W{L-1}, b{L-1}   float arrays; Wi has shape (out_dim, in_dim)
    labels                       1-d array of class names, in class-index order
    activations                  optional 1-d array of "relu"/"identity",
                                 defaulting to ReLU on every layer but the last

This script does no training and no reshaping beyond what HSML needs: if the
arrays are wrong, it says so and exits rather than transposing something and
shipping a model that scores nonsense. It also writes the human-readable
sidecar the settings screen shows (docs/CLASSIFIER.md section 4); training
metadata the trainer knows about (LOSO scores, signer count, git sha) is
merged in by passing --metadata with a JSON file.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
from handspell.hsml import Activation, Layer, pack
from handspell.normalize import SPEC_VERSION, VECTOR_DIM

_ASSETS = (
    Path(__file__).resolve().parent.parent.parent / "android" / "app" / "src" / "main" / "assets" / "classifier"
)
_DEFAULT_OUT = _ASSETS / "mlp-v1.bin"


def layers_from_npz(archive) -> list[Layer]:
    """Reads W0/b0..Wn/bn plus the optional `activations` array."""
    count = 0
    while f"W{count}" in archive:
        count += 1
    if count == 0:
        raise ValueError("archive has no 'W0' array; expected W0, b0, W1, b1, ...")

    names = archive.get("activations", None)
    layers: list[Layer] = []
    for index in range(count):
        bias_key = f"b{index}"
        if bias_key not in archive:
            raise ValueError(f"archive has W{index} but no {bias_key}")
        weights = np.asarray(archive[f"W{index}"], dtype=np.float32)
        bias = np.asarray(archive[bias_key], dtype=np.float32)
        if names is not None:
            activation = Activation[str(names[index]).strip().upper()]
        else:
            activation = Activation.IDENTITY if index == count - 1 else Activation.RELU
        layers.append(Layer(weights=weights, bias=bias, activation=activation))
    return layers


def labels_from_npz(archive) -> list[str]:
    if "labels" not in archive:
        raise ValueError("archive has no 'labels' array")
    return [str(label).strip() for label in np.asarray(archive["labels"]).ravel()]


def _sidecar(path: Path, data: bytes, layers: list[Layer], labels: list[str], extra: dict) -> dict:
    document = {
        "file": path.name,
        "formatVersion": 1,
        "specVersion": SPEC_VERSION,
        "inputDim": VECTOR_DIM,
        "layers": [
            {"inDim": layer.in_dim, "outDim": layer.out_dim, "activation": layer.activation.name.lower()}
            for layer in layers
        ],
        "labels": labels,
        "bytes": len(data),
        "sha256": hashlib.sha256(data).hexdigest(),
        "exportedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
    }
    document.update(extra)
    return document


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--npz", type=Path, required=True, help="archive of trained arrays")
    parser.add_argument("--out", type=Path, default=_DEFAULT_OUT, help=f"output .bin (default: {_DEFAULT_OUT})")
    parser.add_argument(
        "--metadata",
        type=Path,
        default=None,
        help="optional JSON file merged into the sidecar (training date, git sha, LOSO scores)",
    )
    parser.add_argument("--no-sidecar", action="store_true", help="write only the .bin")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    if not args.npz.is_file():
        print(f"error: {args.npz} is not a file", file=sys.stderr)
        return 2

    with np.load(args.npz, allow_pickle=False) as archive:
        try:
            layers = layers_from_npz(archive)
            labels = labels_from_npz(archive)
        except (ValueError, KeyError) as error:
            print(f"error: {args.npz}: {error}", file=sys.stderr)
            return 1

    try:
        data = pack(layers, labels)
    except ValueError as error:
        print(f"error: {args.npz}: {error}", file=sys.stderr)
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(data)
    print(f"wrote {len(data)} bytes to {args.out}")
    print("  layers: " + " -> ".join([str(VECTOR_DIM)] + [str(layer.out_dim) for layer in layers]))
    print(f"  labels: {', '.join(labels)}")

    if not args.no_sidecar:
        extra = json.loads(args.metadata.read_text(encoding="utf-8")) if args.metadata else {}
        sidecar_path = args.out.with_suffix(".json")
        sidecar_path.write_text(
            json.dumps(_sidecar(args.out, data, layers, labels, extra), indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {sidecar_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
