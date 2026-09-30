"""Build the animated reference for every word: a clean, typical example sequence from ISLR (CC BY 4.0) that the app
draws as a looping wireframe (head, shoulders, hands), so a learner can see what to sign.

Per word: among sequences with a hand present in nearly every frame, a full body, and the majority dominant-hand side
(so left- and right-handed signers do not mix), pick the one closest to the word's mean feature vector (a medoid, so
it is typical rather than extreme). Gaps in the hand tracks are filled, the sequence is resampled to FRAMES steps,
lightly smoothed, oriented so the dominant hand is on the screen's right (as the learner sees themselves in the
mirrored preview) and placed in one fixed body frame inside a unit square.

Output (assets/content/word-refs.json), coordinates in the unit square, x right, y down:
  {"version": 1, "fps": 10, "words": {"hello": {"body": [noseX, noseY, leftShoulderX, leftShoulderY, rightShoulderX,
   rightShoulderY], "head": [radiusX, radiusY], "frames": [[hand0, hand1], ...]}}}
where each hand is 42 floats (x, y for the 21 hand landmarks) or null, slot 0 = the dominant hand.

Usage (from training/): uv run --with pandas python scripts/build_word_refs.py --npz data/islr/free.npz data/islr/pro24.npz
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from handspell import words_features as wf  # noqa: E402

FRAMES = 20
FPS = 10
MAX_GAP = 4
SHOULDER_WIDTH = 0.60
NOSE_AT = np.array([0.5, 0.31])
EDGE = 0.03
ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT.parent / "android/app/src/main/assets/content/word-refs.json"


def crop(hands: np.ndarray, pose: np.ndarray):
    seen = np.flatnonzero(~np.isnan(hands[:, :, 0, 0]).all(axis=1))
    if seen.size == 0:
        return None
    return hands[seen[0]:seen[-1] + 1], pose[seen[0]:seen[-1] + 1]


def dominant_slot(hands: np.ndarray) -> int:
    present = ~np.isnan(hands[:, :, 0, 0])
    travel = np.zeros(2)
    for h in range(2):
        idx = np.flatnonzero(present[:, h])
        if idx.size > 1:
            travel[h] = np.linalg.norm(np.diff(hands[idx, h, 0, :], axis=0), axis=1).sum()
    return max((0, 1), key=lambda h: (int(present[:, h].sum()), travel[h]))


def side(hands: np.ndarray, pose: np.ndarray) -> float:
    """Sign of (dominant wrist x - nose x), median over frames: +1 the hand is on the image's right."""
    a = dominant_slot(hands)
    wrist = hands[:, a, 0, 0]
    nose = pose[:, 0, 0]
    d = np.nanmedian(wrist - nose)
    return 1.0 if d >= 0 else -1.0


def fill_gaps(track: np.ndarray) -> np.ndarray:
    """track [F, 21, 2] with NaN frames; linearly fill gaps of at most MAX_GAP frames between present frames."""
    out = track.copy()
    present = ~np.isnan(track[:, 0, 0])
    idx = np.flatnonzero(present)
    for a, b in zip(idx[:-1], idx[1:]):
        if 1 < b - a <= MAX_GAP + 1:
            for f in range(a + 1, b):
                t = (f - a) / (b - a)
                out[f] = track[a] * (1 - t) + track[b] * t
    return out


def resample(track: np.ndarray) -> np.ndarray:
    """[F, 21, 2] -> [FRAMES, 21, 2]; NaN where either neighbouring frame is absent."""
    f = track.shape[0]
    pos = np.linspace(0, f - 1, FRAMES)
    out = np.full((FRAMES, 21, 2), np.nan)
    for k, p in enumerate(pos):
        lo, hi = int(np.floor(p)), int(np.ceil(p))
        if np.isnan(track[lo, 0, 0]) or np.isnan(track[hi, 0, 0]):
            continue
        t = p - lo
        out[k] = track[lo] * (1 - t) + track[hi] * t
    return out


