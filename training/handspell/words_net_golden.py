"""Generates training/testdata/words_net_golden.{bin,json}: a tiny fixed-seed HSCN model plus inputs and the
probabilities handspell.words_net.forward gives, so WordNet.kt is pinned to the numpy reference.

The net is small (embed 8, 2 conv layers of 8 channels, head 6, 4 classes) but uses the real input geometry: 16
steps of the real per-step feature width. Inputs are rounded to 9 significant digits before the expected outputs
are computed. Run `python -m handspell.words_net_golden`.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from handspell.words_features import FEATURE_DIM, SLOT_DIM, STEPS
from handspell.words_net import WordNetWeights, forward, pack, unpack

_TESTDATA = Path(__file__).resolve().parent.parent / "testdata"
STEP_DIM = SLOT_DIM * 2


def build_model() -> WordNetWeights:
    rng = np.random.default_rng(20260930)
    emb, ch, hidden, classes = 8, 8, 6, 4

    def r(*shape, fan):
        return (rng.normal(0, 1, shape) * 1.5 / np.sqrt(fan)).astype(np.float32)

    return WordNetWeights(
        steps=STEPS, step_dim=STEP_DIM,
        mu=rng.normal(0, 0.1, STEPS * STEP_DIM).astype(np.float32),
        sd=rng.uniform(0.5, 1.5, STEPS * STEP_DIM).astype(np.float32),
        w_in=r(emb, 2 * STEP_DIM, fan=2 * STEP_DIM), b_in=r(emb, fan=1),
        convs=[(r(ch, emb, 3, fan=emb * 3), r(ch, fan=1), 1), (r(ch, ch, 3, fan=ch * 3), r(ch, fan=1), 2)],
        w_h1=r(hidden, 2 * ch, fan=2 * ch), b_h1=r(hidden, fan=1),
        w_h2=r(classes, hidden, fan=hidden), b_h2=r(classes, fan=1),
        labels=["hello", "bye", "yes", "other"],
    )


def build() -> tuple[bytes, dict]:
    data = pack(build_model())
    model = unpack(data)  # what a reader sees, float32-rounded
    rng = np.random.default_rng(7)
    cases = []
    for _ in range(3):
        x = np.vectorize(lambda v: float(f"{float(v):.9g}"))(rng.normal(0, 0.6, FEATURE_DIM)).astype(np.float32)
        probs = forward(model, x)
        cases.append({"features": x.tolist(), "expected": [float(f"{float(p):.9g}") for p in probs]})
    return data, {"labels": model.labels, "cases": cases}


if __name__ == "__main__":
    data, doc = build()
    (_TESTDATA / "words_net_golden.bin").write_bytes(data)
    (_TESTDATA / "words_net_golden.json").write_text(json.dumps(doc) + "\n", encoding="utf-8")
    print("wrote words_net_golden.{bin,json}", len(data), "bytes")
