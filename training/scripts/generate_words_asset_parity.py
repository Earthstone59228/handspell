"""Writes training/testdata/words_v3_parity.json and words_pro_v3_parity.json: real feature vectors and the probabilities the
numpy reference gives for the shipped assets/classifier/words-v3.bin and words-pro-v3.bin, so WordNetAssetTest pins the Kotlin forward pass to it on the real file.

Usage (from training/): uv run --with pandas python scripts/generate_words_asset_parity.py
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from handspell import words_features as wf  # noqa: E402
from handspell.words_net import forward, unpack  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
for asset, data_file, out in (("words-v3.bin", "free.npz", "words_v3_parity.json"),
                              ("words-pro-v3.bin", "pro24.npz", "words_pro_v3_parity.json")):
    model = unpack((ROOT.parent / "android/app/src/main/assets/classifier" / asset).read_bytes())
    d = np.load(ROOT / "data/islr" / data_file)
    hands, pose = d["hands"], d["pose"]
    cases = []
    for i in range(0, len(d["sign"]), max(1, len(d["sign"]) // 6)):
        o, n = int(d["offsets"][i]), int(d["frame_count"][i])
        v = wf.sequence_features(hands[o:o + n], pose[o:o + n])
        if v is None:
            continue
        v = np.vectorize(lambda x: float(f"{float(x):.9g}"))(v).astype(np.float32)
        cases.append({"sign": str(d["sign"][i]), "features": v.tolist(),
                      "expected": [float(f"{p:.9g}") for p in forward(model, v)]})
        if len(cases) == 5:
            break
    (ROOT / "testdata" / out).write_text(json.dumps({"labels": model.labels, "cases": cases}) + "\n")
    print("wrote", out, len(cases), "cases")
