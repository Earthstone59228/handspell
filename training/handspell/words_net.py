"""The word-sign network (container HSCN) and its numpy reference forward pass. WordNet.kt must match it.

Architecture (inference; dropout is a training-only detail):
  input      the first STEPS * STEP_DIM floats of a words_features vector (per-step features, step-major), then
             standardised per position with the stored mean and std ("mu", "sd")
  per step   x_t = [s_t, s_t - s_{t-1}] (the first step's difference is zeros), width 2 * STEP_DIM
  embed      ReLU(W_in x_t + b_in)                        -> [STEPS, emb]
  conv 1..n  Conv1d(k=3, padding=1, stride=strides[i]) + ReLU, channels emb -> ch -> ch ...
  pool       concat(mean over time, max over time)        -> 2 * ch
  head       ReLU(W_h1 z + b_h1) -> W_h2 -> softmax       -> classes

HSCN file, little-endian: magic "HSCN"; int32 format version (1), spec version, steps, step_dim, emb, ch, hidden,
classes, n_conv; n_conv int32 strides; float32 arrays in this order: mu, sd, W_in[emb, 2*step_dim], b_in,
per conv (W[out, in, 3], b), W_h1[hidden, 2*ch], b_h1, W_h2[classes, hidden], b_h2; int32 label count then per label
int32 byte length + UTF-8; uint32 CRC32 of everything before it.
"""

from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass

import numpy as np

MAGIC = b"HSCN"
FORMAT_VERSION = 1
SPEC_VERSION = 30  # words_features layout (SLOT_DIM 55, active-span crop)
KERNEL = 3


@dataclass
class WordNetWeights:
    steps: int
    step_dim: int
    mu: np.ndarray  # [steps * step_dim]
    sd: np.ndarray
    w_in: np.ndarray  # [emb, 2 * step_dim]
    b_in: np.ndarray
    convs: list[tuple[np.ndarray, np.ndarray, int]]  # (W [out, in, 3], b, stride)
    w_h1: np.ndarray
    b_h1: np.ndarray
    w_h2: np.ndarray
    b_h2: np.ndarray
    labels: list[str]

    @property
    def emb(self) -> int:
        return self.w_in.shape[0]

    @property
    def ch(self) -> int:
        return self.convs[0][0].shape[0]


def _conv1d(x: np.ndarray, w: np.ndarray, b: np.ndarray, stride: int) -> np.ndarray:
    """x [in, T] -> [out, T_out] with zero padding 1, cross-correlation like torch.nn.Conv1d."""
    c_in, t = x.shape
    padded = np.pad(x, ((0, 0), (1, 1)))
    t_out = (t + 2 - KERNEL) // stride + 1
    out = np.zeros((w.shape[0], t_out), np.float64)
    for i in range(t_out):
        window = padded[:, i * stride:i * stride + KERNEL]  # [in, 3]
        out[:, i] = np.tensordot(w, window, axes=([1, 2], [0, 1])) + b
    return np.maximum(out, 0.0)


def forward(model: WordNetWeights, features: np.ndarray) -> np.ndarray:
    """Probabilities in label order for one words_features vector (float64 arithmetic)."""
    n = model.steps * model.step_dim
    x = (features[:n].astype(np.float64) - model.mu) / model.sd
    seq = x.reshape(model.steps, model.step_dim)
    delta = np.vstack([np.zeros((1, model.step_dim)), seq[1:] - seq[:-1]])
    h = np.maximum(np.hstack([seq, delta]) @ model.w_in.T + model.b_in, 0.0)  # [steps, emb]
    z = h.T  # [emb, steps]
    for w, b, stride in model.convs:
        z = _conv1d(z, w, b, stride)
    pooled = np.concatenate([z.mean(axis=1), z.max(axis=1)])
    hidden = np.maximum(model.w_h1 @ pooled + model.b_h1, 0.0)
    logits = model.w_h2 @ hidden + model.b_h2
    e = np.exp(logits - logits.max())
    return e / e.sum()


def pack(m: WordNetWeights) -> bytes:
    body = bytearray(MAGIC)
    ints = [FORMAT_VERSION, SPEC_VERSION, m.steps, m.step_dim, m.emb, m.ch, m.w_h1.shape[0], len(m.labels), len(m.convs)]
    body += struct.pack(f"<{len(ints)}i", *ints)
    body += struct.pack(f"<{len(m.convs)}i", *[s for _, _, s in m.convs])
    arrays = [m.mu, m.sd, m.w_in, m.b_in]
    for w, b, _ in m.convs:
        arrays += [w, b]
    arrays += [m.w_h1, m.b_h1, m.w_h2, m.b_h2]
    for a in arrays:
        body += np.ascontiguousarray(a, dtype="<f4").tobytes()
    body += struct.pack("<i", len(m.labels))
    for label in m.labels:
        enc = label.encode("utf-8")
        body += struct.pack("<i", len(enc)) + enc
    return bytes(body) + struct.pack("<I", zlib.crc32(bytes(body)) & 0xFFFFFFFF)


def unpack(data: bytes) -> WordNetWeights:
    if data[:4] != MAGIC:
        raise ValueError("magic is not HSCN")
    if struct.unpack_from("<I", data, len(data) - 4)[0] != zlib.crc32(data[:-4]) & 0xFFFFFFFF:
        raise ValueError("CRC32 mismatch")
    off = 4
    fmt, spec, steps, step_dim, emb, ch, hidden, classes, n_conv = struct.unpack_from("<9i", data, off)
    off += 36
    if fmt != FORMAT_VERSION or spec != SPEC_VERSION:
        raise ValueError(f"format {fmt} / spec {spec} not supported")
    strides = struct.unpack_from(f"<{n_conv}i", data, off)
    off += 4 * n_conv

    def take(*shape):
        nonlocal off
        count = int(np.prod(shape))
        arr = np.frombuffer(data, "<f4", count, off).astype(np.float64).reshape(shape)
        off += 4 * count
        return arr

    mu, sd = take(steps * step_dim), take(steps * step_dim)
    w_in, b_in = take(emb, 2 * step_dim), take(emb)
    convs = []
    for i, stride in enumerate(strides):
        convs.append((take(ch, emb if i == 0 else ch, KERNEL), take(ch), stride))
    w_h1, b_h1 = take(hidden, 2 * ch), take(hidden)
    w_h2, b_h2 = take(classes, hidden), take(classes)
    (count,) = struct.unpack_from("<i", data, off)
    off += 4
    labels = []
    for _ in range(count):
        (n,) = struct.unpack_from("<i", data, off)
        off += 4
        labels.append(data[off:off + n].decode("utf-8"))
        off += n
    if off != len(data) - 4:
        raise ValueError("trailing bytes")
    return WordNetWeights(steps, step_dim, mu, sd, w_in, b_in, convs, w_h1, b_h1, w_h2, b_h2, labels)
