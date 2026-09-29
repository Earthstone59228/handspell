"""Train the word-sign MLP from PopSign landmarks extracted by extract_popsign.py.

A word sign moves, so one sample is a short sequence rather than one frame. Each sequence is turned into a
fixed-size vector (sequence_features below; the app must compute exactly the same thing, see
docs/research/words-v1-*.md):

  1. Pick the dominant hand: the MediaPipe handedness label seen in the most frames, ties broken by wrist
     travel. If it is Left, the whole sample is mirrored (x -> 1 - x, labels swapped), so every sample is
     signed with a canonical right hand. The other label becomes the second hand slot.
  2. Per frame and hand slot: the 66-float spec-v1 normalised shape (handspell.normalize), the wrist's image
     position centred on 0 (x - 0.5, y - 0.5) and a presence flag = 69 floats. An absent hand is all zeros.
  3. Resample the frames to T=8 evenly spaced steps (nearest frame).
  4. Append the dominant wrist's per-step displacement (dx, dy for 7 steps) so motion is explicit.
  => 8 * 2 * 69 + 14 = 1118 floats.

Validation is by PopSign's own splits, which are separated by participant. The chosen glosses and the free/Pro
split come from --vocab (a JSON list of glosses); everything else in the extracted data is ignored.

Usage (from training/):
  uv run --with scikit-learn scripts/train_words.py data/popsign/raw --vocab data/popsign/vocab-v1.json --out runs/words-v1.npz
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from handspell.normalize import normalize  # noqa: E402

STEPS = 8
SLOT_DIM = 69
FEATURE_DIM = STEPS * 2 * SLOT_DIM + (STEPS - 1) * 2
LEFT, RIGHT = 1, 2


def sequence_features(world: np.ndarray, image: np.ndarray, label: np.ndarray) -> np.ndarray | None:
    """world[F,2,21,3], image[F,2,21,2], label[F,2] (0 absent, 1 Left, 2 Right) -> FEATURE_DIM floats."""
    frames = world.shape[0]
    if frames == 0:
        return None
    counts = {LEFT: 0, RIGHT: 0}
    travel = {LEFT: 0.0, RIGHT: 0.0}
    last = {}
    for f in range(frames):
        for h in range(2):
            lab = int(label[f, h])
            if lab in counts:
                counts[lab] += 1
                wrist = image[f, h, 0]
                if lab in last:
                    travel[lab] += float(np.linalg.norm(wrist - last[lab]))
                last[lab] = wrist
    if counts[LEFT] == 0 and counts[RIGHT] == 0:
        return None
    dominant = max((LEFT, RIGHT), key=lambda k: (counts[k], travel[k]))
    other = LEFT if dominant == RIGHT else RIGHT
    mirror = dominant == LEFT

    per_frame = np.zeros((frames, 2, SLOT_DIM), np.float32)
    dom_xy = np.full((frames, 2), np.nan, np.float32)
    for f in range(frames):
        for h in range(2):
            lab = int(label[f, h])
            if lab == 0:
                continue
            slot = 0 if lab == dominant else 1
            shape = normalize(world[f, h].astype(np.float64), "Left" if lab == LEFT else "Right")
            if shape is None:
                continue
            x, y = float(image[f, h, 0, 0]), float(image[f, h, 0, 1])
            if mirror:
                x = 1.0 - x
            per_frame[f, slot, :66] = shape
            per_frame[f, slot, 66] = x - 0.5
            per_frame[f, slot, 67] = y - 0.5
            per_frame[f, slot, 68] = 1.0
            if slot == 0:
                dom_xy[f] = (x, y)
    del other
    idx = np.rint(np.linspace(0, frames - 1, STEPS)).astype(int)
    steps = per_frame[idx]
    xy = dom_xy[idx]
    # Fill gaps in the dominant wrist track from the nearest seen step so displacement stays finite.
    seen = ~np.isnan(xy[:, 0])
    if not seen.any():
        return None
    for i in range(STEPS):
        if not seen[i]:
            j = np.flatnonzero(seen)[np.argmin(np.abs(np.flatnonzero(seen) - i))]
            xy[i] = xy[j]
    motion = np.diff(xy, axis=0).reshape(-1)
    return np.concatenate([steps.reshape(-1), motion]).astype(np.float32)


def load(raw_dir: Path, vocab: list[str]):
    keep = {g: i for i, g in enumerate(vocab)}
    X, y, split, signer = [], [], [], []
    for path in sorted(raw_dir.glob("*.npz")):
        if path.name.endswith(".tmp.npz"):
            continue
        d = np.load(path)
        for s, gloss in enumerate(d["gloss"]):
            if gloss not in keep:
                continue
            o, n = int(d["offsets"][s]), int(d["frame_count"][s])
            v = sequence_features(d["world"][o:o + n], d["image"][o:o + n], d["label"][o:o + n])
            if v is None:
                continue
            X.append(v); y.append(keep[gloss]); split.append(str(d["split"][s])); signer.append(str(d["signer"][s]).split("/")[0])
    return np.asarray(X), np.asarray(y), np.asarray(split), np.asarray(signer)


def augment(X: np.ndarray, rng: np.random.Generator, copies: int) -> np.ndarray:
    out = [X]
    for _ in range(copies):
        noise = rng.normal(0, 0.03, X.shape).astype(np.float32)
        noise[:, SLOT_DIM - 1::SLOT_DIM] = 0  # never jitter presence flags (every 69th column)
        out.append(X + noise * (X != 0))
    return np.concatenate(out)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("raw_dir")
    p.add_argument("--vocab", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--hidden", default="256,128")
    p.add_argument("--seed", type=int, default=7)
    a = p.parse_args()
    from sklearn.neural_network import MLPClassifier
    from sklearn.preprocessing import StandardScaler

    vocab = json.loads(Path(a.vocab).read_text())["glosses"]
    X, y, split, signer = load(Path(a.raw_dir), vocab)
    print(f"{len(X)} samples, {len(vocab)} glosses; splits:", {s: int((split == s).sum()) for s in np.unique(split)})
    train = split == "train"
    held = split != "train"
    scaler = StandardScaler().fit(X[train])
    rng = np.random.default_rng(a.seed)
    Xa = augment(X[train], rng, copies=2)
    ya = np.concatenate([y[train]] * 3)
    hidden = tuple(int(h) for h in a.hidden.split(","))
    clf = MLPClassifier(hidden, alpha=1e-3, batch_size=256, learning_rate_init=1e-3, max_iter=60,
                        early_stopping=True, n_iter_no_change=6, random_state=a.seed)
    clf.fit(scaler.transform(Xa), ya)
    proba = clf.predict_proba(scaler.transform(X[held]))
    top1 = float((proba.argmax(1) == y[held]).mean())
    top3 = float(np.mean([y_ in np.argsort(-p_)[:3] for p_, y_ in zip(proba, y[held])]))
    per = {vocab[c]: float((proba[y[held] == c].argmax(1) == c).mean()) for c in np.unique(y[held])}
    print(f"held-out participants: top1={top1:.3f} top3={top3:.3f} n={int(held.sum())}")
    print("weakest:", sorted(per.items(), key=lambda kv: kv[1])[:10])

    # Fold the scaler into the first layer so the app feeds raw features: W0' = W0 / sd, b0' = b0 - W0' . mean.
    Ws = [w.T.astype(np.float32) for w in clf.coefs_]
    bs = [b.astype(np.float32) for b in clf.intercepts_]
    Ws[0] = (Ws[0] / scaler.scale_.astype(np.float32)).astype(np.float32)
    bs[0] = (bs[0] - Ws[0] @ scaler.mean_.astype(np.float32)).astype(np.float32)
    arrays = {f"W{i}": w for i, w in enumerate(Ws)} | {f"b{i}": b for i, b in enumerate(bs)}
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    np.savez(a.out, labels=np.asarray(vocab), **arrays,
             metrics=json.dumps({"top1": top1, "top3": top3, "per_gloss": per, "n_heldout": int(held.sum()),
                                 "feature_dim": FEATURE_DIM, "steps": STEPS}))
    print("wrote", a.out)


if __name__ == "__main__":
    main()