def smooth(track: np.ndarray) -> np.ndarray:
    out = track.copy()
    for k in range(1, FRAMES - 1):
        window = track[k - 1:k + 2]
        if not np.isnan(window[:, 0, 0]).any():
            out[k] = window.mean(axis=0)
    return out


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--npz", nargs="+", required=True)
    p.add_argument("--out", default=str(OUT))
    a = p.parse_args()

    cands: dict[str, list] = {}
    for path in a.npz:
        d = np.load(path)
        hands, pose = d["hands"], d["pose"]
        for i, (o, n) in enumerate(zip(d["offsets"], d["frame_count"])):
            c = crop(hands[o:o + n], pose[o:o + n])
            if c is None:
                continue
            h, po = c
            if not 10 <= len(h) <= 60:
                continue
            slot = dominant_slot(h)
            if (~np.isnan(h[:, slot, 0, 0])).mean() < 0.9:
                continue
            body_ok = ~np.isnan(po[:, :, 0]).any(axis=1)
            if body_ok.mean() < 0.9:
                continue
            feat = wf.sequence_features(h, po)
            if feat is None:
                continue
            second = float((~np.isnan(h[:, 1 - slot, 0, 0])).mean())
            cands.setdefault(str(d["sign"][i]), []).append((h, po, feat, side(h, po), second))

    # The dataset-wide majority side of the dominant hand, so the picked examples all face the same way.
    sides = [c[3] for lst in cands.values() for c in lst]
    majority = 1.0 if np.mean(sides) >= 0 else -1.0
    print(f"{sum(len(v) for v in cands.values())} candidate sequences; dominant hand on image {'right' if majority > 0 else 'left'} "
          f"for {np.mean(np.asarray(sides) == majority):.0%}")
    mirror = majority < 0  # mirror so the dominant hand is on the screen's right

    words = {}
    for word, lst in sorted(cands.items()):
        lst = [c for c in lst if c[3] == majority]
        if len(lst) < 3:
            print("skipping", word, "only", len(lst))
            continue
        # A word most signers sign with both hands is shown with both, taken from clips that tracked both.
        two_handed = float(np.median([c[4] for c in lst])) >= 0.4
        if two_handed:
            both = [c for c in lst if c[4] >= 0.7]
            if len(both) >= 3:
                lst = both
        feats = np.asarray([c[2] for c in lst])
        mu, sd = feats.mean(0), feats.std(0) + 1e-3
        best = int(np.argmin((((feats - mu) / sd) ** 2).sum(1)))
        h, po = lst[best][0], lst[best][1]
        slot = dominant_slot(h)
        tracks = []
        for s in (slot, 1 - slot):
            t = fill_gaps(h[:, s])
            t = smooth(resample(t))
            tracks.append(t)
        nose = np.nanmedian(po[:, 0], axis=0)
        ls, rs = np.nanmedian(po[:, 1], axis=0), np.nanmedian(po[:, 2], axis=0)
        left_sh, right_sh = (ls, rs) if ls[0] <= rs[0] else (rs, ls)
        pts = [nose, left_sh, right_sh]
        # One fixed body frame for every word (same shoulder width, nose in the same place), so words look alike apart
        # from the hands. Mirror if needed; shrink about the middle only if a hand would leave the square.
        if mirror:
            nose, left_sh, right_sh = (np.array([-v[0], v[1]]) for v in (nose, left_sh, right_sh))
            left_sh, right_sh = (left_sh, right_sh) if left_sh[0] <= right_sh[0] else (right_sh, left_sh)
        sw_raw = float(np.linalg.norm(right_sh - left_sh)) or 1.0
        scale = SHOULDER_WIDTH / sw_raw
        offset = NOSE_AT - scale * nose
        pts = [x for tr in tracks for x in tr[~np.isnan(tr[:, 0, 0])].reshape(-1, 2)]
        moved = np.asarray([[-x, y] if mirror else [x, y] for x, y in pts]) * scale + offset if pts else np.zeros((1, 2))
        lo, hi = moved.min(0), moved.max(0)
        shrink = min(1.0, (0.5 - EDGE) / max(abs(lo - 0.5).max(), abs(hi - 0.5).max()))

        def tf(xy):
            xy = np.array(xy, dtype=float)
            if mirror:
                xy[..., 0] = -xy[..., 0]
            return (xy * scale + offset - 0.5) * shrink + 0.5

        frames = []
        for k in range(FRAMES):
            row = []
            for tr in tracks:
                row.append(None if np.isnan(tr[k, 0, 0]) else [round(float(v), 3) for v in tf(tr[k]).reshape(-1)])
            frames.append(row)
        # nose/shoulders are already in the mirrored frame above; tf() would mirror them again, so place them directly.
        place = lambda v: (np.asarray(v, dtype=float) * scale + offset - 0.5) * shrink + 0.5
        nose_t, l_t, r_t = place(nose), place(left_sh), place(right_sh)
        shoulder_w = float(np.linalg.norm(r_t - l_t))
        words[word] = {
            "body": [round(float(v), 3) for v in (*nose_t, *l_t, *r_t)],
            "head": [round(0.30 * shoulder_w, 3), round(0.40 * shoulder_w, 3)],
            "frames": frames,
        }
        present0 = sum(fr[0] is not None for fr in frames)
        present1 = sum(fr[1] is not None for fr in frames)
        print(f"{word:10s} from {len(lst):3d} clean sequences ({'two' if two_handed else 'one'} hand): hand0 in {present0}/{FRAMES}, hand1 in {present1}/{FRAMES}")

    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_text(json.dumps({"version": 1, "fps": FPS, "words": words}, separators=(",", ":")) + "\n")
    print("wrote", a.out, Path(a.out).stat().st_size, "bytes,", len(words), "words")


if __name__ == "__main__":
    main()
