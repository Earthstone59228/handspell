"""Extract raw per-frame hand landmarks from PopSign ASL v1.0 frame sequences.

Source: the `sign/popsign-images` Hugging Face mirror of PopSign ASL v1.0 (CC BY 4.0), non-game subset. Each
row is one isolated sign: an English gloss plus the sign segment's frames (256x256, about 5 fps). Every frame is
run through the same MediaPipe Hand Landmarker task file the app ships, in IMAGE mode with up to two hands, and
the raw result is stored so that sequence features can be iterated on without re-running MediaPipe.

Output, one .npz per input shard, in <out>/:
  gloss[S]        sign label
  split[S]        train / validation / test (from the shard name)
  signer[S]       source file path, whose first component identifies the recording participant
  frame_count[S]  frames in the sample
  offsets[S]      index of the sample's first frame in the frame arrays
  world[F,2,21,3] world landmarks for up to two hands per frame (NaN where absent)
  image[F,2,21,2] normalised image landmarks (x, y) for the same hands
  label[F,2]      handedness: 0 absent, 1 Left, 2 Right (MediaPipe's label, unchanged)
  score[F,2]      handedness score

Usage (from training/):
  uv run --with pyarrow --with pillow scripts/extract_popsign.py data/popsign/non-game data/popsign/raw --workers 8
"""

from __future__ import annotations

import os

# Same SIGKILL workaround as scripts/smoke_landmarker.py: MediaPipe must not see the desktop session.
_SESSION_VAR_PREFIXES = ("XDG_", "WAYLAND_", "DBUS_", "HYPRLAND_", "HYPRCURSOR_", "KITTY_", "UWSM_")
_SESSION_VARS = ("DISPLAY", "TERM", "SYSTEMD_EXEC_PID", "JOURNAL_STREAM", "INVOCATION_ID", "NOTIFY_SOCKET")
for _k in list(os.environ):
    if _k.startswith(_SESSION_VAR_PREFIXES) or _k in _SESSION_VARS:
        os.environ.pop(_k, None)

import argparse
import io
import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np

MODEL_PATH = Path(__file__).resolve().parent.parent / "models" / "hand_landmarker.task"


def _landmarker():
    from mediapipe.tasks.python import vision
    from mediapipe.tasks.python.core.base_options import BaseOptions

    options = vision.HandLandmarkerOptions(
        base_options=BaseOptions(model_asset_path=str(MODEL_PATH)),
        running_mode=vision.RunningMode.IMAGE,
        num_hands=2,
        min_hand_detection_confidence=0.3,
        min_hand_presence_confidence=0.3,
    )
    return vision.HandLandmarker.create_from_options(options)


def extract_shard(args: tuple[str, str, str | None]) -> str:
    shard, out_dir, only = args
    import mediapipe as mp
    import pyarrow.parquet as pq
    from PIL import Image

    out = Path(out_dir) / (Path(shard).stem + ".npz")
    if out.exists():
        return f"skip {out.name}"
    wanted = set(only.split(",")) if only else None
    split = Path(shard).name.split("-")[0]
    pf = pq.ParquetFile(shard)
    gloss, signer, counts, world, image, label, score = [], [], [], [], [], [], []
    with _landmarker() as landmarker:
        for group in range(pf.num_row_groups):
            table = pf.read_row_group(group, columns=["file", "text", "images"])
            for file, text, frames in zip(
                table.column("file").to_pylist(), table.column("text").to_pylist(), table.column("images").to_pylist()
            ):
                if wanted is not None and text not in wanted:
                    continue
                n = 0
                for frame in frames or []:
                    rgb = np.asarray(Image.open(io.BytesIO(frame["bytes"])).convert("RGB"))
                    result = landmarker.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=np.ascontiguousarray(rgb)))
                    w = np.full((2, 21, 3), np.nan, np.float32)
                    im = np.full((2, 21, 2), np.nan, np.float32)
                    lab = np.zeros(2, np.int8)
                    sc = np.zeros(2, np.float32)
                    for h, (wl, il, hd) in enumerate(
                        zip(result.hand_world_landmarks, result.hand_landmarks, result.handedness)
                    ):
                        if h >= 2:
                            break
                        w[h] = [[p.x, p.y, p.z] for p in wl]
                        im[h] = [[p.x, p.y] for p in il]
                        lab[h] = 1 if hd[0].category_name == "Left" else 2
                        sc[h] = hd[0].score
                    world.append(w); image.append(im); label.append(lab); score.append(sc)
                    n += 1
                gloss.append(text); signer.append(file); counts.append(n)
    counts_arr = np.asarray(counts, np.int32)
    offsets = np.concatenate([[0], np.cumsum(counts_arr)[:-1]]).astype(np.int64) if counts else np.zeros(0, np.int64)
    tmp = out.with_suffix(".tmp.npz")
    np.savez_compressed(
        tmp,
        gloss=np.asarray(gloss), split=np.asarray([split] * len(gloss)), signer=np.asarray(signer),
        frame_count=counts_arr, offsets=offsets,
        world=np.asarray(world, np.float32).reshape(-1, 2, 21, 3), image=np.asarray(image, np.float32).reshape(-1, 2, 21, 2),
        label=np.asarray(label, np.int8).reshape(-1, 2), score=np.asarray(score, np.float32).reshape(-1, 2),
    )
    tmp.rename(out)
    return f"done {out.name}: {len(gloss)} samples, {int(counts_arr.sum())} frames"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("shards_dir")
    parser.add_argument("out_dir")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--only", help="comma-separated glosses to keep (default: all)")
    parser.add_argument("--pattern", default="*.parquet")
    args = parser.parse_args()
    Path(args.out_dir).mkdir(parents=True, exist_ok=True)
    shards = sorted(str(p) for p in Path(args.shards_dir).glob(args.pattern))
    with ProcessPoolExecutor(args.workers) as pool:
        for message in pool.map(extract_shard, [(s, args.out_dir, args.only) for s in shards]):
            print(message, flush=True)


if __name__ == "__main__":
    sys.exit(main())
