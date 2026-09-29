"""Train the word-sign conv network (spec words-v3) from landmarks extracted by extract_islr.py and export it.

Classes = the target words + "other" (unrelated signs; keeps false accepts down). Validation is by signer: signers are
split into --folds groups and every fold is scored by a model that never saw those signers (mirror averaging on, as on
the phone). The exported model is trained on all signers. Architecture and container: handspell/words_net.py.

Reported per fold and pooled, on held-out signers only:
  top1    accuracy over the target words ("other" samples excluded)
  accept  drill true-accept, p(target) >= --gate on a sample of that word
  false   drill false-accept, p(target) >= --gate on a sample of a different word or of an "other" sign

Usage (from training/):
  UV_TORCH_BACKEND=cpu uv run --with torch --with pandas python scripts/train_words_conv.py \
      --words data/islr/free.npz --other data/islr/other.npz --out runs/words-v3.bin
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from handspell import words_features as wf  # noqa: E402
from handspell.words_net import WordNetWeights, forward, pack, unpack  # noqa: E402

OTHER = "other"
STEP_DIM = wf.SLOT_DIM * 2
EMB = CH = 192
HIDDEN = 256


def load(path: str, other: bool):
    d = np.load(path)
    hands, pose = d["hands"], d["pose"]  # read once: every d[...] access re-decompresses the whole array
    X, y, s = [], [], []
    for i, (o, n) in enumerate(zip(d["offsets"], d["frame_count"])):
        v = wf.sequence_features(hands[o:o + n], pose[o:o + n])
        if v is not None:
            X.append(v); y.append(OTHER if other else str(d["sign"][i])); s.append(str(d["signer"][i]))
    return np.asarray(X), np.asarray(y), np.asarray(s)


def build_net(classes: int, drop: float):
    import torch.nn as nn

    class Net(nn.Module):
        def __init__(self):
            super().__init__()
            self.inp = nn.Sequential(nn.Linear(2 * STEP_DIM, EMB), nn.ReLU(), nn.Dropout(drop))
            self.conv = nn.Sequential(
                nn.Conv1d(EMB, CH, 3, padding=1), nn.ReLU(), nn.Dropout(drop),
                nn.Conv1d(CH, CH, 3, padding=1, stride=2), nn.ReLU(), nn.Dropout(drop),
                nn.Conv1d(CH, CH, 3, padding=1, stride=2), nn.ReLU())
            self.head = nn.Sequential(nn.Dropout(drop), nn.Linear(2 * CH, HIDDEN), nn.ReLU(), nn.Dropout(drop),
                                      nn.Linear(HIDDEN, classes))

        def forward(self, x):
            import torch
            seq = x[:, : wf.STEPS * STEP_DIM].reshape(len(x), wf.STEPS, STEP_DIM)
            delta = torch.cat([torch.zeros_like(seq[:, :1]), seq[:, 1:] - seq[:, :-1]], 1)
            o = self.conv(self.inp(torch.cat([seq, delta], 2)).transpose(1, 2))
            return self.head(torch.cat([o.mean(2), o.max(2).values], 1))

    return Net()


def fit(X, yi, classes, seed, epochs, drop, wd, dev):
    import torch
    import torch.nn as nn

    torch.manual_seed(seed)
    xcols = torch.as_tensor(wf.X_COLUMNS, device=dev)
    pres = torch.as_tensor([(s * 2 + k) * wf.SLOT_DIM + wf.SLOT_DIM - 1 for s in range(wf.STEPS) for k in range(2)], device=dev)
    xt = torch.as_tensor(X, device=dev)
    yt = torch.as_tensor(yi, device=dev)
    mu, sd = xt.mean(0), xt.std(0) + 1e-3

    def aug(x):
        x = x.clone()
        flip = (torch.rand(len(x), device=dev) < 0.5).nonzero().squeeze(1)
        x[flip[:, None], xcols[None]] *= -1
        x[:, xcols] *= torch.empty(len(x), 1, device=dev).uniform_(0.85, 1.18)
        noise = torch.randn_like(x) * 0.02 * (x != 0)
        noise[:, pres] = 0
        return x + noise

    net = build_net(len(classes), drop).to(dev)
    opt = torch.optim.AdamW(net.parameters(), 1e-3, weight_decay=wd)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, 3e-3, total_steps=epochs * ((len(xt) + 255) // 256))
    for _ in range(epochs):
        net.train()
        perm = torch.randperm(len(xt), device=dev)
        for i in range(0, len(xt), 256):
            b = perm[i:i + 256]
            loss = nn.functional.cross_entropy(net((aug(xt[b]) - mu) / sd), yt[b], label_smoothing=0.05)
            opt.zero_grad(); loss.backward(); opt.step(); sched.step()
    net.eval()
    return net, mu, sd


def predict(net, mu, sd, X, dev):
    import torch

    xcols = torch.as_tensor(wf.X_COLUMNS, device=dev)
    with torch.no_grad():
        x = torch.as_tensor(X, device=dev)
        xm = x.clone(); xm[:, xcols] *= -1
        return ((torch.softmax(net((x - mu) / sd), 1) + torch.softmax(net((xm - mu) / sd), 1)) / 2).cpu().numpy()


def export(net, mu, sd, classes) -> WordNetWeights:
    def t(p):
        return p.detach().cpu().numpy().astype(np.float32)

    n = wf.STEPS * STEP_DIM
    return WordNetWeights(
        steps=wf.STEPS, step_dim=STEP_DIM, mu=t(mu)[:n], sd=t(sd)[:n],
        w_in=t(net.inp[0].weight), b_in=t(net.inp[0].bias),
        convs=[(t(net.conv[i].weight), t(net.conv[i].bias), s) for i, s in ((0, 1), (3, 2), (6, 2))],
        w_h1=t(net.head[1].weight), b_h1=t(net.head[1].bias), w_h2=t(net.head[4].weight), b_h2=t(net.head[4].bias),
        labels=list(classes))


def main() -> None:
    import torch

    p = argparse.ArgumentParser()
    p.add_argument("--words", required=True)
    p.add_argument("--other", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--folds", type=int, default=3)
    p.add_argument("--epochs", type=int, default=100)
    p.add_argument("--drop", type=float, default=0.4)
    p.add_argument("--wd", type=float, default=1e-2)
    p.add_argument("--gate", type=float, default=0.5)
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--no-final", action="store_true")
    a = p.parse_args()
    dev = "cuda" if torch.cuda.is_available() else "cpu"

    Xw, yw, sw = load(a.words, False)
    Xo, yo, so = load(a.other, True)
    X, y, signer = np.concatenate([Xw, Xo]), np.concatenate([yw, yo]), np.concatenate([sw, so])
    classes = sorted(set(yw)) + [OTHER]
    ci = {c: i for i, c in enumerate(classes)}
    yi = np.asarray([ci[c] for c in y])
    n_words = len(classes) - 1
    print(f"{len(Xw)} word + {len(Xo)} other samples, {len(set(signer))} signers, device={dev}", flush=True)

    signers = np.asarray(sorted(set(signer)))
    np.random.default_rng(a.seed).shuffle(signers)
    rows = []
    for k, held in enumerate(np.array_split(signers, a.folds)):
        te = np.isin(signer, held)
        net, mu, sd = fit(X[~te], yi[~te], classes, a.seed + k, a.epochs, a.drop, a.wd, dev)
        pr = predict(net, mu, sd, X[te], dev)
        yt = yi[te]
        word = yt != ci[OTHER]
        top1 = float((pr[word].argmax(1) == yt[word]).mean())
        accept = float(np.mean([(pr[yt == c, c] >= a.gate).mean() for c in range(n_words)]))
        false = [float((pr[yt != c, c] >= a.gate).mean()) for c in range(n_words)]
        per = {classes[c]: round(float((pr[yt == c].argmax(1) == c).mean()), 2) for c in range(n_words)}
        rows.append((top1, accept, float(np.mean(false)), max(false)))
        print(f"fold {k} ({len(held)} signers, n={int(te.sum())}): top1={top1:.3f} accept={accept:.3f} "
              f"false={np.mean(false):.3f} worst_false={max(false):.3f}\n   {per}", flush=True)
    m = np.mean(rows, 0)
    print(f"MEAN top1={m[0]:.3f} accept={m[1]:.3f} false={m[2]:.3f}", flush=True)
    if a.no_final:
        return

    net, mu, sd = fit(X, yi, classes, a.seed, a.epochs, a.drop, a.wd, dev)
    weights = export(net, mu, sd, classes)
    data = pack(weights)
    Path(a.out).parent.mkdir(parents=True, exist_ok=True)
    Path(a.out).write_bytes(data)
    # Export parity: the numpy reference on the packed file must match torch (no mirror averaging here).
    import torch as _t
    check = unpack(data)
    idx = np.random.default_rng(0).choice(len(X), 40, replace=False)
    with _t.no_grad():
        ref = _t.softmax(net((_t.as_tensor(X[idx], device=dev) - mu) / sd), 1).cpu().numpy()
    diff = max(float(np.abs(forward(check, X[i]) - ref[j]).max()) for j, i in enumerate(idx))
    print(f"wrote {a.out} ({len(data)} bytes); numpy-vs-torch max diff {diff:.2e}")
    Path(a.out).with_suffix(".json").write_text(json.dumps({
        "file": Path(a.out).name, "spec": 30, "labels": classes, "gate": a.gate,
        "cv": {"folds": a.folds, "top1": m[0], "accept": m[1], "false": m[2], "worst_false": m[3]},
        "signers": len(set(signer)), "samples": int(len(X))}, indent=2) + "\n")


if __name__ == "__main__":
    main()
