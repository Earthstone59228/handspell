"""Extract compact hand + body landmarks from Google's Kaggle ISLR data (competition asl-signs, MediaPipe Holistic).

Reads sequences straight out of the downloaded zip (or from data/islr/train_landmark_files/ if unpacked), keeps only
what the word features need, and writes one .npz per call:

  sign[N], signer[N], sequence[N]   metadata from train.csv
  frame_count[N], offsets[N]        ragged layout into the frame arrays
  hands[F,2,21,2]                   image x, y for left_hand (slot 0) and right_hand (slot 1); NaN when absent
  pose[F,3,2]                       image x, y for pose landmarks nose (0), left shoulder (11), right shoulder (12)

Hand slots keep Holistic's labels only as a storage convention; handspell.words_features ignores them.

Usage (from training/):
  uv run --with pyarrow --with pandas python scripts/extract_islr.py --zip data/islr/bulk/asl-signs.zip \
      --signs hello,bye,... --out data/islr/free.npz [--per-sign 60]
"""

from __future__ import annotations

import argparse
import collections
import csv
import io
import zipfile
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np

POSE_IDX = {0: 0, 11: 1, 12: 2}
_ZIP: zipfile.ZipFile | None = None


def _load(args: tuple[str, str]):
    zip_path, member = args
    global _ZIP
    import pandas as pd

    if zip_path:
        if _ZIP is None:
            _ZIP = zipfile.ZipFile(zip_path)
        buf = io.BytesIO(_ZIP.read(member))
    else:
        buf = member
    d = pd.read_parquet(buf, columns=["frame", "type", "landmark_index", "x", "y"])
    frames = np.sort(d.frame.unique())
    if len(frames) == 0:
        return None
    pos = {f: i for i, f in enumerate(frames)}
    fi = d.frame.map(pos).to_numpy()
    n = len(frames)
    hands = np.full((n, 2, 21, 2), np.nan, np.float32)
    pose = np.full((n, 3, 2), np.nan, np.float32)
    for slot, kind in enumerate(("left_hand", "right_hand")):
        m = (d.type == kind).to_numpy()
        hands[fi[m], slot, d.landmark_index.to_numpy()[m]] = d[["x", "y"]].to_numpy()[m]
    m = (d.type == "pose").to_numpy() & d.landmark_index.isin(list(POSE_IDX)).to_numpy()
    if m.any():
        slots = d.landmark_index.to_numpy()[m]
        pose[fi[m], [POSE_IDX[int(i)] for i in slots]] = d[["x", "y"]].to_numpy()[m]
    return hands, pose


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--zip", default="")
    p.add_argument("--root", default="data/islr")
    p.add_argument("--signs", required=True)
    p.add_argument("--per-sign", type=int, default=0)
    p.add_argument("--out", required=True)
    p.add_argument("--workers", type=int, default=12)
    a = p.parse_args()
    signs = set(a.signs.split(","))
    seen = collections.Counter()
    rows = []
    for r in csv.DictReader(open(Path(a.root) / "train.csv")):
        if r["sign"] in signs and (a.per_sign == 0 or seen[r["sign"]] < a.per_sign):
            if not a.zip and not (Path(a.root) / r["path"]).exists():
                continue
            seen[r["sign"]] += 1
            rows.append(r)
    print(len(rows), "sequences", dict(seen), flush=True)
    jobs = [(a.zip, r["path"] if a.zip else str(Path(a.root) / r["path"])) for r in rows]
    hands, pose, keep, counts = [], [], [], []
    with ProcessPoolExecutor(a.workers) as pool:
        for r, res in zip(rows, pool.map(_load, jobs, chunksize=8)):
            if res is None:
                continue
            hands.append(res[0]); pose.append(res[1]); keep.append(r); counts.append(len(res[0]))
    counts_arr = np.asarray(counts, np.int32)
    offsets = np.concatenate([[0], np.cumsum(counts_arr)[:-1]]).astype(np.int64)
    np.savez_compressed(
        a.out,
        sign=np.asarray([r["sign"] for r in keep]),
        signer=np.asarray([r["participant_id"] for r in keep]),
        sequence=np.asarray([r["sequence_id"] for r in keep]),
        frame_count=counts_arr, offsets=offsets,
        hands=np.concatenate(hands), pose=np.concatenate(pose),
    )
    print("wrote", a.out, len(keep), "sequences", int(counts_arr.sum()), "frames")


if __name__ == "__main__":
    main()
